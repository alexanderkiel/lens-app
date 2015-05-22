(ns lens.io
  (:require-macros [plumbing.core :refer [defnk]])
  (:require [plumbing.core :refer [assoc-when]]
            [clojure.string :as str]
            [clojure.walk]
            [cljs.reader :as reader]
            [goog.events :as events]
            [cognitect.transit :as transit])
  (:import [goog Uri]
           [goog.net XhrIo EventType]))

(defn resolve-uri [base-uri uri]
  (->> (.parse Uri uri) (.resolve base-uri) (.toString)))

(defn resolve-uri-in-form
  "Resolves relative URIs in :href and :action values of form using base-uri."
  [base-uri form]
  (cond
    (:href form) (update-in form [:href] #(resolve-uri base-uri %))
    (:action form) (update-in form [:action] #(resolve-uri base-uri %))
    :else form))

(defn resolve-uris
  "Resolves relative URIs in all :href and :action values of doc using
  base-uri."
  [base-uri doc]
  (clojure.walk/postwalk #(resolve-uri-in-form base-uri %) doc))

(defn response-handler [xhr url on-complete]
  {:pre [xhr url on-complete]}
  (fn [_]
    (->> (.getResponseText xhr)
         (reader/read-string)
         (resolve-uris (.parse Uri url))
         (on-complete))))

(defn- assoc-authorization [m token]
  (assoc-when m "Authorization" (some->> token (str "Bearer "))))

(defnk get-xhr [url on-complete :as req]
  (let [xhr (XhrIo.)]
    (events/listen xhr goog.net.EventType.COMPLETE
                   (response-handler xhr url on-complete))
    (. xhr
      (send url "GET" nil (clj->js (-> {"Accept" "application/edn"}
                                       (assoc-authorization (:token req))))))))

(defn url-encode [s]
  (some-> s str (js/encodeURIComponent) (.replace "+" "%20")))

(defn pr-form-data [data]
  (->> (for [[k v] data :when k]
         (str/join "=" [(name k) (url-encode v)]))
       (str/join "&")))

(defnk post-form [url data on-complete :as req]
  (let [xhr (XhrIo.)]
    (events/listen xhr goog.net.EventType.COMPLETE
                   (response-handler xhr url on-complete))
    (. xhr
      (send url "POST" (pr-form-data data)
            (clj->js (-> {"Accept" "application/edn"
                          "Content-Type" "application/x-www-form-urlencoded"}
                         (assoc-authorization (:token req))))))))

(defn get-form [{:keys [url data on-complete]}]
  (let [xhr (XhrIo.)]
    (events/listen xhr goog.net.EventType.COMPLETE
                   (response-handler xhr url on-complete))
    (. xhr
      (send (str url "?" (pr-form-data data)) "GET" nil
        #js {"Accept" "application/edn"}))))

(defn form [{:keys [method] :as m}]
  (if (= "GET" method)
    (get-form m)
    (post-form m)))

(def json-reader (transit/reader :json))

(defn json-response-handler [xhr on-complete]
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
