(ns codescene-enterprise-pm-jira.handler
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [taoensso.timbre :as log]
            [codescene-enterprise-pm-jira.db :as db]
            [codescene-enterprise-pm-jira.storage :as storage]
            [codescene-enterprise-pm-jira.project-config :as project-config]
            [codescene-enterprise-pm-jira.jira :as jira]))

(defn fetch-and-save [username password]
  (let [project-config (project-config/read-config)
        issues (jira/find-issues-with-cost username password "timeoriginalestimate")]
    (storage/replace-project (db/persistent-connection) project-config issues)))

(defroutes app-routes
           (GET "/" [username password] (fetch-and-save username password))
           (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))

(defn init []
  (log/info "init called")
  (db/init))

(defn destroy []
  (log/info "destroy called"))
