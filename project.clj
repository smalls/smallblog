(defproject start-clojure "1.0.0-SNAPSHOT"
	:description		"smallblog"
	:dependencies		[[org.clojure/clojure "1.2.1"]
							[org.clojure/clojure-contrib "1.2.0"]
  				 			[compojure "0.6.4"]
							[ring-json-params "0.1.3"]
							[clj-time "0.3.0"]
				 			[clj-sql "0.0.5"]
				 			[postgresql/postgresql "9.0-801.jdbc4"]]
	:dev-dependencies	[[lein-ring "0.4.5"]]
	:ring				{:handler start-clojure.core/app})
