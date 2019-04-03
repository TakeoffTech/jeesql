# Jeesql - a fork of yesql with an upgrade path

Jeesql is a Clojure library for _using_ SQL. Please see krisajenkins/yesql for yesql documentation.
I will only document the notable differences here.

[![Build Status](https://travis-ci.org/tatut/jeesql.svg?branch=master)](https://travis-ci.org/tatut/jeesql)

[![Clojars Project](http://clojars.org/webjure/jeesql/latest-version.svg)](http://clojars.org/webjure/jeesql)

## Notable differences to yesql 0.5 branch

* Connection is *always* first parameter to query function
* Connection cannot be given in defqueries phase
* Generate positional arguments when given :positional? true argument (for upgrade path from yesql <= 0.4 versions)
* No call-options map
* If SQL clause has no arguments, the function will have only 1 arity (the connection parameter)
* Query attributes in SQL files (see below)
* defquery support is removed (only defqueries supported)
* automatic reload of defqueries files
* No instaparse dependency (see parsing below)
* Support a slow query reporter

## Positional arguments

The current yesql approach has moved away from positional arguments in favor of map arguments.
This is fine, but I have a large project with thousands of query fn calls and I wanted
to have an upgrade path that supports both ways.

Now passing in {:positional? true} argument to defqueries will also generate the
positional arguments.

```SQL
-- name: find-by-name-and-age-range
-- Test positional paremeter order with these 3 args
SELECT *
FROM person
WHERE name LIKE :name AND age >= :age_min AND age <= :age_max
```

Will generate a method that can be called in two ways:

```clojure

;; The map parameters still work as expected
(find-by-name-and-age-range db {:name "Foo%" :age_min 20 :age_max 40})

;; Positional arguments are provided in the order they appear in the SQL
(find-by-name-and-age-range db "Foo%" 20 40)
```

## Query attributes

Jeesql adds the possibility to annotate queries with attributes that change the
way the query is processed. I think this is better to do at the query site instead
of at the call site (with yesql's call-options).

Attributes are placed between the name line and the docstring.

Currently two attributes are supported: single? and return-keys.


### single?

Single? attribute (for selects) changes the result set processing to return a
single value.

```SQL
-- name: count-people-older-than
-- single?: true
-- Count how many people are older than the given age
SELECT COUNT(*) FROM people WHERE age > :age
```

Will generate a function that returns a the count number as a single value when
called, instead of a sequence of maps.

### return-keys

Return keys (for insert) is a vector of strings for keys to return.
This is mainly for Oracle users. Remember to use the postfix <! in the function name 
as yesql uses this naming convention to identify it needs to return values
from the insert.

```SQL
-- name: insert-foo<!
-- return-keys: ["FOO_ID"]
-- Insert a foo and return FOO_ID
INSERT INTO foo (bar) VALUES (:bar)
```

Will generate a function that sets the prepared statement return keys
attribute.

### fetch-size

Fetch size sets the JDBC fetch size and streams the results a core.async channel. Generates a
function with an additional result-channel parameter after the connection parameter. The client
must provide the channel when calling the query function.

If the result channel is closed by the consumer before all results are processed, the streaming is
stopped and the result set will be closed. This makes it easy for the consumer to stop early.

```SQL
-- name: fetch-logs-for-event
-- fetch-size: 100
SELECT * FROM log WHERE event = :event;
```

Generates a function with 3 parameters: the connection, the result channel and the query
parameters map.

```clojure
(let [ch (async/chan 100)]
  (async/thread (fetch-logs-for-event db ch {:event "login"}))
  ;; do something with ch
  )
```

### row-fn

Specifying row-fn changes the way each row is processed. As attributes are evaluated in the same
namespace as the defqueries call, you can refer to functions for post-processing. This can be used,
for example, to process values to a more suitable format without having to do it after each call.

```clojure
(ns my.queries
  (:require [jeesql.core :refer [defqueries]]))

(defn extract-tags
  "Turn tags from a JDBC array into a set of strings"
  [{tags :tags :as row}]
  (assoc row
         :tags (set (.getArray tags))))

(defqueries "my/queries/queries.sql")

...
```

And in queries.sql file:
```SQL
-- name: fetch-item-by-id
-- row-fn: extract-tags
SELECT name, category, tags FROM item WHERE id = :id

...
```

## Automatic reload

Calls to defqueries will register the files for watching. If the file changes the
file is reloaded. This removes the need to constantly switch between the SQL file
and the Clojure file to eval again when developing.


## Parsing

Jeesql uses a simple regex based parser for reading multiple queries from a file and
to tokenize a single SQL statement.

Parameters always start with a (single) colon and the first character must be an alpha (a-zA-Z)
the remaining characters may be alphanumeric or one of '-', '_', '?'.

For example, the following parameter names are all fine:

* :foo
* :foo-bar
* :is-foo?
* :foo123
* :foo_and_bar

Note that as the name may contain the '-' character, care must be taken not to mistake it
for arithmetic in SQL, so: *:age-1* is interpreted as the parameter "age-1" not age minus 1.

Colon characters can be escaped by prefixing it with a single backslash.

## Slow query reporting

If a :report-slow-queries key is provided in the options map to defqueries,
queries that take longer to execute than the defined threshold (:slow-query-threshold-ms key,
defaults to 2000ms) are reported using that function.

The function takes 4 arguments: the operation type (keyword :query, :insert or :update), the duration
(in milliseconds) of the operation, the connection and the query (string and params).

There is an example of running an automatic EXPLAIN ANALYZE for PostgreSQL in jeesql/postgres.clj.
