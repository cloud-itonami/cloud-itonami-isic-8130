(ns landscapecare.rollout
  "Gradual-autonomy rollout tiers (0 -> 3), orthogonal to
  `landscapecare.phase`'s workflow STATE machine (`:intake`/`:register`/
  ... -- which state a given site order is currently IN). This namespace
  instead describes how much AUTONOMY the Governor is configured to
  grant at a given deployment-maturity tier -- which of the closed-
  allowlist ops may ever be AUTO-COMMITTED (never merely proposed)
  at that tier, without per-instance human sign-off.

  CRITICAL, cross-cutting invariant for this actor (repeated here
  because it is safety-critical, not merely a style preference):
  `:flag-safety-concern` must NEVER appear in ANY tier's `:auto` set, at
  any rollout tier, ever -- an equipment-hazard or pesticide/herbicide-
  drift concern is never auto-resolved by advisor confidence alone.
  Likewise `:log-service-record` (the one real actuation event this
  actor performs) never appears in any tier's `:auto` set either. This
  is enforced by TWO independent layers, not one:
    1. Structurally, in `landscapecare.governor/always-escalate-ops`,
       regardless of rollout tier at all (see `governor/check` --
       `:ok?` is unconditionally false for these ops even at tier 3).
    2. Redundantly, by every tier table below never listing either op
       in its own `:auto` set.
  `rollout-consistent?` cross-checks these two layers never drift apart.
  `landscapecare.rollout-test` is the dedicated regression test."
  (:require [clojure.set :as set]
            [landscapecare.governor :as governor]))

(def tiers
  "Rollout tier 0 (read-only, no writes at all) through tier 3
  (supervised auto-commit for the lowest-risk ops only). `:writes` is
  which ops MAY ever be proposed (still governor-gated) at this tier;
  `:auto` is the (always strictly smaller-or-equal) subset that may be
  auto-committed by the Governor without per-instance human sign-off,
  when the Governor's hard checks are otherwise clean AND the op is not
  in `governor/always-escalate-ops`."
  {0 {:label :read-only
      :writes #{}
      :auto #{}}
   1 {:label :assisted-logging
      :writes #{:log-service-record}
      :auto #{}}
   2 {:label :assisted-coordination
      :writes #{:log-service-record :schedule-maintenance-operation
                :flag-safety-concern :coordinate-supply-order}
      :auto #{}}
   3 {:label :supervised-auto
      :writes #{:log-service-record :schedule-maintenance-operation
                :flag-safety-concern :coordinate-supply-order}
      :auto #{:schedule-maintenance-operation :coordinate-supply-order}}})

(defn auto-eligible-at-tier?
  "True only if `op` may be auto-committed (never merely proposed) at
  rollout `tier`."
  [tier op]
  (boolean (contains? (get-in tiers [tier :auto] #{}) op)))

(defn rollout-consistent?
  "Cross-check both enforcement layers agree: no tier's `:auto` set may
  EVER contain an op that `governor/always-escalate-ops` marks as
  always-human-sign-off. Returns true when every tier is consistent."
  []
  (every?
   (fn [[_tier {:keys [auto]}]]
     (empty? (set/intersection auto governor/always-escalate-ops)))
   tiers))
