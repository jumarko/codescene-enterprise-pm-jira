(ns codescene-enterprise-pm-jira.storage
  (:require [clojure.set :refer [rename-keys index]]
            [clojure.java.jdbc :as jdbc]
            [yesql.core :refer [defqueries]]))

(defqueries "codescene-enterprise-pm-jira/storage.sql")

(def ^:private row->issue #(rename-keys %1 {:issue_key :key}))
(def ^:private row->issue-work-type #(rename-keys %1 {:issue_key :key
                                                      :type_name :type-name}))

(defn get-issues-in-project [conn project-key]
  (select-issues-in-project
   {:project_key project-key}
   {:connection conn
    :row-fn row->issue}))

(defn get-work-types-in-project [conn project-key]
  (select-work-types-in-project
   {:project_key project-key}
   {:connection conn
    :row-fn row->issue-work-type}))

(defn- work-types-by-key [work-types]
  (index (set work-types) [:key]))

(defn- get-issues-with-work-types-in-project [conn project-key work-types]
  (let [by-key (work-types-by-key work-types)]
    (for [{:keys [key] :as issue} (get-issues-in-project conn project-key)]
      (let [issue-work-types (map :type-name (get by-key {:key key}))]
        (assoc issue :work-types (set issue-work-types))))))

(defn- project-config->params [{:keys [key ticket-id-pattern cost-unit]}]
  {:project_key key
   :ticket_id_pattern ticket-id-pattern
   :cost_unit_type (:type cost-unit)
   :cost_unit_format_singular (get-in cost-unit [:format :singular])
   :cost_unit_format_plural (get-in cost-unit [:format :plural])})

(defn- row->project-config [{:keys [project_key
                                    ticket_id_pattern
                                    cost_unit_type
                                    cost_unit_format_singular
                                    cost_unit_format_plural]}]
  {:key project_key
   :ticket-id-pattern ticket_id_pattern
   :cost-unit (merge {:type cost_unit_type}
                     (when (and cost_unit_format_singular cost_unit_format_plural)
                       {:singular cost_unit_format_singular
                        :plural cost_unit_format_plural}))})

(defn get-project [conn project-key]
  (when-let [project-config (select-project
                           {:project_key project-key}
                           {:connection conn
                            :row-fn row->project-config
                            :result-set-fn first})]
    (let [work-types (get-work-types-in-project conn project-key)
          issues (get-issues-with-work-types-in-project conn project-key work-types)]
      (merge project-config
             {:work-types (set (map :type-name work-types))
              :issues issues}))))

(defn delete-project
  "Deletes the given project together with all its issues and their work types."
  [conn key]
  (jdbc/with-db-transaction [tx conn]
    (delete-work-types-in-project!
     {:project_key key}
     {:connection tx})
    (delete-issues-in-project!
     {:project_key key}
     {:connection tx})
    (delete-project!
     {:project_key key}
     {:connection tx})
    nil))

(defn replace-project [conn project-config issues]
  (let [project-key (:key project-config)]
    ;; Transaction, go!
    (jdbc/with-db-transaction [tx conn]

      ;; First delete the project if it exists.
      (delete-project tx project-key)

      ;; Then add the new ones.
      (insert-project!
       (project-config->params project-config)
       {:connection tx})
      (doseq [{:keys [key cost work-types]} issues]
        (insert-issue-into-project!
         {:project_key project-key
          :issue_key key
          :cost cost}
         {:connection tx})
        (doseq [work-type work-types]
          (insert-issue-work-type-into-project!
           {:project_key project-key
            :issue_key key
            :type_name work-type}
           {:connection tx}))))))
