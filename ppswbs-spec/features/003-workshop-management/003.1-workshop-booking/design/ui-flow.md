# UI Flow Design: 003.1 Workshop Booking

**Date created**: 2026-11-02
**Date updated**: 2026-11-02
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Scope and sources

- Business rules: `spec.md` (customer booking, quote, hold, lookup, payment return, cancellation).
- Transport contract: `contracts/workshop-booking.openapi.yaml`.
- Current implementation reference: `ppswbs_frontend/src/workshops/`.
- Approved visual source: Mland Figma file supplied by the owner; exact node/revision must be recorded before a visual regeneration handoff.
- This document defines observable UI behavior. It does not replace the OpenAPI contract or backend authorization.

## Actors, routes, and authorization

| Actor | Route | Access rule |
| --- | --- | --- |
| Guest/Member | `/workshops` | Public package discovery; package/session data comes from the Operations contract. |
| Guest/Member | `/workshops/:packageId` | Public package detail and available sessions. |
| Guest/Member | `/workshops/:packageId/book` | Create quote and accept a booking; Member benefits are optional, Guest has no benefit selection. |
| Guest/Member | `/workshop-booking/lookup` | Guest proves ownership with booking code plus one contact proof; Member uses authenticated ownership. |
| Guest/Member | `/workshop-booking/:bookingCode/payment-return` | Display-only browser return. It never confirms payment by itself. |

The server remains authoritative for ownership, role, quote validity, capacity, payment state, and cancellation rights.

## Screen and interaction flows

| Screen / flow | Entry and actor | Workflow/API reference | Required states and recovery |
| --- | --- | --- | --- |
| Package list/detail | Public Guest/Member | Operations package/session discovery; see `003.2` contract | Loading; empty catalog; unavailable package; retry without inventing data. |
| Booking form | Guest/Member from package detail | `POST /api/v1/workshop-booking-quotes` | Preserve contact and session selections on validation failure; show session capacity conflicts; disable submit while request is pending. |
| Member benefits | Authenticated Member inside booking form | `POST /api/v1/workshop-booking-quotes` with `benefitSelection` | Use `BenefitSelector` state (`NONE`, `VOUCHER`, `POINTS`); Member must not type a quote UUID. Recalculate quote and set `quoteMatches=true` only for the current selection. |
| Quote confirmation | Guest/Member | `POST /api/v1/workshop-bookings` using `quoteRef` | Reject changed/expired/invalid quotes; never accept a quote with unresolved `preview.reasons`; preserve the form for retry. |
| Pending payment | Guest/Member after accept | `POST /api/v1/workshop-bookings/{bookingCode}/payment-attempts` | Capacity hold and payment link share one deadline of at most 10 minutes. Show countdown; allow a new attempt only under the contract; expired state releases hold and shows recovery. |
| Lookup | Guest/Member | `POST /api/v1/booking-lookups`, member GET route, magic-link exchange | Loading; invalid proof; unauthorized ownership; expired magic link; preserve booking code but clear sensitive proof after failure. |
| Payment return | Guest/Member from VNPay | Browser return only; IPN/verified evidence is backend-owned | Show provider response and “checking backend confirmation”; never mark booking paid from query parameters. |
| Pending-hold release | Guest/Member | `POST /api/v1/workshop-bookings/{bookingCode}/pending-hold-release` | One cancellation action per booking attempt; release capacity, set booking `CANCELLED`, and do not create a refund. Replay returns the existing result. |
| Confirmation delivery | Guest/Member | `POST /api/v1/workshop-bookings/{bookingCode}/confirmation-deliveries` | Show send/retry result and rate limit; do not expose raw provider payload or secrets. |

## Invariants captured for regeneration

1. Quote acceptance uses `quoteRef`; the client never fabricates or asks the Member to enter a UUID.
2. All selected sessions are sent and verified together; no partial booking is shown as successful.
3. Hold and payment-link expiry are both 10 minutes from the server-created deadline.
4. Browser return is informational; only signed IPN/QueryDR evidence can confirm payment.
5. Customer cancellation while payment is pending releases the hold and does not refund.
6. QR/ticket data is opaque and contains no PII.
7. A stale response must not overwrite newer contact, session, or benefit-selection state.

## Session, data handling, and accessibility

- Firebase proves identity; backend/MySQL decides ownership and authorization.
- Do not store payment secrets, raw callback payloads, or unnecessary PII in browser storage, URLs, or logs.
- On authentication loss, show a re-authentication state and preserve only non-sensitive form data.
- Every async action needs keyboard-accessible focus, a visible pending state, and an announced error/success result. Date/session controls must expose labels and validation text.

## Open decisions and dependencies

- **DRAFT**: record the exact Figma file revision/node IDs before visual regeneration.
- **Traceability gap**: map each screen row to the concrete `tasks.md` task and frontend verification case; do not infer a new task ID from the route name.
