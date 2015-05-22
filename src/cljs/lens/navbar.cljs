(ns lens.navbar
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :as async :refer [put! chan <! >! alts! pub sub]]
            [goog.dom :as dom]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]
            [cljs-time.format :as tf]
            [cljs-time.coerce :as tc]
            [lens.io :as io]
            [lens.event-bus :as bus]
            [lens.item-dialog :refer [item-dialog]]
            [lens.workbook :refer [workbook]]
            [lens.util :as util]
            [lens.fa :as fa]))

(defn format-german [date-time]
  (tf/unparse (tf/formatter "dd.MM.yyyy HH:mm") date-time))

(defn last-loaded-text [last-loaded]
  (->> (or (some->> last-loaded (format-german)) "<unknown>")
       (str "Data last loaded at ")))

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
                   :on-click #(bus/publish! owner :sign-in state)} "Go")
        (d/button {:class "btn btn-default" :type "button"
                   :on-click #(om/set-state! owner :expanded false)} "Cancel"))

      (d/button {:class "btn btn-default navbar-btn navbar-right"
                 :on-click #(om/set-state! owner :expanded true)
                 :type "button"} "Sign In"))))

(defn- sign-out-button [owner]
  (d/span {:class "fa fa-sign-out" :role "button" :title "Sign Out"
           :on-click #(bus/publish! owner :sign-out {})}))

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
  (will-mount [_]
    (let [ch (chan)]
      (sub (bus/publication owner) :service-document-loaded ch)
      (go
        (when-let [doc (:service-document (<! ch))]
          (when-let [last-loaded (:last-loaded doc)]
            (om/update! navbar :last-loaded (tc/from-date last-loaded)))))))
  (render [_]
    (println navbar)
    (d/div {:class "navbar navbar-default navbar-fixed-top"
            :role "navigation"}
      (d/div {:class "container-fluid"}
        (d/div {:class "navbar-header"}
          (d/a {:class "navbar-brand" :href "#"} "Lens"))
        (om/build nav (:nav navbar))
        (om/build sign-in-out (:sign-in-out navbar))))))
