(ns landscapecare.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [landscapecare.governor :as governor]))

(def ^:private now-ms #?(:clj (System/currentTimeMillis) :cljs (.now js/Date)))
(def ^:private ten-days-ago (- now-ms (* 10 24 60 60 1000)))
(def ^:private hundred-days-ago (- now-ms (* 100 24 60 60 1000)))
(def ^:private ten-days-from-now (+ now-ms (* 10 24 60 60 1000)))

(def ^:private evidence-checklist
  [:site-contract-record :site-boundary-map :treatment-record
   :applicator-license :weather-log :buffer-zone-assessment])

(def ^:private clean-herbicide-order
  "Baseline clean site order for a chemical-application service type
  (herbicide) -- has license/calibration/REI/wind/buffer specs."
  {:service-type :treatment/herbicide-broadcast
   :jurisdiction :jp/maff
   :client-site-id "site-42"
   :applicator-license-expiry-date ten-days-from-now
   :equipment-last-calibration-date ten-days-ago
   :hours-until-reentry 24
   :wind-speed-kmh 10.0
   :buffer-zone-actual-m 20.0
   :evidence-checklist evidence-checklist})

(def ^:private clean-mowing-order
  "Baseline clean site order for a non-chemical, non-equipment-safety-
  clearance service type (mowing) -- has NO chemical-application or
  equipment-safety-clearance fields at all."
  {:service-type :mowing/routine
   :jurisdiction :jp/maff
   :client-site-id "site-77"
   :evidence-checklist evidence-checklist})

(def ^:private clean-tree-trimming-order
  "Baseline clean site order for an equipment-safety-clearance-required
  service type (tree-trimming) -- has a current clearance, no chemical
  fields."
  {:service-type :pruning/tree-trimming
   :jurisdiction :jp/maff
   :client-site-id "site-88"
   :equipment-safety-clearance-expiry-date ten-days-from-now
   :evidence-checklist evidence-checklist})

;; ──────────────────────── Registration Invariant ──────────────────────

(deftest site-order-not-registered-violation-test
  (testing "log-service-record against a never-registered site order is a hard block"
    (let [store {:site-orders {}}
          req {:op :log-service-record :subject "order-999"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :site-order-not-registered) (:violations result)))))

  (testing "schedule-maintenance-operation against a never-registered site order is a hard block"
    (let [store {:site-orders {}}
          req {:op :schedule-maintenance-operation :subject "order-999"}
          prop {:cites [] :value {} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :site-order-not-registered) (:violations result)))))

  (testing "flag-safety-concern against a never-registered site order is a hard block"
    (let [store {:site-orders {}}
          req {:op :flag-safety-concern :subject "order-999"}
          prop {:cites [{:spec "Field-Report"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :site-order-not-registered) (:violations result)))))

  (testing "coordinate-supply-order against a never-registered site order is a hard block"
    (let [store {:site-orders {}}
          req {:op :coordinate-supply-order :subject "order-999"}
          prop {:cites [{:spec "Supplier-Catalog"}] :value {:jurisdiction :jp/maff :cost-usd 100} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :site-order-not-registered) (:violations result))))))

;; ──────────────────────── Spec Basis ──────────────────────

