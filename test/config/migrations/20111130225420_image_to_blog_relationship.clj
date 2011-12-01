(ns config.migrations.20111130225420-image-to-blog-relationship
    (:use [smallblog.config])
    (:require [clojure.java.jdbc :as sql]))

(defn up
    "Migrates the database up to version 20111130225420."
    []
    (println "config.migrations.20111130225420-image-to-blog-relationship up...")
    (sql/with-connection
        *db*
        (sql/do-commands
            "ALTER TABLE ONLY image ADD COLUMN blog int REFERENCES blog(id) ON DELETE CASCADE"))
    (println "config.migrations.20111130225420-image-to-blog-relationship up done"))

(defn down
    "Migrates the database down from version 20111130225420."
    []
    (println "config.migrations.20111130225420-image-to-blog-relationship down...")
    (sql/with-connection
        *db*
        (sql/do-commands
            "ALTER TABLE ONLY image DROP COLUMN blog"))
    (println "config.migrations.20111130225420-image-to-blog-relationship down done"))
