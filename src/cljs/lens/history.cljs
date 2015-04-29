(ns lens.history
  (:require-macros [cljs.core.async.macros :refer [go-loop]]
                   [plumbing.core :refer [when-letk]])
  (:require [lens.util :as util]
            [bidi.bidi :as bidi]
            [lens.handler :as handler]
            [lens.event-bus :as bus])
  (:import goog.History
           goog.history.Html5History
           goog.history.EventType))

(def routes
  ["/" {"" :index
        ["w/" :id] :workbook}])

(defn create-history []
  (doto (Html5History.)
    (.setUseFragment false)
    (.setPathPrefix "")))

(defn loop
  "Listens on :route expecting maps with :handler and :params where :params is
  a map from param to value."
  [app owner]
  (let [history (create-history)
        nav (util/listen history EventType/NAVIGATE)]
    (go-loop []
      (when-let [token (.-token (<! nav))]
        (when-letk [[handler & more] (bidi/match-route routes token)]
          (let [req {:app app :owner owner :params (:route-params more)}]
            ((handler/handlers handler) req)))
        (recur)))
    (bus/listen-on owner :route
      (fn [m]
        (when-let [match (bidi/unmatch-pair routes m)]
          (.setToken history match))))
    (.setEnabled history true)))
