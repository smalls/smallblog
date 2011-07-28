(ns start-clojure.core
	(:use		[compojure.core])
	(:require	[compojure.route :as route]
				[compojure.handler :as handler]
				[start-clojure.data :as data]
				[clojure.contrib.json :as json]))

(defn index-page []
	(str "hi hi hi"))


(defroutes main-routes
	(GET "/" [] (index-page))

	(POST "/post" []
		(str "tbd"))
	(GET "/post/" []
		(json/json-str (data/get-posts 10 0)))
	(GET "/post/:id" [id]
		(str "<h1>post " id " </h1>"))
	(PUT "/post/:id" [id]
		(str "tbd id " id))

	(route/resources "/")
	(route/not-found "Page not found"))

(def app
	(handler/site main-routes))
