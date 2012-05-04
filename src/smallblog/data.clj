(ns smallblog.data
    (:use [smallblog.templates :only (markdownify *image-full* *image-blog* *image-thumb*)]
          [smallblog.config]
          [clojure.contrib.duck-streams :only (to-byte-array)]
          [clojure.contrib.string :only (split)]
          [sandbar.auth :only (current-user *sandbar-current-user* allow-access?)]
          [sandbar.stateful-session :only (session-put!)]
          [ring.util.mime-type :only (default-mime-types)]
          [postal.core :only (send-message)])
    (:require [clojure.java.jdbc :as sql]
              [clojure.java.jdbc.internal :as sql-int]
              [clojure.string :as str]
              [clj-time.format :as clj-time-format])
    (:import [java.util Calendar]
             [java.io File ByteArrayInputStream ByteArrayOutputStream FileInputStream]
             [java.sql.Timestamp]
             [javax.imageio ImageIO]
             [java.awt.image BufferedImageOp]
             [org.mindrot.jbcrypt BCrypt]
             [com.thebuzzmedia.imgscalr Scalr]
             [org.jets3t.service.security AWSCredentials]
             [org.jets3t.service.utils ServiceUtils]
             [org.jets3t.service.model S3Object]
             [org.jets3t.service.impl.rest.httpclient RestS3Service]))


(defn get-db-version
    "gets the current db migration version"
    []
    (sql/with-connection
        *db*
        (sql/with-query-results
            rs
            ["select version from migration_version order by version desc"]
            (:version (first rs)))))

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
            (:id (sql/insert-record :login {:email email
                                       :password (-hash-pw password)})))))

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

(defn make-blog
    "create a new blog, and return the row"
    [login_id title]
    (sql/with-connection *db*
        (sql/insert-record :blog {:title title :owner login_id})))

(defn delete-blog [id]
    (sql/with-connection *db*
        (sql/delete-rows :blog ["id=?" id])))


(defn get-post
    ([blogid id]
     (sql/with-connection
         *db*
         (sql/with-query-results
             rs
             ["select * from post where blogid=? and id=?" (int blogid) id]
             (first rs))))
    ([blogid title year month]
     (sql/with-connection
         *db*
         (sql/with-query-results
             rs
             [(str "select * from post where blogid=? and title=?"
                   " and extract(year from created_date)=?"
                   " and extract(month from created_date)=?"
                   )
              blogid title year month] (comment title year month) ;]
             (first rs)))))



(defn count-posts [blogid]
    (sql/with-connection *db*
                         (sql/with-query-results
                             rs
                             ["select count(*) from post where blogid=?" blogid]
                             (:count (first rs)))))

(defn get-posts [blogid number offset]
    (sql/with-connection *db*
        (sql/with-query-results rs
            ["select * from post where blogid=? order by created_date desc limit ? offset ?"
             blogid number offset]
            (doall rs))))

(defn make-post
    "create a post, retrieve the newly inserted post (the entire row)"
    [blogid title content created-date]
    (let [with-date (not (nil? created-date))
          sql (if with-date
                  "INSERT INTO post (blogid, title, content, converted_content, created_date) VALUES (?, ?, ?, ?, ?)"
                  "INSERT INTO post (blogid, title, content, converted_content) VALUES (?, ?, ?, ?)")]
        (sql/with-connection
            *db*
            (let [stmt (sql/prepare-statement (sql/connection) sql :return-keys true)]
                (.setObject stmt 1 blogid)
                (.setObject stmt 2 title)
                (.setObject stmt 3 content)
                (.setObject stmt 4 (markdownify content))
                (if with-date
                    (.setTimestamp stmt 5
                                   (java.sql.Timestamp. (.getMillis created-date))
                                   (Calendar/getInstance
                                       (.toTimeZone (.getZone created-date)))))
                (.execute stmt)
                (first (sql-int/resultset-seq* (.getGeneratedKeys stmt)))))))
                    
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

(defn -image-name
    "create an image name based on id from image table, filename, resolution type, and
    content-type"
    [imageid filename res content-type]
    (let [lastDot (.lastIndexOf filename ".")
          shortfilename (if (> lastDot 0) (.substring filename 0 lastDot) filename)
          oldext (if (> lastDot 0) (.substring filename (+ 1 lastDot)) "")
          newext (first (first (filter #(= content-type (val %)) default-mime-types)))
          newext (if (or (nil? newext) (str/blank? newext)) oldext newext)]
        (str imageid "-" shortfilename "-" res "." newext)))

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
            {:image-bytes (.toByteArray baos) :content-type (first mimetype)
             :owner userid}
            (finally (.close baos)))))

(defn scale-image-to-bytes
    [path full-img-content-type width userid]
    (let [is (FileInputStream. path)]
        (try
            (do-scale is full-img-content-type width userid)
            (finally (.close is)))))

