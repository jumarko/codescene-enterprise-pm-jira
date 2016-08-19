(ns codescene-enterprise-pm-jira.handler
  (:gen-class)
  (:require [clojure.string :as string]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [response content-type redirect status header]]
            [ring.util.codec :refer [form-encode]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.httpbasic :refer [http-basic-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.auth.accessrules :refer [restrict]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [html5]]
            [hiccup.form :as form]
            [taoensso.timbre :as log]
            [codescene-enterprise-pm-jira.db :as db]
            [codescene-enterprise-pm-jira.storage :as storage]
            [codescene-enterprise-pm-jira.config :as config]
            [codescene-enterprise-pm-jira.jira :as jira]
            [slingshot.slingshot :refer [try+ throw+]]))

(defn sync-project
  "Tries to sync the JIRA project using the given config and the project
  key. Returns nil if successful and a error message string if failed."
  [{{{:keys [base-uri username password]} :jira} :auth :as config}
   key]
  (let [project-config (config/find-project-in-config config key)]
    (when-not project-config
      (throw+
        {:msg         (format "Cannot sync non-configured project %s!" key)
         :project-key key
         :type        :project-not-configured}))
    (log/info "Syncing project" key)
    (let [cost-field (get project-config :cost-field "timeoriginalestimate")
          issues (jira/find-issues-with-cost base-uri username password key cost-field)]
      (when-not (seq issues)
        (throw+ {:msg         (format "Could not get issues from JIRA for project %s." key)
                 :project-key key
                 :type        :jira-access-problem}))
      (storage/replace-project (db/persistent-connection) project-config issues)
      (log/info "Replaced issues in project" key "with" (count issues) "issues."))))

(defn- status-page [error]
  (-> (html5 (html [:h1 "CodeScene Enterprise JIRA Integration"]
                   (when error
                     [:div.error error])
                   (form/form-to [:post "/sync/force"]
                                 (form/text-field
                                   {:placeholder "Project Key"}
                                   "project-key")
                                 (form/submit-button "Force Sync"))))
      response
      (content-type "text/html")))

(defn- replace-with-nil
  "Retain all values in 'all' that exists in 'v', replace others with nil.

  (replace-with-nil [1 2 3 4 5 6] [4 5 1])
  ;=> [1 nil nil 4 5 nil]
  "
  [all v]
  (map #(v %1) all))

(defn- apply-id-pattern [pattern key]
  (if-let [match (second (re-find (re-pattern pattern) key))]
    match
    (throw+ {:msg       (format "Failed to apply pattern '%s' to issue '%s'" pattern key)
             :issue-key key
             :pattern   pattern
             :type      :invalid-ticket-id-pattern})))

(defn- issue->response [ticket-id-pattern all-work-types {:keys [key cost work-types]}]
  (let [work-type-flags (map #(if %1 1 0)
                             (replace-with-nil all-work-types work-types))]
    {:id    (apply-id-pattern ticket-id-pattern key)
     :cost  cost
     :types work-type-flags}))

(defn- project->response [{:keys [key cost-unit work-types issues ticket-id-pattern]}]
  (let [work-types-ordered (vec work-types)]
    {:id        key
     :costUnit  cost-unit
     :workTypes work-types
     :idType    "ticket-id"
     :items     (map (partial issue->response ticket-id-pattern work-types-ordered) issues)}))

(defn- get-project [project-id]
  (-> (storage/get-project (db/persistent-connection) project-id)
      project->response))

(defn- redirect-with-error [msg]
  (content-type
    (redirect (str "/?" (form-encode {:error msg})) :see-other)
    "text/html"))

(def app nil)

(def ^:private ^:const realm "codescene-jira")

(defn- app-routes [config]
  (-> (routes
       (GET "/" [error]
            (status-page error))

       (GET "/api/1/projects/:project-id" [project-id]
            (response (get-project project-id)))

       (POST "/sync/force" [project-key]
             (try+
              (sync-project config project-key)
              (content-type
               (redirect "/" :see-other)
               "text/html")
              (catch [:type :project-not-configured] {:keys [msg]}
                (redirect-with-error msg))
              (catch [:type :config-not-found] {:keys [msg]}
                (redirect-with-error msg))
              (catch [:type :jira-access-problem] {:keys [msg]}
                (redirect-with-error msg))))
       (route/not-found "Not Found"))
      (restrict {:handler authenticated?
                 :on-error (fn [_ _ ]
                             (-> (response "You need to authenticate.")
                                 (status 401)
                                 (content-type "text/plain")
                                 (header "WWW-Authenticate"
                                         (str "Basic realm=\"" realm "\""))))})))

(defn- create-auth-fn [{{service :service} :auth}]
  {:pre [(not (string/blank? (:username service)))
         (not (string/blank? (:password service)))]}
  (fn [req {:keys [username password]}]
    (and (= (:username service) username)
         (= (:password service) password))))

(defn- create-auth-backend [config]
  (http-basic-backend {:realm realm
                       :authfn (create-auth-fn config)}))

(defn- init-app [config]
  (alter-var-root
   #'app
   (constantly
    (let [auth-backend (create-auth-backend config)]
      (-> (app-routes config)
          (wrap-authentication auth-backend)
          (wrap-authorization auth-backend)
          (wrap-params)
          (wrap-json-response)
          (wrap-defaults api-defaults))))))

(defn- load-config []
  (log/info "Loading config...")
  (let [config (config/read-config)]
    (log/info "Config loaded.")
    config))

(defn init []
  (let [config (load-config)]
    (db/init)
    (init-app config)))

(defn destroy []
  (log/info "destroy called"))
