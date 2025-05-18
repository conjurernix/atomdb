(ns atomdb.serde.test-suite
  (:require [atomdb.serde :as serde]
            [clojure.test :refer :all])
  (:import (java.util Date UUID)))

(defn serde-roundtrip [serde]
  (doseq [v [{:a 1 :b [2 3]}
             #{:x :y}
             '(1 2 3)
             :foo
             'bar
             "hello"
             22/7
             (bigdec "1.23")
             (UUID/randomUUID)
             (Date.)
             true
             false
             nil
             {}
             []
             #{}]]
    (testing (str "EDN roundtrip for: " (pr-str v))
      (let [bytes (serde/serialize serde v)
            result (serde/deserialize serde bytes)]
        (is (= v result))))))
