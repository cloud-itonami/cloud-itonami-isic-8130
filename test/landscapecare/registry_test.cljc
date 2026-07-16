(ns landscapecare.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [landscapecare.registry :as registry]))

;; ──────────────────────── Applicator License ──────────────────────

(deftest applicator-license-expired-test
  (testing "expiry in the future returns false (no violation)"
    (is (false? (registry/applicator-license-expired? 2000 1000))))

  (testing "expiry exactly now returns false"
    (is (false? (registry/applicator-license-expired? 1000 1000))))

  (testing "expiry in the past returns true (violation)"
    (is (true? (registry/applicator-license-expired? 500 1000)))))

;; ──────────────────────── Equipment Calibration ──────────────────────

(deftest equipment-calibration-overdue-test
  (testing "recent calibration returns false (no violation)"
    (let [now 1000000000
          ten-days-ago (- now (* 10 24 60 60 1000))]
      (is (false? (registry/equipment-calibration-overdue? ten-days-ago now)))))

  (testing "overdue calibration returns true (violation)"
    (let [now 1000000000
          hundred-days-ago (- now (* 100 24 60 60 1000))]
      (is (true? (registry/equipment-calibration-overdue? hundred-days-ago now))))))

;; ──────────────────────── Equipment Safety Clearance ──────────────────────

(deftest equipment-safety-clearance-expired-test
  (testing "expiry in the future returns false (no violation)"
    (is (false? (registry/equipment-safety-clearance-expired? 2000 1000))))

  (testing "expiry exactly now returns false"
    (is (false? (registry/equipment-safety-clearance-expired? 1000 1000))))

  (testing "expiry in the past returns true (violation)"
    (is (true? (registry/equipment-safety-clearance-expired? 500 1000)))))

;; ──────────────────────── Restricted-Entry Interval ──────────────────────

(deftest restricted-entry-interval-violated-test
  (testing "hours-until-reentry at REI returns false (no violation)"
    (is (false? (registry/restricted-entry-interval-violated? 12 12))))

  (testing "hours-until-reentry above REI returns false"
    (is (false? (registry/restricted-entry-interval-violated? 24 12))))

  (testing "hours-until-reentry below REI returns true (violation)"
    (is (true? (registry/restricted-entry-interval-violated? 3 12)))))

;; ──────────────────────── Wind Speed ──────────────────────

(deftest wind-speed-exceeded-test
  (testing "wind at or below ceiling returns false (no violation)"
    (is (false? (registry/wind-speed-exceeded? 24.0 24.0)))
    (is (false? (registry/wind-speed-exceeded? 10.0 24.0))))

  (testing "wind above ceiling returns true (violation)"
    (is (true? (registry/wind-speed-exceeded? 30.0 24.0)))))

;; ──────────────────────── Buffer Zone ──────────────────────

(deftest buffer-zone-violated-test
  (testing "buffer at or above minimum returns false (no violation)"
    (is (false? (registry/buffer-zone-violated? 15.0 15.0)))
    (is (false? (registry/buffer-zone-violated? 25.0 15.0))))

  (testing "buffer below minimum returns true (violation)"
    (is (true? (registry/buffer-zone-violated? 5.0 15.0)))))
