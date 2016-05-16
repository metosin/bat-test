(ns metosin.boot-alt-test.impl
  (:require [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.track :as track]
            [clojure.tools.namespace.reload :as reload]
            [clojure.string :as string]
            [clojure.test :as test]
            [eftest.runner :as runner]
            [eftest.report :as report]
            [eftest.report.progress :as progress]
            [boot.util :as util]
            [boot.pod :as pod]))

(def tracker (atom nil))

;; From eftest: https://github.com/weavejester/eftest/blob/master/src/eftest/runner.clj
(defn- synchronize [f]
  (let [lock (Object.)] (fn [x] (locking lock (f x)))))

(defn- test-vars [ns vars opts]
  (let [once-fixtures (-> ns meta ::test/once-fixtures test/join-fixtures)
        each-fixtures (-> ns meta ::test/each-fixtures test/join-fixtures)
        report        (synchronize test/report)
        test-var      (fn [v] (binding [test/report report] (test/test-var v)))]
    (once-fixtures
     (fn []
       (if (:multithread? opts true)
         (dorun (pmap (bound-fn [v] (each-fixtures #(test-var v))) vars))
         (doseq [v vars] (each-fixtures #(test-var v))))))))

(defn- test-ns [ns vars opts]
  (let [ns (the-ns ns)]
    (binding [test/*report-counters* (ref test/*initial-report-counters*)]
      (test/do-report {:type :begin-test-ns, :ns ns})
      (test-vars ns vars opts)
      (test/do-report {:type :end-test-ns, :ns ns})
      @test/*report-counters*)))

(defn- test-all [vars opts]
  (->> (group-by (comp :ns meta) vars)
       (map (fn [[ns vars]] (test-ns ns vars opts)))
       (apply merge-with +)))

;; Modified to return the result map
(defn run-tests [vars opts]
  (let [start-time (System/currentTimeMillis)]
    (if (empty? vars)
      (println "No tests found.")
      (binding [report/*context* (atom {})
                test/report      (:report opts progress/report)]
        (test/do-report {:type :begin-test-run, :count (count vars)})
        (let [counters (test-all vars opts)
              duration (- (System/currentTimeMillis) start-time)
              counters (assoc counters :type :summary, :duration duration)]
          (test/do-report counters)
          counters)))))

(defn reload-and-test
  [tracker {:keys [test-matcher parallel? report-sym]
            :or {test-matcher #".*test"}}]
  (let [changed-ns (::track/load @tracker)
        tests (filter #(re-matches test-matcher (name %)) changed-ns)]

    (swap! tracker reload/track-reload)

    (when (::error @tracker)
      (util/fail "Error reloading: %s\n" (name (::error-ns @tracker)))
      (util/print-ex (::error @tracker)))

    (util/info "Testing: %s\n" (string/join ", " tests))

    (run-tests
      (runner/find-tests tests)
      (->> {:multithread? parallel?
            :report (when report-sym
                      (require (symbol (namespace report-sym)))
                      (deref (resolve report-sym)))}
           (filter val)
           (into {})))))

(defn run [opts]
  (swap! tracker (fn [tracker]
                   (if tracker
                     (dir/scan-dirs tracker)
                     (dir/scan-dirs (track/tracker) (:directories pod/env)))))

  (reload-and-test tracker opts))
