(ns lens.handler.index
  "Index Page Handler

  Does nothing."
  (:require-macros [plumbing.core :refer [defnk]])
  (:require [om.core :as om]
            [lens.navbar :as nav]))

(defnk index [app-state]
  (println 'index-handler)
  (om/transact! app-state #(-> (assoc-in % [:pages :active-page] :index)
                               (nav/nav-item-activator :index))))
