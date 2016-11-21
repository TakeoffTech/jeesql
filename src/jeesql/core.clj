(ns jeesql.core
  (:require [jeesql.util :refer [resource-file-url slurp-from-classpath]]
            [jeesql.generate :refer [generate-var]]
            [jeesql.queryfile-parser :refer [parse-tagged-queries]]
            [jeesql.autoreload :refer [autoreload]]
            [clojure.string :as str]))

(defn defqueries
  "Defines several query functions, as defined in the given SQL file.
  Each query in the file must begin with a `-- name: <function-name>` marker,
  followed by optional comment lines (which form the docstring), followed by
  the query itself."
  ([filename]
     (defqueries filename {}))
  ([filename options]
   (let [file-url (resource-file-url filename)
         ns *ns*
         reload-fn (fn [content]
                     (doall (->> content
                                 parse-tagged-queries
                                 (map #(generate-var ns % options)))))]
     (autoreload file-url reload-fn)
     (reload-fn (slurp file-url)))))

(defmacro require-sql
  "Require-like behavior for jeesql, to prevent namespace pollution.
   Parameter is a list of [sql-source-file-name [:as alias] [:refer [var1 var2]]]
   At least one of :as or :refer is required
   Usage: (require-sql [\"sql/foo.sql\" :as foo-sql :refer [some-query-fn])"
  [[sql-file & {:keys [as refer]} :as require-args]]
  (when-not (or as refer)
    (throw (Exception. "Missing an :as or a :refer")))
  (let [current-ns (ns-name *ns*)
        ;; Keep this .sql file's defqueries in a predictable place:
        target-ns (-> (str "jeesql/" sql-file) (str/replace  #"/" ".") symbol)]
    `(do
       (ns-unalias *ns* '~as)
       (create-ns '~target-ns)
       (in-ns '~target-ns)
       (clojure.core/require '[jeesql.core])
       (jeesql.core/defqueries ~sql-file)
       (clojure.core/in-ns '~current-ns)
       ~(when as
          `(clojure.core/alias '~as '~target-ns))
       ~(when refer
          `(clojure.core/refer '~target-ns :only '~refer)))))
