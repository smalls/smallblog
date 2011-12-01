(ns config.migrations.20111130220559-initial
    (:use [smallblog.config])
    (:require [clojure.java.jdbc :as sql]))

(defn up
    "Migrates the database up to version 20111130220559."
    []
    (println "config.migrations.20111130220559-initial up...")
    (sql/with-connection
        *db*
        (sql/create-table
            :login
            [:id "SERIAL" "PRIMARY KEY"]
            [:email "TEXT" "NOT NULL UNIQUE"]
            [:password "TEXT" "NOT NULL"])
        (sql/create-table
            :blog
            [:id "SERIAL" "PRIMARY KEY"]
            [:owner "int" "NOT NULL REFERENCES login(id) ON DELETE CASCADE"]
            [:title "TEXT" "NOT NULL"]
            [:created_date "TIMESTAMP" "with time zone DEFAULT current_timestamp NOT NULL"])
        (sql/create-table
            :post
            [:id "BIGSERIAL" "PRIMARY KEY"]
            [:blogid "int" "NOT NULL REFERENCES blog(id) ON DELETE CASCADE"]
            [:title "TEXT" "NOT NULL"]
            [:content "TEXT" "NOT NULL"]
            [:converted_content "TEXT" "NOT NULL"]
            [:created_date "TIMESTAMP" "with time zone DEFAULT current_timestamp NOT NULL"])
        (sql/create-table
            :s3reference
            [:id "BIGSERIAL" "PRIMARY KEY"]
            [:bucket "TEXT" "NOT NULL"]
            [:filename "TEXT" "NOT NULL"]
            [:contenttype "TEXT" "NOT NULL"]
            [:md5hash "TEXT" "NOT NULL"]
            [:owner "int" "NOT NULL REFERENCES login(id) ON DELETE CASCADE"])
        (sql/create-table
            :image
            [:id "BIGSERIAL" "PRIMARY KEY"]
            [:owner "int" "NOT NULL REFERENCES login(id) ON DELETE CASCADE"]
            [:filename "TEXT" "NOT NULL"]
            [:title "TEXT"]
            [:description "TEXT"]
            [:fullimage "BIGINT" "REFERENCES s3reference"]
            [:blogwidthimage "BIGINT" "REFERENCES s3reference"]
            [:thumbnail "BIGINT" "REFERENCES s3reference"]
            [:created_date "TIMESTAMP" "with time zone DEFAULT current_timestamp NOT NULL"])
        (sql/create-table
            :domain
            [:id "BIGSERIAL" "PRIMARY KEY"]
            [:domain "TEXT" "NOT NULL UNIQUE"]
            [:owner "int" "NOT NULL REFERENCES login(id) ON DELETE CASCADE"]
            [:blogid "int" "REFERENCES blog(id) ON DELETE SET NULL"]))
    (println "config.migrations.20111130220559-initial up done"))
  
(defn down
    "Migrates the database down from version 20111130220559."
    []
    (println "config.migrations.20111130220559-initial down...")
    (sql/with-connection
        *db*
        (sql/drop-table :domain)
        (sql/drop-table :image)
        (sql/drop-table :s3reference)
        (sql/drop-table :post)
        (sql/drop-table :blog)
        (sql/drop-table :login))
    (println "config.migrations.20111130220559-initial down done"))
