(defproject webjure/jeesql "0.4.7.takeoff-patch"
  :description "A Clojure library for using SQL"
  :url "https://github.com/tatut/jeesql"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/java.jdbc "0.6.2-alpha3"]
                 [org.clojure/core.async "0.3.443"]]
  ;:pedantic? :abort
  :scm {:name "git"
        :url "https://github.com/tatut/jeesql"}
  :profiles {:dev {:dependencies [[expectations "2.1.3" :exclusions [org.clojure/clojure]]
                                  [org.apache.derby/derby "10.11.1.1"]]
                   :plugins [[lein-autoexpect "1.4.0"]
                             [lein-expectations "0.0.8"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}}
  :aliases {"test-all" ["with-profile" "+1.5:+1.6:+1.7:+1.8" "do"
                        ["clean"]
                        ["expectations"]]
            "test-ancient" ["expectations"]}
  :deploy-repositories [["releases"
                         {:url           "https://maven.tom.takeoff.com/artifactory/libs-release-local/"
                          :sign-releases false
                          :username      "tom-dev"
                          :password      "adRi32rrYMTaxeaYQIvh"}]
                        ["snapshots"
                         {:url      "https://maven.tom.takeoff.com/artifactory/libs-snapshot-local/"
                          :username "tom-dev"
                          :password "adRi32rrYMTaxeaYQIvh"}]])
