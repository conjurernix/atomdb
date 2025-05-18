(ns atomdb.store
  (:import (clojure.lang IPersistentList IPersistentMap IPersistentSet IPersistentVector Keyword Ratio Symbol)
           (java.time ZonedDateTime)
           (java.time.format DateTimeFormatter)
           (java.util Date UUID)))

(defprotocol ChunkStore
  (put-chunk! [store data])
  (get-chunk [store hash]))

(defmulti persist (fn [_store v] (type v)))

(defmulti load-node (fn [_store node] (:type node)))

(defmethod persist IPersistentMap [store m]
  (let [children (into {} (map (fn [[k v]] [k (persist store v)]) m))
        node {:type :map :children children}]
    (put-chunk! store node)))

(defmethod persist IPersistentVector [store v]
  (let [children (mapv #(persist store %) v)
        node {:type :vector :children children}]
    (put-chunk! store node)))

(defmethod persist IPersistentSet [store s]
  (let [children (mapv #(persist store %) s)
        node {:type :set :children children}]
    (put-chunk! store node)))

(defmethod persist IPersistentList [store lst]
  (let [children (mapv #(persist store %) lst)
        node {:type :list :children children}]
    (put-chunk! store node)))

(defmethod persist Keyword [store k]
  (put-chunk! store {:type :keyword
                     :ns   (namespace k)
                     :name (name k)}))

(defmethod persist Symbol [store s]
  (put-chunk! store {:type :symbol :value (str s)}))

(defmethod persist String [store s]
  (put-chunk! store {:type :string :value s}))

(defmethod persist UUID [store u]
  (put-chunk! store {:type :uuid :value (str u)}))

(defmethod persist Date [store date]
  (put-chunk! store {:type :date :value (.format DateTimeFormatter/ISO_INSTANT (.toInstant date))}))

(defmethod persist BigDecimal [store v]
  (put-chunk! store {:type :bigdec :value (str v)}))

(defmethod persist Ratio [store v]
  (put-chunk! store {:type :ratio :value (str v)}))

(defmethod persist Boolean [store v]
  (put-chunk! store {:type :bool :value v}))

(defmethod persist :default [store v]
  (put-chunk! store {:type :leaf :value v}))

(defmethod load-node :map [store {:keys [children]}]
  (into {}
        (map (fn [[k h]]
               [k (load-node store (get-chunk store h))])
             children)))

(defmethod load-node :vector [store {:keys [children]}]
  (mapv #(load-node store (get-chunk store %)) children))

(defmethod load-node :set [store {:keys [children]}]
  (into #{}
        (map #(load-node store (get-chunk store %)) children)))

(defmethod load-node :list [store {:keys [children]}]
  (apply list (map #(load-node store (get-chunk store %)) children)))

(defmethod load-node :keyword [_ {:keys [ns name]}]
  (if ns
    (keyword ns name)
    (keyword name)))

(defmethod load-node :symbol [_ {:keys [value]}]
  (symbol value))

(defmethod load-node :string [_ {:keys [value]}]
  value)

(defmethod load-node :uuid [_ {:keys [value]}]
  (UUID/fromString value))

(defmethod load-node :date [_ {:keys [value]}]
  (Date/from (.toInstant (ZonedDateTime/parse value))))

(defmethod load-node :bigdec [_ {:keys [value]}]
  (bigdec value))

(defmethod load-node :ratio [_ {:keys [value]}]
  (read-string value))

(defmethod load-node :bool [_ {:keys [value]}]
  value)

(defmethod load-node :leaf [_ {:keys [value]}]
  value)
