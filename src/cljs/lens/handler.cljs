(ns lens.handler
  "Routing Handler

  All handlers are called with a map of :app-state, :owner and :params."
  (:require-macros [plumbing.core :refer [defnk]])
  (:require [om.core :as om]
            [lens.event-bus :as bus]))

(defnk index [app-state]
  (om/transact! app-state #(-> (dissoc % :workbook)
                               (assoc-in [:workbooks :active] true))))

(defnk find-wb-query [id]
  {:form-rel :lens/find-workbook
   :params {:id id}
   :loaded-topic :loaded-workbook})

(defnk workbook [owner params]
  (bus/publish! owner :query (find-wb-query params)))

(def handlers
  {:index index
   :workbook workbook})
