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

(deftest db-store-data-structures-test
  (testing "Using StoreMap in the database"
    (let [db (db {:init {:users {}}})]
      ;; Add users to the map
      (swap! db assoc-in [:users 1] {:name "Alice" :age 28})
      (swap! db assoc-in [:users 2] {:name "Bob" :age 30})
      
      ;; Verify map operations
      (is (= 2 (count (:users @db))))
      (is (= "Alice" (get-in @db [:users 1 :name])))
      (is (= 30 (get-in @db [:users 2 :age])))
      
      ;; Update a value in the map
      (swap! db update-in [:users 1 :age] inc)
      (is (= 29 (get-in @db [:users 1 :age])))
      
      ;; Remove an entry from the map
      (swap! db update :users dissoc 2)
      (is (= 1 (count (:users @db))))
      (is (nil? (get-in @db [:users 2])))))
  
  (testing "Using StoreVector in the database"
    (let [db (db {:init {:items []}})]
      ;; Add items to the vector
      (swap! db update :items conj "first")
      (swap! db update :items conj "second")
      (swap! db update :items conj "third")
      
      ;; Verify vector operations
      (is (= 3 (count (:items @db))))
      (is (= "first" (get-in @db [:items 0])))
      (is (= "second" (get-in @db [:items 1])))
      (is (= "third" (get-in @db [:items 2])))
      
      ;; Update a value in the vector
      (swap! db assoc-in [:items 1] "updated")
      (is (= "updated" (get-in @db [:items 1])))
      
      ;; Use vector-specific functions
      (swap! db update :items subvec 1)
      (is (= 2 (count (:items @db))))
      (is (= "updated" (get-in @db [:items 0])))
      
      ;; Create a new vector with transformation
      (swap! db update :items (fn [v] (mapv clojure.string/upper-case v)))
      (is (= ["UPDATED" "THIRD"] (u/->clj (:items @db))))))
  
  (testing "Using StoreList in the database"
    (let [db (db {:init {:queue '()}})]
      ;; Add items to the list (conj adds to the front for lists)
      (swap! db update :queue conj "third")
      (swap! db update :queue conj "second")
      (swap! db update :queue conj "first")
      
      ;; Verify list operations
      (is (= 3 (count (:queue @db))))
      (is (= "first" (first (:queue @db))))
      (is (= '("first" "second" "third") (u/->clj (:queue @db))))
      
      ;; Use list-specific functions
      (swap! db update :queue rest)
      (is (= 2 (count (:queue @db))))
      (is (= "second" (first (:queue @db))))
      
      ;; Transform the list
      (swap! db update :queue (fn [lst] (map clojure.string/upper-case lst)))
      (is (= '("SECOND" "THIRD") (u/->clj (:queue @db))))))
  
  (testing "Using StoreSet in the database"
    (let [db (db {:init {:tags #{}}})]
      ;; Add items to the set
      (swap! db update :tags conj "clojure")
      (swap! db update :tags conj "functional")
      (swap! db update :tags conj "immutable")
      (swap! db update :tags conj "clojure") ; Duplicate, should be ignored
      
      ;; Verify set operations
      (is (= 3 (count (:tags @db))))
      (is (contains? (:tags @db) "clojure"))
      (is (contains? (:tags @db) "functional"))
      (is (contains? (:tags @db) "immutable"))
      
      ;; Remove an item from the set
      (swap! db update :tags disj "functional")
      (is (= 2 (count (:tags @db))))
      (is (not (contains? (:tags @db) "functional")))
      
      ;; Set operations
      (swap! db update :tags (fn [s] (into s #{"persistent" "database"})))
      (is (= 4 (count (:tags @db))))
      (is (contains? (:tags @db) "persistent"))
      (is (contains? (:tags @db) "database")))))

(deftest db-nested-data-structures-test
  (testing "Nested data structures in the database"
    (let [db (db {:init {:users {}
                         :tags #{}
                         :history []
                         :queue '()}})]
      ;; Add a user with nested structures
      (swap! db assoc-in [:users 1] 
             {:name "Alice"
              :age 28
              :tags #{"developer" "clojure"}
              :posts [{:title "First Post" :comments ["Great!" "Nice post"]}
                      {:title "Second Post" :comments ["Interesting"]}]})
      
      ;; Verify nested structures
      (is (= "Alice" (get-in @db [:users 1 :name])))
      (is (= 2 (count (get-in @db [:users 1 :tags]))))
      (is (contains? (get-in @db [:users 1 :tags]) "clojure"))
      (is (= 2 (count (get-in @db [:users 1 :posts]))))
      (is (= "First Post" (get-in @db [:users 1 :posts 0 :title])))
      (is (= 2 (count (get-in @db [:users 1 :posts 0 :comments]))))
      
      ;; Update nested structures
      (swap! db update-in [:users 1 :tags] conj "functional")
      (is (= 3 (count (get-in @db [:users 1 :tags]))))
      (is (contains? (get-in @db [:users 1 :tags]) "functional"))
      
      (swap! db update-in [:users 1 :posts 0 :comments] conj "Awesome!")
      (is (= 3 (count (get-in @db [:users 1 :posts 0 :comments]))))
      (is (= "Awesome!" (get-in @db [:users 1 :posts 0 :comments 2])))
      
      ;; Add items to global structures
      (swap! db update :tags into (get-in @db [:users 1 :tags]))
      (is (= 3 (count (:tags @db))))
      (is (contains? (:tags @db) "clojure"))
      
      (swap! db update :history conj {:user 1 :action "login" :timestamp "2023-01-01"})
      (is (= 1 (count (:history @db))))
      (is (= 1 (get-in @db [:history 0 :user])))
      
      (swap! db update :queue conj "process-request")
      (is (= 1 (count (:queue @db))))
      (is (= "process-request" (first (:queue @db)))))))

(deftest db-data-structure-conversion-test
  (testing "Converting between data structures"
    (let [db (db {:init {:vector-data [1 2 3 4 5]
                         :list-data '()
                         :set-data #{}
                         :map-data {}}})]
      ;; Convert vector to set
      (swap! db update :set-data into (:vector-data @db))
      (println "[DEBUG_LOG] set-data:" (u/->clj (:set-data @db)))
      (is (= 5 (count (:set-data @db))))
      (is (contains? (:set-data @db) 3))
      
      ;; Convert vector to list
      (swap! db update :list-data (fn [lst] (concat lst (:vector-data @db))))
      (is (= 5 (count (:list-data @db))))
      (is (= 1 (first (:list-data @db))))
      
      ;; Convert set to map
      (swap! db update :map-data (fn [m] 
                                   (reduce (fn [acc v] (assoc acc v (* v v))) 
                                           m 
                                           (:set-data @db))))
      (println "[DEBUG_LOG] map-data:" (u/->clj (:map-data @db)))
      (is (= 6 (count (:map-data @db))))
      (is (= 9 (get-in @db [:map-data 3])))
      
      ;; Filter and transform
      (swap! db update :vector-data (fn [v] (filterv odd? v)))
      (is (= 3 (count (:vector-data @db))))
      (is (= [1 3 5] (u/->clj (:vector-data @db))))
      
      ;; Combine operations
      (swap! db (fn [data]
                  (-> data
                      (update :set-data (fn [s] (into #{} (filter even? s))))

                      (update :list-data (fn [lst] (filter #(> % 3) lst)))
                      (update :map-data (fn [m] (into {} (filter (fn [[k v]] (and (number? k) (even? k))) m)))))))
      
      (is (= 2 (count (:set-data @db))))
      (is (contains? (:set-data @db) 2))
      (is (contains? (:set-data @db) 4))
      
      (is (= 2 (count (:list-data @db))))
      (is (= '(4 5) (u/->clj (:list-data @db))))
      
      (is (= 2 (count (:map-data @db))))
      (is (= 4 (get-in @db [:map-data 2])))
      (is (= 16 (get-in @db [:map-data 4]))))))