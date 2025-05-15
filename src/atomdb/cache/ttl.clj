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

(defn ttl-cache [ttl-ms]
  (->TTLCache ttl-ms (atom {}) (atom {})))
