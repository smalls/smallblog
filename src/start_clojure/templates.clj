(ns start-clojure.templates
	(:require		[net.cgrand.enlive-html :as html]
					[clj-time.core :as clj-time]
					[clj-time.format :as clj-time-format]
					[clj-time.coerce :as clj-time-coerce])
	(:import (org.mozilla.javascript Context ScriptableObject)))

(def date-output-format (clj-time-format/formatter "dd MMM yyyy HH:mm"))

(defn markdownify [post]
	(let [cx (Context/enter)
			scope (.initStandardObjects cx)
			input (Context/javaToJS post scope)
			script (str
					(html/get-resource "start_clojure/Markdown.Converter.js" slurp)
					"window = {Markdown: {Converter: Markdown.Converter}};"
					(html/get-resource "start_clojure/Markdown.Sanitizer.js" slurp)
					"san = window.Markdown.getSanitizingConverter;"
					"san().makeHtml(input);")]
		(try
			(ScriptableObject/putProperty scope "input" input)
			(let [result (.evaluateString cx scope script "<cmd>" 1 nil)]
				(Context/toString result))
		(finally (Context/exit)))))

(html/deftemplate main "start_clojure/templates/main.html"
	[ctx]
	[:p#blogname] (html/content (:blogname ctx))
	[:head :title] (html/content (:blogname ctx))
	[:div.post] (html/clone-for [item (:posts ctx)]
			[:.posttitle] (html/content (:title item))
			[:.postdate] (html/content (clj-time-format/unparse date-output-format
					(clj-time-coerce/from-date (:created_date item))))
			[:.postbody] (html/html-content (markdownify (:content item)))))

(html/deftemplate newpost "start_clojure/templates/newpost.html"
	[ctx]
	[:p#blogname] (html/content (:blogname ctx))
	[:head :title] (html/content (:blogname ctx)))
