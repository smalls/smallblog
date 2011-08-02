(ns start-clojure.core
	(:use		[compojure.core]
				[ring.middleware.json-params]
				[clojure.contrib.duck-streams :only (slurp*)])
	(:require	[compojure.route :as route]
				[compojure.handler :as handler]
				[start-clojure.data :as data]
				[clj-json.core :as json]))

(defn index-page []
	(str "hi hi hi"))

(defn json-response [data & [status]]
	{:status (or status 200)
		:headers {"Content-Type" "application/json"}
		:body (json/generate-string data)})


(defroutes main-routes
	(GET "/" [] (index-page))

	(GET "/post/" []
		(json-response (data/get-posts 10 0)))
	(POST "/post/" [title content]
		(data/make-post title content)
		(println (str "done title " title " content " content))
		; tdb XXX return id
		(str "insert successful"))
	; (GET "/post/:id" [id]
	; 	(json-response (data/get-post id)))
	; (PUT "/post/:id" [id]
	; 	(str "tbd id " id))

	(route/resources "/")
	(route/not-found "Page not found"))

(def app
	(-> main-routes
		wrap-json-params))
