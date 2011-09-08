(ns smallblog.test.data
	(:use		[smallblog.core]
				[clojure.test]
				[sandbar.auth :only (*sandbar-current-user*)]
				[sandbar.stateful-session :only (sandbar-session)]
				[clj-time.core :only (now)])
	(:require	[smallblog.data :as data]
				[clj-sql.core :as sql]))

(deftest test-login
	"test basic creation and retrival of login rows, test-login-for-session,
	and check-passwords"
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
					loginsession (data/login-for-session login password)]

				; first, regular login
				(is (= loginid (:id loginobj)))
				(is (= login (:email loginobj)))
				(is (= password (:password loginobj)))

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
				; XXX
				;(is (thrown-with-msg? Exception #"bad.*"
				;		(data/check-password login password "foo" "foo")))
				)
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
	"test basic creation of posts - including (make-post), (get-posts 1 0), and (get-post id)"
	[]
	(let [content (str "some content " (now))
			loginid (data/make-login (str (now) "@test.com") "password")
			blogid (:id (data/make-blog loginid "blogname"))]
	 	(try
			(let [new-row (data/make-post blogid "text" content)]
				(is (= content (:content new-row))))
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
				(sql/with-connection data/*db*
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
