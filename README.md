# cloud-itonami-8130

Open Business Blueprint for **ISIC Rev.5 8130**: landscape care and
maintenance service activities (mowing, trimming, irrigation
management and pesticide/herbicide application on existing
grounds).

This repository designs a forkable OSS business for community
landscape care: applicator-licensing and irrigation-compliance-scope
management, robotics-assisted mowing/trimming/irrigation-monitoring,
and crew dispatch/follow-up records — run by a qualified operator so
a landscape-care company keeps its own licensing and treatment
history instead of renting a closed grounds-maintenance platform.

## Scope note: care and maintenance, not design

Landscape ARCHITECTURE and design (site planning, planting-plan
design) is a separately licensed profession already covered elsewhere
in this fleet's specialized-design-activities scope. This repository
is deliberately scoped to the ONGOING care and maintenance of
existing grounds -- mowing, trimming, irrigation management and
chemical treatment application -- matching ISIC's own distinction
between design (`7110`-adjacent) and maintenance (`8130`). Chemical
application in particular carries its own licensing regime distinct
from general landscaping labor: the US EPA/state-level Commercial
Pesticide Applicator license, Japan's 農薬取締法 (Agricultural
Chemicals Regulation Act) governing commercial pesticide application,
and (where irrigation systems are installed or serviced) state-level
irrigator licenses such as Texas's Irrigator License.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here robots (autonomous mowing,
trimming, irrigation-zone monitoring) operate under an actor that
proposes actions and an independent **Landscape Care Governor** that
gates them. The governor never dispatches a chemical-application job
or irrigation change itself; `:high`/`:safety-critical` actions (a
pesticide/herbicide application outside a verified applicator-license
scope, an irrigation change without a completed water-compliance
check, a follow-up record without verified evidence) require human
sign-off.

## Core Contract

```text
intake + identity + applicator-license/irrigation scope + crew registration
        |
        v
Landscape Care Advisor -> Landscape Care Governor -> match, dispatch, follow-up record, or human approval
        |
        v
robot actions (gated) + treatment/service record + audit ledger
```

No automated advice can dispatch a chemical-application or irrigation-
change job the governor refuses, match an unlicensed applicator to a
job, or publish a follow-up record without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `8130`). Implemented by:

