(ns lens.fa
  "Font Awesome Tools

  http://fortawesome.github.io/Font-Awesome/"
  (:require [clojure.string :as str]
            [om-tools.dom :as d :include-macros true]))

(defn join
  "Joins types with dashes.

  Can be used to build composite classes like chevron-left."
  [& types]
  (str/join "-" (map name types)))

(defn span
  "Renders a span with a font awesome icon.

  "
  [& types]
  (d/span {:class (str/join " " (cons "fa" (map #(str "fa-" (name %)) types)))}))
