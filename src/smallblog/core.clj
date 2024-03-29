(ns smallblog.core
    (:use [smallblog.config]
          [compojure.core]
          [ring.util.response :only (redirect redirect-after-post)]
          [ring.util.codec :only (url-encode)]
          [ring.middleware.json-params]
          [ring.middleware.params]
          [ring.middleware.multipart-params]
          [ring.middleware.stacktrace]
          [sandbar stateful-session auth validation]
          [clojure.contrib.string :only (split)])
    (:require [compojure.route :as route]
              [compojure.handler :as handler]
              [ring.adapter.jetty :as jetty]
              [smallblog.util :as util]
              [smallblog.data :as data]
              [smallblog.templates :as templates]
              [clj-time.core :as clj-time]
              [clj-time.format :as clj-time-format]
              [clj-time.coerce :as clj-time-coerce]
              [clj-json.core :as json]))

(defn verify-version
    "throws an exception if the version is incorrect or doesn't match"
    ([] (verify-version 20111130225420))
    ([expected-version]
     (let [version (data/get-db-version)]
         (if (= expected-version version)
             version
             (throw (Exception.
                        (str "unexpected version '" version
                             "', expected '" expected-version)))))))

(defn json-response
    "called on all JSON responses to set content-type, status, render as json -
    should be ring middleware?"
    [data & [status]]
    {:status (or status 200)
     :headers {"Content-Type" "application/json;charset=utf-8"}
     :body (json/generate-string data)})

(defn html-response
    "called on all HTML responses to set content-type, status, etc -
    should be ring middleware?"
    [data & [status]]
    {:status (or status 200)
     :headers {"Content-Type" "text/html;charset=utf-8"}
     :body data})

(defn render-post-json [post]
    {:id (:id post), :title (:title post), :content (:content post),
     :created_date (clj-time-format/unparse
                       (clj-time-format/formatters :basic-date-time)
                       (clj-time-coerce/from-date (:created_date post)))})

(defn render-blog-json [blog]
    {:id (:id blog), :name (:name blog),
     :created_date (clj-time-format/unparse
                       (clj-time-format/formatters :basic-date-time)
                       (clj-time-coerce/from-date (:created_date blog)))})

(defn render-html-posts
    [posts url blogname blogid page pagination]
    (templates/main {:blogname blogname, :posts posts
                     :user (data/get-current-user) :url url
                     :is-blog-owner (data/blog-owner? blogid)
                     :pagination pagination :page page
                     :total-posts (data/count-posts blogid)}))

(defn render-html-newpost [blogname blogid]
    (templates/newpost {:blogname blogname :blogid (str blogid)
                        :user (data/get-current-user)}))

(defn render-html-account [url]
    (let [userid (:id (data/get-current-user))]
        (templates/account {:blogs (data/get-blogs userid) :url url
                            :user (data/get-current-user)
                            :domains (data/get-user-domains userid)})))

(defn render-html-signup []
    (templates/signup {}))

(defn render-html-images [images]
    (let [userid (:id (data/get-current-user))]
        (templates/images {:images images
                           :blogs (data/get-blogs userid)
                           :user (data/get-current-user)})))

(defn render-json-images [images]
    (map #(identity {:id (:id %), :title (:title %), :description (:description %),
                     :filename (:filename %)}) images))



; -----------------------------------------
;  auth & security
;

(defn permission-denied []
    (str "permission denied page"))

