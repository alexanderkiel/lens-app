(ns lens.workbook
  (:require-macros [cljs.core.async.macros :refer [go-loop]]
                   [plumbing.core :refer [fnk defnk]]
                   [lens.macros :refer [h]])
  (:require [cljs.core.async :as async]
            [om.core :as om]
            [om-tools.core :refer-macros [defcomponent defcomponentk]]
            [om-tools.dom :as d :include-macros true]
            [cljsjs.dimple]
            [lens.fa :as fa]
            [lens.item-dialog :as item-dialog]
            [goog.dom :as dom]
            [lens.util :as util]
            [lens.event-bus :as bus]
            [lens.alert :refer [alert!]]
            [schema.core :as s :include-macros true]
            [lens.schema :as ls]
            [lens.version :as version]))

;; ---- History ---------------------------------------------------------------

;; Local history of all versions of the current workbook
(defonce version-history (atom []))

(defnk on-history-tx
  "Workbook version advances.

  Save the old state as point in history so that one can go back there."
  [old-state]
  (swap! version-history conj (-> old-state :workbook ::head)))

;; ---- Charts ----------------------------------------------------------------

(defn clear-chart [id]
  (when-let [n (dom/getElement id)]
    (while (.hasChildNodes n)
      (.removeChild n (.-lastChild n)))))

;; ---- Visit Count By Study Event --------------------------------------------

(defn visit-count-by-study-event [result]
  (->> (:visit-count-by-study-event result)
       (map (fn [[study-event count]]
              {"Study Event" study-event "Visits" count}))))

(defn draw-vc-by-se-result [id result ticks]
  (let [data (visit-count-by-study-event result)
        svg (.newSvg js/dimple (str "#" id) "100%" "100%")
        chart (new js/dimple.chart svg (clj->js data))]
    (.setMargins chart 60 10 50 40)
    (.addOrderRule (.addCategoryAxis chart "x" "Study Event") "Study Event")
    (let [axis (.addMeasureAxis chart "y" "Visits")]
      (set! (.-tickFormat axis) "d")
      (set! (.-ticks axis) ticks))
    (.addSeries chart nil (.-bar (.-plot js/dimple)))
    (.draw chart)))

;; ---- Visit Count By Age Decade --------------------------------------------

(defn visit-count-by-age-decade-and-sex [result]
  (->> (:visit-count-by-age-decade-and-sex result)
       (reduce-kv
         (fn [res age-decade count-by-sex]
           (reduce-kv
             (fn [res sex count]
               (conj res {"Age Decade" age-decade "Sex" (name sex) "Visits" count}))
             res
             count-by-sex))
         [])))

(defn draw-vc-by-ad-and-sex-result [id result ticks]
  (let [data (visit-count-by-age-decade-and-sex result)
        svg (.newSvg js/dimple (str "#" id) "100%" "100%")
        chart (new js/dimple.chart svg (clj->js data))
        width (.-clientWidth (dom/getElement id))]
    (.setMargins chart 60 10 50 40)
    (.addOrderRule (.addCategoryAxis chart "x" "Age Decade") "Age Decade")
    (let [axis (.addMeasureAxis chart "y" "Visits")]
      (set! (.-tickFormat axis) "d")
      (set! (.-ticks axis) ticks))
    (.addOrderRule (.addSeries chart "Sex" (.-bar (.-plot js/dimple))) "Sex")
    (.assignColor chart "male" "#80B1D3" "#6b94b0" 0.8)
    (.assignColor chart "female" "#FB8072" "#d26b5f" 0.8)
    (.addLegend chart (- width 240) 15 200 30 "right")
    (.draw chart)))

;; ---- Queries ---------------------------------------------------------------

(defn execute-query!
  "Executes the query expr and publishes the result on result-topic."
  [owner expr result-topic]
  (bus/publish! owner :most-recent-snapshot-query
                {:form-rel :lens/query
                 :params {:expr expr}
                 :loaded-topic result-topic}))

;; ---- Item Value Histogram --------------------------------------------------

