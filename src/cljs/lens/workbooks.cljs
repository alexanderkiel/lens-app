(ns lens.workbooks
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [plumbing.core :refer [assoc-when]]
            [cljs.core.async :refer [put! sub chan <!]]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]
            [lens.util :refer [set-title!]]
            [lens.fa :as fa]
            [lens.event-bus :as bus]))

(defcomponent workbook [workbook]
  (render [_]
    (d/div {:class "col-sm-4"}
      (d/div {:class "workbook-thumb"}
        (:name workbook)))))

(defn publish-create! [owner]
  (let [name (.-value (om/get-node owner "name"))
        uri (om/get-state owner :create-uri)]
    (bus/publish! owner :post {:action uri :params {:name name}
                               :result-topic ::private-create})))

(defcomponent create-dialog [dialog owner]
  (init-state [_]
    {:create-uri nil})
  (will-mount [_]
    (println "will-mount")
    (bus/listen-on owner ::private-create
      #(om/update! dialog :visible false))
    (bus/listen-on owner ::private-workbooks
      #(do (println (:action (:lens/create (:forms %)))) (om/set-state! owner :create-uri (:action (:lens/create (:forms %)))))))
  (render-state [_ {:keys [create-uri]}]
    (println "render" create-uri)
    (d/div {:class "modal"
            :style {:display (if (:visible dialog) "block" "none")}
            :tab-index -1
            :role "dialog"}
      (d/div {:class "modal-dialog modal-sm"}
        (d/div {:class "modal-content"}
          (d/div {:class "modal-header"}
            (d/button {:type "button" :class "close"
                       :on-click #(om/update! dialog {})} (d/span "\u00D7"))
            (d/h4 {:class "modal-title"} "Create Workbook"))
          (d/div {:class "modal-body"}
            (d/form
              (d/div {:class "form-group"}
                (d/label {:for "workbooks-create-dialog-name"} "Name")
                (d/input {:type "text" :class "form-control" :ref "name"
                          :id "workbooks-create-dialog-name"
                          :placeholder "Like \"Project XY\" for example..."}))))
          (d/div {:class "modal-footer"}
            (d/button {:type "button" :class "btn btn-default"
                       :on-click #(om/update! dialog {})} "Dismiss")
            (d/button (-> {:type "button" :class "btn btn-primary"
                           :on-click #(publish-create! owner)}
                          (assoc-when :disabled (when-not create-uri "disabled")))
                      "Create")))))))

(defcomponent creator [creator]
  (render [_]
    (d/div {:class "col-sm-4"}
      (d/div {:class "workbook-thumb workbook-thumb-create"}
        (d/span {:class "fa fa-plus-circle"
                 :role "button"
                 :on-click #(om/update! creator [:dialog :visible] true)}))
      (om/build create-dialog (:dialog creator)))))

(defcomponent private-workbooks [workbooks owner]
  (will-mount [_]
    (bus/listen-on owner ::private-workbooks
      #(om/update! workbooks :list (:lens/workbooks (:embedded %))))
    (bus/publish! owner :load {:link-rel :lens/private-workbooks
                               :loaded-topic ::private-workbooks}))
  (render [_]
    (d/div
      (d/div {:class "row"}
        (d/div {:class "col-md-12"}
          (d/h4 {:class "text-uppercase text-muted"}
                (fa/span :user) (str " " (:headline workbooks)))))
      (d/div {:class "row"}
        (apply d/div (om/build-all workbook (:list workbooks)))
        (om/build creator (:creator workbooks))))))

(defcomponent workbooks [workbooks]
  (render [_]
    (when (:active workbooks) (set-title! "Workbooks - Lens"))
    (d/div {:class "container-fluid"
            :style {:display (if (:active workbooks)  "block" "none")}}
      (om/build private-workbooks (:private-workbooks workbooks)))))
