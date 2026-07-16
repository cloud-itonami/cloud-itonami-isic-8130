(ns landscapecare.governor
  "Landscape Care Governor -- the independent compliance layer that earns
  the Landscape Care Advisor the right to commit. The LLM has no notion
  of:
    - Whether a client/site-contract record has been independently
      verified/registered in the store at all, for ANY of this actor's
      four proposal ops
    - Whether the applicator's pesticide/herbicide-application license is
      current (only meaningful for chemical-application service types)
    - Whether the sprayer/applicator equipment's calibration is current
      (only meaningful for chemical-application service types)
    - Whether the power-equipment operator's safety-clearance
      certification (chainsaw/pole-saw/heavy-mower) is current (only
      meaningful for service types that require it, e.g. tree-trimming)
    - Whether the planned gap before workers/occupants re-enter a treated
      site meets the service type's restricted-entry interval (only
      meaningful for chemical-application service types)
    - Whether the actual wind speed at time of application exceeded the
      service type's maximum safe spray-drift wind speed (only
      meaningful for chemical-application service types)
    - Whether the actual buffer-zone distance to the nearest sensitive
      site met the service type's minimum (only meaningful for
      chemical-application service types)
    - Whether a proposal is covertly requesting a final equipment-safety
      clearance or a final pesticide-application decision
    - Whether a previously-raised safety concern has been resolved
    - Whether a site order has already been logged (double-commit)

  This MUST be a separate system able to *reject* a proposal and fall
  back to HOLD.

  Unlike directly finalizing an equipment-safety clearance (power
  equipment such as chainsaws/pole-saws/heavy mowers -- NEVER done by
  this actor) or finalizing a pesticide-application decision (NEVER done
  by this actor -- both are HARD, permanent governor blocks, never
  overridable by human approval), the Governor operates on site-order
  metadata: client-site identity, service parameters, and safety/
  compliance flags. This is landscape-care OPERATIONS COORDINATION, not
  direct equipment-safety authority or pesticide-application authority.

  CRITICAL: `:flag-safety-concern` ALWAYS escalates to human sign-off at
  every phase, regardless of advisor confidence -- an equipment-hazard or
  pesticide-drift concern is never auto-resolved by advisor confidence
  alone, and this op is never in any phase's auto-commit set.

  CRITICAL (scope-exclusion phrasing): the two scope-exclusion checks
  below (`equipment-safety-or-pesticide-decision-blocked-violations`,
  a structural boolean-flag check, and `scope-exclusion-text-violations`,
  a defense-in-depth text scan) are deliberately phrased as the
  FINALIZATION/EXECUTION ACTION (\"finalize the equipment-safety
  clearance\", \"finalize the pesticide-application decision\"), never as
  a bare noun (\"equipment\", \"safety\", \"pesticide\"). A bare-noun term
  list would match inside this actor's OWN default mock-advisor rationale
  text for entirely legitimate, allowed proposals -- which routinely and
  correctly use words like \"equipment\", \"safety\", or \"pesticide\" in a
  purely descriptive/logistics sense (e.g. 'equipment-hazard concern
  surfaced for review') -- and would cause the actor to self-block on its
  own happy path. See `landscapecare.advisor-test` for the dedicated
  regression test asserting the advisor's own default proposals for every
  op in the closed allowlist never trip either scope-exclusion check.

  Hard violations (always HOLD, no override):
    1. Operation outside the closed allowlist (`:op-not-allowed`) --
       includes any proposal that would amount to directly finalizing an
       equipment-safety clearance or a pesticide-application decision
    2. Proposal asserting an `:effect` other than `:propose`
       (`:effect-not-propose`)
    3. Site order not independently verified/registered in the store --
       applies to ALL FOUR allowed ops (`:site-order-not-registered`)
    4. No jurisdiction citation (`:no-spec-basis`)
    5. Evidence checklist incomplete (`:evidence-incomplete`)
    6. Pesticide/herbicide-applicator license expired
       (`:applicator-license-expired` -- only when the service type is a
       chemical application)
    7. Sprayer/applicator equipment calibration overdue
       (`:equipment-calibration-overdue` -- only when the service type is
       a chemical application)
    8. Power-equipment safety-clearance certification expired
       (`:equipment-safety-clearance-expired` -- only when the service
       type requires it, e.g. chainsaw/pole-saw tree-trimming)
    9. Restricted-entry interval violated
       (`:restricted-entry-interval-violated` -- only when the service
       type is a chemical application)
   10. Wind speed exceeded the safe spray-drift ceiling
       (`:wind-speed-exceeded` -- only when the service type is a
       chemical application)
   11. Buffer zone narrower than the service type's minimum
       (`:buffer-zone-violated` -- only when the service type is a
       chemical application)
   12. Proposal covertly requests a final equipment-safety clearance or a
       final pesticide-application decision, via structured boolean flags
       (`:equipment-safety-or-pesticide-decision-blocked` -- a HARD,
       PERMANENT block, never overridable by human approval, evaluated
       against every op as defense-in-depth even though those actions are
       already outside the closed allowlist)
   13. Proposal content mentions the finalization action in free text,
       via a defense-in-depth text scan (`:scope-exclusion-text` -- also
       a HARD, PERMANENT block, evaluated against every op)
   14. Unresolved safety concern (`:safety-concern-flag-unresolved`)
   15. Site order already logged (`:already-logged`, double-commit guard)

  Soft gates (always escalate for human):
    - Low confidence
    - `:log-service-record` -- the one real actuation event this actor
      performs (logging completed, billable field work into records)
    - `:flag-safety-concern` -- never auto-resolved by confidence alone,
      and never included in any phase's auto-commit set
    - `:coordinate-supply-order` above the cost threshold
      (`supply-order-cost-threshold-usd`)

  This design mirrors `cropsupport.governor` (ISIC 0161, support
  activities for crop production) in overall shape but specializes on
  landscape-care-service safety concerns -- applicator licensing,
  sprayer/applicator equipment calibration, power-equipment safety
  clearance, restricted-entry interval, spray-drift wind, and buffer
  zones -- for ongoing care and maintenance of an existing client site,
  never landscape architecture/design (a separately licensed profession
  out of this actor's scope)."
  (:require [landscapecare.facts :as facts]
            [landscapecare.registry :as registry]
            [landscapecare.store :as store]))

(def confidence-floor 0.6)

(def supply-order-cost-threshold-usd
  "Supply orders (equipment/chemical/plant-material procurement) at or
  below this cost may auto-commit when the Governor is otherwise clean;
  orders above this threshold always require human sign-off, regardless
  of advisor confidence."
  5000)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Logging a completed service record (`:log-service-record`) is the one
  real-world actuation event this actor performs -- it commits billable
  field-work data (and, transitively, the safety/compliance facts that
  accompanied it) into the permanent record."
  #{:log-service-record})

(def always-escalate-ops
  "Operations that always require human sign-off, even when the
  Governor's hard checks are clean and confidence is high: the high-
  stakes actuation event (`high-stakes`) plus `:flag-safety-concern` --
  an equipment-hazard or pesticide-drift concern is never auto-resolved
  by advisor confidence alone, it always needs a human look, and this op
  must never appear in any phase's auto-commit set."
  (conj high-stakes :flag-safety-concern))

(def allowed-ops
  "Closed allowlist of proposal operations this actor may ever make. Any
  proposal for an operation outside this set -- most importantly directly
  finalizing an equipment-safety clearance or a pesticide-application
  decision -- is a hard, permanent block: this actor coordinates
  landscape-care operations, it never itself finalizes an equipment-
  safety clearance or a pesticide-application decision."
  #{:log-service-record :schedule-maintenance-operation
    :flag-safety-concern :coordinate-supply-order})

;; ────────────────────────── Checks ──────────────────────────

(defn- op-not-allowed-violations
  "HARD, permanent block: any proposal outside the closed operation
  allowlist (e.g. directly finalizing an equipment-safety clearance or a
  pesticide-application decision) is refused unconditionally -- this
  actor has no authority to make such a proposal at all, let alone
  commit it."
  [{:keys [op]} _proposal]
  (when-not (contains? allowed-ops op)
    [{:rule :op-not-allowed
      :detail (str op " はこのactorの許可された提案種別 (log-service-record/"
                  "schedule-maintenance-operation/flag-safety-concern/"
                  "coordinate-supply-order) に含まれない -- 設備安全最終承認"
                  "・農薬散布最終決定はこのactorに無い")}]))

(defn- effect-not-propose-violations
  "HARD invariant: this actor's proposals are always `:effect :propose` --
  it never claims direct write/actuation authority for itself. A proposal
  asserting any other effect is refused unconditionally."
  [_request proposal]
  (when-let [effect (:effect proposal)]
    (when (not= effect :propose)
      [{:rule :effect-not-propose
        :detail (str "この actor の提案は :propose 以外の :effect を持てない (got " effect ")")}])))

(defn- site-order-not-registered-violations
  "HARD invariant: a client/site-contract record must be independently
  verified/registered in the store BEFORE any of this actor's four
  proposal ops can be made against it -- coordinating work for a site
  this actor never checked in is out of scope. Evaluated across ALL FOUR
  allowed ops, not just one."
  [{:keys [op subject]} st]
  (when (contains? allowed-ops op)
    (when-not (store/site-order-registered? st subject)
      [{:rule :site-order-not-registered
        :detail (str subject " は独立に検証・登録された site-order 記録が無い -- いかなる提案も進められない")}])))

(defn- spec-basis-violations
  "A proposal with no jurisdiction citation is a HARD violation -- never
  invent a jurisdiction's landscape-care-service safety requirements."
  [{:keys [op]} proposal]
  (when (contains?
         #{:log-service-record :coordinate-supply-order :flag-safety-concern}
         op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :jurisdiction) (nil? (:jurisdiction value))))
        [{:rule :no-spec-basis
          :detail "公式仕様の引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:log-service-record`, verify the site order's evidence checklist
  is complete per jurisdiction requirements."
  [{:keys [op subject]} st]
  (when (= op :log-service-record)
    (let [o (store/site-order st subject)]
      (when-not (and o
                     (facts/required-evidence-satisfied?
                      (:jurisdiction o)
                      (:evidence-checklist o)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(site-contract-record/site-boundary-map/treatment-record/applicator-license等)が充足していない状態での提案"}]))))

(defn- applicator-license-expired-violations
  "For `:log-service-record`, INDEPENDENTLY verify the applicator's
  license has not expired via `registry/applicator-license-expired?`.
  Only evaluated when the service type actually requires a license
  (chemical-application service types) -- non-chemical service types
  have nothing to check here, never a fabricated requirement."
  [{:keys [op subject]} st now-ms]
  (when (= op :log-service-record)
    (let [o (store/site-order st subject)
          st* (when o (facts/service-type-by-id (:service-type o)))]
      (when (and o st* (:chemical-application? st*) (:applicator-license-expiry-date o)
                 (registry/applicator-license-expired? (:applicator-license-expiry-date o) now-ms))
        [{:rule :applicator-license-expired
          :detail (str subject " の農薬散布者資格(applicator license)が失効している -- 記録提案は進められない")}]))))

(defn- equipment-calibration-overdue-violations
  "For `:log-service-record`, INDEPENDENTLY verify the sprayer/applicator
  equipment's calibration is current via
  `registry/equipment-calibration-overdue?`. Only evaluated when the
  service type actually requires calibration (chemical-application
  service types)."
  [{:keys [op subject]} st now-ms]
  (when (= op :log-service-record)
    (let [o (store/site-order st subject)
          st* (when o (facts/service-type-by-id (:service-type o)))]
      (when (and o st* (:chemical-application? st*) (:equipment-last-calibration-date o)
                 (registry/equipment-calibration-overdue? (:equipment-last-calibration-date o) now-ms))
        [{:rule :equipment-calibration-overdue
          :detail (str subject " の散布機器の校正が期限切れ -- 記録提案は進められない")}]))))

(defn- equipment-safety-clearance-expired-violations
  "For `:log-service-record`, INDEPENDENTLY verify the power-equipment
  operator's safety-clearance certification is current via
  `registry/equipment-safety-clearance-expired?`. Only evaluated when the
  service type actually requires an equipment-safety clearance (e.g.
  chainsaw/pole-saw tree-trimming) -- service types that do not require
  one have nothing to check here, never a fabricated requirement."
  [{:keys [op subject]} st now-ms]
  (when (= op :log-service-record)
    (let [o (store/site-order st subject)
          st* (when o (facts/service-type-by-id (:service-type o)))]
      (when (and o st* (:equipment-safety-clearance-required? st*)
                 (:equipment-safety-clearance-expiry-date o)
                 (registry/equipment-safety-clearance-expired?
                  (:equipment-safety-clearance-expiry-date o) now-ms))
        [{:rule :equipment-safety-clearance-expired
          :detail (str subject " の設備安全資格(equipment safety clearance)が失効している -- 記録提案は進められない")}]))))

(defn- restricted-entry-interval-violated-violations
  "For `:log-service-record`, INDEPENDENTLY verify that the planned gap
  before workers/occupants re-enter a treated site meets the service
  type's restricted-entry interval via
  `registry/restricted-entry-interval-violated?`. Only evaluated when the
  service type actually has a restricted-entry-interval spec."
  [{:keys [op subject]} st]
  (when (= op :log-service-record)
    (let [o (store/site-order st subject)
          st* (when o (facts/service-type-by-id (:service-type o)))]
      (when (and o st* (:restricted-entry-interval-hours st*) (:hours-until-reentry o)
                 (registry/restricted-entry-interval-violated?
                  (:hours-until-reentry o)
                  (:restricted-entry-interval-hours st*)))
        [{:rule :restricted-entry-interval-violated
          :detail (str subject " の再入場までの猶予(" (:hours-until-reentry o)
                      "時間)が再入場禁止期間基準を下回る -- 記録提案は進められない")}]))))

(defn- wind-speed-exceeded-violations
  "For `:log-service-record`, INDEPENDENTLY verify that the actual wind
  speed at time of application did not exceed the service type's maximum
  safe spray-drift wind speed via `registry/wind-speed-exceeded?`. Only
  evaluated when the service type actually has a wind-speed ceiling."
  [{:keys [op subject]} st]
  (when (= op :log-service-record)
    (let [o (store/site-order st subject)
          st* (when o (facts/service-type-by-id (:service-type o)))]
      (when (and o st* (:max-wind-speed-kmh st*) (:wind-speed-kmh o)
                 (registry/wind-speed-exceeded?
                  (:wind-speed-kmh o)
                  (:max-wind-speed-kmh st*)))
        [{:rule :wind-speed-exceeded
          :detail (str subject " の散布時風速(" (:wind-speed-kmh o)
                      "km/h)が飛散防止基準を超過 -- 記録提案は進められない")}]))))

(defn- buffer-zone-violated-violations
  "For `:log-service-record`, INDEPENDENTLY verify that the actual
  distance maintained to the nearest sensitive site met the service
  type's minimum buffer zone via `registry/buffer-zone-violated?`. Only
  evaluated when the service type actually has a buffer-zone minimum."
  [{:keys [op subject]} st]
  (when (= op :log-service-record)
    (let [o (store/site-order st subject)
          st* (when o (facts/service-type-by-id (:service-type o)))]
      (when (and o st* (:min-buffer-zone-m st*) (:buffer-zone-actual-m o)
                 (registry/buffer-zone-violated?
                  (:buffer-zone-actual-m o)
                  (:min-buffer-zone-m st*)))
        [{:rule :buffer-zone-violated
          :detail (str subject " の緩衝地帯距離(" (:buffer-zone-actual-m o)
                      "m)が最小基準を下回る -- 記録提案は進められない")}]))))

(defn- equipment-safety-or-pesticide-decision-blocked-violations
  "HARD, PERMANENT block, defense-in-depth (structural boolean-flag
  check): any proposal whose `:value` covertly requests a final
  equipment-safety clearance (`:finalize-equipment-safety-clearance?`
  true) or a final pesticide-application decision
  (`:finalize-pesticide-application-decision?` true) is refused
  unconditionally, regardless of which op it is nominally filed under and
  regardless of advisor confidence. Never overridable by human approval
  -- this is a scope boundary, not a risk judgment."
  [_request proposal]
  (let [value (:value proposal)]
    (when (or (true? (:finalize-equipment-safety-clearance? value))
              (true? (:finalize-pesticide-application-decision? value)))
      [{:rule :equipment-safety-or-pesticide-decision-blocked
        :detail "設備安全の最終承認または農薬散布の最終決定はこのactorの範囲外 -- 恒久的にブロックされる"}])))

(def ^:private scope-exclusion-patterns
  "Defense-in-depth text scan over the proposal's own declared content
  (never the advisor's self-reported confidence). Deliberately phrased as
  the FINALIZATION/EXECUTION ACTION (e.g. \"finalize the pesticide-
  application decision\"), never as a bare noun (e.g. \"pesticide\") -- a
  bare-noun term list would match inside this actor's own default
  mock-advisor rationale text for entirely legitimate, allowed proposals
  (which routinely and correctly mention words like \"equipment\",
  \"safety\", or \"pesticide\" in a purely descriptive/logistics sense)
  and would cause a self-block on the happy path. See
  `landscapecare.advisor-test` for the dedicated regression test."
  [#"(?i)finaliz(e|ing|ed)\s+(the\s+)?equipment.?safety\s+clearance"
   #"(?i)grant\s+(the\s+)?final\s+equipment.?safety\s+clearance"
   #"(?i)finaliz(e|ing|ed)\s+(the\s+)?(pesticide|herbicide).application\s+decision"
   #"(?i)approve\s+(the\s+)?final\s+(pesticide|herbicide).application\s+decision"
   #"設備安全.{0,4}最終.{0,4}承認"
   #"農薬散布.{0,4}最終.{0,4}決定"])

(defn- scope-exclusion-text-violations
  "HARD, PERMANENT block, defense-in-depth (free-text scan): scans the
  proposal's own `:value` and `:reasoning` for the scope-exclusion
  finalization-action patterns above. Evaluated against every op."
  [_request proposal]
  (let [proposal-text (str (:value proposal) " " (:reasoning proposal))]
    (when (some #(re-find % proposal-text) scope-exclusion-patterns)
      [{:rule :scope-exclusion-text
        :detail "提案内容が設備安全最終承認または農薬散布最終決定の確定行為に触れている -- 恒久的にブロックされる"}])))

(defn- safety-concern-flag-unresolved-violations
  "An unresolved safety-concern flag is a HARD, un-overridable hold.
  Safety concerns (equipment hazard, pesticide/herbicide drift) raised
  during service must be resolved before the site order can be logged.
  Evaluated UNCONDITIONALLY at `:log-service-record`."
  [{:keys [op subject]} st]
  (when (= op :log-service-record)
    (let [o (store/site-order st subject)]
      (when (and (true? (:safety-concern-raised? o))
                 (not (true? (:safety-concern-resolved? o))))
        [{:rule :safety-concern-flag-unresolved
          :detail (str subject " は未解決の安全懸念フラグがある -- 記録提案は進められない")}]))))

(defn- already-logged-violations
  "For `:log-service-record`, refuse to log the SAME site order twice,
  off a dedicated `:logged?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :log-service-record)
    (when (store/site-order-already-logged? st subject)
      [{:rule :already-logged
        :detail (str subject " は既に記録済み")}])))

(defn- now-epoch-ms
  "Current time in epoch milliseconds, portable across Clojure/
  ClojureScript. Isolated to this single call site so the rest of the
  namespace (and all of `landscapecare.registry`) stays free of
  host-clock calls."
  []
  #?(:clj (System/currentTimeMillis)
     :cljs (js/Date.now)))

(defn- high-cost-supply-order?
  "Soft-gate helper: a supply order (equipment/chemical/plant-material
  procurement) whose declared cost exceeds
  `supply-order-cost-threshold-usd` always requires human sign-off, even
  when the Governor's hard checks are clean and confidence is high."
  [{:keys [op]} proposal]
  (and (= op :coordinate-supply-order)
       (some-> (get-in proposal [:value :cost-usd])
               (> supply-order-cost-threshold-usd))))

(defn check
  "Censors a Landscape Care Advisor proposal against the Governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}.

  Stakes (high-stakes actuation vs. always-escalate vs. high-cost supply
  order) are read off the REQUEST's `:op` (and, for supply-order cost,
  the proposal's own declared value) -- not off the advisor's self-
  reported stake -- since the operation being proposed is what determines
  whether a human must sign off."
  [request _context proposal st]
  (let [now-ms (now-epoch-ms)
        hard (into []
                   (concat (op-not-allowed-violations request proposal)
                           (effect-not-propose-violations request proposal)
                           (site-order-not-registered-violations request st)
                           (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (applicator-license-expired-violations request st now-ms)
                           (equipment-calibration-overdue-violations request st now-ms)
                           (equipment-safety-clearance-expired-violations request st now-ms)
                           (restricted-entry-interval-violated-violations request st)
                           (wind-speed-exceeded-violations request st)
                           (buffer-zone-violated-violations request st)
                           (equipment-safety-or-pesticide-decision-blocked-violations request proposal)
                           (scope-exclusion-text-violations request proposal)
                           (safety-concern-flag-unresolved-violations request st)
                           (already-logged-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        actuation? (boolean (high-stakes (:op request)))
        escalate-op? (or (boolean (always-escalate-ops (:op request)))
                          (boolean (high-cost-supply-order? request proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not escalate-op?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? escalate-op?))
     :high-stakes? actuation?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
