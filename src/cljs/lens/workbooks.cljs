(ns lens.workbooks
  (:require-macros [lens.macros :refer [h]])
  (:require [clojure.string :as str]
            [plumbing.core :refer [assoc-when]]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]
            [lens.fa :as fa]
            [lens.event-bus :as bus]
            [lens.util :as util]))

(defn workbook-target [wb]
  {:handler :workbook :params {:id (:id wb)}})

(defcomponent workbook [wb owner]
  (render [_]
    (d/div {:class "col-sm-4"}
      (d/div {:class "workbook-thumb" :role "button"
              :on-click (h (bus/publish! owner :route (workbook-target wb)))}
        (:name wb)))))

(defn publish-create! [dialog owner]
  (let [name (.-value (om/get-node owner "name"))]
    (bus/publish! owner :post (assoc (:create-form dialog)
                                :params {:name name}
                                :result-topic :private-workbooks/create))))

(defcomponent create-dialog [dialog owner]
  (will-mount [_]
    (println "will-mount")
    (bus/listen-on owner :private-workbooks/create
      #(om/update! dialog :visible false)))
  (render [_]
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
                           :on-click #(publish-create! dialog owner)}
                          (assoc-when :disabled (when-not (:create-form dialog)
                                                  "disabled")))
                      "Create")))))))

(defcomponent creator [creator]
  (render [_]
    (d/div {:class "col-sm-4"}
      (d/div {:class "workbook-thumb workbook-thumb-create"}
        (d/span {:class "fa fa-plus-circle"
                 :role "button"
                 :on-click #(om/update! creator [:dialog :visible] true)}))
      (om/build create-dialog (:dialog creator)))))

(defcomponent private-workbooks [workbooks]
  (render [_]
    (d/div
      (d/div {:class "row"}
        (d/div {:class "col-md-12"}
          (d/h4 {:class "text-uppercase text-muted"}
                (fa/span :user) " My Workbooks")))
      (d/div {:class "row"}
        (apply d/div (om/build-all workbook (:list workbooks)))
        (om/build creator (:creator workbooks))))))

(defn build-workbooks-state [doc]
  {:list (:lens/workbooks (:embedded doc))
   :creator
   {:dialog
    {:create-form (select-keys (:lens/create (:forms doc)) [:action])
     :visible false}}})

(defn build-initial-workbooks-state! [wbs owner key]
  (bus/listen-on owner key #(om/update! wbs key (build-workbooks-state %))))

(defn add-created-workbook! [wbs owner key]
  (bus/listen-on owner (keyword (name key) "create")
    (fn [doc]
      (om/transact! wbs [key :list]
                    #(util/insert-by (comp str/lower-case :name) % doc)))))

(defn load-workbooks! [owner key]
  (bus/publish! owner :load {:link-rel (util/prepend-ns "lens" key)
                             :loaded-topic key}))

(defn on-signed-in [owner]
  (load-workbooks! owner :private-workbooks))

(defn on-sign-out [wbs]
  (om/transact! wbs #(dissoc % :private-workbooks)))

(defcomponent workbooks [wbs owner]
  (will-mount [_]
    (build-initial-workbooks-state! wbs owner :private-workbooks)
    (add-created-workbook! wbs owner :private-workbooks)
    (bus/listen-on owner :signed-in #(on-signed-in owner))
    (bus/listen-on owner :sign-out #(on-sign-out wbs)))
  (render [_]
    (when (:active wbs) (util/set-title! "Lens"))
    (d/div {:class "container-fluid"
            :style {:display (if (:active wbs)  "block" "none")}}
      (when-let [ws (:private-workbooks wbs)]
        (om/build private-workbooks ws)))))
