(ns jeesql.domain.company
  (:require [clojure.spec :as s]
            [clojure.future :refer :all]))


(s/def ::id nat-int?)
(s/def ::name string?)

(s/def ::company
  (s/keys :req [::id ::name]))
