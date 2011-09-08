(ns smallblog.data
	(:use		[smallblog.templates :only (markdownify)]
				[sandbar.auth :only (current-user, *sandbar-current-user*)]
				[sandbar.stateful-session :only (session-put!)])
	(:require	[clj-sql.core :as sql])
	(:import	[org.mindrot.jbcrypt BCrypt]))

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
			converted_content TEXT NOT NULL,
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
	(try
		(if-let [user (current-user)]
			user
			nil)
		(catch IllegalStateException e
			; (.printStackTrace e) XXX maybe this can be removed once cookies
				  ; are good
			nil)))

(defn -hash-pw [password]
	(BCrypt/hashpw password (BCrypt/gensalt)))

(defn -check-hashed [password hashed]
	(BCrypt/checkpw password hashed))

(defn get-login
	"return the login object if (email, password) points to a valid user"
	[email password]
	(sql/with-connection *db*
		(sql/with-query-results rs ["select * from login where email=?" email]
			(let [login (first rs)]
				(if (-check-hashed password (:password login))
					login
					nil)))))

(defn check-password
	"check to make sure the passwords are equal, and if the email parameter
	exists, that the password is valid for that account.  Throws exceptions on
	error."
	([newpassword confirmpassword]
		(check-password nil nil newpassword confirmpassword))
	([email password newpassword confirmpassword]
		(if (not (= newpassword confirmpassword))
			(throw (Exception. "passwords don't match")))
		(if (and
				(not (nil? email))
				(= nil (get-login email password)))
			(throw (Exception. "bad username or password")))))

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
		(if (nil? login)
			nil
			{:id (:id login) :name (:email login)
					:roles (concat (get-roles-for-user (:id login)) [:user])})))

(defn establish-session [email password]
	(binding [*sandbar-current-user*
			(login-for-session email password)]
		(session-put! :current-user *sandbar-current-user*)
		*sandbar-current-user*))

(defn change-password
	"update the password after calling check-password."
	[email password newpassword confirmpassword]
	(if (nil? email)
		(throw (Exception. "email was nil")))
	(check-password email password newpassword confirmpassword)
	(let [loginid (:id (get-login email password))]
		(sql/with-connection *db*
			(sql/update-values :login ["id=?" loginid]
					{:password (-hash-pw newpassword)}))))

(defn make-login
	"creates a new login and returns the id (instead of a populated object).
	In the 2-password form, also check-password."
	([email password confirm-password]
		(check-password password confirm-password)
		(make-login email password))
	([email password]
		(sql/with-connection *db*
			(sql/insert-record :login {:email email
						:password (-hash-pw password)}))))

(defn delete-login [id]
	(sql/with-connection *db*
		(sql/delete-rows :login ["id=?" id])))


(defn get-blogs
	"get the blogs owned by the given user"
	[userid]
	(sql/with-connection *db*
		(sql/with-query-results rs ["select * from blog where owner=?" userid]
			(doall rs))))

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
		(let [id (sql/insert-record :post
					{:title title :content content :blogid blogid
						:converted_content (markdownify content)})]
			(get-post blogid id))))
