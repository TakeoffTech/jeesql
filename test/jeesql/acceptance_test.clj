(ns jeesql.acceptance-test
  (:require [expectations :refer :all]
            [clojure.java.jdbc :as jdbc]
            [jeesql.core :refer :all])
  (:import [java.sql SQLException SQLSyntaxErrorException SQLDataException]))

(def derby-db {:subprotocol "derby"
               :subname (gensym "memory:")
               :create true})

(defqueries "jeesql/sample_files/current_time.sql")
(expect java.util.Date
        (-> (current-time-query derby-db)
            first
            :time))

;;; Multiple-query workflow.
(defqueries
  "jeesql/sample_files/acceptance_test_combined.sql")

;; Create
(expect (create-person-table! derby-db))

;; Insert -> Select.
(expect {:1 1M} (insert-person<! derby-db {:name "Alice"
                                           :age 20}))
(expect {:1 2M} (insert-person<! derby-db {:name "Bob"
                                           :age 25}))
(expect {:1 3M} (insert-person<! derby-db {:name "Charlie"
                                           :age 35}))

(expect 3 (count (find-older-than derby-db {:age 10})))
(expect 1 (count (find-older-than derby-db {:age 30})))
(expect 0 (count (find-older-than derby-db {:age 50})))

;;; Select with IN.
(expect 2 (count (find-by-age derby-db {:age [20 35]})))

;; Update -> Select.
(expect 1 (update-age! derby-db {:age 38
                                 :name "Alice"}))
(expect 0 (update-age! derby-db {:age 38
                                 :name "David"}))

(expect 3 (count (find-older-than derby-db {:age 10})))
(expect 2 (count (find-older-than derby-db {:age 30})))
(expect 0 (count (find-older-than derby-db {:age 50})))


;; Test that default parameters work
(expect {:name "Alice" :age 38} (first (find-people-with-name-or-alice derby-db {})))
(expect {:name "Bob" :age 25} (first (find-people-with-name-or-alice derby-db {:name "Bob"})))


;; Delete -> Select.
(expect 1 (delete-person! derby-db {:name "Alice"}))

(expect 2 (count (find-older-than derby-db {:age 10})))
(expect 1 (count (find-older-than derby-db {:age 30})))
(expect 0 (count (find-older-than derby-db {:age 50})))

;; Check that our query with {:single? true} attribute
;; returns a single value
(expect 2 (count-people-older-than derby-db {:age 10}))

(expect 2 (count (find-older-than derby-db {:age 10})))


;; Failing transaction: Insert with abort.
;; Insert two rows in a transaction. The second throws a deliberate error, meaning no new rows created.


(expect SQLException
        (jdbc/with-db-transaction [connection derby-db]
          (insert-person<! connection {:name "Eamonn"
                                       :age 20})
          (insert-person<! connection {:name "Bob"
                                       :age 25} )))

(expect 2
        (count (find-older-than derby-db {:age 10})))

;;; Type error.
(expect SQLDataException
        (insert-person<! derby-db {:name 5
                                   :age "Eamonn"}))


;; Test return keys
(expect {:1 6M}
        (insert-person-return-keys<! derby-db {:name "Max" :age 71}))

;; Empty return-keys array is a silly test
(expect 1
        (insert-person-empty-return-keys<! derby-db {:name "Barbara" :age 44}))

;; Test that sets and vectors work as in parameters
(expect 2
        (count (find-people-with-ages derby-db {:ages #{71 44}})))
(expect 3
        (count (find-people-with-ages derby-db {:ages [35 25 44]})))


;; Drop
(expect (drop-person-table! derby-db))

;; Syntax error handling.
(defqueries "jeesql/sample_files/syntax_error.sql")

(expect SQLSyntaxErrorException
        (syntax-error derby-db))

;;; Incorrect parameters.
(expect AssertionError
        ;; :age missing
        (find-by-name-and-age-is-not derby-db {:name "foo"}))

(expect AssertionError
        (find-people-with-ages derby-db {}))
