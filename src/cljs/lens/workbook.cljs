(ns lens.workbook
  (:require-macros [plumbing.core :refer [fnk]]
                   [lens.macros :refer [h]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]
            [cljsjs.dimple]
            [lens.io :as io]
            [lens.fa :as fa]
            [lens.item-dialog :as item-dialog]
            [goog.dom :as dom]
            [lens.util :as util]
            [lens.event-bus :as bus]
            [lens.alert :as alert :refer [alert!]]))

;; ---- Visit Count By Study Event Query --------------------------------------

(defn clear-chart [id]
  (let [n (dom/getElement id)]
    (while (.hasChildNodes n)
      (.removeChild n (.-lastChild n)))))

(defn visit-count-by-study-event [result]
  (->> (:visit-count-by-study-event result)
       (map (fn [[study-event count]]
              {"Study Event" study-event "Visits" count}))))

(defn draw-query-result [id result]
  (let [data (visit-count-by-study-event result)
        svg (.newSvg js/dimple (str "#" id) "100%" "100%")
        chart (new js/dimple.chart svg (clj->js data))]
    (.setMargins chart 50 10 50 40)
    (.addOrderRule (.addCategoryAxis chart "x" "Study Event") "Study Event")
    (.addMeasureAxis chart "y" "Visits")
    (.addSeries chart nil (.-bar (.-plot js/dimple)))
    (.draw chart)))

(defn execute-query
  "Executes the query expr using the form and updating :result in target."
  [form expr target]
  {:pre [(:action form)]}
  (io/post-form
    {:url (:action form)
     :data {:expr expr}
     :on-complete
     #(om/update! target :result
                  (select-keys % [:visit-count-by-study-event])
                  :query)}))

;; ---- Headline --------------------------------------------------------------

(defcomponent headline [headline _ {:keys [collapsed]}]
  (render [_]
    (d/div {:class "row"}
      (d/div {:class "col-md-12"}
        (d/h4 {:class "text-uppercase text-muted"}
              (fa/span (if collapsed :chevron-right :chevron-down))
              (str " " headline))))))

;; ---- Query Grid ------------------------------------------------------------

(defn single-form-expr [{:keys [id]}]
  {:items [[[:form id]]]})

(defcomponent form [{:keys [id] :as form} owner {:keys [query-form]}]
  (will-mount [_]
    (println :form :will-mount)
    (when query-form
      (execute-query query-form (single-form-expr form) form)))
  (did-update [_ _ _]
    (println :form :did-update)
    (when-let [result (:result form)]
      (clear-chart id)
      (draw-query-result id result)))
  (render [_]
    (d/div
      (d/div
        (if-let [alias (:alias form)]
          (str alias " (" id ")")
          id))
      (d/p {:class "text-muted"}
        (:name form))
      (d/div {:id id :style {:height "200px"}}))))

(defn single-item-group-expr [{:keys [id]}]
  {:items [[[:item-group id]]]})

(defcomponent item-group [{:keys [id] :as item-group} owner {:keys [query-form]}]
  (will-mount [_]
    (when query-form
      (execute-query query-form (single-item-group-expr item-group) item-group)))
  (will-update [_ _ _]
    (when (and query-form (not= id (om/get-props owner :id)))
      (execute-query query-form (single-item-group-expr item-group) item-group)))
  (did-update [_ _ _]
    (when-let [result (:result item-group)]
      (clear-chart (str "IG" id))
      (draw-query-result (str "IG" id) result)))
  (render [_]
    (d/div
      (d/div (util/add-soft-hyphen (:name item-group)))
      (d/p {:class "text-muted"}
        (str "Form: " (:name (:parent item-group))))
      (d/div {:id (str "IG" id)}))))

(defcomponent item [{:keys [id] :as item} owner {:keys [query-form]}]
  (will-mount [_]
    )
  (will-update [_ _ _]
    )
  (did-update [_ _ _]
    )
  (render [_]
    (d/div
      (d/div
        (if-let [alias (:name item)]
          (str alias " (" id ")")
          id))
      (d/p {:class "text-muted"}
        (:question item))
      (d/div {:id id}))))

(defn query-grid-cell-mouse-leave [state]
  (assoc state :hover false :dropdown-hover false :dropdown-active false))

