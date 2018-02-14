(ns metosin.bat-test.cloverage
  (:require [clojure.set :as set]
            [clojure.java.io :as io]
            [cloverage.coverage :as cov]

            [cloverage.debug :as debug]
            ; [cloverage.dependency :as dep]
            [cloverage.instrument :as inst]
            [cloverage.report :as rep]
            [cloverage.report.console :as console]
            [cloverage.report.coveralls :as coveralls]
            [cloverage.report.codecov :as codecov]
            [cloverage.report.emma-xml :as emma-xml]
            [cloverage.report.html :as html]
            [cloverage.report.lcov :as lcov]
            [cloverage.report.raw :as raw]
            [cloverage.report.text :as text]
            ; [cloverage.source :as src]
            ))

(defn wrap-cloverage
  "Produce test coverage report for some namespaces"
  [namespaces opts run-tests]
  (let [ns-matcher      (:ns-matcher opts)
        namespaces      (cond->> namespaces
                          ns-matcher (filter #(re-matches ns-matcher (name %))))

        ^String output  (:output opts "target/coverage")
        text?           (:text opts false)
        html?           (:html opts true)
        raw?            (:raw opts false)
        emma-xml?       (:emma-xml opts false)
        lcov?           (:lcov opts false)
        codecov?        (:codecov opts false)
        coveralls?      (:coveralls opts false)
        summary?        (:summary opts true)
        fail-threshold  (:fail-threshold opts 0)
        low-watermark   (:low-watermark opts 50)
        high-watermark  (:high-watermark opts 80)
        debug?          (:debug opts false)
        nops?           (:nop opts false)]

    (binding [*ns* (find-ns 'cloverage.coverage)
              debug/*debug* debug?]

      (doseq [namespace namespaces]
        (binding [cov/*instrumented-ns* namespace]
          (if nops?
            (inst/instrument #'inst/nop namespace)
            (inst/instrument #'cov/track-coverage namespace)))

        ;; mark the ns as loaded
        (cov/mark-loaded namespace))

      (let [test-result (run-tests)
            forms       (rep/gather-stats @cov/*covered*)]

        (when output
          (.mkdirs (io/file output))
          (when text? (text/report output forms))
          (when html? (html/report output forms))
          (when emma-xml? (emma-xml/report output forms))
          (when lcov? (lcov/report output forms))
          (when raw? (raw/report output forms @cov/*covered*))
          (when codecov? (codecov/report output forms))
          (when coveralls? (coveralls/report output forms))
          (when summary? (console/summary forms low-watermark high-watermark)))

        test-result))))
