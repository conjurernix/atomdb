(ns atomdb.utils
  (:import (java.security MessageDigest)))

(defn sha256 [s]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (->> (.digest digest (.getBytes (pr-str s) "UTF-8"))
         (map #(format "%02x" %))
         (apply str))))
