(ns server.handler
  (:require [compojure.core :refer :all]
            [ring.middleware.resource :refer [wrap-resource]]))

(defroutes myroutes
  (GET "*/*" []
    (-> "index.html"
        (ring.util.response/resource-response {:root "public"})
        (ring.util.response/content-type "text/html"))))



(def app (-> myroutes
             (wrap-resource "public")))
