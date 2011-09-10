(ns smallblog.templates
	(:use			[ring.util.codec :only (url-encode)])
	(:require		[net.cgrand.enlive-html :as html]
					[clj-time.core :as clj-time]
					[clj-time.format :as clj-time-format]
					[clj-time.coerce :as clj-time-coerce])
	(:import		[org.mozilla.javascript Context ScriptableObject]))

(def *https-port* 4330)
(def *server* "localhost")

; when changing these, also check snippets.html and core/security-config
(def *login-url* "/login")
(def *login-fqurl* (str "https://" *server* ":" *https-port* *login-url*))
(def *signup-url* "/signup")
(def *signup-fqurl* (str "https://" *server* ":" *https-port* *signup-url*))
(def *account-url* "/account")
(def *account-fqurl* (str "https://" *server* ":" *https-port* *account-url*))
(def *login-redirect-url* "/login-redirect")
(def *login-redirect-fqurl*
	(str "https://localhost:" *https-port* *login-redirect-url*))
(def *logout-url* "/logout")
(def *permission-denied-uri* "/permission-denied")


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
	[:#accountlink] (html/set-attr :href *account-fqurl*))

(html/defsnippet no-user-menu
	"smallblog/templates/snippets.html"
	[:#no-user-menu]
	[ctx]
	[:#signuplink] (html/set-attr :href *signup-fqurl*)
	[:#loginlink] (html/set-attr :href
		(if (nil? (:url ctx))
			*login-fqurl*
			(str *login-fqurl* "?url=" (url-encode (:url ctx))))))

(defn user-menu [ctx]
	(try
		(if (nil? (:user ctx))
			(no-user-menu ctx)
			(valid-user-menu ctx))
		(catch Exception e
	  		(println "caught it" e)
			(.printStackTrace e)
	  		(println "foo"))))

(html/defsnippet new-post-button-snippet
	"smallblog/templates/snippets.html"
	[:#newpost]
	[ctx])

(defn new-post-button [ctx]
	(if (:is-blog-owner ctx)
		(html/content (new-post-button-snippet ctx))
		nil))

(html/deftemplate main "smallblog/templates/main.html"
	[ctx]
	[:p#blogname] (html/content (:blogname ctx))
	[:head :title] (html/content (:blogname ctx))
	[:#menu] (html/content (user-menu ctx))
	[:#newpost] (new-post-button ctx)
	[:div.post] (html/clone-for [item (:posts ctx)]
			[:.posttitle] (html/content (:title item))
			[:.postdate] (html/content (clj-time-format/unparse date-output-format
					(clj-time-coerce/from-date (:created_date item))))
			[:.postbody] (html/html-content (:converted_content item))))

(html/deftemplate newpost "smallblog/templates/newpost.html"
	[ctx]
	[:p#blogname] (html/content (:blogname ctx))
	[:head :title] (html/content (:blogname ctx))
	[:#menu] (html/content (user-menu ctx)))

(html/deftemplate login "smallblog/templates/login.html"
	[ctx]
	[:#login_form] (html/set-attr :action
		(if (nil? (:url ctx))
			*login-redirect-fqurl*
			(str *login-redirect-fqurl* "?url=" (url-encode(:url ctx))))))

(html/deftemplate account "smallblog/templates/account.html"
	[ctx]
	[:#email] (html/set-attr :value (:name (:user ctx)))
	[:#menu] (html/content (user-menu ctx))
	[:div.blog] (html/clone-for [item (:blogs ctx)]
			[:.blogtitle] (html/content (:title item))))

(html/deftemplate signup "smallblog/templates/signup.html"
	[ctx]
	[:#menu] (html/content (user-menu ctx)))
