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

(defn- replace-with-nil
  "Retain all values in 'all' that exists in 'v', replace others with nil.

  (replace-with-nil [1 2 3 4 5 6] [4 5 1])
  ;=> [1 nil nil 4 5 nil]
  "
  [all v]
  (map #(v %1) all))

(defn- issue->response [all-work-types {:keys [key cost work-types]}]
  (let [work-type-flags (map #(if %1 1 0)
                             (replace-with-nil all-work-types work-types))]
    {:id key
     :cost cost
     :types work-type-flags}))

(defn- project->response [{:keys [key cost-unit work-types issues]}]
  (let [work-types-ordered (vec work-types)]
    {:id key
     :costUnit cost-unit
     :workTypes work-types
     :issues (map (partial issue->response work-types-ordered) issues)}))

(defn- get-project [project-id]
  (-> (storage/get-project (db/persistent-connection) project-id)
      project->response))

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
