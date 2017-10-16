(ns metosin.boot-alt-test
  {:boot/export-tasks true}
  (:require [boot.pod :as pod]
            [boot.core :as core]
            [boot.util :as util]))

(def ^:private deps
  [['eftest "0.3.1"]
   ['org.clojure/tools.namespace "0.3.0-alpha3"]])

(core/deftask alt-test
  "Run clojure.test tests in a pod.

  Changed namespaces are reloaded using clojure.tools.namespace.
  Only tests in changed or affected namespaces are run.

  Default reporter is eftest.report.progress/report. Some alternatives are:
  - eftest.report.pretty/report (no progress bar)
  - clojure.test/report"
  [m test-matcher VAL regex "Regex used to select test namespaces"
   p parallel         bool  "Run tests parallel (default off)"
   r report       VAL edn   "Reporting function"
   f filter       VAL sym   "Function to filter the test vars"
   s on-start     VAL sym   "Function to be called before running tests (after reloading namespaces)"
   e on-end       VAL sym   "Function to be called after running tests"]
  (let [p (-> (core/get-env)
              (update-in [:dependencies] into deps)
              pod/make-pod
              future)
        opts (into {} (remove (comp nil? val)
                              {:test-matcher test-matcher
                               ;; If not set, nil => false
                               :parallel? (true? parallel)
                               :report report
                               :on-start-sym on-start
                               :on-end-sym on-end
                               :filter-sym filter
                               :verbosity (deref util/*verbosity*)
                               :watch-directories (:directories pod/env)}))]
    (fn [handler]
      (System/setProperty "java.awt.headless" "true")
      (pod/with-call-in @p (metosin.boot-alt-test.impl/enter-key-listener ~opts))
      (fn [fileset]
        (let [summary (pod/with-call-in @p (metosin.boot-alt-test.impl/run ~opts))]
          (if (> (+ (:fail summary 0) (:error summary 0)) 0)
            (throw (ex-info "Tests failed\n" (assoc summary :boot.util/omit-stacktrace? true)))
            (handler fileset)))))))
