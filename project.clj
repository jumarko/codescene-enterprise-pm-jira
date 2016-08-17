(defproject codescene-enterprise-pm-jira "0.1.0-SNAPSHOT"
  :description "A Project Management service which integrates JIRA with CodeScene Enterprise"
  :url "https://github.com/empear-analytics/codescene-enterprise-pm-jira"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.5.1" :exclusions [commons-io]]
                 [ring/ring-defaults "0.2.1" :exclusions [commons-io]]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-codec "1.0.1"]
                 [io.forward/yaml "1.0.4"]
                 [slingshot "0.12.2"]
                 [clj-http "3.1.0"]
                 [hiccup "1.0.5"]
                 [cheshire "5.6.3"]
                 [ragtime "0.6.3"]
                 [com.h2database/h2 "1.4.192"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [com.taoensso/timbre "4.7.0" :exclusions [org.clojure/tools.reader]]
                 [yesql "0.5.3"]]
  :plugins [[lein-ring "0.9.7"]
            [lein-uberwar "0.2.0"]]
  :ring {:handler codescene-enterprise-pm-jira.handler/app
         :init codescene-enterprise-pm-jira.handler/init
         :destroy codescene-enterprise-pm-jira.handler/destroy}
  :uberwar {:handler codescene-enterprise-pm-jira.handler/app
            :init codescene-enterprise-pm-jira.handler/init
            :destroy codescene-enterprise-pm-jira.handler/destroy
            :name "codescene-enterprise-pm-jira.war"}
  :uberjar-name "codescene-enterprise-pm-jira.standalone.jar"
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]]}}
  :aot [codescene-enterprise-pm-jira.handler]
  :release-tasks [["ring" "uberjar"]
                  ["uberwar"]])
