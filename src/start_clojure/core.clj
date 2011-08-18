(ns start-clojure.core
	(:use		[compojure.core]
				[ring.middleware.json-params]
				[ring.middleware.params]
				[ring.middleware.reload]
				[ring.middleware.stacktrace])
	(:require	[compojure.route :as route]
				[compojure.handler :as handler]
				[start-clojure.data :as data]
				[start-clojure.templates :as templates]
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


(defroutes main-routes
	(GET "/" [] (index-page))
	

	(GET "/blog/:bid/post/" [bid]
		(render-html-posts (data/get-posts (Integer/parseInt bid) 10 0)))
	(GET "/blog/:bid/post/new" [bid]
		(render-html-newpost))
	(POST "/blog/:bid/post/new" [bid title content]
		(data/make-post (Integer/parseInt bid) title content)
		(str "XXX should redirect or something title " title " content " content))


	(POST "/api/blog/" [title]
		(json-response (render-blog-json (data/make-blog title))))

	(GET "/api/blog/:bid/post/" [bid]
		(let [bid (Integer/parseInt bid)]
			(json-response (doall (for [post (data/get-posts bid 10 0)]
					(render-post-json post))))))
	(POST "/api/blog/:bid/post/" [bid title content]
		(json-response (render-post-json
			(data/make-post (Integer/parseInt bid) title content))))
	; (GET "/api/post/:id" [id]
	; 	(json-response (data/get-post id)))
	; (PUT "/api/post/:id" [id]
	; 	(str "tbd id " id))

	(route/resources "/")
	(route/not-found "Page not found"))

(def app
	(-> main-routes
		(wrap-reload '(start-clojure.templates)) ; XXX not for production
		(wrap-stacktrace)
		(wrap-params)
		(wrap-json-params)))
