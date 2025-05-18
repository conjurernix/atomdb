(ns atomdb.store.test-suite
  (:require [atomdb.store :as store]
            [clojure.test :refer :all])
  (:import (java.util Date UUID)))

(defn store-roundtrip-supported-types [store]
  (testing "persist/load-node roundtrip for core data types"
    (doseq [v [{:a 1 :b 2}
               [1 2 3]
               #{:x :y}
               '(1 2)
               :keyword
               'symbol
               "string"
               (UUID/randomUUID)
               (Date.)
               (bigdec "42.42")
               22/7
               true
               false
               nil
               {}
               []
               #{}
               '()]]
      (testing (str "roundtrip for: " (pr-str v))
        (let [h (store/persist store v)
              node (store/get-chunk store h)
              result (store/load-node store node)]
          (is (= v result)))))))

(defn store-edge-case-behavior [store]
  (testing "handles missing hash safely"
    (is (nil? (store/get-chunk store "notarealhash"))))

  (testing "persists nested structure with mixed types"
    (let [val {:a [1 #{:b (UUID/randomUUID)}]
               :c (Date.)
               :d {:inner (bigdec "99.99")}}
          h (store/persist store val)
          result (store/load-node store (store/get-chunk store h))]
      (is (= val result)))))
