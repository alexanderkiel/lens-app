(ns lens.workbooks.create-dialog
  (:require-macros [lens.macros :refer [h]])
    (:require [om.core :as om :include-macros true]
              [om-tools.core :refer-macros [defcomponent]]
              [om-tools.dom :as d :include-macros true]
              [lens.event-bus :as bus]
              [lens.util :as util]))

(defn hide! [dialog]
  (om/transact! dialog #(assoc % :visible false :form {})))

(defn publish-create! [dialog owner]
  (bus/publish! owner :post (assoc (:create-form dialog)
                              :params (:form dialog)
                              :result-topic :private-workbooks/created)))

(defn create-button [dialog owner]
  (d/button
    (-> {:type "button" :class "btn btn-primary"
         :on-click (h (publish-create! dialog owner))}
        (util/disable-when (not (:create-form dialog))))
    "Create"))

(defn dismiss-button [dialog]
  (d/button {:type "button" :class "btn btn-default"
             :on-click (h (hide! dialog))} "Dismiss"))

(defn close-button [dialog]
  (d/button {:type "button" :class "close" :on-click (h (hide! dialog))}
            (d/span "\u00D7")))

(defn form [form]
  (d/form
    (d/div {:class "form-group"}
      (d/label {:for "workbooks-create-dialog-name"} "Name")
      (d/input {:type "text" :class "form-control" :ref "name"
                :value (:name form)
                :id "workbooks-create-dialog-name"
                :placeholder "Like \"Project XY\" for example..."
                :on-change #(om/update! form :name (util/target-value %))}))))

(defcomponent create-dialog [dialog owner]
  (will-mount [_]
    (bus/listen-on owner :private-workbooks/created #(hide! dialog)))
  (did-update [_ _ _]
    (.focus (om/get-node owner "name")))
  (render [_]
    (d/div {:class "modal"
            :style {:display (if (:visible dialog) "block" "none")}
            :tab-index -1
            :role "dialog"}
      (d/div {:class "modal-dialog modal-sm"}
        (d/div {:class "modal-content"}
          (d/div {:class "modal-header"}
            (close-button dialog)
            (d/h4 {:class "modal-title"} "Create Workbook"))
          (d/div {:class "modal-body"}
            (form (:form dialog)))
          (d/div {:class "modal-footer"}
            (dismiss-button dialog)
            (create-button dialog owner)))))))
