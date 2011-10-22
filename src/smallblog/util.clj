(ns smallblog.util
    (:use		[clojure.contrib.string :only (as-str)]))

(defn uri-from-request
    "get the URI from the request map"
    [request]
    (str (as-str (:scheme request)) "://" (:server-name request)
        (if (not (nil? (:server-port request)))
            (str ":" (:server-port request))
            "")
        (:uri request)
        (if (not (nil? (:query-string request)))
            (str "?" (:query-string request))
            "")))
