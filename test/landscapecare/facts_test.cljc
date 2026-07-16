(ns landscapecare.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [landscapecare.facts :as facts]))

;; ──────────────────────── Service-Type Lookups ──────────────────────

(deftest service-type-by-id-test
  (testing "herbicide-broadcast service type exists"
    (let [s (facts/service-type-by-id :treatment/herbicide-broadcast)]
      (is (some? s))
      (is (= (:id s) :treatment/herbicide-broadcast))
      (is (true? (:chemical-application? s)))
      (is (= (:restricted-entry-interval-hours s) 12))))

  (testing "pesticide-application service type has a much stricter window than herbicide"
    (let [s (facts/service-type-by-id :treatment/pesticide-application)]
      (is (some? s))
      (is (true? (:chemical-application? s)))
      (is (= (:restricted-entry-interval-hours s) 24))
      (is (= (:max-wind-speed-kmh s) 16.0))))

  (testing "mowing service type exists and has no chemical or equipment-safety-clearance spec"
    (let [s (facts/service-type-by-id :mowing/routine)]
      (is (some? s))
      (is (false? (:chemical-application? s)))
      (is (false? (:equipment-safety-clearance-required? s)))
      (is (nil? (:restricted-entry-interval-hours s)))
      (is (nil? (:max-wind-speed-kmh s)))))

  (testing "irrigation zone-monitoring service type has no chemical or equipment-safety-clearance spec"
    (let [s (facts/service-type-by-id :irrigation/zone-monitoring)]
      (is (some? s))
      (is (false? (:chemical-application? s)))
      (is (false? (:equipment-safety-clearance-required? s)))))

  (testing "tree-trimming service type requires an equipment-safety clearance but has no chemical spec"
    (let [s (facts/service-type-by-id :pruning/tree-trimming)]
      (is (some? s))
      (is (false? (:chemical-application? s)))
      (is (true? (:equipment-safety-clearance-required? s)))
      (is (nil? (:restricted-entry-interval-hours s)))))

  (testing "nonexistent service type returns nil"
    (is (nil? (facts/service-type-by-id :nonexistent/service)))))

;; ──────────────────────── Jurisdiction Lookups ──────────────────────

(deftest jurisdiction-by-id-test
  (testing "JP MAFF jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :jp/maff)]
      (is (some? j))
      (is (contains? (set (:required-evidence j)) :applicator-license))))

  (testing "US EPA jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :us/epa)]
      (is (some? j))
      (is (contains? (set (:required-evidence j)) :buffer-zone-assessment))))

  (testing "EU REG1107 jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :eu/reg1107)]
      (is (some? j))
      (is (contains? (set (:required-evidence j)) :weather-log))))

  (testing "nonexistent jurisdiction returns nil"
    (is (nil? (facts/jurisdiction-by-id :xx/unknown)))))

;; ──────────────────────── Landscape-Care-Service Safety Predicates ──────

(deftest applicator-license-current-test
  (let [herbicide (facts/service-type-by-id :treatment/herbicide-broadcast)
        mowing (facts/service-type-by-id :mowing/routine)]
    (testing "license expiring in the future is current"
      (is (true? (facts/applicator-license-current? 2000 1000 herbicide))))

    (testing "license expiring in the past is not current"
      (is (false? (facts/applicator-license-current? 500 1000 herbicide))))

    (testing "non-chemical service type never needs a license"
      (is (false? (facts/applicator-license-current? 2000 1000 mowing))))))

(deftest equipment-calibration-current-test
  (let [herbicide (facts/service-type-by-id :treatment/herbicide-broadcast)
        mowing (facts/service-type-by-id :mowing/routine)
        now 1000000
        ten-days-ago (- now (* 10 24 60 60 1000))
        hundred-days-ago (- now (* 100 24 60 60 1000))]
    (testing "recent calibration is current"
      (is (true? (facts/equipment-calibration-current? ten-days-ago now herbicide))))

    (testing "overdue calibration is not current"
      (is (false? (facts/equipment-calibration-current? hundred-days-ago now herbicide))))

    (testing "non-chemical service type never needs calibration"
      (is (false? (facts/equipment-calibration-current? ten-days-ago now mowing))))))

