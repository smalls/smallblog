(defproject start-clojure "1.0.0-SNAPSHOT"
	:description		"smallblog"
	:dependencies		[[org.clojure/clojure "1.2.1"]
							[org.clojure/clojure-contrib "1.2.0"]
							[org.clojure/data.json "0.1.1"]
  				 			[compojure "0.6.4"]
							[clj-time "0.3.0"]
				 			[sqlitejdbc "0.5.6"]]
	:dev-dependencies	[[lein-ring "0.4.5"]]
	:ring				{:handler start-clojure.core/app})
