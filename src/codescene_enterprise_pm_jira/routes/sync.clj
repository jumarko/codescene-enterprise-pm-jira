(ns codescene-enterprise-pm-jira.routes.sync
  "Sync routes handling manual syncing via status page."
  (:require [codescene-enterprise-pm-jira.sync :as sync]
            [compojure.core :refer [POST]]
            [hiccup.util :refer [*base-url*]]
            [ring.util
             [codec :refer [form-encode]]
             [response :refer [content-type redirect]]]
            [slingshot.slingshot :refer [try+]]))

(defn- redirect-with-query [query]
  (content-type
   (redirect (str *base-url* "/?" (form-encode query)) :see-other)
   "text/html"))

(defn sync-handler [config project-key]
  (try+
   (sync/sync-project-with-key config project-key)
   (redirect-with-query
    {:message (format "Successfully synced project %s." project-key)})
   (catch [:type :project-not-configured] {:keys [msg]}
     (redirect-with-query {:error msg}))
   (catch [:type :config-not-found] {:keys [msg]}
     (redirect-with-query {:error msg}))
   (catch [:type :jira-access-problem] {:keys [msg]}
     (redirect-with-query {:error msg}))))

