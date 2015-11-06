(ns lens.alert
  (:require-macros [plumbing.core :refer [fnk]]
                   [lens.macros :refer [h]])
  (:require [om.core :as om]
            [om-tools.core :refer-macros [defcomponent defcomponentk]]
            [om-tools.dom :as d :include-macros true]
            [lens.event-bus :as bus]
            [schema.core :as s :include-macros true]))

(def Level
  "Alert level as available in Bootstrap."
  (s/enum :success :info :warning :danger))

(s/defn alert!
  "Show an alert banner with level and message."
  [owner level :- Level msg]
  (bus/publish! owner ::add {:id (random-uuid) :level level :msg msg}))

(defn internal-error! [owner n]
  (alert! owner :danger (str "Internal Error E" n ". "
                             "Please try to reload Lens.")))

(defn remove! [owner id]
  (bus/publish! owner ::remove id))

(defn remove-all! [owner]
  (bus/publish! owner ::remove-all {}))

(defcomponentk alert [[:data id level msg] owner]
  (render [_]
    (d/div {:class "container-fluid"}
      (d/div {:class (str "alert alert-" (name level) " alert-dismissible")
              :role "alert"}
        (d/button {:type "button" :class "close"
                   :on-click (h (remove! owner id))}
          (d/span "\u00D7"))
        msg))))

(defcomponentk alerts [data owner]
  (will-mount [_]
    (bus/listen-on owner ::add
      (fn [alert]
        (om/transact! data #(conj % alert))))
    (bus/listen-on owner ::remove
      (fn [id]
        (om/transact! data (comp vec (partial remove #(= id (:id %)))))))
    (bus/listen-on owner ::remove-all
      (fn [_]
        (om/update! data []))))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (render [_]
    (d/div (om/build-all alert data))))
