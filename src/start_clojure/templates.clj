(ns start-clojure.templates
	(:require		[net.cgrand.enlive-html :as html]))

(html/defsnippet post-snippet "start_clojure/templates/main.html" [:div.post]
	[{:keys [title content]}]
	[:.title] (html/content title)
	[:.body] (html/content content))

(html/deftemplate main "start_clojure/templates/main.html"
	[ctx]
	[:p#blogname] (html/content (:blogname ctx))
	[:head :title] (html/content (:blogname ctx))
	[:div.posts] (html/clone-for [item (:posts ctx)]
				   (html/content (post-snippet item))))
