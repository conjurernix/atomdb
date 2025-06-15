(ns atomdb.cache.ttl
  (:require [atomdb.cache :refer [Cache]]))

(defrecord TTLCache [ttl-ms store timestamps]
  Cache
  (cache-get [_ k]
    (let [now (System/currentTimeMillis)
          ts (get @timestamps k)]
      (when (and ts (< (- now ts) ttl-ms))
        (get @store k))))
  (cache-put [_ k v]
    (let [now (System/currentTimeMillis)]
      (swap! store assoc k v)
      (swap! timestamps assoc k now))))

(defn ttl-cache
  "Creates a new Time-To-Live (TTL) cache for AtomDB.

   Arguments:
   - ttl-ms: Time-to-live in milliseconds for cache entries

   The TTL cache automatically expires entries after they have been
   in the cache for the specified amount of time. This is useful for
   caching data that becomes stale after a certain period, or when
   you want to limit memory usage without using a fixed capacity.

   Example:
   ```
   (require '[atomdb.cache.ttl :as ttl]
            '[atomdb.core :as atomdb])

   ;; Create a cache with 5-minute TTL
   (def db (atomdb/db {:cache (ttl/ttl-cache (* 5 60 1000))}))
   ```"
  [ttl-ms]
  (->TTLCache ttl-ms (atom {}) (atom {})))
