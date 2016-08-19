(ns codescene-enterprise-pm-jira.jndi
  (:require [taoensso.timbre :as log])
  (:import (javax.naming InitialContext Context NoInitialContextException)))

(defn- jndi-lookup
  ([^Context context ^String k]
   (.lookup context k))
  ([k]
   (jndi-lookup (InitialContext.) k)))

(defn path-from-context
  [path]
  (try
    (some-> (jndi-lookup "java:comp/env")
            (jndi-lookup path))
    (catch NoInitialContextException e
      (do
        (log/info "No value set in JNDI for path:" path)
        false))))
