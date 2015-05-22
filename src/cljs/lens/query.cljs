(ns lens.query
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [clojure.data :as data]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]
            [cljs.core.async :refer [put! chan <!]]
            [cljsjs.dimple]
            [lens.component :as comp]
            [lens.schema :as s]
            [lens.io :as io]
            [lens.fa :as fa]
            [lens.util :as util]
            [lens.scroll-list :as sl]))

(defn close-button [click-handler]
  (d/button {:type "button" :class "close" :onClick click-handler} "\u00D7"))

(defn primary-code-list-item-label [term]
  (str (or (:item-name term) (:item-id term)) ": " (:code term)))

(defn primary-label [{:keys [id type] :as term}]
  (condp = type
    :form (or (:alias term) id)
    :item-group (util/add-soft-hyphen (:name term))
    :item (or (:name term) id)
    :code-list-item (primary-code-list-item-label term)
    (or id (:name term))))

(defn secondary-label [{:keys [type] :as term}]
  (condp = type
    :form (:name term)
    :item-group (secondary-label (:parent term))
    :item (:question term)
    :code-list-item (:label term)
    nil))

(defn round [precision x]
  (let [mag (count (str (int x)))
        factor (.pow js/Math 10 (max 0 (- precision mag)))]
    (/ (.round js/Math (* x factor)) factor)))

(defn value-hist [term]
  (->> (:value-histogram term)
       (map (fn [[x y]] (hash-map "Bucket" (round 3 x) "Values" y)))))

(defn draw-term-chart [term]
  (let [data (value-hist term)
        svg (.newSvg js/dimple (str "#" (:id term)) "100%" 200)
        chart (new js/dimple.chart svg (clj->js data))]
    (.setMargins chart 50 10 50 35)
    (.addCategoryAxis chart "x" "Bucket")
    (.addMeasureAxis chart "y" "Values")
    (.addSeries chart nil (.-bar (.-plot js/dimple)))
    (.draw chart)))

(defcomponent expanded-item [term _]
  (did-mount [_]
    (draw-term-chart term))
  (did-update [_ _ _]
    (let [n (.getElementById js/document (:id term))]
      (while (.hasChildNodes n)
        (.removeChild n (.-lastChild n))))
    (draw-term-chart term))
  (render [_]
    (d/div
      (d/p "Id: " (:id term))
      (d/p {:id (:id term)}))))

