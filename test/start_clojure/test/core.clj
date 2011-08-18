(ns start-clojure.test.core
	(:use		[start-clojure.core]
				[clojure.test]
				[clojure.string :only (join)]
				[clj-time.core :only (now date-time)])
	(:require	[clojure.contrib.string]
				[clj-json.core :as json]
				[start-clojure.data :as data]))

(defn request-get [resource web-app & params]
 	(web-app {:request-method :get :uri resource :params (first params)}))

(defn request-post [resource web-app & params]
 	(web-app {:request-method :post :uri resource :params (first params)}))

(defn parse-json-body [response]
	(let [response-body (:body response)]
		(json/parse-string response-body true)))

(deftest test-api-routes
	(let [new-content (str "asdf new content" (now))
			url (str "/api/blog/")
			response (request-post url main-routes {:title "blog title"})
			response-body (parse-json-body response)
			blogid (:id response-body)]
		(try
			(is (= 200 (:status response)) (str "request failed " url))
			(is (= 200 (:status (request-get (str "/api/blog/" blogid "/post/")
					main-routes))))
			(is (= 200 (:status (request-post (str "/api/blog/" blogid "/post/")
				main-routes {:title "mytitle" :content new-content}))))
			(let [url (str "/api/blog/"  blogid "/post/")
					response (request-get url main-routes)
		 			body (:body response)]
				(is (= 200 (:status response)) (str "request failed " url))
				(is (clojure.contrib.string/substring?
						(str ":\"" new-content "\"") body ))
				(is (clojure.contrib.string/substring? "\"content\":" body))
				(is (clojure.contrib.string/substring? "\"title\":" body)))
			(finally (data/delete-blog blogid)))))

(deftest test-post-json-representation []
	(let [post {:title "title", :text "text",
			:created_date (java.sql.Timestamp. (.getMillis (
					date-time 2011 8 2 3 4 5 6)))}]
		(is (= "20110802T030405.006Z" (:created_date
				(render-post-json post))))))

(deftest test-get-html-posts []
	(let [content (str "some new content" (now)) title (str "new title " (now))
			url (str "/api/blog/")
			response (request-post url main-routes {:title "blog title"})
			response-body (parse-json-body response)
			blogid (:id response-body)]
		(try
			(is (= 200 (:status (request-post (str "/api/blog/" blogid "/post/")
					main-routes {:title title :content content}))))
			(let [body (join (:body (request-get (str "/blog/" blogid "/post/")
					main-routes)))]
				(is (clojure.contrib.string/substring? "<html" body))
				(is (clojure.contrib.string/substring? "div class=\"container" body))
				(is (clojure.contrib.string/substring? title body))
				(is (clojure.contrib.string/substring? content body)))
			(finally (data/delete-blog blogid)))))

(deftest test-get-markdownified-html-posts []
	(let [nowstr (str (now))
			reqcontent (str "some markdown content " nowstr " *italic* **bold**")
			expcontent (str "<p>some markdown content " nowstr " <em>italic</em> <strong>bold</strong></p>")
			title (str "new title " (now))
			url (str "/api/blog/")
			response (request-post url main-routes {:title "blog title"})
			response-body (parse-json-body response)
			blogid (:id response-body)]
		(try
			(is (= 200 (:status (request-post (str "/api/blog/" blogid "/post/")
					main-routes {:title title :content reqcontent}))))
			(let [body (join (:body (request-get (str "/blog/" blogid "/post/")
					main-routes)))]
				(is (clojure.contrib.string/substring? expcontent body)))
			(finally (data/delete-blog blogid)))))
