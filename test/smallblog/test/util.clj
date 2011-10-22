(ns smallblog.test.util
    (:use		[smallblog.util]
        [clojure.test]))

(deftest test-url-from-request []
         (let [request {:scheme :http
                        :query-string "a=b&b=c"
                        :uri "/blog/67/post/"
                        :server-name "localhost"
                        :server-port 3000
                        }]
             (is (= "http://localhost:3000/blog/67/post/?a=b&b=c"
                     (uri-from-request request)))
             (let [request (assoc request :query-string nil)]
                 (is (= "http://localhost:3000/blog/67/post/"
                         (uri-from-request request))))
             (let [request (assoc request :server-port nil :query-string nil)]
                 (is (= "http://localhost/blog/67/post/"
                         (uri-from-request request))))))
