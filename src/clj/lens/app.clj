(ns lens.app
  (:require [lens.routes :refer [routes]]))

(defn app [dev]
  (routes dev))

(def app-dev (app true))
