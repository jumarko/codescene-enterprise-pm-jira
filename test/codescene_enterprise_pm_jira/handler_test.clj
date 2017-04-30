(ns codescene-enterprise-pm-jira.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [ring.util.codec :refer [base64-encode]]
            [codescene-enterprise-pm-jira.handler :refer :all]
            [codescene-enterprise-pm-jira.auth-test :refer [with-auth]]))

(def test-app (init-app {:sync {:hour-interval 1}
                         :auth {:jira {}
                                :service {:username "testuser"
                                          :password "testpassword"}}
                         :projects []}))

(deftest test-app
  (testing "requires authentication"
    (let [response (app (mock/request :get "/"))]
      (is (= (:status response) 401))
      (is (= (:body response) "You need to authenticate."))))

  (testing "main route"
    (let [response (app (with-auth (mock/request :get "/")))]
      (is (= (:status response) 200))
      (is (clojure.string/includes? (get-in response [:headers "Content-Type"]) "text/html"))))

  (testing "not-found route"
    (let [response (app (with-auth (mock/request :get "/invalid")))]
      (is (= (:status response) 404)))))
