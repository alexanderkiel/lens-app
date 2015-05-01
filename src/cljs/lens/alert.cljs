(ns lens.alert
  (:require-macros [plumbing.core :refer [fnk]]
                   [lens.macros :refer [h]])
  (:require [om.core :as om]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]
            [lens.event-bus :as bus]))

(defn alert! [owner level msg]
  (bus/publish! owner ::open {:level level :msg msg}))

(defn internal-error! [owner n]
  (alert! owner :danger (str "Internal Error E" n ". "
                             "Please try to reload Lens.")))

(defn close! [owner]
  (bus/publish! owner ::close {}))

(defcomponent alert [alert owner]
  (will-mount [_]
    (bus/listen-on owner ::open
      (fnk [level msg]
        (om/transact! alert #(assoc % :level level :msg msg))))
    (bus/listen-on owner ::close #(om/update! alert :level nil)))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (render [_]
    (when (:level alert)
      (d/div {:class "container-fluid"}
        (d/div {:class (str "alert alert-" (name (:level alert))
                            " alert-dismissible")
                :role "alert"}
          (d/button {:type "button" :class "close"
                     :on-click (h (close! owner))}
                    (d/span "\u00D7")) (:msg alert))))))
