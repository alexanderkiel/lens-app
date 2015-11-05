(ns lens.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [plumbing.core :refer [fnk defnk letk when-letk for-map]]
                   [lens.macros :refer [h]])
  (:require [plumbing.core :refer [assoc-when]]
            [cljs.core.async :as async :refer [put! chan <!]]
            [goog.dom :as dom]
            [goog.events :as events]
            [schema.core :as s]
            [om.core :as om]
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
            [lens.util :as util]))

(enable-console-print!)
(s/set-fn-validation! js/enableSchemaValidation)

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

(defn load-count-loop [owner load-ch]
  (go-loop []
    (when-let [{:keys [uri result-ch]} (<! load-ch)]
      (io/get-xhr {:url uri :on-complete #(put! result-ch %)})
      (recur))))

(defn load!
  "Fetches uri and publishes the result under loaded-topic."
  [owner uri auth loaded-topic]
  {:pre [owner uri loaded-topic]}
  (let [req {:url uri :on-complete #(bus/publish! owner loaded-topic %)}]
    (if auth
      (io/get-xhr (auth/assoc-auth-token req))
      (io/get-xhr req))))

(defn resolv-uri
  "Tries to resolv the uri of the link relation using links from all
  service documents. Returns nil if not found."
  [owner link-rel]
  (:href (link-rel @(om/get-shared owner :links))))

(defn resolv-action
  "Tries to resolv the action of the form relation using forms from all
  service documents. Returns nil if not found."
  [owner form-rel]
  (:action (form-rel @(om/get-shared owner :forms))))

(defn load-loop
  "Listens on :load and :service-document-loaded topics. Tries to load
  documents from the messages :link-rel. Spools messages with unresolvable
  link relations and tries to resolv them after a new service document was
  loaded."
  [owner]
  (bus/listen-on-mult owner
    {:load
     (fn [unresolvables {:keys [uri link-rel auth loaded-topic] :as msg}]
       (assert (or uri link-rel))
       (if-let [uri (or uri (resolv-uri owner link-rel))]
         (do (load! owner uri auth loaded-topic) unresolvables)
         (conj unresolvables msg)))
     :service-document-loaded
     (fn [unresolvables]
       (reduce
         (fn [unresolvables {:keys [link-rel auth loaded-topic] :as unresolvable}]
           (if-let [uri (resolv-uri owner link-rel)]
             (do (load! owner uri auth loaded-topic) unresolvables)
             (conj unresolvables unresolvable)))
         #{}
         unresolvables))}
    #{}))

(defn query!
  "Issues a query to action with params and publishes the result under
  loaded-topic."
  [owner action more]
  {:pre [owner action (:loaded-topic more)]}
  (-> {:url action :data (:params more)
       :on-complete #(bus/publish! owner (:loaded-topic more) %)}
      (auth/assoc-auth-token)
      (io/get-form)))

(defn query-loop
  "Listens on :query and :service-document-loaded topics. Tries to issue a query
  to the messages :form-rel. Spools messages with unresolvable form relations
  and tries to resolv them after a new service document was loaded."
  [owner]
  (bus/listen-on-mult owner
    {:query
     (fn [unresolvables {:keys [action form-rel] :as msg}]
       (assert (or action form-rel))
       (if-let [action (or action (resolv-action owner form-rel))]
         (do (query! owner action msg) unresolvables)
         (conj unresolvables msg)))
     :service-document-loaded
     (fn [unresolvables]
       (reduce
         (fn [unresolvables {:keys [form-rel] :as unresolvable}]
           (if-let [action (resolv-action owner form-rel)]
             (do (query! owner action unresolvable) unresolvables)
             (conj unresolvables unresolvable)))
         #{}
         unresolvables))}
    #{}))

(defn resolv-most-recent-snapshot-action
  "Tries to resolv the action of the form relation using forms of the most
  recent snapshot. Returns nil if not found."
  [owner link-rel]
  (:action (link-rel (:forms @(om/get-shared owner :most-recent-snapshot)))))

(defn most-recent-snapshot-query-loop
  "Listens on :most-recent-snapshot-query and :most-recent-snapshot-loaded
  topics. Tries to issue a query to the messages :form-rel. Spools messages with
  unresolvable form relations and tries to resolv them after the most recent
  snapshot was loaded."
  [owner]
  (bus/listen-on-mult owner
    {:most-recent-snapshot-query
     (fn [{:keys [snapshot] :as state} {:keys [form-rel] :as msg}]
       (assert form-rel)
       (if-let [action (:action (form-rel (:forms snapshot)))]
         (do (query! owner action msg) state)
         (update-in state [:unresolvables] #(conj % msg))))
     :most-recent-snapshot-loaded
     (fn [{:keys [unresolvables]} snapshot]
       {:unresolvables
        (reduce
          (fn [unresolvables {:keys [form-rel] :as unresolvable}]
            (if-let [action (:action (form-rel (:forms snapshot)))]
              (do (query! owner action unresolvable) unresolvables)
              (conj unresolvables unresolvable)))
          #{}
          unresolvables)
        :snapshot snapshot})}
    {:unresolvables #{}
     :snapshot nil}))

(defn post!
  "Issues a post to action with params and publishes the result under
  result-topic."
  [owner action params result-topic]
  {:pre [owner action result-topic]}
  (-> {:url action :data params
       :on-complete #(bus/publish! owner result-topic %)}
      (auth/assoc-auth-token)
      (io/post-form)))

(defn post-loop [owner]
  (bus/listen-on-mult owner
    {:post
     (fn [unresolvables {:keys [action form-rel params result-topic] :as msg}]
       (assert (or action form-rel))
       (if-let [action (or action (resolv-action owner form-rel))]
         (do (post! owner action params result-topic) unresolvables)
         (conj unresolvables msg)))
     :service-document-loaded
     (fn [unresolvables]
       (reduce
         (fn [unresolvables {:keys [form-rel params result-topic]
                             :as unresolvable}]
           (if-let [action (resolv-action owner form-rel)]
             (do (post! owner action params result-topic) unresolvables)
             (conj unresolvables unresolvable)))
         #{}
         unresolvables))}
      #{}))

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

(defn load-most-recent-snapshot [owner]
  (bus/publish! owner :load {:link-rel :lens/most-recent-snapshot
                             :loaded-topic :most-recent-snapshot-loaded}))

(defcomponent app [app-state owner]
  (will-mount [_]
    (history-loop app-state owner)
    (auth/sign-in-loop owner)
    (auth/sign-out-loop owner)
    (service-documents-loop owner)
    (load-loop owner)
    (query-loop owner)
    (most-recent-snapshot-query-loop owner)
    (post-loop owner)
    (put-loop owner)
    (figwheel-reload-loop owner)
    (load-count-loop owner (om/get-shared owner :count-load-ch))
    (auth/validate-token owner)
    (bus/listen-on owner :loaded-workbook
      #(on-loaded-workbook app-state owner %))
    (load-all-service-documents owner)
    (load-most-recent-snapshot owner))
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
            :forms (atom {})
            :most-recent-snapshot (atom nil)}
   :tx-listen
   (fn [{:keys [path old-value new-value tag] :as tx} _]
     (condp = tag

       :history
       (on-history-tx tx)

       #_:query
       #_(println "TX at" path ": " old-value "->" new-value)

       (do)))})
