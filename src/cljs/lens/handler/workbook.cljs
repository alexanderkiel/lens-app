(ns lens.handler.workbook
  "Single Workbook Page Handler

  Expects an (workbook) id in params. Publishes a query to lead the workbook
  with the loaded topic :loaded-workbook."
  (:require-macros [plumbing.core :refer [defnk]])
  (:require [lens.event-bus :as bus]))

(defn find-wb-query [id]
  {:form-rel :lens/find-workbook
   :params {:id id}
   :loaded-topic :loaded-workbook})

(defnk workbook [owner [:params id]]
  (bus/publish! owner :query (find-wb-query id)))