(defn round [precision x]
  (let [mag (count (str (int x)))
        factor (.pow js/Math 10 (max 0 (- precision mag)))]
    (/ (.round js/Math (* x factor)) factor)))

(defn value-hist [item]
  (->> (:value-histogram item)
       (map (fn [[x y]] (hash-map "Bucket" (round 3 x) "Values" y)))))

(defn draw-item-chart [id value-hist]
  (let [data value-hist
        svg (.newSvg js/dimple (str "#" id) "100%" "100%")
        chart (new js/dimple.chart svg (clj->js data))]
    (.setMargins chart 50 10 50 35)
    (.addCategoryAxis chart "x" "Bucket")
    (.addMeasureAxis chart "y" "Values")
    (.addSeries chart nil (.-bar (.-plot js/dimple)))
    (.draw chart)))

;; ---- Headline --------------------------------------------------------------

(defn collapsed-sign [collapsed]
  (d/span {:class (str "fa fa-" (if collapsed "chevron-right" "chevron-down"))
           :style {:margin-right "5px"}}))

(defn update-queries
  "Returns a function which updates the queries of a version using f and takes
  care of query indicies."
  [f]
  (fn [version]
    (let [res (update-in version [:queries] (comp vec util/index f))]
      (println "update version" (map :idx (:queries version)) "->" (map :idx (:queries res)))
      res)))

(defn duplicate-query-msg [idx]
  {:form-rel :lens/duplicate-query
   :params {:idx idx}
   :state-fn (update-queries (util/duplicate-at idx))})

(defn duplicate-query-button [owner idx]
  (d/span {:class "fa fa-files-o"
           :title "Duplicate Query"
           :role "button"
           :style {:margin-left "10px"}
           :on-click (h (bus/publish! owner ::tx (duplicate-query-msg idx)))}))

(defn remove-query-msg [idx]
  {:form-rel :lens/remove-query
   :params {:idx idx}
   :state-fn (update-queries (util/remove-at idx))})

(defn remove-query-button [owner idx]
  (d/span {:class "fa fa-minus-square-o"
           :title "Remove Query"
           :role "button"
           :style {:margin-left "10px"}
           :on-click (h (bus/publish! owner ::tx (remove-query-msg idx)))}))

