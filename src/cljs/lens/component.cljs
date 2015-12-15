(ns lens.component
  (:require-macros [cljs.core.async.macros :refer [go-loop]]
                   [lens.macros :refer [h]])
  (:require [cljs.core.async :as async :refer [put! chan <! >! alts!]]
            [om.core :as om]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as d :include-macros true]
            [lens.fa :as fa]
            [lens.util :as util]))

(defcomponent typeahead-search-field
  "Renders a standalone typeahead search field.

  The search field collects the chars typed and executes a search after
  :timeout. It than waits for the search result being available and puts it
  onto :result-ch if no other chars were typed in the meantime. This behaviour
  ensures that the query always matches the result.

  The query string typed into the field is associated to the state under
  :query. Blanking the search field is possible by removing the query key from
  state.

  The following options are available:

  :search      - a function which takes two arguments, the supplied state and
                 the query string and returns a channel conveying the result.
                 The function has to return the channel immediately.

  :result-ch   - a channel onto which all valid results are put. Valid results
                 are described above.

  :enabled?    - a function which takes the supplied state and returns true if
                 the search field should be enabled. Optional. Defaults to
                 true.

  :timeout     - the time after which a search is executed. Optional. Defaults
                 to 350 ms.

  :placeholder - the placeholder of the input field. Optional. Defaults to
                 \"Search\"."
  [state owner {:keys [search result-ch enabled? timeout placeholder]
                :or {enabled? (constantly true)
                     timeout 350
                     placeholder "Search"}}]
  (init-state [_]
    {:query-ch (chan)
     :clear-ch (chan)})
  (will-mount [_]
    (let [query-ch (om/get-state owner :query-ch)]
      (go-loop [query nil
                tmp-result-ch nil]
        (let [timeout-ch (when (and query search) (async/timeout timeout))
              [v ch] (alts! (remove nil? [query-ch timeout-ch tmp-result-ch])
                            :priority true)]
          (condp = ch
            query-ch
            (when v
              (om/update! state :query v)
              (recur v nil))
            timeout-ch
            (recur nil (search @state query))
            tmp-result-ch
            (do
              (when result-ch (>! result-ch v))
              (recur query nil)))))))
  (did-mount [_]
    (let [clear-ch (om/get-state owner :clear-ch)
          query-ch (om/get-state owner :query-ch)
          node (om/get-node owner "input")]
      (go-loop []
        (when (<! clear-ch)
          (>! query-ch "")
          (.focus node)
          (recur)))))
  (will-unmount [_]
    (async/close! (om/get-state owner :query-ch))
    (async/close! (om/get-state owner :clear-ch)))
  (render-state [_ {:keys [query-ch clear-ch]}]
    (d/div {:class "input-group"}
      (d/input {:type "search"
                :ref "input"
                :class "form-control"
                :placeholder placeholder
                :value (or (:query state) "")
                :disabled (not (enabled? state))
                :on-change #(when-let [query (util/target-value %)]
                             (put! query-ch query))})
      (d/span {:class "input-group-btn"}
        (d/button {:class "btn btn-default" :type "button"
                   :on-click (h (put! clear-ch :clear))}
          (fa/span :times))))))
