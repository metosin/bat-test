(ns leiningen.bat-test
  (:require [leiningen.help]
            [leiningen.core.eval :as eval]
            [leiningen.core.project :as project]
            [leiningen.core.main :as main]
            [leiningen.help :as help]
            [leiningen.test :as test]
            [clojure.java.io :as io]
            [metosin.bat-test.version :refer [+version+]]))

(def profile {:dependencies [['metosin/bat-test +version+]
                             ['eftest "0.5.9"]
                             ['org.clojure/tools.namespace "0.3.0-alpha4"]
                             ['cloverage "1.0.13"]
                             ['watchtower "0.1.1"]]})

(defn quoted-namespace [key s]
  (try
    `'~(-> s namespace symbol)
    (catch NullPointerException cause
      (throw (ex-info (format "%s symbol must be namespace qualified" key) {key s} cause)))))

(defn report-namespaces [report]
  (cond
    (vector? report) (mapcat report-namespaces report)
    (map? report) (report-namespaces (:type report))
    (symbol? report) [(quoted-namespace :report report)]
    :else nil))

(defn used-namespaces [{:keys [report on-start on-end filter]}]
  (cond-> (report-namespaces report)
    filter (conj (quoted-namespace :filter filter))
    on-start (conj (quoted-namespace :on-start on-start))
    on-end (conj (quoted-namespace :on-end on-end))))

(defn- run-tests [project opts watch?]
  (let [watch-directories (vec (concat (:test-paths project)
                                       (:source-paths project)
                                       (:resource-paths project)))
        opts (assoc opts :watch-directories watch-directories)]
    (eval/eval-in-project
      project
      (if watch?
        `(do
           (System/setProperty "java.awt.headless" "true")
           (metosin.bat-test.impl/enter-key-listener ~opts)
           @(watchtower.core/watcher
              ~watch-directories
              (watchtower.core/rate 100)
              (watchtower.core/file-filter watchtower.core/ignore-dotfiles)
              (watchtower.core/file-filter (watchtower.core/extensions :clj :cljc))
              (watchtower.core/on-change (fn [~'_]
                                           (println)
                                           (try
                                             (metosin.bat-test.impl/run ~opts)
                                             (catch Exception e#
                                               (println e#)))))))
        `(let [summary# (metosin.bat-test.impl/run ~opts)
               exit-code# (+ (:fail summary# 0) (:error summary# 0))]
           (if ~(= :leiningen (:eval-in project))
             exit-code#
             (System/exit exit-code#))))
      `(require 'metosin.bat-test.impl 'watchtower.core
                ~@(used-namespaces opts)))))

;; For docstrings

(defn- once
  "Run tests once"
  [project]
  nil)

(defn- auto
  "Run tests, then watch for changes and re-run until interrupted."
  [project]
  nil)

(defn bat-test
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

Options should be provided using `:bat-test` key in project map.

Available options:
:test-matcher    Regex used to select test namespaces
:parallel?       Run tests parallel (default off)
:report          Reporting function
:filter          Function to filter the test vars
:on-start        Function to be called before running tests (after reloading namespaces)
:on-end          Function to be called after running tests
:cloverage-opts  Cloverage options
:notify-command  String or vector describing a command to run after tests

Also supports Lein test selectors, check `lein test help` for more information.

Arguments:
- once, auto, cloverage, help
- test selectors"
  {:help-arglists '([& tests])
   :subtasks      [#'once #'auto]}
  [project & args]
  (let [subtask (or (some #{"auto" "once" "help" "cloverage"} args) "once")
        args (remove #{"auto" "once" "help" "cloverage"} args)
        ;; read-args tries to find namespaces in test-paths if args doesn't contain namespaces
        [namespaces selectors] (test/read-args args (assoc project :test-paths nil))
        project (project/merge-profiles project [:leiningen/test :test profile])
        config (assoc (:bat-test project)
                      :selectors (vec selectors)
                      :namespaces (mapv (fn [n] `'~n) namespaces)
                      :cloverage (= "cloverage" subtask))]
    (case subtask
      ("once" "cloverage")
      (try
        (when-let [n (run-tests project config false)]
          (when (and (number? n) (pos? n))
            (throw (ex-info "Tests failed." {:exit-code n}))))
        (catch clojure.lang.ExceptionInfo e
          (main/abort "Tests failed.")))

      "auto" (run-tests project config true)
      "help" (println (help/help-for "bat-test"))
      (main/warn "Unknown task."))))
