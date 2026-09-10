(ns landscapecare.advisor
  "Landscape Care Advisor -- the LLM/decision-maker that proposes
  landscape-care operations-coordination actions.

  The advisor operates purely at the proposal level; the Governor
  (`landscapecare.governor`) independently validates all proposals
  against physical/safety/regulatory rules before any action is
  committed. The advisor NEVER finalizes an equipment-safety clearance
  or a pesticide-application decision itself -- it only ever proposes,
  with `:effect :propose`.

  In production, this is driven by langgraph-clj StateGraph with LLM
  chat turns. For demo/tests, `default-*`/`advise-*` below is a pure,
  deterministic mock advisor -- no network call, no randomness. Its
  rationale text is written carefully to describe each proposal's
  logistics/administrative purpose WITHOUT ever using the scope-
  exclusion finalization phrasing the Governor's
  `scope-exclusion-text-violations` check scans for (see
  `landscapecare.governor` docstring) -- this is verified by a dedicated
  regression test in `landscapecare.advisor-test`, not merely asserted
  here."
  )

(defn advise-log-service-record
  "Deterministic mock-advisor proposal: log a completed field-visit
  service record (mowing/pruning/treatment-visit data plus the safety
  and compliance parameters that accompanied the visit) for `site-id`.
  This is the one real actuation event this actor performs, so the
  Governor always routes it to human sign-off -- this proposal only ever
  proposes the logging, it never itself finalizes anything."
  [site-id jurisdiction]
  {:request {:op :log-service-record :subject site-id}
   :proposal {:cites [{:spec "site-treatment-record"}]
              :value {:jurisdiction jurisdiction}
              :effect :propose
              :confidence 0.85
              :reasoning "Field-visit service-record logging for completed, routine work -- mowing/pruning/treatment-visit data and the safety and compliance parameters observed during the visit."}})

(defn advise-schedule-maintenance-operation
  "Deterministic mock-advisor proposal: propose crew/equipment/site
  scheduling for an upcoming maintenance visit at `site-id`. Purely
  administrative coordination -- no dispatch authority, no equipment
  operation."
  [site-id jurisdiction]
  {:request {:op :schedule-maintenance-operation :subject site-id}
   :proposal {:cites [{:spec "crew-roster"}]
              :value {:jurisdiction jurisdiction}
              :effect :propose
              :confidence 0.82
              :reasoning "Crew and equipment scheduling proposal for an upcoming site visit; administrative coordination only."}})

(defn advise-flag-safety-concern
  "Deterministic mock-advisor proposal: surface an equipment-hazard or
  pesticide/herbicide-drift concern observed in the field for `site-id`.
  ALWAYS escalates to a human -- this proposal only ever raises the
  concern for review, it never itself resolves or clears it, and it must
  never appear in any phase's auto-commit set."
  [site-id jurisdiction concern-type description]
  {:request {:op :flag-safety-concern :subject site-id}
   :proposal {:cites [{:spec "field-observation-report"}]
              :value {:jurisdiction jurisdiction
                      :concern-type concern-type
                      :description description}
              :effect :propose
              :confidence 0.9
              :reasoning "Field-observed concern surfaced for immediate human review; raising this concern does not resolve it."}})

(defn advise-coordinate-supply-order
  "Deterministic mock-advisor proposal: coordinate equipment/chemical/
  plant-material procurement for `site-id`. Orders above the Governor's
  cost threshold always escalate to a human regardless of confidence."
  [site-id jurisdiction cost-usd]
  {:request {:op :coordinate-supply-order :subject site-id}
   :proposal {:cites [{:spec "supplier-catalog"}]
              :value {:jurisdiction jurisdiction :cost-usd cost-usd}
              :effect :propose
              :confidence 0.8
              :reasoning "Equipment, chemical, or plant-material procurement coordination for an upcoming service visit."}})

(defn default-proposals
  "A representative sample of the advisor's own default proposal for
  EVERY op in the closed allowlist, all against the same `site-id`.
  Used by the dedicated regression test
  (`landscapecare.advisor-test/mock-advisor-defaults-never-self-trip-
  scope-exclusion-test`) that asserts none of them ever self-trip the
  Governor's scope-exclusion checks (`:equipment-safety-or-pesticide-
  decision-blocked` or `:scope-exclusion-text`) on their own happy path."
  [site-id]
  [(advise-log-service-record site-id :jp/maff)
   (advise-schedule-maintenance-operation site-id :jp/maff)
   (advise-flag-safety-concern site-id :jp/maff :equipment-hazard "blade guard missing on mower")
   (advise-coordinate-supply-order site-id :jp/maff 1500)])
