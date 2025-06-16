(ns atomdb.data.store-list-test
  (:require [atomdb.data.store-list :as store-list]
            [atomdb.store :as store]
            [atomdb.store.memory :as memory]
            [atomdb.utils :as u]
            [clojure.test :refer :all]
            [atomdb.core]))

(deftest store-list-test
  (testing "Basic list operations"
    (let [store (memory/->MemoryChunkStore (atom {}))
          data '(1 2 3 4 5)
          hash (store/persist store data)
          node (store/get-chunk store hash)
          store-l (store-list/store-list store node)]

      (testing "nth operation"
        (is (= 1 (nth store-l 0)))
        (is (= 2 (nth store-l 1)))
        (is (= 3 (nth store-l 2)))
        (is (= 4 (nth store-l 3)))
        (is (= 5 (nth store-l 4)))
        (is (nil? (nth store-l 5)))
        (is (= :not-found (nth store-l 5 :not-found))))

      (testing "get operation (via IFn)"
        (is (= 1 (store-l 0)))
        (is (= 2 (store-l 1)))
        (is (= 3 (store-l 2))))

      (testing "count operation"
        (is (= 5 (count store-l))))

      (testing "seq operation"
        (is (= '(1 2 3 4 5) (seq store-l))))

      (testing "cons operation"
        (let [new-l (cons 0 store-l)]
          (is (= 6 (count new-l)))
          (is (= 0 (nth new-l 0)))
          (is (= 1 (nth new-l 1)))))

      (testing "list conversion"
        (is (= '(1 2 3 4 5) (u/->clj store-l))))

      (testing "reverse operation"
        (is (= '(5 4 3 2 1) (reverse store-l)))))))

(deftest lazy-loading-test
  (testing "Values are loaded lazily"
    (let [store-atom (atom {})
          store (memory/->MemoryChunkStore store-atom)
          data '(1 {:x 10 :y 20} (1 2 3) "hello" 42)
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
          store-l (store-list/store-list counting-store node)]

      ;; Just creating the list shouldn't load any values
      (is (= 0 @count-atom))

      ;; Getting index 0 should load that value
      (nth store-l 0)
      (let [calls-0 @count-atom]
        (println "[DEBUG] After getting index 0, load-count =" calls-0)
        (is (> calls-0 0))

        ;; Getting index 0 again should not load anything (cached)
        (nth store-l 0)
        (is (= calls-0 @count-atom))
        ;; Getting index 1 should load that value
        (nth store-l 1)
        (let [calls-1 @count-atom]
          (println "[DEBUG] After getting index 1, load-count =" calls-1)
          (is (> calls-1 calls-0))

          ;; Getting index 2 should load that value
          (nth store-l 2)
          (let [calls-2 @count-atom]
            (println "[DEBUG] After getting index 2, load-count =" calls-2)
            (is (> calls-2 calls-1))))))))

(deftest nested-structures-test
  (testing "Nested structures work correctly"
    (let [store (memory/->MemoryChunkStore (atom {}))
          data '(1 {:a 10} (100 200))
          hash (store/persist store data)
          node (store/get-chunk store hash)
          store-l (store-list/store-list store node)]

      (testing "Simple value access"
        (is (= 1 (nth store-l 0))))

      (testing "Nested map access"
        (let [nested-map (nth store-l 1)]
          (is (= 10 (get nested-map :a)))))

      (testing "Nested list access"
        (let [nested-list (nth store-l 2)]
          (is (= 100 (nth nested-list 0)))
          (is (= 200 (nth nested-list 1))))))))

(deftest java-list-interface-test
  (testing "Java List interface methods"
    (let [store (memory/->MemoryChunkStore (atom {}))
          data '(1 2 3)
          hash (store/persist store data)
          node (store/get-chunk store hash)
          store-l (store-list/store-list store node)]

      (testing "size and isEmpty"
        (is (= 3 (.size store-l)))
        (is (not (.isEmpty store-l))))

      (testing "contains"
        (is (.contains store-l 2))
        (is (not (.contains store-l 99))))

      (testing "indexOf and lastIndexOf"
        (is (= 1 (.indexOf store-l 2)))
        (is (= 1 (.lastIndexOf store-l 2)))
        (is (= -1 (.indexOf store-l 99))))

      (testing "toArray"
        (is (= [1 2 3] (vec (.toArray store-l)))))

      (testing "subList"
        (is (= '(2 3) (.subList store-l 1 3)))))))