(deftest spec-basis-violation-test
  (testing "proposal with no jurisdiction citation is a hard violation"
    (let [store {:site-orders {"order-001" clean-herbicide-order}}
          req {:op :log-service-record :subject "order-001"}
          prop {:cites [] :value {:jurisdiction nil}}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :no-spec-basis) (:violations result)))))

  (testing "proposal with proper citation passes spec basis check"
    (let [store {:site-orders {"order-001" clean-herbicide-order}}
          req {:op :log-service-record :subject "order-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result))))))

;; ──────────────────────── Applicator License Violations ──────────────────────

(deftest applicator-license-expired-violation-test
  (testing "expired applicator license triggers hard violation"
    (let [store {:site-orders {"order-001" (assoc clean-herbicide-order
                                                    :applicator-license-expiry-date hundred-days-ago)}}
          req {:op :log-service-record :subject "order-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :applicator-license-expired) (:violations result)))))

  (testing "current applicator license passes"
    (let [store {:site-orders {"order-001" clean-herbicide-order}}
          req {:op :log-service-record :subject "order-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result)))))

  (testing "non-chemical service type never triggers this rule"
    (let [store {:site-orders {"order-002" clean-mowing-order}}
          req {:op :log-service-record :subject "order-002"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :applicator-license-expired) (:violations result)))))))

;; ──────────────────────── Equipment Calibration Violations ──────────────────────

(deftest equipment-calibration-overdue-violation-test
  (testing "overdue equipment calibration triggers hard violation"
    (let [store {:site-orders {"order-001" (assoc clean-herbicide-order
                                                    :equipment-last-calibration-date hundred-days-ago)}}
          req {:op :log-service-record :subject "order-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :equipment-calibration-overdue) (:violations result)))))

  (testing "non-chemical service type never triggers this rule"
    (let [store {:site-orders {"order-002" clean-mowing-order}}
          req {:op :log-service-record :subject "order-002"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :equipment-calibration-overdue) (:violations result)))))))

;; ──────────────────────── Equipment Safety Clearance Violations ──────────────────────

(deftest equipment-safety-clearance-expired-violation-test
  (testing "expired equipment-safety clearance triggers hard violation"
    (let [store {:site-orders {"order-001" (assoc clean-tree-trimming-order
                                                    :equipment-safety-clearance-expiry-date hundred-days-ago)}}
          req {:op :log-service-record :subject "order-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :equipment-safety-clearance-expired) (:violations result)))))

  (testing "current equipment-safety clearance passes"
    (let [store {:site-orders {"order-001" clean-tree-trimming-order}}
          req {:op :log-service-record :subject "order-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result)))))

  (testing "service type that does not require an equipment-safety clearance never triggers this rule"
    (let [store {:site-orders {"order-002" clean-mowing-order}}
          req {:op :log-service-record :subject "order-002"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :equipment-safety-clearance-expired) (:violations result))))))

  (testing "chemical-application service type never triggers this rule (independent dimension)"
    (let [store {:site-orders {"order-003" clean-herbicide-order}}
          req {:op :log-service-record :subject "order-003"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :equipment-safety-clearance-expired) (:violations result)))))))

;; ──────────────────────── Restricted-Entry Interval Violations ──────────────────────

(deftest restricted-entry-interval-violated-violation-test
  (testing "hours-until-reentry below REI triggers hard violation"
    (let [store {:site-orders {"order-001" (assoc clean-herbicide-order :hours-until-reentry 2)}}
          req {:op :log-service-record :subject "order-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :restricted-entry-interval-violated) (:violations result)))))

  (testing "non-chemical service type never triggers this rule"
    (let [store {:site-orders {"order-002" clean-mowing-order}}
          req {:op :log-service-record :subject "order-002"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :restricted-entry-interval-violated) (:violations result)))))))

;; ──────────────────────── Wind Speed Violations ──────────────────────

(deftest wind-speed-exceeded-violation-test
  (testing "wind speed above the service type's ceiling triggers hard violation"
    (let [store {:site-orders {"order-001" (assoc clean-herbicide-order :wind-speed-kmh 30.0)}}
          req {:op :log-service-record :subject "order-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :wind-speed-exceeded) (:violations result)))))

  (testing "pesticide-application service type has a much tighter wind ceiling than herbicide"
    (let [order (assoc clean-herbicide-order
                        :service-type :treatment/pesticide-application
                        :wind-speed-kmh 20.0)
          store {:site-orders {"order-002" order}}
          req {:op :log-service-record :subject "order-002"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :wind-speed-exceeded) (:violations result)))))

  (testing "non-chemical service type never triggers this rule"
    (let [store {:site-orders {"order-003" clean-mowing-order}}
          req {:op :log-service-record :subject "order-003"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :wind-speed-exceeded) (:violations result)))))))

;; ──────────────────────── Buffer Zone Violations ──────────────────────

(deftest buffer-zone-violated-violation-test
  (testing "buffer zone narrower than minimum triggers hard violation"
    (let [store {:site-orders {"order-001" (assoc clean-herbicide-order :buffer-zone-actual-m 5.0)}}
          req {:op :log-service-record :subject "order-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :buffer-zone-violated) (:violations result)))))

  (testing "buffer zone at or above minimum passes"
    (let [store {:site-orders {"order-002" clean-herbicide-order}}
          req {:op :log-service-record :subject "order-002"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result)))))

  (testing "non-chemical service type never triggers this rule"
    (let [store {:site-orders {"order-003" clean-mowing-order}}
          req {:op :log-service-record :subject "order-003"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :buffer-zone-violated) (:violations result)))))))

;; ──────────────────────── Evidence Completeness ──────────────────────

(deftest evidence-incomplete-violation-test
  (testing "incomplete evidence checklist triggers hard violation"
    (let [store {:site-orders {"order-001" (assoc clean-herbicide-order
                                                    :evidence-checklist [:site-contract-record])}}
          req {:op :log-service-record :subject "order-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :evidence-incomplete) (:violations result))))))

;; ──────────────── Equipment-Safety-or-Pesticide-Decision Block (boolean flag) ────────────

(deftest equipment-safety-or-pesticide-decision-blocked-violation-test
  (testing "a proposal covertly requesting a final equipment-safety clearance is a hard, permanent block"
    (let [store {:site-orders {"order-001" clean-herbicide-order}}
          req {:op :schedule-maintenance-operation :subject "order-001"}
          prop {:cites [] :value {:finalize-equipment-safety-clearance? true} :confidence 0.99}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :equipment-safety-or-pesticide-decision-blocked) (:violations result)))))

  (testing "a proposal covertly requesting a final pesticide-application decision is a hard, permanent block"
    (let [store {:site-orders {"order-001" clean-herbicide-order}}
          req {:op :log-service-record :subject "order-001"}
          prop {:cites [{:spec "ISO-12345"}]
                :value {:jurisdiction :jp/maff :finalize-pesticide-application-decision? true}
                :confidence 0.99}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :equipment-safety-or-pesticide-decision-blocked) (:violations result))))))

;; ──────────────── Scope-Exclusion Text Scan (defense-in-depth) ────────────

(deftest scope-exclusion-text-violation-test
  (testing "reasoning text that explicitly finalizes an equipment-safety clearance is a hard, permanent block"
    (let [store {:site-orders {"order-001" clean-herbicide-order}}
          req {:op :schedule-maintenance-operation :subject "order-001"}
          prop {:cites [] :value {} :confidence 0.9
                :reasoning "Proposing to finalize the equipment-safety clearance for this crew."}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :scope-exclusion-text) (:violations result)))))

  (testing "reasoning text that explicitly finalizes a pesticide-application decision is a hard, permanent block"
    (let [store {:site-orders {"order-001" clean-herbicide-order}}
          req {:op :log-service-record :subject "order-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.9
                :reasoning "This log entry is finalizing the pesticide-application decision for the site."}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :scope-exclusion-text) (:violations result)))))

  (testing "legitimate proposals that merely MENTION safety/equipment/pesticide words, without a finalization verb, are never scope-excluded"
    (let [store {:site-orders {"order-001" clean-herbicide-order}}
          req {:op :flag-safety-concern :subject "order-001"}
          prop {:cites [{:spec "Field-Report"}]
                :value {:jurisdiction :jp/maff}
                :confidence 0.9
                :reasoning "Equipment-hazard and pesticide-drift concern observed at the site; surfaced for human review, not resolved by this proposal."}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :scope-exclusion-text) (:violations result))))
      (is (not (some #(= (:rule %) :equipment-safety-or-pesticide-decision-blocked) (:violations result)))))))

;; ──────────────────────── Safety-Concern Flag Violations ──────────────────────

(deftest safety-concern-flag-unresolved-violation-test
  (testing "an unresolved safety-concern flag triggers hard violation"
    (let [store {:site-orders {"order-001" (assoc clean-herbicide-order
                                                    :safety-concern-raised? true
                                                    :safety-concern-resolved? false)}}
          req {:op :log-service-record :subject "order-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :safety-concern-flag-unresolved) (:violations result)))))

  (testing "a resolved safety-concern flag does not trigger this rule"
    (let [store {:site-orders {"order-002" (assoc clean-herbicide-order
                                                    :safety-concern-raised? true
                                                    :safety-concern-resolved? true)}}
          req {:op :log-service-record :subject "order-002"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :safety-concern-flag-unresolved) (:violations result)))))))

;; ──────────────────────── Escalation (Low Confidence) ──────────────────────

(deftest low-confidence-escalation-test
  (testing "low confidence proposal escalates even when hard checks pass"
    (let [store {:site-orders {"order-001" clean-herbicide-order}}
          req {:op :schedule-maintenance-operation :subject "order-001"}
          prop {:cites [] :value {} :confidence 0.5}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))

;; ──────────────────────── High Stakes Escalation ──────────────────────

(deftest high-stakes-escalation-test
  (testing "log-service-record escalates even when all checks pass"
    (let [store {:site-orders {"order-001" clean-herbicide-order}}
          req {:op :log-service-record :subject "order-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.95}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))

;; ──────────────────────── Safety Concern Always Escalates ──────────────────────

(deftest safety-concern-always-escalates-test
  (testing "a clean flag-safety-concern proposal is never auto-ok"
    (let [store {:site-orders {"order-001" clean-herbicide-order}}
          req {:op :flag-safety-concern :subject "order-001"}
          prop {:cites [{:spec "Field-Report"}] :value {:jurisdiction :jp/maff} :confidence 0.99}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))

;; ──────────────────────── High-Cost Supply Order Escalation ──────────────────────

(deftest high-cost-supply-order-escalation-test
  (testing "a supply order above the cost threshold escalates even when clean"
    (let [store {:site-orders {"order-001" clean-herbicide-order}}
          req {:op :coordinate-supply-order :subject "order-001"}
          prop {:cites [{:spec "Supplier-Catalog"}]
                :value {:jurisdiction :jp/maff :cost-usd 10000}
                :confidence 0.9}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result)))))

  (testing "a supply order at or below the cost threshold does not force escalation"
    (let [store {:site-orders {"order-002" clean-herbicide-order}}
          req {:op :coordinate-supply-order :subject "order-002"}
          prop {:cites [{:spec "Supplier-Catalog"}]
                :value {:jurisdiction :jp/maff :cost-usd 1000}
                :confidence 0.9}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:ok? result))))))

;; ──────────────────────── Already Logged Violation ──────────────────────

(deftest already-logged-violation-test
  (testing "site order already logged triggers hard violation"
    (let [store {:site-orders {"order-001"
                                {:service-type :treatment/herbicide-broadcast
                                 :logged? true}}}
          req {:op :log-service-record :subject "order-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :already-logged) (:violations result))))))

;; ──────────────────────── Op-Not-Allowed Violation ──────────────────────

(deftest op-not-allowed-violation-test
  (testing "an out-of-allowlist op (e.g. directly finalizing an equipment-safety clearance) is a hard, permanent block"
    (let [store {:site-orders {"order-001" clean-herbicide-order}}
          req {:op :finalize-equipment-safety-clearance :subject "order-001"}
          prop {:cites [{:spec "Equipment-Manual"}] :value {:jurisdiction :jp/maff} :confidence 0.99}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :op-not-allowed) (:violations result))))))

;; ──────────────────────── Effect-Not-Propose Violation ──────────────────────

(deftest effect-not-propose-violation-test
  (testing "a proposal asserting a non-:propose effect is a hard, permanent block"
    (let [store {:site-orders {"order-001" clean-herbicide-order}}
          req {:op :schedule-maintenance-operation :subject "order-001"}
          prop {:effect :commit :cites [] :value {} :confidence 0.9}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :effect-not-propose) (:violations result))))))
