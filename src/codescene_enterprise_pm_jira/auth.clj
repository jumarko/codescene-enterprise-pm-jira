(ns codescene-enterprise-pm-jira.auth
  "Implements basic authentication.
  No specific authorization rules are defined. We require the general authentication for the whole application."
  (:require [buddy.auth :refer [authenticated?]]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth.backends.httpbasic :refer [http-basic-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [clojure.string :as str]
            [ring.util.response :refer [content-type header response status]]))

(def ^:private ^:const realm "codescene-jira")

(defn- create-auth-fn [{{service :service} :auth}]
  {:pre [(not (str/blank? (:username service)))
         (not (str/blank? (:password service)))]}
  (fn [req {:keys [username password]}]
    (and (= (:username service) username)
         (= (:password service) password))))

(defn- create-auth-backend [config]
  (http-basic-backend {:realm realm
                       :authfn (create-auth-fn config)}))

(defn- restrict-access
  [handler]
  (restrict
   handler
   {:handler authenticated?
    :on-error (fn [_ _]
                (-> (response "You need to authenticate.")
                    (status 401)
                    (content-type "text/plain")
                    (header "WWW-Authenticate"
                            (str "Basic realm=\"" realm "\""))))}))

(defn wrap-auth
  "Protects given handler with basic authentication."
  [handler config]
  (let [auth-backend (create-auth-backend config)]
    (-> handler
        restrict-access
        (wrap-authentication auth-backend))))

