(ns codescene-enterprise-pm-jira.routes.app
  "Top-level routes for application.
  This includes home-page, basic error handling, and reference to api routes.
  More fain-grained routes can be found in other routes.* namespaces."
  (:require [codescene-enterprise-pm-jira.routes
             [api :refer [api-status-handler project-handler]]
             [status-page :refer [status-page]]
             [sync :refer [sync-handler]]]
            [compojure
             [core :refer [GET POST routes]]
             [route :as route]]))

(defn app-routes [config]
  (routes
   (GET "/" [message error]
     (status-page config message error))

   (GET "/api/1/status" []
     (api-status-handler))

   (GET "/api/1/projects/:project-id" [project-id]
     (project-handler project-id))

   (POST "/sync/force" [project-key]
     (sync-handler config project-key))

   (route/not-found "Not Found")))
