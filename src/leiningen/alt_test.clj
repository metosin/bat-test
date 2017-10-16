(ns leiningen.alt-test
  (:require [leiningen.help]
            [leiningen.core.eval :as leval]
            [leiningen.core.project :as project]
            [leiningen.core.main :as main]
            [leiningen.help :as help]
            [clojure.java.io :as io]))

(def profile {:dependencies [['metosin/boot-alt-test "0.4.0-SNAPSHOT"]
                             ['eftest "0.3.1"]
                             ['org.clojure/tools.namespace "0.3.0-alpha3"]
                             ['watchtower "0.1.1"]]})

; From lein-cljsbuild
(defn- eval-in-project [project form requires]
  (leval/eval-in-project
    project
    ; Without an explicit exit, the in-project subprocess seems to just hang for
    ; around 30 seconds before exiting.  I don't fully understand why...
    `(try
       (do
         ~form
         (System/exit 0))
       (catch Exception e#
         (do
           (if (= ::fail (ex-data e#))
             (println (.getMessage e#))
             (.printStackTrace e#))
           (System/exit 1))))
    requires))

(defn- run-compiler
  "Run the alt-test."
  [project
   options
   watch?]
  (let [project' (project/merge-profiles project [profile])
        watch-directories (vec (concat (:test-paths project') (:source-paths project') (:resource-paths project')))
        opts (into {} (remove (comp nil? val)
                              ;; FIXME:
                              {:test-matcher (:test-matcher options)
                               ;; If not set, nil => false
                               :parallel? (true? (:parallel options))
                               :report (:report options)
                               :on-start-sym (:on-start options)
                               :on-end-sym (:on-end options)
                               :filter-sym (:filter options)
                               :verbosity (:verbosity options)
                               :watch-directories watch-directories}))]
    (eval-in-project
      project'
      `(let [f# (fn ~'run-tests [& ~'_]
                  (let [summary# (metosin.boot-alt-test.impl/run ~opts)]
                    (when (> (+ (:fail summary# 0) (:error summary# 0)) 0)
                      (throw (ex-info "Tests failed\n" (assoc summary# ::fail true))))))]
         (System/setProperty "java.awt.headless" "true")
         (metosin.boot-alt-test.impl/enter-key-listener ~opts)
         (if ~watch?
           @(watchtower.core/watcher
             ~watch-directories
             (watchtower.core/rate 100)
             (watchtower.core/file-filter watchtower.core/ignore-dotfiles)
             (watchtower.core/file-filter (watchtower.core/extensions :clj :cljc))
             (watchtower.core/on-change f#))
           (f#)))
      '(require 'metosin.boot-alt-test.impl 'watchtower.core))))

;; For docstrings

(defn- once
  "Run tests once"
  [project]
  nil)

(defn- auto
  "Run tests, then watch for changes and re-run until interrupted."
  [project]
  nil)

(defn alt-test
"Run clojure.test tests.

Changed namespaces are reloaded using clojure.tools.namespace.
Only tests in changed or affected namespaces are run.

Default reporter is eftest.report.progress/report. Some alternatives are:
- eftest.report.pretty/report (no progress bar)
- clojure.test/report

Options should be provided using `:alt-test` key in project map.

Available options:
:test-matcher    Regex used to select test namespaces
:parallel        Run tests parallel (default off)
:report          Reporting function
:filter          Function to filter the test vars
:on-start        Function to be called before running tests (after reloading namespaces)
:on-end          Function to be called after running tests

Command arguments:
Add `:debug` as subtask argument to enable debugging output."
  {:help-arglists '([once auto])
   :subtasks      [#'once #'auto]}
  ([project]
   (println (help/help-for "alt-test"))
   (main/abort))
  ([project subtask & args]
   (let [args (set args)
         config (cond-> (:alt-test project)
                  (contains? args ":debug") (assoc :verbosity 2))]
     (case subtask
       "once" (run-compiler project config false)
       "auto" (run-compiler project config true)
       "help" (println (help/help-for "alt-test"))
       (main/warn "Unknown task.")))))
