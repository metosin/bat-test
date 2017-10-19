(ns metosin.boot-alt-test.impl
  (:require [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.track :as track]
            [clojure.tools.namespace.reload :as reload]
            [clojure.string :as string]
            [eftest.runner :as runner]
            ;; FIXME: load lazily?
            [eftest.report.pretty :as pretty]
            [eftest.report.progress :as progress]
            [eftest.report.junit :as junit]
            [eftest.report :as report]
            [metosin.boot-alt-test.util :as util]))

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
  [opts]
  (util/dbug "Listening to the enter key\n")
  (on-keypress
   java.awt.event.KeyEvent/VK_ENTER
   (fn [_]
     (when-not @running
       (util/info "Running all tests\n")
       (run-all opts)))))

(defn load-only-loaded-and-test-ns
  [{:keys [::track/load] :as tracker} test-matcher]
  (let [x (filter (fn [nss]
                    (or (find-ns nss)
                        (re-matches test-matcher (name nss))))
                  load)]
    (assoc tracker ::track/load x)))

(defn declarative-reporter
  "Configure reporters declaratively.

  :type can be keyword :pretty, :progress or nil (no output)."
  [{:keys [junit junit-output-to type]}]
  (fn [m]
    (case type
      :pretty (pretty/report m)
      :progress (progress/report m)
      nil nil
      (throw (ex-info "Bad declarative-reporter :type, should be :pretty, :progress or nil." {:type type})))

    (when junit
      (binding [clojure.test/*report-counters* nil]
        (let [r (report/report-to-file junit/report (or junit-output-to "junit.xml"))]
          (r m))))))

(defn reload-and-test
  [tracker {:keys [on-start-sym test-matcher parallel? report filter-sym]
            :or {test-matcher #".*test"}}]
  (let [changed-ns (::track/load @tracker)
        tests (filter #(re-matches test-matcher (name %)) changed-ns)
        filter-fn (if filter-sym
                    (do (require (symbol (namespace filter-sym)))
                        (deref (resolve filter-sym)))
                    identity)]

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

    (when on-start-sym
      (require (symbol (namespace on-start-sym)))
      ((resolve on-start-sym)))

    (util/info "Testing: %s\n" (string/join ", " tests))

    (runner/run-tests
      (filter filter-fn (runner/find-tests tests))
      (->> {:multithread? parallel?
            :report (cond
                      (map? report) (declarative-reporter report)
                      (symbol? report) (do (require (symbol (namespace report)))
                                        (deref (resolve report)))
                      (ifn? report) report
                      :else (throw (ex-info "Unknown :report value, should be map, symbol or fn." {:report report})))}
           (remove (comp nil? val))
           (into {})))))

(defn run [{:keys [on-end-sym watch-directories] :as opts}]
  (try
    (reset! running true)
    (swap! tracker (fn [tracker]
                     (util/dbug "Scan directories: %s\n" (pr-str watch-directories))
                     (dir/scan-dirs (or tracker (track/tracker)) watch-directories)))

    (reload-and-test tracker opts)
    (finally
      (when on-end-sym
        (require (symbol (namespace on-end-sym)))
        ((resolve on-end-sym)))

      (reset! running false))))

(defn run-all [opts]
  (reset! tracker nil)
  (run opts))
