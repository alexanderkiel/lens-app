(ns lens.util
  (:require [clojure.string :as str]
            [cljs.core.async :refer [chan put!]]
            [plumbing.core :refer [assoc-when]]
            [goog.events :as events]
            [schema.core :as s :include-macros true]
            [om-tools.dom :as d :include-macros true]))

(defn add-soft-hyphen
  "Adds U+00AD soft hyphens after certain chars in string in order to
  facilitate hyphenation."
  [s]
  (when s (str/replace s "/" "/\u00AD")))

(defn prepend-ns [ns kw]
  (keyword ns (name kw)))

(defn insert-by
  "Inserts x into coll sorted by fn."
  [fn coll x]
  (let [pred #(neg? (compare (fn %) (fn x)))]
    (into (vec (take-while pred coll)) (cons x (drop-while pred coll)))))

(s/defn index :- [{:idx s/Int s/Any s/Any}]
  "Indices maps in coll under :idx starting at zero."
  [coll :- [{s/Any s/Any}]]
  (map-indexed (fn [i x] (assoc x :idx i)) coll))

(defn duplicate-at
  "Returns a function which takes a coll and duplicates the element at idx."
  [idx]
  (fn [coll] (concat (take (inc idx) coll) (drop idx coll))))

(defn remove-at
  "Returns a function which takes a coll and removes the element at idx."
  [idx]
  (fn [coll] (concat (take idx coll) (drop (inc idx) coll))))

(defn disable-when [m pred]
  (assoc-when m :disabled (when pred "disabled")))

(defn render-multi-line-text [text]
  (map #(d/p (str/trim %)) (str/split text #"\n")))

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
  (when-not (= title (.-title js/document))
    (set! (.-title js/document) title)))
