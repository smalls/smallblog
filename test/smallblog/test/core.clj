(ns smallblog.test.core
	(:use		[smallblog.core]
				[clojure.test]
				[clojure.string :only (join)]
				[clojure.contrib.string :only (substring?)]
				[clj-time.core :only (now date-time)]
				[sandbar.auth :only (*sandbar-current-user*)])
	(:require	[clj-json.core :as json]
				[smallblog.data :as data]
				[smallblog.templates :as templates]))

(defn request-get [scheme resource web-app & params]
 	(web-app {:request-method :get :scheme scheme
			:uri resource :params (first params)}))

(defn request-post [scheme resource web-app & params]
 	(web-app {:request-method :post :scheme scheme
			:uri resource :params (first params)}))

(defn parse-json-body [response]
	(let [response-body (:body response)]
		(json/parse-string response-body true)))

(defn with-login-and-blog-id
	"calls func with the first argument of blogid and then the rest of the
	arguments"
	[func]
	(let [username (str (now) "@test.com")
			password "foobar"
			loginid (data/make-login username password)]
		(try
			(binding [*sandbar-current-user*
						(data/login-for-session username password)]
				(let [url (str "/api/blog/")
						response (request-post :https url main-routes
								{:title "blog title"})]
					(binding [*sandbar-current-user*
								(data/login-for-session username password)]
		 				(let [response-body (parse-json-body response)
								blogid (:id response-body)
								args '()]
							(try
								(is (= 200 (:status response))
										(str "request failed " url))
								(is (= 200 (:status (request-get :http
										(str "/api/blog/" blogid "/post/") main-routes))))
								(apply func loginid blogid args)
								(finally (data/delete-blog blogid)))))))
			(finally (data/delete-login loginid)))))

(deftest test-api-routes
	(with-login-and-blog-id (fn [loginid, blogid]
		(let [new-content (str "asdf new content" (now))
				url (str "/api/blog/" blogid "/post/")
				response-post (request-post :http url main-routes
						{:title "mytitle" :content new-content})
				response-get (request-get :http url main-routes)
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
	(with-login-and-blog-id (fn [loginid, blogid]
		(let [content (str "some new content" (now)) title (str "new title " (now))
				response-post (request-post :http
						(str "/api/blog/" blogid "/post/")
						main-routes {:title title :content content})
				response-get (request-get :http (str "/blog/" blogid "/post/")
						main-routes)
				response-body (join (:body response-get))]
			(is (= 200 (:status response-post)))
			(is (= 200 (:status response-get)))
			(is (substring? "<html" response-body))
			(is (substring? "div class=\"container" response-body))
			(is (substring? title response-body))
			(is (substring? content response-body))))))

(deftest test-get-markdownified-html-posts []
	(with-login-and-blog-id (fn [loginid, blogid]
		(let [nowstr (str (now))
				reqcontent (str "some markdown content " nowstr " *italic* **bold**")
				expcontent (str "<p>some markdown content " nowstr " <em>italic</em> <strong>bold</strong></p>")
				title (str "new title " (now))
				response-post (request-post :http 
						(str "/api/blog/" blogid "/post/")
						main-routes {:title title :content reqcontent})
				response-get (request-get :http
						(str "/blog/" blogid "/post/")
						main-routes)
				response-body (join (:body response-get))]
			(is (= 200 (:status response-post)))
			(is (= 200 (:status response-get)))
			(is (substring?
					expcontent response-body))))))

(deftest test-get-login []
	(let [response-get (request-get :https templates/*login-url*
			main-routes)]
		(is (= 200 (:status response-get)))
		(is (substring? (str "action=\"" templates/*login-redirect-fqurl* "\"")
				(join (:body response-get))))))

(deftest test-permissions []
	(with-login-and-blog-id (fn [loginid, blogid]
		(let [noperm_username (str (now) "@test.com")
				noperm_password "foobar"
				noperm_loginid (data/make-login noperm_username noperm_password)]
			(try
				(let [response-get (request-get :http
						(str "/blog/" blogid "/post/new") main-routes)]
					(is (= 200 (:status response-get))))
					(binding [*sandbar-current-user*
							(data/login-for-session noperm_username noperm_password)]
						(let [response-get (request-get :http
								(str "/blog/" blogid "/post/new") main-routes)]
							(is (= 302 (:status response-get)))
							(is (substring? templates/*permission-denied-uri*
									(join (:headers response-get))))))
				(finally (data/delete-login noperm_loginid)))))))
