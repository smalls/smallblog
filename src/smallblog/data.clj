(ns smallblog.data
	(:use		[smallblog.templates :only (markdownify
								*image-full* *image-blog* *image-thumb*)]
				[clojure.contrib.duck-streams :only (to-byte-array)]
				[clojure.contrib.string :only (split)]
				[sandbar.auth :only (current-user *sandbar-current-user*
								allow-access?)]
				[sandbar.stateful-session :only (session-put!)])
	(:require	[clj-sql.core :as sql])
	(:import	[org.mindrot.jbcrypt BCrypt]
				[java.io ByteArrayInputStream ByteArrayOutputStream]
				[javax.imageio ImageIO]
				[java.awt.image BufferedImageOp]
				[com.thebuzzmedia.imgscalr Scalr ]))

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

		CREATE TABLE imageblob (
			id BIGSERIAL,
			image BYTEA,
			contenttype TEXT NOT NULL,
			owner int NOT NULL REFERENCES login(id) ON DELETE CASCADE,
			PRIMARY KEY(id)
		);

		CREATE TABLE image (
			id BIGSERIAL,
			owner int NOT NULL REFERENCES login(id) ON DELETE CASCADE,
			filename TEXT NOT NULL,
			title TEXT,
			description TEXT,
			fullimage BIGINT REFERENCES imageblob,
			blogwidthimage BIGINT REFERENCES imageblob,
			thumbnail BIGINT REFERENCES imageblob,
			created_date TIMESTAMP with time zone DEFAULT current_timestamp NOT NULL,
			PRIMARY KEY(id)
		);

		CREATE TABLE domain (
			id BIGSERIAL,
			domain TEXT NOT NULL UNIQUE,
			owner int NOT NULL REFERENCES login(id) ON DELETE CASCADE,
			blogid int REFERENCES blog(id) ON DELETE SET NULL,
			PRIMARY KEY(id)
		);


	   misc postgres notes
		   ;to describe a table: psql$ \d+ tablename
)

