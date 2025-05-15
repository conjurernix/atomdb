(ns atomdb.store.memory
  (:require [atomdb.store :as store]
            [atomdb.utils :as u]
            [clojure.edn :as edn]))

(defrecord MemoryChunkStore [store]
  store/ChunkStore
  (put-chunk! [_ data]
    (let [s (pr-str data)
          h (u/sha256 s)]
      (swap! store assoc h s)
      h))
  (get-chunk [_ hash]
    (some-> (@store hash) edn/read-string)))
