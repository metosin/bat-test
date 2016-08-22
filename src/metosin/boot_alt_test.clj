(ns metosin.boot-alt-test
  {:boot/export-tasks true}
  (:require [boot.pod :as pod]
            [boot.core :as core]))

(def ^:private deps
  [['eftest "0.1.1"]
   ['org.clojure/tools.namespace "0.3.0-alpha3"]])

(core/deftask alt-test
  "Run clojure.test tests in a pod.

  Changed namespaces are reloaded using clojure.tools.namespace.
  Only tests in changed or affected namespaces are run.

  By default tests are run parallel (parallel option).

  Default reporter is eftest.report.progress/report. Some alternatives are:
  - eftest.report.pretty/report (no progress bar)
  - clojure.test/report"
  [m test-matcher VAL regex "Regex used to select test namespaces"
   p parallel         bool  "Run tests parallel"
   r report       VAL sym   "Reporting function"
   f fail             bool  "Throw on failure (use on CI)"]
  (let [p (-> (core/get-env)
              (update-in [:dependencies] into deps)
              pod/make-pod
              future)
        opts (into {} (remove (comp nil? val)
                              {:test-matcher test-matcher
                               :parallel? parallel
                               :report-sym report}))]
    (fn [handler]
      (System/setProperty "java.awt.headless" "true")
      (pod/with-call-in @p (metosin.boot-alt-test.impl/enter-key-listener ~opts))
      (fn [fileset]
        (let [summary (pod/with-call-in @p (metosin.boot-alt-test.impl/run ~opts))]
          (if (> (+ (:fail summary 0) (:error summary 0)) 0)
            (if fail
              (throw (ex-info "Tests failed" summary))
              fileset)
            (handler fileset)))))))
