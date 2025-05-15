(ns atomdb.cache.noop-test
  (:require [clojure.test :refer :all]
            [atomdb.cache :as cache]
            [atomdb.cache.noop :refer [no-op-cache]]))

(deftest noop-cache-behavior
  (testing "cache-put has no effect"
    (let [c (no-op-cache)]
      (cache/cache-put c :foo 42)
      (is (nil? (cache/cache-get c :foo)))
      (is (nil? (cache/cache-get c :bar))))))