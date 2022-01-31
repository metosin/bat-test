(ns cli-fail.test-fail
  (:require [clojure.test :as t]))

(t/deftest ^:fail i-fail
  (t/is nil
        (str "cli-fail.test-pass was" (when-not (find-ns 'cli-fail.test-pass) " not") " loaded.")))
