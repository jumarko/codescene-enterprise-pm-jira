(ns codescene-enterprise-pm-jira.routes.api
  "Logic related to api routes handlers."
  (:require [codescene-enterprise-pm-jira
             [db :as db]
             [storage :as storage]]
            [codescene-enterprise-pm-jira.routes.status-page :refer [status-page]]
            [ring.util.response :refer [response]]
            [slingshot.slingshot :refer [throw+]]))

(defn- work-type-flags
  "Converts actual issue work types to flags.
  Flag is 0 (= issue doesn't have the work type) or 1 (= issue has the work type).

  Example:
  (work-type-flags [\"Documentation\" \"Feature\" \"Bug\"] [\"Documentation\" \"Bug\"])
  ;=> (1 0 1)
  "
  [all-work-types issue-work-types]
  (map #(if %1 1 0)
       (map (set issue-work-types) all-work-types)))

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
  {:id    (apply-id-pattern ticket-id-pattern key)
   :cost  (convert-cost cost project)
   :types (work-type-flags all-work-types work-types)})

(defn project->response [{:keys [key cost-unit work-types issues ticket-id-pattern] :as project}]
  (let [work-types-ordered (vec work-types)]
    {:id        key
     :costUnit  cost-unit
     :workTypes work-types
     :idType    "ticket-id"
     :items     (map (partial issue->response ticket-id-pattern work-types-ordered project) issues)}))

(defn project-handler [project-id]
  (response (project->response
             (storage/get-project (db/persistent-connection) project-id))))

(defn api-status-handler []
  (response {:status :ok
             :name "CodeScene EnterPrise JIRA Integration"}))
