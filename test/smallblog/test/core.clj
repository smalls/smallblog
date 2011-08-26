(ns smallblog.test.core
	(:use		[smallblog.core]
				[clojure.test]
				[clojure.string :only (join)]
				[clojure.contrib.string :only (substring?)]
				[clj-time.core :only (now date-time)])
	(:require	[clj-json.core :as json]
				[smallblog.data :as data]))

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
			(is (substring?
					(str ":\"" new-content "\"") body-get ))
			(is (substring? "\"content\":" body-get))
			(is (substring? "\"title\":" body-get))))))

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
			(is (substring? "<html" response-body))
			(is (substring? "div class=\"container" response-body))
			(is (substring? title response-body))
			(is (substring? content response-body))))))

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
			(is (substring?
					expcontent response-body))))))

(deftest test-get-login []
	(let [response-get (request-get "/login")]
		(is (= 200 (:status response-get)))
		(is (substring? "action=\"login\"" (:body response-get))))
	(let [url "foobarbaz"
			response-get (request-get (str "/login?url=" url))]
		(is (= 200 (:status response-get)))
		(is (substring? (str "action=\"" url "\"") (:body response-get)))))
