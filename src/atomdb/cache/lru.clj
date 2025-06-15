(ns atomdb.cache.lru
  (:require [atomdb.cache :refer [Cache]]))

(defrecord LRUCache [capacity store order]
  Cache
  (cache-get [_ k]
    (let [v (get @store k)]
      (when v
        ;; move k to end to mark as recently used
        (swap! order (fn [o] (conj (vec (remove #{k} o)) k))))
      v))

  (cache-put [_ k v]
    (swap! store assoc k v)
    (swap! order
           (fn [o]
             (let [updated (conj (vec (remove #{k} o)) k)]
               (if (> (count updated) capacity)
                 (let [oldest (first updated)]
                   (swap! store dissoc oldest)
                   (subvec updated 1))
                 updated))))))

(defn lru-cache
  "Creates a new Least Recently Used (LRU) cache for AtomDB.

   Arguments:
   - capacity: Maximum number of items the cache can hold

   The LRU cache keeps track of the order in which items are accessed
   and evicts the least recently used items when the capacity is reached.
   This provides a good balance between memory usage and performance for
   most use cases.

   Example:
   ```
   (require '[atomdb.cache.lru :as lru]
            '[atomdb.core :as atomdb])

   (def db (atomdb/db {:cache (lru/lru-cache 10000)}))
   ```"
  [capacity]
  (->LRUCache capacity (atom {}) (atom [])))
