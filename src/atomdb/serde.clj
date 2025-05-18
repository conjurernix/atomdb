(ns atomdb.serde)

(defprotocol Serde
  (serialize [this value])
  (deserialize [this bytes]))

