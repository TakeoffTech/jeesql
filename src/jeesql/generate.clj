(ns jeesql.generate
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [clojure.string :refer [join lower-case]]
            [jeesql.util :refer [create-root-var]]
            [jeesql.types :refer [map->Query]]
            [jeesql.statement-parser :refer [tokenize]]
            [clojure.core.async :as async]
            [clojure.spec :as s])
  (:import [jeesql.types Query]))

(def in-list-parameter?
  "Check if a type triggers IN-list expansion."
  (some-fn list? vector? seq? set?))

(defn- args-to-placeholders
  [args]
  (if (in-list-parameter? args)
    (if (empty? args)
      "NULL"
      (clojure.string/join "," (repeat (count args) "?")))
    "?"))

(defn- analyse-statement-tokens
  [tokens]
  {:expected-keys (set (map keyword (filter symbol? tokens)))
   ;; Positional ? parameters are no longer supported
   :expected-positional-count 0})

(defn- positional-parameter-list [tokens]
  (distinct (filter symbol? tokens)))

(defn expected-parameter-list
  [query ns]
  (as-> query q
    (tokenize q ns)
    (remove string? q)
    (distinct q)))

(defn rewrite-query-for-jdbc
  [tokens initial-args]
  (let [[final-query final-parameters consumed-args]
        (reduce (fn [[query parameters args] token]
                  (if (string? token)
                    [(str query token)
                     parameters
                     args]
                    (let [[arg new-args] [(get args token) args]]
                      [(str query (args-to-placeholders arg))
                       (vec (if (in-list-parameter? arg)
                              (concat parameters arg)
                              (conj parameters arg)))
                       new-args])))
                ["" [] initial-args]
                tokens)]
    (concat [final-query] final-parameters)))

;; Maintainer's note: clojure.java.jdbc.execute! returns a list of
;; rowcounts, because it takes a list of parameter groups. In our
;; case, we only ever use one group, so we'll unpack the
;; single-element list with `first`.
(defn execute-handler
  [db sql-and-params]
  (first (jdbc/execute! db sql-and-params)))

(defn insert-handler
  [db statement-and-params]
  (jdbc/db-do-prepared-return-keys db statement-and-params))

(defn insert-handler-return-keys
  [return-keys db [statement & params]]
  (with-open [ps (jdbc/prepare-statement (jdbc/get-connection db) statement
                                         {:return-keys return-keys})]
    (jdbc/db-do-prepared-return-keys db (cons ps params))))

(defn query-handler
  [row-fn db sql-and-params]
  (jdbc/query db sql-and-params
              {:identifiers lower-case
               :row-fn row-fn
               :result-set-fn doall}))

(defn query-handler-single-value
  [db sql-and-params]
  (jdbc/query db sql-and-params
              {:row-fn (comp val first seq)
               :result-set-fn first}))

(defn query-handler-stream
  [fetch-size row-fn db result-channel sql-and-params]
  (jdbc/db-query-with-resultset
   db sql-and-params
   (fn [rs]
     (loop [[row & rows] (jdbc/result-set-seq rs)]
       (if-not row
         ;; No more rows, close the channel
         (async/close! result-channel)
         ;; have more rows to send
         (when (async/>!! result-channel (row-fn row))
           ;; channel is not closed yet
           (recur rows)))))
   {:fetch-size fetch-size}))

(def ^:private supported-attributes #{:single? :return-keys :default-parameters
                                      :fetch-size :row-fn})

(defn- check-attributes [attributes]
  (when attributes
    (doseq [key (keys attributes)]
      (assert (supported-attributes key)
              (str "Unsupported attribute " key
                   ". Valid attributes are: "
                   (join ", " supported-attributes))))))

