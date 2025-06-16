
(ns atomdb.data.store-vector
  (:require [atomdb.store :as store]
            [atomdb.utils :as u])
  (:import (clojure.lang Counted IFn IMeta IObj IPersistentCollection IPersistentVector Indexed Seqable)
           (java.util List)))

;; Forward declarations
(declare store-vector)

(deftype StoreVector [store node ^:volatile-mutable loaded-values]
  IPersistentVector
  (assocN [_this i v]
    (let [new-children (assoc (:children node) i (store/persist store v))
          new-node {:type :vector :children new-children}]
      (store-vector store new-node)))

  (cons [this o]
    (let [new-children (assoc (:children node) (count this) (store/persist store o))
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
      (map #(nth this %) (range (count this)))))

  Counted
  (count [this]
    (count (:children node)))

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
    (.iterator ^Iterable (seq this)))

  (listIterator [this]
    (.listIterator ^List (vec (seq this))))

  (listIterator [this index]
    (.listIterator ^List (vec (seq this)) index))

  u/StoreDataStructure
  (to-clj [this]
    (mapv u/->clj (seq this))))

(defn store-vector
  "Creates a new StoreVector instance.

   Parameters:
   - store: The chunk store to load data from
   - node: The node containing the vector data"
  [store node]
  (->StoreVector store node {}))

;; Define methods for lazy-load-node
(defmethod store/load-node :vector [store {:keys [children]}]
  (store-vector store {:type :vector :children children}))