(ns landscapecare.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave2 flagship item2 for ISIC 8130): this repo previously had NO demo
  page and no generator at all. This namespace drives the REAL actor
  stack (`landscapecare.operation` -> `landscapecare.governor` ->
  `landscapecare.store`, with proposals from `landscapecare.advisor`)
  through a scenario adapted from this repo's own `landscapecare.sim`
  demo driver and the fixtures exercised in
  `test/landscapecare/operation_test.cljc` / `governor_test.cljc`.

  Honest gap vs the 9522 reference (`applianceshop.render-html`): this
  actor does not yet compile a langgraph-clj StateGraph (see
  `landscapecare.sim` docstring -- 'TODO: integrate langgraph-clj
  StateGraph when available'). The real production-facing seam today is
  the pure-function `landscapecare.operation/run-operation` gate that
  every test already drives. This generator uses that seam -- not a
  fabricated StateGraph -- so every disposition shown is a real
  `governor/check` verdict, not a hand-typed copy.

  Scenario mix (every disposition this actor can reach):
    - site-demo-001 (clean mowing): schedule-maintenance auto-commits;
      low-cost supply order auto-commits; log-service-record ALWAYS
      escalates (high-stakes actuation); flag-safety-concern ALWAYS
      escalates; high-cost supply order escalates above the USD 5000
      threshold
    - HARD holds that never reach a human:
        * unregistered site-order (`:site-order-not-registered`)
        * proposal asserting `:effect :commit` (`:effect-not-propose`)
        * out-of-allowlist finalize op (`:op-not-allowed`)
        * covert finalize-equipment-safety flag
          (`:equipment-safety-or-pesticide-decision-blocked`)
        * expired applicator license on a chemical-application site
          (`:applicator-license-expired`)

  Deterministic: no invented numbers, no timestamps in the page content,
  byte-identical across reruns against the same seed (verify by diffing
  two consecutive runs). Seed site-order shapes are lifted from the
  existing test fixtures (disclosed), not invented here.

  Usage: `clojure -M:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [kotoba.lang.text :as str]
            [landscapecare.advisor :as advisor]
            [landscapecare.governor :as governor]
            [landscapecare.operation :as operation]
            [landscapecare.store :as store]))

;; ----------------------------- seed (test-fixture shapes) -------------

(def ^:private evidence-checklist
  [:site-contract-record :site-boundary-map :treatment-record
   :applicator-license :weather-log :buffer-zone-assessment])

(def ^:private clean-mowing-order
  "Lifted from landscapecare.governor-test/clean-mowing-order shape --
  non-chemical, no equipment-safety-clearance fields."
  {:service-type :mowing/routine
   :jurisdiction :jp/maff
   :client-site-id "site-demo-001"
   :evidence-checklist evidence-checklist})

(defn- expired-herbicide-order
  "Chemical-application site with an EXPIRED applicator license -- the
  HARD rule `applicator-license-expired` is only reachable on chemical
  service types (see governor_test). License/calibration windows are
  placed relative to wall-clock at seed time (never rendered into the
  page) so the intended single HARD rule stays stable: license expired,
  calibration still within the 90-day window."
  []
  (let [now (System/currentTimeMillis)
        ten-days-ago (- now (* 10 24 60 60 1000))
        hundred-days-ago (- now (* 100 24 60 60 1000))]
    {:service-type :treatment/herbicide-broadcast
     :jurisdiction :jp/maff
     :client-site-id "site-demo-herb-expired"
     :applicator-license-expiry-date hundred-days-ago
     :equipment-last-calibration-date ten-days-ago
     :hours-until-reentry 24
     :wind-speed-kmh 10.0
     :buffer-zone-actual-m 20.0
     :evidence-checklist evidence-checklist}))

(defn- seed-db
  "Fresh in-memory store with the two site orders this demo exercises.
  Registration is the HARD invariant for every allowed op."
  []
  {:site-orders {"site-demo-001" clean-mowing-order
                 "site-demo-herb-expired" (expired-herbicide-order)}
   :facts []})

;; ----------------------------- real actor driver ----------------------

(def ^:private context
  {:actor-id "landscape-care-advisor-1"
   :hold-fact-fn governor/hold-fact})

(defn- classify
  "Map a real `operation/run-operation` result onto a page-facing
  disposition. Hard holds and soft escalations both produce
  `ok?=false` facts today (see operation_test) -- distinguish them by
  inspecting the verdict, never by inventing a third gate."
  [result]
  (cond
    (:ok? result) :auto-committed
    (get-in result [:verdict :hard?]) :hard-hold
    (get-in result [:verdict :escalate?]) :escalate
    :else :unexpected))

(defn- exec!
  "Drive ONE proposal through the real pure-function OperationActor.
  Appends any hold facts the actor itself produced to the store.
  Returns {:store :result :disposition :request :proposal}."
  [st request proposal]
  (let [result (operation/run-operation request context proposal st governor/check)
        st' (reduce store/append-fact st (:facts result))]
    {:store st'
     :result result
     :disposition (classify result)
     :request request
     :proposal proposal}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach. Returns `{:store .. :runs [..]}` -- every field
  read by `render` below is real governor/store output, not a hand-typed
  copy. HARD holds never reach a human (this actor has no approval
  resume path yet; escalate is recorded as the real soft gate)."
  []
  (let [st0 (seed-db)
        steps
        [;; site-demo-001: clean auto-commit path
         (let [p (advisor/advise-schedule-maintenance-operation
                  "site-demo-001" :jp/maff)]
           [(:request p) (:proposal p)])
         (let [p (advisor/advise-coordinate-supply-order
                  "site-demo-001" :jp/maff 1500)]
           [(:request p) (:proposal p)])
         ;; ALWAYS-escalate soft gates
         (let [p (advisor/advise-log-service-record
                  "site-demo-001" :jp/maff)]
           [(:request p) (:proposal p)])
         (let [p (advisor/advise-flag-safety-concern
                  "site-demo-001" :jp/maff
                  :equipment-hazard "blade guard missing on mower")]
           [(:request p) (:proposal p)])
         (let [p (advisor/advise-coordinate-supply-order
                  "site-demo-001" :jp/maff 10000)]
           [(:request p) (:proposal p)])
         ;; HARD: unregistered site
         (let [p (advisor/advise-schedule-maintenance-operation
                  "site-unregistered" :jp/maff)]
           [(:request p) (:proposal p)])
         ;; HARD: effect not propose
         [{:op :schedule-maintenance-operation :subject "site-demo-001"}
          {:cites [{:spec "crew-roster"}]
           :value {:jurisdiction :jp/maff}
           :effect :commit
           :confidence 0.9
           :reasoning "adversarial: claims direct write authority"}]
         ;; HARD: out of allowlist (finalize equipment-safety clearance)
         [{:op :finalize-equipment-safety-clearance :subject "site-demo-001"}
          {:cites [{:spec "equipment-manual"}]
           :value {:jurisdiction :jp/maff}
           :effect :propose
           :confidence 0.99
           :reasoning "adversarial: out-of-scope finalize"}]
         ;; HARD: covert finalize flag (defense-in-depth structural block)
         [{:op :schedule-maintenance-operation :subject "site-demo-001"}
          {:cites [{:spec "crew-roster"}]
           :value {:jurisdiction :jp/maff
                   :finalize-equipment-safety-clearance? true}
           :effect :propose
           :confidence 0.9
           :reasoning "adversarial: covert finalize flag"}]
         ;; HARD: expired applicator license on chemical site
         (let [p (advisor/advise-log-service-record
                  "site-demo-herb-expired" :jp/maff)]
           [(:request p) (:proposal p)])]
        runs
        (loop [st st0
               remaining steps
               acc []]
          (if (empty? remaining)
            {:store st :runs acc}
            (let [[req prop] (first remaining)
                  r (exec! st req prop)]
              (recur (:store r) (rest remaining) (conj acc r)))))]
    runs))

;; ----------------------------- rendering ------------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- rule-names [result]
  (let [rules (->> (get-in result [:verdict :violations])
                   (map :rule)
                   (map name))]
    (if (seq rules) (str/join ", " rules) "-")))

(defn- disposition-cell [{:keys [disposition result]}]
  (case disposition
    :auto-committed "<span class=\"ok\">auto-committed</span>"
    :escalate "<span class=\"warn\">escalate · human sign-off required</span>"
    :hard-hold (str "<span class=\"critical\">HARD hold &middot; "
                    (esc (rule-names result)) "</span>")
    "<span class=\"muted\">unexpected</span>"))

(defn- site-row [site-id order]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc site-id)
          (esc (name (:service-type order)))
          (esc (name (:jurisdiction order)))
          (if (:logged? order)
            "<span class=\"ok\">logged</span>"
            "<span class=\"muted\">registered</span>")))

(defn- run-row [{:keys [request disposition] :as run}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name (:op request)))
          (esc (:subject request))
          (disposition-cell run)))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t))
          (esc (name (or op :n-a)))
          (esc subject)
          (esc (or (some->> basis (map name) (str/join ", "))
                   (some-> disposition name)
                   ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract
  ;; (README Ops, landscapecare.governor / landscapecare.rollout) --
  ;; documentation of fixed behavior, not runtime telemetry.
  ["        <tr><td><code>:schedule-maintenance-operation</code></td><td><span class=\"ok\">tier-3 auto when clean</span></td></tr>"
   "        <tr><td><code>:coordinate-supply-order</code></td><td><span class=\"ok\">tier-3 auto when clean &middot; ALWAYS human approval above USD 5000</span></td></tr>"
   "        <tr><td><code>:log-service-record</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any tier (sole real actuation)</span></td></tr>"
   "        <tr><td><code>:flag-safety-concern</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any tier</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a demo result
  that has already run `run-demo!` (or any other real scenario)."
  [{:keys [store runs]}]
  (let [ledger (vec (store/audit-trail store))
        sites (:site-orders store)
        site-rows (str/join "\n" (map (fn [[id o]] (site-row id o))
                                      (sort-by key sites)))
        run-rows (str/join "\n" (map run-row runs))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        hard-count (count (filter #(= :hard-hold (:disposition %)) runs))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-8130 &middot; landscape-care operations</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Landscape care &amp; maintenance operations (ISIC 8130) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · never finalizes equipment-safety clearance or pesticide-application decisions</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Site orders</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>landscapecare.store</code> via <code>landscapecare.render-html</code> (<code>clojure -M:render-html</code>). Seed shapes match <code>landscapecare.governor-test</code> fixtures.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Site order</th><th>Service type</th><th>Jurisdiction</th><th>Status</th></tr></thead>\n"
     "      <tbody>\n"
     site-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Landscape Care Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Finalizing an equipment-safety clearance or a pesticide-application decision is permanently out of scope — structural boolean flag + free-text scan, defense in depth. This demo produced "
     hard-count
     " HARD hold(s).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Scenario runs (this build)</h2>\n"
     "    <p class=\"muted\">Every row is a real <code>landscapecare.operation/run-operation</code> + <code>landscapecare.governor/check</code> verdict. Soft escalations and HARD holds both return <code>ok?=false</code>; the page distinguishes them by <code>:hard?</code> / <code>:escalate?</code> on the verdict.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Subject</th><th>Disposition</th></tr></thead>\n"
     "      <tbody>\n"
     run-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — hold facts the real actor itself wrote via <code>governor/hold-fact</code>. Auto-commits produce no hold fact (see <code>operation_test</code>).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        demo (run-demo!)
        html (render demo)
        hard-n (count (filter #(= :hard-hold (:disposition %)) (:runs demo)))
        esc-n (count (filter #(= :escalate (:disposition %)) (:runs demo)))
        ok-n (count (filter #(= :auto-committed (:disposition %)) (:runs demo)))]
    (.mkdirs (java.io.File. (.getParent (java.io.File. out))))
    (spit out html)
    (println "wrote" out
             "(" (count (store/audit-trail (:store demo))) "ledger facts,"
             ok-n "auto-committed,"
             esc-n "escalate,"
             hard-n "HARD hold )")
    (when (< hard-n 1)
      (binding [*out* *err*]
        (println "ERROR: flagship item2 requires ≥1 HARD hold; got" hard-n))
      (System/exit 1))))
