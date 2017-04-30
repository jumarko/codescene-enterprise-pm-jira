(ns codescene-enterprise-pm-jira.routes.api
  "API route handlers and related logic."
  (:require [codescene-enterprise-pm-jira
             [db :as db]
             [storage :as storage]]
            [codescene-enterprise-pm-jira.routes.status-page :refer [status-page]]
            [ring.util.response :refer [response]]
            [slingshot.slingshot :refer [throw+]]))

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

(defn api-status-handler []
  (response {:status :ok
             :name "CodeScene EnterPrise JIRA Integration"}))

(defn project-handler [project-id]
  (response (project->response
             (storage/get-project (db/persistent-connection) project-id))))

