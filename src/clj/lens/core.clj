(ns lens.core
  (:require [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [org.httpkit.server :refer [run-server]]
            [lens.app :refer [app]]))

(defn- parse-int [s]
  (Integer/parseInt s))

(def cli-options
  [["-p" "--port PORT" "Listen on this port" :default 8080 :parse-fn parse-int
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-i" "--ip" "The IP to bind" :default "0.0.0.0"]
   ["-t" "--thread" "Number of worker threads" :default 4 :parse-fn parse-int
    :validate [#(< 0 % 64) "Must be a number between 0 and 64"]]
   ["-h" "--help" "Show this help"]])

(defn usage [options-summary]
  (->> ["Usage: lens-app [options]"
        ""
        "Options:"
        options-summary
        ""]
       (str/join "\n")))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join "\n" errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options errors summary]} (cli/parse-opts args cli-options)
        version (System/getProperty "lens.version")]
    (cond
      (:help options)
      (exit 0 (usage summary))

      errors
      (exit 1 (error-msg errors)))

    (run-server (app false)
                (merge {:worker-name-prefix "http-kit-worker-"} options))
    (println "Server started")
    (println "Listen at" (str (:ip options) ":" (:port options)))
    (println "Using" (:thread options) "worker threads")))
