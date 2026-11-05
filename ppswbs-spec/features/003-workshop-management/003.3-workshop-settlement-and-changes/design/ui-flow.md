# UI Flow Design: 003.3 Workshop Settlement and Changes

**Date created**: 2026-11-02
**Date updated**: 2026-11-03
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Scope and sources

- Business rules: `spec.md` (final bill/payment, direct cancellation, required-session changes, Mland-caused remedy/refund, add-ons).
- Transport contract: `contracts/workshop-settlement.openapi.yaml`.
- Current implementation reference: `ppswbs_frontend/src/workshop-settlement/`.

## Actors, routes, and authorization

| Actor | Route | Access rule |
| --- | --- | --- |
| Staff/Manager | `/settlement` | Staff operates in location scope; Manager has global Feature 003 scope. |
| Guest/Member | Booking lookup/detail | May view their own bill/case result and submit permitted whole-booking cancellation; ownership is server-verified. |
| Manager | Settlement/case actions | Required-session change, Mland-caused remedy/refund exception and policy administration are Manager-authorized. |

## Screen and interaction flows

| Screen / flow | Workflow/API reference | Required states and recovery |
| --- | --- | --- |
| Final bill workspace | `GET/POST /api/v1/workshop-bookings/{bookingCode}/final-bill` | Loading; no bill yet; immutable/snapshot lines; mismatch or finalized state is read-only. |
| Add-on catalogue (read-only) | Booking detail / settlement workspace | Show eligible package/location add-ons and prices; no customer selection or checkout. For direct Mland bookings, Staff/Manager may confirm any active priced add-on, including laser engraving; the OTA-only free rule is not applied here. |
| Charge/add-on line | `/final-bill/charges` | Staff/Manager records workshop consent; server derives catalogue price; direct OPEN bill appends, finalized direct booking uses a separate bill, OTA uses separate direct-Mland bill; reject stale/duplicate charge. |
| Final payment | `/final-bill/payment-attempts`, `/final-bill/finalizations` | Separate `FINAL_PAYMENT` attempt; server-derived outstanding VND balance; payment attempt expires after 10 minutes; only verified IPN/QueryDR can settle. No deferred-credit path. |
| Direct customer cancellation | `/cancellation-cases` | Whole-booking request with reason; show non-refundable result; release/cancel through Operations handoff; never show a VNPay refund action. One case per booking. |
| Required-session change | `/required-session-change-cases` | Show eligible replacement options and stale-capacity conflict; no silent partial change; recover by reloading current sessions. |
| Mland-caused remedy/refund | `/mland-cancellation-remedy-cases` | Manager-only decision path; calculate from retained policy/payment evidence; show `PENDING_PROVIDER` only while awaiting verified provider evidence; do not mark complete on provider acceptance alone. |
| Refund-policy administration | `/workshop-refund-policies` | Manager global; show effective time and percentage validation; preserve historical snapshots and display audit evidence. |
| Settlement result | Bill/case detail | Distinguish service state, bill state, payment state, and case state; show typed errors and correlation ID without secrets. |

## Final-payment recovery guardrails for regeneration

The final-payment screen uses only the state returned by Settlement and
verified `paymentintegration` evidence. It does not infer payment truth from a
provider redirect.

| Observed state | UI action permitted | UI action prohibited |
| --- | --- | --- |
| `PENDING` | Display the provider attempt deadline and prevent duplicate checkout. | Creating another final-payment URL. |
| `UNKNOWN` (network timeout, lost response, unverified provider result) | Retain the original `Idempotency-Key`; offer an explicit server status check or same-command retry with that key. | Automatic retry, a new key, a new attempt, output handover, or a generic reissue control. |
| `VERIFIED_PAID` | Refresh the server bill and allow only its eligible finalization/release action. | Using browser-return data as proof. |
| `EXPIRED` | Keep the bill unpaid and output withheld; show reconciliation status. | Immediate new checkout, credit, reminder, or payment-link reissue. |

After an `EXPIRED` or `UNKNOWN` outcome, any later attempt is exclusively a
server decision after QueryDR under `FR-011`; the UI does not manufacture a
recovery path.

## Invariants captured for regeneration

1. Direct customer cancellation after a confirmed direct booking is non-refundable; it does not create `PENDING_PROVIDER`, QueryDR, or VNPay refund.
2. VNPay refund remains only for an approved Mland-caused direct-payment remedy, not for ordinary customer cancellation and not for OTA-collected base-package money.
3. Final-payment checkout is separate from the deposit attempt and expires after 10 minutes.
4. A provider response is not verified completion; the case changes only after verified evidence.
5. No credit/deferred-payment workflow is exposed.
6. Customer add-on selection is not a booking step. Staff/Manager confirmation is
   required at the workshop; direct OPEN bills may append, finalized direct
   bookings and all OTA bookings use separate direct-Mland add-on bills. OTA
   base-package financial facts remain isolated.

## Session, data handling, and accessibility

- Customer views must expose only their own booking/bill/case data.
- Never display gateway secrets, raw callback payloads, card data, or unredacted PII.
- On payment timeout, keep the bill snapshot and show the limited `UNKNOWN`
  recovery action rather than silently retrying. Do not call it failed or offer
  reissue until server-side reconciliation has produced an eligible outcome.
- Tables, totals, status badges, and confirmation dialogs must be keyboard accessible and expose text equivalents for color/state.

## Open decisions and dependencies

- **DRAFT**: exact Figma revision/node IDs and final customer-facing copy for remedy/case statuses.
- **Dependency**: verified payment/refund evidence remains owned by `paymentintegration`; the UI must consume the settlement facade/contract only.
