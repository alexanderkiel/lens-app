(ns lens.handler.study-list
  "Study List Page Handler

  Shows a list of all studies."
  (:require-macros [plumbing.core :refer [defnk]])
  (:require [om.core :as om]
            [lens.navbar :as nav]
            [lens.event-bus :as bus]))

(defnk study-list [app-state owner]
  (println 'study-list-handler)
  (bus/publish! owner :query {:query-rel :lens/all-studies
                              :params {:pull-pattern [{:data [:name :desc]}]}
                              :target :lens.page.study-list/loaded})
  (om/transact! app-state #(-> (assoc-in % [:pages :active-page] :study-list)
                               (nav/nav-item-activator :study-list))))
