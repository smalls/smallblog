(ns start-clojure.data
	(:require	[clojure.contrib.sql :as sql]))

#_ (comment
	CREATE TABLE posts (
		id INTEGER PRIMARY KEY ASC,
		title TEXT NOT NULL,
		content TEXT NOT NULL,
		created_date DATE DEFAULT (datetime()) NOT NULL
	);
)

(def db-path  "test/start_clojure/test.sqlite")
(def db {:classname  "org.sqlite.JDBC",
				 :subprotocol   "sqlite",
				 :subname       db-path})

(def +transactions-query+ "select * from my_table")


(defn make-post [title, content]
	(sql/with-connection db
		(sql/insert-values :posts [:title :content]
			[title content])))

(defn get-posts [number, offset]
	(sql/with-connection db
		(sql/with-query-results rs
				["select * from posts order by created_date desc limit ? offset ?"
						number offset]
					(doall rs))))
