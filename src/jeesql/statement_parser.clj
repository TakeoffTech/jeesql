(ns jeesql.statement-parser
  (:require [clojure.java.io :as io]
            [clojure.string :refer [join]]
            [jeesql.util :refer [str-non-nil]]
            [clojure.string :as str])
  (:import [jeesql.types Query]))

(defn- replace-escaped-colon [string]
  (str/replace string #"\\:" ":"))

(def ^:const parameter-chars #{\- \? \_})

(defn- parse-statement
  [statement ns]
  (let [make-param (if ns
                     #(binding [*ns* ns]
                        (eval (read-string %)))
                     #(keyword (subs % 1)))]
    (as-> (reduce
           (fn [{:keys [tokens token parameter in-quoted? last-ch] :as state} ch]
             (let [[state done?] (if parameter
                                   (if (or (Character/isJavaLetterOrDigit ch)
                                           (parameter-chars ch)
                                           (and (= ch \:) (= parameter ":")))
                                     [(assoc state :parameter (str parameter ch)) true]
                                     [(assoc state
                                             :tokens (conj tokens (make-param parameter))
                                             :parameter nil) false])
                                   [state false])]
               (if done?
                 state
                 (assoc
                  (case ch
                    \"
                    (assoc state
                           :in-quoted? (not in-quoted?)
                           :token (str token ch))

                    \:
                    (if (and (not in-quoted?)
                             (not= last-ch \\)
                             (nil? parameter))
                      (assoc state
                             :tokens (if-not (empty? token)
                                       (conj tokens token)
                                       tokens)
                             :parameter (str ch)
                             :token nil)
                      (assoc state :token (str token ch)))

                    ;; default, append to parameter or token
                    (assoc state :token (str token ch)))
                  :last-ch ch))))
           {:tokens [] :token nil :parameter nil :last-ch nil :in-quoted? false}
           statement) parsed
      (if-not (empty? (:token parsed))
        (assoc parsed :tokens (conj (:tokens parsed) (:token parsed)))
        parsed)
      (if-not (empty? (:parameter parsed))
        (assoc parsed :tokens (conj (:tokens parsed) (make-param (:parameter parsed))))
        parsed)
      (:tokens parsed))))

(defmulti tokenize
  "Turn a raw SQL statement into a vector of SQL-substrings
  interspersed with clojure symbols or namespaced keywords for the query's parameters.

  For example, `(parse-statement \"SELECT * FROM person WHERE :age > age\")`
  becomes: `[\"SELECT * FROM person WHERE \" age \" > age\"]`"
  (fn [this ns] (type this)))

(defmethod tokenize String
  [this ns]
  (parse-statement this ns))

(defmethod tokenize Query
  [{:keys [statement]} ns]
  (parse-statement statement ns))
