(ns smallblog.templates
    (:use [ring.util.codec :only (url-encode)]
          [clojure.string :only (join)]
          [smallblog.config])
    (:require [net.cgrand.enlive-html :as html]
              [clj-time.core :as clj-time]
              [clj-time.format :as clj-time-format]
              [clj-time.coerce :as clj-time-coerce])
    (:import [org.mozilla.javascript Context ScriptableObject]))

(defn sslurl
    "create an ssl url; url must start with a /"
    ([url] (sslurl *ssl-server* *ssl-port* url))
    ([server port url] (str "https://" server
                            (if (not (= "443" (str port)))
                                (str ":" port))
                            url)))

; when changing these, also check snippets.html and core/security-config
(def *login-url* "/login")
(def *login-fqurl* (sslurl *login-url*))

(def *signup-url* "/signup")
(def *signup-fqurl* (sslurl *signup-url*))

(def *account-url* "/account")
(def *account-fqurl* (sslurl *account-url*))

(def *login-redirect-url* "/login-redirect")
(def *login-redirect-fqurl* (sslurl *login-redirect-url*))
(def *logout-url* "/logout")
(def *permission-denied-uri* "/permission-denied")

(def *image-url* "/images")
(def *image-full* "full")
(def *image-blog* "blog")
(def *image-thumb* "thumb")


(def date-output-format (clj-time-format/formatter "dd MMM yyyy HH:mm"))

(defn markdownify [post]
    (let [cx (Context/enter)
          scope (.initStandardObjects cx)
          input (Context/javaToJS post scope)
          script (str
                     (html/get-resource "smallblog/Markdown.Converter.js" slurp)
                     "window = {Markdown: {Converter: Markdown.Converter}};"
                     (html/get-resource "smallblog/Markdown.Sanitizer.js" slurp)
                     "san = window.Markdown.getSanitizingConverter;"
                     "san().makeHtml(input);")]
        (try
            (ScriptableObject/putProperty scope "input" input)
            (let [result (.evaluateString cx scope script "<cmd>" 1 nil)]
                (Context/toString result))
            (finally (Context/exit)))))

