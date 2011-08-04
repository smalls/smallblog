(ns start-clojure.data
	(:require	[clj-sql.core :as sql]))

#_ (comment
	postgres
		bash$ createdb smallblog
		bash$ psql smallblog -h localhost
		psql$
			CREATE TABLE posts (
				id SERIAL,
				title TEXT NOT NULL,
				content TEXT NOT NULL,
				created_date TIMESTAMP with time zone DEFAULT current_timestamp NOT NULL,
				PRIMARY KEY(id)
			);
)

(let [db-host "localhost"
		db-port "5432"
		db-name "smallblog"]
	(def *db* {:classname		"org.postgresql.Driver",
				:subprotocol	"postgresql",
				:subname		(str "//" db-host ":" db-port "/" db-name)
				;:user			"auser"
				;:password		"apw"
			}))


(defn get-post [id]
	(sql/with-connection *db*
		(sql/with-query-results rs ["select * from posts where id=?" id]
			(first rs))))

(defn get-posts [number, offset]
	(sql/with-connection *db*
		(sql/with-query-results rs
				["select * from posts order by created_date desc limit ? offset ?"
						number offset]
			(doall rs))))

(defn make-post [title, content]
	(sql/with-connection *db*
		(let [id (sql/insert-record :posts {:title title :content content})]
			(get-post id))))
