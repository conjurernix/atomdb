(ns atomdb.serde.fressian
  (:require [atomdb.serde :as serde]
            [clojure.data.fressian :as fress])
  (:import (java.io ByteArrayInputStream)))

(defrecord FressianSerde []
  serde/Serde
  (serialize [_ x] (.array (fress/write x)))

  (deserialize [_ bytes]
    (fress/read (ByteArrayInputStream. bytes))))

(defn fressian-serde [] (->FressianSerde))
