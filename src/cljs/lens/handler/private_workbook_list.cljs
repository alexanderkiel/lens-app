(ns lens.handler.private-workbook-list
  "Private Workbook List Page Handler

  Displays the list of all workbooks a user owns."
  (:require-macros [plumbing.core :refer [defnk]])
  (:require [om.core :as om]))

(defnk private-workbook-list [app-state]
  (om/update! app-state [:pages :active-page] :private-workbook-list))
