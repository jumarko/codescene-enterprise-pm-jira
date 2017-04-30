(ns codescene-enterprise-pm-jira.handler
  (:gen-class)
  (:require [codescene-enterprise-pm-jira
             [auth :as auth]
             [config :as config]
             [db :as db]
             [scheduling :as scheduling]]
            [codescene-enterprise-pm-jira.routes.app :refer [app-routes]]
            [hiccup.middleware :refer [wrap-base-url]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware
             [defaults :refer [api-defaults wrap-defaults]]
             [json :refer [wrap-json-response]]
             [params :refer [wrap-params]]
             [resource :refer [wrap-resource]]]
            [taoensso.timbre :as log]))

(def app nil)

(defn init-app [config]
  (alter-var-root
   #'app
   (constantly
    (-> (app-routes config)
        wrap-base-url
        (wrap-resource "public")
        (auth/wrap-auth config)
        (wrap-params)
        (wrap-json-response)
        (wrap-defaults api-defaults)))))

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
