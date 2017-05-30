(ns codescene-enterprise-pm-jira.end2end-test
  (:require [clojure
             [string :as string]
             [test :refer :all]]
            [codescene-enterprise-pm-jira
             [db :as db]
             [handler :refer [app init-app]]]
            [ring.mock.request :as mock]
            [ring.util.codec :refer [base64-encode]]
            [clojure.java.io :refer [delete-file]]))

;; TODO: document env variables?

;;; Helper functions

(defn- read-json [filename]
  (-> (str "test/codescene_enterprise_pm_jira/jira_golden_copy/" filename)
      slurp
      (string/replace #"\s+" "")))

(def cse-project-json (read-json "cse2.json"))

(def ti-project-json (read-json "ti.json"))

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

(defn- add-auth-header [ring-request]
  (mock/header ring-request
               "Authorization"
               (str "Basic "
                    (base64-encode
                     (.getBytes "testuser:testpassword" "UTF-8")))))

(defn- make-request [ring-request]
  (app (add-auth-header ring-request)))


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
    (make-request (mock/request :post "/sync/force" {:project-key (:key project)}))))

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

(defn check-jira-project [project-key expected-json]
  (let [response (make-request (mock/request :get (str "/api/1/projects/" project-key)))]
    (is (= (:status response) 200))
    (is (clojure.string/includes? (get-in response [:headers "Content-Type"]) "application/json"))
    (is (= expected-json (:body response)))))

(deftest ^:regression-test cse-project-test
  (testing "CSE2 jira project stats"
    (check-jira-project "CSE2" cse-project-json)))

(deftest ^:regression-test ti-project-test
  (testing "TI jira project stats"
    (check-jira-project "TI" ti-project-json)))
