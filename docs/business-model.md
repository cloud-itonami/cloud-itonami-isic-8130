# Business Model: Community Landscape Care and Maintenance Operations

## Classification
- Repository: `cloud-itonami-8130`
- ISIC Rev.5: `8130` — landscape care and maintenance service
  activities
- Social impact: applicator/crew worker safety, water stewardship,
  green-space access

## Customer
- independent/community landscape-care companies needing an
  auditable applicator-licensing and irrigation-compliance platform
- property managers and municipalities needing verifiable treatment
  and service records
- regulators needing verifiable pesticide/herbicide applicator-
  licensing and water-use compliance records
- programs that cannot accept closed, unauditable grounds-maintenance
  platforms

## Offer
- applicator-license and irrigation-compliance-scope management
- robotics-assisted mowing, trimming and irrigation-zone monitoring
- crew registration, dispatch and follow-up records
- client billing and disclosure records
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per service territory
- support retainer with SLA
- mowing/trimming/irrigation-monitoring robot integration and
  maintenance

## Trust Controls
- a robot action the governor refuses is never dispatched
- safety-critical actions (a pesticide/herbicide application outside
  verified applicator-license scope, an irrigation change without a
  completed water-compliance check, an unverified follow-up record)
  require human sign-off
- crews cannot be dispatched outside verified licensing scope
- follow-up records require verified evidence
- sensitive client and property data stays outside Git
