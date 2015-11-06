(ns lens.handler
  "Routing Handlers

  All handlers are called with a map of :app-state, :owner and :params."
  (:require-macros [plumbing.core :refer [defnk]])
  (:require [lens.handler.index :refer [index]]
            [lens.handler.workbook :refer [workbook]]))

(def handlers
  {:index index
   :workbook workbook})
