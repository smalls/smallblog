(ns start-clojure.test.data
	(:use		[start-clojure.core]
				[clojure.test]
				[clj-time.core :only (now)])
	(:require	[start-clojure.data :as data]
				[clj-sql.core :as sql]))

(deftest post
	"test basic creation of posts - including (make-post), (get-posts 1 0), and (get-post id)"
	(let [content (str "some content " (now))
		blogid (:id (data/make-blog "blogname"))]
	 	(try
			(let [new-row (data/make-post blogid "text" content)]
				(is (= content (:content new-row))))
			(let [result (data/get-posts blogid 2 0)]
				(is (= 1 (count result)))
				(is (= content (get (first result) :content)))
				(let [single-result (data/get-post blogid (:id (first result)))]
					(is (= (get (first result) :content)
							(get single-result :content)))))
			(finally (data/delete-blog blogid)))))

(deftest post-cascade-delete
	"test that the cascade delete removes nested posts when deleting a blog"
	(let [blogid (:id (data/make-blog "blogname"))]
		(try
			(let [postid (:id (data/make-post blogid "title" "content"))]
				(data/delete-blog blogid)
				(sql/with-connection data/*db*
					(sql/with-query-results rs
							["select * from post where id=?" postid]
						(is (= 0 (count rs))))))
			(finally (data/delete-blog blogid)))))

(deftest post-interval
	"test getting posts over certain intervals"
	(let [content1 (str "first content " (now))
			content2 (str "second content " (now))
			content3 (str "third content " (now))
			content4 (str "fourth content " (now))
			blogid (:id (data/make-blog "blogname"))]
		(try
			(data/make-post blogid "title" content1)
			(data/make-post blogid "title" content2)
			(data/make-post blogid "title" content3)
			(let [result (data/get-posts blogid 1 0)]
				(is (= 1 (count result)))
				(is (= content3 (get (first result) :content))))
			(let [result (data/get-posts blogid 1 1)]
				(is (= 1 (count result)))
				(is (= content2 (get (first result) :content))))
			(let [result (data/get-posts blogid 2 1)]
				(is (= 2 (count result)))
				(is (= content2 (get (first result) :content)))
				(is (= content1 (get (second result) :content))))
			(finally (data/delete-blog blogid)))))
