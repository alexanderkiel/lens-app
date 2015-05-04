(ns lens.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [plumbing.core :refer [fnk defnk letk when-letk for-map]]
                   [lens.macros :refer [h]])
  (:require [plumbing.core :refer [assoc-when]]
            [cljs.core.async :as async :refer [put! chan <!]]
            [goog.dom :as dom]
            [goog.events :as events]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]
            [lens.io :as io]
            [lens.auth :as auth]
            [lens.history :refer [history-loop]]
            [lens.event-bus :as bus]
            [lens.navbar :refer [navbar]]
            [lens.item-dialog :refer [item-dialog]]
            [lens.alert :as alert :refer [alert alert!]]
            [lens.workbook :refer [workbook]]
            [lens.workbooks :refer [workbooks]]
            [lens.fa :as fa]))

(enable-console-print!)

(defonce app-state
  (atom
    {:navbar
     {:nav
      {:undo-nav-item {}
       :items []}
      :sign-in-out {}}
     :alert
     {}
     :item-dialog
     {:display "none"
      :terms
      {:return-stack []
       :list {:terms []}}}
     :workbooks
     {}}))

(defn load-service-document [owner service]
  (io/get-xhr {:url service
               :on-complete #(bus/publish! owner :service-document-loaded %)}))

(defn load-all-service-documents [owner]
  (load-service-document owner js/lensWorkbook)
  (load-service-document owner js/lensWarehouse))

(defn service-documents-loop
  "Subscribes to :service-document-loaded and merges the links and forms of
  all service documents under :links and :forms of shared state."
  [owner]
  (let [links (om/get-shared owner :links)
        forms (om/get-shared owner :forms)]
    (bus/listen-on owner :service-document-loaded
      (fn [doc]
        (swap! links #(merge % (dissoc (:links doc) :self)))
        (swap! forms #(merge % (:forms doc)))))))

(defn load-count-loop [load-ch]
  (go-loop []
    (when-let [{:keys [uri result-ch]} (<! load-ch)]
      (io/get-xhr {:url uri :on-complete #(put! result-ch %)})
      (recur))))

(defn load!
  "Fetches uri and publishes the result under loaded-topic."
  [owner uri loaded-topic]
  {:pre [owner uri loaded-topic]}
  (-> {:url uri :on-complete #(bus/publish! owner loaded-topic %)}
      (auth/assoc-auth-token)
      (io/get-xhr)))

(defn query!
  "Issues a query to action with params and publishes the result under
  loaded-topic."
  [owner action params loaded-topic]
  {:pre [owner action params loaded-topic]}
  (-> {:url action :data params
       :on-complete #(bus/publish! owner loaded-topic %)}
      (auth/assoc-auth-token)
      (io/get-form)))

(defn resolv-uri
  "Tries to resolv the uri of the link relation using links from all
  service documents. Returns nil if not found."
  [owner link-rel]
  (:href (link-rel @(om/get-shared owner :links))))

(defn resolv-action
  "Tries to resolv the action of the form relation using forms from all
  service documents. Returns nil if not found."
  [owner link-rel]
  (:action (link-rel @(om/get-shared owner :forms))))

(defn load-loop
  "Listens on :load and :service-document-loaded topics. Tries to load
  documents from the messages :link-rel. Spools messages with unresolvable
  link relations and tries to resolv them after a new service document was
  loaded."
  [owner]
  (bus/listen-on-mult owner
    {:load
     (fn [unresolvables {:keys [uri link-rel loaded-topic] :as msg}]
       (assert (or uri link-rel))
       (if-let [uri (or uri (resolv-uri owner link-rel))]
         (do (load! owner uri loaded-topic) unresolvables)
         (conj unresolvables msg)))
     :service-document-loaded
     (fn [unresolvables]
       (reduce
         (fn [unresolvables {:keys [link-rel loaded-topic] :as unresolvable}]
           (if-let [uri (resolv-uri owner link-rel)]
             (do (load! owner uri loaded-topic) unresolvables)
             (conj unresolvables unresolvable)))
         #{}
         unresolvables))}
    #{}))

(defn query-loop
  "Listens on :query and :service-document-loaded topics. Tries to issue a query
  to the messages :form-rel. Spools messages with unresolvable form relations
  and tries to resolv them after a new service document was loaded."
  [owner]
  (bus/listen-on-mult owner
    {:query
     (fn [unresolvables {:keys [form-rel params loaded-topic] :as msg}]
       (if form-rel
         (if-let [action (resolv-action owner form-rel)]
           (do (query! owner action params loaded-topic) unresolvables)
           (conj unresolvables msg))
         unresolvables))
     :service-document-loaded
     (fn [unresolvables]
       (reduce
         (fn [unresolvables {:keys [form-rel params loaded-topic] :as unresolvable}]
           (if-let [action (resolv-action owner form-rel)]
             (do (query! owner action params loaded-topic) unresolvables)
             (conj unresolvables unresolvable)))
         #{}
         unresolvables))}
    #{}))

(defn post-loop [owner]
  (bus/listen-on owner :post
    (fnk [action result-topic & more]
      (assert action)
      (assert result-topic)
      (println "post-loop post to" action)
      (-> {:url action :data (:params more)
           :on-complete (fn [result] (bus/publish! owner result-topic result))}
          (auth/assoc-auth-token)
          (io/post-form)))))

(defn put-loop [owner]
  (bus/listen-on owner :put
    (fnk [action if-match params result-topic]
      (assert action)
      (assert if-match)
      (-> {:url action :if-match if-match :data params
           :on-complete (fn [result] (bus/publish! owner result-topic result))}
          (auth/assoc-auth-token)
          (io/put-form)))))

(defn prepare-workbook
  "Copies the ETag from metadata to the :etag key."
  [wb]
  (assoc wb :etag ((meta wb) "etag")))

(defn on-loaded-workbook [app-state owner wb]
  (if wb
    (om/transact! app-state #(-> (assoc % :workbook (prepare-workbook wb))
                                 (assoc-in [:workbooks :active] false)))
    (alert! owner :warning
            (d/span "Workbook not found. Please go "
              (d/a {:href "#" :class "alert-link"
                    :on-click (h (alert/close! owner)
                                 (bus/publish! owner :route {:handler :index}))}
                "home")
              "."))))

(defonce figwheel-reload-ch
  (let [ch (chan)]
    (events/listen (.-body js/document) "figwheel.js-reload" #(put! ch %))
    ch))

(defn figwheel-reload-loop [owner]
  (go-loop []
    (when (<! figwheel-reload-ch)
      (om/refresh! owner)
      (recur))))

(defcomponent app [app-state owner]
  (will-mount [_]
    (history-loop app-state owner)
    (auth/sign-in-loop owner)
    (auth/sign-out-loop owner)
    (service-documents-loop owner)
    (load-loop owner)
    (query-loop owner)
    (post-loop owner)
    (put-loop owner)
    (figwheel-reload-loop owner)
    (load-count-loop (om/get-shared owner :count-load-ch))
    (auth/validate-token owner)
    (bus/listen-on owner :loaded-workbook #(on-loaded-workbook app-state owner %))
    (load-all-service-documents owner))
  (will-unmount [_]
    (bus/unlisten-all owner)
    (async/close! (om/get-shared owner :count-load-ch)))
  (render [_]
    (if-let [wb (:workbook app-state)]
      (d/div
        (om/build item-dialog (:item-dialog app-state))
        (om/build navbar (:navbar app-state))
        (om/build alert (:alert app-state))
        (om/build workbooks (:workbooks app-state))
        (om/build workbook wb))
      (d/div
        (om/build item-dialog (:item-dialog app-state))
        (om/build navbar (:navbar app-state))
        (om/build alert (:alert app-state))
        (om/build workbooks (:workbooks app-state))))))

(defnk on-history-tx
  "Workbook version advances.

  Save the old state as point in history so that one can go back there."
  [old-state]
  (swap! lens.workbook/version-history conj (-> old-state :workbook :head)))

(om/root app app-state
  {:target (dom/getElement "app")
   :shared {:auth-service js/lensAuth
            :count-load-ch (chan)
            :item-dialog-ch (chan)
            :event-bus (bus/init-bus)
            :links (atom {})
            :forms (atom {})}
   :tx-listen
   (fn [{:keys [path old-value new-value tag] :as tx} _]
     (condp = tag

       :history
       (on-history-tx tx)

       :query
       (println "TX at" path ": " old-value "->" new-value)

       (do)))})
