(defproject route-params "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.9.0-alpha13"]
                 [org.clojure/clojurescript "1.9.229"]
                 [org.omcljs/om "1.0.0-alpha47"]
                 [org.clojure/core.async "0.2.374"
                  :exclusions [org.clojure/tools.reader]]
                 [org.omcljs/om "1.0.0-alpha47"]
                 [compassus "1.0.0-alpha2"]
                 [bidi "2.0.9"]
                 [kibu/pushy "0.3.6"]
                 [com.cemerick/url "0.1.1"]
                 [compojure "1.5.0"]
                 ]

  :source-paths ["src"]

  :profiles
  {:dev
   {:dependencies  [[figwheel-sidecar "0.5.8"]
                    [com.cemerick/piggieback "0.2.1"]
                    [org.clojure/tools.nrepl "0.2.10"]]
    :source-paths ["dev"]
    :repl-options  {:nrepl-middleware  [cemerick.piggieback/wrap-cljs-repl]}}}

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]
)
