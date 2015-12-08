(ns lens.test
  (:require [cljs.test :refer-macros [run-all-tests]]))

(enable-console-print!)

(defn ^:export run []
  (.log js/console "Tests started.")
  (run-all-tests #"lens.*-test"))
