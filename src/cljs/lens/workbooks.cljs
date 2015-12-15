(ns lens.workbooks
  (:require-macros [lens.macros :refer [h]])
  (:require [clojure.string :as str]
            [plumbing.core :refer [assoc-when]]
            [om.core :as om]
            [om-tools.core :refer-macros [defcomponent defcomponentk]]
            [om-tools.dom :as d :include-macros true]
            [lens.workbooks.create-dialog :refer [create-dialog]]
            [lens.fa :as fa]
            [lens.event-bus :as bus]
            [lens.util :as util]))

(defn workbook-target [id]
  {:handler :workbook
   :params {:id id}})

(defcomponentk workbook [[:data id name] owner]
  (render [_]
    (d/div {:class "col-sm-4"}
      (d/div {:class "workbook-thumb" :role "button"
              :on-click (h (bus/publish! owner :route (workbook-target id)))}
        name))))

(defcomponent creator [creator]
  (render [_]
    (d/div {:class "col-sm-4"}
      (d/div {:class "workbook-thumb workbook-thumb-create"}
        (d/span {:class "fa fa-plus-circle"
                 :role "button"
                 :on-click (h (om/update! creator [:dialog :visible] true))}))
      (om/build create-dialog (:dialog creator)))))

(defcomponent private-workbooks [workbooks]
  (render [_]
    (d/div
      (d/div {:class "row"}
        (d/div {:class "col-md-12"}
          (d/h4 {:class "text-uppercase text-muted"}
                (fa/span :user) " My Workbooks")))
      (d/div {:class "row"}
        (d/div (om/build-all workbook (:list workbooks)))
        (om/build creator (:creator workbooks))))))

(defn build-workbooks-state [doc]
  {:list (mapv :data (:lens/workbooks (:embedded doc)))
   :creator
   {:dialog
    {:create-form (select-keys (:lens/create (:forms doc)) [:href])
     :form {}
     :visible false}}})

(defn build-initial-workbooks-state! [wbs owner key]
  (bus/listen-on owner key #(om/update! wbs key (build-workbooks-state %))))

(defn add-created-workbook! [wbs owner key]
  (bus/listen-on owner (keyword (name key) "created")
    (fn [doc]
      (om/transact! wbs [key :list]
                    #(util/insert-by (comp str/lower-case :name) % (:data doc))))))

(defn load-workbooks! [owner key]
  (bus/publish! owner :load {:link-rel (util/prepend-ns "lens" key)
                             :loaded-topic key}))

(defn- on-signed-in [owner]
  (load-workbooks! owner :private-workbooks))

(defn- on-sign-out [wbs]
  (om/transact! wbs #(dissoc % :private-workbooks)))

(defcomponent private-workbook-list [wbs owner]
  (will-mount [_]
    (build-initial-workbooks-state! wbs owner :private-workbooks)
    (add-created-workbook! wbs owner :private-workbooks)
    (bus/listen-on owner :signed-in #(on-signed-in owner))
    (bus/listen-on owner :sign-out #(on-sign-out wbs)))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (render [_]
    (when (:active wbs) (util/set-title! "Lens"))
    (d/div {:style {:display (if (:active wbs)  "block" "none")}}
      (when-let [ws (:private-workbooks wbs)]
        (om/build private-workbooks ws)))))
