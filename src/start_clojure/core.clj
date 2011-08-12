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

(defn render-html-posts [posts]
	(templates/main {:blogname "first blog name", :posts posts}))

(defn render-html-newpost []
	(templates/newpost {:blogname "first blog name"}))


(defroutes main-routes
	(GET "/" [] (index-page))
	

	(GET "/post/" []
		(render-html-posts (data/get-posts 10 0)))
	(GET "/post/new" []
		(render-html-newpost))
	(POST "/post/new" [title content]
		(println "title" title "content" content)
		(data/make-post title content)
		(str "XXX should redirect or something title " title " content " content))


	(GET "/api/post/" []
		(json-response (doall (for [post (data/get-posts 10 0)]
				(render-post-json post)))))
	(POST "/api/post/" [title content]
		(json-response (render-post-json (data/make-post title content))))
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
