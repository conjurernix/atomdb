(ns atomdb.serde.fressian-test
  (:require [clojure.test :refer :all])
  (:require [atomdb.serde.fressian :refer [fressian-serde]]
            [atomdb.serde.test-suite :as serde.suite]))

(deftest fressian-serde-roundtrip
  (serde.suite/serde-roundtrip (fressian-serde)))
