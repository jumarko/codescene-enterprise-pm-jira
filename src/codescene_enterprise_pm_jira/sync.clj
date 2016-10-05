(ns codescene-enterprise-pm-jira.sync
  (:require [taoensso.timbre :as log]
            [slingshot.slingshot :refer [try+ throw+]]
            [codescene-enterprise-pm-jira.db :as db]
            [codescene-enterprise-pm-jira.config :as config]
            [codescene-enterprise-pm-jira.storage :as storage]
            [codescene-enterprise-pm-jira.jira :as jira]))

(defn- sync-project
  "Tries to sync the JIRA project config, given the JIRA auth config."
  [{:keys [base-uri username password]}
   {:keys [key] :as project-config}]
  (log/info "Syncing project" key)
  (let [cost-field (get project-config :cost-field "timeoriginalestimate")
        issues (jira/find-issues-with-cost base-uri username password project-config cost-field)]
    (when-not (seq issues)
      (throw+ {:msg         (format "Could not get issues from JIRA for project %s." key)
               :project-key key
               :type        :jira-access-problem}))
    (storage/replace-project (db/persistent-connection) project-config issues)
    (log/info "Replaced issues in project" key "with" (count issues) "issues.")))

(defn sync-project-with-key
  "Tries to sync the JIRA project using the given config and project key."
  [{:keys [auth] :as config} key]
  (let [project-config (config/find-project-in-config config key)]
    (when-not project-config
      (throw+
       {:msg         (format "Cannot sync non-configured project %s!" key)
        :project-key key
        :type        :project-not-configured}))
    (sync-project (:jira auth) project-config)))

(defn sync-all-projects [{:keys [auth projects] :as config}]
  (doseq [project-config projects]
    (try+
     (sync-project (:jira auth) project-config)
     (catch Object e
       (log/warnf "Failed to sync project with key %s, when syncing all projects."
                  (:key project-config))))))
