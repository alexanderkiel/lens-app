(ns lens.auth-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [lens.auth :refer [set-token! assoc-access-token]]))

(deftest assoc-access-token-test
  (testing "Does nothing on empty storage."
    (is (= {} (assoc-access-token {}))))
  (testing "Assocs the stored access token."
    (set-token! "token-151523")
    (is (= {:headers {"Authorization" "Bearer token-151523"}}
           (assoc-access-token {})))))
