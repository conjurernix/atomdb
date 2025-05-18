(ns atomdb.store.file-test
  (:require [clojure.test :refer :all]
            [atomdb.store :as store]
            [atomdb.store.file :refer [file-store]]
            [atomdb.store.test-suite :as store.suite]))

(deftest file-store-roundtrip
  (testing "stores and retrieves data correctly"
    (let [fs (file-store "/tmp/atomdb-file-test")
          value {:x [1 2 3]}
          hash (store/put-chunk! fs value)]
      (is (string? hash))
      (is (= value (store/get-chunk fs hash))))))

(deftest file-store-nonexistent-hash
  (testing "returns nil for missing hash"
    (let [fs (file-store "/tmp/atomdb-file-test")]
      (is (nil? (store/get-chunk fs "deadbeefdeadbeefdeadbeefdeadbeef"))))))

(deftest store-roundtrip-supported-types
  (store.suite/store-roundtrip-supported-types (file-store "/tmp/atomdb-file-test")))

(deftest store-edge-case-behavior
  (store.suite/store-edge-case-behavior (file-store "/tmp/atomdb-file-test")))
