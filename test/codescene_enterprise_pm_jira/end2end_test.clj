(ns codescene-enterprise-pm-jira.end2end-test
  (:require [clojure.test :refer :all]
            [codescene-enterprise-pm-jira.handler :refer [app init-app]]
            [ring.mock.request :as mock]
            [ring.util.codec :refer [base64-encode]]
            [codescene-enterprise-pm-jira.db :as db]))

;; TODO: document env variables?
;; - check that DB directory doesn't exist before running test

(def cse-project {:key                  "CSE2"
                  :cost-unit            {:type "minutes"}
                  :cost-field           "timeoriginalestimate"
                  :supported-work-types ["Bug" "Feature" "Refactoring" "Documentation" "Epic" "UI/UX" "Cool+_Stuff_-_Good_to_Have"
                                         "Must_Have" "Needs_research"]
                  :ticket-id-pattern    "CSE2-(\\d+)"})

(def ti-project {:key                  "TI"
                 :cost-unit            {:type "minutes"}
                 :cost-field           "timeoriginalestimate"
                 :supported-work-types ["Bug" "Improvement"]
                 :ticket-id-pattern    "TI-(\\d+)"})

(def test-app
  (do (db/init)
      (init-app {:sync {:hour-interval 1}
                 :auth {:jira {:username (System/getenv "JIRA_USERNAME")
                               :password (System/getenv "JIRA_PASSWORD")}
                        :service {:username "testuser"
                                  :password "testpassword"}}
                 :projects [cse-project ti-project]})))

(defn- add-auth-header [request]
  (mock/header request
               "Authorization"
               (str "Basic "
                    (base64-encode
                     (.getBytes "testuser:testpassword" "UTF-8")))))

(deftest test-app
  (testing "project stats"
    (let [response (app (-> (mock/request :get "/api/1/projects/CSE2")
                            add-auth-header))
          _ (println response)]
      (is (= (:status response) 200))
      (is (clojure.string/includes? (get-in response [:headers "Content-Type"]) "text/html")))))

