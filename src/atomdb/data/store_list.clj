(ns atomdb.data.store-list
  "Implementation of a persistent list backed by a content-addressable store.

   This namespace provides a lazy-loading list implementation that stores
   its elements in a content-addressable store. Elements are only loaded
   when accessed, making it memory-efficient for large lists.

   The StoreList type implements the standard Clojure list interfaces
   (IPersistentList, Seqable, etc.) as well as the Java List interface,
   allowing it to be used as a drop-in replacement for regular Clojure lists."
  (:require [atomdb.store :as store]
            [atomdb.utils :as u])
  (:import (clojure.lang Counted IFn IMeta IObj IPersistentCollection IPersistentList Seqable)
           (java.util List)))

;; Forward declarations
(declare store-list lazy-elements)

; A persistent list implementation backed by a content-addressable store.
;
; This type provides a lazy-loading list where elements are only loaded
; from the store when accessed. This makes it memory-efficient for large
; lists as only the accessed elements are kept in memory
; Fields:
; - store: The chunk store where list elements are stored
; - node: The node containing the list structure with child references
; - loaded-values: A cache of already loaded values to avoid repeated loading
(deftype StoreList
  [store node ^:volatile-mutable loaded-values]
  IPersistentList
  (cons [this o]
    ;; For lists, cons adds to the front
    (let [new-children (into [] (cons (store/persist store o) (:children node)))
          new-node {:type :list :children new-children}]
      (store-list store new-node)))

  IPersistentCollection
  (empty [_]
    '())

  (equiv [this o]
    (and (instance? List o)
         (= (count this) (count o))
         (every? (fn [i] (= (nth this i) (nth o i))) (range (count this)))))

  Seqable
  (seq [this]
    (when (> (count this) 0)
      (lazy-elements this)))

  Counted
  (count [this]
    (count (:children node)))

  clojure.lang.Indexed
  (nth [this i]
    (.nth this i nil))

  (nth [this i not-found]
    (if (and (>= i 0) (< i (count this)))
      (if-let [hash (get-in node [:children i])]
        (if (and loaded-values (contains? loaded-values i))
          ;; Return cached value if available
          (get loaded-values i)
          ;; Otherwise load from store and cache
          (let [value-node (store/get-chunk store hash)
                result (store/load-node store value-node)]
            ;; Cache the result
            (if loaded-values
              (set! loaded-values (assoc loaded-values i result))
              (set! loaded-values {i result}))
            result))
        not-found)
      not-found))

  IFn
  (invoke [this i]
    (nth this i))

  (invoke [this i not-found]
    (nth this i not-found))

  IObj
  (withMeta [this meta]
    (let [new-node (assoc node :meta meta)]
      (store-list store new-node)))

  IMeta
  (meta [this]
    (:meta node))

  Object
  (toString [this]
    (str "#stored " (list* (seq this))))

  (equals [this other]
    (.equiv this other))

  (hashCode [this]
    (reduce (fn [acc v]
              (+ (* 31 acc) (hash v)))
            1
            (seq this)))

  List
  (size [this]
    (count this))

  (isEmpty [this]
    (zero? (count this)))

  (contains [this o]
    (boolean (some #(= o %) (seq this))))

  (get [this i]
    (nth this i))

  (indexOf [this o]
    (loop [i 0]
      (cond
        (>= i (count this)) -1
        (= (nth this i) o) i
        :else (recur (inc i)))))

  (lastIndexOf [this o]
    (loop [i (dec (count this))]
      (cond
        (< i 0) -1
        (= (nth this i) o) i
        :else (recur (dec i)))))

  (toArray [this]
    (object-array (seq this)))

  (add [this o]
    (throw (UnsupportedOperationException. "Use conj instead of add for immutable lists")))

  (add [this i o]
    (throw (UnsupportedOperationException. "Use conj instead of add for immutable lists")))

  (addAll [this coll]
    (throw (UnsupportedOperationException. "Use into instead of addAll for immutable lists")))

  (addAll [this i coll]
    (throw (UnsupportedOperationException. "Use into instead of addAll for immutable lists")))

  (clear [this]
    (throw (UnsupportedOperationException. "Use empty list '() instead of clear for immutable lists")))

  (removeAll [this coll]
    (throw (UnsupportedOperationException. "Use filter instead of removeAll for immutable lists")))

  (retainAll [this coll]
    (throw (UnsupportedOperationException. "Use filter instead of retainAll for immutable lists")))

  (set [this i o]
    (throw (UnsupportedOperationException. "Lists are immutable, use a vector for indexed updates")))

  (subList [this from-index to-index]
    (apply list (take (- to-index from-index) (drop from-index (seq this)))))

  (iterator [this]
    (.iterator ^Iterable (seq this)))

  (listIterator [this]
    (.listIterator ^List (apply list (seq this))))

  (listIterator [this index]
    (.listIterator ^List (apply list (seq this)) index))

  u/StoreDataStructure
  (to-clj [this]
    (apply list (map u/->clj (seq this)))))


;; Helper functions
(defn lazy-elements
  "Returns a lazy sequence of all elements in the list.

   This function provides efficient lazy access to list elements,
   only loading them from the store when they are actually accessed.

   Parameters:
   - lst: The StoreList to get elements from

   Returns:
   - A lazy sequence of all elements in the list"
  [lst]
  (map #(nth lst % nil) (range (count lst))))

(defn store-list
  "Creates a new StoreList instance.

   This function creates a new StoreList backed by the given store
   and node. The list elements are loaded lazily when accessed.

   Parameters:
   - store: The chunk store to load data from
   - node: The node containing the list data

   Returns:
   - A new StoreList instance"
  [store node]
  (->StoreList store node {}))

;; Define methods for lazy-load-node
(defmethod store/load-node :list [store {:keys [children]}]
  (store-list store {:type :list :children children}))
