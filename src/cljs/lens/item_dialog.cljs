(ns lens.item-dialog
  (:require-macros [cljs.core.async.macros :refer [go-loop]]
                   [lens.macros :refer [h]])
  (:require [cljs.core.async :refer [chan put! <! >!]]
            [om.core :as om]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]
            [lens.terms :as terms]
            [lens.event-bus :as bus]))

(defn show! [owner target-topic]
  (bus/publish! owner ::show target-topic))

(defn close! [owner]
  (bus/publish! owner ::close {}))

(defn save! [owner term]
  (bus/publish! owner ::save term))

(defcomponent body [terms]
  (render [_]
    (d/div {:class "modal-body" :style {:max-height "60vh" :overflow-y "auto"}}
      (om/build terms/terms terms))))

(defcomponent item-dialog [item-dialog owner]
  (will-mount [_]
    (bus/listen-on owner ::show
      (fn [target-topic]
        (om/set-state! owner :target-topic target-topic)
        (om/update! item-dialog :display "block")))
    (bus/listen-on owner ::close
      (fn [_]
        (om/update! item-dialog :display "none")))
    (bus/listen-on owner ::save
      (fn [term]
        (bus/publish! owner (om/get-state owner :target-topic) term)
        (om/update! item-dialog :display "none"))))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (render [_]
    (d/div {:id "item-dialog"
            :class "modal"
            :style {:display (:display item-dialog)}
            :tab-index -1
            :role "dialog"}
      (d/div {:class "modal-dialog"}
        (d/div {:class "modal-content"}
          (d/div {:class "modal-header"}
            (d/button {:type "button" :class "close"
                       :on-click (h (close! owner))} (d/span "\u00D7"))
            (d/h4 {:class "modal-title"} "Choose Item"))
          (om/build body (:terms item-dialog))
          (d/div {:class "modal-footer"}
            (d/button {:type "button" :class "btn btn-default"
                       :on-click (h (close! owner))} "Close")
            (d/button {:type "button" :class "btn btn-primary"
                       :on-click (h (->> (:terms item-dialog)
                                         (terms/find-active-term)
                                         (deref)
                                         (terms/clean-term)
                                         (save! owner)))} "Choose")))))))
