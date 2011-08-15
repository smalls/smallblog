(ns start-clojure.test.core
	(:use		[start-clojure.core]
				[clojure.test]
				[clojure.string :only (join)]
				[clj-time.core :only (now date-time)])
	(:require	[clojure.contrib.string]))

(defn request-get [resource web-app & params]
 	(web-app {:request-method :get :uri resource :params (first params)}))

(defn request-post [resource web-app & params]
 	(web-app {:request-method :post :uri resource :params (first params)}))

(deftest test-api-routes
	(let [new-content (str "asdf new content" (now))]
		(is (= 200 (:status (request-get "/api/post/" main-routes))))
		(is (= 200 (:status (request-post "/api/post/" main-routes
			{:title "mytitle" :content new-content}))))
		(let [body (:body (request-get "/api/post/" main-routes))]
			(is (clojure.contrib.string/substring?
					(str ":\"" new-content "\"") body ))
			(is (clojure.contrib.string/substring? "\"content\":" body))
			(is (clojure.contrib.string/substring? "\"title\":" body)))))

(deftest test-post-json-representation []
	(let [post {:title "title", :text "text",
			:created_date (java.sql.Timestamp. (.getMillis (
					date-time 2011 8 2 3 4 5 6)))}]
		(is (= "20110802T030405.006Z" (:created_date
				(render-post-json post))))))

(deftest test-get-html-posts []
	(let [content (str "some new content" (now)) title (str "new title " (now))]
		(is (= 200 (:status (request-post "/api/post/" main-routes
			{:title title :content content}))))
		(let [body (join (:body (request-get "/post/" main-routes)))]
			(is (clojure.contrib.string/substring? "<html" body))
			(is (clojure.contrib.string/substring? "div class=\"container" body))
			(is (clojure.contrib.string/substring? title body))
			(is (clojure.contrib.string/substring? content body)))))

(deftest test-get-markdownified-html-posts []
	(let [nowstr (str (now))
			reqcontent (str "some markdown content " nowstr " *italic* **bold**")
			expcontent (str "<p>some markdown content " nowstr " <em>italic</em> <strong>bold</strong></p>")
			title (str "new title " (now))]
		(is (= 200 (:status (request-post "/api/post/" main-routes
			{:title title :content reqcontent}))))
		(let [body (join (:body (request-get "/post/" main-routes)))]
			(is (clojure.contrib.string/substring? expcontent body)))))
