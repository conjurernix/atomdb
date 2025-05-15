(ns atomdb.store.file
  (:require [atomdb.store :as store]
            [atomdb.utils :as u]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defrecord FileChunkStore [dir]
  store/ChunkStore
  (put-chunk! [_ data]
    (let [s (pr-str data)
          h (u/sha256 s)
          path (io/file dir (subs h 0 2) (subs h 2))]
      (when-not (.exists path)
        (.mkdirs (.getParentFile path))
        (spit path s))
      h))
  (get-chunk [_ hash]
    (let [path (io/file dir (subs hash 0 2) (subs hash 2))]
      (when (.exists path)
        (edn/read-string (slurp path))))))

(defn file-store [dir]
  (->FileChunkStore dir))
