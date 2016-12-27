(ns user
  (:require
   [figwheel-sidecar.repl :as r]
   [figwheel-sidecar.repl-api :as ra]
   ))


(defn start
  []
  (ra/start-figwheel!
  {:figwheel-options
   {:ring-handler 'server.handler/app}
   :build-ids ["dev"]
   :all-builds
   [{:id "dev"
     :figwheel true
     :source-paths ["src"]
     :compiler {:main 'route-params.core
                :asset-path "/js/compiled"
                :output-to "resources/public/js/compiled/route_params.js"
                :output-dir "resources/public/js/compiled"
                :verbose true}}]})
  )

(comment
 (ra/fig-status)
 )