(defcomponent query-grid-cell
  "A cell in a query grid."
  [term owner {:keys [query-idx col-idx] :as opts}]
  (init-state [_]
    {:hover false
     :dropdown-hover false
     :dropdown-active false})
  (will-mount [_]
    (println "will-mount query-grid-cell"))
  (did-update [_ old _]
    (println :query-grid-cell :did-update (:id old) "->" (:id term)))
  (render-state [_ {:keys [hover dropdown-hover dropdown-active]}]
    (println "render query-grid-cell" query-idx col-idx)
    (d/div {:class "query-cell"
            :on-mouse-enter #(om/set-state! owner :hover true)
            :on-mouse-leave #(om/update-state! owner query-grid-cell-mouse-leave)}
      (d/div {:class "dropdown pull-right"
              :style {:display (if hover "block" "none")}
              :on-click #(om/update-state! owner :dropdown-active not)
              :on-mouse-enter #(om/set-state! owner :dropdown-hover true)
              :on-mouse-leave #(om/set-state! owner :dropdown-hover false)}
        (d/span (when-not dropdown-hover {:class "text-muted"})
          (fa/span :chevron-down))
        (d/div {:class "tooltip right" :role "tooltip"
                :style {:display (if dropdown-hover "inline-block" "none")}}
          (d/div {:class "tooltip-arrow"})
          (d/div {:class "tooltip-inner"} "Options Menu"))
        (d/ul {:class "dropdown-menu dropdown-menu-right" :role "menu"
               :style {:display (if dropdown-active "block" "none")}}
          (d/li {:role "presentation"}
            (d/a {:role "menuitem" :tab-index "-1" :href "#"
                  :on-click (h (bus/publish! owner [:remove-cell query-idx
                                                    col-idx] (:id term)))}
              "Remove"))))
      (condp = (:type term)
        :form (om/build form term {:opts opts})
        :item-group (om/build item-group term {:opts opts})
        :item (om/build item term {:opts opts})))))

(defn show-item-dialog! [owner query-idx col-idx]
  (item-dialog/show! owner [::add-cell query-idx col-idx]))

(defn cell-adder [owner query-idx col-idx]
  (d/p {:class "text-muted"}
    (d/a {:href "#" :on-click (h (show-item-dialog! owner query-idx col-idx))}
      "Add a cell...")))

(defcomponent query-grid-col [{:keys [idx] :as col} owner
                              {:keys [query-idx] :as opts}]
  (will-mount [_]
    (println "mount col" query-idx idx)
    (bus/listen-on owner [::add-cell query-idx idx]
      (fn [cell]
        (println "add cell" query-idx idx (:id cell))
        (bus/publish! owner ::add-cell [query-idx idx cell])
        (om/transact! col :cells #(conj % cell))))
    (bus/listen-on owner [:remove-cell query-idx idx]
      (fn [id]
        (om/transact! col :cells #(filterv (comp (partial not= id) :id) %)))))
  (will-unmount [_]
    (println "unmount col" query-idx idx)
    (bus/unlisten-all owner))
  (did-update [_ old _]
    (println :query-grid-col :did-update
             (count (:cells old)) "->" (count (:cells col))))
  (render [_]
    (println "render query-grid-col" query-idx idx)
    (d/div {:class "col-md-4"}
      (apply d/div (om/build-all query-grid-cell (:cells col)
                                 {:opts (assoc opts :col-idx idx)}))
      (cell-adder owner query-idx idx))))

(defcomponent query-grid [query-grid _ {:keys [query-idx collapsed] :as opts}]
  (render [_]
    (println "render query-grid" query-idx)
    (apply d/div {:class "row" :style {:display (if collapsed "none" "block")}}
           (om/build-all query-grid-col (:cols query-grid) {:opts opts}))))

;; ---- Result ----------------------------------------------------------------

(defn result-chart-id [query-idx]
  (str "result-chart-" query-idx))

(defcomponent result
  "The result component subscribes to the :query-updated topic and executes a
  query whenever something is published."
  [result owner {:keys [query-idx query-form collapsed]}]
  (will-mount [_]
    (bus/listen-on owner [:query-updated query-idx]
      (fn [query-expr]
        (println :result :execute-query query-expr)
        (execute-query query-form query-expr result))))
  (did-update [_ _ _]
    (println :result :did-update)
    (when-let [result (:result result)]
      (clear-chart (result-chart-id query-idx))
      (draw-query-result (result-chart-id query-idx) result)))
  (render [_]
    (d/div {:class "row" :style {:display (if collapsed "none" "block")}}
      (d/div {:class "col-md-12"}
        (d/div {:class "result"}
          (d/p {:class "text-uppercase"} "Result")
          (if (:result result)
            (d/div {:id (result-chart-id query-idx) :style {:height "400px"}})
            (d/div {:class " text-muted text-center"}
              "Please add items to the query grid.")))))))

;; ---- Query -----------------------------------------------------------------

(defn build-query-atom [{:keys [type id]}]
  [type id])

(defn build-query-expr [query]
  {:items
   (->> (:cols (:query-grid query))
        (map (fn [col]
               (->> (remove nil? (:cells col))
                    (map build-query-atom)
                    (seq))))
        (filter seq))})

(defcomponent query [{:keys [idx collapsed] :as query} owner opts]
  (did-update [_ prev-props _]
    (println :query :did-update)
    (let [old-query-expr (build-query-expr prev-props)
          new-query-expr (build-query-expr query)]
      (when (not= old-query-expr new-query-expr)
        (println :query :publish-new-expr :under idx)
        (bus/publish! owner [:query-updated idx] new-query-expr))))
  (render [_]
    (println "render query" idx)
    (apply d/div
           (om/build headline (or (:name query) (str "Query " (inc idx))) {:opts {:collapsed collapsed}})
           (om/build query-grid (:query-grid query)
                     {:opts (assoc opts :query-idx idx :collapsed collapsed)})
           (om/build-all result (:result-list query)
                         {:opts (assoc opts :query-idx idx :collapsed collapsed)}))))

;; ---- Workbook --------------------------------------------------------------

(defn assoc-idx [idx x]
  (assoc x :idx idx))

(defn index-query [idx query]
  (-> (update-in query [:query-grid :cols] #(vec (map-indexed assoc-idx %)))
      (assoc :idx idx)))

(defn index-queries-and-cols [version]
  (update-in version [:queries] #(vec (map-indexed index-query %))))

(defn on-loaded-version [workbook version]
  (->> (index-queries-and-cols version)
       (om/update! workbook :head)))

(defn add-query-cell-msg [version query-idx idx cell]
  {:action (-> version :forms :lens/add-query-cell :action)
   :params
   {:query-idx query-idx
    :col-idx idx
    :term-type (name (:type cell))
    :term-id (:id cell)}
   :result-topic ::new-version})

(defn add-query-msg [version]
  {:action (-> version :forms :lens/add-query :action)
   :result-topic ::new-version})

(defn query-adder [version owner]
  (d/p {:class "text-uppercase text-muted"}
    (fa/span :chevron-right) " "
    (d/a {:href "#"
          :on-click (h (bus/publish! owner :post (add-query-msg version)))}
      "Add a query...")))

(defcomponent version [version owner]
  (will-mount [_]
    (println "mount version" (:id version))
    (bus/listen-on owner ::add-cell
      (fn [[query-idx idx cell]]
        (bus/publish! owner :post (add-query-cell-msg (om/get-props owner)
                                                      query-idx idx cell)))))
  (will-unmount [_]
    (println "unmount version" (:id version))
    (bus/unlisten-all owner))
  (render [_]
    (d/div {:class "container-fluid"}
      (apply d/div (om/build-all query (:queries version)))
      (query-adder version owner))))

(defn update-workbook-msg [workbook version-id]
  {:action (-> workbook :links :self :href)
   :if-match (:etag workbook)
   :params {:version-id version-id}
   :result-topic ::workbook-updated})

(defn on-new-version
  "Updates the workbooks with the new version created earlier.

  Doing something to the workbook is always a two stage process. First one
  creates a new immutable version carrying the changes and second one updates
  the workbook to point to that new version."
  [owner {:keys [id] :as new-version}]
  (let [workbook (om/get-props owner)]
    (om/transact! workbook :head #(assoc % :links (:links new-version)
                                           :forms (:forms new-version)
                                           :id (:id new-version)))
    (bus/publish! owner :put (update-workbook-msg workbook id))))

(defcomponent workbook [workbook owner]
  (will-mount [_]
    (bus/listen-on owner :loaded-version #(on-loaded-version workbook %))
    (bus/listen-on owner ::new-version #(on-new-version owner %))
    (bus/listen-on owner ::workbook-updated
      (fn [wb] (om/update! workbook :etag ((meta wb) "etag"))))
    (bus/publish! owner :load {:uri (-> workbook :links :lens/head :href)
                               :loaded-topic :loaded-version}))
  (render [_]
    (util/set-title! (str (:name workbook) " - Lens"))
    (when-let [head (:head workbook)] (om/build version head))))
