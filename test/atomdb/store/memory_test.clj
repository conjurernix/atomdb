(ns atomdb.store.memory-test
  (:require [atomdb.store.test-suite :as store.suite]
            [clojure.test :refer :all]
            [atomdb.store :as store]
            [atomdb.store.memory :refer [->MemoryChunkStore]]))

(defn memory-store []
  (->MemoryChunkStore (atom {})))

(deftest memory-store-roundtrip
  (testing "stores and retrieves data correctly in memory"
    (let [ms (memory-store)
          val {:foo "bar"}
          hash (store/put-chunk! ms val)]
      (is (string? hash))
      (is (= val (store/get-chunk ms hash))))))

(deftest memory-store-nonexistent
  (testing "returns nil for nonexistent hash"
    (let [ms (memory-store)]
      (is (nil? (store/get-chunk ms "notarealhash"))))))

(deftest store-roundtrip-supported-types
  (store.suite/store-roundtrip-supported-types (memory-store)))

(deftest store-edge-case-behavior
  (store.suite/store-edge-case-behavior (memory-store)))