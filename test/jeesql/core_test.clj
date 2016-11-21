(ns jeesql.core-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :refer [upper-case]]
            [expectations :refer :all]
            [jeesql.core :refer :all]))

(def derby-db {:subprotocol "derby"
               :subname (gensym "memory:")
               :create true})

;;; Test-environment check. Can we actually access the test DB?
(expect (more-> java.sql.Timestamp (-> first :1))
        (jdbc/query derby-db
                    ["SELECT CURRENT_TIMESTAMP FROM SYSIBM.SYSDUMMY1"]))

(defqueries "jeesql/sample_files/current_time.sql")

(defqueries "jeesql/sample_files/mixed_parameters.sql")

;;; Test querying.
(expect (more-> java.util.Date
                (-> first :time))
        (current-time-query derby-db))

(expect (more-> java.util.Date
                (-> first :time))
        (mixed-parameters-query derby-db
                                {:value1 1
                                 :value2 2}))

(expect empty?
        (mixed-parameters-query derby-db
                                {:value1 1
                                 :value2 2
                                 :value3 0}))

;;; Test comment rules.
(defqueries "jeesql/sample_files/inline_comments.sql")

(expect (more-> java.util.Date :time
                "Not -- a comment" :string)
        (first (inline-comments-query derby-db)))

;;; Test Metadata.
(expect (more-> "Just selects the current time.\nNothing fancy." :doc
                'current-time-query :name
                (list '[connection]) :arglists)
        (meta (var current-time-query)))

(expect (more->  "Here's a query with some named and some anonymous parameters.\n(...and some repeats.)" :doc
                 'mixed-parameters-query :name
                 true (-> :arglists list?)
                 ;; TODO We could improve the clarity of what this is testing.
                 1 (-> :arglists count)

                 2 (-> :arglists first count)
                 #{'value1 'value2 'value3} (-> :arglists first second   :keys set))
        (meta (var mixed-parameters-query)))

;; Running a query in a transaction and using the result outside of it should work as expected.
(expect-let [[{time :time}] (jdbc/with-db-transaction [connection derby-db]
                              (current-time-query connection))]
  java.util.Date
  time)

;;; Check defqueries returns the list of defined vars.
(expect-let [return-value (defqueries "jeesql/sample_files/combined_file.sql")]
  (repeat 4 clojure.lang.Var)
  (map type return-value))

;;; SQL's quoting rules.
(defqueries "jeesql/sample_files/quoting.sql")

(expect "'can't'"
        (:word (first (quoting derby-db))))

;;; Switch into a fresh namespace
(ns jeesql.core-test.test-require-sql
  (:require [expectations :refer :all]
            [jeesql.core :refer :all]))

(require-sql ["jeesql/sample_files/combined_file.sql" :as combined])

(expect var? #'combined/edge)

(expect first (combined/edge jeesql.core-test/derby-db))

(require-sql ["jeesql/sample_files/combined_file.sql" :refer [the-time]])

(expect var? #'the-time)

(expect first (the-time jeesql.core-test/derby-db))
