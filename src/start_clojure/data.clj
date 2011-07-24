(ns start-clojure.data
	(:use [clojure.contrib.sql :only (with-connection with-query-results)] )
	(:import (java.sql DriverManager)))

(Class/forName "org.sqlite.JDBC")

(defn make-post [text] text)