(defcomponentk query-head [data owner [:opts idx collapsed]]
  (render-state [_ {:keys [hover]}]
    (d/h4 {:class "query-head"
           :on-mouse-enter #(om/set-state! owner :hover true)
           :on-mouse-leave #(om/set-state! owner :hover false)}
          (collapsed-sign collapsed)
          data
          (when hover
            [(duplicate-query-button owner idx)
             (remove-query-button owner idx)]))))

;; ---- Query Grid ------------------------------------------------------------

(defn cell-id [{:keys [query-idx col-idx]} id]
  (str "Q" query-idx "-C" col-idx "-" id))

(defn result-loaded [{:keys [query-idx col-idx]} id]
  {:pre [query-idx col-idx id]}
  [:result-loaded query-idx col-idx id])

(defn term-loaded [{:keys [query-idx col-idx]} id]
  [:term-loaded query-idx col-idx id])

(defn single-form-expr [{:keys [id]}]
  {:items [[[:form id]]]})

(defn load-term! [owner form-rel opts id]
  (bus/publish! owner :query {:form-rel form-rel
                              :params {:id id}
                              :target (term-loaded opts id)}))

(defcomponent form [{:keys [id] :as form} owner opts]
  (will-mount [_]
    (when-not (:name form)
      (load-term! owner :lens/find-form opts id))
    (execute-query! owner (single-form-expr form) (result-loaded opts id)))
  (render [_]
    (d/div
      (d/div
        (if-let [alias (:alias form)]
          (str alias " (" id ")")
          id))
      (d/p {:class "text-muted"}
           (or (:name form) "loading..."))
      (d/div {:id (cell-id opts id) :style {:height "150px"}}))))

(defn single-item-group-expr [{:keys [id]}]
  {:items [[[:item-group id]]]})

(defcomponent item-group [{:keys [id] :as item-group} owner opts]
  (will-mount [_]
    (when-not (:name item-group)
      (load-term! owner :lens/find-item-group opts id))
    (execute-query! owner (single-item-group-expr item-group)
                    (result-loaded opts id)))
  (render [_]
    (d/div
      (d/div (or (util/add-soft-hyphen (:name item-group)) "loading..."))
      (d/div {:id (cell-id opts id) :style {:height "150px"}}))))

(defcomponent item [{:keys [id] :as item} owner opts]
  (will-mount [_]
    (when-not (if (ls/is-numeric? item) (:value-histogram item) (:question item))
      (load-term! owner :lens/find-item opts id)))
  (did-update [_ _ _]
    (when-let [value-histogram (seq (value-hist item))]
      (clear-chart (cell-id opts id))
      (draw-item-chart (cell-id opts id) value-histogram)))
  (render [_]
    (d/div
      (d/div
        (if-let [alias (:name item)]
          (str alias " (" id ")")
          id))
      (d/p {:class "text-muted"}
           (or (:question item) "loading..."))
      (when (ls/is-numeric? item)
        (d/div {:id (cell-id opts id) :style {:height "150px"}})))))

(defcomponent code-list-item [cl-item]
  (render [_]
    (d/div
      (d/div
        (str (-> cl-item :id :item-id) ": " (-> cl-item :id :code))))))

(defn query-grid-cell-mouse-leave [state]
  (assoc state :hover false :dropdown-hover false :dropdown-active false))

(defn merge-into-term [term new-term]
  (merge term (dissoc new-term :embedded :forms :links)))

(defcomponent query-grid-cell
  "A cell in a query grid.

  Renders a term which can be a form, an item-group or an item. Terms coming
  from the item dialog have already properties like :name or :question. Terms
  which are loaded from the workbook do not have such properties. In a workbook
  only the :type and the :id of a term is saved.

  If :name or :question is missing, this information has to be loaded from the
  warehouse."
  [{:keys [id] :as term} owner {:keys [query-idx col-idx] :as opts}]
  (init-state [_]
    {:hover false
     :dropdown-hover false
     :dropdown-active false})
  (will-mount [_]
    (bus/listen-on owner (result-loaded opts id)
      #(om/update! term :result (select-keys % [:visit-count-by-study-event])))
    (bus/listen-on owner [:term-loaded query-idx col-idx id]
      (fn [new-term] (om/transact! term #(merge-into-term % new-term)))))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (did-update [_ _ _]
    (when-let [result (:result term)]
      (clear-chart (cell-id opts id))
      (draw-vc-by-se-result (cell-id opts id) result 5)))
  (render-state [_ {:keys [hover dropdown-hover dropdown-active]}]
    (d/div
      (d/div {:class "query-cell" :ref "cell"
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
                                                      col-idx] id))}
                "Remove"))))
        (condp = (:type term)
          :form (om/build form term {:opts opts})
          :item-group (om/build item-group term {:opts opts})
          :item (om/build item term {:opts opts})
          :code-list-item (om/build code-list-item term {:opts opts})))
      (d/div {:class "query-col-or-badge text-muted"} "OR"))))

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
    (d/div {:class "col-xs-4"}
      (when (and (pos? idx) (seq (:cells col)))
        (d/div {:class "query-col-and-badge text-muted"} "AND"))
      (d/div {:class "query-cell-list"}
        (om/build-all query-grid-cell (:cells col)
                      {:opts (assoc opts :col-idx idx)}))
      (cell-adder owner query-idx idx))))

(defcomponentk query-grid
  "A grid of query cells."
  [[:data cols] opts]
  (render [_]
    (d/div {:class "query-grid row"}
      (om/build-all query-grid-col cols {:opts opts}))))

;; ---- Result ----------------------------------------------------------------

(defn listen-on-query-updates [owner query-idx]
  (bus/listen-on owner [:query-updated query-idx]
    (fn [query-expr]
      (if (seq (:items query-expr))
        (execute-query! owner query-expr [:result-loaded query-idx])))))

