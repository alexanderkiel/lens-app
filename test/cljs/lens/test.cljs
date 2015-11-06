(ns lens.test
  (:require [cljs.test :refer-macros [run-tests]]
            [lens.util-test]))

(enable-console-print!)

(defn ^:export run []
  (.log js/console "Tests started.")
  (run-tests 'lens.util-test)
  0)
