(ns landscapecare.store
  "Store abstraction for landscape-care client/site-contract orders.
  Current implementation operates on plain data (`{:site-orders
  {site-order-id order-map} :facts [...]}`); production should migrate
  this seam to Datomic/kotoba-server (the same seam point all cloud-itonami
  actors use) while keeping the same pure-function surface.

  A site order is the minimal unit of work: one client/site-contract
  engagement for mowing/pruning/irrigation-monitoring/treatment service
  visits at a client property. Representative site-order keys:
    - :service-type keyword service-type id (see `landscapecare.facts/service-types`)
    - :jurisdiction keyword jurisdiction id (see `landscapecare.facts/jurisdictions`)
    - :client-site-id the served property's identifier
    - :site-boundary-hectares serviced site area
    - :evidence-checklist evidence items present for the site order
    - :applicator-license-expiry-date epoch-ms of the applicator's license
      expiry (nil for non-chemical service types)
    - :equipment-last-calibration-date epoch-ms of last sprayer/applicator
      equipment calibration (nil for non-chemical service types)
    - :equipment-safety-clearance-expiry-date epoch-ms of the power-
      equipment operator's safety-clearance certification expiry (nil for
      service types that do not require it)
    - :hours-until-reentry planned gap before workers/occupants re-enter
      the treated site (nil for non-chemical service types)
    - :wind-speed-kmh actual wind speed at time of application (nil for
      non-chemical service types)
    - :buffer-zone-actual-m actual distance maintained to the nearest
      sensitive site (nil for non-chemical service types)
    - :safety-concern-raised? / :safety-concern-resolved? open equipment-
      hazard/pesticide-drift concern flag
    - :logged? true once a `:log-service-record` proposal commits
    - :scheduled? true once a `:schedule-maintenance-operation` proposal
      commits

  The ledger (`:facts`) is a separate append-only vector of audit facts,
  kept alongside `:site-orders` in the same store value.")

(defn site-order
  "Retrieve a site order by id, or nil if it does not exist / is not yet
  registered."
  [st site-order-id]
  (get-in st [:site-orders site-order-id]))

(defn site-order-registered?
  "True only if the site order exists in the store -- registration is
  the HARD invariant that must be independently verified before ANY of
  this actor's four proposal ops can be made against it."
  [st site-order-id]
  (some? (site-order st site-order-id)))

(defn site-order-already-logged?
  "True only if the site order exists and has already been marked
  logged."
  [st site-order-id]
  (true? (:logged? (site-order st site-order-id))))

(defn log-service-record
  "Register/update `order-data` under `site-order-id` and mark it
  logged (one-way flag). Used once a `:log-service-record` proposal
  commits."
  [st site-order-id order-data]
  (assoc-in st [:site-orders site-order-id] (assoc order-data :logged? true)))

(defn mark-scheduled
  "Mark an existing site order as scheduled (one-way flag). Used once a
  `:schedule-maintenance-operation` proposal commits."
  [st site-order-id]
  (assoc-in st [:site-orders site-order-id :scheduled?] true))

(defn audit-trail
  "Return the append-only audit ledger (empty vector if none yet)."
  [st]
  (get st :facts []))

(defn append-fact
  "Append `fact` to the store's audit ledger."
  [st fact]
  (update st :facts (fnil conj []) fact))
