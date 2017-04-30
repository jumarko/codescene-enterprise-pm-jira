(ns codescene-enterprise-pm-jira.routes.app
  "Top-level routes for application.
  This includes home-page, basic error handling, and reference to api routes.
  More fain-grained routes can be found in other routes.* namespaces."
  (:require [codescene-enterprise-pm-jira
             [config :as config]
             [db :as db]
             [storage :as saorage]
             [sync :as sync]]
            [compojure
             [core :refer [GET POST routes]]
             [route :as route]]
            [hiccup
             [core :refer [html]]
             [form :as form]
             [page :refer [html5 include-css]]
             [util :refer [*base-url*]]]
            [ring.util
             [codec :refer [form-encode]]
             [response :refer [content-type redirect response]]]
            [slingshot.slingshot :refer [throw+ try+]]))

(defn- layout [& forms]
  (html5
   (include-css "/vendor/bootstrap/css/bootstrap.min.css")
   (html
    [:div.container
     forms])))

(defn- status-page [config message error]
  (-> (layout
       [:div.row
        [:div.col-lg-12

          [:h1 "CodeScene Enterprise JIRA Integration"]]
         [:hr]
         (when message
           [:div.alert.alert-success message])
         (when error
           [:div.alert.alert-danger error])]
        [:div.col-sm-6
         [:div.panel.panel-default
          [:div.panel-heading "Force Project Sync"]
          [:div.panel-body
           (form/form-to
            [:post "/sync/force"]

            [:div.form-group
             (form/label
              "project-key"
              "Project Key")
             [:div
              (form/drop-down
               {:id "project-key"}
               "project-key"
               (config/project-keys-in-config config))]]
            [:div.form-group
             (form/submit-button {:class "btn btn-success"} "Force Sync")])]]])
      response
      (content-type "text/html")))

(defn- replace-with-nil
  "Retain all values in 'all' that exists in 'v', replace others with nil.

  (replace-with-nil [1 2 3 4 5 6] [4 5 1])
  ;=> [1 nil nil 4 5 nil]
  "
  [all v]
  ;; TODO: replace with `(map v all)`
  (map #(v %1) all))

(defn- apply-id-pattern [pattern key]
  (if-let [match (second (re-find (re-pattern pattern) key))]
    match
    (throw+ {:msg       (format "Failed to apply pattern '%s' to issue '%s'" pattern key)
             :issue-key key
             :pattern   pattern
             :type      :invalid-ticket-id-pattern})))

(defn- jira-seconds->minutes
  "The JIRA API reports costs in seconds, but we use minutes for our internal format.
   In the future we'll support a more dynamic configuration."
  [cost]
  (/ cost 60))

(defn- convert-cost
  [cost {:keys [cost-unit] :as p}]
  (if (= (:type cost-unit) "minutes")
    (jira-seconds->minutes cost)
    cost))

(defn- issue->response [ticket-id-pattern all-work-types project {:keys [key cost work-types]}]
  (let [work-type-flags (map #(if %1 1 0)
                             (replace-with-nil all-work-types work-types))]
    {:id    (apply-id-pattern ticket-id-pattern key)
     :cost  (convert-cost cost project)
     :types work-type-flags}))

(defn- project->response [{:keys [key cost-unit work-types issues ticket-id-pattern] :as project}]
  (let [work-types-ordered (vec work-types)]
    {:id        key
     :costUnit  cost-unit
     :workTypes work-types
     :idType    "ticket-id"
     :items     (map (partial issue->response ticket-id-pattern work-types-ordered project) issues)}))

(defn- get-project [project-id]
  (-> (saorage/get-project (db/persistent-connection) project-id)
      project->response))

(defn- redirect-with-query [query]
  (content-type
   (redirect (str *base-url* "/?" (form-encode query)) :see-other)
   "text/html"))


(defn app-routes [config]
  (routes
   (GET "/" [message error]
     (status-page config message error))

   (GET "/api/1/status" []
     (-> (response {:status :ok
                    :name "CodeScene EnterPrise JIRA Integration"})))

   (GET "/api/1/projects/:project-id" [project-id]
     (response (get-project project-id)))

   (POST "/sync/force" [project-key]
     (try+
      (sync/sync-project-with-key config project-key)
      (redirect-with-query
       {:message (format "Successfully synced project %s." project-key)})
      (catch [:type :project-not-configured] {:keys [msg]}
        (redirect-with-query {:error msg}))
      (catch [:type :config-not-found] {:keys [msg]}
        (redirect-with-query {:error msg}))
      (catch [:type :jira-access-problem] {:keys [msg]}
        (redirect-with-query {:error msg}))))
   (route/not-found "Not Found")))
