(ns codescene-enterprise-pm-jira.end2end-test
  (:require [clojure
             [string :refer [replace]]
             [test :refer :all]]
            [codescene-enterprise-pm-jira
             [db :as db]
             [handler :refer [app init-app]]]
            [ring.mock.request :as mock]
            [ring.util.codec :refer [base64-encode]]
            [clojure.java.io :refer [delete-file]]))

;; TODO: document env variables?
;; - check that DB directory doesn't exist before running test

;;; Helper functions

(defn- read-json [filename]
  (-> (str "test/codescene_enterprise_pm_jira/jira_golden_copy/" filename)
      slurp
      (replace #"\s+" "")))

(def cse-project-json (read-json "cse2.json"))

(def cse-project {:key                  "CSE2"
                  :cost-unit            {:type "minutes"} :cost-field "timeoriginalestimate"
                  :supported-work-types ["Bug" "Feature" "Refactoring" "Documentation" "Epic" "UI/UX"
                                         "Cool+_Stuff_-_Good_to_Have" "Must_Have" "Needs_research"]
                  :ticket-id-pattern    "CSE2-(\\d+)"})

(def ti-project {:key                  "TI"
                 :cost-unit            {:type "minutes"}
                 :cost-field           "timeoriginalestimate"
                 :supported-work-types ["Bug" "Improvement"]
                 :ticket-id-pattern    "TI-(\\d+)"})

(defn- add-auth-header [request]
  (mock/header request
               "Authorization"
               (str "Basic "
                    (base64-encode
                     (.getBytes "testuser:testpassword" "UTF-8")))))

(defn- request [ring-request]
  (app (-> ring-request
           add-auth-header)))


;;; Test fixture
(defn- configure-service []
  (db/init)
  (init-app {:sync     {:hour-interval 1}
             :auth     {:jira    {:username (System/getenv "JIRA_USERNAME")
                                  :password (System/getenv "JIRA_PASSWORD")
                                  :base-uri "http://jira-integration.codescene.io"}
                        :service {:username "testuser"
                                  :password "testpassword"}}
             :projects [cse-project ti-project]}))

(defn- sync-projects-from-jira []
  (doseq [project [cse-project ti-project]]
    (request (mock/request :post "/sync/force" {:project-key (:key project)}))))

(defn- delete-test-db []
  (let [test-db-path (System/getenv "CODESCENE_JIRA_DATABASE_PATH")
        ;; actual file has .mv.db extension
        test-db-file (str test-db-path ".mv.db")]
    (delete-file test-db-file)))

(defn- test-fixture [f]
  (configure-service)
  (sync-projects-from-jira)
  (f)
  (delete-test-db))


(use-fixtures :each test-fixture)


;;; Tests

(deftest test-app
  (testing "project stats"
    (let [response (request (mock/request :get "/api/1/projects/CSE2"))]
      (is (= (:status response) 200))
      (is (clojure.string/includes? (get-in response [:headers "Content-Type"]) "application/json"))
      (is (= cse-project-json (:body response))))))

