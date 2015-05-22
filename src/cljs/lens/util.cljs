(ns lens.util
  (:require [clojure.string :as str]
            [cljs.core.async :refer [chan put!]]
            [goog.events :as events]))

(defn add-soft-hyphen
  "Adds U+00AD soft hyphens after certain chars in string in order to
  facilitate hyphenation."
  [s]
  (str/replace s "/" "/\u00AD"))

;; ---- Events ----------------------------------------------------------------

(defn listen [el type]
  (let [out (chan)]
    (events/listen el type
      (fn [e] (put! out e)))
    out))

(defn target-value [e]
  (.. e -target -value))

;; ---- Document --------------------------------------------------------------

(defn set-title! [title]
  (set! (.-title js/document) title))
