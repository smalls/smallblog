(ns smallblog.config
    (:import [java.lang System]))

; db configuration.  Read from DATABASE_URL.
(def *db* (get (System/getenv) "DATABASE_URL"))

; AWS configuration.  Read from AWS_ACCESS_KEY, AWS_SECRET_KEY, and
; IMAGE_BUCKET
(def *aws-access-key* (get (System/getenv) "AWS_ACCESS_KEY"))
(def *aws-secret-key* (get (System/getenv) "AWS_SECRET_KEY"))
(def *image-bucket* (get (System/getenv) "IMAGE_BUCKET"))

; if not nil, the signup page url requires a get parameter with the key 'token',
; value the value of *signup-token*.  Read from SIGNUP_TOKEN
(def *signup-token* (if (contains? (System/getenv) "SIGNUP_TOKEN")
                        (get (System/getenv) "SIGNUP_TOKEN")
                        nil))
