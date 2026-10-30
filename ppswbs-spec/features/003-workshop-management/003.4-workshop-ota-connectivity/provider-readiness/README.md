# Provider Readiness Evidence Template

**Date created**: 2026-10-29
**Date updated**: 2026-10-29
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Purpose and use

Copy this template into one readiness record per provider and mapping. Record
evidence references and review outcomes; do not copy credentials, signing
secrets, raw callback bodies, payment-card data, or unnecessary customer data
into the record. A missing or unverified item blocks booking enablement. A
completed record documents evidence and review; it does not itself authorize
production launch.

Use only provider-approved onboarding material for provider-specific routes,
authentication, callback acknowledgements, quotas, retry rules, and commercial
terms. Do not infer these values from public API documentation or simulator
fixtures. Keep each mapping sales-closed until its required evidence has been
reviewed and the authorized enablement decision is recorded.

## Record identity

| Field | Value |
| --- | --- |
| Provider | `<provider name>` |
| Environment | `<sandbox or production>` |
| Mland mapping reference | `<opaque mapping reference>` |
| Mland offer/session references | `<opaque references>` |
| Provider product/option references | `<approved references>` |
| Evidence review date | `<YYYY-MM-DD>` |
| Evidence reviewer | `<responsible reviewer>` |
| Booking state | `SALES_CLOSED` until authorized enablement |

## Evidence register

For each row, record a safe evidence reference, the review outcome, reviewer,
and review date. Keep the underlying artifact in its approved location with
appropriate access controls.

| Area | Evidence to establish | Evidence reference | Outcome | Reviewer / date |
| --- | --- | --- | --- | --- |
| Contract and onboarding | Signed/onboarded commercial terms and authority to integrate this product/option. | `<reference>` | `MISSING` | `<reviewer / date>` |
| Commercial terms | Confirmed price semantics, commission/fees, taxes, currency/FX, and payout terms that apply to this mapping. | `<reference>` | `MISSING` | `<reviewer / date>` |
| Connection ownership | Approved environment and Admin-owned opaque credential reference; no credential value is recorded here. | `<reference>` | `MISSING` | `<reviewer / date>` |
| Callback authentication | Approved authentication/signature procedure and evidence of valid, invalid, and replay handling. | `<reference>` | `MISSING` | `<reviewer / date>` |
| Quota and delivery behavior | Provider-approved quota, timeout, retry, idempotency, and acknowledgement behavior. Record only values confirmed by onboarding evidence. | `<reference>` | `MISSING` | `<reviewer / date>` |
| Product and session mapping | Provider product/option maps to an eligible Basic Ring offer/session and canonical Mland location. | `<reference>` | `MISSING` | `<reviewer / date>` |
| Data allowlist | Fields received/sent, operational purpose, provider authorization, and applicable retention source. Exclude unapproved contact and participant data. | `<reference>` | `MISSING` | `<reviewer / date>` |
| Preparation synchronization | Schedule, availability, normal sales-close, and confirmed re-sync behavior were exercised while booking remained disabled. | `<reference>` | `MISSING` | `<reviewer / date>` |
| Ticket delivery and scan | A sandbox booking for `N` participants produced exactly `N` individual Mland tickets/QR values, and each was independently scanned. | `<reference>` | `MISSING` | `<reviewer / date>` |
| Cancellation and refund | Request and confirmed outcome are distinguished; duplicate, late, and out-of-order delivery behavior is documented and tested. | `<reference>` | `MISSING` | `<reviewer / date>` |
| Late booking and capacity responsibility | Written provider behavior supports Mland rejection after sales-close without a capacity commitment, including timeout/retry responsibility. | `<reference>` | `MISSING` | `<reviewer / date>` |
| Failure isolation and reconciliation | Retry exhaustion closes only the affected OTA mapping, creates actionable reconciliation evidence, and does not alter Operations capacity, sale state, or direct-Mland booking. | `<reference>` | `MISSING` | `<reviewer / date>` |

## Review and enablement decision

| Decision field | Value |
| --- | --- |
| Unresolved evidence or exceptions | `<none, or describe with evidence references>` |
| Booking enablement outcome | `BLOCKED` until every required gate is verified |
| Authorized decision maker | `<name and role>` |
| Decision date | `<YYYY-MM-DD>` |
| Decision evidence reference | `<reference>` |

Any missing, failed, or conditional evidence keeps the mapping sales-closed.
Preparation synchronization may continue only within the approved sales-closed
behavior. Record the resolution and its evidence before requesting a new review.

The Admin booking-enable action is the final readiness attestation. It records
one outcome and safe evidence reference for each checklist area above, bound to
the provider, environment and exact mapping. Mland persists the assessment,
checklist references, Admin account, decision and timestamp; it does not copy
the underlying documents. Rejected or incomplete reviews remain in the audit
history and do not enable sales. A changed mapping or environment needs a new
assessment. Booking enablement is synchronous and atomic with the approved
assessment; only a `SALES_CLOSED` mapping can be enabled.

The reviewed readiness record is the authoritative approval for the permitted
provider data fields. The matching provider adapter enforces those fields with
an explicit code allowlist and default-deny behavior. Updating the approved
allowlist requires a corresponding code change and release; do not maintain a
second mutable copy in the application database. Tests must verify that
non-allowlisted fields are discarded before persistence, audit, error, and API
projection. Missing allowlist evidence blocks booking enablement.

Commercial terms are negotiated and confirmed with the provider outside Mland.
An active Manager records only the final provider-confirmed terms and a safe
evidence reference in Mland. This action creates an approved immutable price
snapshot immediately; it is not a draft submission for another Mland actor to
review. Unconfirmed proposals are not persisted, and the mapping remains
pending and sales-closed until confirmed terms and all other readiness gates
are present.
