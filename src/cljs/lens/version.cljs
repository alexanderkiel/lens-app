(ns lens.version
  (:require [cljs.reader :as reader]))

(defn assoc-idx [idx x]
  (assoc x :idx idx))

(defn index-query [idx query]
  (-> (update-in query [:query-grid :cols] #(vec (map-indexed assoc-idx %)))
      (assoc :idx idx)))

(defn resolve-code-list-item-ids [query]
  (update-in
    query [:query-grid :cols]
    (fn [cols]
      (mapv
        (fn [col]
          (update-in
            col [:cells]
            (fn [cells]
              (mapv
                (fn [{:keys [type] :as cell}]
                  (if (= :code-list-item type)
                    (update-in cell [:id] (fn [id] (reader/read-string id)))
                    cell))
                cells))))
        cols))))

(defn assoc-default-results [query]
  (assoc query :vc-by-se-result {} :vc-by-ad-and-sex-result {}))

(defn- prepare-queries [queries]
  (->> (map-indexed index-query queries)
       (map resolve-code-list-item-ids)
       (mapv assoc-default-results)))

(defn api->app-state
  "Converts a version from the API representation into the app-state
  representation."
  [version]
  {:queries (prepare-queries (:queries (:data version)))
   :forms (:forms version)
   :open-txs #queue []})

