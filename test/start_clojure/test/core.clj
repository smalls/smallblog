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

(defn with-blog-id
	"calls func with the first argument of blogid and then the rest of the
	arguments"
	[func]
	(let [url (str "/api/blog/")
			response (request-post url main-routes {:title "blog title"})
			response-body (parse-json-body response)
			blogid (:id response-body)
			args '()]
		(try
			(is (= 200 (:status response)) (str "request failed " url))
			(is (= 200 (:status (request-get (str "/api/blog/" blogid "/post/")
					main-routes))))
			(apply func blogid args)
			(finally (data/delete-blog blogid)))))

(deftest test-api-routes
	(with-blog-id (fn [blogid]
		(let [new-content (str "asdf new content" (now))
				url (str "/api/blog/" blogid "/post/")
				response-post (request-post url main-routes
						{:title "mytitle" :content new-content})
				response-get (request-get url main-routes)
				body-get (:body response-get)]
			(is (= 200 (:status response-post)))
			(is (= 200 (:status response-get)) (str "request failed " url))
			(is (clojure.contrib.string/substring?
					(str ":\"" new-content "\"") body-get ))
			(is (clojure.contrib.string/substring? "\"content\":" body-get))
			(is (clojure.contrib.string/substring? "\"title\":" body-get))))))

(deftest test-post-json-representation []
	(let [post {:title "title", :text "text",
			:created_date (java.sql.Timestamp. (.getMillis (
					date-time 2011 8 2 3 4 5 6)))}]
		(is (= "20110802T030405.006Z" (:created_date
				(render-post-json post))))))

(deftest test-get-html-posts []
	(with-blog-id (fn [blogid]
		(let [content (str "some new content" (now)) title (str "new title " (now))
				response-post (request-post (str "/api/blog/" blogid "/post/")
						main-routes {:title title :content content})
				response-get (request-get (str "/blog/" blogid "/post/")
						main-routes)
				response-body (join (:body response-get))]
			(is (= 200 (:status response-post)))
			(is (= 200 (:status response-get)))
			(is (clojure.contrib.string/substring? "<html" response-body))
			(is (clojure.contrib.string/substring? "div class=\"container" response-body))
			(is (clojure.contrib.string/substring? title response-body))
			(is (clojure.contrib.string/substring? content response-body))))))

(deftest test-get-markdownified-html-posts []
	(with-blog-id (fn [blogid]
		(let [nowstr (str (now))
				reqcontent (str "some markdown content " nowstr " *italic* **bold**")
				expcontent (str "<p>some markdown content " nowstr " <em>italic</em> <strong>bold</strong></p>")
				title (str "new title " (now))
				response-post (request-post (str "/api/blog/" blogid "/post/")
						main-routes {:title title :content reqcontent})
				response-get (request-get (str "/blog/" blogid "/post/")
						main-routes)
				response-body (join (:body response-get))]
			(is (= 200 (:status response-post)))
			(is (= 200 (:status response-get)))
			(is (clojure.contrib.string/substring?
					expcontent response-body))))))
