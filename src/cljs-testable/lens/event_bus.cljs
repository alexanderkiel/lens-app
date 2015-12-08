(ns lens.event-bus
  "Global Events:

  :load - load stuff
    {(s/optional-key :uri) hap/Uri
     (s/optional-key :link-rel) s/Keyword
     :loaded-topic s/Keyword}

  :query - query stuff
    {(s/optional-key :uri) hap/Uri
     (s/optional-key :query-rel) s/Keyword
     :params hap/Args
     :target Target}

  :post - create stuff
    {:uri hap/Uri
     :params hap/Args
     :result-topic s/Keyword}

  :put - update stuff
    {:}

  :route - go to handler
    {:handler :index}
    {:handler :workbook :params {:id ...}}

  :sign-in - user signs in
    {:username Str
     :password Str}

  :signed-in - user signed in
    {:username Str}

  :sign-out - user signs out
    {}

  :signed-out - user signed out
    {}

  :undo - undo action
    {}

  :undo-enabled - set enabled state of the undo menu item
    true
    false

  :query - top level queries available through service document forms"
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :as async :refer [<!]]
            [om.core :as om]
            [schema.core :as s :include-macros true]))

(def Topic
  "A topic in the global event bus."
  s/Keyword)

(defn publisher [owner]
  (:publisher (om/get-shared owner :event-bus)))

(defn publication [owner]
  (:publication (om/get-shared owner :event-bus)))

(defn init-bus []
  (let [publisher (async/chan)]
    {:publisher publisher
     :publication (async/pub publisher :topic)}))

(defn register-for-unlisten [owner topic ch]
  (om/update-state! owner ::subs #(conj % [topic ch])))

(s/defn listen-on
  "Listens on a topic of the publication. Calls the callback with the message."
  [owner topic :- Topic callback]
  (let [ch (async/chan)]
    (register-for-unlisten owner topic ch)
    (async/sub (publication owner) topic ch)
    (go-loop []
      (when-let [{:keys [msg]} (<! ch)]
        (callback msg)
        (recur)))))

(defn listen-on-mult
  "Listens on multiple topics of the publication.

  Callbacks are reduce functions over one single immediate result starting with
  start. Callbacks are supplied in a map from topic to callback.

  Example:

  Listens on :topic-a and :topic-b. Does something with messages of :topic-a
  incrementing a counter every time. Resets the counter on every occurence of
  arbitary messages of :topic-b.

  (listen-on-mult owner
    {:topic-a
     (fn [count msg]
       (do-something! msg)
       (inc count))
     :topic-b
     (fn [_ _]
       0)}
    0)"
  [owner topic-callback-map start]
  (let [ch (async/chan)]
    (doseq [[topic] topic-callback-map]
      (register-for-unlisten owner topic ch)
      (async/sub (publication owner) topic ch))
    (go-loop [result start]
      (when-let [{:keys [topic msg]} (<! ch)]
        (recur ((topic-callback-map topic) result msg))))))

(defn unlisten-all [owner]
  (doseq [[topic ch] (om/get-state owner ::subs)]
    (async/unsub (publication owner) topic ch)
    (async/close! ch)))

(s/defn publish! [owner topic :- Topic msg]
  (async/put! (publisher owner) {:topic topic :msg msg}))
