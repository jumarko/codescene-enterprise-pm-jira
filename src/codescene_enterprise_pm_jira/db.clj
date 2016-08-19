(ns codescene-enterprise-pm-jira.db
  (:require [clojure.java.jdbc :as jdbc]
            [ragtime.jdbc :as ragtime-jdbc]
            [ragtime.repl :as ragtime-repl]
            [taoensso.timbre :as log]
            [codescene-enterprise-pm-jira.jndi :as jndi]))

(def ^:private default-db-path "./db/codescene-enterprise-pm-jira")

(def ^:private jndi-path "codescene/enterprise/pm/jira/dbpath")

(defn- db-physical-path
  []
  (let [db-path (or (jndi/path-from-context jndi-path)
                    default-db-path)]
    (log/info "Database is located on" db-path)
    db-path))

(defn get-db-spec
  "Creates a DB connection spec."
  []
  {:classname "org.h2.Driver"
   :subprotocol "h2"
   :subname (db-physical-path)
   :user "sa"
   :password ""})

(def ^:private db-spec (atom nil))

(defn persistent-connection [] @db-spec)

(defn- migration-config
  [spec]
  {:datastore (ragtime-jdbc/sql-database spec)
   :migrations (ragtime-jdbc/load-resources "migrations")})

(defn migrate
  [spec]
  (ragtime-repl/migrate (migration-config spec)))

(defn rollback
  [spec]
  (ragtime-repl/rollback (migration-config spec)))

(defn init
  ([]
   (init (get-db-spec)))
  ([spec]
   (migrate spec)
   (reset! db-spec spec)
   (log/info "Database initialized.")))
