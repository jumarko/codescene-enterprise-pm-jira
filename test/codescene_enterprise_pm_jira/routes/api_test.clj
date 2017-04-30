(ns codescene-enterprise-pm-jira.routes.api-test
  (:require [clojure.test :refer :all]
            [codescene-enterprise-pm-jira.routes.api :as api]))

(def ^:private test-project-data
  {:key "CSE"
   :ticket-id-pattern "(CSE-\\d+)"
   :cost-unit {:type "points" :singular "point" :plural "points"}
   :work-types #{"Documentation" "Feature" "Bug"}
   :issues '({:cost 10 :key "CSE-1" :work-types #{"Documentation" "Bug"}}
             {:cost 25 :key "CSE-2" :work-types #{"Documentation" "Feature"}}
             {:cost 5 :key "CSE-3" :work-types #{"Bug"}})})

(deftest convert-project-db-data-to-api-response
  (testing "convert valid db project converted to api format"
    (is (= {:id "CSE"
            :costUnit {:type "points"
                       :singular "point"
                       :plural "points"}
            :workTypes #{"Documentation"
                         "Feature"
                         "Bug"}
            ;; this is hard-coded in project->response
            :idType "ticket-id"
            :items [{:id "CSE-1" :cost 10 :types [1 0 1]}
                    {:id "CSE-2" :cost 25 :types [1 1 0]}
                    {:id "CSE-3" :cost 5 :types [0 0 1]}]}
           (api/project->response test-project-data))))

  (testing "convert nil project to nil"
    (is (nil? (api/project->response nil))))
  )
