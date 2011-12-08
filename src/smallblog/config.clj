(ns smallblog.config
    (:import [java.lang System]))

(defn -get-from-env
    ([envname]
     (-get-from-env envname nil))
    ([envname default]
     (if (contains? (System/getenv) envname)
         (get (System/getenv) envname)
         default)))

; db configuration.  Read from DATABASE_URL.
(def *db* (-get-from-env "DATABASE_URL"))

; port and server configuration
(def *port* (Integer/parseInt (-get-from-env "PORT" "3000")))
(def *ssl-port* (let [ssl-port (-get-from-env "SSL_PORT")
                      ssl-port (if (not (nil? ssl-port))
                                   (Integer/parseInt ssl-port)
                                   nil)]
                    ssl-port))
(def *ssl-server* (-get-from-env "SSL_SERVER" "localhost"))

; AWS configuration.  Read from AWS_ACCESS_KEY, AWS_SECRET_KEY, and
; IMAGE_BUCKET
(def *aws-access-key* (-get-from-env "AWS_ACCESS_KEY"))
(def *aws-secret-key* (-get-from-env "AWS_SECRET_KEY"))
(def *image-bucket* (-get-from-env "IMAGE_BUCKET"))

; if not nil, the signup page url requires a get parameter with the key 'token',
; value the value of *signup-token*.  Read from SIGNUP_TOKEN
(def *signup-token* (-get-from-env "SIGNUP_TOKEN"))
