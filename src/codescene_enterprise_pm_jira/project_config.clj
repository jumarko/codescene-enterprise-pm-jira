(ns codescene-enterprise-pm-jira.project-config
  (:require [taoensso.timbre :as log]))

(defn read-config []
  {:sync nil
   :projects [{:key "CSE2"
               :cost-field "timeoriginalestimate"
               :cost-unit {:type "numeric"
                           :format {:singular "point" :plural "points"}}}
              {:key "PROJ"
               :cost-field "foobar"
               :cost-unit {:type "minutes"}}]})

(defn read-config-for-project [key]
  (let [projects (:projects (read-config))
        matching (filter #(= (:key %1) key) projects)]
    (when (> (count matching) 1)
      (log/warnf
       "Configuration contains more than one project with the key %s, using the first one."
       key))
    (first matching)))