(html/defsnippet valid-user-menu
                 "smallblog/templates/snippets.html"
                 [:#valid-user-menu]
                 [ctx]
                 [:.accountlink] (html/set-attr :href *account-fqurl*))

(html/defsnippet no-user-menu
                 "smallblog/templates/snippets.html"
                 [:#no-user-menu]
                 [ctx]
                 [:.signuplink] (html/set-attr :href *signup-fqurl*)
                 [:.loginlink] (html/set-attr :href
                                   (if (nil? (:url ctx))
                                       *login-fqurl*
                                       (str *login-fqurl* "?url=" (url-encode (:url ctx))))))

(defn user-menu [ctx]
    (if (nil? (:user ctx))
        (no-user-menu ctx)
        (valid-user-menu ctx)))

(html/defsnippet new-post-button-snippet
                 "smallblog/templates/snippets.html"
                 [:#newpost]
                 [ctx])

(defn new-post-button [ctx]
    (if (:is-blog-owner ctx)
        (html/content (new-post-button-snippet ctx))
        nil))

(defn -main-div-post [ctx]
    (html/clone-for [item (:posts ctx)]
                    [:.posttitle] (html/content (:title item))
                    [:.postdate] (html/content
                                     (clj-time-format/unparse
                                         date-output-format
                                         (clj-time-coerce/from-date (:created_date item))))
                    [:.postbody] (html/html-content (:converted_content item))))

(defn -is-first-page? [page pagination total-posts]
    (= 0 page))

(defn -number-of-pages [pagination total-posts]
    (int (/ total-posts pagination)))

(defn -is-last-page? [page pagination total-posts]
    (= page (-number-of-pages pagination total-posts)))

(defn -pager-text [page pagination total-posts]
    (str "Page " (+ 1 page) " of " (+ 1 (-number-of-pages pagination total-posts))))

(html/deftemplate main "smallblog/templates/main.html"
                  [ctx]
                  [:p#blogname] (html/content (:blogname ctx))
                  [:head :title] (html/content (:blogname ctx))
                  [:#menu] (html/content (user-menu ctx))
                  [:#newpost] (new-post-button ctx)
                  [:div.post] (-main-div-post ctx)
                  [:a#pager-newer] (if (-is-first-page?
                                           (:page ctx) (:pagination ctx) (:total-posts ctx))
                                       nil)
                  [:a#pager-older] (if (-is-last-page?
                                           (:page ctx) (:pagination ctx) (:total-posts ctx))
                                       nil)
                  [:span#pager-text] (html/content (-pager-text
                                                       (:page ctx) (:pagination ctx)
                                                       (:total-posts ctx))))

(html/deftemplate newpost "smallblog/templates/newpost.html"
                  [ctx]
                  [:p#blogname] (html/content (:blogname ctx))
                  [:head :title] (html/content (:blogname ctx))
                  [[:meta (html/attr= :name "blog")]] (html/set-attr
                                                          :content (str (:blogid ctx)))
                  [:#menu] (html/content (user-menu ctx)))

(html/deftemplate login "smallblog/templates/login.html"
                  [ctx]
                  [:.signuplink] (html/set-attr :href *signup-fqurl*)
                  [:#login_form] (html/set-attr :action
                                     (if (nil? (:url ctx))
                                         *login-redirect-fqurl*
                                         (str *login-redirect-fqurl* "?url=" (url-encode(:url ctx))))))

(html/deftemplate contact "smallblog/templates/contact.html" [])

(html/deftemplate about "smallblog/templates/about.html" [])


(html/defsnippet signup-restricted-snippet
                 "smallblog/templates/snippets.html"
                 [:#signup-restricted]
                 [])

(html/deftemplate signup-restricted "smallblog/templates/contact.html" []
                  [:#contact-admin] (html/before (signup-restricted-snippet)))


(defn -domains-for-blog [blogid domains]
    (filter #(= blogid (:blogid %)) domains))

(defn -domains-with-blognames [blogs domains]
    "return a map of domainid -> blogtitle"
    (let [blogbyid (reduce (fn [m blog] (assoc m (:id blog) (:title blog)))
                       {} blogs)]
        (reduce (fn [m domain]
                    (assoc m (:id domain) (get blogbyid (:blogid domain))))
            {} domains)))

(html/deftemplate account "smallblog/templates/account.html"
                  [ctx]
                  [:#email] (html/set-attr :value (:name (:user ctx)))
                  [:#menu] (html/content (user-menu ctx))
                  [:#imageform] (html/set-attr :action
                                    (if (nil? (:url ctx))
                                        *image-url*
                                        (str *image-url* "?url=" (url-encode (:url ctx)))))
                  [:tr.blog] (html/clone-for [item (:blogs ctx)]
                                 [:.blogtitle] (html/set-attr :href
                                                   (str "/blog/" (:id item) "/post/"))
                                 [:.blogtitle] (html/content (:title item))
                                 [:.blogdomains] (html/content (join "\n"
                                                                   (map #(:domain %)
                                                                       (-domains-for-blog (:id item)
                                                                           (:domains ctx))))))
                  [:tr.domain-entry] (let [domain-to-blog
                                           (-domains-with-blognames (:blogs ctx) (:domains ctx))]
                                         (html/clone-for [item (:domains ctx)]
                                             [:.domain-name] (html/content (:domain item))
                                             [:.domain-blog-title] (html/content
                                                                       (get domain-to-blog (:id item)))
                                             [:select.domain-change] (html/set-attr :name
                                                                         (str "change-domain-" (:id item)))
                                             [:option.domain-change] (html/clone-for [bitem (:blogs ctx)]
                                                                         [:*] (html/content (:title bitem))
                                                                         [:*] (html/set-attr :value (:id bitem))
                                                                         [(html/attr= :value (:blogid item))]
                                                                         (html/set-attr :selected
                                                                             "selected")))))

(html/deftemplate images "smallblog/templates/images.html"
                  [ctx]
                  [:#menu] (html/content (user-menu ctx))
                  [:div.image] (html/clone-for [item (:images ctx)]
                                   [:.imglink] (html/set-attr :href
                                                   (str *image-url* "/" (:id item) "/" *image-full*))
                                   [:.imgdisp] (html/set-attr :src
                                                   (str *image-url* "/" (:id item) "/" *image-blog*)))
                  [:option.blog] (html/clone-for [item (:blogs ctx)]
                                                 [:.blog] (html/set-attr :value (:id item))
                                                 [:.blog] (html/content (:title item))))

(html/deftemplate signup "smallblog/templates/signup.html"
                  [ctx]
                  [:#menu] (html/content (user-menu ctx)))
