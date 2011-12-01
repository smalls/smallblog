(ns smallblog.test.data
    (:use [smallblog.core]
          [smallblog.config]
          [smallblog.templates :only (*image-blog*, *image-full*, *image-thumb*)]
          [clojure.test]
          [sandbar.auth :only (*sandbar-current-user*)]
          [sandbar.stateful-session :only (sandbar-session)]
          [clj-time.core :only (now)])
    (:require [smallblog.data :as data]
              [clojure.java.jdbc :as sql])
    (:import [java.io File FileNotFoundException]))

(deftest test-login
         "test basic creation and retrival of login rows, test-login-for-session,
         get-login, and check-passwords"
         []
         (let [login (str (now) "newlogin@test-login.com")
               password "somepassword"
               loginid (data/make-login login password)]
             (try
                 (let [blogobj1 (data/make-blog loginid "foo")
                       blogobj2 (data/make-blog loginid "bar")
                       expectedowner1 (keyword (str data/owner-blog-prefix
                                                   (:id blogobj1)))
                       expectedowner2 (keyword (str data/owner-blog-prefix
                                                   (:id blogobj2)))
                       loginobj (data/get-login login password)
                       login-wrong-pw (data/get-login login "foo")
                       loginsession (data/login-for-session login password)]

                     ; first, regular login
                     (is (= loginid (:id loginobj)))
                     (is (= login (:email loginobj)))
                     (is (data/-check-hashed password (:password loginobj)))
                     (is (not (= password (:password loginobj))))

                     ; a bad password should result in nil
                     (is (nil? login-wrong-pw))

                     ; now login-session
                     (is (= loginid (:id loginsession)))
                     (is (= login (:name loginsession)))
                     (is (= nil (:password loginsession)))
                     (is (= expectedowner1 (nth (:roles loginsession) 0)))
                     (is (= expectedowner2 (nth (:roles loginsession) 1)))

                     (data/check-password "foo" "foo")
                     (data/check-password login password "foo" "foo")
                     (is (thrown-with-msg? Exception #"passwords.*match"
                             (data/check-password "bar" "foo")))
                     (is (thrown-with-msg? Exception #"passwords.*match"
                             (data/check-password login password "bar" "foo")))
                     (is (thrown-with-msg? Exception #"bad.*"
                             (data/check-password login "bar" "foo" "foo"))))
                 (finally (data/delete-login loginid)))))

(deftest test-change-password
         []
         (let [login (str (now) "newlogin@test-change-password.com")
               password "somepassword"
               loginid (data/make-login login password)]
             (try 
                 (data/change-password login password "foo" "foo")
                 (is (not (nil? (data/get-login login "foo"))))
                 (finally (data/delete-login loginid)))))


