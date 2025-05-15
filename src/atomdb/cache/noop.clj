(ns atomdb.cache.noop
  (:require [atomdb.cache :refer [Cache]]))

(defrecord NoOpCache []
  Cache
  (cache-get [_ _] nil)
  (cache-put [_ _ _] nil))

(defn no-op-cache []
  (->NoOpCache))
