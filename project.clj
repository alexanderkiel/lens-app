(defproject lens-app "0.1-SNAPSHOT"
  :description "Lens is a tool for online analytical data processing in medical studies."
  :url "https://github.com/alexanderkiel/lens-app"
  :license {:name "Eclipse"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.0.0"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.145"]
                 [org.clojure/core.async "0.2.371"]
                 [http-kit "2.1.18"]
                 [ring/ring-core "1.3.2"]
                 [bidi "1.21.0" :exclusions [ring/ring-core]]
                 [compojure "1.3.3"]
                 [org.omcljs/om "0.9.0"]
                 [prismatic/plumbing "0.4.4"]
                 [prismatic/om-tools "0.3.12"]
                 [cljsjs/dimple "2.1.2-0"]
                 [com.andrewmcveigh/cljs-time "0.1.4"]
                 [com.cognitect/transit-cljs "0.8.225"]
                 [hodgepodge "0.1.3"]]

  :profiles {:dev
             {:source-paths ["dev"]
              :dependencies [[org.clojure/tools.namespace "0.2.4"]]
              :global-vars {*print-length* 20}

              :cljsbuild
              {:builds [{:id "dev"
                         :source-paths ["src/cljs"]
                         :figwheel true
                         :compiler
                         {:output-to "resources/public/js/lens-dev.js"
                          :output-dir "resources/public/js/out-dev"
                          :optimizations :none
                          :source-map true}}]}}

             :production
             {:hooks [leiningen.cljsbuild]

              :cljsbuild
              {:builds [{:source-paths ["src/cljs"]
                         :compiler
                         {:output-to "resources/public/js/lens.js"
                          :optimizations :advanced
                          :pretty-print false}}]}}}

  :plugins [[lein-cljsbuild "1.1.0"]
            [lein-figwheel "0.4.1" :exclusions [org.codehaus.plexus/plexus-utils
                                                org.clojure/clojure]]]

  :source-paths ["src/clj" "src/cljs"]
  :resource-paths ["resources"]
  :clean-targets ^{:protect false} ["target" "out" "repl" "resources/public/js"]

  :repl-options {:welcome (do
                            (println "   Docs: (doc function-name-here)")
                            (println "         (find-doc \"part-of-name-here\")")
                            (println "   Exit: Control+D or (exit) or (quit)")
                            (println "  Start: (startup)")
                            (println "Restart: (reset)"))}

  :figwheel {:css-dirs ["resources/public/css"]
             :nrepl-port 7889
             :server-port 5000
             :ring-handler lens.app/app-dev})
