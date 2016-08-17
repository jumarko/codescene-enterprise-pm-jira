(ns codescene-enterprise-pm-jira.handler
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [response]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-response]]
            [taoensso.timbre :as log]
            [codescene-enterprise-pm-jira.db :as db]
            [codescene-enterprise-pm-jira.storage :as storage]
            [codescene-enterprise-pm-jira.project-config :as project-config]
            [codescene-enterprise-pm-jira.jira :as jira]))

(defn fetch-and-save [username password]
  (let [project-config (project-config/read-config)
        issues (jira/find-issues-with-cost username password "timeoriginalestimate")]
    (storage/replace-project (db/persistent-connection) project-config issues)))

(defn- get-project [project-id]
  (storage/get-project (db/persistent-connection) project-id))

(defroutes app-routes
  (GET "/" [username password] (fetch-and-save username password))
  (GET "/api/1/projects/:project-id" [project-id]
       (response (get-project project-id)))
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (wrap-json-response)
      (wrap-defaults api-defaults)))

(defn init []
  (log/info "init called")
  (db/init))

(defn destroy []
  (log/info "destroy called"))
