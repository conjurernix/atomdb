(ns atomdb.serde.edn
  (:require [atomdb.serde :as serde]
            [clojure.edn :as edn]))

(defrecord EdnSerde []
  serde/Serde
  (serialize [_ value] (.getBytes (pr-str value) "UTF-8"))
  (deserialize [_ bin] (edn/read-string (String. bin "UTF-8"))))

(defn edn-serde
  "Creates a new EDN serializer/deserializer for AtomDB.

   This serde implementation uses Clojure's built-in EDN reader/writer
   to serialize and deserialize data structures. It's the default
   serialization format for AtomDB and provides good compatibility
   with Clojure data structures.

   Example:
   ```
   (require '[atomdb.serde.edn :as serde.edn]
            '[atomdb.store.file :as file])

   (def store (file/file-store \"/path/to/data\" (serde.edn/edn-serde)))
   ```"
  [] 
  (->EdnSerde))
