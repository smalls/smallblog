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
			(is (= content (get (first result) :content))))))
