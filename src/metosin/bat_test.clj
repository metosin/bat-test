(ns metosin.bat-test
  {:boot/export-tasks true}
  (:require [boot.pod :as pod]
            [boot.core :as core]
            [boot.util :as util]
            [metosin.bat-test.version :refer [+version+]]))

(def ^:private deps
  [['eftest "0.5.9"]
   ['metosin/bat-test +version+]
   ['cloverage "1.0.13"]
   ['org.clojure/tools.namespace "0.3.0-alpha4"]])

(core/deftask bat-test
  "Run clojure.test tests in a pod.

  Changed namespaces are reloaded using clojure.tools.namespace.
  Only tests in changed or affected namespaces are run.

  Cloverage report is not supposed to be used when used together
  with `watch` task.

  Reporter can be either:

  - vector     List of reporters to run, items can be other allowed values
  - map        Map with property `:type` which can be one of the following options
               and optional `:output-to` property which will redirect the output
               to a file.
  - keyword    Shorthand to use the built-in eftest reporters: :pretty, :progress, :junit
  - symbol     Symbol pointing to any reporter fn

  Default reporter is :progress."
  [m test-matcher VAL regex "Regex used to select test namespaces (default #\".*test\")"
   p parallel         bool  "Run tests parallel (default off)"
   r report       VAL edn   "Reporting function"
   f filter       VAL sym   "Function to filter the test vars"
   s on-start     VAL sym   "Function to be called before running tests (after reloading namespaces)"
   e on-end       VAL sym   "Function to be called after running tests"
   c cloverage        bool  "Enable Cloverage coverage report (default off)"
   _ cloverage-opts VAL edn "Cloverage options"]
  (let [p (-> (core/get-env)
              (update-in [:dependencies] into deps)
              pod/make-pod
              future)
        opts (assoc *opts*
                    :verbosity (deref util/*verbosity*)
                    :watch-directories (:directories pod/env))]
    (fn [handler]
      (System/setProperty "java.awt.headless" "true")
      (pod/with-call-in @p (metosin.bat-test.impl/enter-key-listener ~opts))
      (fn [fileset]
        (let [summary (pod/with-call-in @p (metosin.bat-test.impl/run ~opts))]
          (if (> (+ (:fail summary 0) (:error summary 0)) 0)
            (throw (ex-info "Tests failed\n" (assoc summary :boot.util/omit-stacktrace? true)))
            (handler fileset)))))))
