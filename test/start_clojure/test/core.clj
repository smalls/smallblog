(ns start-clojure.test.core
	(:use [start-clojure.core])
	(:require [start-clojure.data :as data])
	(:use [clojure.test])
	(:use [clj-time.core :only (now)]))

(deftest post
	(let [content (str "some content " (now))]
		(data/make-post "text" content)
		(let [result (data/get-mostrecent-posts 1 0)]
			(is (= 1 (count result)))
			(is (= content (get (first result) :content)))))
	(comment the sleep is so that sqlite can sort things)
	(. Thread (sleep 1001)))

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
		(let [result (data/get-mostrecent-posts 1 0)]
			(is (= 1 (count result)))
			(is (= content3 (get (first result) :content))))
		(let [result (data/get-mostrecent-posts 1 1)]
			(is (= 1 (count result)))
			(is (= content2 (get (first result) :content))))
		(let [result (data/get-mostrecent-posts 2 1)]
			(is (= 2 (count result)))
			(is (= content2 (get (first result) :content)))
			(is (= content1 (get (second result) :content)))))
	(. Thread (sleep 1001)))
