(ns atomdb.serde.fressian
  (:require [atomdb.serde :as serde]
            [clojure.data.fressian :as fress])
  (:import (java.io ByteArrayInputStream)))

(defrecord FressianSerde []
  serde/Serde
  (serialize [_ x] (.array (fress/write x)))

  (deserialize [_ bytes]
    (fress/read (ByteArrayInputStream. bytes))))

(defn fressian-serde
  "Creates a new Fressian serializer/deserializer for AtomDB.

   This serde implementation uses Clojure's data.fressian library
   to serialize and deserialize data structures. Fressian is a binary
   format that provides better performance and smaller size compared to
   EDN, while still maintaining good compatibility with Clojure data structures.

   Example:
   ```
   (require '[atomdb.serde.fressian :as serde.fressian]
            '[atomdb.store.file :as file])

   (def store (file/file-store \"/path/to/data\" (serde.fressian/fressian-serde)))
   ```"
  []
  (->FressianSerde))