(let [db-host "localhost"
		db-port "5432"
		db-name "smallblog"]
	(def *db* {:classname		"org.postgresql.Driver"
				:subprotocol	"postgresql"
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

(defn blog-owner?
	"does the current user own the specified blog?"
	[blogid]
	(allow-access? #{(keyword (str owner-blog-prefix blogid))}
			(:roles (get-current-user))))

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
		(sql/with-query-results rs ["select * from blog where owner=? order by created_date asc" userid]
			(doall rs))))

(defn get-blog [id]
	(sql/with-connection *db*
		(sql/with-query-results rs ["select * from blog where id=?" id]
			(first rs))))

(defn make-blog [login_id title]
	(sql/with-connection *db*
		(let [id (sql/insert-record :blog {:title title :owner login_id})]
			(get-blog id))))

(defn delete-blog [id]
	(sql/with-connection *db*
		(sql/delete-rows :blog ["id=?" id])))


(defn get-post [blogid id]
	(sql/with-connection *db*
		(sql/with-query-results rs
				["select * from post where blogid=? and id=?" (int blogid) id]
			(first rs))))

(defn get-posts [blogid number offset]
	(sql/with-connection *db*
		(sql/with-query-results rs
				["select * from post where blogid=? order by created_date desc limit ? offset ?"
						blogid number offset]
			(doall rs))))

(defn make-post
	"create a post, retrieve the newly inserted post"
	[blogid title content]
	(sql/with-connection *db*
		(let [id (sql/insert-record :post
					{:title title :content content :blogid blogid
						:converted_content (markdownify content)})]
			(get-post blogid id))))

(defn get-content-type
	"get the image content type and format; map gif to png, otherwise make a
		best effort to match the type"
	[full-image-content-type]
	(let [mime-types (ImageIO/getWriterMIMETypes)
			formats (ImageIO/getWriterFormatNames)
			desired-content-type (if (= "image/gif" full-image-content-type)
							"image/png"
							full-image-content-type)
			desired-format (last (split #"\/" desired-content-type))]
		(cond
			(and
				(some #(= desired-content-type %) mime-types)
				(some #(= desired-format %) formats))
				[desired-content-type desired-format]
			(and
				(some #(= "image/png" %) mime-types)
				(some #(= "png" %) formats))
				["image/png" "png"]
			:else (throw (Exception.
					(str "unknown mime type: " desired-content-type))))))

(defn do-scale
	"scales the image, returns the bytes of the image"
	[imagestream full-img-content-type width userid]
		(let [fullimg (ImageIO/read imagestream)
				scaledimg (Scalr/resize fullimg width
							(make-array BufferedImageOp 0))
				mimetype (get-content-type full-img-content-type)
				baos (ByteArrayOutputStream.)]
			(try
				(ImageIO/write scaledimg (last mimetype) baos)
				{:image (.toByteArray baos) :contenttype (first mimetype)
					:owner userid}
				(finally (.close baos)))))

(defn scale-image-to-bytes
	[imagebytes full-img-content-type width userid]
	(let [bais (ByteArrayInputStream. imagebytes)]
		(try
			(do-scale bais full-img-content-type width userid)
			(finally (.close bais)))))

(defn make-image
	"make an image, returns the id"
	[filename title description content-type path userid]
	(let [imagebytes (to-byte-array path)]
		(sql/with-connection *db* (sql/transaction
			(let [fullimageid (sql/insert-record :imageblob
						{:contenttype content-type
							:image imagebytes :owner userid})
					thumbimageid (sql/insert-record :imageblob
							(scale-image-to-bytes imagebytes content-type 150
								userid))
					blogimageid (sql/insert-record :imageblob
							(scale-image-to-bytes imagebytes content-type 550
								userid))]
				(sql/insert-record :image
					{:filename filename :title title :description description
						:owner userid :fullimage fullimageid
						:blogwidthimage blogimageid
						:thumbnail thumbimageid}))))))

(defn get-image-results
	"get the image result object, including the imageblob specified by id.
		Must have an open db connection."
	[image blobid]
	(sql/with-query-results rs ["select * from imageblob where id=?" blobid]
		(let [blob (first rs)]
			(if (nil? blob)
				nil
				{:filename (:filename image)
					:title (:title image)
					:description (:description image)
					:image (ByteArrayInputStream. (:image blob))
					:content-type (:contenttype blob)}))))

(defn get-image
	"returns a map with :filename, :title, :description (from image table)
		and :image (as a stream), :content-type from imageblob"
	[imageid res]
	(sql/with-connection *db*
		(sql/with-query-results rs ["select * from image where id=?" imageid]
			(let [image (first rs)]
				(cond
					(nil? image) nil
					(= *image-full* res) (get-image-results image
												(:fullimage image))
					(= *image-blog* res) (get-image-results image
												(:blogwidthimage image))
					(= *image-thumb* res) (get-image-results image
												(:thumbnail image))
					:else nil)))))

(defn get-images
	"returns some basic info about images with the specified offset for the
		current user"
	[userid number offset]
	(sql/with-connection *db*
		(sql/with-query-results rs
				["select id, filename, title, description from image where owner=? order by created_date desc limit ? offset ?"
						userid number offset]
			(doall rs))))

(defn make-domain
	"create a domain, return the id"
	([domainname userid]
		(make-domain domainname userid nil))
	([domainname userid blogid]
		(sql/with-connection *db*
			(let [id (sql/insert-record :domain
						{:domain domainname :owner userid :blogid blogid})]
				id))))

(defn get-domain [domainname]
	(sql/with-connection *db*
		(sql/with-query-results rs ["select * from domain where domain=?" domainname]
			(first rs))))

(defn get-user-domains [loginid]
	(sql/with-connection *db*
		(sql/with-query-results rs ["select * from domain where owner=?" loginid]
			(doall rs))))

(defn change-domain
	"change the specified domain to point at the new blog"
	[userid domainid blogid]
	(sql/with-connection *db*
		(sql/update-values :domain ["id=? and owner=?" domainid userid]
				{:blogid blogid})))
