(ns lens.terms
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [lens.macros :refer [h]])
  (:require [plumbing.core :refer [assoc-when conj-when]]
            [clojure.string :as str]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]
            [cljs.core.async :refer [put! close! chan <! alts! sub]]
            [schema.core :as s :include-macros true]
            [lens.schema :as ls]
            [lens.io :as io]
            [lens.fa :as fa]
            [lens.component :as comp]
            [lens.event-bus :as event-bus]
            [lens.scroll-list :as sl]
            [lens.util :as util]
            [lens.event-bus :as bus]))

(defcomponent return-stack-item [term]
  (render-state [_ {:keys [return-ch]}]
    (d/li
      (d/a {:href "#"
            :on-click (h (put! return-ch @term))}
        (when-let [count (:count term)]
          (d/span {:class "badge"} count))
        (or (some-> (:name term) (util/add-soft-hyphen)) (:id term))))))

(defcomponent return-stack [terms]
  (render-state [_ {:keys [return-ch]}]
    (apply d/ol {:class "breadcrumb"}
           (om/build-all return-stack-item terms
                         {:init-state {:return-ch return-ch}}))))

(defn build-format [term]
  (str "term/" (if (= :study-event (:type term)) "study-event" "form")))

(defn primary-label [{:keys [id type] :as term}]
  (condp = type
    :form (or (:alias term) id)
    :item-group (util/add-soft-hyphen (:name term))
    :item (or (:name term) id)
    :code-list-item (:code term)
    (or id (:name term))))

(defn secondary-label [{:keys [type] :as term}]
  (condp = type
    :form (:name term)
    :item (:question term)
    :code-list-item (:label term)
    nil))

