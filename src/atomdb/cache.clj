(ns atomdb.cache
  "Caching functionality for AtomDB.

   This namespace defines the Cache protocol that provides caching capabilities
   for AtomDB. Different implementations of this protocol can be used to
   customize caching behavior, such as LRU (Least Recently Used), TTL
   (Time-To-Live), or no caching at all.")

(defprotocol Cache
  "Protocol for caching key-value pairs.

   Implementations of this protocol provide different caching strategies
   for AtomDB, allowing for performance optimization based on specific
   use cases."
  (cache-get 
    [cache k]
    "Retrieves a value from the cache by key.

     Returns the cached value if found, or nil if not found or expired.")
  (cache-put 
    [cache k v]
    "Stores a value in the cache with the given key.

     The implementation may choose to evict other entries based on its
     caching strategy (e.g., LRU, TTL)."))
