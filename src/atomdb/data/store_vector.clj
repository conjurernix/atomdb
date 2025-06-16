(ns atomdb.data.store-vector
  "Implementation of a persistent vector backed by a content-addressable store.

   This namespace provides a lazy-loading vector implementation that stores
   its elements in a content-addressable store. Elements are only loaded
   when accessed, making it memory-efficient for large vectors.

   The StoreVector type implements the standard Clojure vector interfaces
   (IPersistentVector, Indexed, etc.) as well as the Java List interface,
   allowing it to be used as a drop-in replacement for regular Clojure vectors."
  (:require [atomdb.store :as store]
            [atomdb.utils :as u])
  (:import (clojure.lang Counted IFn ILookup IMeta IObj IPersistentCollection IPersistentVector Indexed Seqable)
           (java.util List)))

;; Forward declarations
(declare store-vector lazy-elements)

; A persistent vector implementation backed by a content-addressable store.
;
; This type provides a lazy-loading vector where elements are only loaded
; from the store when accessed. This makes it memory-efficient for large
; vectors as only the accessed elements are kept in memory
; Fields:
; - store: The chunk store where vector elements are stored
; - node: The node containing the vector structure with child references
; - loaded-values: A cache of already loaded values to avoid repeated loading
(deftype StoreVector
  [store node ^:volatile-mutable loaded-values]
  IPersistentVector
  (assocN [_this i v]
    (let [new-children (assoc (:children node) i (store/persist store v))
          new-node {:type :vector :children new-children}]
      (store-vector store new-node)))

  (cons [this o]
    ;; More efficient implementation that directly conjoins to the children vector
    (let [new-children (conj (:children node) (store/persist store o))
          new-node {:type :vector :children new-children}]
      (store-vector store new-node)))

  (assoc [this i v]
    (.assocN this i v))

  (rseq [this]
    (throw (UnsupportedOperationException. "Use reverse instead.")))

  IPersistentCollection
  (empty [_]
    [])

  (equiv [this o]
    (and (instance? List o)
         (= (count this) (count o))
         (every? (fn [i] (= (nth this i) (nth o i))) (range (count this)))))

  Indexed
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

  Seqable
  (seq [this]
    (when (> (count this) 0)
      (lazy-elements this)))

  Counted
  (count [this]
    (count (:children node)))

  ILookup
  (valAt [this k]
    (.valAt this k nil))

  (valAt [this k not-found]
    (if (integer? k)
      (nth this k not-found)
      not-found))

  IFn
  (invoke [this i]
    (nth this i))

  (invoke [this i not-found]
    (nth this i not-found))

  IObj
  (withMeta [this meta]
    (let [new-node (assoc node :meta meta)]
      (store-vector store new-node)))

  IMeta
  (meta [this]
    (:meta node))

  Object
  (toString [this]
    (str "#stored " (vec this)))

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
    (throw (UnsupportedOperationException. "Use conj instead of add for immutable vectors")))

  (add [this i o]
    (throw (UnsupportedOperationException. "Use assoc instead of add for immutable vectors")))

  (addAll [this coll]
    (throw (UnsupportedOperationException. "Use into instead of addAll for immutable vectors")))

  (addAll [this i coll]
    (throw (UnsupportedOperationException. "Use into instead of addAll for immutable vectors")))

  (clear [this]
    (throw (UnsupportedOperationException. "Use empty vector [] instead of clear for immutable vectors")))

  (removeAll [this coll]
    (throw (UnsupportedOperationException. "Use filterv instead of removeAll for immutable vectors")))

  (retainAll [this coll]
    (throw (UnsupportedOperationException. "Use filterv instead of retainAll for immutable vectors")))

  (set [this i o]
    (throw (UnsupportedOperationException. "Use assoc instead of set for immutable vectors")))

  (subList [this from-index to-index]
    (vec (take (- to-index from-index) (drop from-index (seq this)))))

  (iterator [this]
    (let [s (seq this)]
      (if s
        (.iterator ^Iterable s)
        (.iterator ^Iterable []))))

  (listIterator [this]
    (let [s (seq this)]
      (if s
        (.listIterator ^List (vec s))
        (.listIterator ^List []))))

  (listIterator [this index]
    (let [s (seq this)]
      (if s
        (.listIterator ^List (vec s) index)
        (.listIterator ^List [] index))))

  u/StoreDataStructure
  (to-clj [this]
    (mapv u/->clj (seq this))))

;; Helper functions
(defn lazy-elements
  "Returns a lazy sequence of all elements in the vector.

   This function provides efficient lazy access to vector elements,
   only loading them from the store when they are actually accessed.

   Parameters:
   - v: The StoreVector to get elements from

   Returns:
   - A lazy sequence of all elements in the vector"
  [v]
  (map #(nth v %) (range (count v))))

(defn store-vector
  "Creates a new StoreVector instance.

   This function creates a new StoreVector backed by the given store
   and node. The vector elements are loaded lazily when accessed.

   Parameters:
   - store: The chunk store to load data from
   - node: The node containing the vector data

   Returns:
   - A new StoreVector instance"
  [store node]
  (->StoreVector store node {}))

;; Define methods for lazy-load-node
(defmethod store/load-node :vector [store {:keys [children]}]
  (store-vector store {:type :vector :children children}))
