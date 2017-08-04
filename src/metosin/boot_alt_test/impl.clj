(ns metosin.boot-alt-test.impl
  (:require [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.track :as track]
            [clojure.tools.namespace.reload :as reload]
            [clojure.string :as string]
            [eftest.runner :as runner]
            [metosin.boot-alt-test.eftest :as eftest]
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

(defn reload-and-test
  [tracker {:keys [on-start-sym test-matcher parallel? report-sym filter-sym]
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

    (eftest/run-tests
      (filter filter-fn (runner/find-tests tests))
      (->> {:multithread? parallel?
            :report (when report-sym
                      (require (symbol (namespace report-sym)))
                      (deref (resolve report-sym)))}
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
