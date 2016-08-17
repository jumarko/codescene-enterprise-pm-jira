(ns codescene-enterprise-pm-jira.jira
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [slingshot.slingshot :refer [try+ throw+]]
            [taoensso.timbre :as log]))

(def ^:const ^:private rest-api-path
  "/rest/api/latest")

(defn- get-paged-data [base-uri params]
  (loop [start-at 0
         total-issues []]
    (let [result (client/get (str base-uri rest-api-path "/search")
                             (-> params
                                 (assoc-in [:query-params :startAt] start-at)
                                 (assoc :conn-timeout 5000)))
          body (:body result)
          {:keys [issues maxResults]} (json/parse-string body true)]
      (if (seq issues)
        (recur (+ maxResults start-at) (concat total-issues issues))
        total-issues))))

(defn- jira-issue->db-format [cost-field-name {:keys [fields key] :as issue}]
  {:key        key
   :cost       (get fields (keyword cost-field-name))
   :work-types (set (:labels fields))})

(defn find-issues-with-cost [base-uri username password key cost-field-name]
  (try+
    (map (partial jira-issue->db-format cost-field-name)
         (get-paged-data base-uri {:basic-auth   [username password]
                                   :accept       :json
                                   :query-params {:jql (format "project=%s and %s!=EMPTY"
                                                               key
                                                               cost-field-name)}}))
    (catch [:status 400] {:keys [body]}
      (log/errorf "Could not find issues in project %s: %s" key body))

    (catch [:status 401] _
      (log/errorf "Authentication failed when fetching issues for project %s." key))

    (catch [:status 403] {:keys [body headers]}
      (if (= "AUTHENTICATION_DENIED" (get headers "X-Seraph-LoginReason"))
        (log/error
          (str "JIRA denied authentication when fetching issues for project " key
               ". This usually means that JIRA's CAPTCHA feature has been"
               " triggered for the authenticating user. Log in using the JIRA"
               " web site and then try again."))
        (log/errorf "Unauthorized when fetching issues for project %s." key)))

    (catch Object _
      (log/errorf "Unexpected error when fetching issues for project %s: %s"
                  key
                  (:throwable &throw-context))
      (throw+))))
