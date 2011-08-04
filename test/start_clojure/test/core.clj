(ns start-clojure.test.core
	(:use		[start-clojure.core]
				[clojure.test]
				[clj-time.core :only (now date-time)])
	(:require	[clojure.contrib.string]))

(defn request-get [resource web-app & params]
 	(web-app {:request-method :get :uri resource :params (first params)}))

(defn request-post [resource web-app & params]
 	(web-app {:request-method :post :uri resource :params (first params)}))

(deftest routes
	(let [new-content (str "asdf new content" (now))]
		(is (= 200 (:status (request-get "/post/" main-routes))))
		(is (= 200 (:status (request-post "/post/" main-routes
			{:title "mytitle" :content new-content}))))
		(let [body (:body (request-get "/post/" main-routes))]
			(is (clojure.contrib.string/substring?
					(str ":\"" new-content "\"") body ))
			(is (clojure.contrib.string/substring? "\"content\":" body ))
			(is (clojure.contrib.string/substring? "\"title\":" body )))))

(deftest test-post-representation []
	(let [post {:title "title", :text "text",
			:created_date (java.sql.Timestamp. (.getMillis (
					date-time 2011 8 2 3 4 5 6)))}]
		(is (= "20110802T030405.006Z" (:created_date
				(post-representation post))))))

