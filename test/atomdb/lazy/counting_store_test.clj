(ns atomdb.lazy.counting-store-test
  (:require [clojure.test :refer :all]
            [atomdb.lazy.store-map :as lazy-map]
            [atomdb.store :as store]
            [atomdb.store.memory :as memory]))

;; A store implementation that counts the number of times get-chunk is called
(defrecord CountingStore [delegate count-atom]
  store/ChunkStore
  (put-chunk! [_ data]
    (store/put-chunk! delegate data))
  (get-chunk [_ hash]
    (swap! count-atom inc)
    (store/get-chunk delegate hash)))

(defn create-counting-store []
  (let [delegate (memory/->MemoryChunkStore (atom {}))
        count-atom (atom 0)]
    [delegate count-atom (->CountingStore delegate count-atom)]))

(deftest lazy-loading-test
  (testing "Values are loaded lazily"
    (let [[delegate count-atom counting-store] (create-counting-store)
          data {:a 1 :b {:x 10 :y 20} :c [1 2 3]}
          hash (store/persist delegate data)
          node (store/get-chunk delegate hash)
          lazy-m (lazy-map/store-map counting-store node)]
      
      ;; Just creating the map shouldn't load any values
      (is (= 0 @count-atom))
      
      ;; Getting :a should load :a (and possibly other things)
      (get lazy-m :a)
      (let [a-calls @count-atom]
        (println "[DEBUG] After getting :a, count =" a-calls)
        (is (> a-calls 0))
        
        ;; Getting :a again should not load anything (cached)
        (get lazy-m :a)
        (is (= a-calls @count-atom))
        
        ;; Getting :b should load :b (and possibly other things)
        (get lazy-m :b)
        (let [b-calls @count-atom]
          (println "[DEBUG] After getting :b, count =" b-calls)
          (is (> b-calls a-calls))
          
          ;; Getting :c should load :c (and possibly other things)
          (get lazy-m :c)
          (let [c-calls @count-atom]
            (println "[DEBUG] After getting :c, count =" c-calls)
            (is (> c-calls b-calls))))))))

(deftest nested-lazy-maps-test
  (testing "Nested maps are also lazy"
    (let [[delegate count-atom counting-store] (create-counting-store)
          data {:a {:x 1 :y 2} :b {:z 3}}
          hash (store/persist delegate data)
          node (store/get-chunk delegate hash)
          lazy-m (lazy-map/store-map counting-store node)]
      
      ;; Just creating the map shouldn't load any values
      (is (= 0 @count-atom))
      
      ;; Getting :a should load :a (and possibly other things)
      (get lazy-m :a)
      (let [a-calls @count-atom]
        (println "[DEBUG] After getting :a, count =" a-calls)
        (is (> a-calls 0))
        
        ;; Getting :a.x should load :a.x (and possibly other things)
        (get-in lazy-m [:a :x])
        (let [ax-calls @count-atom]
          (println "[DEBUG] After getting :a.x, count =" ax-calls)
          (is (> ax-calls a-calls))
          
          ;; Getting :a.y should load :a.y (and possibly other things)
          (get-in lazy-m [:a :y])
          (let [ay-calls @count-atom]
            (println "[DEBUG] After getting :a.y, count =" ay-calls)
            (is (> ay-calls ax-calls))
            
            ;; Getting :b should load :b (and possibly other things)
            (get lazy-m :b)
            (let [b-calls @count-atom]
              (println "[DEBUG] After getting :b, count =" b-calls)
              (is (> b-calls ay-calls))
              
              ;; Getting :b.z should load :b.z (and possibly other things)
              (get-in lazy-m [:b :z])
              (let [bz-calls @count-atom]
                (println "[DEBUG] After getting :b.z, count =" bz-calls)
                (is (> bz-calls b-calls))))))))))