(ns codescene-enterprise-pm-jira.project-config)

(defn read-config []
  {:key "CSE2"
   :cost-unit {:type "numeric"
               :format {:singular "point" :plural "points"}}})
