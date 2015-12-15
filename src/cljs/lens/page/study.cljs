(ns lens.page.study
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [plumbing.core :refer [fnk if-letk]]
                   [lens.macros :refer [h]])
  (:require [cljs.core.async :refer [put! close! chan <! alts!]]
            [om.core :as om]
            [om-tools.core :refer-macros [defcomponent defcomponentk]]
            [om-tools.dom :as d :include-macros true]
            [schema.core :as s :include-macros true]
            [lens.alert :refer [alert!]]
            [lens.event-bus :as bus]
            [hap-client.core :as hap]
            [clojure.string :as str]
            [lens.component :refer [typeahead-search-field]]
            [lens.util :as util]))

(defn attachment-link [type-name uri]
  (d/a {:class "attachment-link"
        :title (str "Download " type-name)
        :href uri
        :target "_blank"}
    (d/span {:class "fa fa-file-text"})
    type-name))

(defcomponentk form' [[:data id name {desc nil} {keywords nil}
                       {recording-type nil}]]
  (render [_]
    (println 'render-form name)
    (d/li
      (d/h4 {:class "pull-left"} (str name " (" id ")"))
      (attachment-link
        "Report"
        (str js/lensReport "/" id ".pdf"))
      (attachment-link
        "aCRF"
        (str js/lensAcrf "/" id ".pdf"))
      (d/div {:class "clearfix"})
      (when desc
        (util/render-multi-line-text desc))
      (when (or keywords recording-type)
        (d/dl
          (when recording-type (d/dt "Erhebungsart"))
          (when recording-type (d/dd recording-type))
          (when keywords (d/dt "Schlagwörter"))
          (when keywords (d/dd (str/join ", " (sort keywords)))))))))

(defcomponentk desc' [[:data desc]]
  (render [_]
    (d/div
      (d/div {:class "study-desc-head"}
        (when desc
          (into [] (comp (map str/trim)
                         (filter #(str/starts-with? % "Projektleitung"))
                         (map d/p))
                (str/split desc #"\n"))))
      (d/div {:class "study-desc"}
        (when desc
          (into [] (comp (map str/trim)
                         (remove #(str/starts-with? % "Projektleitung"))
                         (map d/p))
                (str/split desc #"\n")))))))

(defn- to-form [res]
  (:data res))

(defn- to-form-list [data res]
  (assoc data
    :uri (-> res :links :self :href)
    :search-uri (-> res :queries :lens/filter :href)
    :total (-> res :data :total)
    :list (mapv to-form (-> res :embedded :lens/form-defs))))

(defn handle-form-list-loaded-event [owner]
  (bus/listen-on owner ::form-list-loaded
    (fn [res]
      (if (instance? js/Error res)
        (alert! owner :danger (.-message res))
        (om/transact! (om/get-props owner) #(to-form-list {} res))))))

(defn handle-form-list-search-results [owner]
  (let [ch (om/get-state owner :search-result-ch)]
    (go-loop []
      (when-let [res (<! ch)]
        (if (instance? js/Error res)
          (alert! owner :danger (.-message res))
          (om/transact! (om/get-props owner) #(to-form-list % res)))
        (recur)))))

(s/defn load-form-list [owner uri :- hap/Uri]
  (bus/publish! owner :load {:uri uri :loaded-topic ::form-list-loaded}))

(s/defn form-list-search [owner]
  (fn [{:keys [search-uri]} query]
    (let [result-ch (chan)]
      (assert search-uri)
      (bus/publish! owner :query {:uri search-uri
                                  :params {:filter query}
                                  :target result-ch})
      result-ch)))

(defcomponentk form-list' [data owner]
  (init-state [_]
    {:search-result-ch (chan)})
  (will-mount [_]
    (println 'mount-form-list (str (:uri data)))
    (handle-form-list-loaded-event owner)
    (handle-form-list-search-results owner)
    (load-form-list owner (:uri data)))
  (will-unmount [_]
    (println 'unmount-form-list (str (:uri data)))
    (bus/unlisten-all owner))
  (render-state [_ {:keys [search-result-ch]}]
    (println 'render-form-list (str (:uri data)))
    (d/div
      (d/div {:class "forms-search-form"}
        (om/build typeahead-search-field data
                  {:opts {:search (form-list-search owner)
                          :result-ch search-result-ch
                          :placeholder "Suche"}}))
      (d/ul {:class "form-list"}
        (om/build-all form' (or (:list data) []))
        (d/li {:class "text-center text-muted"}
          (let [count (count (:list data))
                total (:total data)]
            (cond
              (= 0 total)
              "keine Instrumente gefunden"
              :else
              (str "zeige " count " von " total " Instrumente"))))))))

(defcomponentk study' [[:data {nav :form-list} name {desc nil} form-list] owner]
  (will-mount [_]
    (println 'mount-study name))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (render [_]
    (println 'render-study name)
    (d/div
      (d/h3 name)
      (d/ul {:class "nav nav-tabs"}
        (d/li (when (= :desc nav) {:class "active"})
          (d/a {:href "#"
                :on-click (h (om/update! (om/get-props owner) :nav :desc))}
            "Beschreibung"))
        (d/li (when (= :form-list nav) {:class "active"})
          (d/a {:href "#"
                :on-click (h (om/update! (om/get-props owner) :nav :form-list))}
            "Instrumente")))
      (case nav
        :desc
        (om/build desc' {:desc desc})
        :form-list
        (om/build form-list' form-list)))))

(defn study-updater [res]
  (fn [study]
    (-> (merge study (:data res))
        (assoc-in [:form-list :uri] (-> res :links :lens/form-defs :href)))))

(defn handle-study-loaded-event [owner]
  (bus/listen-on owner ::study-loaded
    (fn [res]
      (condp = (:status (ex-data res))
        404
        (alert! owner :warning "Study not found")
        nil
        (let [id (-> res :data :id)]
          (om/transact! (om/get-props owner) [:studies id] (study-updater res)))
        (alert! owner :danger "Error while loading a study")))))

(defn- load-study [owner active-study]
  (bus/publish! owner :query {:query-rel :lens/find-study
                              :params {:id active-study}
                              :target ::study-loaded}))

(defcomponentk study-page [[:data active-study studies] owner]
  (will-mount [_]
    (println 'mount-study-page (:name active-study))
    (handle-study-loaded-event owner)
    (load-study owner active-study))
  (will-unmount [_]
    (bus/unlisten-all owner))
  (render [_]
    (println 'render-study-page (:name active-study))
    (if-let [study (studies active-study)]
      (om/build study' study)
      (d/p))))
