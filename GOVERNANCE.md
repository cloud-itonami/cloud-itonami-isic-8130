# Governance

`cloud-itonami-8130` is an OSS open-business blueprint for community
landscape care and maintenance operations, robotics-premised.

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a robot action the governor refuses is never dispatched to hardware.
- the Landscape Care Governor remains independent of the advisor.
- hard policy violations (an out-of-scope chemical application, an
  unverified irrigation change, an unverified follow-up record)
  cannot be overridden by human approval.
- every dispatch, sign-off and follow-up path is auditable.
- sensitive client and property data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model, storage contract, public business model, operator certification or license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a separate trust mark and should require security, robot-safety, audit and data-flow review.

Certified operators can lose certification for:
- bypassing robot-safety or licensing-scope checks
- mishandling client or property data
- misrepresenting certification status
- failing to respond to safety incidents
