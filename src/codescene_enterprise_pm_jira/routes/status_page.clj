(ns codescene-enterprise-pm-jira.routes.status-page
  "Main status page for the whole application.
  Features UI for manual project sync."
  (:require [codescene-enterprise-pm-jira.config :as config]
            [hiccup
             [core :refer [html]]
             [form :as form]
             [page :refer [html5 include-css]]]
            [ring.util.response :refer [content-type response]]))

(defn- header [message error]
  [:div.col-lg-12
   [:div
    [:h1 "CodeScene Enterprise JIRA Integration"]]
   [:hr]
   (when message
     [:div.alert.alert-success message])
   (when error
     [:div.alert.alert-danger error])])

(defn- project-drop-down [config]
  [:div.form-group
   (form/label
    "project-key"
    "Project Key")
   [:div
    (form/drop-down
     {:id "project-key"}
     "project-key"
     (config/project-keys-in-config config))]])

(defn- sync-button []
  [:div.form-group
   (form/submit-button {:class "btn btn-success"} "Force Sync")])

(defn- project-sync-panel [config]
  [:div.col-sm-6
   [:div.panel.panel-default
    [:div.panel-heading "Force Project Sync"]
    [:div.panel-body
     (form/form-to
      [:post "/sync/force"]
      (project-drop-down config)
      (sync-button))]]])

(defn- layout [& forms]
  (html5
   (include-css "/vendor/bootstrap/css/bootstrap.min.css")
   (html
    [:div.container
     forms])))

(defn status-page
  "Renders main status page with project list and 'Force Sync' action."
  [config message error]
  (-> (layout
       [:div.row (header message error) (project-sync-panel config)])
      response
      (content-type "text/html")))
