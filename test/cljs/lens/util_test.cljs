(ns lens.util-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [lens.util :refer [index render-multi-line-text]]))

(deftest index-test
  (is (= [{:idx 0}] (index [{}]))))

(deftest render-multi-line-text-test
  (let [ps (render-multi-line-text "a\nb")]
    (is (= 2 (count ps)))
    (is (= "p" ((js->clj (first ps)) "type")))
    (is (= "a" ((((js->clj (first ps)) "_store") "props") "children")))
    (is (= "p" ((js->clj (second ps)) "type")))
    (is (= "b" ((((js->clj (second ps)) "_store") "props") "children")))))
