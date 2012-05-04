(ns smallblog.test.templates
    (:use		[smallblog.templates]
        [clojure.test]
        [clojure.string :only (join)]
        [clj-time.core :only (now date-time)])
    (:require	[clojure.contrib.string]))

(deftest test-markdownify
         (let [reqcontent (str "some markdown content *italic* **bold**")
               expcontent (str "<p>some markdown content <em>italic</em> <strong>bold</strong></p>")]
             (is (= expcontent (markdownify reqcontent)))))

(deftest test-markdownify-attack
         (let [reqcontent (str "some content <script>evil</script>")
               expcontent (str "<p>some content evil</p>")]
             (is (= expcontent (markdownify reqcontent)))))

(deftest test-domains-for-blog
         (let [domains [{:id 1 :blogid 1} {:id 2 :blogid 3} {:id 3 :blogid 1}]
               filtered-1 (-domains-for-blog 1 domains)
               filtered-2 (-domains-for-blog 2 domains)
               filtered-3 (-domains-for-blog 3 domains)]
             (is (= 2 (count filtered-1)))
             (is (= 0 (count filtered-2)))
             (is (= 1 (count filtered-3)))))

(deftest test-domains-with-blognames
         (let [domains [{:id 1 :blogid 1 :domain "foo.com"}
                        {:id 2 :blogid 1 :domain "bar.com"}
                        {:id 3 :blogid 2 :domain "baz.com"}
                        {:id 4 :blogid nil :domain "baz.com"}]
               blogs [{:id 1 :title "first"} {:id 2 :title "second"}]
               result (-domains-with-blognames blogs domains)]
             (is (= 4 (count result)))
             (is (= "first" (get result 1)))
             (is (= "first" (get result 2)))
             (is (= "second" (get result 3)))
             (is (nil? (get result 4)))))

(deftest test-is-first-page?
         (is (= true (-is-first-page? 0 10 11)))
         (is (= false (-is-first-page? 1 10 11))))

(deftest test-is-last-page?
         (is (= true (-is-last-page? 0 10 0)))
         (is (= false (-is-last-page? 0 10 11)))
         (is (= true (-is-last-page? 1 10 11))))

(deftest test-pager-text
         (is (= "page 1 of 1" (-pager-text 0 10 0)))
         (is (= "page 1 of 2" (-pager-text 0 10 11)))
         (is (= "page 2 of 2" (-pager-text 1 10 11))))

(deftest test-create-sslurl
         (is (= "https://af:8000/foo" (sslurl "af" "8000" "/foo")))
         (is (= "https://af/foo" (sslurl "af" "443" "/foo"))))

(deftest test-permalink-url []
         (let [post {:title "title", :text "text",
                     :created_date (java.sql.Timestamp.
                                       (.getMillis (date-time 2011 8 2 3 4 5 6)))}
               ctx {:url "http://foo/bar/"}
               url (-permalink-url ctx post)]
             (is (= (str (:url ctx) "2011/8/title") url))))
