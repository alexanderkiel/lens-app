(ns lens.scroll-list
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [om.core :as om]
            [om.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]
            [cljs.core.async :as async :refer [put! chan alts!]]
            [lens.fa :as fa]))

(defn start-scroll-kw [dir]
  (keyword (str "start-scroll-" (name dir))))

(defn render-scroll-list-pad
  "Renders an end pad of a scroll-list.

    scroll-ch - a channel onto which either :start-scroll-up,
                :start-scroll-down or :stop-scroll is put

    dir       - the direction of the pad - either :up or :down"
  [scroll-ch dir]
  (d/div {:class "scroll-list-scroll-pad"
          :on-mouse-down #(put! scroll-ch (start-scroll-kw dir))
          :on-mouse-up #(put! scroll-ch :stop-scroll)
          :ref (str "scroll-pad-" (name dir))}
    (fa/span (fa/join :chevron dir))))

(defn scroll-up [_]
  #(max 0 (- % 50)))

(defn scroll-down [owner]
  #(min (.-scrollHeight (om/get-node owner "scroll-view")) (+ % 50)))

(defn render-scroll-list
  "Renders a scrolling list."
  [list scroll-ch opts content]
  (dom/div (clj->js (merge-with (fn [res v] (str res " " v))
                                {:className "scroll-list"} opts))
    (render-scroll-list-pad scroll-ch :up)
    (d/div {:class "scroll-list-hidden"}
      (apply d/div {:class "scroll-list-scroll"
                    :ref "scroll-view"
                    :on-scroll #(om/update! list :scroll-top
                                            (.. % -target -scrollTop))}
        content))
    (render-scroll-list-pad scroll-ch :down)))

(defn scroll-top [owner]
  (let [scroll-view (om/get-node owner "scroll-view")]
    (.-scrollTop scroll-view)))

(defn update-scroll-top! [owner f]
  (let [scroll-view (om/get-node owner "scroll-view")]
    (set! (.-scrollTop scroll-view) (f (.-scrollTop scroll-view)))))

(defn scroll-height-left [owner]
  (let [scroll-view (om/get-node owner "scroll-view")]
    (- (.-scrollHeight scroll-view) (.-scrollTop scroll-view)
       (.-clientHeight scroll-view))))

(defn scroll-loop [list owner]
  (let [scroll-ch (om/get-state owner :scroll-ch)]
    (go-loop [timeout (chan)
              dir nil]
      (let [[cmd ch] (alts! [scroll-ch timeout] :priority true)]
        (condp = ch
          scroll-ch
          (condp = cmd
            :start-scroll-up (recur (async/timeout 100) scroll-up)
            :start-scroll-down (recur (async/timeout 100) scroll-down)
            :stop-scroll (recur (chan) nil))
          timeout
          (if (or (and (= dir scroll-up) (= 0 (scroll-top owner)))
                  (and (= dir scroll-down) (= 0 (scroll-height-left owner))))
            (recur (chan) nil)
            (do
              (update-scroll-top! owner (dir owner))
              (recur (async/timeout 100) dir))))))))

(defn set-scroll-pad-up-state! [owner]
  (set! (.-className (om/get-node owner "scroll-pad-up"))
        (if (= 0 (scroll-top owner))
          "scroll-list-scroll-pad disabled"
          "scroll-list-scroll-pad")))

(defn set-scroll-pad-down-state! [owner]
  (set! (.-className (om/get-node owner "scroll-pad-down"))
        (if (= 0 (scroll-height-left owner))
          "scroll-list-scroll-pad disabled"
          "scroll-list-scroll-pad")))

(defn set-scroll-pad-state! [owner]
  (set-scroll-pad-up-state! owner)
  (set-scroll-pad-down-state! owner))