(defn expand-term
  "Expands a term in a query group.

  The expanded term shows additional information and forms. Items will be
  loaded if the :value-histogram is missing. That is the case if a item comes
  as embedded item first which lacks the :value-histogram."
  [term]
  (if (and (= :item (:type term)) (nil? (:value-histogram term)))
    (io/get-xhr
      {:url (-> term :links :self :href)
       :on-complete (fn [body]
                      (om/transact! term #(-> (merge % body)
                                              (assoc :expanded true))))})
    (om/transact! term #(assoc % :expanded true))))

(defn collapse-term [term]
  (om/transact! term #(assoc % :expanded false)))

(defcomponent query-group-list-item [term owner]
  (init-state [_]
    {:hover false})
  (render-state [_ {:keys [hover remove]}]
    (d/div {:class "scroll-list-item"
            :style {:padding-bottom (if hover 0 10)}
            :on-mouse-over #(om/set-state! owner :hover true)
            :on-mouse-out #(om/set-state! owner :hover false)}
      (if hover
        (close-button #(put! remove @term))
        (om/build comp/count-badge term))
      (d/div (primary-label term))
      (when-let [text (secondary-label term)]
        (d/p {:class "text-muted"}
          (d/small nil text)))
      (when (and hover (not (:expanded term)))
        (d/div {:class "text-center" :style {:margin-top "-20px"}
                :on-click (fn [e] (.preventDefault e) (expand-term term))}
          (fa/span :angle-down)))
      (when (:expanded term)
        (condp = (:type term)
          :form (d/div (d/p "Id: " (:id term)))
          :item-group (d/div (d/p "Form: " (secondary-label (:parent term))))
          :item (if (s/is-numeric? term)
                  (om/build expanded-item term)
                  (d/div (d/p "Id: " (:id term))))
          :code-list-item
          (d/div (d/p "Frage: " (secondary-label (:parent term))))))
      (when (and hover (:expanded term))
        (d/div {:class "text-center" :style {:margin-top "-10px"}
                :on-click (fn [e] (.preventDefault e) (collapse-term term))}
          (fa/span :angle-up))))))

(defn add-term [term]
  (fn [group]
    (update-in group [:terms] #(conj % term))))

(defn render-drop-a-term-here []
  (d/p {:class "text-center" :style {:padding "10px 15px"}} "drop a term here"))

(defcomponent query-group-list [group owner]
  (init-state [_]
    {:remove (chan)
     :scroll-ch (chan)})
  (will-mount [_]
    (let [remove (om/get-state owner :remove)]
      (go-loop []
               (let [term (<! remove)]
                 (om/transact!
                   group :terms
                   (fn [terms]
                     (vec (clojure.core/remove #(= term %) terms)))))
               (recur))))
  (did-mount [_]
    (sl/scroll-loop group owner)
    (sl/set-scroll-pad-state! owner))
  (did-update [_ _ _]
    (sl/set-scroll-pad-state! owner))
  (render-state [_ {:keys [remove scroll-ch]}]
    (sl/render-scroll-list group scroll-ch
      {:style #js {:flex "1"}}
      (-> (vec (om/build-all query-group-list-item (:terms group)
                             {:init-state {:remove remove}}))
          (conj (render-drop-a-term-here))))))

(defn title [{:keys [num]}]
  (str "Group " num))

(defn query-group [group]
  (reify
    om/IRender
    (render [_]
      (d/div (let [m {:class "col-xs-4-flex"}]
               (if (:visible group) m (assoc m :style #js {:display "none"})))
        (d/div {:class "list-group"}
          (d/div {:class "list-group-item list-group-item-header"}
            (title group)))
        (om/build query-group-list group)))))

(defn move-left [list]
  (->> (conj list {:visible false})
       (map :visible)
       (rest)
       (mapv (fn [group vis] (assoc group :visible vis)) list)))

(defn move-right [list]
  (->> (map :visible list)
       (cons false)
       (mapv (fn [group vis] (assoc group :visible vis)) list)))

(defn move [dir]
  (condp = dir
    :left move-left
    :right move-right))

(defn query-pagination-arrow [dir]
  (fn [groups]
    (reify
      om/IRender
      (render [_]
        (if (:visible ((condp = dir :left first :right peek) groups))
          (d/li {:class "disabled"} (fa/span (fa/join :chevron dir)))
          (d/li (d/a {:href "#" :on-click #(om/transact! groups (move dir))}
                     (fa/span (fa/join :chevron dir)))))))))

(defn- query-pagination-bullet-type [group]
  (cond
    (:visible group) :circle
    (seq (:terms group)) :dot-circle-o
    :else :circle-o))

(defcomponent query-pagination-bullet [group]
  (render [_]
    (d/li (fa/span (query-pagination-bullet-type group)))))

(defcomponent query-pagination
  "The pagination of the query groups where three of ten groups are shown.

  Contains left and right arrows with ten bullets in the middle which represent
  the ten groups."
  [groups]
  (render [_]
    (apply d/ul {:class "pagination"
                 :style {:margin-top "0px" :align-self "center"}}
           (om/build (query-pagination-arrow :left) groups)
           (conj (vec (om/build-all query-pagination-bullet groups))
                 (om/build (query-pagination-arrow :right) groups)))))

(defcomponent query-groups [groups]
  (render [_]
    (d/div {:class "row-flex" :style {:flex 1}}
      (d/div {:class "col-xs-12-flex"}
        (apply d/div {:class "row-flex" :style {:flex 1}}
               (om/build-all query-group groups))
        (d/div {:class "row-flex"}
          (d/div {:class "col-xs-12-flex"}
            (om/build query-pagination groups)))))))

(defcomponent query-result-study-event-row [[study-event count]]
  (render [_]
    (d/tr
      (d/td study-event)
      (d/td {:class "text-right"} count)
      (d/td {:class "text-right"} count))))

(defn visit-count-by-study-event [result]
  (->> (:visit-count-by-study-event result)
       (map (fn [[study-event count]]
              {"Study Event" study-event "Visits" count}))))

(defn draw-query-result [result]
  (let [data (visit-count-by-study-event result)
        svg (.newSvg js/dimple "#query-result-chart" "100%" 300)
        chart (new js/dimple.chart svg (clj->js data))]
    (.setMargins chart 50 10 50 70)
    (.addOrderRule (.addCategoryAxis chart "x" "Study Event") "Study Event")
    (.addMeasureAxis chart "y" "Visits")
    (.addSeries chart nil (.-bar (.-plot js/dimple)))
    (.draw chart)))

(defcomponent query-result [result]
  (did-mount [_]
    (draw-query-result result))
  (did-update [_ _ _]
    (let [n (.getElementById js/document "query-result-chart")]
      (while (.hasChildNodes n)
        (.removeChild n (.-lastChild n))))
    (draw-query-result result))
  (render [_]
    (d/div {:class "row-flex"}
      (d/div {:class "col-xs-12-flex"}
        (d/div {:id "query-result-chart"})))))

(defn build-query-atom [{:keys [type id]}]
  [type id])

(defn build-query-expr [query]
  {:items
   (->> (map :terms (:groups query))
        (filter seq)
        (mapv
          (fn [terms]
            (mapv build-query-atom terms))))})

(defn groups-changed [old-state new-state]
  (let [diff (data/diff (-> old-state :groups)
                        (-> new-state :groups))]
    (or (first diff)
        (second diff))))

(defcomponent query [query]
  (will-update [_ next-props _]
    (when (groups-changed query next-props)
      (io/post-form
        {:url (:uri query)
         :data {:expr (build-query-expr next-props)}
         :on-complete #(om/update! query [:result]
                                   (select-keys % [:visit-count
                                                   :visit-count-by-study-event
                                                   :subject-count]))})))
  (render [_]
    (d/div {:class "col-xs-9-flex"}
      (om/build query-groups (:groups query))
      (when-let [result (:result query)]
        (om/build query-result result)))))
