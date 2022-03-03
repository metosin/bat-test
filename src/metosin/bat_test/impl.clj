(ns metosin.bat-test.impl
  (:require [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.find :as find]
            [clojure.tools.namespace.track :as track]
            [clojure.tools.namespace.reload :as reload]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as string]
            [eftest.runner :as runner]
            [eftest.report :as report]
            [metosin.bat-test.util :as util])
  (:import [java.util.regex Pattern]))

(def tracker (atom nil))
(def running (atom false))

(declare run-all)

(defn on-keypress
  [key f]
  (letfn [(read-in [] (.read System/in))]
    (future
      (loop [k (read-in)]
        (when (= k key) (f k))
        (recur (read-in))))))

(defn enter-key-listener
  ([opts] (enter-key-listener opts #'run-all))
  ([opts run-all]
   (when (:enter-key-listener opts)
     (util/dbug "Listening to the enter key\n")
     (on-keypress
       java.awt.event.KeyEvent/VK_ENTER
       (fn [_]
         (when-not @running
           (util/info "Running all tests\n")
           (run-all opts)))))))

(defn load-only-loaded-and-test-ns
  [{:keys [::track/load] :as tracker} test-matcher]
  (let [x (filter (fn [nss]
                    (or (find-ns nss)
                        (re-matches test-matcher (name nss))))
                  load)]
    (assoc tracker ::track/load x)))

(def built-in-reporters
  {:pretty 'eftest.report.pretty/report
   :progress 'eftest.report.progress/report
   :junit 'eftest.report.junit/report})

(defn combined-reporter
  "Combines the reporters by running first one directly,
  and others with clojure.test/*report-counters* bound to nil."
  [first & rst]
  (fn [m]
    (first m)
    (doseq [report rst]
      (binding [clojure.test/*report-counters* nil]
        (report m)))))

(defn resolve-reporter
  [report]
  (cond
    (vector? report)
    (let [reporters (doall (map resolve-reporter report))]
      (apply combined-reporter reporters))

    (map? report)
    (cond-> (resolve-reporter (:type report))
      (:output-to report) (report/report-to-file (:output-to report)))

    (keyword? report)
    (resolve-reporter (or (get built-in-reporters report)
                          (throw (ex-info "Unknown reporter" {:report report
                                                              :reporters (keys built-in-reporters)}))))

    (symbol? report)
    (do (assert (namespace report) "Reporter symbol should be namespaced")
        (require (symbol (namespace report)))
        (deref (resolve report)))

    (ifn? report) report

    :else (throw (ex-info "Unknown reporter value, should be keyword, symbol or fn." {:report report}))))

(defn resolve-hook
  "Resolve :report, :on-start etc. value to fn.

  In Boot, these are symbols, but in lein, usually they are already fns."
  [x]
  (cond
    (symbol? x)
    (do
      (require (symbol (namespace x)))
      (resolve x))

    (ifn? x) x

    :else (constantly true)))

(defn namespaces-match [selected-namespaces nss]
  (cond->> nss
    (seq selected-namespaces) (filter (set selected-namespaces))))

(defn selectors-match [selectors vars]
  (cond->> vars
    (seq selectors)
    (filter (fn [var]
              (some (fn [[selector args]]
                      (apply (eval (cond-> selector
                                     (vector? selector) second))
                             (merge (-> var meta :ns meta)
                                    (assoc (meta var) :leiningen.test/var var))
                             args))
                    selectors)))))

(defn maybe-run-cloverage [run-tests opts changed-ns test-namespaces]
  (if (:cloverage opts)
    ((requiring-resolve 'metosin.bat-test.cloverage/wrap-cloverage)
     ;; Don't instrument -test namespaces
     (remove #(contains? (set test-namespaces) %) changed-ns)
     (:cloverage-opts opts)
     run-tests)
    (run-tests)))

(defn test-matcher-only-in-directories [test-matcher
                                        test-dirs]
  {:pre [(or (instance? java.util.regex.Pattern test-matcher)
             (string? test-matcher))
         (or (nil? test-dirs)
             (string? test-dirs)
             (coll? test-dirs))]
   :post [(instance? java.util.regex.Pattern %)]}
  (let [test-dirs (cond-> test-dirs
                    (string? test-dirs) vector)
        re-and (fn [rs]
                 {:pre [(every? #(instance? java.util.regex.Pattern %) rs)]
                  :post [(instance? java.util.regex.Pattern %)]}
                 (case (count rs)
                   0 #".*"
                   1 (first rs)
                   ;; lookaheads
                   ;; https://www.ocpsoft.org/tutorials/regular-expressions/and-in-regex/
                   ;; https://stackoverflow.com/a/470602
                   (re-pattern (str "^"
                                    (apply str (map #(str "(?=" % ")") rs))
                                    ".*" ;; I think lookaheads don't consume anything..or something. see SO answer.
                                    "$"))))]
    (re-and
      (remove nil?
              [(cond-> test-matcher
                 (string? test-matcher) re-pattern)
               ;; if test-dirs is nil, don't change test-matcher
               ;; otherwise, it's a seqable of zero or more directories that a test must
               ;; be in to run.
               (when (some? test-dirs)
                 ;; both restricts the tests being run and stops
                 ;; bat-test.impl/load-only-loaded-and-test-ns from loading
                 ;; all test namespaces if only a subset are specified by
                 ;; `test-dirs`.
                 (if-some [matching-ns-res (->> test-dirs
                                                (map io/file)
                                                find/find-namespaces
                                                (map name)
                                                ;; TODO may want to further filter these files via a pattern
                                                ;(filter #(string/includes? % "test"))
                                                (map #(str "^" (Pattern/compile % Pattern/LITERAL) "$"))
                                                seq)]
                   (->> matching-ns-res
                        (string/join "|")
                        re-pattern)
                   ;; no namespaces in these directories, match nothing
                   ;;   https://stackoverflow.com/a/2930209
                   ;;   negative lookahead that matches anything. never matches.
                   #"(?!.*)"))]))))

(defn reload-and-test
  [tracker {:keys [on-start test-matcher parallel? report selectors namespaces test-dirs]
            :or {report :progress
                 test-matcher #".*test"}
            :as opts}]
  (let [test-matcher (test-matcher-only-in-directories test-matcher test-dirs)
        parallel? (true? parallel?)
        changed-ns (::track/load @tracker)
        test-namespaces (->> changed-ns
                             (filter #(re-matches test-matcher (name %)))
                             (namespaces-match namespaces))]

    ;; Only reload non-test namespaces which are already loaded
    (swap! tracker load-only-loaded-and-test-ns test-matcher)

    (util/dbug "Unload: %s\n" (pr-str (::track/unload @tracker)))
    (util/dbug "Load: %s\n" (pr-str (::track/load @tracker)))

    (swap! tracker reload/track-reload)

    (try
      (when (::reload/error @tracker)
        (util/fail "Error reloading: %s\n" (name (::reload/error-ns @tracker)))
        (throw (::reload/error @tracker)))
      (catch java.io.FileNotFoundException e
        (util/info "Reseting tracker due to file not found exception, all namespaces will be reloaded next time.\n")
        (reset! tracker (track/tracker))
        (throw e)))

    ((resolve-hook on-start))
    (util/info "Testing: %s\n" (string/join ", " test-namespaces))

    (maybe-run-cloverage
       (fn []
         (runner/run-tests
           (->> (runner/find-tests test-namespaces)
                (selectors-match selectors)
                (filter (resolve-hook (:filter opts))))
           (-> opts
               (dissoc :parallel? :on-start :on-end :filter :test-matcher :selectors :test-dirs)
               (assoc :multithread? parallel?
                      :report (resolve-reporter report)))))
       opts
       changed-ns
       test-namespaces)))

(defn result-message [{:keys [pass error fail]}]
  (if (pos? (+ fail error))
    (format "Failed %s of %s assertions"
            (+ fail error)
            (+ fail error pass))
    (format "Passed all tests")))

(defn run-notify-command [notify-command summary]
  (let [notify-command (if (string? notify-command)
                         [notify-command]
                         notify-command)
        message (result-message summary)]
    (when (seq notify-command)
      (let [command (concat notify-command [message])]
        (try
          (apply sh command)
          (catch Exception e
            (util/warn "Problem running shell command `%s`\n" (clojure.string/join " " command))
            (util/warn "Exception: %s\n" (.getMessage e))))))) )

(defn run [{:keys [on-end watch-directories notify-command] :as opts}]
  ;(prn `run opts)
  (try
    (reset! running true)
    (swap! tracker (fn [tracker]
                     (util/dbug "Scan directories: %s\n" (pr-str watch-directories))
                     (dir/scan-dirs (or tracker (track/tracker)) watch-directories)))
    (let [summary (reload-and-test tracker opts)]
      (run-notify-command notify-command summary)
      summary)
    (finally
      ((resolve-hook on-end))
      (reset! running false))))

(defn run-all [opts]
  (reset! tracker nil)
  (run opts))
