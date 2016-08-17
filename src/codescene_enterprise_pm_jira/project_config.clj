(ns codescene-enterprise-pm-jira.project-config
  (:require [taoensso.timbre :as log]
            [yaml.core :as yaml]))

(defn get-config-file-name []
  (or (System/getenv "CODESCENE_JIRA_CONFIG") "codescene-jira.yml"))

(defn read-config
  ([] (read-config (get-config-file-name)))
  ([filename]
   (or (yaml/from-file filename true)
       (throw (ex-info (str "No config found at " filename) {})))))

(defn find-project-in-config [config key]
  (let [projects (:projects config)
        matching (filter #(= (:key %1) key) projects)]
    (when (> (count matching) 1)
      (log/warnf
        "Configuration contains more than one project with the key %s, using the first one."
        key))
    (first matching)))
