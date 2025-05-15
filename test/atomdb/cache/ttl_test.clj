(ns atomdb.cache.ttl-test
  (:require [clojure.test :refer :all]
            [atomdb.cache :as cache]
            [atomdb.cache.ttl :refer [ttl-cache]]))

(deftest ttl-cache-behavior
  (testing "returns value within TTL window"
    (let [ttl (ttl-cache 100)]
      (cache/cache-put ttl :a "fresh")
      (is (= "fresh" (cache/cache-get ttl :a)))))

  (testing "evicts value after TTL expiration"
    (let [ttl (ttl-cache 50)]
      (cache/cache-put ttl :x 42)
      (Thread/sleep 100)
      (is (nil? (cache/cache-get ttl :x)))))

  (testing "missing key returns nil"
    (let [ttl (ttl-cache 100)]
      (is (nil? (cache/cache-get ttl :missing))))))

