(ns jeesql.postgres
  "Utilities for PostgreSQL"
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]))

(def +query-plan-keyword+ (keyword "query plan"))

(defn report-slow-queries [operation-type duration-ms db query]
  (println "------- Slow " (name operation-type) " (" duration-ms "ms) ------ \n"
           (first query))
  (when (= :query operation-type)
    (println "PARAMS: " (pr-str (rest query)))
    (let [explain-query (concat [(str "EXPLAIN ANALYZE " (first query))]
                                (rest query))]
      (println "EXPLAIN ANALYZE OUTPUT FOR QUERY:\n"
               (str/join "\n"
                         (map +query-plan-keyword+
                              (jdbc/query db explain-query)))))))