(defn- generate-spec [ns args]
  (let [{req-un true
         req false} (group-by (comp nil? namespace) args)]
    (eval `(s/keys :req-un [~@(map #(keyword (name (ns-name ns)) (name %)) req-un)]
                   :req [~@req]))))

(defn generate-query-fn
  "Generate a function to run a query.

  - If the query name ends in `!` it will call `clojure.java.jdbc/execute!`,
  - If the query name ends in `<!` it will call `clojure.java.jdbc/insert!`,
  - otherwise `clojure.java.jdbc/query` will be used."
  [ns {:keys [name docstring statement attributes]
       :as query}
   {:keys [report-slow-queries slow-query-threshold-ms]
    :as query-options}]
  (assert name      "Query name is mandatory.")
  (assert statement "Query statement is mandatory.")
  (check-attributes attributes)
  (let [slow-query-threshold-ms (or slow-query-threshold-ms 2000)
        attributes (binding [*ns* ns] (eval attributes))
        stream? (:fetch-size attributes)
        row-fn (or (:row-fn attributes) identity)
        operation-type (cond (= (take-last 2 name) [\< \!]) :insert
                             (= (last name) \!) :update
                             :default :query)
        jdbc-fn (cond
                  (= (take-last 2 name) [\< \!]) (if-let [rk (:return-keys attributes)]
                                                   (partial insert-handler-return-keys rk)
                                                   insert-handler)
                  (= (last name) \!) execute-handler
                  (:single? attributes) query-handler-single-value
                  stream? (partial query-handler-stream (:fetch-size attributes) row-fn)
                  :else (partial query-handler row-fn))
        required-args (expected-parameter-list statement ns)
        required-arg-symbols (map (comp symbol clojure.core/name)
                                  required-args)
        tokens (tokenize statement ns)
        spec (generate-spec ns required-args)
        real-fn (fn [connection args]
                  (assert connection
                          (format "First argument must be a database connection to function '%s'."
                                  name))
                  (assert (s/valid? spec args)
                          (format "Query argument mismatch.\n%s"
                                  (s/explain-str spec args)))
                  (let [start (System/nanoTime)
                        jdbc-query (rewrite-query-for-jdbc tokens args)
                        result (jdbc-fn connection jdbc-query)
                        time (/ (double (- (System/nanoTime) start)) 1000000.0)]
                    (when (and report-slow-queries (> time slow-query-threshold-ms))
                      (report-slow-queries operation-type time connection jdbc-query))
                    result))
        [display-args generated-function]
        (let [default-parameters (or (:default-parameters attributes) {})
              named-args (when-not (empty? required-arg-symbols)
                           {:keys (vec required-arg-symbols)})]
          (cond
            (nil? named-args)
            [(list ['connection])
             (fn query-wrapper-fn-noargs [connection]
               (real-fn connection {}))]

            stream?
            [(list ['connection 'result-channel named-args])
             (fn query-wrapper-streaming
               [connection result-channel args]
               (jdbc-fn connection result-channel
                        (rewrite-query-for-jdbc tokens
                                                (merge default-parameters args))))]

            (and (:positional? query-options)
                 (< (count required-args) 20))
            (let [params (positional-parameter-list tokens)
                  keywords (map (comp keyword clojure.core/name) params)]
              [(list ['connection named-args]
                     (vec (concat ['connection] params)))
               (fn query-wrapper-fn-positional
                 [connection & args]
                 (if (and (= 1 (count args))
                          (map? (first args)))
                   ;; One argument that is a map
                   (real-fn connection (merge default-parameters
                                              (first args)))

                   ;; Given all positional args
                   (real-fn connection (zipmap keywords args))))])

            :default
            [(list ['connection named-args])
             (fn query-wrapper-fn [connection args]
               (real-fn connection (merge default-parameters args)))]))]
    (with-meta generated-function
      (merge {:name name
              :arglists display-args
              ::source (str statement)}
             (when docstring
               {:doc docstring})))))

(defn generate-var
  ([this options] (generate-var *ns* this options))
  ([ns this options]
   (create-root-var ns (:name this)
                    (generate-query-fn ns this options))))
