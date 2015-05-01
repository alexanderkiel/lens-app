(ns lens.alert
  (:require-macros [plumbing.core :refer [fnk]]
                   [lens.macros :refer [h]])
  (:require [om.core :as om]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]
            [lens.event-bus :as bus]))

(defn alert! [owner level msg]
  (bus/publish! owner :alert {:level level :msg msg}))

(defn internal-error! [owner n]
  (alert! owner :danger (str "Internal Error E" n ". "
                             "Please try to reload Lens.")))

(defcomponent alert [_ owner]
  (will-mount [_]
    (bus/listen-on owner :alert
      (fnk [level msg]
        (om/update-state! owner #(assoc % :level level :msg msg)))))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (render-state [_ {:keys [level msg]}]
    (when level
      (d/div {:class "container-fluid"}
        (d/div {:class (str "alert alert-" (name level) " alert-dismissible")
                :role "alert"}
          (d/button {:type "button" :class "close"
                     :on-click (h (om/set-state! owner :level nil))}
                    (d/span "\u00D7")) msg)))))
