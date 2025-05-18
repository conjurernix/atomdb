(ns atomdb.serde.edn-test
  (:require [atomdb.serde.edn :refer [edn-serde]]
            [atomdb.serde.test-suite :as serde.suite]
            [clojure.test :refer :all]))

(deftest edn-serde-roundtrip
  (serde.suite/serde-roundtrip (edn-serde)))