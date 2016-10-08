(ns codescene-enterprise-pm-jira.config
  (:require [taoensso.timbre :as log]
            [yaml.core :as yaml]
            [slingshot.slingshot :refer [throw+]]
            [taoensso.timbre :as log]
            [codescene-enterprise-pm-jira.jndi :as jndi]))

(def ^:private config-file-setting-jndi-path
  "codescene/enterprise/pm/jira/config")

(defn get-config-file-name []
  (or (System/getenv "CODESCENE_JIRA_CONFIG")
      (jndi/path-from-context config-file-setting-jndi-path)
      "codescene-jira.yml"))

(def ^:private only-supported-cost-unit "minutes")

(defn- validate
  [{:keys [projects] :as complete-config}]
  (doseq [{:keys [key cost-unit]} projects]
    (let [configured-unit (:type cost-unit)]
      (when-not (= configured-unit only-supported-cost-unit)
        (throw+ {:msg (str "The config for project " key " specifies an unsupported cost-unit: found '" configured-unit
                           "' but we only support " only-supported-cost-unit)
                 :type :invalid-config}))))
  complete-config)

(defn read-config
  ([] (read-config (get-config-file-name)))
  ([filename]
   (log/info "Reading config at" filename)
   (validate
     (or (yaml/from-file filename true)
         (throw+ {:msg (str "No config found at " filename)
                  :type :config-not-found
                  :path filename})))))

(defn find-project-in-config [config key]
  (let [projects (:projects config)
        matching (filter #(= (:key %1) key) projects)]
    (when (> (count matching) 1)
      (log/warnf
        "Configuration contains more than one project with the key %s, using the first one."
        key))
    (first matching)))

(defn project-keys-in-config [{:keys [projects]}]
  (filter identity (map :key projects)))
