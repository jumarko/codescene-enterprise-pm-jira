(ns codescene-enterprise-pm-jira.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clj-http.client :as client]
            [cheshire.core :as json]))

(def ^:const rest-url "http://jira-integration.codescene.io/rest/api/latest")

(defn get-paged-data [params]
  (loop [start-at 0
         total-issues []]
    (let [result (client/get (str rest-url "/search")
                             (assoc-in params [:query-params :startAt] start-at))
          body (:body result)
          {:keys [issues maxResults]} (json/parse-string body true)]
      (if (seq issues)
        (recur (+ maxResults start-at) (concat total-issues issues))
        total-issues))))

(defn find-issues-with-estimates [username password]
  (get-paged-data {:basic-auth   [username password]
                   :accept       :json
                   :query-params {:jql "timeoriginalestimate!=EMPTY"}}))

(defroutes app-routes
           (GET "/" [username password] (find-issues-with-estimates username password))
           (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))
