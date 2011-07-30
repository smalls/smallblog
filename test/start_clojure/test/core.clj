(ns start-clojure.test.core
	(:use		[start-clojure.core]
				[clojure.test]
				[clj-time.core :only (now)])
	(:require	[start-clojure.data :as data]))

(defn request [resource web-app & params]
	(web-app {:request-method :get :uri resource :params (first params)}))

(deftest routes
	(is (= 200 (:status (request "/post/" main-routes)))))
	

(comment XXX remove all this garbage
; test (make-post), (get-posts 1 0), and (get-post id)
(deftest post
	(let [content (str "some content " (now))]
		(data/make-post "text" content)
		(let [result (data/get-posts 1 0)]
			(is (= 1 (count result)))
			(is (= content (get (first result) :content)))
	  		(let [single-result (data/get-post (get (first result) :id))]
		 		(is (= (get (first result) :content)
						(get single-result :content))))))
	(comment sqlite doesn't sort sub-1s, so this'll clean up for the next test)
	(. Thread (sleep 1001)))

; test getting many posts, and posts over an interval
(deftest interval-post
	(let [content1 (str "first content " (now))
			content2 (str "second content " (now))
			content3 (str "third content " (now))
			content4 (str "fourth content " (now))]
		(. Thread (sleep 1001))
		(data/make-post "title" content1)
		(. Thread (sleep 1001))
		(data/make-post "title" content2)
		(. Thread (sleep 1001))
		(data/make-post "title" content3)
		(let [result (data/get-posts 1 0)]
			(is (= 1 (count result)))
			(is (= content3 (get (first result) :content))))
		(let [result (data/get-posts 1 1)]
			(is (= 1 (count result)))
			(is (= content2 (get (first result) :content))))
		(let [result (data/get-posts 2 1)]
			(is (= 2 (count result)))
			(is (= content2 (get (first result) :content)))
			(is (= content1 (get (second result) :content)))))
	(. Thread (sleep 1001)))
)
