(require '[figwheel-sidecar.system :as sys])
(require '[com.stuartsierra.component :as component])
(require '[lens.routes :refer [routes]])

(def system
  (component/system-map
    :figwheel-system
    (sys/figwheel-system
      {:figwheel-options
       {:css-dirs ["resources/public/css"]
        :ring-handler routes}
       :build-ids ["dev"]
       :all-builds
       [{:id "dev"
         :figwheel true #_{:on-jsload "lens.core/on-js-reload"}
         :source-paths ["src/cljs-testable" "src/cljs"]
         :compiler {:main 'lens.core
                    :asset-path "/js/compiled/out"
                    :output-to "resources/public/js/compiled/main.js"
                    :output-dir "resources/public/js/compiled/out"
                    :source-map-timestamp true}}]})))

(alter-var-root #'system component/start)

(sys/cljs-repl (:figwheel-system system))
