(ns lens.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [plumbing.core :refer [fnk defnk letk when-letk for-map]]
                   [lens.macros :refer [h]])
  (:require [plumbing.core :refer [assoc-when]]
            [cljs.core.async :as async :refer [put! close! chan <!]]
            [async-error.core :refer-macros [go-try <?]]
            [goog.dom :as dom]
            [goog.events :as events]
            [schema.core :as s :refer [Any Bool] :include-macros true]
            [om.core :as om]
            [om-tools.core :refer-macros [defcomponent defcomponentk]]
            [om-tools.dom :as d :include-macros true]
            [lens.io :as io]
            [lens.auth :as auth]
            [lens.history :refer [history-loop]]
            [lens.event-bus :as bus]
            [lens.navbar :refer [navbar]]
            [lens.alert :refer [alerts alert!]]
            [lens.page.index :refer [index]]
            [lens.page.study-list :refer [study-list]]
            [lens.page.study :refer [study-page]]
            [hap-client.core :as hap]))

(enable-console-print!)
(s/set-fn-validation! js/enableSchemaValidation)

(defonce app-state
  (atom
    {:navbar
     {:nav
      {:undo-nav-item {}
       :items
       [{:id :study-list
         :name "Studien"
         :active false
         :handler #(bus/publish! % :route {:handler :study-list})}]}
      :sign-in-out {}}
     :alerts
     []
     :item-dialog
     {:display "none"
      :terms
      {:return-stack []
       :list {:terms []}}}
     :pages
     {:active-page :index
      :pages
      {:index {}
       :study-list
       {:list
        []}
       :study
       {:active-study nil
        :studies {}}}}}))

(s/defn load-service-document [owner service :- hap/Resource]
  (go
    (try
      (let [doc (<? (hap/fetch service))]
        (bus/publish! owner :service-document-loaded doc))
      (catch js/Error _
        (alert! owner :warning (str "Service " service " not available. "
                                    "Functionality may be limited."))))))

(defn load-all-service-documents [owner]
  (load-service-document owner js/lensWorkbook)
  (load-service-document owner js/lensWarehouse))

(defn service-documents-loop
  "Subscribes to :service-document-loaded and merges the links, queries and
  forms of all service documents under :links, :queries and :forms of shared
  state."
  [owner]
  (let [links (om/get-shared owner :links)
        queries (om/get-shared owner :queries)
        forms (om/get-shared owner :forms)]
    (bus/listen-on owner :service-document-loaded
      (fn [doc]
        (swap! links #(merge % (dissoc (:links doc) :self)))
        (swap! queries #(merge % (:queries doc)))
        (swap! forms #(merge % (:forms doc)))))))

(def Chan
  "A core.async channel."
  (s/pred some? 'channel?))

(def Target
  "A event target which can be a topic in the global event bus or a channel."
  (s/either bus/Topic Chan))

(s/defn publish! [owner target :- Target result]
  (if (keyword? target)
    (bus/publish! owner target result)
    (do (put! target result)
        (close! target))))

(s/defn load!
  "Fetches uri and publishes the result under loaded-topic."
  [owner uri :- hap/Resource loaded-topic :- s/Keyword]
  (go
    (->> (<! (hap/fetch uri (auth/assoc-access-token {})))
         (bus/publish! owner loaded-topic))))

(s/defn resolv-uri
  "Tries to resolv the uri of the link relation using links from all
  service documents. Returns nil if not found."
  [owner were :- (s/enum :links :queries :forms) rel :- s/Keyword]
  (:href (rel @(om/get-shared owner were))))

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
       (if-let [uri (or uri (resolv-uri owner :links link-rel))]
         (do (load! owner uri loaded-topic) unresolvables)
         (conj unresolvables msg)))
     :service-document-loaded
     (fn [unresolvables]
       (reduce
         (fn [unresolvables {:keys [link-rel loaded-topic] :as unresolvable}]
           (if-let [uri (resolv-uri owner :links link-rel)]
             (do (load! owner uri loaded-topic) unresolvables)
             (conj unresolvables unresolvable)))
         #{}
         unresolvables))}
    #{}))

(s/defn query!
  "Issues a query to uri with params and publishes the result under target."
  [owner uri :- hap/Uri args :- hap/Args target :- Target]
  (go
    (->> (<! (hap/query {:href uri} args))
         (publish! owner target))))

(defn query-loop
  "Listens on :query and :service-document-loaded topics. Tries to issue a query
  to the messages :query-rel. Spools messages with unresolvable query relations
  and tries to resolv them after a new service document was loaded."
  [owner]
  (bus/listen-on-mult owner
    {:query
     (fn [unresolvables {:keys [uri query-rel params target] :as msg}]
       (assert (or uri query-rel))
       (if-let [uri (or uri (resolv-uri owner :queries query-rel))]
         (do (query! owner uri params target) unresolvables)
         (conj unresolvables msg)))
     :service-document-loaded
     (fn [unresolvables]
       (reduce
         (fn [unresolvables {:keys [query-rel params target] :as msg}]
           (if-let [uri (resolv-uri owner :queries query-rel)]
             (do (query! owner uri params target) unresolvables)
             (conj unresolvables msg)))
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
     (fn [{:keys [snapshot] :as state} {:keys [form-rel params target] :as msg}]
       (assert form-rel)
       (if-let [uri (:href (form-rel (:forms snapshot)))]
         (do (query! owner uri params target) state)
         (update-in state [:unresolvables] #(conj % msg))))
     :most-recent-snapshot-loaded
     (fn [{:keys [unresolvables]} snapshot]
       {:unresolvables
        (reduce
          (fn [unresolvables {:keys [form-rel params target] :as msg}]
            (if-let [uri (:href (form-rel (:forms snapshot)))]
              (do (query! owner uri params target) unresolvables)
              (conj unresolvables msg)))
          #{}
          unresolvables)
        :snapshot snapshot})}
    {:unresolvables #{}
     :snapshot nil}))

(s/defn post!
  "Issues a post to uri with params and publishes the result under
  result-topic."
  [owner uri :- hap/Uri params :- (s/maybe hap/Args) result-topic :- s/Keyword]
  (go
    (try
      (let [resource (<? (hap/create {:href uri} (or params {})
                                     (auth/assoc-access-token {})))
            doc (<? (hap/fetch resource (auth/assoc-access-token {})))]
        (bus/publish! owner result-topic doc))
      (catch js/Error e
        (alert! owner :danger (.-message e))))))

(defn post-loop [owner]
  (bus/listen-on-mult owner
    {:post
     (fn [unresolvables {:keys [uri form-rel params result-topic] :as msg}]
       (assert (or uri form-rel))
       (if-let [uri (or uri (resolv-uri owner :forms form-rel))]
         (do (post! owner uri params result-topic) unresolvables)
         (conj unresolvables msg)))
     :service-document-loaded
     (fn [unresolvables]
       (reduce
         (fn [unresolvables {:keys [form-rel params result-topic]
                             :as unresolvable}]
           (if-let [uri (resolv-uri owner :forms form-rel)]
             (do (post! owner uri params result-topic) unresolvables)
             (conj unresolvables unresolvable)))
         #{}
         unresolvables))}
    #{}))

(defn put-loop [owner]
  (bus/listen-on owner :put
    (fnk [resource representation result-topic]
      (go
        (->> (<! (hap/update resource representation (auth/assoc-access-token {})))
             (bus/publish! owner result-topic))))))

(defonce figwheel-reload-ch
  (let [ch (chan)]
    (events/listen (.-body js/document) "figwheel.js-reload" #(put! ch %))
    ch))

(defn figwheel-reload-loop [owner]
  (go-loop []
    (when (<! figwheel-reload-ch)
      (om/refresh! owner)
      (recur))))

(defcomponentk page-selector [[:data active-page pages]]
  (render [_]
    (case active-page
      :index (om/build index (:index pages))
      :study-list (om/build study-list (:study-list pages))
      :study (om/build study-page (:study pages))
      (d/p (str "Unknown page " active-page ".")))))

(defcomponent app [app-state owner]
  (will-mount [_]
    (println 'mount-app)
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
    (auth/validate-token owner)
    (load-all-service-documents owner))
  (will-unmount [_]
    (bus/unlisten-all owner)
    (async/close! (om/get-shared owner :count-load-ch)))
  (render [_]
    (println 'render-app)
    (d/div
      (om/build navbar (:navbar app-state))
      (om/build alerts (:alerts app-state))
      (d/div {:class "container main-content"}
        (om/build page-selector (:pages app-state))))))

(om/root app app-state
  {:target (dom/getElement "app")
   :shared {:auth-service js/lensAuth
            :count-load-ch (chan)
            :item-dialog-ch (chan)
            :event-bus (bus/init-bus)
            :links (atom {})
            :queries (atom {})
            :forms (atom {})}})
