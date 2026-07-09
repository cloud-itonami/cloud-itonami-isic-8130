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

## License

AGPL-3.0-or-later.
