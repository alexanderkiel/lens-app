(ns lens.navbar
  (:require-macros [lens.macros :refer [h]])
  (:require [om.core :as om]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]
            [lens.event-bus :as bus]
            [lens.util :as util]
            [lens.fa :as fa]))

;; ---- Nav -------------------------------------------------------------------

(defcomponent undo-nav-item [_ owner]
  (will-mount [_]
    (bus/listen-on owner :undo-enabled #(om/set-state! owner :enabled %)))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (render-state [_ {:keys [enabled]}]
    (d/li (when-not enabled {:class "disabled"})
      (d/a {:href "#" :title "Undo"
            :on-click (h (bus/publish! owner :undo {}))} (fa/span :undo)))))

(defcomponent nav-item [item owner]
  (render [_]
    (d/li (when (:active item) {:class "active"})
      (d/a {:href "#" :on-click (h ((:handler item) owner))} (:name item)))))

(defcomponent nav [nav]
  (render [_]
    (d/ul {:class "nav navbar-nav"}
      (om/build undo-nav-item (:undo-nav-item nav))
      (om/build-all nav-item (:items nav)))))

;; ---- Sign In/Out -----------------------------------------------------------

(defn on-cancel [owner]
  (om/update-state! owner #(assoc % :username "" :password "" :expanded false)))

(defn- username-input [username owner]
  (d/input {:type "text" :class "form-control" :ref "username"
            :placeholder "Username"
            :value username
            :on-change #(om/set-state! owner :username (util/target-value %))}))

(defn- password-input [password owner]
  (d/input {:type "password" :class "form-control"
            :placeholder "Password"
            :value password
            :on-change
            #(om/set-state! owner :password (util/target-value %))}))

(defn- submit-button [username password owner]
  (d/button {:class "btn btn-primary" :type "submit"
             :on-click (h (bus/publish! owner :sign-in {:username username
                                                        :password password}))}
    "Go"))

(defn- cancel-button [owner]
  (d/button {:class "btn btn-default" :type "button"
             :on-click (h (on-cancel owner))}
    "Cancel"))

(defn sign-in-button [owner]
  (d/button {:class "btn btn-default navbar-btn navbar-right"
             :on-click (h (om/set-state! owner :expanded true))
             :type "button"} "Sign In"))

(defcomponent sign-in-form [_ owner]
  (init-state [_]
      {:username ""
       :password ""
       :expanded false})
  (will-mount [_]
    (bus/listen-on owner :signed-in
      (om/update-state! owner #(assoc % :username "" :password ""))))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (did-update [_ _ prev-state]
    (when (and (not (:expanded prev-state)) (om/get-state owner :expanded))
      (.focus (om/get-node owner "username"))))
  (render-state [_ {:keys [username password expanded]}]
    (if expanded
      (d/form {:class "navbar-form navbar-right"}
        (d/div {:class "form-group"}
          (username-input username owner)
          (password-input password owner))
        (submit-button username password owner)
        (cancel-button owner))
      (sign-in-button owner))))

(defn- sign-out-button [owner]
  (d/span {:class "fa fa-sign-out" :role "button" :title "Sign Out"
           :on-click (h (bus/publish! owner :sign-out {}))}))

(defcomponent sign-in-out [sign-in-out owner]
  (will-mount [_]
    (bus/listen-on owner :signed-in #(om/update! sign-in-out %))
    (bus/listen-on owner :signed-out #(om/update! sign-in-out {})))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (render [_]
    (if (:username sign-in-out)
      (d/p {:class "navbar-text navbar-right"}
        (:username sign-in-out) " " (sign-out-button owner))
      (om/build sign-in-form (:form sign-in-out)))))

;; ---- Navbar ----------------------------------------------------------------

(defcomponent navbar [navbar owner]
  (render [_]
    (d/div {:class "navbar navbar-default navbar-fixed-top"
            :role "navigation"}
      (d/div {:class "container-fluid"}
        (d/div {:class "navbar-header"}
          (d/a {:class "navbar-brand" :href "#"
                :title "Home"
                :on-click (h (bus/publish! owner :route {:handler :index}))}
            "Lens"))
        (om/build nav (:nav navbar))
        (om/build sign-in-out (:sign-in-out navbar))))))
