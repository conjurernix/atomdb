(ns atomdb.data.store-set
  "Implementation of a persistent set backed by a content-addressable store.

   This namespace provides a lazy-loading set implementation that stores
   its elements in a content-addressable store. Elements are only loaded
   when accessed, making it memory-efficient for large sets.

   The StoreSet type implements the standard Clojure set interfaces
   (IPersistentSet, Seqable, etc.) as well as the Java Set interface,
   allowing it to be used as a drop-in replacement for regular Clojure sets."
  (:require [atomdb.store :as store]
            [atomdb.utils :as u])
  (:import (clojure.lang Counted IFn IMeta IObj IPersistentCollection IPersistentSet Seqable)
           (java.util Collection Iterator Set)))

;; Forward declarations
(declare store-set lazy-seq-elements find-index)

; A persistent set implementation backed by a content-addressable store.
;
; This type provides a lazy-loading set where elements are only loaded
; from the store when accessed. This makes it memory-efficient for large
; sets as only the accessed elements are kept in memory
; Fields:
; - store: The chunk store where set elements are stored
; - node: The node containing the set structure with child references
; - loaded-values: A cache of already loaded values to avoid repeated loading
(deftype StoreSet
  [store node ^:volatile-mutable loaded-values]
  IPersistentSet
  (disjoin [this o]
    (let [idx (find-index store node o)]
      (if (neg? idx)
        this
        (let [new-children (into (subvec (:children node) 0 idx)
                                 (subvec (:children node) (inc idx)))
              new-node {:type :set :children new-children}]
          (store-set store new-node)))))

  (contains [this o]
    (>= (find-index store node o) 0))

  (get [this o]
    ;; First check if we already have the value in the cache
    (if (and loaded-values (some (fn [[_ v]] (= v o)) loaded-values))
      o
      ;; Otherwise find the index and load the value
      (let [idx (find-index store node o)]
        (when (>= idx 0)
          (if (and loaded-values (contains? loaded-values idx))
            ;; Return cached value if available
            (get loaded-values idx)
            ;; Otherwise load from store and cache
            (let [hash (get-in node [:children idx])
                  value-node (store/get-chunk store hash)
                  result (store/load-node store value-node)]
              ;; Cache the result
              (if loaded-values
                (set! loaded-values (assoc loaded-values idx result))
                (set! loaded-values {idx result}))
              result))))))

  IPersistentCollection
  (cons [this o]
    (if (contains? this o)
      this
      (let [new-children (conj (:children node) (store/persist store o))
            new-node {:type :set :children new-children}]
        (store-set store new-node))))

  (empty [_]
    #{})

  (equiv [this o]
    (and (instance? Set o)
         (= (count this) (count o))
         (every? #(contains? o %) (seq this))))

  Seqable
  (seq [this]
    (when (> (count this) 0)
      (let [loaded-values-atom (atom loaded-values)]
        (let [result (doall (lazy-seq-elements store node loaded-values-atom))]
          (set! loaded-values @loaded-values-atom)
          result))))

  Counted
  (count [this]
    (count (:children node)))

  IFn
  (invoke [this o]
    (get this o))

  IObj
  (withMeta [this meta]
    (let [new-node (assoc node :meta meta)]
      (store-set store new-node)))

  IMeta
  (meta [this]
    (:meta node))

  Object
  (toString [this]
    (str "#stored " (into #{} this)))

  (equals [this other]
    (.equiv this other))

  (hashCode [this]
    (reduce (fn [acc v]
              (+ acc (hash v)))
            0
            (seq this)))

  Set
  (size [this]
    (count this))

  (isEmpty [this]
    (zero? (count this)))

  (^objects toArray [this]
    (object-array (seq this)))

  (^objects toArray [this ^objects arr]
    (let [elements (seq this)
          result-arr (if (or (nil? arr) (< (alength arr) (count this)))
                       (make-array Object (count this))
                       arr)]
      (loop [i 0
             s elements]
        (if (and s (< i (alength result-arr)))
          (do
            (aset result-arr i (first s))
            (recur (inc i) (next s)))
          result-arr))))

  (add [this o]
    (throw (UnsupportedOperationException. "Use conj instead of add for immutable sets")))

  (remove [this o]
    (throw (UnsupportedOperationException. "Use disj instead of remove for immutable sets")))

  (containsAll [this coll]
    (every? #(contains? this %) coll))

  (addAll [this coll]
    (throw (UnsupportedOperationException. "Use into instead of addAll for immutable sets")))

  (retainAll [this coll]
    (throw (UnsupportedOperationException. "Use filter instead of retainAll for immutable sets")))

  (removeAll [this coll]
    (throw (UnsupportedOperationException. "Use filter instead of removeAll for immutable sets")))

  (clear [this]
    (throw (UnsupportedOperationException. "Use empty set #{} instead of clear for immutable sets")))

  (iterator [this]
    (let [elements (atom (seq this))]
      (reify Iterator
        (hasNext [_]
          (boolean @elements))
        (next [_]
          (let [current (first @elements)]
            (swap! elements next)
            current))
        (remove [_]
          (throw (UnsupportedOperationException. "Cannot remove from immutable set"))))))


  u/StoreDataStructure
  (to-clj [this]
    (into #{} (map u/->clj (seq this)))))

;; Helper functions
(defn find-index
  "Finds the index of an element in a StoreSet.

   This function searches for an element in the set and returns its index.
   If the element is not found, it returns -1.

   Parameters:
   - store: The chunk store
   - node: The node containing the set data
   - o: The element to search for

   Returns:
   - The index of the element, or -1 if not found"
  [store node o]
  (let [children (:children node)]
    (loop [idx 0]
      (if (< idx (count children))
        (let [hash (nth children idx)
              value-node (store/get-chunk store hash)
              element (store/load-node store value-node)]
          (if (= element o)
            idx
            (recur (inc idx))))
        -1))))

(defn lazy-seq-elements
  "Returns a lazy sequence of all elements in the set.

   This function provides efficient lazy access to set elements,
   only loading them from the store when they are actually accessed.

   Parameters:
   - store: The chunk store
   - node: The node containing the set data
   - loaded-values: A reference to the cache of loaded values

   Returns:
   - A lazy sequence of all elements in the set"
  [store node loaded-values-ref]
  (map-indexed (fn [idx hash]
                 (if (and @loaded-values-ref (contains? @loaded-values-ref idx))
                   (get @loaded-values-ref idx)
                   (let [value-node (store/get-chunk store hash)
                         result (store/load-node store value-node)]
                     ;; Cache the result
                     (if @loaded-values-ref
                       (reset! loaded-values-ref (assoc @loaded-values-ref idx result))
                       (reset! loaded-values-ref {idx result}))
                     result)))
               (:children node)))

(defn store-set
  "Creates a new StoreSet instance.

   This function creates a new StoreSet backed by the given store
   and node. The set elements are loaded lazily when accessed.

   Parameters:
   - store: The chunk store to load data from
   - node: The node containing the set data

   Returns:
   - A new StoreSet instance"
  [store node]
  (->StoreSet store node {}))

;; Define methods for lazy-load-node
(defmethod store/load-node :set [store {:keys [children]}]
  (store-set store {:type :set :children children}))
