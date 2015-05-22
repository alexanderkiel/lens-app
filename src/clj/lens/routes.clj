(ns lens.routes
  (:require [compojure.core :as compojure :refer [GET]]
            [compojure.route :as route]
            [ring.util.response :refer [file-response]]))

(defn index-html [dev]
  (if dev
    "public/html/index-dev.html"
    "public/html/index.html"))

;; ---- Routes ----------------------------------------------------------------

(defn routes [dev]
  (compojure/routes
    (GET "/" []
      (file-response (index-html dev) {:root "resources"}))

    (GET "/workbooks" []
      (file-response (index-html dev) {:root "resources"}))

    (GET "/workbooks/:id" []
      (file-response (index-html dev) {:root "resources"}))

    (route/files "/" {:root "resources/public"})))
