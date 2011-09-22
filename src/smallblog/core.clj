(ns smallblog.core
	(:use		[compojure.core]
				[ring.util.response :only (redirect redirect-after-post)]
				[ring.util.codec :only (url-encode)]
				[ring.middleware.json-params]
				[ring.middleware.params]
				[ring.middleware.multipart-params]
				[ring.middleware.stacktrace]
				[sandbar stateful-session auth validation])
	(:require	[compojure.route :as route]
				[compojure.handler :as handler]
				[ring.adapter.jetty :as jetty]
				[smallblog.util :as util]
				[smallblog.data :as data]
				[smallblog.templates :as templates]
				[clj-time.core :as clj-time]
				[clj-time.format :as clj-time-format]
				[clj-time.coerce :as clj-time-coerce]
				[clj-json.core :as json]))
	

(defn index-page []
	(str "hi hi hi <a href=\"http://localhost:3000/blog/67/post/\">link</a>"))

(defn json-response [data & [status]]
	{:status (or status 200)
		:headers {"Content-Type" "application/json"}
		:body (json/generate-string data)})

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

(defn render-html-posts [posts url blogname blogid]
	(templates/main {:blogname blogname, :posts posts
				:user (data/get-current-user) :url url
				:is-blog-owner (data/blog-owner? blogid)}))

(defn render-html-newpost [blogname]
	(templates/newpost {:blogname blogname
				:user (data/get-current-user)}))

(defn render-html-account [url]
	(let [userid (:id (data/get-current-user))]
		(templates/account {:blogs (data/get-blogs userid) :url url
					:user (data/get-current-user)})))

(defn render-html-signup []
	(templates/signup {}))

(defn render-html-images [images]
	(templates/images {:images images
				:user (data/get-current-user)}))



(defn permission-denied []
	(str "permission denied page"))

(def security-config
	[#"/api/blog/" #{:admin :user}
	 #"/blog/.*/post/new" #{:admin :user}
	 #"/account" #{:admin :user}
	 #"/api/blog/.*/post/" #{:admin :user}
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
	(= :https (:scheme request)))



(defroutes main-routes
	(GET "/" [] (index-page))

	; "account urls"
	(GET templates/*permission-denied-uri* [] (permission-denied))
	(GET templates/*logout-url* [] (logout! {}))
	(GET templates/*login-url* [url :as request]
		(if (not (ensure-secure request))
			{:status 403}
			(templates/login {:url url})))
	(POST templates/*login-redirect-url* [url :as request]
		(if (not (ensure-secure request))
			{:status 403}
			(if (nil? url)
				(redirect-after-post "/")
				(redirect-after-post url))))
	(GET templates/*account-url* [:as request]
		(if (not (ensure-secure request))
			{:status 403}
			(render-html-account (util/uri-from-request request))))
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
					:else (do
	  					(println "XXX should be a log not a print" request)
						{:status 401 :body "bad form parameters"})))))
	(GET templates/*signup-url* [:as request]
		(if (not (ensure-secure request))
			{:status 403}
			(render-html-signup)))
	(POST templates/*signup-url* [email newpw confirmpw :as request]
		(if (not (ensure-secure request))
			{:status 403}
			(do
				(data/make-login email newpw confirmpw)
				(redirect-after-post templates/*account-fqurl*))))
	

	; "post urls"
	(GET "/blog/:bid/post/" [bid :as request]
		(render-html-posts
				(data/get-posts (Integer/parseInt bid) 10 0)
				(util/uri-from-request request)
				(:title (data/get-blog (Integer/parseInt bid)))
				bid))
	(GET "/blog/:bid/post/new" [bid]
		(if (not (data/blog-owner? bid))
			(redirect templates/*permission-denied-uri*)
			(render-html-newpost (:title (data/get-blog (Integer/parseInt bid))))))
	(POST "/blog/:bid/post/new" [bid title content :as request]
		(if (not (data/blog-owner? bid))
			(redirect templates/*permission-denied-uri*)
			(let [this-url (:uri request)
					to-url (subs this-url 0 (- (count this-url) 3))]
				(data/make-post (Integer/parseInt bid) title content)
				(redirect-after-post to-url))))


	; "image urls"
	(GET templates/*image-url* []
		(render-html-images (data/get-images
						(:id (data/get-current-user)) 10 0)))
	(wrap-multipart-params
		(POST templates/*image-url* {params :params}
			(let [image (get params "image")]
				(data/make-image (:filename image) (get params "title")
						(get params "description") (:content-type image)
						(:tempfile image) (:id (data/get-current-user)))
				(redirect-after-post templates/*image-url*))))
	(GET (str templates/*image-url* "/:imgid/:res") [imgid res]
		(let [image (data/get-image (Integer/parseInt imgid) res)]
			(if (nil? image)
				nil
				{:body (:image image)
					; XXX need to add cache control to this
					:headers {"Content-Type" (:content-type image)
						"filename" (:filename image)}})))


	; "api urls"
	(POST "/api/blog/" [title]
		(let [userid (:id (data/get-current-user))]
			(json-response (render-blog-json
					(data/make-blog userid title)))))
	(GET "/api/blog/:bid/post/" [bid]
		(let [bid (Integer/parseInt bid)]
			(json-response (doall (for [post (data/get-posts bid 10 0)]
					(render-post-json post))))))
	(POST "/api/blog/:bid/post/" [bid title content]
		(if (not (allow-access? #{(keyword (str data/owner-blog-prefix bid))}
				(:roles (data/get-current-user))))
			(redirect templates/*permission-denied-uri*)
			(json-response (render-post-json
				(data/make-post (Integer/parseInt bid) title content)))))
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

(defn -main []
	(let [port (System/getenv "PORT")
			port (if (nil? port) "3000" port)
			port (Integer/parseInt port)
			ssl-port templates/*https-port*]
		(jetty/run-jetty (app port ssl-port)
				{:port port :ssl-port ssl-port
						:keystore "devonly.keystore" :key-password "foobar"})))
