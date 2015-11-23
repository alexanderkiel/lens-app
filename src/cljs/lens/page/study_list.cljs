(ns lens.page.study-list
  (:require-macros [plumbing.core :refer [fnk]]
                   [lens.macros :refer [h]])
  (:require [om.core :as om]
            [om-tools.core :refer-macros [defcomponent defcomponentk]]
            [om-tools.dom :as d :include-macros true]
            [lens.event-bus :as bus]))

(defn- study-target [id]
  {:handler :study :params {:id id}})

(defcomponentk study [[:data id name {desc nil}] owner]
  (render [_]
    (d/li
      (d/a {:href "#"
            :on-click (h (bus/publish! owner :route (study-target id)))}
        name)
      (when desc (d/div {:class "desc"} desc)))))

(defcomponentk study-list [data owner]
  (will-mount [_]
    (bus/listen-on owner ::loaded
      (fnk [embedded]
        (om/update! data :list (mapv :data (:lens/studies embedded))))))
  (render [_]
    (println 'render-page-study-list)
    (d/div {:class "container-fluid"}
      (d/ul {:class "study-list"}
        (om/build-all study (:list data))))))