(def security-config
    [#"/api/blog/" #{:admin :user}
     #"/blog/.*/post/new" #{:admin :user}
     #"/account" #{:admin :user}
     #"/api/blog/.*/post/" #{:admin :user}
     #"/api/images/" #{:admin :user}
     #"/login-redirect.*" #{:admin :user}
     #"/images" #{:admin :user}
     #".*" :any])

(defn authorize [request]
    (let [form-params (:form-params request)
          username (get form-params "email")
          password (get form-params "password")]
        (if (and (not (nil? username)) (not (nil? password)))
            (data/establish-session username password)
            (let [redirect-url (str templates/*login-fqurl* "?url="
                                   (url-encode (:uri request)))] 
                (redirect redirect-url)))))

(defn ensure-secure [request]
    (or (= :https (:scheme request))
        (let [headers (:headers request)
              forwarded-header "x-forwarded-proto"]
            (and (contains? headers forwarded-header)
                 (= "https" (get headers forwarded-header))))))


; -----------------------------------------
;  post pages - rendering w/ pagination
;
(defn -get-page-from-req [request]
    (let [req-params (:params request)]
        (if (contains? req-params "page")
            (Integer/parseInt (get req-params "page"))
            0)))

(defn -get-pagination-from-req [request]
    (let [req-params (:params request)]
        (if (contains? req-params "pagination")
            (Integer/parseInt (get req-params "pagination"))
            10)))

(defn -render-html-posts-helper
    [blogid request posts page pagination]
    (render-html-posts
        posts
        (util/uri-from-request request)
        (:title (data/get-blog blogid))
        blogid
        page
        pagination))

(defn render-html-posts-paginator
    [blogid request]
    "paginate a request for posts based on request parameters"
    (let [page (-get-page-from-req request)
          pagination (-get-pagination-from-req request)]
        (-render-html-posts-helper blogid request
                                   (data/get-posts blogid pagination (* page pagination))
                                   page pagination)))

(defn -get-newpost-date
    "return a date if postdate is not nil or empty, or nil if it is"
    [postdate]
    (if (and (not (nil? postdate)) (not (empty? postdate)))
        (clj-time-format/parse
            (clj-time-format/formatters :date) postdate)
        nil))

(defn server-name-from-request [request]
    (str (:server-name request)
         (if (not (nil? (:server-port request)))
             (str ":" (:server-port request)))))
    
(defroutes main-routes
           (GET "/" [:as request]
               (let [server-name (server-name-from-request request)
                     domain (data/get-domain server-name)]
                   (html-response
                       (if (not (nil? domain))
                           (render-html-posts-paginator (:blogid domain) request)
                           (templates/about {:user (data/get-current-user)})))))
           (GET "/:year/:month/:title" [year month title :as request]
                (let [server-name (server-name-from-request request)
                      domain (data/get-domain server-name)]
                    (if (not (nil? domain))
                        (html-response (render-html-posts-paginator
                                           (:blogid domain) request year month title))
                        (route/not-found "page not found"))))

           ; "account urls"
           (GET templates/*permission-denied-uri* [] (html-response (permission-denied)))
           (GET templates/*logout-url* [] (logout! {}))
           (GET templates/*login-url* [url :as request]
               (if (not (ensure-secure request))
                   {:status 403}
                   (html-response (templates/login {:url url}))))
           (POST templates/*login-redirect-url* [url :as request]
               (if (not (ensure-secure request))
                   {:status 403}
                   (if (nil? url)
                       (redirect-after-post "/")
                       (redirect-after-post url))))
           (GET templates/*account-url* [:as request]
               (if (not (ensure-secure request))
                   {:status 403}
                   (html-response (render-html-account (util/uri-from-request request)))))
           (POST templates/*account-url* [:as request]
               (if (not (ensure-secure request))
                   {:status 403}
                   (let [params (:form-params request)]
                       (cond
                           (and (contains? params "oldpw") (contains? params "newpw")
                               (contains? params "confirmpw"))
                           (let [oldpw (get params "oldpw")
                                 newpw (get params "newpw")
                                 confirmpw (get params "confirmpw")
                                 email (:name (data/get-current-user))]
                               (data/change-password email oldpw newpw confirmpw)
                               (redirect-after-post templates/*account-fqurl*))

                           (contains? params "blogtitle")
                           (let [blogtitle (get params "blogtitle")
                                 userid (:id (data/get-current-user))]
                               (data/make-blog userid blogtitle)
                               (redirect-after-post templates/*account-fqurl*))

                           (contains? params "domainname")
                           (let [domainname (get params "domainname")
                                 userid (:id (data/get-current-user))]
                               (data/make-domain domainname userid)
                               (redirect-after-post templates/*account-fqurl*))

                           (contains? params "change-domains")
                           (let [p-keys (keys params)]
                               (doseq [p p-keys]
                                   (if (.startsWith p "change-domain-")
                                       (let [domainid (Integer/parseInt
                                                          (nth (split #"-" p) 2))
                                             userid (:id (data/get-current-user))
                                             bp (get params p)
                                             blogid (if (= 0 (count bp)) nil
                                                        (Integer/parseInt bp))]
                                           (data/change-domain userid
                                               domainid blogid))))
                               (redirect-after-post templates/*account-fqurl*))

                           :else (do
                                     (println "XXX should be a log not a print" request)
                                     {:status 400 :body "bad form parameters"})))))
           (GET templates/*signup-url* [token :as request]
                (html-response
                    (if (and
                            (ensure-secure request)
                            (or
                                (nil? *signup-token*)
                                (= token *signup-token*)))
                        (render-html-signup)
                        (templates/signup-restricted))))
           (POST templates/*signup-url* [email newpw confirmpw :as request]
               (if (not (ensure-secure request))
                   {:status 403}
                   (do
                       (data/make-login email newpw confirmpw)
                       (redirect-after-post templates/*account-fqurl*))))


           ; "post urls"
           (GET "/blog/:blogid/post/" [blogid :as request]
               (html-response (render-html-posts-paginator
                                  (Integer/parseInt blogid) request)))
           (GET "/blog/:blogid/post/:year/:month/:title" [blogid year month title :as request]
                (let [blogid (Integer/parseInt blogid)
                      post (data/get-post blogid
                                          title
                                          (Integer/parseInt year)
                                          (Integer/parseInt month))]
                    (if (not (nil? post))
                        (html-response (-render-html-posts-helper blogid request [post] 0 1))
                        (route/not-found "page not found"))))
           (GET "/blog/:bid/post/new" [bid]
               (if (not (data/blog-owner? bid))
                   (redirect templates/*permission-denied-uri*)
                   (let [int-blogid (Integer/parseInt bid)]
                       (html-response
                           (render-html-newpost
                               (:title (data/get-blog int-blogid))
                               int-blogid)))))
           (POST "/blog/:bid/post/new" [bid title content postdate :as request]
               (if (not (data/blog-owner? bid))
                   (redirect templates/*permission-denied-uri*)
                   (let [this-url (:uri request)
                         to-url (subs this-url 0 (- (count this-url) 3))
                         postdate (-get-newpost-date postdate)]
                       (data/make-post (Integer/parseInt bid) title content postdate)
                       (redirect-after-post to-url))))


           ; "image urls"
           (GET templates/*image-url* []
               (html-response (render-html-images (data/get-images
                                                      (:id (data/get-current-user)) 10 0))))
           (wrap-multipart-params
               (POST templates/*image-url* {params :params}
                   (let [image (get params "image")
                         blogid (if (not (empty? (get params "blogid")))
                                    (Integer/parseInt (get params "blogid"))
                                    nil)]
                       (data/make-image (:filename image)
                                        (get params "title")
                                        (get params "description")
                                        (:content-type image)
                                        (:tempfile image)
                                        blogid
                                        (:id (data/get-current-user)))
                       (redirect-after-post templates/*image-url*))))
           (GET (str templates/*image-url* "/:imgid/:res") [imgid res :as request]
               (let [image (data/get-image-header (Integer/parseInt imgid) res)]
                   (if (nil? image)
                       nil
                       (redirect (str (name (:scheme request)) "://"
                                      *image-bucket* ".s3.amazonaws.com/"
                                      (:remote-filename image))))))


           ; contact/about
           (GET "/contact" []
                (html-response (templates/contact {:user (data/get-current-user)})))
           (POST "/contact" [:as request]
                (let [params (:params request)
                      email (get params "email")
                      confirm-email (get params "confirmemail")
                      subject (get params "subject")
                      body (get params "body")
                      admin-email *admin-email*]
                    (if (not (= email confirm-email))
                        (throw (Exception. (str "email addresses must match: " email " and "
                                                confirm-email))))
                    (if (or (empty? email) (empty? subject) (empty? body))
                        (throw (Exception. (str "all fields must be filled in"))))
                    (data/send-email email admin-email subject body)
                    (redirect-after-post "/")))
           (GET "/about" []
                (html-response (templates/about {:user (data/get-current-user)})))


           ; "api urls"
           (POST "/api/blog/" [title]
               (let [userid (:id (data/get-current-user))]
                   (json-response (render-blog-json
                                      (data/make-blog userid title)))))
           (GET "/api/blog/:bid/post/" [bid :as request]
               (let [bid (Integer/parseInt bid)
                     page (-get-page-from-req request)
                     pagination (-get-pagination-from-req request)]
                   (json-response (doall (for [post (data/get-posts bid pagination page)]
                                             (render-post-json post))))))
           (POST "/api/blog/:bid/post/" [bid title content]
               (if (not (allow-access? #{(keyword (str data/owner-blog-prefix bid))}
                            (:roles (data/get-current-user))))
                   (redirect templates/*permission-denied-uri*)
                   (json-response (render-post-json
                                      (data/make-post (Integer/parseInt bid) title content nil)))))
           (GET "/api/images/" [:as request]
                (let [req-params (:params request)
                      blog (if (contains? req-params "blog")
                               (Integer/parseInt (get req-params "blog"))
                               nil)
                      page (-get-page-from-req request)
                      pagination (-get-pagination-from-req request)
                      userid (:id (data/get-current-user))]
                   (json-response (render-json-images
                                      (data/get-images userid blog pagination page)))))
           ; (GET "/api/post/:id" [id]
           ; 	(json-response (data/get-post id)))
           ; (PUT "/api/post/:id" [id]
           ; 	(str "tbd id " id))

           (route/resources "/")
           (route/not-found "Page not found"))

(defn app [port ssl-port]
    (-> main-routes
        (with-security security-config authorize)
        wrap-stateful-session

        (wrap-stacktrace)
        (wrap-params)
        (wrap-json-params)))

(defn start-server [join]
    (verify-version)
    (jetty/run-jetty (app *port* *ssl-port*)
                     {:join? join :port *port* :ssl-port *ssl-port*
                      :keystore "devonly.keystore" :key-password "foobar"}))

(defn -main []
    (start-server true))
