(defproject lens-app "0.1-SNAPSHOT"
  :description "Lens is a tool for online analytical data processing in medical studies."
  :url "https://github.com/alexanderkiel/lens-app"
  :license {:name "Eclipse"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.0.0"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.7.228"]
                 [org.clojure/core.async "0.2.374"
                  :exclusions [org.clojure/tools.reader]]
                 [bidi "1.25.0"]
                 [org.omcljs/om "0.9.0"]
                 [prismatic/om-tools "0.3.12"]
                 [prismatic/plumbing "0.5.2"]
                 [prismatic/schema "1.0.4"]
                 [cljsjs/dimple "2.1.2-0"]
                 [com.andrewmcveigh/cljs-time "0.3.14"]
                 [hodgepodge "0.1.3"]
                 [http-kit "2.1.19"]
                 [org.clojars.akiel/hap-client-clj "0.4"
                  :exclusions [com.cognitect/transit-clj]]
                 [org.clojars.akiel/async-error "0.1"]]

  :plugins [[lein-cljsbuild "1.1.2"]]

  :profiles {:dev
             {:source-paths ["dev" "script"]
              :dependencies [[figwheel-sidecar "0.5.0-5"
                              :exclusions [org.clojure/tools.reader
                                           ring/ring-core
                                           commons-fileupload
                                           clj-time]]]}

             :production
             {:cljsbuild
              {:builds [{:source-paths ["src/cljs-testable" "src/cljs"]
                         :compiler
                         {:output-to "resources/public/js/compiled/main.js"
                          :main lens.core
                          :optimizations :advanced
                          :pretty-print false
                          :parallel-build true}}]}}}

  :source-paths ["src/clj"]
  :resource-paths ["resources"]
  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"])
