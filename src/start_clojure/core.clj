(ns start-clojure.core
	(:use compojure.core)
	(:require	[compojure.route :as route]
				[compojure.handler :as handler]))

(defn index-page []
	(str "hi hi hi"))


(defroutes main-routes
	(GET "/" [] (index-page))

	(POST "/post" []
		(str "tbd"))
	(GET "/post/:id" [id]
		(str "<h1>post " id " </h1>"))
	(PUT "/post/:id" [id]
		(str "tbd id " id))

	(route/resources "/")
	(route/not-found "Page not found"))

(def app
	(handler/site main-routes))

