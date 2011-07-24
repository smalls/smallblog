(ns start-clojure.test.core
	(:use [start-clojure.core])
	(:require [start-clojure.data :as data])
	(:use [clojure.test]))

(deftest post
	(is "text" (data/make-post "text")))