(deftest equipment-safety-clearance-current-test
  (let [tree-trimming (facts/service-type-by-id :pruning/tree-trimming)
        mowing (facts/service-type-by-id :mowing/routine)]
    (testing "clearance expiring in the future is current"
      (is (true? (facts/equipment-safety-clearance-current? 2000 1000 tree-trimming))))

    (testing "clearance expiring in the past is not current"
      (is (false? (facts/equipment-safety-clearance-current? 500 1000 tree-trimming))))

    (testing "service type with no equipment-safety-clearance requirement never needs one"
      (is (false? (facts/equipment-safety-clearance-current? 2000 1000 mowing))))))

(deftest restricted-entry-interval-satisfied-test
  (let [pest-control (facts/service-type-by-id :treatment/pesticide-application)
        mowing (facts/service-type-by-id :mowing/routine)]
    (testing "hours-until-reentry at or above REI passes"
      (is (true? (facts/restricted-entry-interval-satisfied? 24 pest-control)))
      (is (true? (facts/restricted-entry-interval-satisfied? 48 pest-control))))

    (testing "hours-until-reentry below REI fails"
      (is (false? (facts/restricted-entry-interval-satisfied? 6 pest-control))))

    (testing "non-chemical service type has no REI to satisfy"
      (is (false? (facts/restricted-entry-interval-satisfied? 6 mowing))))))

(deftest wind-speed-in-range-test
  (let [pest-control (facts/service-type-by-id :treatment/pesticide-application)
        mowing (facts/service-type-by-id :mowing/routine)]
    (testing "wind speed at or below ceiling passes"
      (is (true? (facts/wind-speed-in-range? 16.0 pest-control)))
      (is (true? (facts/wind-speed-in-range? 5.0 pest-control))))

    (testing "wind speed above ceiling fails"
      (is (false? (facts/wind-speed-in-range? 20.0 pest-control))))

    (testing "non-chemical service type has no wind-speed ceiling"
      (is (false? (facts/wind-speed-in-range? 5.0 mowing))))))

(deftest buffer-zone-in-range-test
  (let [pest-control (facts/service-type-by-id :treatment/pesticide-application)
        mowing (facts/service-type-by-id :mowing/routine)]
    (testing "buffer distance at or above minimum passes"
      (is (true? (facts/buffer-zone-in-range? 30.0 pest-control)))
      (is (true? (facts/buffer-zone-in-range? 50.0 pest-control))))

    (testing "buffer distance below minimum fails"
      (is (false? (facts/buffer-zone-in-range? 10.0 pest-control))))

    (testing "non-chemical service type has no buffer-zone minimum"
      (is (false? (facts/buffer-zone-in-range? 50.0 mowing))))))

;; ──────────────────────── Evidence Completeness ──────────────────────

(deftest required-evidence-satisfied-test
  (testing "complete evidence checklist passes"
    (let [j (facts/jurisdiction-by-id :jp/maff)
          evidence [:site-contract-record :site-boundary-map :treatment-record
                    :applicator-license :weather-log :buffer-zone-assessment]]
      (is (true? (facts/required-evidence-satisfied? j evidence)))))

  (testing "incomplete evidence fails"
    (let [j (facts/jurisdiction-by-id :jp/maff)
          evidence [:site-contract-record :site-boundary-map]]
      (is (false? (facts/required-evidence-satisfied? j evidence)))))

  (testing "raw jurisdiction id call convention also works"
    (let [evidence [:site-contract-record :site-boundary-map :treatment-record
                    :applicator-license :weather-log :buffer-zone-assessment]]
      (is (true? (facts/required-evidence-satisfied? :us/epa evidence)))))

  (testing "unknown jurisdiction never satisfies"
    (is (false? (facts/required-evidence-satisfied? :xx/unknown [])))))
