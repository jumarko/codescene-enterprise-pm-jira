(ns codescene-enterprise-pm-jira.config
  (:require [taoensso.timbre :as log]
            [slingshot.slingshot :refer [throw+]]))

(defn find-project-in-config [config key]
  (let [projects (:projects config)
        matching (filter #(= (:key %1) key) projects)]
    (when (> (count matching) 1)
      (log/warnf
        "Configuration contains more than one project with the key %s, using the first one."
        key))
    (first matching)))
