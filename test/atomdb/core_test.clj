(ns atomdb.core-test
  (:require [atomdb.cache.lru :as lru]
            [atomdb.cache.noop :as noop]
            [atomdb.cache.ttl :as ttl]
            [atomdb.core :refer :all]
            [atomdb.serde.edn :as serde.edn]
            [atomdb.serde.fressian :as serde.fress]
            [atomdb.store :as store]
            [atomdb.store.file :as file]
            [atomdb.store.memory :as memory]
            [atomdb.utils :as u]
            [clojure.test :refer :all])
  (:import (atomdb.core AtomDB)))

;; Test data
(def test-users
  {1 {:id 1 :name "Alice" :email "alice@example.com" :age 28}
   2 {:id 2 :name "Bob" :email "bob@example.com" :age 30}
   3 {:id 3 :name "Charlie" :email "charlie@example.com" :age 44}})

(deftest db-creation-test
  (testing "Creating a database with default options"
    (let [db (db)]
      (is (nil? @db))
      (is (instance? AtomDB db))))

  (testing "Creating a database with initial data"
    (let [db (db {:init {:users test-users}})]
      (is (map? @db))
      (is (= test-users (u/->clj (:users @db)))))))

(deftest db-deref-test
  (testing "Dereferencing a database returns its current state"
    (let [db (db {:init {:counter 0}})]
      (is (= {:counter 0} (u/->clj @db))))))

(deftest db-swap-test
  (testing "Swapping a database updates its state"
    (let [db (db {:init {:counter 0}})]
      (swap! db update :counter inc)
      (is (= {:counter 1} (u/->clj @db)))

      (swap! db assoc :new-key "value")
      (is (= {:counter 1 :new-key "value"} (u/->clj @db)))

      (swap! db update-in [:counter] + 10)
      (is (= {:counter 11 :new-key "value"} (u/->clj @db))))))

(deftest db-reset-test
  (testing "Resetting a database replaces its state"
    (let [db (db {:init {:counter 0}})]
      (reset! db {:new-state true})
      (is (= {:new-state true} (u/->clj @db))))))

(deftest db-compare-and-set-test
  (testing "Compare and set works correctly"
    (let [db (db {:init {:counter 0}})]
      (is (true? (compare-and-set! db {:counter 0} {:counter 1})))
      (is (= {:counter 1} (u/->clj  @db)))

      (is (false? (compare-and-set! db {:counter 0} {:counter 2})))
      (is (= {:counter 1} (u/->clj @db))))))

(deftest db-helper-functions-test
  (testing "Standard Clojure functions work correctly with the database"
    (let [db (db {:init {:users test-users}})]
      ;; get-in with deref
      (is (= test-users (u/->clj (:users @db))))
      (is (= (:name (get test-users 1))
             (get-in @db [:users 1 :name])))

      ;; swap! with assoc-in
      (swap! db assoc-in [:users 4] {:id 4 :name "Diana" :email "diana@example.com" :age 33})
      (is (= 4 (count (:users @db))))
      (is (= "Diana" (get-in @db [:users 4 :name])))

      ;; swap! with update-in
      (swap! db update-in [:users 1 :age] inc)
      (is (= 29 (get-in @db [:users 1 :age])))

      ;; swap! with update-in and dissoc
      (swap! db update-in [:users] dissoc 3)
      (is (= 3 (count (:users @db))))
      (is (nil? (get-in @db [:users 3]))))))

(deftest db-persistence-test
  (testing "Database persists data correctly"
    (let [db (db {:init {:users test-users}})
          store (get-store db)
          root-hash (get-root-hash db)]
      (is (string? root-hash))
      (is (map? (store/get-chunk store root-hash)))

      ;; Update and check new hash
      (swap! db assoc-in [:users 1 :age] 29)
      (let [new-hash (get-root-hash db)]
        (is (not= root-hash new-hash))
        (is (map? (store/get-chunk store new-hash)))))))

(deftest db-with-different-stores-test
  (testing "Database works with memory store"
    (let [db (db {:store (memory/->MemoryChunkStore (atom {}))
                  :init  {:test true}})]
      (is (= {:test true} (u/->clj @db)))))

  (testing "Database works with file store using edn serde"
    (let [temp-dir (str (System/getProperty "java.io.tmpdir") "/atomdb-test-" (System/currentTimeMillis))
          db (db {:store (file/file-store temp-dir (serde.edn/edn-serde))
                  :init  {:test true}})]
      (is (= {:test true} (u/->clj @db)))))

  (testing "Database works with file store using fressian serde"
    (let [temp-dir (str (System/getProperty "java.io.tmpdir") "/atomdb-test-fress-" (System/currentTimeMillis))
          db (db {:store (file/file-store temp-dir (serde.fress/fressian-serde))
                  :init  {:test true}})]
      (is (= {:test true} (u/->clj @db))))))

(deftest db-with-different-caches-test
  (testing "Database works with LRU cache"
    (let [db (db {:cache (lru/lru-cache 10)
                  :init  {:test true}})]
      (is (= {:test true} (u/->clj @db)))))

  (testing "Database works with TTL cache"
    (let [db (db {:cache (ttl/ttl-cache 1000)
                  :init {:test true}})]
      (is (= {:test true} (u/->clj @db)))))

  (testing "Database works with no-op cache"
    (let [db (db {:cache (noop/no-op-cache)
                  :init {:test true}})]
      (is (= {:test true} (u/->clj @db))))))

(deftest db-complex-operations-test
  (testing "Complex operations on nested data structures"
    (let [db (db {:init {:users test-users
                         :counters {:visits 0 :updates 0}
                         :settings {:theme "light" :notifications true}}})]
      ;; Multiple updates in one swap
      (swap! db (fn [data]
                  (-> data
                      (assoc-in [:users 4] {:id 4 :name "Diana" :email "diana@example.com" :age 33})
                      (update-in [:counters :visits] inc)
                      (update-in [:settings :theme] #(if (= % "light") "dark" "light")))))

      (is (= 4 (count (:users @db))))
      (is (= 1 (get-in @db [:counters :visits])))
      (is (= "dark" (get-in @db [:settings :theme])))

      ;; Nested updates with standard functions
      (swap! db update-in [:users 1 :profile] (fn [_] {:bio "Software developer" :location "San Francisco"}))
      (is (= "Software developer" (get-in @db [:users 1 :profile :bio])))

      ;; Batch updates
      (doseq [user-id (keys (:users @db))]
        (swap! db update-in [:users user-id :last-seen] (fn [_] "2023-01-01")))

      (is (every? #(= "2023-01-01" (:last-seen %)) (vals (:users @db))))

      ;; Remove nested data
      (swap! db update-in [:settings] dissoc :notifications)
      (is (nil? (get-in @db [:settings :notifications])))
      (is (= "dark" (get-in @db [:settings :theme]))))))