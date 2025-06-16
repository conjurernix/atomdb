(ns atomdb.utils-test
  (:require [atomdb.data.store-map :as lazy-map]
            [atomdb.store :as store]
            [atomdb.store.memory :as memory]
            [atomdb.utils :as utils]
            [clojure.test :refer :all]))

(deftest ->clj-test
  (testing "->clj with objects that satisfy StoreDataStructure protocol"
    (let [store (memory/->MemoryChunkStore (atom {}))
          data {:a 1 :b 2 :c 3}
          hash (store/persist store data)
          node (store/get-chunk store hash)
          lazy-m (lazy-map/store-map store node)]

      ;; Test that lazy-map is converted to a regular map
      (is (= data (utils/->clj lazy-m)))))

  (testing "->clj with regular Clojure data structures"
    (is (= 42 (utils/->clj 42)))
    (is (= "string" (utils/->clj "string")))
    (is (= {:a 1 :b 2} (utils/->clj {:a 1 :b 2})))
    (is (= [1 2 3] (utils/->clj [1 2 3])))
    (is (= #{1 2 3} (utils/->clj #{1 2 3}))))

  (testing "->clj with nested structures"
    (let [store (memory/->MemoryChunkStore (atom {}))
          nested-data {:a {:x 1 :y 2} :b {:z 3}}
          hash (store/persist store nested-data)
          node (store/get-chunk store hash)
          lazy-m (lazy-map/store-map store node)]

      ;; Test that nested lazy-map is converted to a regular map
      (is (= nested-data (utils/->clj lazy-m))))))

(deftest sha256-test
  (testing "sha256 produces consistent hashes"
    (is (string? (utils/sha256 "test")))
    (is (= (utils/sha256 "test") (utils/sha256 "test")))
    (is (not= (utils/sha256 "test") (utils/sha256 "test2")))))
