(ns atomdb.serde
  "Serialization and deserialization functionality for AtomDB.

   This namespace defines the Serde protocol that provides serialization
   and deserialization capabilities for AtomDB. Different implementations
   of this protocol can be used to customize how data is converted to and
   from bytes for storage, such as EDN or Fressian.")

(defprotocol Serde
  "Protocol for serializing and deserializing data.

   Implementations of this protocol provide different serialization formats
   for AtomDB, allowing for flexibility in how data is stored and retrieved."
  (serialize 
    [this value]
    "Serializes a Clojure value to a byte array.

     Converts the given value to a format suitable for storage.")
  (deserialize 
    [this bytes]
    "Deserializes a byte array to a Clojure value.

     Converts the given bytes back to the original Clojure value."))
