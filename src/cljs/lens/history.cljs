(ns lens.history
  (:require-macros [cljs.core.async.macros :refer [go-loop]]
                   [plumbing.core :refer [when-letk fnk]])
  (:require [lens.util :as util]
            [bidi.bidi :as bidi]
            [lens.handler :as handler]
            [lens.event-bus :as bus])
  (:import goog.History
           goog.history.Html5History
           goog.history.EventType))

(def ^:private routes
  ["/"
   {"" :index
    "studies"
    {"" :study-list
     ["/" :id] :study}
    ["w/" :id] :workbook}])

(defn- create-history []
  (doto (Html5History.)
    (.setUseFragment false)
    (.setPathPrefix "")))

(defn history-loop
  "Listens on :route expecting maps with :handler and :params where :params is
  a map from param to value."
  [app-state owner]
  (let [history (create-history)
        nav (util/listen history EventType.NAVIGATE)]
    (go-loop []
      (when-let [token (.-token (<! nav))]
        (when-letk [[handler & more] (bidi/match-route routes token)]
          (let [req {:app-state app-state
                     :owner owner
                     :params (or (:route-params more) {})}]
            (println "execute handler" handler "with" (:params req))
            ((handler/handlers handler) req)))
        (recur)))
    (bus/listen-on owner :route
      (fnk [handler {params {}} :as m]
        (println "route request to" handler "with" params)
        (when-let [match (bidi/unmatch-pair routes m)]
          (println "set token" match)
          (.setToken history match))))
    (.setEnabled history true)))
