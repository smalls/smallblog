(ns smallblog.core
	(:use		[compojure.core]
				[ring.util.response :only (redirect redirect-after-post)]
				[ring.util.codec :only (url-encode)]
				[ring.middleware.json-params]
				[ring.middleware.params]
				[ring.middleware.reload]
				[ring.middleware.stacktrace]
				[sandbar stateful-session auth validation])
	(:require	[compojure.route :as route]
				[compojure.handler :as handler]
				[smallblog.data :as data]
				[smallblog.templates :as templates]
				[clj-time.core :as clj-time]
				[clj-time.format :as clj-time-format]
				[clj-time.coerce :as clj-time-coerce]
				[clj-json.core :as json]))

(defn index-page []
	(str "hi hi hi"))

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

(defn render-html-posts [posts]
	(templates/main {:blogname "first blog name", :posts posts}))

(defn render-html-newpost []
	(templates/newpost {:blogname "first blog name"}))



(defn permission-denied []
	(str "permission denied page"))

(def security-config
	[#"/api/blog/" #{:admin :user}
	 #"/blog/.*/post/new" #{:admin :user}
	 #"/api/blog/.*/post/" #{:admin :user}
	 #"/login-redirect" #{:admin :user}
	 #".*" :any])

; XXX call login-for-session
(defn authorize [request]
	(let [form-params (:form-params request)
			username (get form-params "email")
			password (get form-params "password")]
		(if (and (not (nil? username)) (not (nil? password)))
			(data/establish-session username password)
			(let [redirect-url (str templates/*login-url* "?url="
							(url-encode (:uri request)))] 
				(redirect redirect-url)))))


(def permission-denied-uri "/permission-denied")

(defroutes main-routes
	(GET "/" [] (index-page))

	(GET permission-denied-uri [] (permission-denied))
	(GET "/logout" [] (logout! {}))
	(GET templates/*login-url* [url] (templates/login {:url url}))
	(POST templates/*login-redirect-url* [url] (do
			(if (nil? url)
				(redirect-after-post "/")
				(redirect-after-post url))))
	

	(GET "/blog/:bid/post/" [bid]
		(render-html-posts (data/get-posts (Integer/parseInt bid) 10 0)))
	(GET "/blog/:bid/post/new" [bid]
		(if (not (allow-access? #{(keyword (str data/owner-blog-prefix bid))}
				(:roles (data/get-current-user))))
			(redirect permission-denied-uri)
			(render-html-newpost)))
	(POST "/blog/:bid/post/new" [bid title content]
		(if (not (allow-access? #{(keyword (str data/owner-blog-prefix bid))}
				(:roles (data/get-current-user))))
			(redirect permission-denied-uri)
			(do
				(data/make-post (Integer/parseInt bid) title content)
				(str "XXX should redirect or something title " title
						" content " content))))


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
			(redirect permission-denied-uri)
			(json-response (render-post-json
				(data/make-post (Integer/parseInt bid) title content)))))
	; (GET "/api/post/:id" [id]
	; 	(json-response (data/get-post id)))
	; (PUT "/api/post/:id" [id]
	; 	(str "tbd id " id))

	(route/resources "/")
	(route/not-found "Page not found"))

(def app
	(-> main-routes
		(with-security security-config authorize)
		(wrap-stateful-session)
		(wrap-reload '(smallblog.templates)) ; XXX not for production
		(wrap-stacktrace)
		(wrap-params)
		(wrap-json-params)))
