(ns metosin.boot-alt-test.eftest
  "From eftest: https://github.com/weavejester/eftest/blob/master/src/eftest/runner.clj

  With a minor modification to run-tests."
  (:require [clojure.test :as test]
            [eftest.runner :as runner]
            [eftest.report :as report]
            [eftest.report.progress :as progress]))

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
