(ns lens.workbook
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :as async :refer [chan put! <! >! sub unsub]]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]
            [cljsjs.dimple]
            [lens.io :as io]
            [lens.fa :as fa]
            [lens.item-dialog :as item-dialog]
            [lens.terms :as terms]
            [lens.component :as comp]
            [goog.dom :as dom]
            [lens.event-bus :as event-bus]
            [lens.util :as util]
            [clojure.string :as str]))

(defn sub-local [owner topic k]
  (sub (event-bus/publication owner) topic (om/get-state owner k)))

(defn unsub-local [owner topic k]
  (unsub (event-bus/publication owner) topic (om/get-state owner k)))

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
              (d/span (str " " headline)))))))

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
  [term owner {:keys [cell-ch] :as opts}]
  (init-state [_]
    {:hover false
     :dropdown-hover false
     :dropdown-active false})
  (will-mount [_]
    (println :query-grid-cell :will-mount))
  (did-update [_ old _]
    (println :query-grid-cell :did-update (:id old) "->" (:id term)))
  (render-state [_ {:keys [hover dropdown-hover dropdown-active]}]
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
                  :on-click #(put! cell-ch (:id term))}
              "Remove"))))
      (condp = (:type term)
        :form (om/build form term {:opts opts})
        :item-group (om/build item-group term {:opts opts})
        :item (om/build item term {:opts opts})))))

(defcomponent query-grid-col [col owner opts]
  (init-state [_]
    {:cell-ch (chan)})
  (will-mount [_]
    (go-loop []
      (when-let [term-or-id (<! (om/get-state owner :cell-ch))]
        (if (map? term-or-id)
          (om/transact! col :rows #(conj % term-or-id))
          (om/transact! col :rows #(filterv (comp (partial not= term-or-id) :id) %)))
        (recur))))
  (did-update [_ old _]
    (println :query-grid-col :did-update
             (count (:rows old)) "->" (count (:rows col))))
  (render-state [_ {:keys [cell-ch]}]
    (d/div {:class "col-md-4"}
      (apply d/div (om/build-all query-grid-cell (:rows col)
                                 {:opts (assoc opts :cell-ch cell-ch)}))
      (d/div
        (d/div {:class "query-cell query-cell-empty"}
          (d/span {:class "fa fa-plus-circle"
                   :style {:cursor "pointer"}
                   :on-click #(item-dialog/show! owner cell-ch)}))))))

(defcomponent query-grid [query-grid _ {:keys [collapsed] :as opts}]
  (render [_]
    (apply d/div {:class "row" :style {:display (if collapsed "none" "block")}}
           (om/build-all query-grid-col (:cols query-grid) {:opts opts}))))

;; ---- Result ----------------------------------------------------------------

(defn execute-query-loop [query-form query-update-ch result]
  (go-loop []
    (when-let [{:keys [query-expr]} (<! query-update-ch)]
      (execute-query query-form query-expr result)
      (recur))))

(defn result-chart-id [query-id]
  (str (name query-id) "-result-chart"))

(defcomponent result
  "The result component subscribes to the :query-updated topic and executes a
  query whenever something is published."
  [result owner {:keys [query-id query-form collapsed]}]
  (init-state [_]
    {:query-update-ch (chan)})
  (will-mount [_]
    (sub-local owner [query-id :query-updated] :query-update-ch)
    (execute-query-loop query-form (om/get-state owner :query-update-ch) result))
  (will-unmount [_]
    (unsub-local owner [query-id :query-updated] :query-update-ch))
  (did-update [_ _ _]
    (println :result :did-update)
    (when-let [result (:result result)]
      (clear-chart (result-chart-id query-id))
      (draw-query-result (result-chart-id query-id) result)))
  (render [_]
    (d/div {:class "row" :style {:display (if collapsed "none" "block")}}
      (d/div {:class "col-md-12"}
        (d/div {:class "result"}
          (d/p {:class "text-uppercase"} "Result")
          (if (:result result)
            (d/div {:id (result-chart-id query-id) :style {:height "400px"}})
            (d/div {:class " text-muted text-center"}
              "Please add items to the query grid.")))))))

;; ---- Query -----------------------------------------------------------------

(defn build-query-atom [{:keys [type id]}]
  [type id])

(defn build-query-expr [query]
  {:items
   (->> (:cols (:query-grid query))
        (map (fn [col]
               (->> (remove nil? (:rows col))
                    (map build-query-atom)
                    (seq))))
        (filter seq))})

(defcomponent query [{:keys [id collapsed] :as query} owner opts]
  (did-update [_ prev-props _]
    (println :query :did-update)
    (let [old-query-expr (build-query-expr prev-props)
          new-query-expr (build-query-expr query)]
      (when (not= old-query-expr new-query-expr)
        (put! (event-bus/publisher owner) {:topic [id :query-updated]
                                           :query-expr new-query-expr}))))
  (render [_]
    (apply d/div {:class "container-fluid query"}
           (om/build headline (:headline query) {:opts {:collapsed collapsed}})
           (om/build query-grid (:query-grid query)
                     {:opts (assoc opts :collapsed collapsed)})
           (om/build-all result (:result-list query)
                         {:opts (assoc opts :query-id id :collapsed collapsed)}))))

;; ---- Workbook --------------------------------------------------------------

(defcomponent workbook [workbook]
  (render [_]
    (apply d/div (om/build-all query (:queries workbook)
                               {:opts (select-keys workbook [:query-form])}))))
