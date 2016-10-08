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
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.adapter.jetty :refer [run-jetty]]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.httpbasic :refer [http-basic-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.auth.accessrules :refer [restrict]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [html5 include-css]]
            [hiccup.form :as form]
            [hiccup.util :refer [*base-url*]]
            [hiccup.middleware :refer [wrap-base-url]]
            [taoensso.timbre :as log]
            [codescene-enterprise-pm-jira.db :as db]
            [codescene-enterprise-pm-jira.storage :as storage]
            [codescene-enterprise-pm-jira.config :as config]
            [codescene-enterprise-pm-jira.scheduling :as scheduling]
            [codescene-enterprise-pm-jira.sync :as sync]
            [slingshot.slingshot :refer [try+ throw+]]))

(defn- layout [& forms]
  (html5
   (include-css "/vendor/bootstrap/css/bootstrap.min.css")
   (html
    [:div.container
     forms])))

(defn- status-page [config message error]
  (-> (layout
       [:div.row
        [:div.col-lg-12
         [:div
          [:h1 "CodeScene Enterprise JIRA Integration"]]
         [:hr]
         (when message
           [:div.alert.alert-success message])
         (when error
           [:div.alert.alert-danger error])]
        [:div.col-sm-6
         [:div.panel.panel-default
          [:div.panel-heading "Force Project Sync"]
          [:div.panel-body
           (form/form-to
            [:post "/sync/force"]

            [:div.form-group
             (form/label
              "project-key"
              "Project Key")
             [:div
              (form/drop-down
               {:id "project-key"}
               "project-key"
               (config/project-keys-in-config config))]]
            [:div.form-group
             (form/submit-button {:class "btn btn-success"} "Force Sync")])]]]])
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

(defn- jira-seconds->minutes
  "The JIRA API reports costs in seconds, but we use minutes for our internal format.
   In the future we'll support a more dynamic configuration."
  [cost]
  (/ cost 60))

(defn- issue->response [ticket-id-pattern all-work-types {:keys [key cost work-types]}]
  (let [work-type-flags (map #(if %1 1 0)
                             (replace-with-nil all-work-types work-types))]
    {:id    (apply-id-pattern ticket-id-pattern key)
     :cost  (jira-seconds->minutes cost)
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

(defn- redirect-with-query [query]
  (content-type
   (redirect (str *base-url* "/?" (form-encode query)) :see-other)
   "text/html"))

(def app nil)

(def ^:private ^:const realm "codescene-jira")

(defn- app-routes [config]
  (-> (routes
       (GET "/" [message error]
            (status-page config message error))

       (GET "/api/1/status" []
            (-> (response {:status :ok
                           :name "CodeScene EnterPrise JIRA Integration"})))

       (GET "/api/1/projects/:project-id" [project-id]
            (response (get-project project-id)))

       (POST "/sync/force" [project-key]
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

(defn init-app [config]
  (alter-var-root
   #'app
   (constantly
    (let [auth-backend (create-auth-backend config)]
      (-> (app-routes config)
          wrap-base-url
          (wrap-resource "public")
          (wrap-authentication auth-backend)
          (wrap-authorization auth-backend)
          (wrap-params)
          (wrap-json-response)
          (wrap-defaults api-defaults))))))

(defn- load-config []
  (let [config (config/read-config)]
    (log/info "Config loaded.")
    config))

(defn init []
  (let [config (load-config)]
    (db/init)
    (init-app config)
    config))

(defn destroy []
  (log/info "destroy called"))

(defonce ^:private server (atom nil))

(defn stop-server []
  (when @server
    (.stop @server))
  (scheduling/stop-scheduled-sync))

(defn start-server
  ([]
   (start-server {:with-period-sync true}))
  ([options]
   (stop-server)
   (let [user-config (init)
         port (or (some-> (System/getenv "PORT")
                          Integer/parseInt)
                  3004)]
     (when (:with-period-sync options)
       (scheduling/start-scheduled-sync true user-config))

     (log/infof "Starting server at port %d..." port)
     (let [jetty-server (run-jetty
                         app
                         (merge {:port port
                                 :join? false}
                                options))]
       (reset! server jetty-server)))))

(defn start-server-without-period-sync []
  (start-server {:with-period-sync false}))

(defn -main [& args]
  (start-server {:join? true}))
