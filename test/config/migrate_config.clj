(ns config.migrate-config
    (:use [smallblog.config]
          [smallblog.data :only [get-db-version]])
    (:require [clojure.java.jdbc :as sql]))

(defn version-table-exists? []
    (sql/with-connection
        *db*
        (let [rs (sql/resultset-seq
                     (-> (sql/connection)
                         (.getMetaData)
                         (.getTables nil nil "migration_version"
                                     (into-array ["TABLE" "VIEW"]))))]
            (not (empty? rs)))))

(defn get-current-version []
    (get-db-version))

(defn current-db-version []
    (if (not (version-table-exists?))
        0
        (let [version (get-current-version)]
            (if (not (nil? version))
                version
                0))))

(defn update-db-version [version]
    (sql/with-connection
        *db*
        (sql/update-or-insert-values
            :migration_version ["id=0"]
            {:version version :id 0})))


(defn migrate-config []
    {:directory "/test/config/migrations"
     :current-version current-db-version
     :update-version update-db-version})