(deftest test-role-keywords
         "test the -role-keywords method"
         []
         (is (= [:owner-blog-1 :owner-blog-2]
                 (data/-role-keywords '({:id 1} {:id 2}))))
         (is (= [] (data/-role-keywords '()))))

(deftest test-get-owned-blogs
         "test basic creation and retrival of login rows"
         []
         (let [login (str (now) "newlogin@test-get-owned-blogs.com")
               password "somepassword"
               loginid (data/make-login login password)]
             (try
                 (let [blogobj1 (data/make-blog loginid "foo")
                       blogobj2 (data/make-blog loginid "bar")
                       blogids (data/get-roles-for-user loginid)
                       blogs (data/get-blogs loginid)]
                     (is (= (keyword (str data/owner-blog-prefix (:id blogobj1)))
                             (nth blogids 0)))
                     (is (= (keyword (str data/owner-blog-prefix (:id blogobj2)))
                             (nth blogids 1)))
                     (is (= 2 (count blogs)))
                     (is (= "foo" (:title (first blogs))))
                     (is (= "bar" (:title (last blogs)))))
                 (finally (data/delete-login loginid)))))

(deftest post
         "test basic creation of posts - including (make-post), (get-posts 1 0),
         (get-post id), and (count-posts)"
         []
         (let [content (str "some **content** " (now))
               expected-markdown-content "<p>some <strong>content</strong>"
               loginid (data/make-login (str (now) "@test.com") "password")
               blogid (:id (data/make-blog loginid "blogname"))]
             (try
                 (let [new-row (data/make-post blogid "text" content)]
                     (is (= expected-markdown-content
                             (subs (:converted_content new-row) 0
                                 (count expected-markdown-content))))
                     (is (= content (:content new-row)))
                     (is (= 1 (data/count-posts blogid))))
                 (let [result (data/get-posts blogid 2 0)]
                     (is (= 1 (count result)))
                     (is (= content (get (first result) :content)))
                     (let [single-result (data/get-post blogid (:id (first result)))]
                         (is (= (get (first result) :content)
                                 (get single-result :content)))))
                 (finally (data/delete-login loginid)))))

(deftest post-cascade-delete
         "test that the cascade delete removes nested posts when deleting a blog"
         (let [loginid (data/make-login (str (now) "@test.com") "password")]
             (try
                 (let [blogid (:id (data/make-blog loginid "blogname"))
                       postid (:id (data/make-post blogid "title" "content"))]
                     (data/delete-blog blogid)
                     (sql/with-connection *db*
                         (sql/with-query-results rs
                             ["select * from post where id=?" postid]
                             (is (= 0 (count rs))))))
                 (finally (data/delete-login loginid)))))

(deftest post-interval
         "test getting posts over certain intervals"
         (let [content1 (str "first content " (now))
               content2 (str "second content " (now))
               content3 (str "third content " (now))
               content4 (str "fourth content " (now))
               loginid (data/make-login (str (now) "@test.com") "password")
               blogid (:id (data/make-blog loginid "blogname"))]
             (try
                 (data/make-post blogid "title" content1)
                 (data/make-post blogid "title" content2)
                 (data/make-post blogid "title" content3)
                 (is (= 3 (data/count-posts blogid)))
                 (let [result (data/get-posts blogid 1 0)]
                     (is (= 1 (count result)))
                     (is (= content3 (get (first result) :content))))
                 (let [result (data/get-posts blogid 1 1)]
                     (is (= 1 (count result)))
                     (is (= content2 (get (first result) :content))))
                 (let [result (data/get-posts blogid 2 1)]
                     (is (= 2 (count result)))
                     (is (= content2 (get (first result) :content)))
                     (is (= content1 (get (second result) :content))))
                 (finally (data/delete-login loginid)))))

(deftest test-get-current-userid
         "test getting the current user's id"
         (let [email (str (now) "@test.com")
               password "password"
               loginid (data/make-login email password)]
             (try 
                 (binding [*sandbar-current-user*
                           (data/login-for-session email password)]
                     (is (not (nil? (data/get-current-user)))))
                 (binding [*sandbar-current-user* nil]
                     (is (nil? (data/get-current-user))))
                 (finally (data/delete-login loginid)))))

(deftest test-password-hash []
         (let [hashed (data/-hash-pw "foo")]
             (is (not (= hashed "foo")))
             (is (data/-check-hashed "foo" hashed))
             (is (not (data/-check-hashed "fooo" hashed)))))

(deftest test-get-content-type []
         (is (= ["image/jpeg" "jpeg"] (data/get-content-type "image/jpeg")))
         (is (= ["image/png" "png"] (data/get-content-type "image/png")))
         (is (= ["image/png" "png"] (data/get-content-type "image/gif")))
         (is (= ["image/png" "png"] (data/get-content-type "image/nonesuch"))))

(deftest test-image-name []
         (is (= "12-lamematt-full.jpg"
                (data/-image-name 12 "lamematt.png" *image-full* "image/jpeg")))
         (is (= "12-lamematt-blog.png"
                (data/-image-name 12 "lamematt.png" *image-blog* "image/png")))
         (is (= "abc-lamematt.bar-blog.jpg"
                (data/-image-name "abc" "lamematt.bar.png" *image-blog* "image/jpeg")))
         (is (= "abc-lamematt.bar-blog.png"
                (data/-image-name "abc" "lamematt.bar.png" *image-blog* "image/jabcd")))
         (is (= "abc-lamematt.bar-blog.jpg"
                (data/-image-name "abc" "lamematt.bar." *image-blog* "image/jpeg")))
         (is (= "abc-lamematt-blog.jpg"
                (data/-image-name "abc" "lamematt" *image-blog* "image/jpeg"))))

(deftest test-make-image
         "test make-image, get-image, and get-images"
         []
         (if-let [disable-test true]
             (println "this test contacts s3 and costs money; disabled")
             (let [loginid (data/make-login (str (now) "@test.com") "password")]
                 (println "warning: this contacts s3 and is costing money")
                 (try
                     (let [path (File. "test/smallblog/test/data/IMG_0568.jpg")
                           imageid (data/make-image "IMG_0568.jpg" "title" "description"
                                                    "image/tiff" path nil loginid)
                           imageid2 (data/make-image "IMG_0568.jpg" "title2" "description"
                                                    "image/tiff" path nil loginid)]
                         (is (.exists path))
                         (is (thrown-with-msg? Exception #"No such file or directory"
                                      (data/make-image "foo.tiff" "title" "description"
                                                       "image/tiff" (File. "nonesuch.tiff")
                                                       nil loginid)))
                         (let [image (data/get-image imageid *image-blog*)]
                             (is (not (nil? image)))
                             (.close (:image-bytes image)))
                         (let [images (data/get-images loginid 3 0)]
                             (is (= 2 (count images)))
                             (is (= "title2" (:title (first images)))))
                         (let [images (data/get-images loginid 1 0)]
                             (is (= 1 (count images)))
                             (is (= "title2" (:title (first images)))))
                         (let [images (data/get-images loginid 1 1)]
                             (is (= 1 (count images)))
                             (is (= "title" (:title (first images))))))
                     (finally (data/delete-login loginid))))))

(deftest test-image-scoped-to-blog
         "test that images can be scoped to a blog"
         []
         (let [loginid (data/make-login (str (now) "@test.com") "password")]
             (try
                 (let [blogid (:id (data/make-blog loginid "someblog"))
                       faux-blogid (+ 1 blogid)
                       imageid1 (sql/with-connection
                                    *db*
                                    (sql/insert-record :image {:filename "withblog"
                                                              :title "title"
                                                              :description "desc"
                                                              :owner loginid
                                                              :blog blogid}))
                       imageid2 (sql/with-connection
                                    *db*
                                    (sql/insert-record :image {:filename "noblog"
                                                              :title "title"
                                                              :description "desc"
                                                              :owner loginid
                                                              :blog nil}))]
                     (sql/with-connection
                         *db*
                         (is (= 2 (count (data/get-images loginid 10 0))))
                         (is (= 2 (count (data/get-images loginid nil 10 0))))
                         (is (= 2 (count (data/get-images loginid blogid 10 0))))
                         (is (= 1 (count (data/get-images loginid faux-blogid 10 0))))))
                 (finally (data/delete-login loginid)))))


(deftest test-domains
         "test make-domain, get-domain, and get-user-domains"
         []
         (let [loginid (data/make-login (str (now) "@test.com") "password")]
             (try
                 (let [domainname "ablahblah"
                       id (data/make-domain domainname loginid)
                       loaded-domain (data/get-domain domainname)
                       user-domains (data/get-user-domains loginid)]
                     (is (not (nil? loaded-domain)))
                     (is (= domainname (:domain loaded-domain)))
                     (is (not (nil? user-domains)))
                     (is (= 1 (count user-domains)))
                     (is (= domainname (:domain (first user-domains)))))
                 (finally (data/delete-login loginid)))))
