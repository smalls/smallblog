(ns start-clojure.test.data
	(:use		[start-clojure.core]
				[clojure.test]
				[clj-time.core :only (now)])
	(:require	[start-clojure.data :as data]))

; test (make-post), (get-posts 1 0), and (get-post id)
(deftest post
	(let [content (str "some content " (now))]
		(let [new-row (data/make-post "text" content)]
			(is (= content (:content new-row))))
		(let [result (data/get-posts 1 0)]
			(is (= 1 (count result)))
			(is (= content (get (first result) :content)))
	  		(let [single-result (data/get-post (get (first result) :id))]
		 		(is (= (get (first result) :content)
						(get single-result :content)))))))

; test getting many posts, and posts over an interval
(deftest interval-post
	(let [content1 (str "first content " (now))
			content2 (str "second content " (now))
			content3 (str "third content " (now))
			content4 (str "fourth content " (now))]
		(data/make-post "title" content1)
		(data/make-post "title" content2)
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
			(is (= content1 (get (second result) :content))))))
