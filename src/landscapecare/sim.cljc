(ns landscapecare.sim
  "Simulation driver for exercising the landscape-care operations-
  coordination actor end-to-end, with a demo run of the mock advisor's
  default proposals against a small in-memory store.

  For CLI: clojure -M:run

  Example flow:
    1. Start with a store holding one registered, clean site order
    2. Run each of the mock advisor's default proposals
       (`:schedule-maintenance-operation`, `:log-service-record`,
       `:flag-safety-concern`, `:coordinate-supply-order`) through the
       Governor
    3. Print each verdict (commit / escalate / hard-hold) and the
       resulting audit trail"
  (:require [landscapecare.advisor :as advisor]
            [landscapecare.governor :as governor]
            [landscapecare.operation :as operation]
            [landscapecare.store :as store]))

(def ^:private demo-site-id "site-demo-001")

(def ^:private demo-store
  {:site-orders
   {demo-site-id
    {:service-type :mowing/routine
     :jurisdiction :jp/maff
     :client-site-id demo-site-id
     :evidence-checklist [:site-contract-record :site-boundary-map
                           :treatment-record :applicator-license
                           :weather-log :buffer-zone-assessment]}}
   :facts []})

(defn- describe-verdict [{:keys [op]} result]
  (cond
    (:ok? result) (str op " -> COMMIT (governor clean, no mandatory escalation)")
    (get-in result [:verdict :hard?]) (str op " -> HARD HOLD "
                                            (mapv :rule (get-in result [:verdict :violations])))
    :else (str op " -> ESCALATE (human sign-off required)")))

(defn -main [& _args]
  (println "LandscapeCare simulation (ISIC 8130) -- deterministic mock advisor demo")
  (println "TODO: integrate langgraph-clj StateGraph when available.")
  (let [context {:actor-id "landscape-care-advisor-1" :hold-fact-fn governor/hold-fact}
        proposals (advisor/default-proposals demo-site-id)
        ledger (reduce
                (fn [ledger {:keys [request proposal]}]
                  (let [result (operation/run-operation request context proposal demo-store governor/check)]
                    (println (describe-verdict request result))
                    (reduce store/append-fact ledger (:facts result))))
                {:facts []}
                proposals)]
    (println "Note: this demo store is never mutated by these proposals --")
    (println "committing to the store is a separate, production-only step gated on human sign-off.")
    (println "Audit trail entries recorded this run:" (count (store/audit-trail ledger)))))
