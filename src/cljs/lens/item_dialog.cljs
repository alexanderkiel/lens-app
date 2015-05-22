(ns lens.item-dialog
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :as async :refer [chan put! <! >!]]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]
            [lens.io :as io]
            [lens.fa :as fa]
            [lens.terms :as terms]))

(defn- get-dialog-channel [owner]
  (om/get-shared owner :item-dialog-ch))

(defn show! [owner item-target-ch]
  (put! (get-dialog-channel owner) [:show item-target-ch]))

(defn close! [owner]
  (put! (get-dialog-channel owner) [:close]))

(defn save! [owner term]
  (put! (get-dialog-channel owner) [:save term]))

(defn remote-term-search
  "Search function for the typeahead-search-field.

  It takes the terms state to be able to obtain the :filter-form and returns
  a channel conveying the result."
  [terms query]
  (let [filter-form (-> terms :list :filter-form)
        result-ch (chan)]
    (io/form
     {:url (:action filter-form)
      :method (:method filter-form)
      :data {(-> filter-form :params ffirst) query}
      :on-complete #(put! result-ch %)})
    result-ch))

(defcomponent body [terms]
  (render [_]
    (d/div {:class "modal-body" :style {:max-height "60vh" :overflow-y "auto"}}
      (om/build terms/terms terms))))

(defcomponent item-dialog [item-dialog owner]
  (will-mount [_]
    (go-loop []
      (when-let [[action x] (<! (get-dialog-channel owner))]
        (condp = action
          :show
          (do
            (om/set-state! owner :item-ch x)
            (om/update! item-dialog :display "block"))
          :close
          (om/update! item-dialog :display "none")
          :save
          (do
            (>! (om/get-state owner :item-ch) x)
            (om/update! item-dialog :display "none")))
        (recur))))
  (will-unmount [_]
    (async/close! (get-dialog-channel owner)))
  (render [_]
    (d/div {:id "item-dialog"
            :class "modal"
            :style {:display (:display item-dialog)}
            :tab-index -1
            :role "dialog"}
      (d/div {:class "modal-dialog"}
        (d/div {:class "modal-content"}
          (d/div {:class "modal-header"}
            (d/button {:type "button" :class "close" :on-click #(close! owner)}
                      (d/span "\u00D7"))
            (d/h4 {:class "modal-title"} "Choose Item"))
          (om/build body (:terms item-dialog))
          (d/div {:class "modal-footer"}
            (d/button {:type "button" :class "btn btn-default"
                       :on-click #(close! owner)} "Close")
            (d/button {:type "button" :class "btn btn-primary"
                       :on-click #(->> (:terms item-dialog)
                                       (terms/find-active-term)
                                       (deref)
                                       (save! owner))} "Choose")))))))
