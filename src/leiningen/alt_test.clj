(ns leiningen.alt-test
  (:require [leiningen.help]
            [leiningen.core.eval :as eval]
            [leiningen.core.project :as project]
            [leiningen.core.main :as main]
            [leiningen.help :as help]
            [clojure.java.io :as io]))

(def profile {:dependencies [['metosin/boot-alt-test "0.4.0-SNAPSHOT"]
                             ['eftest "0.3.2"]
                             ['org.clojure/tools.namespace "0.3.0-alpha3"]
                             ['watchtower "0.1.1"]]})

(defn- run-tests [project options watch?]
  (let [watch-directories (vec (concat (:test-paths project)
                                       (:source-paths project)
                                       (:resource-paths project)))
        opts (into {} (remove (comp nil? val)
                              (merge (dissoc options :on-start :on-end :filter)
                                     {;; If not set, nil => false
                                      :parallel? (true? (:parallel? options))
                                      :on-start-sym (:on-start options)
                                      :on-end-sym (:on-end options)
                                      :filter-sym (:filter options)})))]
    (eval/eval-in-project
      project
      (if watch?
        `(do
           (System/setProperty "java.awt.headless" "true")
           (metosin.boot-alt-test.impl/enter-key-listener ~opts)
           @(watchtower.core/watcher
              ~watch-directories
              (watchtower.core/rate 100)
              (watchtower.core/file-filter watchtower.core/ignore-dotfiles)
              (watchtower.core/file-filter (watchtower.core/extensions :clj :cljc))
              (watchtower.core/on-change (fn [~'_]
                                           (println)
                                           (metosin.boot-alt-test.impl/run ~opts)))))
        `(let [summary# (metosin.boot-alt-test.impl/run ~opts)
               exit-code# (+ (:fail summary# 0) (:error summary# 0))]
           (if ~(= :leiningen (:eval-in project))
             exit-code#
             (System/exit exit-code#))))
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

Reporter can be either:

- vector     List of reporters to run, items can be other allowed values
- map        Map with property `:type` which can be one of the following options
             and optional `:output-to` property which will redirect the output
             to a file.
- keyword    Shorthand to use the built-in eftest reporters: :pretty, :progress, :junit
- symbol     Symbol pointing to any reporter fn

Default reporter is :progress.

Options should be provided using `:alt-test` key in project map.

Available options:
:test-matcher    Regex used to select test namespaces
:parallel?       Run tests parallel (default off)
:report          Reporting function
:filter          Function to filter the test vars
:on-start        Function to be called before running tests (after reloading namespaces)
:on-end          Function to be called after running tests

Command arguments:
Add `:debug` as subtask argument to enable debugging output."
  {:help-arglists '([once auto])
   :subtasks      [#'once #'auto]}
  ([project]
   (alt-test project nil))
  ([project subtask & args]
   (let [args (set args)
         project (project/merge-profiles project [:leiningen/test :test profile])
         config (cond-> (:alt-test project)
                  (contains? args ":debug") (assoc :verbosity 2))]
     (case subtask
       ("once" nil) (try (when-let [n (run-tests project config false)]
                           (when (and (number? n) (pos? n))
                             (throw (ex-info "Tests failed." {:exit-code n}))))
                         (catch clojure.lang.ExceptionInfo e
                           (main/abort "Tests failed.")))
       "auto" (run-tests project config true)
       "help" (println (help/help-for "alt-test"))
       (main/warn "Unknown task.")))))
