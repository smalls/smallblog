(ns start-clojure.test.core
	(:use		[start-clojure.core]
				[clojure.test]
				[clj-time.core :only (now)])
	(:require	[clojure.contrib.string]))

 (defn request [method resource web-app & params]
 	(web-app {:request-method method :uri resource :params (first params)}))

(deftest routes
	(let [new-content (str "asdf new content" (now))]
		(is (= 200 (:status (request :get "/post/" main-routes))))
		(let [body (:body (request :get "/post/" main-routes))]
			; (is (clojure.contrib.string/substring?
			; 		(str ":\"" new-content "\"") body ))
			(is (clojure.contrib.string/substring? "\"content\":" body ))
			(is (clojure.contrib.string/substring? "\"title\":" body )))))
