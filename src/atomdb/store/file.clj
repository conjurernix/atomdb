(ns atomdb.store.file
  (:require [atomdb.serde :as serde]
            [atomdb.serde.edn :as serde.edn]
            [atomdb.store :as store]
            [atomdb.utils :as u]
            [clojure.java.io :as io])
  (:import (java.io ByteArrayOutputStream OutputStream)))

(defrecord FileChunkStore [dir serde]
  store/ChunkStore
  (put-chunk! [_ data]
    (let [s (serde/serialize serde data)
          h (u/sha256 s)
          path (io/file dir (subs h 0 2) (subs h 2))]
      (.mkdirs (.getParentFile path))
      (with-open [out (io/output-stream path)]
        (.write ^OutputStream out ^bytes s))
      h))
  (get-chunk [_ hash]
    (let [path (io/file dir (subs hash 0 2) (subs hash 2))]
      (when (.exists path)
        (with-open [in (io/input-stream path)
                    baos (ByteArrayOutputStream.)]
          (io/copy in baos)
          (serde/deserialize serde (.toByteArray baos)))))))

(defn file-store
  "Creates a new file-based chunk store that persists data to the filesystem.

   Arguments:
   - dir: Directory path where chunks will be stored
   - serde: Serializer/deserializer implementation (must implement Serde protocol)

   If serde is not provided, an EDN serializer will be used by default.

   The file store organizes chunks in a two-level directory structure based on
   the hash of the serialized data for efficient lookup.

   Example:
   ```
   (require '[atomdb.store.file :as file]
            '[atomdb.serde.fressian :as serde.fressian])

   (def store (file/file-store \"/path/to/data\" (serde.fressian/fressian-serde)))
   ```"
  ([dir]
   (file-store dir (serde.edn/edn-serde)))
  ([dir serde]
   (->FileChunkStore dir serde)))
