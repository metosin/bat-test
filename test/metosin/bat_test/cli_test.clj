(ns ^:eftest/synchronized metosin.bat-test.cli-test
  "doseq is used to parallelize tests and install bat-test jar.
  Always wrap assertions about the shell in a doseq for reliability."
  (:refer-clojure :exclude [doseq])
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [metosin.bat-test.cli :as cli]
            [clojure.java.shell :as sh]))

(defn install-bat-test-jar []
  (:out (sh/sh "clojure" "-T:build" "install")))

(def ^:dynamic *bat-test-jar-version* nil)

;; compile-time flag
(def parallel? true)

(defmacro doseq
  "Parallel doseq via pmap.

  If parallel? is false, like clojure.core/doseq.

  argv must be pure"
  [argv & body]
  (if parallel?
    `(binding [;; install jar synchronously
               *bat-test-jar-version* (install-bat-test-jar)]
       (dorun
         (pmap
           (fn [f#] (f#))
           (doto (for ~argv
                   ;; `let` to avoid recur target
                   (fn [] (let [res# (do ~@body)] res#)))
             (-> seq assert)))))
    `(clojure.core/doseq ~argv ~@body)))

(defn prep-exec-cmds
  ([cmd] (prep-exec-cmds #{:cli :lein} cmd))
  ([impls cmd]
   {:post [(seq %)]}
   (cond-> []
     (:cli impls) (conj (into ["clojure" "-X:test"] cmd)
                        (into ["clojure" "-M:test" ":"] cmd))
     (:lein impls) (conj (into ["lein" "bat-test" ":"] cmd)))))

(defn prep-main-cmds
  ([cmd] (prep-main-cmds #{:cli :lein} cmd))
  ([impls cmd]
   {:post [(seq %)]}
   (cond-> []
     (:cli impls) (conj (into ["clojure" "-M:test"] cmd))
     (:lein impls) (conj (into ["lein" "bat-test"] cmd)))))

(defn sh-in-dir [dir cmd]
  (apply sh/sh (concat cmd [:dir dir
                            :env (-> (into {} (System/getenv))
                                     (assoc ;; https://github.com/technomancy/leiningen/issues/2611
                                            "LEIN_JVM_OPTS" ""
                                            ;; jvm 17 support for fipp https://github.com/brandonbloom/fipp/issues/60
                                            "LEIN_USE_BOOTCLASSPATH" "no")
                                     (cond->
                                       *bat-test-jar-version* (assoc "INSTALLED_BAT_TEST_VERSION" *bat-test-jar-version*)))])))

(def sh-in-cli-fail (partial #'sh-in-dir "test-projects/cli-fail"))

(deftest cli-fail-test-all-tests
  ;; different ways of running all tests
  (doseq [cmd (-> []
                  (into (prep-exec-cmds []))
                  (into (prep-exec-cmds #{:cli} [":system-exit" "false"]))
                  (into (prep-exec-cmds [":test-dirs" "[\"test-pass\" \"test-fail\"]"]))
                  (into (prep-exec-cmds [":selectors" "[cli-fail.test-fail cli-fail.test-pass]"]))
                  ;; selectors from test-selectors.clj
                  (into (prep-exec-cmds [":selectors" "[:just-passing :just-failing]"]))
                  (into (prep-exec-cmds [":selectors" "[:just-failing :only cli-fail.test-pass/i-pass]"]))
                  (into (prep-main-cmds [":just-passing" ":just-failing"]))
                  (into (prep-main-cmds [":just-passing" ":" ":selectors" "[:just-failing]"]))
                  ;; :only with 2 args
                  (into (prep-exec-cmds [":selectors" "[:only cli-fail.test-fail/i-fail cli-fail.test-pass]"]))
                  ;; combine :only and :selectors
                  (into (prep-main-cmds [":only" "cli-fail.test-fail/i-fail" ":just-passing"]))
                  (into (prep-main-cmds [":only" "cli-fail.test-fail/i-fail" ":" ":selectors" "[:just-passing]"]))
                  (into (prep-main-cmds [":just-passing" ":" ":selectors" "[:only cli-fail.test-fail/i-fail]"])))]
    (testing (pr-str cmd)
      (let [{:keys [exit out] :as res} (sh-in-cli-fail cmd)]
        (is (= 1 exit) (pr-str res))
        (is (str/includes? out "Ran 2 tests") (pr-str res))
        (is (str/includes? out "2 assertions, 1 failure, 0 errors") (pr-str res))))))

(deftest cli-fail-test-just-fail
  ;; different ways of just running `cli-fail.test-fail/i-fail`
  (doseq [cmd (-> []
                  (into (prep-exec-cmds [":test-dirs" "\"test-fail\""]))
                  (into (prep-exec-cmds [":test-dirs" "[\"test-fail\"]"]))
                  (into (prep-exec-cmds [":selectors" "[cli-fail.test-fail]"]))
                  (into (prep-exec-cmds [":selectors" "[:just-failing]"]))
                  (into (prep-exec-cmds [":selectors" "[:only cli-fail.test-fail/i-fail]"]))
                  (into (prep-exec-cmds [":test-matcher" "\"cli-fail.test-fail\""])))]
    (testing (pr-str cmd)
      (let [{:keys [exit out] :as res} (sh-in-cli-fail cmd)]
        (is (= 1 exit) (pr-str res))
        (is (str/includes? out "Ran 1 tests") (pr-str res))
        (is (str/includes? out "1 assertion, 1 failure, 0 errors") (pr-str res))))))

(deftest cli-fail-test-just-pass
  ;; different ways of just running `cli-fail.test-pass/i-pass`
  (doseq [cmd (-> []
                  (into (prep-exec-cmds [":test-dirs" "\"test-pass\""]))
                  (into (prep-exec-cmds [":test-dirs" "[\"test-pass\"]"]))
                  (into (prep-exec-cmds [":selectors" "[cli-fail.test-pass]"]))
                  (into (prep-exec-cmds [":selectors" "[:just-passing]"]))
                  (into (prep-exec-cmds [":selectors" "[:only cli-fail.test-pass/i-pass]"]))
                  (into (prep-exec-cmds [":test-matcher" "\"cli-fail.test-pass\""]))
                  (into (prep-exec-cmds [":test-matcher" "\".*-pass\""])))]
    (testing (pr-str cmd)
      (let [{:keys [exit out] :as res} (sh-in-cli-fail cmd)]
        (is (= 0 exit) (pr-str res))
        (is (str/includes? out "Ran 1 tests") (pr-str res))
        (is (str/includes? out "1 assertion, 0 failures, 0 errors") (pr-str res))))))

(deftest cli-fail-test-no-tests
  ;; different ways of running no tests
  (doseq [cmd (-> []
                  ;; namespace before :only a conjunction
                  (into (prep-exec-cmds [":selectors" "[cli-fail.test-pass :only cli-fail.test-fail/i-fail]"]))
                  ;; same, but via main
                  (into (prep-main-cmds ["cli-fail.test-pass" ":only" "cli-fail.test-fail/i-fail"])))]
    (testing (pr-str cmd)
      (let [{:keys [exit out] :as res} (sh-in-cli-fail cmd)]
        (is (= 0 exit) (pr-str res))
        (is (str/includes? out "No tests found.") (pr-str res))))))

(deftest cli-fail-test-clojure-test-reporter
  ;; clojure.test/report reporter
  (doseq [cmd (-> []
                  (into (prep-exec-cmds [":report" "[clojure.test/report]"])))]
    (testing (pr-str cmd)
      (let [{:keys [exit out] :as res} (sh-in-cli-fail cmd)]
        (is (= 1 exit) (pr-str res))
        (is (str/includes? out "Testing cli-fail.test-fail") (pr-str res))
        (is (str/includes? out "FAIL in (i-fail)") (pr-str res))
        (is (str/includes? out "Testing cli-fail.test-pass") (pr-str res))
        (is (str/includes? out "Ran 2 tests containing 2 assertions") (pr-str res))
        (is (str/includes? out "1 failures, 0 errors") (pr-str res))))))

(deftest cli-fail-test-test-dirs-loading
  ;; :test-dirs influences which namespaces are initially loaded
  (doseq [{:keys [cmds pass-loaded?] :as test-case} [{:desc "Load all namespaces"
                                                      :pass-loaded? true
                                                      :cmds (prep-exec-cmds [":report" "[clojure.test/report]"])}
                                                     {:desc "Don't load cli-fail.test-pass"
                                                      :pass-loaded? false
                                                      :cmds (prep-exec-cmds [":report" "[clojure.test/report]" ":test-dirs" "[\"test-fail\"]"])}]
          :let [_ (assert (seq cmds))]
          cmd cmds]
    (testing (pr-str test-case)
      (let [{:keys [exit out] :as res} (sh-in-cli-fail cmd)]
        (is (= 1 exit) (pr-str res))
        (is (str/includes? out (str "cli-fail.test-pass was" (when-not pass-loaded? " not") " loaded.")) (pr-str res))
        (is (str/includes? out (format "Ran %s tests" (if pass-loaded? 2 1))) (pr-str res))
        (is (str/includes? out "1 failures, 0 errors") (pr-str res))))))

(deftest cli-no-tests-test
  ;; check that there are no tests
  (doseq [cmd (-> []
                  (into (prep-exec-cmds [])))]
    (testing (pr-str cmd)
      (let [{:keys [exit out] :as res} (sh-in-dir "test-projects/cli-no-tests" cmd)]
        (is (= 0 exit) (pr-str res))
        (is (str/includes? out "No tests found") (pr-str res))))))
