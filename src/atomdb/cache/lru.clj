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

(defn lru-cache [capacity]
  (->LRUCache capacity (atom {}) (atom [])))
