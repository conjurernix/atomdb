(ns atomdb.cache.noop
  (:require [atomdb.cache :refer [Cache]]))

(defrecord NoOpCache []
  Cache
  (cache-get [_ _] nil)
  (cache-put [_ _ _] nil))

(defn no-op-cache
  "Creates a new no-operation cache for AtomDB.

   The no-op cache doesn't actually cache anything - all cache lookups
   will miss and all cache puts are ignored. This is useful when you
   want to disable caching entirely, for example in testing scenarios
   or when memory usage is a concern.

   Example:
   ```
   (require '[atomdb.cache.noop :as noop]
            '[atomdb.core :as atomdb])

   (def db (atomdb/db {:cache (noop/no-op-cache)}))
   ```"
  []
  (->NoOpCache))
