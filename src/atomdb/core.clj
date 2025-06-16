(ns atomdb.core
  (:require [atomdb.cache.lru :as lru]
            [atomdb.store :as store]
            [atomdb.store.memory :as memory]
            [atomdb.data.store-map]
            [atomdb.data.store-set]
            [atomdb.data.store-list]
            [atomdb.data.store-vector]
            [clojure.test :refer :all])
  (:import (clojure.lang IAtom IDeref)))

;; AtomDB - An in-process, content-addressable, immutable, pluggable database
;; designed with Clojure's functional data principles in mind.

;; Default configuration
(def default-config
  {:store-type :memory
   :cache-type :lru
   :cache-size 1000
   :file-path "atomdb-data"})

(defprotocol IAtomDB
  "Protocol for AtomDB operations"
  (get-store [this] "Returns the store used by this database")
  (get-cache [this] "Returns the cache used by this database")
  (get-root-hash [this] "Returns the current root hash of the database"))

(deftype AtomDB [config store cache root-hash-atom]
  IAtomDB
  (get-store [_] store)
  (get-cache [_] cache)
  (get-root-hash [_] @root-hash-atom)

  IDeref
  (deref [this]
    (let [root-hash @root-hash-atom]
      (when root-hash
        (let [root-node (store/get-chunk store root-hash)
              data (store/load-node store root-node)]
          data))))

  IAtom
  (swap [this f]
    (let [current @this
          new-value (f current)
          new-hash (store/persist store new-value)]
      (reset! root-hash-atom new-hash)
      new-value))

  (swap [this f arg]
    (let [current @this
          new-value (f current arg)
          new-hash (store/persist store new-value)]
      (reset! root-hash-atom new-hash)
      new-value))

  (swap [this f arg1 arg2]
    (let [current @this
          new-value (f current arg1 arg2)
          new-hash (store/persist store new-value)]
      (reset! root-hash-atom new-hash)
      new-value))

  (swap [this f arg1 arg2 args]
    (let [current @this
          new-value (apply f current arg1 arg2 args)
          new-hash (store/persist store new-value)]
      (reset! root-hash-atom new-hash)
      new-value))

  (reset [this new-value]
    (let [new-hash (store/persist store new-value)]
      (reset! root-hash-atom new-hash)
      new-value))

  (compareAndSet [this old-value new-value]
    (let [current @this]
      (if (= current old-value)
        (do
          (let [new-hash (store/persist store new-value)]
            (reset! root-hash-atom new-hash))
          true)
        false))))


(defn db
  "Creates a new AtomDB instance with the given configuration.

   Options:
   :store - Store implementation (must implement ChunkStore protocol)
   :cache - Cache implementation (must implement Cache protocol)
   :init - Initial data to populate the database with

   If :store is not provided, a memory store will be used.
   If :cache is not provided, an LRU cache will be used.

   Example:
   ```
   (require '[atomdb.store.file :as file]
            '[atomdb.cache.lru :as lru]
            '[atomdb.serde.edn :as serde.edn])

   (def db (db {:store (file/file-store \"/path/to/data\" (serde.edn/edn-serde))
                :cache (lru/lru-cache 10000)
                :init {:users {...}}}))
   ```"
  ([]
   (db {}))
  ([options]
   (let [store (or (:store options) (memory/->MemoryChunkStore (atom {})))
         cache (or (:cache options) (lru/lru-cache 1000))
         root-hash-atom (atom nil)]
     ;; Initialize with data if provided
     (when-let [init-data (:init options)]
       (let [hash (store/persist store init-data)]
         (reset! root-hash-atom hash)))
     (->AtomDB options store cache root-hash-atom))))
