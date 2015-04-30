(ns lens.navbar
  (:require-macros [lens.macros :refer [h]])
  (:require [om.core :as om]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]
            [lens.event-bus :as bus]
            [lens.util :as util]))

;; ---- Nav -------------------------------------------------------------------

(defcomponent nav-item [item]
  (render [_]
    (d/li (when (:active item) {:class "active"})
      (d/a {:role "button"} (:name item)))))

(defcomponent nav [nav]
  (render [_]
    (apply d/ul {:class "nav navbar-nav"}
           (om/build-all nav-item (:items nav)))))

;; ---- Sign In/Out -----------------------------------------------------------

(defcomponent sign-in-form [form owner]
  (init-state [_]
      {:username ""
       :password ""
       :expanded false})
  (will-mount [_]
    (bus/listen-on owner :signed-in
      #(do
        (om/set-state! owner :username "")
        (om/set-state! owner :password ""))))
  (render-state [_ {:keys [username password expanded] :as state}]
    (if expanded
      (d/form {:class "navbar-form navbar-right"}
        (d/div {:class "form-group"}
          (d/input {:type "text" :class "form-control"
                    :placeholder "Username"
                    :value username
                    :on-change
                    #(om/set-state! owner :username (util/target-value %))})
          (d/input {:type "password" :class "form-control"
                    :placeholder "Password"
                    :value password
                    :on-change
                    #(om/set-state! owner :password (util/target-value %))}))
        (d/button {:class "btn btn-primary" :type "button"
                   :on-click (h (bus/publish! owner :sign-in state))} "Go")
        (d/button {:class "btn btn-default" :type "button"
                   :on-click (h (om/set-state! owner :expanded false))} "Cancel"))

      (d/button {:class "btn btn-default navbar-btn navbar-right"
                 :on-click (h (om/set-state! owner :expanded true))
                 :type "button"} "Sign In"))))

(defn- sign-out-button [owner]
  (d/span {:class "fa fa-sign-out" :role "button" :title "Sign Out"
           :on-click (h (bus/publish! owner :sign-out {}))}))

(defcomponent sign-in-out [sign-in-out owner]
  (will-mount [_]
    (bus/listen-on owner :signed-in #(om/update! sign-in-out %))
    (bus/listen-on owner :signed-out #(om/update! sign-in-out {})))
  (render [_]
    (if (:username sign-in-out)
      (d/p {:class "navbar-text navbar-right"}
        (:username sign-in-out) " " (sign-out-button owner))
      (om/build sign-in-form (:form sign-in-out)))))

;; ---- Navbar ----------------------------------------------------------------

(defcomponent navbar [navbar owner]
  (render [_]
    (println navbar)
    (d/div {:class "navbar navbar-default navbar-fixed-top"
            :role "navigation"}
      (d/div {:class "container-fluid"}
        (d/div {:class "navbar-header"}
          (d/a {:class "navbar-brand" :href "#"
                :on-click (h (bus/publish! owner :route {:handler :index}))}
            "Lens"))
        (om/build nav (:nav navbar))
        (om/build sign-in-out (:sign-in-out navbar))))))
