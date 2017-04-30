(ns codescene-enterprise-pm-jira.auth-test
  (:require [clojure.test :refer :all]
            [codescene-enterprise-pm-jira.auth :refer [wrap-auth]]
            [compojure.core :refer [GET routes]]
            [ring.mock.request :as mock]
            [ring.util
             [codec :refer [base64-encode]]
             [response :refer [response]]]))

(defn with-auth
  "Add basic Authorization to the request.
  Uses 'testuser' and 'testpassword' if explicit username/password is not provided."
  ([request]
   (with-auth request "testuser" "testpassword"))
  ([request username password]
   (mock/header request
                "Authorization"
                (str "Basic "
                     (base64-encode
                      (.getBytes (str username ":" password) "UTF-8"))))))

(def ^:private authenticated-routes
  (let [auth-config {:auth {:service {:username "testuser"
                                      :password "testpassword"}}}]
    (routes
     (GET "/" [] (response "public"))
     (wrap-auth
      (GET "/secure" [] (response "protected"))
      auth-config))))

(deftest public-resource
  (testing "public resource is accessible without authentication"
    (is (= 200 (:status (authenticated-routes (mock/request :get "/")))))))

(deftest protected-resource
  (testing "secure resource is not accessible without authentication"
    (is (= 401 (:status (authenticated-routes (mock/request :get "/secure"))))))

  (testing "secure resource is not accessible with incorrect credentials"
    (is (= 401 (:status (authenticated-routes (mock/request :get "/secure"))))))

  (testing "secure resource is accessible with proper credentials"
    (is (= 200 (:status (authenticated-routes (with-auth (mock/request :get "/secure"))))))))
