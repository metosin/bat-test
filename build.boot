(def +version+ "0.4.0-SNAPSHOT")

(set-env!
  :resource-paths #{"src"}
  :dependencies   '[[org.clojure/clojure "1.8.0" :scope "provided"]
                    [eftest "0.4.3" :scope "test"]
                    [cloverage "1.0.10" :scope "test"]
                    [org.clojure/tools.namespace "0.3.0-alpha4" :scope "test"]])

(task-options!
  pom {:project     'metosin/bat-test
       :version     +version+
       :description "Fast Clojure.test runner for Boot and Lein"
       :url         "https://github.com/metosin/bat-test"
       :scm         {:url "https://github.com/metosin/bat-test"}
       :license     {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask write-version-file
  [n namespace NAMESPACE sym "Namespace"]
  (let [d (tmp-dir!)]
    (fn [next-handler]
      (fn [fileset]
        (let [f (clojure.java.io/file d (-> (name namespace)
                                            (clojure.string/replace #"\." "/")
                                            (clojure.string/replace #"-" "_")
                                            (str ".clj")))]
          (clojure.java.io/make-parents f)
          (spit f (format "(ns %s)\n\n(def +version+ \"%s\")" (name namespace) +version+)))
        (next-handler (-> fileset (add-resource d) commit!))))))

(deftask build []
  (comp
    (write-version-file :namespace 'metosin.bat-test.version)
    (pom)
    (jar)
    (install)))

(deftask dev []
  (comp
   (watch)
   (repl :server true)
   (build)
   (target)))

(deftask deploy []
  (comp
   (build)
   (push :repo "clojars" :gpg-sign (not (.endsWith +version+ "-SNAPSHOT")))))
