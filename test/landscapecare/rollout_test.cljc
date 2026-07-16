(ns landscapecare.rollout-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [landscapecare.governor :as governor]
            [landscapecare.rollout :as rollout]))

(deftest flag-safety-concern-never-auto-eligible-test
  (testing ":flag-safety-concern is never in any rollout tier's :auto set, at any tier"
    (doseq [tier (keys rollout/tiers)]
      (is (false? (rollout/auto-eligible-at-tier? tier :flag-safety-concern))
          (str "tier " tier " must never auto-commit :flag-safety-concern")))))

(deftest log-service-record-never-auto-eligible-test
  (testing ":log-service-record (the one real actuation event) is never in any rollout tier's :auto set"
    (doseq [tier (keys rollout/tiers)]
      (is (false? (rollout/auto-eligible-at-tier? tier :log-service-record))
          (str "tier " tier " must never auto-commit :log-service-record")))))

(deftest no-out-of-allowlist-op-in-any-auto-set-test
  (testing "every tier's :auto set is a subset of the closed op allowlist -- never a finalize-equipment-safety-clearance or finalize-pesticide-application-decision style op"
    (doseq [[_tier {:keys [auto]}] rollout/tiers]
      (is (every? governor/allowed-ops auto)))))

(deftest tier-writes-monotonically-non-decreasing-test
  (testing "auto is always a subset of writes at every tier (never auto-commit something not even proposable)"
    (doseq [[_tier {:keys [writes auto]}] rollout/tiers]
      (is (every? writes auto)))))

(deftest rollout-consistent-test
  (testing "the rollout tier tables and the governor's always-escalate-ops never drift apart"
    (is (true? (rollout/rollout-consistent?)))))

(deftest tier-3-auto-set-is-exactly-non-escalating-ops-test
  (testing "at the highest rollout tier, :auto is exactly the allowlisted ops minus the always-escalate ops"
    (is (= (set/difference governor/allowed-ops governor/always-escalate-ops)
           (get-in rollout/tiers [3 :auto])))))
