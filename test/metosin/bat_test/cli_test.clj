(ns metosin.bat-test.cli-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [metosin.bat-test.cli :as cli]
            [clojure.java.shell :as sh]))

(deftest cli-fail-test
  (let [sh #(apply sh/sh (concat % [:dir "test-projects/cli-fail"]))]
    ;; different ways of running all tests
    (doseq [cmd [["clojure" "-X:test"]
                 ["clojure" "-X:test" ":system-exit" "true"]
                 ["clojure" "-X:test" ":test-matcher-directories" "[\"test-pass\" \"test-fail\"]"]
                 ["clojure" "-X:test" ":selectors" "[cli-fail.test-fail cli-fail.test-pass]"]
                 ;; selectors from test-selectors.clj
                 ["clojure" "-X:test" ":selectors" "[:just-passing :just-failing]"]
                 ["clojure" "-X:test" ":selectors" "[:just-failing :only cli-fail.test-pass/i-pass]"]
                 ;; combine :only and :selectors
                 ["clojure" "-X:test" ":only" "cli-fail.test-fail/i-fail" ":selectors" "[cli-fail.test-pass]"]
                 ["clojure" "-X:test" ":only" "cli-fail.test-fail/i-fail" ":selectors" "[:just-passing]"]]]
      (testing (pr-str cmd)
        (let [{:keys [exit out] :as res} (sh cmd)]
          (is (= 1 exit) (pr-str res))
          (is (str/includes? out "Ran 2 tests") (pr-str res))
          (is (str/includes? out "2 assertions, 1 failure, 0 errors") (pr-str res)))))
    ;; different ways of just running `cli-fail.test-fail/i-fail`
    (doseq [cmd [["clojure" "-X:test" ":test-matcher-directories" "[\"test-fail\"]"]
                 ["clojure" "-X:test" ":test-matcher-directories" "[\"test-fail\"]" ":system-exit" "true"]
                 ["clojure" "-X:test" ":selectors" "[cli-fail.test-fail]"]
                 ["clojure" "-X:test" ":selectors" "[:just-failing]"]
                 ["clojure" "-X:test" ":selectors" "[:only cli-fail.test-fail/i-fail]"]
                 ["clojure" "-X:test" ":test-matcher" "\"cli-fail.test-fail\""]]]
      (testing (pr-str cmd)
        (let [{:keys [exit out] :as res} (sh cmd)]
          (is (= 1 exit) (pr-str res))
          (is (str/includes? out "Ran 1 tests") (pr-str res))
          (is (str/includes? out "1 assertion, 1 failure, 0 errors") (pr-str res)))))
    ;; different ways of just running `cli-fail.test-pass/i-pass`
    (doseq [cmd [["clojure" "-X:test" ":test-matcher-directories" "[\"test-pass\"]"]
                 ["clojure" "-X:test" ":test-matcher-directories" "[\"test-pass\"]" ":system-exit" "true"]
                 ["clojure" "-X:test" ":selectors" "[cli-fail.test-pass]"]
                 ["clojure" "-X:test" ":selectors" "[:just-passing]"]
                 ["clojure" "-X:test" ":selectors" "[:only cli-fail.test-pass/i-pass]"]
                 ["clojure" "-X:test" ":test-matcher" "\"cli-fail.test-pass\""]
                 ["clojure" "-X:test" ":test-matcher" "\".*-pass\""]]]
      (testing (pr-str cmd)
        (let [{:keys [exit out] :as res} (sh cmd)]
          (is (= 0 exit) (pr-str res))
          (is (str/includes? out "Ran 1 tests") (pr-str res))
          (is (str/includes? out "1 assertion, 0 failures, 0 errors") (pr-str res)))))
    ;; clojure.test/report reporter
    (let [cmd ["clojure" "-X:test" ":report" "[clojure.test/report]"]]
      (testing (pr-str cmd)
        (let [{:keys [exit out] :as res} (sh cmd)]
          (is (= 1 exit) (pr-str res))
          (is (str/includes? out "Testing cli-fail.test-fail") (pr-str res))
          (is (str/includes? out "FAIL in (i-fail)") (pr-str res))
          (is (str/includes? out "Testing cli-fail.test-pass") (pr-str res))
          (is (str/includes? out "Ran 2 tests containing 2 assertions") (pr-str res))
          (is (str/includes? out "1 failures, 0 errors") (pr-str res)))))
    ;; :test-matcher-directories influences which namespaces are initially loaded
    (doseq [{:keys [cmd pass-loaded?] :as test-case} [{:desc "Load all namespaces"
                                                       :pass-loaded? true
                                                       :cmd ["clojure" "-X:test" ":report" "[clojure.test/report]"]}
                                                      {:desc "Don't load cli-fail.test-pass"
                                                       :pass-loaded? false
                                                       :cmd ["clojure" "-X:test" ":report" "[clojure.test/report]" ":test-matcher-directories" "[\"test-fail\"]"]}]]
      (testing (pr-str test-case)
        (let [{:keys [exit out] :as res} (sh cmd)]
          (is (= 1 exit) (pr-str res))
          (is (str/includes? out (str "cli-fail.test-pass was" (when-not pass-loaded? " not") " loaded.")) (pr-str res))
          (is (str/includes? out (format "Ran %s tests" (if pass-loaded? 2 1))) (pr-str res))
          (is (str/includes? out "1 failures, 0 errors") (pr-str res)))))))

(deftest cli-no-tests-test
  (let [sh #(apply sh/sh (concat % [:dir "test-projects/cli-no-tests"]))]
    ;; check that there are no tests
    (let [cmd ["clojure" "-X:test"]]
      (testing (pr-str cmd)
        (let [{:keys [exit out] :as res} (sh cmd)]
          (is (= 0 exit) (pr-str res))
          (is (str/includes? out "No tests found") (pr-str res)))))))
