(ns atomdb.data.store-map-test
  (:require [atomdb.data.store-map :as store-map]
            [atomdb.store :as store]
            [atomdb.store.memory :as memory]
            [clojure.test :refer :all]))

(deftest store-map-test
  (testing "Basic map operations"
    (let [store (memory/->MemoryChunkStore (atom {}))
          data {:a 1 :b 2 :c 3}
          hash (store/persist store data)
          node (store/get-chunk store hash)
          store-m (store-map/store-map store node)]

      (testing "get operation"
        (is (= 1 (get store-m :a)))
        (is (= 2 (get store-m :b)))
        (is (= 3 (get store-m :c)))
        (is (nil? (get store-m :d)))
        (is (= :not-found (get store-m :d :not-found))))

      (testing "contains? operation"
        (is (contains? store-m :a))
        (is (contains? store-m :b))
        (is (contains? store-m :c))
        (is (not (contains? store-m :d))))

      (testing "keys operation"
        (is (= #{:a :b :c} (set (keys store-m)))))

      (testing "vals operation"
        (is (= #{1 2 3} (set (vals store-m)))))

      (testing "count operation"
        (is (= 3 (count store-m))))

      (testing "seq operation"
        (is (= #{[:a 1] [:b 2] [:c 3]} (set (seq store-m))))))))

(deftest lazy-loading-test
  (testing "Values are loaded lazily"
    (let [store-atom (atom {})
          store (memory/->MemoryChunkStore store-atom)
          data {:a 1 :b {:x 10 :y 20} :c [1 2 3]}
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
          store-m (store-map/store-map counting-store node)]

      ;; Just creating the map shouldn't load any values
      (is (= 0 @count-atom))

      ;; Getting :a should load :a (and possibly other things)
      (get store-m :a)
      (let [a-calls @count-atom]
        (println "[DEBUG] After getting :a, load-count =" a-calls)
        (is (> a-calls 0))

        ;; Getting :a again should not load anything (cached)
        (get store-m :a)
        (is (= a-calls @count-atom))

        ;; Getting :b should load :b (and possibly other things)
        (get store-m :b)
        (let [b-calls @count-atom]
          (println "[DEBUG] After getting :b, load-count =" b-calls)
          (is (> b-calls a-calls))

          ;; Getting :c should load :c (and possibly other things)
          (get store-m :c)
          (let [c-calls @count-atom]
            (println "[DEBUG] After getting :c, load-count =" c-calls)
            (is (> c-calls b-calls))))))))
