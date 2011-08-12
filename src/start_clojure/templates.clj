(ns start-clojure.templates
	(:require		[net.cgrand.enlive-html :as html]
					[clj-time.core :as clj-time]
					[clj-time.format :as clj-time-format]
					[clj-time.coerce :as clj-time-coerce]))

(def date-output-format (clj-time-format/formatter "dd MMM yyyy HH:mm"))

(html/deftemplate main "start_clojure/templates/main.html"
	[ctx]
	[:p#blogname] (html/content (:blogname ctx))
	[:head :title] (html/content (:blogname ctx))
	[:div.post] (html/clone-for [item (:posts ctx)]
			[:.posttitle] (html/content (:title item))
			[:.postdate] (html/content (clj-time-format/unparse date-output-format
					(clj-time-coerce/from-date (:created_date item))))
			[:.postbody] (html/content (:content item))))

(html/deftemplate newpost "start_clojure/templates/newpost.html"
	[ctx]
	[:p#blogname] (html/content (:blogname ctx))
	[:head :title] (html/content (:blogname ctx)))
