(defproject smallblog "1.0.0-SNAPSHOT"
            :description    "smallblog"
            :dependencies   [[org.clojure/clojure "1.2.1"]
                             [org.clojure/clojure-contrib "1.2.0"]
                             [ring "0.3.11"]
                             [ring-json-params "0.1.3"]
                             [compojure "0.6.5"]
                             [clj-time "0.3.0"]
                             [org.clojure/java.jdbc "0.1.1"]
                             [enlive "1.0.0"]
                             [rhino/js "1.7R2"]
                             [sandbar/sandbar "0.3.0"]
                             [org.mindrot/jbcrypt "0.3m"]
                             [com.thebuzzmedia/imgscalr-lib "3.2"]
                             [postgresql/postgresql "8.4-702.jdbc4"]
                             [net.java.dev.jets3t/jets3t "0.8.1"]
                             [drift "1.4.1"]
                             [com.draines/postal "1.7.0"]]
            :dev-dependencies   [[vimclojure/server "2.2.0"]
                                 [org.clojars.autre/lein-vimclojure "1.0.0"]
                                 [drift "1.4.1"]]
            :run-aliases    {:server smallblog.core
                             :migrate smallblog.dbmigrate}
            :repositories   {"The Buzz Media Maven Repository" ; imgscalr
                             "http://maven.thebuzzmedia.com"})
