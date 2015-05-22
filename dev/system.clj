(ns system
  (:require [clojure.string :as str]
            [lens.app :refer [app]]
            [org.httpkit.server :refer [run-server]]))

(defn env []
  (->> (str/split-lines (slurp ".env"))
       (reduce (fn [ret line]
                 (let [vs (str/split line #"=")]
                   (assoc ret (first vs) (str/join "=" (rest vs))))) {})))

(defn system [env]
  {:app app
   :version (System/getProperty "lens.version")
   :port (or (env "PORT") 5000)})

(defn start [{:keys [app port] :as system}]
  (let [stop-fn (run-server (app true)
                            {:port port})]
    (assoc system :stop-fn stop-fn)))

(defn stop [{:keys [stop-fn] :as system}]
  (stop-fn)
  (dissoc system :stop-fn))
