(ns lens.routes
  (:require [compojure.core :as compojure :refer [GET]]
            [compojure.route :as route]
            [ring.util.response :refer [file-response]]))

(def routes
  (compojure/routes

    (route/files "/css" {:root "resources/public"})
    (route/files "/fonts" {:root "resources/public"})
    (route/files "/js" {:root "resources/public"})

    (GET "/*" []
      (file-response "public/index-dev.html" {:root "resources"}))))
