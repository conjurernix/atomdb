(ns atomdb.serde.edn
  (:require [atomdb.serde :as serde]
            [clojure.edn :as edn]))

(defrecord EdnSerde []
  serde/Serde
  (serialize [_ value] (.getBytes (pr-str value) "UTF-8"))
  (deserialize [_ bin] (edn/read-string (String. bin "UTF-8"))))

(defn edn-serde [] (->EdnSerde))