(defn vc-by-se-result-chart-id [query-idx]
  (str "visit-count-by-study-event-result-chart-" query-idx))

(defcomponent visit-count-by-study-event-result
  "The result component subscribes to the :query-updated topic and executes a
  query whenever something is published."
  [result owner {:keys [query-idx]}]
  (will-mount [_]
    (bus/listen-on owner [:result-loaded query-idx]
      #(om/update! result :result (select-keys % [:visit-count-by-study-event]))))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (did-update [_ _ _]
    (clear-chart (vc-by-se-result-chart-id query-idx))
    (when-let [result (:result result)]
      (draw-vc-by-se-result (vc-by-se-result-chart-id query-idx) result 7)))
  (render [_]
    (d/div {:class "result"}
      (d/p {:class "text-uppercase"} "Visits by Study Event")
      (d/p {:class "text-muted text-center"
            :style {:display (if (:result result) "none" "block")}}
           "Please add items to the query grid.")
      (d/div {:id (vc-by-se-result-chart-id query-idx)
              :style {:height (if (:result result) "250px" "0")}}))))

(defn vc-by-ad-and-sex-result-chart-id [query-idx]
  (str "visit-count-by-age-decade-result-chart-" query-idx))

(defcomponent visit-count-by-age-decade-result
  "The result component subscribes to the :query-updated topic and executes a
  query whenever something is published."
  [result owner {:keys [query-idx]}]
  (will-mount [_]
    (bus/listen-on owner [:result-loaded query-idx]
      #(om/update! result :result (select-keys % [:visit-count-by-age-decade-and-sex]))))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (did-update [_ _ _]
    (clear-chart (vc-by-ad-and-sex-result-chart-id query-idx))
    (when-let [result (:result result)]
      (draw-vc-by-ad-and-sex-result (vc-by-ad-and-sex-result-chart-id query-idx) result 7)))
  (render [_]
    (d/div {:class "result"}
      (d/p {:class "text-uppercase"} "Visits by Age Decade")
      (d/p {:class "text-muted text-center"
            :style {:display (if (:result result) "none" "block")}}
           "Please add items to the query grid.")
      (d/div {:id (vc-by-ad-and-sex-result-chart-id query-idx)
              :style {:height (if (:result result) "250px" "0")}}))))

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

(defn publish-query-expr! [owner idx expr]
  (bus/publish! owner [:query-updated idx] expr))

(defcomponent query [{:keys [idx collapsed] :as data} owner opts]
  (will-mount [_]
    (listen-on-query-updates owner idx)
    (let [expr (build-query-expr data)]
      (publish-query-expr! owner idx expr)))
  (will-update [_ new-query _]
    (let [old-query-expr (build-query-expr (om/get-props owner))
          new-query-expr (build-query-expr new-query)]
      (when (not= old-query-expr new-query-expr)
        (publish-query-expr! owner idx new-query-expr))))
  (render [_]
    (d/div {:class "query"}
      (om/build query-head (or (:name data) (str "Query " (inc idx)))
                {:opts {:idx idx :collapsed collapsed}})
      (d/div {:class "query-body" :style {:display (if collapsed "none" "block")}}
        (om/build query-grid (:query-grid data)
                  {:opts (assoc opts :query-idx idx)})
        (om/build visit-count-by-study-event-result (:vc-by-se-result data)
                  {:opts (assoc opts :query-idx idx)})
        (om/build visit-count-by-age-decade-result (:vc-by-ad-and-sex-result data)
                  {:opts (assoc opts :query-idx idx)})))))

;; ---- Workbook --------------------------------------------------------------

(defn on-loaded-version [workbook version]
  (om/update! workbook ::head (version/api->app-state version)))

(defn tx-msg [version {:keys [form-rel params]}]
  {:uri (-> version :forms form-rel :href)
   :params params
   :result-topic ::new-version})

(defn update-state [version {:keys [state-fn] :as msg}]
  (-> (state-fn version)
      (update-in [:open-txs] #(conj % msg))))

(defn perform-tx!
  "Performs a transaction on a version like adding a query."
  [owner msg]
  (let [version (om/get-props owner)]
    (when (empty? (:open-txs version))
      (bus/publish! owner :post (tx-msg @version msg))
      (bus/publish! owner ::out-of-sync true))
    (om/transact! version [] #(update-state % msg) :history)))

(defn empty-query [idx]
  (->> {:query-grid {:cols (vec (repeat 3 {:cells []}))}}
       (version/assoc-default-results)
       (version/index-query idx)))

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
              (recur (if (= 2 (count ports)) ports [ch (async/timeout 2000)]))
              (do
                (om/set-state! owner :out-of-sync false)
                (recur [ch]))))
          (do
            (om/set-state! owner :out-of-sync true)
            (recur [ch])))))))

(defcomponentk version
  "Component which holds a version of a workbook.

  It consists of two parts, a list of queries and a query adder, both wrapped in
  a container-fluid.

  The component also manages updates of its version. It listens on update events
  from subcomponents and delegates them to the server. All updates lead to a
  ::new-version event which is than handled by its parent component the
  workbook."
  [[:data queries] owner]
  (will-mount [_]
    (bus/listen-on owner ::tx #(perform-tx! owner %))
    (out-of-sync-loop owner))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (render-state [_ {:keys [out-of-sync]}]
    (d/div {:class "container-fluid"}
      (when out-of-sync
        (d/div {:class "alert alert-warning" :role "alert"} "Out of sync!"))
      (om/build-all query queries)
      (query-adder owner))))

(defn update-workbook-msg [workbook new-version]
  {:resource (-> workbook :links :self :href)
   :representation (assoc-in workbook [:data :head-id] (-> new-version :data :id))
   :result-topic ::workbook-updated})

(defn- update-head [new-version]
  (fn [head]
    (-> (update head :open-txs pop)
        (assoc :forms (:forms new-version)))))

(defn on-new-version
  "Updates the workbook with the new version created earlier.

  Doing something to the workbook is always a two stage process. First one
  creates a new immutable version carrying the changes and second one updates
  the workbook to point to that new version."
  [owner new-version]
  (let [workbook (om/get-props owner)
        head (::head @workbook)
        open-txs (:open-txs head)]
    (if (= 1 (count open-txs))
      (bus/publish! owner :put (update-workbook-msg @workbook new-version))
      (bus/publish! owner :post (tx-msg new-version (second open-txs))))
    (om/transact! workbook [::head] (update-head new-version))))

(defn on-workbook-updated [owner wb]
  (bus/publish! owner ::out-of-sync false)
  (if-let [status (:status (ex-data wb))]
    (case status
      412
      (alert! owner :danger (str "Someone edited the workbook before your "
                                 "change. Please reload."))
      (alert! owner :danger (str "Error while saving the workbook: "
                                 (.-message wb))))
    (let [workbook (om/get-props owner)]
      (om/transact! workbook #(assoc wb ::head (::head %))))))

(defn load-head-msg
  "Message for :load topic loading the head of the workbook."
  [workbook]
  {:uri (-> workbook :links :lens/head :href) :loaded-topic ::loaded-version})

(defn local-undo [workbook]
  (om/update! workbook ::head (peek @version-history))
  (swap! version-history pop))

(defn on-undo [owner]
  (let [workbook (om/get-props owner)]
    (if (seq @version-history)
      (local-undo workbook)
      (when-let [uri (-> workbook ::head :links :lens/parent :href)]
        (bus/publish! owner :load {:uri uri :loaded-topic ::loaded-version})))))

(defn update-undo-enabled-state [owner workbook]
  (->> (if (-> workbook ::head :links :lens/parent) true false)
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
    (bus/listen-on owner ::workbook-updated #(on-workbook-updated owner %))
    (bus/listen-on owner :undo #(on-undo owner))
    (bus/publish! owner :load (load-head-msg workbook)))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (will-update [_ new-workbook _]
    (update-undo-enabled-state owner new-workbook))
  (render [_]
    (util/set-title! (str (:name (:data workbook)) " - Lens"))
    (when-let [head (::head workbook)] (om/build version head))))
