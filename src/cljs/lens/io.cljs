(ns lens.io
  (:require [clojure.string :as str]
            [goog.events :as events]
            [cognitect.transit :as transit])
  (:import [goog.net XhrIo EventType]))

(defn- url-encode [s]
  (some-> s str (js/encodeURIComponent) (.replace "+" "%20")))

(defn- pr-form-data [data]
  (->> (for [[k v] data :when k]
         (str/join "=" [(name k) (url-encode v)]))
       (str/join "&")))

(def ^:private json-reader (transit/reader :json))

(defn- json-response-handler [xhr on-complete]
  (fn [_]
    (->> (.getResponseText xhr)
         (transit/read json-reader)
         (on-complete))))

(defn json-post [{:keys [url data on-complete]}]
  (let [xhr (XhrIo.)]
    (events/listen xhr goog.net.EventType.COMPLETE
                   (json-response-handler xhr on-complete))
    (. xhr
      (send url "POST" (pr-form-data data)
        #js {"Accept" "application/json"
             "Content-Type" "application/x-www-form-urlencoded"}))))
