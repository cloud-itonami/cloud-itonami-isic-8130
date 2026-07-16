(ns landscapecare.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [landscapecare.store :as store]))

;; ──────────────────────── Site-Order Retrieval ──────────────────────

(deftest site-order-test
  (testing "retrieve an existing site order"
    (let [order-data {:service-type :treatment/herbicide-broadcast :client-site-id "site-42"}
          st {:site-orders {"order-001" order-data}}
          result (store/site-order st "order-001")]
      (is (= result order-data))))

  (testing "nonexistent site order returns nil"
    (let [st {:site-orders {}}
          result (store/site-order st "nonexistent")]
      (is (nil? result)))))

(deftest site-order-registered-test
  (testing "registered site order returns true"
    (let [st {:site-orders {"order-001" {:client-site-id "site-42"}}}
          result (store/site-order-registered? st "order-001")]
      (is (true? result))))

  (testing "unregistered site order returns false"
    (let [st {:site-orders {}}
          result (store/site-order-registered? st "order-999")]
      (is (false? result)))))

;; ──────────────────────── Site-Order Status Checks ──────────────────────

(deftest site-order-already-logged-test
  (testing "logged site order is detected"
    (let [st {:site-orders {"order-001" {:logged? true}}}
          result (store/site-order-already-logged? st "order-001")]
      (is (true? result))))

  (testing "unlogged site order returns false"
    (let [st {:site-orders {"order-001" {:logged? false}}}
          result (store/site-order-already-logged? st "order-001")]
      (is (false? result))))

  (testing "nonexistent site order returns false"
    (let [st {:site-orders {}}
          result (store/site-order-already-logged? st "order-001")]
      (is (false? result)))))

;; ──────────────────────── Site-Order Logging ──────────────────────

(deftest log-service-record-test
  (testing "logging a site order marks it as logged"
    (let [st {:site-orders {}}
          order-data {:service-type :treatment/herbicide-broadcast}
          result (store/log-service-record st "order-001" order-data)]
      (is (true? (get-in result [:site-orders "order-001" :logged?])))))

  (testing "logging preserves site-order data"
    (let [st {:site-orders {}}
          order-data {:service-type :treatment/herbicide-broadcast :client-site-id "site-42"}
          result (store/log-service-record st "order-001" order-data)]
      (is (= (:service-type (get-in result [:site-orders "order-001"])) :treatment/herbicide-broadcast))
      (is (= (:client-site-id (get-in result [:site-orders "order-001"])) "site-42")))))

;; ──────────────────────── Site-Order Scheduling ──────────────────────

(deftest mark-scheduled-test
  (testing "marking a site order marks it as scheduled"
    (let [st {:site-orders {"order-001" {:client-site-id "site-42"}}}
          result (store/mark-scheduled st "order-001")]
      (is (true? (get-in result [:site-orders "order-001" :scheduled?]))))))

;; ──────────────────────── Audit Trail ──────────────────────

(deftest audit-trail-test
  (testing "audit trail is initially empty"
    (let [st {:facts []}
          result (store/audit-trail st)]
      (is (empty? result))))

  (testing "appended facts appear in audit trail"
    (let [st {:facts []}
          fact1 {:t :test-fact :detail "test 1"}
          fact2 {:t :test-fact :detail "test 2"}
          st' (store/append-fact st fact1)
          st'' (store/append-fact st' fact2)
          result (store/audit-trail st'')]
      (is (= (count result) 2))
      (is (= (first result) fact1))
      (is (= (second result) fact2)))))

(deftest append-fact-test
  (testing "appending a fact increases ledger length"
    (let [st {:facts []}
          fact {:t :governor-hold :op :log-service-record}
          result (store/append-fact st fact)]
      (is (= (count (:facts result)) 1))
      (is (= (first (:facts result)) fact)))))
