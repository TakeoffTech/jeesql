(defproject webjure/jeesql "0.4.6"
  :description "A Clojure library for using SQL"
  :url "https://github.com/tatut/jeesql"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/java.jdbc "0.6.2-alpha3"]
                 [org.clojure/core.async "0.2.385"]
                 [clojure-future-spec "1.9.0-alpha14"]]
  ;:pedantic? :abort
  :scm {:name "git"
        :url "https://github.com/tatut/jeesql"}
  :profiles {:dev {:dependencies [[expectations "2.1.3" :exclusions [org.clojure/clojure]]
                                  [org.apache.derby/derby "10.11.1.1"]]
                   :plugins [[lein-autoexpect "1.4.0"]
                             [lein-expectations "0.0.8"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [clojure-future-spec "1.9.0-alpha14"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0-alpha14"]]}}
  :aliases {"test-all" ["with-profile" "+1.8:+1.9" "do"
                        ["clean"]
                        ["expectations"]]
            "test-ancient" ["expectations"]})
