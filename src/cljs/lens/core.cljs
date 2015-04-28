(ns lens.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [plumbing.core :refer [fnk defnk]])
  (:require [plumbing.core :refer [assoc-when]]
            [schema.core :as s]
            [cljs.core.async :as async :refer [put! chan <! >! alts! pub sub]]
            [goog.dom :as dom]
            [bidi.bidi :as bidi]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]
            [cljs-time.format :as tf]
            [cljs-time.coerce :as tc]
            [hodgepodge.core :as storage :refer [session-storage]]
            [lens.io :as io]
            [lens.util :as util]
            [lens.event-bus :as bus]
            [lens.navbar :refer [navbar]]
            [lens.item-dialog :refer [item-dialog]]
            [lens.workbook :refer [workbook]]
            [lens.workbooks :refer [workbooks]])
  (:import goog.History goog.history.Html5History goog.history.EventType))

(enable-console-print!)

(def routes
  ["/" {"" :index
        "workbooks" :workbooks}])

(def services
  {:auth js/lensAuth
   :workbook js/lensWorkbook
   :warehouse js/lensWarehouse})

(def app-state
  (atom
    {:navbar
     {:nav
      {:items
       [{:id :workbooks :name "Workbooks"}]}
      :sign-in-out {}}
     :item-dialog
     {:display "none"
      :terms
      {:return-stack []
       :list {:terms []}}}
     :pages
     {:workbooks
      {}}}))

(defn create-history []
  (doto (Html5History.)
    (.setUseFragment false)
    (.setPathPrefix "")))

(defn activate-nav-item [item target]
  (println :item item :target target)
  (assoc item :active (= target (:id item))))

(defn navigate [target]
  (println :navigate target)
  (fn [app]
    (-> app
        (update-in [:navbar :nav :items]
                   (partial mapv #(activate-nav-item % target)))
        (assoc-in [:pages target :active] true))))

(defn history-loop [app]
  (let [history (create-history)
        nav (util/listen history EventType/NAVIGATE)]
    (go-loop []
      (when-let [token (.-token (<! nav))]
        (when-let [match (bidi/match-route routes token)]
          (println "match" (:handler match))
          (condp = (:handler match)
            :index (.setToken history "/workbooks" "Workbooks - Lens")
            :workbooks (om/transact! app (navigate :workbooks))))
        (recur)))
    (.setEnabled history true)))

(defn service-document-loaded [doc]
  {:topic :service-document-loaded
   :service-document doc})

(defn load-service-document [service publisher]
  (io/get-xhr {:url service
               :on-complete #(put! publisher (service-document-loaded %))}))

(defn load-all-service-documents [publisher]
  (load-service-document (:workbook services) publisher)
  (load-service-document (:warehouse services) publisher))

(defn load-count-loop [load-ch]
  (go-loop []
    (when-let [{:keys [uri result-ch]} (<! load-ch)]
      (io/get-xhr {:url uri :on-complete #(put! result-ch %)})
      (recur))))

(defn service-documents-loop
  "Subscribes to :service-document-loaded and merges the links and forms of
  all service documents under :links and :forms of shared state."
  [owner]
  (let [links (om/get-shared owner :links)
        forms (om/get-shared owner :forms)
        ch (chan)]
    (sub (bus/publication owner) :service-document-loaded ch)
    (go-loop []
      (when-let [doc (:service-document (<! ch))]
        (swap! links #(merge % (dissoc (:links doc) :self)))
        (swap! forms #(merge % (:forms doc)))
        (recur)))))

(defn sign-in-loop [owner]
  (bus/listen-on owner :sign-in
    (fn [{:keys [username password]}]
      (io/json-post
        {:url (str (:auth services) "/token")
         :data
         {:grant_type "password"
          :username username
          :password password}
         :on-complete
         (fn [resp]
           (storage/set-item session-storage "token" (resp "access_token"))
           (bus/publish! owner :signed-in {:username username}))}))))

(defn sign-out! [owner]
  (storage/remove-item session-storage "token")
  (bus/publish! owner :signed-out {}))

(defn sign-out-loop [owner]
  (bus/listen-on owner :sign-out #(sign-out! owner)))

(defn- get-token []
  (storage/get-item session-storage "token"))

(defn load!
  "Fetches uri and publishes the result under :loaded."
  [owner uri loaded-topic]
  {:pre [owner uri loaded-topic]}
  (-> {:url uri :on-complete #(bus/publish! owner loaded-topic %)}
      (assoc-when :token (get-token))
      (io/get-xhr)))

(defn resolv-uri
  "Tries to resolv the uri of the link relation using links from all
  service documents. Returns nil if not found."
  [owner link-rel]
  (:href (link-rel @(om/get-shared owner :links))))

(defn load-loop
  "Listens on :load and :service-document-loaded topics. Tries to load
  documents from the messages :link-rel. Spools messages with unresolvable
  link relations and tries to resolv them after a new service document was
  loaded."
  [owner]
  (bus/listen-on-mult owner
    {:load
     (fn [unresolvables {:keys [link-rel loaded-topic] :as msg}]
       (if link-rel
         (if-let [uri (resolv-uri owner link-rel)]
           (do (load! owner uri loaded-topic) unresolvables)
           (conj unresolvables msg))
         unresolvables))
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

(defn post-loop [owner]
  (bus/listen-on owner :post
    (fnk [action params result-topic]
      (-> {:url action :data params
           :on-complete (fn [result] (bus/publish! owner result-topic result))}
          (assoc-when :token (get-token))
          (io/post-form)))))

(defn validate-token
  "Executed once at page load. Looks up a token in session storage and
  introspects it at the auth service. In case the token is still active, shows
  the current signed in user. Clears the token otherwise."
  [owner]
  (when-let [token (get-token)]
    (io/json-post
      {:url (str (:auth services) "/introspect")
       :data {:token token}
       :on-complete
       (fn [resp]
         (if (resp "active")
           (bus/publish! owner :signed-in {:username (resp "username")})
           (sign-out! owner)))})))

(defcomponent pages [pages]
  (render [_]
    (om/build workbooks (:workbooks pages))
    #_(om/build workbook (:workbook app))))

(defcomponent app [app owner]
  (will-mount [_]
    (history-loop app)
    (sign-in-loop owner)
    (sign-out-loop owner)
    (service-documents-loop owner)
    (load-loop owner)
    (post-loop owner)
    (load-count-loop (om/get-shared owner :count-load-ch))
    (validate-token owner)
    (load-all-service-documents (bus/publisher owner)))
  (will-unmount [_]
    (async/close! (om/get-shared owner :count-load-ch)))
  (render [_]
    (d/div
      (om/build item-dialog (:item-dialog app))
      (om/build navbar (:navbar app))
      (om/build pages (:pages app)))))

(om/root app app-state
  {:target (dom/getElement "app")
   :shared {:count-load-ch (chan)
            :item-dialog-ch (chan)
            :event-bus (bus/init-bus)
            :links (atom {})
            :forms (atom {})}
   :tx-listen
   (fn [{:keys [path old-value new-value tag]} _]
     (when (= :query tag)
       (println "TX at" path ": " old-value "->" new-value)))})
