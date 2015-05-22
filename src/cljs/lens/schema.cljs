(ns lens.schema
  "Schema Definitions"
  (:require [schema.core :as s :refer-macros [defschema] :include-macros true]))

(defschema ChildSpec
  "A child spec consists of the :link which to follow to obtain the childs and
  the :rel under which the childs can be found in :embedded of the returned
  resource."
  {:link s/Str
   :rel s/Str})

(defschema Term
  {:id s/Str
   :type s/Keyword
   (s/optional-key :links) s/Any
   (s/optional-key :childs) ChildSpec
   (s/optional-key :search-childs) s/Any})

(s/defn has-code-list? [term :- Term]
  (-> term :links :lens/code-list))

(s/defn is-numeric? [term :- Term]
  (= :number (:value-type term)))