(defn -do-image-upload
    "upload the image to s3, and return the id of the new s3reference row"
    [imgmap filename imageid res]
    (let [credentials (AWSCredentials. *aws-access-key* *aws-secret-key*)
          image-md5 (ServiceUtils/computeMD5Hash (:image-bytes imgmap))
          s3Service (RestS3Service. credentials)
          s3Bucket (.getBucket s3Service *image-bucket*)
          remote-filename (-image-name imageid filename res (:content-type imgmap))
          s3Object (S3Object. remote-filename (:image-bytes imgmap))]
        (.setContentType s3Object (:content-type imgmap))
        (.setMd5Hash s3Object image-md5)
        (.addMetadata s3Object "owner" (str (:owner imgmap)))
        (.putObject s3Service s3Bucket s3Object)
        (:id (sql/insert-record :s3reference {:bucket *image-bucket*
                                              :filename remote-filename
                                              :owner (:owner imgmap) :md5hash image-md5
                                              :contenttype (:content-type imgmap)}))))

(defn -make-image-in-tx
    "helped for make-image; must be run in a tx within a db connection.  Returns the id
    of the new image."
    [filename title description content-type path blogid userid]
    (let [imagerow (sql/insert-record :image {:filename filename :title title
                                              :description description :owner userid
                                              :blog blogid})
          imageid (:id imagerow)
          full-map {:image-bytes (to-byte-array path) :content-type content-type
                    :owner userid}
          blog-map (scale-image-to-bytes path content-type 550 userid)
          thumb-map (scale-image-to-bytes path content-type 150 userid)
          full-s3id (-do-image-upload full-map filename imageid *image-full*)
          blog-s3id (-do-image-upload blog-map filename imageid *image-blog*)
          thumb-s3id (-do-image-upload thumb-map filename imageid *image-thumb*)]
        (sql/update-values :image ["id=? and owner=?" imageid userid]
                           {:fullimage full-s3id :blogwidthimage blog-s3id
                            :thumbnail thumb-s3id})
        imageid))

(defn make-image
    "make an image, returns the id"
    [filename title description content-type path blogid userid]
    (sql/with-connection *db* (sql/transaction
                                  (-make-image-in-tx filename title description
                                                     content-type path blogid userid))))

(defn -get-image-bytes-from-s3 [filename]
    (let [credentials (AWSCredentials. *aws-access-key* *aws-secret-key*)
          s3Service (RestS3Service. credentials)
          s3Bucket (.getBucket s3Service *image-bucket*)
          s3Object (.getObject s3Service s3Bucket filename)]
        (.getDataInputStream s3Object)))

(defn -get-image-results
    "get the image result object, including the imageblob specified by id.
    Must have an open db connection."
    [image s3id include-bytes?]
    (sql/with-query-results rs ["select * from s3reference where id=?" s3id]
        (let [s3ref (first rs)]
            (if (nil? s3ref)
                nil
                {:filename (:filename image)
                 :title (:title image)
                 :description (:description image)
                 :remote-filename (:filename s3ref)
                 :content-type (:contenttype s3ref)
                 :image-bytes (if include-bytes?
                                  (-get-image-bytes-from-s3 (:filename s3ref)))}))))


(defn -get-image
    "returns a map with :filename, :title, :description (from image table),
    :content-type, and optionally :image (as a stream)"
    [imageid res include-bytes?]
    (sql/with-connection *db*
        (sql/with-query-results rs ["select * from image where id=?" imageid]
            (let [image (first rs)]
                (cond
                    (nil? image) nil
                    (= *image-full* res) (-get-image-results image
                                             (:fullimage image) include-bytes?)
                    (= *image-blog* res) (-get-image-results image
                                             (:blogwidthimage image) include-bytes?)
                    (= *image-thumb* res) (-get-image-results image
                                              (:thumbnail image) include-bytes?)
                    :else nil)))))

(defn get-image
    "returns a map with :filename, :title, :description (from image table)
    and :image (as a stream), :content-type from imageblob"
    [imageid res]
    (-get-image imageid res true))

(defn get-image-header
    "get only the header information (filename, title, dscription, content-type) for an
    image"
    [imageid res]
    (-get-image imageid res false))

(defn get-images
    "returns some basic info about images with the specified offset for the
    current user.  If a blogid is specified, only return results with that blogid or null."
    ([userid number offset]
     (get-images userid nil number offset))
    ([userid blogid number offset]
     (sql/with-connection
         *db*
         (sql/with-query-results
             rs
             (if (nil? blogid)
                 ["select id, filename, title, description from image where owner=? order by created_date desc limit ? offset ?"
                  userid number offset]
                 ["select id, filename, title, description from image where owner=? and (blog=? or blog is null) order by created_date desc limit ? offset ?"
                  userid blogid number offset])
             (doall rs)))))

(defn make-domain
    "create a domain, return the id"
    ([domainname userid]
        (make-domain domainname userid nil))
    ([domainname userid blogid]
        (sql/with-connection *db*
            (:id (sql/insert-record :domain
                         {:domain domainname :owner userid :blogid blogid})))))

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

(defn send-email
    "send an email using the smtp server from config.clj"
    [from-address to-address subject body]
    (let [result (send-message #^{:host *smtp-host*
                                  :ssl :true
                                  :user *smtp-user*
                                  :pass *smtp-password*}
                               {:from from-address
                                :to to-address
                                :subject subject
                                :body body})]
        (if (not (= :SUCCESS (:error result)))
            (throw (Exception. (str "failed sending email: " result)))
            true)))
