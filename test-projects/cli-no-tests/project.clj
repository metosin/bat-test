(def bat-test-version
  (or (System/getenv "INSTALLED_BAT_TEST_VERSION")
      (:out ((requiring-resolve 'clojure.java.shell/sh)
             "clojure" "-T:build" "install"
             :dir "../.."))))
(defproject metosin-test/cli-no-tests "1.0.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.10.3"]]
  :plugins [[metosin/bat-test ~bat-test-version]
            [lein-pprint "1.3.2"]])
