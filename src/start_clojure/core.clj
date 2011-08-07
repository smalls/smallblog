(ns start-clojure.core
	(:use		[compojure.core]
				[ring.middleware.json-params])
	(:require	[compojure.route :as route]
				[compojure.handler :as handler]
				[start-clojure.data :as data]
				[clj-time.core :as clj-time]
				[clj-time.format :as clj-time-format]
				[clj-time.coerce :as clj-time-coerce]
				[net.cgrand.enlive-html :as html]
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

(html/deftemplate main "start_clojure/templates/main.html"
	[ctx]
	[:p#blogname] (html/content (:blogname ctx))
	[:head :title] (html/content (:blogname ctx)))

(defn render-posts-html [posts]
	(apply str (main {:blogname "first blog name"})))


(defroutes main-routes
	(GET "/" [] (index-page))
	
	(GET "/post/" []
		(render-posts-html (data/get-posts 10 0)))


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
		wrap-json-params))
