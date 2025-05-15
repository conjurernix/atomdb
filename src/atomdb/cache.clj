(ns atomdb.cache)

(defprotocol Cache
  (cache-get [cache k])
  (cache-put [cache k v]))
