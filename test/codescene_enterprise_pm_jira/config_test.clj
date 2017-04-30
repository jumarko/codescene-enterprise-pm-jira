(ns codescene-enterprise-pm-jira.config-test
  (:require [clojure.test :refer :all]
            [codescene-enterprise-pm-jira.config :as c]))

(defn- example-config []
  (c/read-config "codescene-jira-example.yml"))

(deftest test-get-config-file-name
  (testing "get-config-file-name should return default config file if no specific config file has been set via env variable or jndi."
    (is (= "codescene-jira.yml" (c/get-config-file-name)))))

(deftest test-read-config
  (testing "read example configuration from codescene-jira-example.yml'"
    (let [config (example-config)]
      (is (= "DVP" (get-in config [:projects 1 :key])))
      (is (= ["Bug" "Feature" "Refactoring" "Documentation"] (get-in config [:projects 0 :supported-work-types])))))

  (testing "read non-existent file"
    (is (thrown? clojure.lang.ExceptionInfo (c/read-config "codescene-invisible.yml")))))

(deftest test-find-project-in-config
  (testing "project by key should be found"
    (let [project (c/find-project-in-config (example-config) "DVP")]
      (is (= "DVP" (:key project)))
      (is (= "points" (-> project :cost-unit :type)))))

  (testing "first project should be returned if there are two projects with the same key"
    (let [config-with-two-dvp-projects
          (update (example-config) :projects #(cons {:key "DVP"
                                                     :cost-unit {:type "minutes"}}
                                                    %))
          project (c/find-project-in-config config-with-two-dvp-projects "DVP")]
      (is (= "DVP" (:key project)))
      ;; notice that now we expect "minutes" to be returned
      ;; because we added duplicated MVP project to the beginning of vector
      (is (= "minutes" (-> project :cost-unit :type))))))

(deftest test-find-project-keys-in-config
  (testing "2 project keys expected to be found in example config"
    (is (= ["CSE" "DVP"]
           (c/project-keys-in-config (example-config))))))
