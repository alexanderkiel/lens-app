(ns lens.handler.study
  "Study Page Handler

  Shows a list of all forms of a particular study."
  (:require-macros [plumbing.core :refer [defnk]])
  (:require [om.core :as om]
            [lens.navbar :as nav]))

(defnk study [app-state [:params id]]
  (println 'study-handler id)
  (om/transact! app-state #(-> (assoc-in % [:pages :active-page] :study)
                               (assoc-in [:pages :pages :study :active-study] id)
                               (nav/nav-item-activator :study-list))))
