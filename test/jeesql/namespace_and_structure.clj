(ns jeesql.namespace-and-structure
  (:require  [expectations :as e]
             [jeesql.domain.address :as address]
             [jeesql.domain.company :as company]
             [jeesql.core :refer [defqueries]]))

(def db {:subprotocol "derby"
         :subname (gensym "memory:")
         :create true})

(defqueries "jeesql/sample_files/namespace_and_structure.sql"
  {:structure true})
