(ns jeesql.statement-parser
  (:require [clojure.java.io :as io]
            [clojure.string :refer [join]]
            [jeesql.util :refer [str-non-nil]]
            [clojure.string :as str])
  (:import [jeesql.types Query]))

(def ^{:doc "Regular expression to split statement into three parts: before the first parameter,
the parameter name and the rest of the statement. A parameter always starts with a single colon and
may contain alphanumerics as well as '-', '_' and '?' characters."}
  parameter #"(?s)(.*?[^:\\]):(\p{Alpha}[\p{Alnum}\_\-\?\./]*)(.*)")

(defn- replace-escaped-colon [string]
  (str/replace string #"\\:" ":"))

(defn- parse-statement
  [statement context]
  (loop [acc []
         rest-of-statement statement]
    (let [[_ before parameter after :as match] (re-find parameter rest-of-statement)]
      (if-not match
        (if rest-of-statement
          (conj acc (replace-escaped-colon rest-of-statement))
          acc)
        (recur (into acc
                     [(replace-escaped-colon before) (symbol parameter)])
               after)))))

(defmulti tokenize
  "Turn a raw SQL statement into a vector of SQL-substrings
  interspersed with clojure symbols for the query's parameters.

  For example, `(parse-statement \"SELECT * FROM person WHERE :age > age\")`
  becomes: `[\"SELECT * FROM person WHERE \" age \" > age\"]`"
  (fn [this] (type this)))

(defmethod tokenize String
  [this]
  (parse-statement this nil))

(defmethod tokenize Query
  [{:keys [statement]}]
  (parse-statement statement nil))
