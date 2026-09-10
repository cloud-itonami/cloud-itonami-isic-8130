(ns landscapecare.operation-test
  (:require [clojure.test :refer [deftest is testing]]
            [landscapecare.operation :as operation]
            [landscapecare.governor :as governor]))

(def ^:private now-ms #?(:clj (System/currentTimeMillis) :cljs (.now js/Date)))
(def ^:private ten-days-ago (- now-ms (* 10 24 60 60 1000)))
(def ^:private ten-days-from-now (+ now-ms (* 10 24 60 60 1000)))

(def ^:private clean-herbicide-order
  {:service-type :treatment/herbicide-broadcast
   :jurisdiction :jp/maff
   :client-site-id "site-42"
   :applicator-license-expiry-date ten-days-from-now
   :equipment-last-calibration-date ten-days-ago
   :hours-until-reentry 24
   :wind-speed-kmh 10.0
   :buffer-zone-actual-m 20.0
   :evidence-checklist [:site-contract-record :site-boundary-map :treatment-record
                        :applicator-license :weather-log :buffer-zone-assessment]})

(deftest run-operation-commit-test
  (testing "clean, non-actuation proposal commits with no hold facts"
    (let [store {:site-orders {"order-001" clean-herbicide-order}}
          request {:op :schedule-maintenance-operation :subject "order-001"}
          proposal {:cites [{:spec "Site-Schedule"}]
                    :value {:jurisdiction :jp/maff}
                    :effect :propose
                    :confidence 0.9}
          context {:actor-id "op-1" :hold-fact-fn governor/hold-fact}
          result (operation/run-operation request context proposal store governor/check)]
      (is (true? (:ok? result)))
      (is (= [] (:facts result))))))

(deftest run-operation-hold-test
  (testing "hard-violating proposal (already-logged order) produces a hold fact"
    (let [store {:site-orders {"order-002" {:service-type :treatment/herbicide-broadcast
                                             :logged? true}}}
          request {:op :log-service-record :subject "order-002"}
          proposal {:cites [{:spec "ISO-12345"}]
                    :value {:jurisdiction :jp/maff}
                    :effect :propose
                    :confidence 0.9}
          context {:actor-id "op-1" :hold-fact-fn governor/hold-fact}
          result (operation/run-operation request context proposal store governor/check)]
      (is (false? (:ok? result)))
      (is (= 1 (count (:facts result))))
      (is (= :governor-hold (:t (first (:facts result)))))
      (is (true? (:hard? (:verdict result)))))))

(deftest run-operation-escalate-test
  (testing "clean but high-stakes proposal is not auto-ok (escalation required)"
    (let [store {:site-orders {"order-003" clean-herbicide-order}}
          request {:op :log-service-record :subject "order-003"}
          proposal {:cites [{:spec "ISO-12345"}]
                    :value {:jurisdiction :jp/maff}
                    :effect :propose
                    :confidence 0.95}
          context {:actor-id "op-1" :hold-fact-fn governor/hold-fact}
          result (operation/run-operation request context proposal store governor/check)]
      (is (false? (:ok? result)))
      (is (false? (:hard? (:verdict result))))
      (is (true? (:escalate? (:verdict result))))
      ;; operation.cljc has a single :ok?/not-ok? gate today; both hard-hold
      ;; and escalate-only verdicts route through the same hold-fact-fn.
      ;; Callers distinguish the two by inspecting `(:verdict result)`.
      (is (= 1 (count (:facts result)))))))

(deftest run-operation-safety-concern-always-escalates-test
  (testing "a clean flag-safety-concern proposal is never auto-ok"
    (let [store {:site-orders {"order-004" clean-herbicide-order}}
          request {:op :flag-safety-concern :subject "order-004"}
          proposal {:cites [{:spec "Field-Report"}]
                    :value {:jurisdiction :jp/maff}
                    :effect :propose
                    :confidence 0.99}
          context {:actor-id "op-1" :hold-fact-fn governor/hold-fact}
          result (operation/run-operation request context proposal store governor/check)]
      (is (false? (:ok? result)))
      (is (false? (:hard? (:verdict result))))
      (is (true? (:escalate? (:verdict result)))))))

(deftest run-operation-op-not-allowed-test
  (testing "an out-of-allowlist op (e.g. directly finalizing an equipment-safety clearance) is a hard, permanent block"
    (let [store {:site-orders {"order-005" clean-herbicide-order}}
          request {:op :finalize-equipment-safety-clearance :subject "order-005"}
          proposal {:cites [{:spec "Equipment-Manual"}]
                    :value {:jurisdiction :jp/maff}
                    :effect :propose
                    :confidence 0.99}
          context {:actor-id "op-1" :hold-fact-fn governor/hold-fact}
          result (operation/run-operation request context proposal store governor/check)]
      (is (false? (:ok? result)))
      (is (true? (:hard? (:verdict result))))
      (is (some #(= (:rule %) :op-not-allowed) (:violations (:verdict result)))))))

(deftest run-operation-effect-not-propose-test
  (testing "a proposal asserting a non-:propose effect is a hard, permanent block"
    (let [store {:site-orders {"order-006" clean-herbicide-order}}
          request {:op :schedule-maintenance-operation :subject "order-006"}
          proposal {:cites [{:spec "Site-Schedule"}]
                    :value {:jurisdiction :jp/maff}
                    :effect :commit
                    :confidence 0.9}
          context {:actor-id "op-1" :hold-fact-fn governor/hold-fact}
          result (operation/run-operation request context proposal store governor/check)]
      (is (false? (:ok? result)))
      (is (true? (:hard? (:verdict result))))
      (is (some #(= (:rule %) :effect-not-propose) (:violations (:verdict result)))))))

(deftest run-operation-site-order-not-registered-test
  (testing "any op against a never-registered site order is a hard block"
    (let [store {:site-orders {}}
          request {:op :schedule-maintenance-operation :subject "order-999"}
          proposal {:cites []
                    :value {}
                    :effect :propose
                    :confidence 0.9}
          context {:actor-id "op-1" :hold-fact-fn governor/hold-fact}
          result (operation/run-operation request context proposal store governor/check)]
      (is (false? (:ok? result)))
      (is (true? (:hard? (:verdict result))))
      (is (some #(= (:rule %) :site-order-not-registered) (:violations (:verdict result)))))))

(deftest run-operation-high-cost-supply-order-escalates-test
  (testing "a supply order above the cost threshold is not auto-ok"
    (let [store {:site-orders {"order-007" clean-herbicide-order}}
          request {:op :coordinate-supply-order :subject "order-007"}
          proposal {:cites [{:spec "Supplier-Catalog"}]
                    :value {:jurisdiction :jp/maff :cost-usd 10000}
                    :effect :propose
                    :confidence 0.9}
          context {:actor-id "op-1" :hold-fact-fn governor/hold-fact}
          result (operation/run-operation request context proposal store governor/check)]
      (is (false? (:ok? result)))
      (is (false? (:hard? (:verdict result))))
      (is (true? (:escalate? (:verdict result)))))))

(deftest run-operation-low-cost-supply-order-commits-test
  (testing "a supply order at or below the cost threshold commits when clean"
    (let [store {:site-orders {"order-008" clean-herbicide-order}}
          request {:op :coordinate-supply-order :subject "order-008"}
          proposal {:cites [{:spec "Supplier-Catalog"}]
                    :value {:jurisdiction :jp/maff :cost-usd 1000}
                    :effect :propose
                    :confidence 0.9}
          context {:actor-id "op-1" :hold-fact-fn governor/hold-fact}
          result (operation/run-operation request context proposal store governor/check)]
      (is (true? (:ok? result)))
      (is (= [] (:facts result))))))
