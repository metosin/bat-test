(ns build
  (:refer-clojure :exclude [compile])
  (:require [clojure.tools.build.api :as b]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def lib 'metosin/bat-test)
(def +version+ (format "%s.%s" (slurp "version") (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) +version+))

(defn write-version-file [d nsym]
  (let [f (io/file d (-> (name nsym)
                         (str/replace #"\." "/")
                         (str/replace #"-" "_")
                         (str ".clj")))]
    (io/make-parents f)
    (spit f (format "(ns %s)\n\n(def +version+ \"%s\")" (name nsym) +version+))))

;; operations

(defn clean [_]
  (b/delete {:path "target"}))

(defn compile [_]
  (write-version-file class-dir 'metosin.bat-test.version))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version +version+
                :basis basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (compile {})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

;; clojure -T:build version
(defn version [_] (print +version+))

;; clojure -T:build install
(defn install
  "Prints the version that was installed."
  [_]
  (clean {})
  (jar {})
  (b/install {:basis basis
              :lib lib
              :version +version+
              :class-dir class-dir
              :jar-file jar-file})
  ;; test-projects expect version to be printed
  (version {}))
