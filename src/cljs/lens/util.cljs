(ns lens.util
  (:require [clojure.string :as str]
            [cljs.core.async :refer [chan put!]]
            [goog.events :as events]))

(defn add-soft-hyphen
  "Adds U+00AD soft hyphens after certain chars in string in order to
  facilitate hyphenation."
  [s]
  (str/replace s "/" "/\u00AD"))

(defn prepend-ns [ns kw]
  (keyword ns (name kw)))

(defn insert-by [fn coll x]
  (let [pred #(neg? (compare (fn %) (fn x)))]
    (into (vec (take-while pred coll)) (cons x (drop-while pred coll)))))

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
