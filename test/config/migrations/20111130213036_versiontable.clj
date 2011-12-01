(ns config.migrations.20111130213036-versiontable
    (:use [smallblog.config])
    (:require [clojure.java.jdbc :as sql]))

(defn up
    "Migrates the database up to version 20111130213036."
    []
    (println "config.migrations.20111130213036-versiontable up...")
    (sql/with-connection
        *db*
        (sql/create-table
            :migration_version
            [:id "INT" "PRIMARY KEY"]
            [:version "BIGINT"]))
    (println "config.migrations.20111130213036-versiontable up done"))
  
(defn down
    "Migrates the database down from version 20111130213036."
    []
    (println "config.migrations.20111130213036-versiontable down...")
    (sql/with-connection
        *db*
        (sql/drop-table :migration_version))
    (println "config.migrations.20111130213036-versiontable down done"))
