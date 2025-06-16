(ns atomdb.data.store-vector-test
  (:require [atomdb.data.store-vector :as store-vector]
            [atomdb.store :as store]
            [atomdb.store.memory :as memory]
            [atomdb.utils :as u]
            [clojure.test :refer :all]))

(deftest store-vector-test
  (testing "Basic vector operations"
    (let [store (memory/->MemoryChunkStore (atom {}))
          data [1 2 3 4 5]
          hash (store/persist store data)
          node (store/get-chunk store hash)
          store-v (store-vector/store-vector store node)]

      (testing "nth operation"
        (is (= 1 (nth store-v 0)))
        (is (= 2 (nth store-v 1)))
        (is (= 3 (nth store-v 2)))
        (is (= 4 (nth store-v 3)))
        (is (= 5 (nth store-v 4)))
        (is (nil? (nth store-v 5)))
        (is (= :not-found (nth store-v 5 :not-found))))

      (testing "get operation (via IFn)"
        (is (= 1 (store-v 0)))
        (is (= 2 (store-v 1)))
        (is (= 3 (store-v 2))))

      (testing "count operation"
        (is (= 5 (count store-v))))

      (testing "seq operation"
        (is (= [1 2 3 4 5] (seq store-v))))

      (testing "conj operation"
        (let [new-v (conj store-v 6)]
          (is (= 6 (count new-v)))
          (is (= 6 (nth new-v 5)))))

      (testing "assoc operation"
        (let [new-v (assoc store-v 2 :replaced)]
          (is (= 5 (count new-v)))
          (is (= :replaced (nth new-v 2)))
          (is (= 1 (nth new-v 0)))
          (is (= 2 (nth new-v 1)))))

      (testing "vector conversion"
        (is (= [1 2 3 4 5] (u/->clj store-v))))

      (testing "reverse operation"
        (is (= [5 4 3 2 1] (reverse store-v))))

      (testing "reseq operation"
        (is (thrown? UnsupportedOperationException
                     (rseq store-v)))))))

(deftest lazy-loading-test
  (testing "Values are loaded lazily"
    (let [store-atom (atom {})
          store (memory/->MemoryChunkStore store-atom)
          data [1 {:x 10 :y 20} [1 2 3] "hello" 42]
          hash (store/persist store data)
          node (store/get-chunk store hash)
          ;; Create a CountingStore to track get-chunk calls
          count-atom (atom 0)
          counting-store (reify store/ChunkStore
                           (put-chunk! [_ data]
                             (store/put-chunk! store data))
                           (get-chunk [_ hash]
                             (swap! count-atom inc)
                             (println "[DEBUG] get-chunk called with hash:" hash)
                             (let [result (store/get-chunk store hash)]
                               (println "[DEBUG] get-chunk returned:" result)
                               result)))
          store-v (store-vector/store-vector counting-store node)]

      ;; Just creating the vector shouldn't load any values
      (is (= 0 @count-atom))

      ;; Getting index 0 should load that value
      (nth store-v 0)
      (let [calls-0 @count-atom]
        (println "[DEBUG] After getting index 0, load-count =" calls-0)
        (is (> calls-0 0))

        ;; Getting index 0 again should not load anything (cached)
        (nth store-v 0)
        (is (= calls-0 @count-atom))

        ;; Getting index 1 should load that value
        (nth store-v 1)
        (let [calls-1 @count-atom]
          (println "[DEBUG] After getting index 1, load-count =" calls-1)
          (is (> calls-1 calls-0))

          ;; Getting index 2 should load that value
          (nth store-v 2)
          (let [calls-2 @count-atom]
            (println "[DEBUG] After getting index 2, load-count =" calls-2)
            (is (> calls-2 calls-1))))))))

(deftest nested-structures-test
  (testing "Nested structures work correctly"
    (let [store (memory/->MemoryChunkStore (atom {}))
          data [1 {:a 10} [100 200]]
          hash (store/persist store data)
          node (store/get-chunk store hash)
          store-v (store-vector/store-vector store node)]

      (testing "Simple value access"
        (is (= 1 (nth store-v 0))))

      (testing "Nested map access"
        (let [nested-map (nth store-v 1)]
          (is (= 10 (get nested-map :a)))))

      (testing "Nested vector access"
        (let [nested-vec (nth store-v 2)]
          (is (= 100 (nth nested-vec 0)))
          (is (= 200 (nth nested-vec 1))))))))

(deftest java-list-interface-test
  (testing "Java List interface methods"
    (let [store (memory/->MemoryChunkStore (atom {}))
          data [1 2 3]
          hash (store/persist store data)
          node (store/get-chunk store hash)
          store-v (store-vector/store-vector store node)]

      (testing "size and isEmpty"
        (is (= 3 (.size store-v)))
        (is (not (.isEmpty store-v))))

      (testing "contains"
        (is (.contains store-v 2))
        (is (not (.contains store-v 99))))

      (testing "indexOf and lastIndexOf"
        (is (= 1 (.indexOf store-v 2)))
        (is (= 1 (.lastIndexOf store-v 2)))
        (is (= -1 (.indexOf store-v 99))))

      (testing "toArray"
        (is (= [1 2 3] (vec (.toArray store-v)))))

      (testing "subList"
        (is (= [2 3] (.subList store-v 1 3)))))))