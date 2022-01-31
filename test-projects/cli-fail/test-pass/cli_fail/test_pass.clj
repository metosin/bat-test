(ns cli-fail.test-pass
  (:require [clojure.test :as t]))

(t/deftest ^:pass i-pass
  (t/is true))
