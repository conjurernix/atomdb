(ns atomdb.cache.lru-test
  (:require [clojure.test :refer :all]
            [atomdb.cache :as cache]
            [atomdb.cache.lru :refer [lru-cache]]))

(deftest lru-cache-behavior
  (testing "evicts least recently used entry when capacity is exceeded"
    (let [c (lru-cache 2)]
      (cache/cache-put c :a 1)
      (cache/cache-put c :b 2)
      (is (= 1 (cache/cache-get c :a))) ;; access :a to mark it recently used
      (cache/cache-put c :c 3)          ;; should evict :b
      (is (nil? (cache/cache-get c :b)))
      (is (= 1 (cache/cache-get c :a)))
      (is (= 3 (cache/cache-get c :c)))))

  (testing "returns nil for missing keys"
    (let [c (lru-cache 1)]
      (is (nil? (cache/cache-get c :missing)))))

  (testing "does not exceed capacity"
    (let [c (lru-cache 1)]
      (cache/cache-put c :x 100)
      (cache/cache-put c :y 200)
      (is (nil? (cache/cache-get c :x)))
      (is (= 200 (cache/cache-get c :y))))))

