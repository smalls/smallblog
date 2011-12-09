(ns smallblog.dbmigrate
	(:use [smallblog.config])
	(:require [drift.execute]))

(defn -main
    ([]
     (drift.execute/run []))
    ([version]
     (drift.execute/run ["-version" version])))
