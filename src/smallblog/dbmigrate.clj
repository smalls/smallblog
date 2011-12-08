(ns smallblog.dbmigrate
	(:use [smallblog.config])
	(:require [drift.execute]))

(defn -main
    ([]
     (println "migrate to max")
     (drift.execute/run []))
    ([version]
     (println "migrate to version" version)
     (drift.execute/run ["-version" version])))
