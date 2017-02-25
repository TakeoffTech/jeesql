(ns jeesql.domain.address
  (:require [clojure.spec :as s]
            [clojure.future :refer :all]))

(s/def ::address (s/keys :req [::street ::postal-code ::country]))

(s/def ::street string?)
(s/def ::postal-code string?)
(s/def ::country (s/and string? #(= 2 (count %))))
