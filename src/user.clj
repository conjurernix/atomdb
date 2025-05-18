(ns user
  (:require [clojure.tools.namespace.repl :as repl]))

(defn refresh []
  (repl/refresh-all))
