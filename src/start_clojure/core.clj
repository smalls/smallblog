(ns start-clojure.core
	(:use		[compojure.core]
				[ring.middleware.json-params]
				[clojure.contrib.duck-streams :only (slurp*)])
	(:require	[compojure.route :as route]
				[compojure.handler :as handler]
				[start-clojure.data :as data]
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

(defn post-representation [post]
	{:id (:id post), :title (:title post), :content (:content post),
			:created_date (clj-time-format/unparse
					(clj-time-format/formatters :basic-date-time)
					(clj-time-coerce/from-date (:created_date post)))})


(defroutes main-routes
	(GET "/" [] (index-page))

	(GET "/api/post/" []
		(json-response (doall (for [post (data/get-posts 10 0)]
			(post-representation post)))))
	(POST "/api/post/" [title content]
		(json-response (post-representation (data/make-post title content))))
	; (GET "/api/post/:id" [id]
	; 	(json-response (data/get-post id)))
	; (PUT "/api/post/:id" [id]
	; 	(str "tbd id " id))

	(route/resources "/")
	(route/not-found "Page not found"))

(def app
	(-> main-routes
		wrap-json-params))
