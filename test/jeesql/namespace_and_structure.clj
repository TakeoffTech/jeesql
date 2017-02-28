(ns jeesql.namespace-and-structure
  (:require  [expectations :refer :all]
             [jeesql.domain.address :as address]
             [jeesql.domain.company :as company]
             [jeesql.core :refer [defqueries]]))

(def db {:subprotocol "derby"
         :subname (gensym "memory:")
         :create true})

(defqueries "jeesql/sample_files/namespace_and_structure.sql"
  {:structure true})s

(expect 0 (create-company-table! db))
y
(expect {:1 1M} (insert-company<! db
                                  {::company/name "Acme Inc"
                                   :visiting_street_address "visit 1"
                                   :visiting_postal_code "90123"
                                   :visiting_country "FI"

                                   :billing_street_address "billing 2"
                                   :billing_postal_code "90666"
                                   :billing_country "FI"}))

(expect {::company/id 1
         ::company/name "Acme Inc"
         ::company/visiting-address {::address/street "visit 1"
                                     ::address/postal-code "90123"
                                     ::address/country "FI"}
         ::company/billing-address {::address/street "billing 2"
                                    ::address/postal-code "90666"
                                    ::address/country "FI"}}
        (first (companies-in-country db {::address/country "FI"})))
