(ns build
  (:require [clojure.tools.build.api :as b]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'atomdb/atomdb)
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))

;; Version management functions

(defn get-latest-git-tag
  "Retrieves the latest git tag. Returns 'v0.1.0' if no tags exist."
  []
  (let [result (sh "git" "describe" "--tags" "--abbrev=0")]
    (if (= 0 (:exit result))
      (str/trim (:out result))
      "v0.1.0"))) ; Default version if no tags exist

(defn version-from-tag
  "Extracts the version number from a git tag by removing the 'v' prefix if present."
  [tag]
  (if (str/starts-with? tag "v")
    (subs tag 1)
    tag))

(defn get-version
  "Gets the current version from the latest git tag."
  []
  (version-from-tag (get-latest-git-tag)))

(defn get-jar-file
  "Generates the jar file path based on the library name and version."
  [version]
  (format "target/%s-%s.jar" (name lib) version))

(defn clean [_opts]
  (b/delete {:path "target"}))

(defn jar [opts]
  (clean opts)
  (let [version (get-version)
        jar-file (get-jar-file version)]
    (println "Building jar" jar-file)
    (b/copy-dir {:src-dirs ["src" "resources"]
                 :target-dir class-dir})
    (b/write-pom {:class-dir class-dir
                  :lib lib
                  :version version
                  :basis basis
                  :src-dirs ["src"]})
    (b/jar {:class-dir class-dir
            :jar-file jar-file})))

(defn install [opts]
  (jar opts)
  (let [version (get-version)
        jar-file (get-jar-file version)]
    (println "Installing jar to local Maven repository")
    (b/install {:basis basis
                :lib lib
                :version version
                :jar-file jar-file
                :class-dir class-dir})))

(defn deploy [_opts]
  (let [version (get-version)
        jar-file (get-jar-file version)]
    (println "Deploying jar to Clojars using deps-deploy")
    (dd/deploy {:installer :remote
                :artifact jar-file
                :pom-file (str class-dir "/META-INF/maven/" (namespace lib) "/" (name lib) "/pom.xml")})))

(defn run-test [_opts]
  (println "Running tests")
  (let [result (sh "clojure" "-M:dev:test:kaocha")]
    (println (:out result))
    (when (not= 0 (:exit result))
      (println (:err result))
      (throw (ex-info "Tests failed" {:exit-code (:exit result)})))))

(defn ci [opts]
  (run-test opts)
  (jar opts)
  (deploy opts))

;; Version bumping functions

(defn parse-version
  "Parses a version string (e.g., '1.2.3') into a vector of integers [1 2 3].
   Returns [0 1 0] if the version string doesn't match the expected format."
  [version-str]
  (let [pattern #"(\d+)\.(\d+)\.(\d+)"
        matcher (re-matcher pattern version-str)]
    (if (.find matcher)
      [(Integer/parseInt (.group matcher 1))
       (Integer/parseInt (.group matcher 2))
       (Integer/parseInt (.group matcher 3))]
      [0 1 0]))) ; Default if version doesn't match pattern

(defn create-new-tag
  "Creates a new git tag with the given version (prefixed with 'v').
   Returns the version string if successful, nil otherwise."
  [new-version]
  (let [tag (str "v" new-version)
        result (sh "git" "tag" tag)]
    (if (= 0 (:exit result))
      (do
        (println "Created new tag:" tag)
        new-version)
      (do
        (println "Failed to create tag:" tag)
        (println (:err result))
        nil))))

(defn bump-version
  "Bumps the version number based on the index:
   0 = major (x.0.0), 1 = minor (x.y.0), 2 = patch (x.y.z).
   Returns the new version as a string."
  [version-parts index]
  (let [[major minor patch] version-parts
        new-parts (case index
                    0 [(inc major) 0 0]      ; Bump major, reset minor & patch
                    1 [major (inc minor) 0]  ; Bump minor, reset patch
                    2 [major minor (inc patch)] ; Bump patch
                    [major minor patch])]    ; Default: no change
    (str/join "." new-parts)))

(defn bump-major
  "Increments the major version number (x.0.0) and creates a new git tag.
   Usage: clojure -T:build bump-major"
  [_]
  (let [current-version (version-from-tag (get-latest-git-tag))
        version-parts (parse-version current-version)
        new-version (bump-version version-parts 0)]
    (create-new-tag new-version)))

(defn bump-minor
  "Increments the minor version number (x.y.0) and creates a new git tag.
   Usage: clojure -T:build bump-minor"
  [_]
  (let [current-version (version-from-tag (get-latest-git-tag))
        version-parts (parse-version current-version)
        new-version (bump-version version-parts 1)]
    (create-new-tag new-version)))

(defn bump-patch
  "Increments the patch version number (x.y.z) and creates a new git tag.
   Usage: clojure -T:build bump-patch"
  [_]
  (let [current-version (version-from-tag (get-latest-git-tag))
        version-parts (parse-version current-version)
        new-version (bump-version version-parts 2)]
    (create-new-tag new-version)))
