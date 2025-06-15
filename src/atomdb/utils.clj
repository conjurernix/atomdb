(ns atomdb.utils
  "Utility functions for AtomDB.

   This namespace provides utility functions used throughout the AtomDB
   library, such as hashing functions for content-addressable storage."
  (:import (java.security MessageDigest)))

(defn sha256
  "Computes the SHA-256 hash of a value as a hexadecimal string.

   This function is used to generate content-addressable hashes for
   storing and retrieving data in AtomDB. The value is first converted
   to a string using pr-str, then hashed using SHA-256, and finally
   converted to a hexadecimal string.

   Parameters:
   - s: The value to hash

   Returns:
   - A 64-character hexadecimal string representing the SHA-256 hash"
  [s]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (->> (.digest digest (.getBytes (pr-str s) "UTF-8"))
         (map #(format "%02x" %))
         (apply str))))

(defprotocol StoreDataStructure
  (to-clj [this] "Converts the AtomDB StoreDataStructure into its Clojure core data structure equivalent"))

(defn ->clj [x]
  (when (some? x)
    (if (satisfies? StoreDataStructure x)
      (to-clj x)
      x)))