(ns landscapecare.phase
  "Phase machine: the states a landscape-care client/site-contract order
  transits through.

  State machine:
    :intake -> :register -> :match -> :dispatch -> :follow-up -> :audit

  `:intake` is service-request receiving (client, site, requested
  service); `:register` is client/site-contract registration and
  applicator-license/equipment-safety-clearance scope verification;
  `:match` is crew/equipment/applicator matching to the job; `:dispatch`
  is the crew being dispatched and the actual mowing/pruning/irrigation-
  monitoring/treatment field work performed at the client site;
  `:follow-up` is logging the completed service (evidence, safety
  parameters) into records; `:audit` is compliance audit, the terminal
  state. This sequence matches the registry's own registered
  `:operating-states` for ISIC 8130 exactly.

  Each transition can accept a proposal and yield an audit fact.")

(def all-phases
  "All valid phases in the landscape-care-service workflow."
  [:intake :register :match :dispatch :follow-up :audit])

(def phase-sequence
  "Ordered phases representing normal site-order progression."
  [:intake :register :match :dispatch :follow-up :audit])

(defn valid-phase?
  "Check if a phase is valid."
  [phase]
  (contains? (set all-phases) phase))

(defn- index-of
  "Portable (Clojure/ClojureScript) index lookup -- `.indexOf` is a
  JVM-only `java.util.List` method that ClojureScript's PersistentVector
  does not implement, so it is avoided here even though `phase-sequence`
  is a plain vector. Returns -1 when `x` is not found, matching
  `java.util.List/indexOf`'s contract."
  [coll x]
  (or (first (keep-indexed (fn [i v] (when (= v x) i)) coll)) -1))

(defn can-transition?
  "Check if a transition from one phase to another is valid
  (must be forward-only in the sequence, no backtracking). Always returns a
  boolean (never nil), including when either phase is invalid."
  [from-phase to-phase]
  (boolean
   (and (valid-phase? from-phase) (valid-phase? to-phase)
        (let [from-idx (index-of phase-sequence from-phase)
              to-idx (index-of phase-sequence to-phase)]
          (and (>= from-idx 0) (>= to-idx 0) (< from-idx to-idx))))))
