(defproject lens-app "0.1-SNAPSHOT"
  :description "Lens is a tool for online analytical data processing in medical studies."
  :url "https://github.com/alexanderkiel/lens-app"

  :min-lein-version "2.0.0"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/clojurescript "0.0-2760"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [http-kit "2.1.16"]
                 [ring/ring-core "1.3.2"]
                 [bidi "1.18.10" :exclusions [ring/ring-core]]
                 [compojure "1.3.3"]
                 [ring-middleware-format "0.5.0"
                  :exclusions [org.clojure/tools.reader
                               ring ring/ring-core
                               commons-codec]]
                 [org.omcljs/om "0.8.8" :scope "provided"]
                 [prismatic/om-tools "0.3.11" :scope "provided"]
                 [cljsjs/dimple "2.1.2-0" :scope "provided"]
                 [com.andrewmcveigh/cljs-time "0.1.4"]
                 [com.cognitect/transit-cljs "0.8.207"]
                 [hodgepodge "0.1.3"]
                 [org.slf4j/slf4j-api "1.7.7"]
                 [ch.qos.logback/logback-classic "1.1.2"]]

  :profiles {:dev
             {:source-paths ["dev"]
              :dependencies [[org.clojure/tools.namespace "0.2.4"]]
              :global-vars {*print-length* 20}

              :cljsbuild
              {:builds [{:source-paths ["src/cljs"]
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
                          :pretty-print false}}]}}

             :production-run
             {:main lens.core
              :jvm-opts ["-Xmx4g"]

              :cljsbuild
              {:builds []}}}

  :plugins [[lein-cljsbuild "1.0.5"]]

  :source-paths ["src/clj" "src/cljs"]
  :resource-paths ["resources"]
  :clean-targets ^{:protect false} ["target" "out" "repl" "resources/public/js"]

  :repl-options {:welcome (do
                            (println "   Docs: (doc function-name-here)")
                            (println "         (find-doc \"part-of-name-here\")")
                            (println "   Exit: Control+D or (exit) or (quit)")
                            (println "  Start: (startup)")
                            (println "Restart: (reset)"))})
