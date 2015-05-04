(ns lens.workbook
  (:require-macros [cljs.core.async.macros :refer [go-loop]]
                   [plumbing.core :refer [fnk]]
                   [lens.macros :refer [h]])
  (:require [cljs.core.async :as async]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]
            [cljsjs.dimple]
            [lens.io :as io]
            [lens.fa :as fa]
            [lens.item-dialog :as item-dialog]
            [goog.dom :as dom]
            [lens.util :as util]
            [lens.event-bus :as bus]
            [lens.alert :refer [alert!]]))

;; Local history of all versions of the current workbook
(defonce version-history (atom []))

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

(defn execute-query!
  "Executes the query expr and publishes the result on result-topic."
  [owner expr result-topic]
  (bus/publish! owner :post {:form-rel :lens/query
                             :params {:expr expr}
                             :result-topic result-topic}))

;; ---- Headline --------------------------------------------------------------

(defn remove-query-msg [idx]
  {:form-rel :lens/remove-query
   :params {:idx idx}
   :state-fn
   (fn [version]
     (update-in version [:queries] #(->> (concat (take idx %) (drop (inc idx) %))
                                         (map-indexed (fn [i q] (assoc q :idx i)))
                                         (vec))))})

(defn query-remove-button [owner idx]
  (d/span {:class "fa fa-minus-circle"
           :role "button"
           :style {:margin-left "10px"}
           :on-click (h (bus/publish! owner ::tx (remove-query-msg idx)))}))

