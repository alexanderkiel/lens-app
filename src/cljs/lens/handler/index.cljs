(ns lens.handler.index
  "Index Page Handler

  Activates the workbook list."
  (:require-macros [plumbing.core :refer [defnk]])
  (:require [om.core :as om]))

(defnk index [app-state]
  (om/transact! app-state #(-> (dissoc % :workbook)
                               (assoc-in [:workbooks :active] true))))
