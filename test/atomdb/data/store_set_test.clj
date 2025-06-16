(ns atomdb.data.store-set-test
  (:require [atomdb.data.store-set :as store-set]
            [atomdb.store :as store]
            [atomdb.store.memory :as memory]
            [atomdb.utils :as u]
            [clojure.test :refer :all])
  (:import (java.util ArrayList)))

(deftest store-set-test
  (testing "Basic set operations"
    (let [store (memory/->MemoryChunkStore (atom {}))
          data #{1 2 3 4 5}
          hash (store/persist store data)
          node (store/get-chunk store hash)
          store-s (store-set/store-set store node)]

      (testing "contains? operation"
        (is (contains? store-s 1))
        (is (contains? store-s 2))
        (is (contains? store-s 3))
        (is (contains? store-s 4))
        (is (contains? store-s 5))
        (is (not (contains? store-s 6))))

      (testing "get operation"
        (is (= 1 (get store-s 1)))
        (is (= 2 (get store-s 2)))
        (is (= 3 (get store-s 3)))
        (is (nil? (get store-s 6))))

      (testing "get operation (via IFn)"
        (is (= 1 (store-s 1)))
        (is (= 2 (store-s 2)))
        (is (= 3 (store-s 3)))
        (is (nil? (store-s 99))))

      (testing "count operation"
        (is (= 5 (count store-s))))

      (testing "seq operation"
        (is (= #{1 2 3 4 5} (set (seq store-s)))))

      (testing "conj operation"
        (let [new-s (conj store-s 6)]
          (is (= 6 (count new-s)))
          (is (contains? new-s 6))
          (is (contains? new-s 1))))

      (testing "disj operation"
        (let [new-s (disj store-s 3)]
          (is (= 4 (count new-s)))
          (is (not (contains? new-s 3)))
          (is (contains? new-s 1))
          (is (contains? new-s 2))))

      (testing "empty operation"
        (let [empty-s (empty store-s)]
          (is (= 0 (count empty-s)))
          (is (= #{} empty-s))))

      (testing "equiv operation"
        (is (.equiv store-s #{1 2 3 4 5}))
        (is (not (.equiv store-s #{1 2 3})))
        (is (not (.equiv store-s #{1 2 3 4 5 6}))))

      (testing "meta operations"
        (let [meta-s (with-meta store-s {:test true})
              meta-data (meta meta-s)]
          (is (= true (:test meta-data)))))

      (testing "set conversion"
        (is (= #{1 2 3 4 5} (u/->clj store-s)))))))

(deftest lazy-loading-test
  (testing "Values are loaded lazily"
    (let [store-atom (atom {})
          store (memory/->MemoryChunkStore store-atom)
          data #{1 {:x 10 :y 20} [1 2 3] "hello" 42}
          hash (store/persist store data)
          node (store/get-chunk store hash)
          ;; Create a CountingStore to track get-chunk calls
          count-atom (atom 0)
          counting-store (reify store/ChunkStore
                           (put-chunk! [_ data]
                             (store/put-chunk! store data))
                           (get-chunk [_ hash]
                             (swap! count-atom inc)
                             (let [result (store/get-chunk store hash)]
                               result)))
          store-s (store-set/store-set counting-store node)]

      ;; Just creating the set shouldn't load any values
      (is (= 0 @count-atom))

      ;; Getting an element should load that value
      (get store-s 1)
      (let [calls-1 @count-atom]
        (is (> calls-1 0))

        ;; Getting the same element again should not load anything (cached)
        (get store-s 1)
        (is (= calls-1 @count-atom))

        ;; Getting another element should load that value
        (get store-s {:x 10 :y 20})
        (let [calls-2 @count-atom]
          (is (> calls-2 calls-1))

          ;; Getting another element should load that value
          (get store-s [1 2 3])
          (let [calls-3 @count-atom]
            (is (> calls-3 calls-2))))))))

(deftest nested-structures-test
  (testing "Nested structures work correctly"
    (let [store (memory/->MemoryChunkStore (atom {}))
          data #{1 {:a 10} [100 200]}
          hash (store/persist store data)
          node (store/get-chunk store hash)
          store-s (store-set/store-set store node)]

      (testing "Simple value access"
        (is (= 1 (get store-s 1))))

      (testing "Nested map access"
        (let [nested-map (get store-s {:a 10})]
          (is (= 10 (get nested-map :a)))))

      (testing "Nested vector access"
        (let [nested-vec (get store-s [100 200])]
          (is (= 100 (nth nested-vec 0)))
          (is (= 200 (nth nested-vec 1))))))))

(deftest java-set-interface-test
  (testing "Java Set interface methods"
    (let [store (memory/->MemoryChunkStore (atom {}))
          data #{1 2 3}
          hash (store/persist store data)
          node (store/get-chunk store hash)
          store-s (store-set/store-set store node)]

      (testing "size and isEmpty"
        (is (= 3 (.size store-s)))
        (is (not (.isEmpty store-s)))

        (let [empty-s (store-set/store-set store {:type :set :children {}})]
          (is (.isEmpty empty-s))))

      (testing "contains"
        (is (.contains store-s 2))
        (is (not (.contains store-s 99))))

      (testing "toArray"
        (is (= #{1 2 3} (set (vec (.toArray store-s)))))

        (let [arr (make-array Object 5)]
          (let [result (.toArray store-s arr)]
            (is (= arr result))
            (is (= 3 (count (filter some? (vec result)))))))

        (let [arr (make-array Object 2)]
          (let [result (.toArray store-s arr)]
            (is (not= arr result))
            (is (= 3 (count (filter some? (vec result))))))))

      (testing "containsAll"
        (is (.containsAll store-s [1 2]))
        (is (.containsAll store-s #{1 2}))
        (is (not (.containsAll store-s [1 2 99]))))

      (testing "iterator"
        (let [iter (.iterator store-s)
              elements (loop [result #{}]
                         (if (.hasNext iter)
                           (recur (conj result (.next iter)))
                           result))]
          (is (= #{1 2 3} elements))))

      (testing "unsupported operations"
        (is (thrown? UnsupportedOperationException (.add store-s 4)))
        (is (thrown? UnsupportedOperationException (.remove store-s 1)))
        (is (thrown? UnsupportedOperationException (.addAll store-s [4 5])))
        (is (thrown? UnsupportedOperationException (.retainAll store-s [1])))
        (is (thrown? UnsupportedOperationException (.removeAll store-s [1])))
        (is (thrown? UnsupportedOperationException (.clear store-s)))
        (is (thrown? UnsupportedOperationException 
              (let [iter (.iterator store-s)]
                (.next iter)
                (.remove iter))))))))

(deftest equality-test
  (testing "Set equality"
    (let [store (memory/->MemoryChunkStore (atom {}))
          data1 #{1 2 3}
          data2 #{1 2 3}
          data3 #{1 2 3 4}
          hash1 (store/persist store data1)
          hash2 (store/persist store data2)
          hash3 (store/persist store data3)
          node1 (store/get-chunk store hash1)
          node2 (store/get-chunk store hash2)
          node3 (store/get-chunk store hash3)
          store-s1 (store-set/store-set store node1)
          store-s2 (store-set/store-set store node2)
          store-s3 (store-set/store-set store node3)]

      (testing "equals and hashCode"
        (is (= store-s1 store-s2))
        (is (not= store-s1 store-s3))
        (is (= (hash store-s1) (hash store-s2)))
        (is (not= (hash store-s1) (hash store-s3)))))))

(deftest persistence-test
  (testing "Persistence and loading"
    (let [store (memory/->MemoryChunkStore (atom {}))
          data #{1 2 3 4 5}
          hash (store/persist store data)
          loaded-data (store/load-node store (store/get-chunk store hash))]

      (testing "Loaded data is a StoreSet"
        (is (instance? atomdb.data.store_set.StoreSet loaded-data)))

      (testing "Loaded data has correct elements"
        (is (= 5 (count loaded-data)))
        (is (contains? loaded-data 1))
        (is (contains? loaded-data 5))
        (is (not (contains? loaded-data 6)))))))