- [`kotoba-lang/robotics`](https://github.com/kotoba-lang/robotics) — missions, actions, safety-stops, telemetry proofs
- [`kotoba-lang/labor`](https://github.com/kotoba-lang/labor) — crew registration, dispatch, timesheet/follow-up contracts

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Implementation: Landscape Care Operations Coordination Actor

`src/landscapecare` implements a LANDSCAPE-CARE OPERATIONS COORDINATION
actor: Landscape Care Advisor (LLM proposal-only; deterministic mock
advisor for demo/tests) ⊣ Landscape Care Governor (closed-op-allowlist
/ site-contract-registration-before-any-action hard-gate). **This actor
never dispatches robots or field equipment, never finalizes an
equipment-safety clearance, and never finalizes a pesticide/herbicide-
application decision.** It only ever proposes, with `:effect :propose`.

### Governor (Independent Compliance Layer)

The Governor is the separation-of-powers enforcement. It never trusts
the advisor's confidence for anything safety- or compliance-relevant,
and it always wins over the advisor.

- **Hard HOLD** (un-overridable):
  - Operation outside the closed allowlist (`:op-not-allowed`)
  - Proposal asserting an `:effect` other than `:propose` (`:effect-not-propose`)
  - Site order not independently verified/registered in the store — applies to ALL FOUR allowed ops (`:site-order-not-registered`)
  - No jurisdiction citation (`:no-spec-basis`)
  - Evidence checklist incomplete (`:evidence-incomplete`)
  - Pesticide/herbicide-applicator license expired (`:applicator-license-expired`) — only for chemical-application service types
  - Sprayer/applicator equipment calibration overdue (`:equipment-calibration-overdue`) — only for chemical-application service types
  - Power-equipment (chainsaw/pole-saw/heavy-mower) safety-clearance certification expired (`:equipment-safety-clearance-expired`) — only for service types that require one
  - Restricted-entry interval violated (`:restricted-entry-interval-violated`) — only for chemical-application service types
  - Wind speed exceeded the safe spray-drift ceiling (`:wind-speed-exceeded`) — only for chemical-application service types
  - Buffer zone narrower than the service type's minimum (`:buffer-zone-violated`) — only for chemical-application service types
  - Proposal covertly requests a final equipment-safety clearance or a final pesticide-application decision, via structured boolean flags (`:equipment-safety-or-pesticide-decision-blocked`) — a HARD, PERMANENT block, defense-in-depth against every op
  - Proposal content mentions the finalization action in free text, via a defense-in-depth text scan (`:scope-exclusion-text`) — also a HARD, PERMANENT block
  - Unresolved safety concern (`:safety-concern-flag-unresolved`)
  - Site order already logged (`:already-logged`, double-commit guard)
- **Escalate** (human sign-off always required):
  - `:log-service-record` — the one real actuation event this actor performs, always requires human sign-off even when the Governor is otherwise clean
  - `:flag-safety-concern` — an equipment-hazard or pesticide/herbicide-drift concern is never auto-resolved by advisor confidence alone, and this op is never in any phase's auto-commit set
  - `:coordinate-supply-order` above `governor/supply-order-cost-threshold-usd` (5000 USD)
  - Low advisor confidence (below `governor/confidence-floor`, 0.6)
- **Commit** (advisor proposal approved; Governor clean; not a mandatory-escalation op):
  - Routine, low-stakes proposals only — in this actor's current allowlist that is effectively `:schedule-maintenance-operation` when clean, or `:coordinate-supply-order` at or below the cost threshold

### Scope-exclusion phrasing (known bug class in this actor family)

The two scope-exclusion checks above are deliberately phrased as the
**finalization/execution action** ("finalize the pesticide-application
decision"), never as a bare noun ("pesticide"). A bare-noun term list
would match inside this actor's own default mock-advisor rationale text
for entirely legitimate, allowed proposals — which routinely and
correctly use words like "equipment", "safety", or "pesticide" in a
purely descriptive/logistics sense — and would cause the actor to
self-block on its own happy path. `test/landscapecare/advisor_test.cljc`
is a dedicated regression test asserting the advisor's own default
proposals for every op in the closed allowlist never trip either
scope-exclusion check.

### Operations (Proposals)

Closed allowlist — the advisor may **only** ever propose these four
operation types, all `:effect :propose`:

- **`:log-service-record`** — Log mowing/pruning/treatment-visit service data, plus safety/compliance parameters, into service records (always requires human sign-off)
- **`:schedule-maintenance-operation`** — Propose crew/equipment/site scheduling for an upcoming maintenance visit (routine, low risk)
- **`:flag-safety-concern`** — Surface an equipment-hazard or pesticide/herbicide-drift concern; always escalates
- **`:coordinate-supply-order`** — Propose equipment/chemical/plant-material procurement (escalates above the cost threshold)

Any proposal for an operation outside this allowlist — most importantly
anything that would amount to directly finalizing an equipment-safety
clearance or a pesticide-application decision — is refused
unconditionally by the Governor (`:op-not-allowed`), regardless of
advisor confidence.

## Testing

```bash
# Run full test suite
clojure -M:test

# Check code quality
clojure -M:lint

# Run demo simulation
clojure -M:run
```

## Standalone Use

This repo is **forkable outside the workspace**. If cloning standalone
(not in the kotoba-lang monorepo), override `:local/root` paths in `deps.edn`:

```clojure
{:deps {io.github.kotoba-lang/langchain {:git/url "https://github.com/kotoba-lang/langchain" :git/tag "v0.1.0"}
        io.github.kotoba-lang/langgraph {:git/url "https://github.com/kotoba-lang/langgraph" :git/tag "v0.1.0"}}}
```

## License

AGPL-3.0-or-later. Forking/contribution welcome; see `CONTRIBUTING.md`.

## Security

Report security issues to the issue tracker or private disclosure; see `SECURITY.md`.

---

Part of **cloud-itonami**: autonomous actor fleet for regulated industries. See [github.com/cloud-itonami](https://github.com/cloud-itonami).
