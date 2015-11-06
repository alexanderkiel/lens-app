(ns lens.handler.workbook
  "Single Workbook Page Handler

  Expects an (workbook) id in params. Publishes a query to lead the workbook
  with the loaded topic :loaded-workbook."
  (:require-macros [plumbing.core :refer [defnk]])
  (:require [lens.event-bus :as bus]
            [om.core :as om]))

(defn find-wb-query [id]
  {:form-rel :lens/find-workbook
   :params {:id id}
   :target :loaded-workbook})

(defnk workbook [app-state owner [:params id]]
  (bus/publish! owner :query (find-wb-query id))
  (om/update! app-state [:pages :active-page] :workbook))
