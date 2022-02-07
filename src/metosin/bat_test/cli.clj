(ns metosin.bat-test.cli
  (:refer-clojure :exclude [read-string test])
  (:require [clojure.edn :refer [read-string]]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.namespace.file :refer [read-file-ns-decl]]
            [clojure.tools.namespace.find :refer [find-namespaces]]
            [hawk.core :as hawk]
            [metosin.bat-test.impl :as impl]
            [metosin.bat-test.util :as util]))

;; https://github.com/technomancy/leiningen/blob/4d8ee78018158c05d69b250e7851f9d7c3a44fac/src/leiningen/test.clj#L162-L175
(def ^:private -only-form
  [`(fn [ns# & vars#]
      ((set (for [v# vars#]
              (-> (str v#)
                  (.split "/")
                  first
                  symbol)))
       ns#))
   `(fn [m# & vars#]
      (some #(let [var# (str "#'" %)]
               (if (some #{\/} var#)
                 (= var# (-> m# :leiningen.test/var str))
                 (= % (ns-name (:ns m#)))))
            vars#))])

(defn ^:private -user-test-selectors-form [{:keys [test-selectors-form-file] :as _args}]
  (let [selectors (some-> test-selectors-form-file
                          slurp
                          read-string)]
    (when (some? selectors)
      (assert (map? selectors) (format "Selectors in file %s must be a map" test-selectors-form-file)))
    selectors))

;; https://github.com/technomancy/leiningen/blob/4d8ee78018158c05d69b250e7851f9d7c3a44fac/src/leiningen/test.clj#L144-L154
(defn- -split-selectors
  "Selectors are (namespace*)(selector-kw selector-non-kw-arg*)*

  Unlike original function, returns a value--not code."
  [args quote-args?]
  (let [[nses selectors] (split-with (complement keyword?) args)]
    [nses
     (loop [acc {} [selector & selectors] selectors]
       (if (seq selectors)
         (let [[args next] (split-with (complement keyword?) selectors)]
           (recur (assoc acc selector (cond->> args
                                        quote-args? (list 'quote)))
                  next))
         (if selector
           (assoc acc selector ())
           acc)))]))

;; https://github.com/technomancy/leiningen/blob/4d8ee78018158c05d69b250e7851f9d7c3a44fac/src/leiningen/test.clj#L156-L160
(defn- -partial-selectors [project-selectors selectors]
  (for [[k v] selectors
        :let [selector-form (k project-selectors)]
        :when selector-form]
    [selector-form v]))

(defn ^:private -default-opts [args]
  (let [opts (if (map? args)
               args
               (if (map? (first args))
                 (do (assert (not (next args)))
                     (first args))
                 (apply hash-map args)))]
    (-> opts
        (assoc :cloverage
               (if-some [[_ v] (find opts :cloverage)]
                 v
                 (some? (:cloverage-opts opts))))
        (update :watch-directories
                (fn [watch-directories]
                  (or (not-empty watch-directories)
                      (->> (str/split (java.lang.System/getProperty "java.class.path") #":")
                           (remove #(str/ends-with? % ".jar")))))))))

;; https://github.com/technomancy/leiningen/blob/4d8ee78018158c05d69b250e7851f9d7c3a44fac/src/leiningen/test.clj#L177-L180
(defn- -convert-to-ns
  "Unlike original function, takes data, not strings.
  Files only converted if they are strings, which is
  a change to the lein-test interface."
  [possible-file]
  (if (and (string? possible-file)
           (re-matches #".*\.cljc?" possible-file)
           (.exists (io/file possible-file)))
    (second (read-file-ns-decl possible-file))
    possible-file))

(defn- opts->selectors [{:keys [only selectors] :as _opts}]
  (-> []
      ;; selectors go first in case they are prefixed by namespaces
      (into (or (when (vector? selectors)
                  selectors)
                (when (some? selectors)
                  [selectors])))
      (into (or (when (vector? only)
                  (into [:only] only))
                (when (some? only)
                  [:only only])))))

;; https://github.com/technomancy/leiningen/blob/4d8ee78018158c05d69b250e7851f9d7c3a44fac/src/leiningen/test.clj#L182
(defn ^:internal -lein-test-read-args
  "Unlike original function, reads list of values, not strings,
  and returns a value, not code.
  
  opts are from `run-tests`."
  [opts test-paths quote-args? user-selectors]
  (let [args (opts->selectors opts)
        args (->> args (map -convert-to-ns))
        [nses given-selectors] (-split-selectors args quote-args?)
        nses (or (seq nses)
                 (sort (find-namespaces
                         (map io/file (distinct test-paths)))))
        default-selectors {:all `(constantly true)
                           :only -only-form}
        selectors (-partial-selectors (into default-selectors
                                            user-selectors)
                                      given-selectors)
        selectors-or-default (if-some [default (when (empty? selectors)
                                                 (:default user-selectors))]
                               [[default ()]]
                               selectors)]
    (when (and (empty? selectors)
               (seq given-selectors))
      (throw (ex-info "Could not find test selectors." {})))
    [nses selectors-or-default]))

(defn- run-tests1
  "Run tests in :test-matcher-directories. Takes the same options as `run-tests`.
  
  Returns a test summary {:fail <num-tests-failed> :error <num-tests-errored>}"
  [opts]
  (let [[namespaces selectors] (-lein-test-read-args opts
                                                     (:paths opts)
                                                     false
                                                     (-user-test-selectors-form opts))
        opts (dissoc opts :only)
        {:keys [test-matcher-directories]} opts]
    (impl/run
      (-> opts
          ;; convert from `lein test`-style :selectors to internal bat-test representation.
          ;; done just before calling bat-test to avoid mistakes.
          (assoc :selectors selectors)
          (assoc :namespaces namespaces)))))

(defn run-tests
  "Run tests. 

  If :watch is true, returns a value that can be passed to `hawk.core/stop!` to stop watching.
  If :watch is false, returns a test summary {:fail <num-tests-failed> :error <num-tests-errored>}.

  Takes a single map of keyword arguments, or as keyword args.

  Takes a vector of test selectors as :selectors, basically the args to `lein test`,
  except file arguments must be strings:
  eg., (run-tests :selectors '[my.ns \"my/file.clj\" :disable :only foo/bar :integration])
       <=>
       lein test my.ns my/file.clj :disable :only foo/bar :integration

  There is special support for the `:only` selector.
  eg., (run-tests :only 'foo/bar :selectors '[blah thing])
       <=>
       (run-tests :selectors '[:only foo/bar blah thing])

  Available options:
  :watch             If true, continuously watch and reload (loaded) namespaces in
                     :watch-directories, and run tests in :test-matcher-directories when needed.
                     Default: false
  :selectors         A vector of test selectors.
  :only              A qualified deftest var to test only. `:only <provided arg>` will be added to existing :selectors.
  :test-matcher      The regex used to select test namespaces. A string can also be provided which will be coerce via `re-pattern`.
  :parallel?         Run tests parallel (default off)
  :capture-output?   Display logs even if tests pass (option for Eftest test runner)
  :report            Reporting function, eg., [:pretty {:type :junit :output-to \"target/junit.xml\"}]
  :filter            Function to filter the test vars
  :on-start          Function to be called before running tests (after reloading namespaces)
  :on-end            Function to be called after running tests
  :cloverage         True to activate cloverage
  :cloverage-opts    Cloverage options
  :notify-command    String or vector describing a command to run after tests
  :watch-directories Vector of paths to refresh if loaded. Relative to project root.
                     Only meaningful when :watch is true.
                     Defaults to all non-jar classpath entries.
  :test-matcher-directories  Vector of paths restricting the tests that will be matched. Relative to project root.
                             Default: nil (no restrictions).
  :headless           If true, set -Djava.awt.headless=true. Default: false. Only meaningful when :watch is true.
  :enter-key-listener If true, refresh tracker on enter key. Default: false. Only meaningful when :watch is true."
  [& args]
  ;; based on https://github.com/metosin/bat-test/blob/636a9964b02d4b4e5665fa83fea799fcc12e6f5f/src/leiningen/bat_test.clj#L36
  (let [opts (-default-opts args)]
    (cond
      (:watch opts) (let [{:keys [watch-directories]} opts]
                      (when (:headless opts)
                        (System/setProperty "java.awt.headless" "true"))
                      (run-tests1 opts)
                      (impl/enter-key-listener opts
                                               (fn [opts]
                                                 (reset! impl/tracker nil)
                                                 (run-tests1 opts)))
                      (hawk/watch! [{:paths watch-directories
                                     :filter hawk/file?
                                     :context (constantly 0)
                                     :handler (fn [ctx e]
                                                (if (and (re-matches #"^[^.].*[.]cljc?$" (.getName (:file e)))
                                                         (< (+ ctx 1000) (System/currentTimeMillis)))
                                                  (do
                                                    (try
                                                      (println)
                                                      (run-tests1 opts)
                                                      (catch Exception e
                                                        (println e)))
                                                    (System/currentTimeMillis))
                                                  ctx))}]))
      :else (run-tests1 opts))))

(defn tests-failed? [{:keys [fail error]}]
  (pos? (+ fail error)))

(defn- -wrap-run-tests1 [args]
  (let [signal-failure? (-> args
                            -default-opts
                            run-tests1
                            tests-failed?)]
    (if (:system-exit args)
      (System/exit (if signal-failure? 1 0))
      ;; Clojure -X automatically exits cleanly, so we can make this 
      ;; more generalizable by avoiding System/exit.
      ;; https://clojure.atlassian.net/browse/TDEPS-198
      ;; see also: https://github.com/cognitect-labs/test-runner/commit/23771f4bee77d4e938b9bfa89031d2822b4e2622
      (when signal-failure?
        (throw (ex-info "Test failures or errors occurred." {}))))))

(defn massage-cli-args
  "Reduce the escaping necessary on the command line."
  [args]
  (-> args
      (set/rename-keys {:capture-output :capture-output?
                        :parallel :parallel?})))

(defn test
  "Run tests with some useful defaults for REPL usage.

  If :watch is false and tests fail or error, throws an exception (see also `:system-exit` option).

  Supports the same options of `metosin.bat-test.cli/run-tests`, except
  for the following differences:
  - :parallel        Alias for `:parallel?`
  - :capture-output  Alias for `:capture-output?`
  - :system-exit  If true and :watch is not true, exit via System/exit (code 0 if tests pass, 1 on failure).
                  If not true and :watch is not true, throw an exception if tests fail.
                  Default: nil"
  [args]
  (let [args (massage-cli-args args)]
    (if (:watch args)
      (run-tests args)
      (-wrap-run-tests1 args))))

(def -default-exec-args
  {:headless true
   :enter-key-listener true
   :system-exit true})

(defn exec
  "Run tests via Clojure CLI's -X flag.

  Setup:
  eg., deps.edn: {:aliases {:test {:exec-fn metosin.bat-test.cli/exec}}}
       $ clojure -X:test :test-matcher-directories '[\"submodule\"]'
  
  Supports the same options of `metosin.bat-test.cli/test`, except
  for the following differences:
  - :headless        Defaults to true.
  - :enter-key-listener Defaults to true.
  - :system-exit  Defaults to true for cleaner output."
  [args]
  (-> -default-exec-args
      (into args)
      test))
