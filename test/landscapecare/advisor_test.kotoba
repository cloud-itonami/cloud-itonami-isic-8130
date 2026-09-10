(ns landscapecare.advisor-test
  "Dedicated regression test for a known bug class in this actor family:
  a Governor scope-exclusion term list phrased as a bare noun (e.g.
  \"pesticide\", \"equipment\", \"safety\") can accidentally match inside
  the mock advisor's OWN default rationale/disclaimer text for an
  entirely legitimate, allowed proposal -- causing the actor to
  self-block on its own happy path. This test asserts the advisor's
  default proposal for EVERY op in the closed allowlist is checked
  against the live Governor and NEVER trips either scope-exclusion
  check (`:equipment-safety-or-pesticide-decision-blocked` or
  `:scope-exclusion-text`)."
  (:require [clojure.test :refer [deftest is testing]]
            [landscapecare.advisor :as advisor]
            [landscapecare.governor :as governor]))

(def ^:private scope-exclusion-rules
  #{:equipment-safety-or-pesticide-decision-blocked :scope-exclusion-text})

(deftest mock-advisor-defaults-never-self-trip-scope-exclusion-test
  (testing "none of the advisor's own default proposals for any op in the closed allowlist ever self-trip either scope-exclusion check"
    (doseq [{:keys [request proposal]} (advisor/default-proposals "site-001")]
      ;; Empty store deliberately -- other hard checks (e.g.
      ;; :site-order-not-registered) MAY legitimately fire here; this
      ;; test isolates ONLY the two scope-exclusion rules, which the
      ;; Governor evaluates unconditionally regardless of registration.
      (let [store {:site-orders {}}
            result (governor/check request {:actor-id "gov-1"} proposal store)
            offending (filter #(contains? scope-exclusion-rules (:rule %))
                               (:violations result))]
        (is (empty? offending)
            (str (:op request) "'s default advisor proposal self-tripped scope-exclusion: "
                 (pr-str offending)))))))

(deftest mock-advisor-defaults-shape-test
  (testing "the advisor returns exactly one default proposal per allowlisted op, all :effect :propose"
    (let [defaults (advisor/default-proposals "site-001")
          ops (set (map (comp :op :request) defaults))]
      (is (= 4 (count defaults)))
      (is (= governor/allowed-ops ops))
      (is (every? #(= :propose (get-in % [:proposal :effect])) defaults)))))

(deftest advise-flag-safety-concern-always-would-escalate-test
  (testing "the advisor's own default flag-safety-concern proposal is never governor-:ok? even when clean and registered"
    (let [{:keys [request proposal]} (advisor/advise-flag-safety-concern
                                       "site-001" :jp/maff :equipment-hazard "guard missing")
          store {:site-orders {"site-001" {:service-type :mowing/routine
                                            :jurisdiction :jp/maff
                                            :evidence-checklist [:site-contract-record :site-boundary-map
                                                                  :treatment-record :applicator-license
                                                                  :weather-log :buffer-zone-assessment]}}}
          result (governor/check request {:actor-id "gov-1"} proposal store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))