(defcomponent terms-list-item
  "Term item with primary label, secondary label and subject count."
  [term :- ls/Term]
  (render-state [_ {:keys [open activate]}]
    (d/a {:class (str/join " " (conj-when ["list-group-item"]
                                          (when (:active term) "active")))
          :href "#"
          :on-click (h (put! activate (:id @term)))
          :on-double-click #(when (:childs @term) (put! open @term))}
      (om/build comp/count-badge term)
      (primary-label term)
      (cond
        (ls/has-code-list? term) (d/span " " (fa/span :book))
        (ls/is-numeric? term) (d/span " " (fa/span :bar-chart-o)))
      (when-let [text (secondary-label term)]
        (d/div (when (not (:active term)) {:class "text-muted"})
          (d/small text))))))

(def child-rel {:lens/forms :lens/item-groups
                :lens/item-groups :lens/items
                :lens/items :lens/item-code-list-items})

(defn childs-link [body]
  (-> body :links :self))

(s/defn childs-spec :- ls/ChildSpec
  "Builds a spec which contains the information nessesary to navigate to the
  childs of a term."
  [rel body]
  (some->> (childs-link body)
           (hash-map :rel (child-rel rel) :link)))

(def search-childs-form-rel
  {:lens/forms :lens/search-item-groups
   :lens/item-groups :lens/search-items})

(defn search-childs-form [rel body]
  (when-let [form-rel (search-childs-form-rel rel)]
    (-> body :forms form-rel)))

(defn search-childs-spec
  "Builds a spec which contains the information nessesary to search for certain
  childs of a term.

  The spec consists of the :form which can be used to execute the search and
  the :rel under which the childs can be found in :embedded of the returned
  resource."
  [rel body]
  (some->> (search-childs-form rel body)
           (hash-map :rel (child-rel rel) :form)))

(defn build-term [parent rel body]
  (-> body
      (assoc :parent parent)
      (assoc-when :childs (childs-spec rel body))
      (assoc-when :search-childs (search-childs-spec rel body))))

(def filter-form-rel {:lens/forms :lens/filter
                      :lens/item-groups :lens/search-item-groups
                      :lens/items :lens/search-items})

(defn build-terms-list
  "Rel is the link relation of the terms found in :embedded."
  [rel body]
  {:terms (mapv #(build-term body rel %) (rel (:embedded body)))
   :rel rel
   :filter-form ((get body :forms {}) (filter-form-rel rel))
   :next (-> body :links :next)})

(defn extend-terms-list [old body]
  (let [new (build-terms-list (:rel old) body)]
    (-> old
        (update-in [:terms] #(into % (:terms new)))
        (assoc-in [:next] (:next new)))))

(defn on-update [list owner]
  (when-let [next-link (:next list)]
    (when (and (not (:issued-next-page list))
               (< (sl/scroll-height-left owner) 500))
      (om/update! list :issued-next-page true)
      (io/get-xhr
       {:url (:href next-link)
        :on-complete
        (fn [body]
          (om/transact! list #(-> (extend-terms-list % body)
                                  (dissoc :issued-next-page))))}))))

(defn active-state-updater
  "Maps over a seq of terms activating/deactivating each depending on term-id."
  [term-id]
  (partial mapv #(assoc % :active (= term-id (:id %)))))

(defcomponent terms-list [list owner]
  (init-state [_]
    {:activate (chan)})
  (will-mount [_]
    (go-loop []
      (when-let [term-id (<! (om/get-state owner :activate))]
        (om/transact! list :terms (active-state-updater term-id))
        (recur))))
  (will-unmount [_]
    (close! (om/get-state owner :activate)))
  (render-state [_ {:keys [open activate]}]
    (d/div {:className "list-group"}
      (om/build-all terms-list-item (:terms list)
                    {:init-state {:open open :activate activate}}))))

(defn update-return-stack
  "Puts the new-node on the return-stack.

  Searches the whole stack for the new-node cutting any childs."
  [return-stack new-node]
  (-> (into [] (take-while #(not= new-node %) return-stack))
      (conj new-node)))

(defn load-childs [terms term]
  {:pre [(-> term :childs :link :href)]}
  (io/get-xhr
   {:url (-> term :childs :link :href)
    :on-complete
    (fn [body]
      (om/transact!
       terms
       (fn [terms]
         (-> terms
             (update-in [:return-stack] update-return-stack term)
             (assoc :list (build-terms-list (-> term :childs :rel) body))
             (dissoc :query)))))}))

(defn search-childs [form terms term]
  (io/form
   {:url (:action form)
    :method (:method form)
    :data {(-> form :params ffirst) (:query @terms)}
    :on-complete
    (fn [body]
      (om/transact!
       terms
       (fn [terms]
         (-> terms
             (update-in [:return-stack] update-return-stack term)
             (assoc :list (build-terms-list (-> term :search-childs :rel) body))))))}))

(defn remote-term-search
  "Search function for the typeahead-search-field.

  It takes the terms state to be able to obtain the :filter-form and returns
  a channel conveying the result."
  [terms query]
  (let [filter-form (-> terms :list :filter-form)
        result-ch (chan)]
    (io/form
     {:url (:action filter-form)
      :method (:method filter-form)
      :data {(-> filter-form :params ffirst) query}
      :on-complete #(put! result-ch %)})
    result-ch))

(defn search-result-loop [terms search-result-ch]
  (go-loop []
    (when-let [result (<! search-result-ch)]
      (om/transact! terms :list #(build-terms-list (:rel %) result))
      (recur))))

(defn all-forms [all-forms-href]
  {:pre [all-forms-href]}
  {:name "Forms"
   :childs
   {:rel :lens/forms
    :link {:href all-forms-href}}
   :search-childs
   {:rel :lens/forms
    :form
    {:action all-forms-href
     :method "GET"
     :title "Filter Forms"
     :params
     {:filter
      {:type :string}}}}})

(defn find-active-term [terms]
  (first (filter :active (-> terms :list :terms))))

(defn clean-term [term]
  (dissoc term :childs :search-childs :parent :active :embedded :forms :links))

(defcomponent terms
  "The terms component consists of a return stack, a search field and a terms
  list."
  [terms owner]
  (init-state [_]
    {:open (chan)
     :return-ch (chan)
     :search-result-ch (chan)})
  (will-mount [_]
    (bus/listen-on owner :service-document-loaded
      (fn [doc]
        (when-let [all-forms-href (-> doc :links :lens/all-forms :href)]
          (put! (om/get-state owner :open) (all-forms all-forms-href)))))
    (let [open (om/get-state owner :open)
          return-ch (om/get-state owner :return-ch)]
      (go-loop []
        (let [[term] (alts! [open return-ch])]
          (if (str/blank? (:query @terms))
            (load-childs terms term)
            (if-let [form (-> term :search-childs :form)]
              (search-childs form terms term)
              (load-childs terms term)))
          (recur))))
    (search-result-loop terms (om/get-state owner :search-result-ch)))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (render-state [_ {:keys [open return-ch search-result-ch]}]
    (d/div
      (om/build return-stack (:return-stack terms)
                {:init-state {:return-ch return-ch}})
      (om/build comp/typeahead-search-field terms
                {:opts {:search remote-term-search
                        :enabled? #(-> % :list :filter-form :action)
                        :result-ch search-result-ch}})
      (om/build terms-list (:list terms)
                {:init-state {:open open}}))))
