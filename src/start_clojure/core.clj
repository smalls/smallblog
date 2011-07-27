(ns start-clojure.core
	(:use		[compojure.core]
				[clojure.data.json :only (json-str write-json read-json)])
	(:require	[compojure.route :as route]
				[compojure.handler :as handler]
				[start-clojure.data :as data]))

(defn index-page []
	(str "hi hi hi"))


(defroutes main-routes
	(GET "/" [] (index-page))

	(POST "/post" []
		(str "tbd"))
	(GET "/post/" []
		(str "<h1>post </h1>"))
	(GET "/post/:id" [id]
		(str "<h1>post " id " </h1>"))
	(PUT "/post/:id" [id]
		(str "tbd id " id))

	(route/resources "/")
	(route/not-found "Page not found"))

(def app
	(handler/site main-routes))
