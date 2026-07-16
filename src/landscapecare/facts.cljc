(ns landscapecare.facts
  "Reference facts for landscape-care and maintenance-service contractors:
  service-type safety windows (applicator-license currency, sprayer/
  applicator-equipment calibration, restricted-entry interval, wind-speed
  drift ceiling, buffer-zone minimum), power-equipment safety-clearance
  currency (chainsaw/pole-saw/heavy-mower operator certification),
  jurisdiction evidence-checklist requirements. This namespace contains
  pure lookup functions for landscape-care-service safety compliance
  checks -- the Governor calls these to independently validate proposals;
  the advisor's confidence is never sufficient on its own.

  A landscape-care and maintenance-service contractor (ISIC Rev.5 8130)
  performs ONGOING CARE AND MAINTENANCE of existing grounds -- mowing,
  trimming/pruning, irrigation-zone monitoring, and pesticide/herbicide
  treatment application -- for a client site on a fee/contract basis.
  This is deliberately scoped to maintenance, not landscape ARCHITECTURE
  or planting-plan design (a separately licensed profession covered
  elsewhere in this fleet's specialized-design-activities scope).

  Service types split into three safety shapes:
    - Routine, non-mechanical-hazard services (mowing, irrigation-zone
      monitoring) have NO chemical-application safety window and NO
      power-equipment safety-clearance requirement at all.
    - Power-equipment services (tree-trimming/pruning with chainsaw or
      pole-saw) carry an equipment-safety-clearance requirement (the
      operator's power-equipment certification must be current) but no
      chemical-application safety window.
    - Chemical-application services (herbicide/pesticide spraying) carry
      a genuine restricted-entry interval (hours before workers/occupants
      may safely re-enter the treated site), maximum safe wind speed
      (spray-drift risk), and minimum buffer zone (distance to sensitive
      sites such as water bodies, schools, playgrounds, or residences).
  A service type may require BOTH a power-equipment safety clearance and
  chemical-application safety windows in principle, though none of the
  catalog entries below do today -- the two dimensions are independent
  flags precisely so a future service type could.")

(def service-types
  "Valid landscape-care-service categories and their safety windows.
  `restricted-entry-interval-hours`/`max-wind-speed-kmh`/
  `min-buffer-zone-m` are nil for non-chemical-application service types
  -- the Governor's corresponding checks are skipped entirely for those
  types rather than fabricating a target. `equipment-safety-clearance-
  required?` is independent of `chemical-application?` -- it is only
  true for service types that require power-equipment (chainsaw/pole-saw/
  heavy-mower) operator certification."
  {:mowing/routine
   {:id :mowing/routine
    :name "定期芝刈り"
    :chemical-application? false
    :equipment-safety-clearance-required? false
    :restricted-entry-interval-hours nil
    :max-wind-speed-kmh nil
    :min-buffer-zone-m nil}

   :irrigation/zone-monitoring
   {:id :irrigation/zone-monitoring
    :name "灌漑ゾーン点検・調整"
    :chemical-application? false
    :equipment-safety-clearance-required? false
    :restricted-entry-interval-hours nil
    :max-wind-speed-kmh nil
    :min-buffer-zone-m nil}

   :pruning/tree-trimming
   {:id :pruning/tree-trimming
    :name "樹木剪定・チェーンソー作業"
    :chemical-application? false
    :equipment-safety-clearance-required? true
    :restricted-entry-interval-hours nil
    :max-wind-speed-kmh nil
    :min-buffer-zone-m nil}

   :treatment/herbicide-broadcast
   {:id :treatment/herbicide-broadcast
    :name "除草剤散布(ブロードキャスト)"
    :chemical-application? true
    :equipment-safety-clearance-required? false
    :restricted-entry-interval-hours 12
    :max-wind-speed-kmh 24.0
    :min-buffer-zone-m 15.0}

   :treatment/pesticide-application
   {:id :treatment/pesticide-application
    :name "殺虫剤散布"
    :chemical-application? true
    :equipment-safety-clearance-required? false
    :restricted-entry-interval-hours 24
    :max-wind-speed-kmh 16.0
    :min-buffer-zone-m 30.0}})

(defn service-type-by-id [id]
  (get service-types id))

(def jurisdictions
  "Landscape-care-service jurisdictions and their evidence-checklist
  requirements."
  {:jp/maff
   {:id :jp/maff
    :name "日本 (農薬取締法・農林水産省)"
    :required-evidence
    [:site-contract-record
     :site-boundary-map
     :treatment-record
     :applicator-license
     :weather-log
     :buffer-zone-assessment]}

   :us/epa
   {:id :us/epa
    :name "United States (FIFRA / EPA Pesticide Regulation)"
    :required-evidence
    [:site-contract-record
     :site-boundary-map
     :treatment-record
     :applicator-license
     :weather-log
     :buffer-zone-assessment]}

   :eu/reg1107
   {:id :eu/reg1107
    :name "European Union (Regulation (EC) No 1107/2009 on plant protection products)"
    :required-evidence
    [:site-contract-record
     :site-boundary-map
     :treatment-record
     :applicator-license
     :weather-log
     :buffer-zone-assessment]}})

(defn jurisdiction-by-id [id]
  (get jurisdictions id))

(defn required-evidence-satisfied?
  "Verify that every item in the jurisdiction's `:required-evidence` list
  is present in `evidence`. `jurisdiction` may be a resolved jurisdiction
  map (as returned by `jurisdiction-by-id`) or a raw jurisdiction id --
  both call conventions are in use (tests pass a resolved map; the
  Governor passes the raw id straight off site-order metadata)."
  [jurisdiction evidence]
  (let [j (if (map? jurisdiction) jurisdiction (jurisdiction-by-id jurisdiction))]
    (if-not j
      false
      (every? (set evidence) (:required-evidence j)))))

(defn applicator-license-current?
  "Positive-sense convenience predicate: is the applicator license valid
  (not yet expired) as of `now-epoch-ms`? Returns false when the service
  type has no chemical-application license requirement at all -- there is
  nothing to be 'current' about for a non-chemical service."
  [expiry-epoch-ms now-epoch-ms service-type]
  (boolean
   (and (some? service-type)
        (true? (:chemical-application? service-type))
        (some? expiry-epoch-ms)
        (>= expiry-epoch-ms now-epoch-ms))))

(defn equipment-calibration-current?
  "Positive-sense convenience predicate: was the sprayer/applicator
  equipment calibrated within the safety interval (90 days) of
  `now-epoch-ms`? Returns false when the service type has no
  chemical-application equipment-calibration requirement at all."
  [last-calibration-epoch-ms now-epoch-ms service-type]
  (boolean
   (and (some? service-type)
        (true? (:chemical-application? service-type))
        (some? last-calibration-epoch-ms)
        (<= (- now-epoch-ms last-calibration-epoch-ms)
            (* 90 24 60 60 1000)))))

(defn equipment-safety-clearance-current?
  "Positive-sense convenience predicate: is the power-equipment operator's
  safety-clearance certification (chainsaw/pole-saw/heavy-mower) current
  as of `now-epoch-ms`? Returns false when the service type has no
  equipment-safety-clearance requirement at all."
  [clearance-expiry-epoch-ms now-epoch-ms service-type]
  (boolean
   (and (some? service-type)
        (true? (:equipment-safety-clearance-required? service-type))
        (some? clearance-expiry-epoch-ms)
        (>= clearance-expiry-epoch-ms now-epoch-ms))))

(defn restricted-entry-interval-satisfied?
  "Positive-sense convenience predicate: does `hours-until-reentry` meet
  or exceed the service type's restricted-entry interval? Returns false
  when the service type has no restricted-entry-interval spec at all
  (non-chemical service -- nothing to satisfy)."
  [hours-until-reentry service-type]
  (boolean
   (and (some? service-type)
        (some? (:restricted-entry-interval-hours service-type))
        (some? hours-until-reentry)
        (>= hours-until-reentry (:restricted-entry-interval-hours service-type)))))

(defn wind-speed-in-range?
  "Positive-sense convenience predicate: does `actual-kmh` stay at or
  below the service type's maximum safe spray-drift wind speed? Returns
  false when the service type has no wind-speed ceiling at all."
  [actual-kmh service-type]
  (boolean
   (and (some? service-type)
        (some? (:max-wind-speed-kmh service-type))
        (some? actual-kmh)
        (<= actual-kmh (:max-wind-speed-kmh service-type)))))

(defn buffer-zone-in-range?
  "Positive-sense convenience predicate: does `actual-m` meet or exceed
  the service type's minimum buffer-zone distance? Returns false when
  the service type has no buffer-zone minimum at all."
  [actual-m service-type]
  (boolean
   (and (some? service-type)
        (some? (:min-buffer-zone-m service-type))
        (some? actual-m)
        (>= actual-m (:min-buffer-zone-m service-type)))))
