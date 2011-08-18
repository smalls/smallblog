(ns start-clojure.data
	(:require	[clj-sql.core :as sql]))

#_ (comment
	postgres
		bash$ createdb smallblog
		bash$ psql smallblog -h localhost
		psql$
CREATE TABLE blog (
id SERIAL,
title TEXT NOT NULL,
created_date TIMESTAMP with time zone DEFAULT current_timestamp NOT NULL,
PRIMARY KEY(id)
);
CREATE TABLE post (
id BIGSERIAL,
blogid int NOT NULL REFERENCES blog(id) ON DELETE CASCADE,
title TEXT NOT NULL,
content TEXT NOT NULL,
created_date TIMESTAMP with time zone DEFAULT current_timestamp NOT NULL,
PRIMARY KEY(id)
);

	   misc postgres notes
		   ;to describe a table: psql$ \d+ tablename
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


(defn get-blog [id]
	(sql/with-connection *db*
		(sql/with-query-results rs ["select * from blog where id=?" id]
			(first rs))))

(defn make-blog [title]
	(sql/with-connection *db*
		(let [id (sql/insert-record :blog {:title title})]
			(get-blog id))))

(defn delete-blog [id]
	(sql/with-connection *db*
		(sql/delete-rows :blog ["id=?" id])))


(defn get-post [blogid, id]
	(sql/with-connection *db*
		(sql/with-query-results rs
				["select * from post where blogid=? and id=?" (int blogid) id]
			(first rs))))

(defn get-posts [blogid, number, offset]
	(sql/with-connection *db*
		(sql/with-query-results rs
				["select * from post where blogid=? order by created_date desc limit ? offset ?"
						blogid number offset]
			(doall rs))))

(defn make-post [blogid, title, content]
	(sql/with-connection *db*
		(let [id (sql/insert-record :post {:title title :content content :blogid blogid})]
			(get-post blogid id))))
