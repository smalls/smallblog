(ns smallblog.data
	(:use		[sandbar.auth :only (current-user, *sandbar-current-user*)]
				[sandbar.stateful-session :only (session-put!)])
	(:require	[clj-sql.core :as sql]))

#_ (comment
	postgres
		bash$ createdb smallblog
		bash$ psql smallblog -h localhost
		psql$
		CREATE TABLE login (
			id SERIAL,
			email TEXT NOT NULL UNIQUE,
			password TEXT NOT NULL,
			PRIMARY KEY(id)
		);
		CREATE TABLE blog (
			id SERIAL,
			owner int NOT NULL REFERENCES login(id) ON DELETE CASCADE,
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

(defn get-current-user
	"gets the current user, or nil if none is defined"
	[]
	(if-let [user (current-user)]
		(current-user)
		nil))

; XXX only return if the password matches
(defn get-login [email password]
	(sql/with-connection *db*
		(sql/with-query-results rs ["select * from login where email=?" email]
			(first rs))))

(def owner-blog-prefix "owner-blog-")
(defn -role-keywords [a]
	(if (empty? a)
		[]
		(concat [(keyword (str owner-blog-prefix (:id (first a))))]
			(-role-keywords (rest a)))))

(defn get-roles-for-user [userid]
	(sql/with-connection *db*
		(sql/with-query-results rs ["select id from blog where owner=?" userid]
			(-role-keywords rs))))

(defn login-for-session [email password]
	(let [login (get-login email password)]
		{:id (:id login) :name (:email login)
				:roles (concat (get-roles-for-user (:id login)) [:user])}))

(defn establish-session [email password]
	(binding [*sandbar-current-user*
			(login-for-session email password)]
		(session-put! :current-user *sandbar-current-user*)
		*sandbar-current-user*))

(defn make-login
	"creates a new login and returns the id (instead of a populated object)"
	[email password]
	(sql/with-connection *db*
		(sql/insert-record :login {:email email :password password})))

(defn delete-login [id]
	(sql/with-connection *db*
		(sql/delete-rows :login ["id=?" id])))


(defn get-blog [id]
	(sql/with-connection *db*
		(sql/with-query-results rs ["select * from blog where id=?" id]
			(first rs))))

(defn make-blog [login_id, title]
	(sql/with-connection *db*
		(let [id (sql/insert-record :blog {:title title :owner login_id})]
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
