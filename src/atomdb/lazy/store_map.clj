(ns atomdb.lazy.store-map
  (:require [atomdb.store :as store]
            [atomdb.utils :as u])
  (:import (clojure.lang Associative Counted IFn ILookup IMeta IObj IPersistentCollection IPersistentMap MapEntry Seqable)
           (java.util Map Map$Entry)))

;; Forward declarations
(declare store-map lazy-keys lazy-vals)

(deftype StoreMap [store node ^:volatile-mutable loaded-keys]
  IPersistentMap
  (assoc [this k v]
    (let [new-children (assoc (:children node) k (store/persist store v))
          new-node {:type :map :children new-children}]
      (store-map store new-node)))

  (without [this k]
    (let [new-children (dissoc (:children node) k)
          new-node {:type :map :children new-children}]
      (store-map store new-node)))

  (iterator [this]
    (.iterator ^Iterable (seq this)))

  IPersistentCollection
  (cons [this o]
    (cons o (seq this)))

  (empty [_]
    {})

  (equiv [this o]
    (and (instance? Map o)
         (= (count this) (count o))
         (every? (fn [[k v]] (= v (get o k))) (seq this))))

  Associative
  (containsKey [_this k]
    (contains? (:children node) k))

  (entryAt [this k]
    (when (contains? this k)
      (MapEntry. k (get this k))))

  ILookup
  (valAt [this k]
    (.valAt this k nil))

  (valAt [this k not-found]
    (if-let [hash (get-in node [:children k])]
      (if (and loaded-keys (contains? loaded-keys k))
        ;; Return cached value if available
        (get loaded-keys k)
        ;; Otherwise load from store and cache
        (let [value-node (store/get-chunk store hash)
              result (store/load-node store value-node)]
          ;; Cache the result
          (if loaded-keys
            (set! loaded-keys (assoc loaded-keys k result))
            (set! loaded-keys {k result}))
          result))
      not-found))

  Seqable
  (seq [this]
    (map (fn [k] (MapEntry. k (get this k)))
         (lazy-keys this)))

  Counted
  (count [this]
    (count (:children node)))

  IFn
  (invoke [this k]
    (get this k))

  (invoke [this k not-found]
    (get this k not-found))

  IObj
  (withMeta [this meta]
    (let [new-node (assoc node :meta meta)]
      (store-map store new-node)))

  IMeta
  (meta [this]
    (:meta node))

  Object
  (toString [this]
    (str "#stored " (into {} this)))

  (equals [this other]
    (.equiv this other))

  (hashCode [this]
    (reduce (fn [acc [k v]]
              (+ acc (bit-xor (hash k) (hash v))))
            0
            (seq this)))

  Map
  (size [this]
    (count this))

  (isEmpty [this]
    (zero? (count this)))

  (containsValue [this v]
    (boolean (some #(= v %) (lazy-vals this))))

  (get [this k]
    (get this k nil))

  (put [this k v]
    (throw (UnsupportedOperationException. "Use assoc instead of put for immutable maps")))

  (remove [this k]
    (throw (UnsupportedOperationException. "Use dissoc instead of remove for immutable maps")))

  (putAll [this m]
    (throw (UnsupportedOperationException. "Use merge or into instead of putAll for immutable maps")))

  (clear [this]
    (throw (UnsupportedOperationException. "Use empty map {} instead of clear for immutable maps")))

  (keySet [this]
    (set (lazy-keys this)))

  (values [this]
    (lazy-vals this))

  (entrySet [this]
    (set (map (fn [[k v]] (proxy [Map$Entry] []
                            (getKey [] k)
                            (getValue [] v)
                            (setValue [v] (throw (UnsupportedOperationException. "Use assoc instead of setValue for immutable maps")))))
              (seq this))))
  u/StoreDataStructure
  (to-clj [this]
    (->> (seq this)
         (map (fn [[k v]]
                [(u/->clj k)
                 (u/->clj v)]))
         (into {}))))

;; Helper functions
(defn lazy-keys [^StoreMap m]
  (clojure.core/keys (:children (.node m))))

(defn lazy-vals [^StoreMap m]
  (map #(get m %) (lazy-keys m)))

(defn store-map
  "Creates a new StoreMap instance.

   Parameters:
   - store: The chunk store to load data from
   - node: The node containing the map data"
  [store node]
  (->StoreMap store node {}))

;; Define methods for lazy-load-node
(defmethod store/load-node :map [store {:keys [children]}]
  (store-map store {:type :map :children children}))
