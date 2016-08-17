(ns codescene-enterprise-pm-jira.jira
  (:require [clj-http.client :as client]
            [cheshire.core :as json]))

(def ^:const ^:private rest-url
  "http://jira-integration.codescene.io/rest/api/latest")

(defn- get-paged-data [params]
  (loop [start-at 0
         total-issues []]
    (let [result (client/get (str rest-url "/search")
                             (assoc-in params [:query-params :startAt] start-at))
          body (:body result)
          {:keys [issues maxResults]} (json/parse-string body true)]
      (if (seq issues)
        (recur (+ maxResults start-at) (concat total-issues issues))
        total-issues))))

(defn- jira-issue->db-format [cost-field-name {:keys [fields key] :as issue}]
  {:key key
   :cost (get fields (keyword cost-field-name))
   :work-types (set (:labels fields))})

(defn find-issues-with-cost [username password cost-field-name]
  (map (partial jira-issue->db-format cost-field-name)
       (get-paged-data {:basic-auth   [username password]
                        :accept       :json
                        :query-params {:jql (str cost-field-name "!=EMPTY")}})))
