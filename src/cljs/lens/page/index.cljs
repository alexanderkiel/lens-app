(ns lens.page.index
  (:require [om.core :as om]
            [om-tools.core :refer-macros [defcomponent defcomponentk]]
            [om-tools.dom :as d :include-macros true]))

(defcomponentk index []
  (render [_]
    (println 'render-page-index)
    (d/p "Index Seite")))
