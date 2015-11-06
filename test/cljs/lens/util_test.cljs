(ns lens.util-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [lens.util :refer [index]]))

(deftest index-test
  (is (= [{:idx 0}] (index [{}]))))