(defcomponent headline [headline owner {:keys [idx collapsed]}]
  (render-state [_ {:keys [hover]}]
    (d/div {:class "row"}
      (d/div {:class "col-md-12"
              :on-mouse-enter #(om/set-state! owner :hover true)
              :on-mouse-leave #(om/set-state! owner :hover false)}
        (d/h4 {:class "text-uppercase text-muted"}
          (d/span {:class (str "fa fa-" (if collapsed "chevron-right"
                                                      "chevron-down"))
                   :style {:margin-right "5px"}} )
          headline
          (when hover
            (query-remove-button owner idx)))))))

;; ---- Query Grid ------------------------------------------------------------

(defn cell-id [query-idx col-idx id]
  (str "Q" query-idx "-C" col-idx "-" id))

(defn single-form-expr [{:keys [id]}]
  {:items [[[:form id]]]})

(defcomponent form [{:keys [id] :as form} owner {:keys [query-idx col-idx]}]
  (will-mount [_]
    (bus/listen-on owner (cell-id query-idx col-idx id)
      #(om/update! form :result (select-keys % [:visit-count-by-study-event])))
    (execute-query! owner (single-form-expr form)
                    (cell-id query-idx col-idx id)))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (did-update [_ _ _]
    (when-let [result (:result form)]
      (clear-chart (cell-id query-idx col-idx id))
      (draw-query-result (cell-id query-idx col-idx id) result)))
  (render [_]
    (d/div
      (d/div
        (if-let [alias (:alias form)]
          (str alias " (" id ")")
          id))
      (d/p {:class "text-muted"}
        (:name form))
      (d/div {:id (cell-id query-idx col-idx id)
              :style {:height "200px"}}))))

(defn single-item-group-expr [{:keys [id]}]
  {:items [[[:item-group id]]]})

(defcomponent item-group [{:keys [id] :as item-group} owner {:keys [query-form]}]
  (will-mount [_]
    (when query-form
      (execute-query! query-form (single-item-group-expr item-group) item-group)))
  (will-update [_ _ _]
    (when (and query-form (not= id (om/get-props owner :id)))
      (execute-query! query-form (single-item-group-expr item-group) item-group)))
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

(defcomponent item [{:keys [id] :as item} _ _]
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
                  :on-click (h (bus/publish! owner [::remove-cell query-idx
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
    (d/a {:href "#" :class "cell-adder"
          :on-click (h (show-item-dialog! owner query-idx col-idx))}
      "Add a cell...")))

(defn col-contains-cell? [owner cell]
  (some #{(:id cell)} (map :id (om/get-props owner :cells))))

(defn duplicate-cell-warning [cell]
  (str "Can't add cell " (:id cell) " because it's already there."))

(defn add-cell [query-idx col-idx cell]
  (fn [version]
    (update-in version [:queries query-idx :query-grid :cols col-idx :cells]
               #(conj % cell))))

(defn on-add-cell
  "Tests if the cell can be added and fires an ::tx event if so."
  [owner query-idx idx cell]
  (if (col-contains-cell? owner cell)
    (alert! owner :warning (duplicate-cell-warning cell))
    (bus/publish! owner ::tx
                  {:form-rel :lens/add-query-cell
                   :params {:query-idx query-idx
                            :col-idx idx
                            :term-type (name (:type cell))
                            :term-id (:id cell)}
                   :state-fn (add-cell query-idx idx cell)})))

(defn remove-cell [query-idx col-idx id]
  (fn [version]
    (update-in version [:queries query-idx :query-grid :cols col-idx :cells]
               #(filterv (comp (partial not= id) :id) %))))

(defn on-remove-cell [owner query-idx idx id]
  (bus/publish! owner ::tx
                {:form-rel :lens/remove-query-cell
                 :params {:query-idx query-idx
                          :col-idx idx
                          :term-id id}
                 :state-fn (remove-cell query-idx idx id)}))

(defcomponent query-grid-col
  "A column of query cells in the query grid.

  This component handles additions and removals of its cells."
  [{:keys [idx] :as col} owner {:keys [query-idx] :as opts}]
  (will-mount [_]
    (bus/listen-on owner [::add-cell query-idx idx]
      #(on-add-cell owner query-idx idx %))
    (bus/listen-on owner [::remove-cell query-idx idx]
      #(on-remove-cell owner query-idx idx %)))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (render [_]
    (d/div {:class "col-md-4"}
      (apply d/div (om/build-all query-grid-cell (:cells col)
                                 {:opts (assoc opts :col-idx idx)}))
      (cell-adder owner query-idx idx))))

(defcomponent query-grid
  "A grid of query cells."
  [query-grid _ {:keys [collapsed] :as opts}]
  (render [_]
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
        (execute-query! query-form query-expr result))))
  (will-unmount [_]
    (bus/unlisten-all owner))
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
    (apply d/div
           (om/build headline (or (:name query) (str "Query " (inc idx)))
                     {:opts {:idx idx :collapsed collapsed}})
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
  (->> (-> (index-queries-and-cols version)
           (assoc :open-txs #queue []))
       (om/update! workbook :head)))

(defn tx-msg [version {:keys [form-rel params]}]
  {:action (-> version :forms form-rel :action)
   :params params
   :result-topic ::new-version})

(defn update-state [version {:keys [state-fn] :as msg}]
  (-> (state-fn version)
      (update-in [:open-txs] #(conj % msg))))

(defn perform-tx! [owner msg]
  (let [version (om/get-props owner)]
    (when (empty? (:open-txs version))
      (bus/publish! owner :post (tx-msg version msg))
      (bus/publish! owner ::out-of-sync true))
    (om/transact! version [] #(update-state % msg) :history)))

(defn empty-query [idx]
  (index-query idx {:query-grid {:cols [{} {} {}]}}))

(defn add-query-msg []
  {:form-rel :lens/add-query
   :state-fn
   (fn [version]
     (update-in version [:queries] #(conj % (empty-query (count %)))))})

(defn query-adder [owner]
  (d/p {:class "text-uppercase text-muted"}
    (fa/span :chevron-right) " "
    (d/a {:href "#" :class "query-adder"
          :on-click (h (bus/publish! owner ::tx (add-query-msg)))}
      "Add a query...")))

(defn out-of-sync-loop
  "Listens for ::out-of-sync events and sets the local :out-of-sync state
  according.

  Waits for 500 ms before it activates the local :out-of-sync state."
  [owner]
  (let [ch (async/chan)]
    (bus/register-for-unlisten owner ::out-of-sync ch)
    (async/sub (bus/publication owner) ::out-of-sync ch)
    (go-loop [ports [ch]]
      (let [[val port] (async/alts! ports)]
        (if (= ch port)
          (when val
            (if (:msg val)
              (recur (if (= 2 (count ports)) ports [ch (async/timeout 500)]))
              (do
                (om/set-state! owner :out-of-sync false)
                (recur [ch]))))
          (do
            (om/set-state! owner :out-of-sync true)
            (recur [ch])))))))

(defcomponent version
  "Component which holds a version of a workbook.

  It consists of two parts, a list of queries and a query adder, both wrapped in
  a container-fluid.

  The component also manages updates of its version. It listens on update events
  from subcomponents and delegates them to the server. All updates lead to a
  ::new-version event which is than handled by its parent component the
  workbook."
  [version owner]
  (will-mount [_]
    (bus/listen-on owner ::tx #(perform-tx! owner %))
    (out-of-sync-loop owner))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (render-state [_ {:keys [out-of-sync]}]
    (d/div {:class "container-fluid"}
      (when out-of-sync
        (d/div {:class "alert alert-warning" :role "alert"} "Out of sync!"))
      (apply d/div (om/build-all query (:queries version)))
      (query-adder owner))))

(defn update-workbook-msg [workbook]
  {:action (-> workbook :links :self :href)
   :if-match (:etag workbook)
   :params {:version-id (:id (:head workbook))}
   :result-topic ::workbook-updated})

(defn on-new-version
  "Updates the workbook with the new version created earlier.

  Doing something to the workbook is always a two stage process. First one
  creates a new immutable version carrying the changes and second one updates
  the workbook to point to that new version."
  [owner {:keys [id] :as new-version}]
  (let [workbook (om/get-props owner)
        head (:head workbook)
        open-txs (:open-txs head)]
    (when (<= (count open-txs) 1)
      (bus/publish! owner ::out-of-sync false))
    (when (< 1 (count open-txs))
      (bus/publish! owner :post (tx-msg new-version (second open-txs))))
    (om/transact! workbook :head
                  #(-> (assoc % :id id
                                :links (:links new-version)
                                :forms (:forms new-version))
                       (update-in [:open-txs] pop)))))

(defn on-workbook-updated [workbook wb]
  (om/update! workbook :etag ((meta wb) "etag")))

(defn load-head-msg
  "Message for :load topic loading the head of the workbook."
  [workbook]
  {:uri (-> workbook :links :lens/head :href) :loaded-topic ::loaded-version})

(defn local-undo [workbook]
  (om/update! workbook :head (peek @version-history))
  (swap! version-history pop))

(defn on-undo [owner]
  (let [workbook (om/get-props owner)]
    (if (seq @version-history)
      (local-undo workbook)
      (when-let [uri (-> workbook :head :links :lens/parent :href)]
        (bus/publish! owner :load {:uri uri :loaded-topic ::loaded-version})))))

(defn update-workbook-on-head-change [owner old-workbook new-workbook]
  (when-let [old-head (:head old-workbook)]
    (when-let [new-head (:head new-workbook)]
      (if (not= (:id new-head) (:id old-head))
        (bus/publish! owner :put (update-workbook-msg new-workbook))))))

(defn update-undo-enabled-state [owner workbook]
  (->> (if (-> workbook :head :links :lens/parent) true false)
       (bus/publish! owner :undo-enabled)))

(defcomponent workbook
  "Component which represents a workbook showing its head through the version
  component.

  Loads the head of the workbook on mounting. A central routing should remount
  the component on each new workbook.

  Listens also on ::new-version which fires every time the version of a workbook
  is advanced. See doc of on-new-version for more information."
  [workbook owner]
  (will-mount [_]
    (reset! version-history [])
    (bus/listen-on owner ::loaded-version #(on-loaded-version workbook %))
    (bus/listen-on owner ::new-version #(on-new-version owner %))
    (bus/listen-on owner ::workbook-updated #(on-workbook-updated workbook %))
    (bus/listen-on owner :undo #(on-undo owner))
    (bus/publish! owner :load (load-head-msg workbook)))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (will-update [_ new-workbook _]
    (let [old-workbook (om/get-props owner)]
      (update-workbook-on-head-change owner old-workbook new-workbook)
      (update-undo-enabled-state owner new-workbook)))
  (render [_]
    (util/set-title! (str (:name workbook) " - Lens"))
    (when-let [head (:head workbook)] (om/build version head))))
