(ns leiningen.bat-test
  (:refer-clojure :exclude [read-string])
  (:require [leiningen.help]
            [leiningen.core.eval :as eval]
            [leiningen.core.project :as project]
            [leiningen.core.main :as main]
            [leiningen.help :as help]
            [leiningen.test :as test]
            [clojure.edn :refer [read-string]]
            [clojure.java.io :as io]
            [metosin.bat-test.cli :as cli]
            [metosin.bat-test.version :refer [+version+]]))

(def profile {:dependencies [['metosin/bat-test +version+]
                             ['eftest "0.5.9"]
                             ['org.clojure/tools.namespace "1.2.0"]
                             ['cloverage "1.1.1"]
                             ['hawk "0.2.11"]]})

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
  (let [watch-directories (or (:watch-directories opts) ;;TODO absolutize paths?
                              (vec (concat (:test-paths project)
                                           (:source-paths project)
                                           (:resource-paths project))))
        opts (assoc opts :watch-directories watch-directories)]
    (eval/eval-in-project
      project
      (if watch?
        `(let [opts# ~opts]
           (System/setProperty "java.awt.headless" "true")
           (metosin.bat-test.impl/run opts#)
           (metosin.bat-test.impl/enter-key-listener opts#)
           (hawk.core/watch! [{:paths '~watch-directories
                               :filter hawk.core/file?
                               :context (constantly 0)
                               :handler (fn [ctx# e#]
                                          (if (and (re-matches #"^[^.].*[.]cljc?$" (.getName (:file e#)))
                                                   (< (+ ctx# 1000) (System/currentTimeMillis)))
                                            (do
                                              (try
                                                (println)
                                                (metosin.bat-test.impl/run opts#)
                                                (catch Exception e#
                                                  (println e#)))
                                              (System/currentTimeMillis))
                                            ctx#))}]))
        `(let [opts# ~opts
               summary# (metosin.bat-test.impl/run opts#)
               exit-code# (min 1 (+ (:fail summary# 0) (:error summary# 0)))]
           (if ~(= :leiningen (:eval-in project))
             exit-code#
             (System/exit exit-code#))))
      `(require 'metosin.bat-test.impl 'hawk.core
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

lein bat-test (once|auto|cloverage|help)? <ns-sym>* (<selector-kw> <selector-non-kw-args>*)* (: <metosin.bat-test.cli/exec args...>)?

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
:test-matcher-directories    Vector of paths restricting the tests that will be matched. Relative to project root.
                             Default: nil (no restrictions).
:enter-key-listener If true, refresh tracker on enter key. Default: true. Only meaningful via `auto` subtask.

Also supports Lein test selectors, check `lein test help` for more information.

Arguments:
- once (default), auto, cloverage, help
- test selectors

Provides the same interface as metosin.bat-test.cli/exec for arguments after `:`.
eg., lein bat-test my.ns :only foo.bar/baz : :parallel? true"
  {:help-arglists '([& tests])
   :subtasks      [#'once #'auto]}
  [project & args]
  (let [opts (into {:enter-key-listener true}
                   (:bat-test project))
        [subtask args opts] (let [[op args] (or (when-some [op (#{"auto" "once" "help" "cloverage"} (first args))]
                                                  [op (next args)])
                                                ["once" args])
                                  [args opts] (cli/split-selectors-and-cli-args
                                                (into {:cloverage (= "cloverage" op)} opts)
                                                args)
                                  ;; give cli args the last word on auto/once. :cloverage value will be preserved via opts.
                                  op (case (:watch opts)
                                       true "auto"
                                       false "once"
                                       op)]
                              [op args opts])
        [namespaces selectors] (let [;; selectors after :
                                     [namespaces1 selectors1]
                                     (when (seq opts)
                                       (cli/-lein-test-read-args
                                         opts ;; opts
                                         nil  ;; test-paths
                                         true ;; quote-args?
                                         (:test-selectors project))) ;; user-selectors
                                     ;; selectors before :
                                     ;; read-args tries to find namespaces in test-paths if args doesn't contain namespaces
                                     [namespaces2 selectors2]
                                     (when (or (seq args)
                                               ;; TODO unit test this
                                               (empty? opts))
                                       (test/read-args
                                         args
                                         (assoc project :test-paths nil)))]
                                 ;; combine in disjunction
                                 [(concat namespaces1 namespaces2)
                                  (concat selectors1 selectors2)])
        ;; :only is now part of `selectors`
        opts (dissoc opts :only)
        project (project/merge-profiles project [:leiningen/test :test profile])
        config (-> opts
                   (assoc :selectors (vec selectors)
                          :namespaces (mapv (fn [n] `'~n) namespaces)))
        do-once #(try
                   (when-let [n (run-tests project config false)]
                     (when (and (number? n) (pos? n))
                       (throw (ex-info "Tests failed." {:exit-code n}))))
                   (catch clojure.lang.ExceptionInfo e
                     (main/abort "Tests failed.")))
        do-watch #(run-tests project config true)]
    (case subtask
      ("once" "cloverage") (do-once)
      "auto" (do-watch)
      "help" (println (help/help-for "bat-test"))
      (main/warn "Unknown task."))))
