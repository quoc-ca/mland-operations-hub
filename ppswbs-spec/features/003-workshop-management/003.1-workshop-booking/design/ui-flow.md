# UI Flow Design: 003.1 Workshop Booking

**Date created**: 2026-11-02
**Date updated**: 2026-11-03
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
| Booking form | Guest/Member from package detail | `POST /api/v1/workshop-booking-quotes` | Derive the journey from session/authenticated canonical role: signed-out visitors see Guest only; authenticated `MEMBER` accounts see Member only. Do not render a manual Guest/Member switch. Keep ticket quantity at `1` until all required sessions are selected; render the ticket quantity control only after the required-session selection is complete. Once selected, label `participantCount` as `Số vé` (optionally `Số vé / số người tham gia`) and cap it at the minimum `remainingCapacity` across all selected sessions (still subject to the published Package policy). Preserve contact/session selections on validation failure, show capacity conflicts, and disable submit while pending. |
| Member benefits | Authenticated Member inside booking form | `POST /api/v1/workshop-booking-quotes` with `benefitSelection` | Use `BenefitSelector` state (`NONE`, `VOUCHER`, `POINTS`); Member must not type a quote UUID. Recalculate quote and set `quoteMatches=true` only for the current selection. The Member form loads `/api/v1/accounts/me/profile`, auto-fills profile contact data, and keeps fields editable only for the explicit booking-for-another-person use case. |
| Quote confirmation | Guest/Member | `POST /api/v1/workshop-bookings` using `quoteRef` | Reject changed/expired/invalid quotes; never accept a quote with unresolved `preview.reasons`; preserve the form for retry. |
| Pending payment | Guest/Member after accept | `POST /api/v1/workshop-bookings/{bookingCode}/payment-attempts` | Capacity hold and payment link share one deadline of at most 10 minutes. Show countdown; allow a new attempt only under the contract; expired state releases hold and shows recovery. |
| Lookup | Guest/Member | `POST /api/v1/booking-lookups`, member GET route, magic-link exchange | Loading; invalid proof; unauthorized ownership; expired magic link; preserve booking code but clear sensitive proof after failure. |
| Payment return | Guest/Member from VNPay | Browser return only; IPN/verified evidence is backend-owned | Show provider response and “checking backend confirmation”; never mark booking paid from query parameters. |
| Pending-hold release | Guest/Member | `POST /api/v1/workshop-bookings/{bookingCode}/pending-hold-release` | One cancellation action per booking attempt; release capacity, set booking `CANCELLED`, and do not create a refund. Replay returns the existing result. |
| Confirmation delivery | Guest/Member | `POST /api/v1/workshop-bookings/{bookingCode}/confirmation-deliveries` | Show send/retry result and rate limit; do not expose raw provider payload or secrets. |

### Behaviour backfilled from implementation (2026-11-12)

Recorded from commits made without a spec update (see `artifacts/003-spec-code-gap-review.md`).

| Screen / flow | Entry and actor | Workflow/API reference | Behaviour |
| --- | --- | --- | --- |
| Package page | Public Guest/Member | 003.2 discovery; Feature 011 public review list | Sessions are paged; the package's reviews show on the package page and on workshop list cards. Choosing a session card opens the booking page with that session, branch and dates pre-selected (12e7b76). |
| Booking form | Guest/Member | Quote and booking routes | A progress bar follows the form; the ticket count is a stepper (bfdb20e). |
| Member benefits | Authenticated Member | `POST /api/v1/workshop-booking-quotes` | Member picks a voucher from "Kho Voucher" instead of typing a code, sees available points, and the quote is requested automatically on change; Vietnamese `reasonDetails` messages are shown (FR-006a, FR-006b). |
| Hold success | Guest/Member right after accept | `POST .../payment-attempts` | The success page shows the VNPay deposit button and the hold countdown. When the countdown ends the booking is reloaded until the server reports the new state (bfdb20e). |
| Member bookings ("Đặt chỗ của tôi") | Authenticated Member on `/workshop-booking/lookup` | `GET /api/v1/members/me/workshop-bookings` | A signed-in Member picks a booking from the list instead of typing a code; the list shows tickets, `attendanceStatus` (including no-show) and expired holds from the last 24 hours (FR-021a, FR-021b). |
| Review offer | Authenticated Member in own bookings | Feature 011 eligibility, `ticketReferences` | A review is offered only for an attended ticket that Feature 011 reports eligible (FR-021c). |
| Home quick bar | Authenticated Member | Member bookings list | Shows the Member's next confirmed session and opens that booking (bfdb20e). |
| Magic Link | Anyone holding the link | `POST /api/v1/booking-magic-links/exchanges` | Opens the read-only booking without signing in, using the token carried by the confirmation email (881fd93). |
| Payment return (Staff) | Staff who opened a counter VNPay return page | 003.3 final bill | A Staff is told to go back to the booking's final bill instead of the customer view (34012cf). |

## Payment recovery guardrails for regeneration

These states apply to the booking lookup and browser-return views as well as
the initial pending-payment view. They are intentionally stricter than a
generic client-side "try again" pattern.

| Observed state | UI action permitted | UI action prohibited |
| --- | --- | --- |
| `PENDING` | Show the server deadline and wait for the provider/verified backend result. | Creating another attempt or implying the booking is paid. |
| `UNKNOWN` (network timeout, lost response, unverified provider result) | Keep the exact original `Idempotency-Key`; offer an explicit status check or a same-command retry with that key. | Automatic retry, a different key, a second payment URL, or a new booking. |
| `VERIFIED_PAID` | Show the confirmed booking/ticket projection supplied by the backend. | Treating browser return parameters as the evidence. |
| `VERIFIED_UNPAID` while the hold remains active | Follow only the backend's existing retry/attempt rule. | Extending the hold or inventing a payment link. |
| `EXPIRED` | Disable resume/send-again controls and route to a new booking journey. | Link reissue, resend, hold extension, or reactivation of the old booking. |

Confirmation resend is manually initiated and server rate-limited. A rejected
or rate-limited resend sends no email. The client must not auto-retry, rotate
tickets/Magic Link, or ask for a replacement delivery destination.

## Invariants captured for regeneration

1. Quote acceptance uses `quoteRef`; the client never fabricates or asks the Member to enter a UUID.
2. All selected sessions are sent and verified together; no partial booking is shown as successful.
3. Hold and payment-link expiry are both 10 minutes from the server-created deadline.
4. Browser return is informational; only signed IPN/QueryDR evidence can confirm payment.
5. Customer cancellation while payment is pending releases the hold and does not refund.
6. QR/ticket data is opaque and contains no PII.
7. A stale response must not overwrite newer contact, session, or benefit-selection state.
8. Until all required sessions are selected, the customer-facing ticket
   quantity remains `1` and the quantity control is not shown. After the
   required-session selection is complete, the upper bound is
   `min(package.maxParticipants,
   min(selectedSession.remainingCapacity))`. This is only a snapshot hint; the
   backend remains authoritative and rechecks Package min/max and capacity.
9. Regenerated clients must preserve the API field name `participantCount` and
   the ticket/seat UI terminology; changing the label does not rename the
   transport or persistence field.
10. A payment command state is not inferred from the browser. `UNKNOWN` keeps
    the original idempotency key and has only status-check/same-key recovery;
    `EXPIRED` has no reissue/resend recovery and starts a new booking instead.
11. Confirmation resend is user-initiated and rate-limited. It never retries
    itself after an error and never changes the booking, payment, ticket set,
    contact destination, or active Magic Link.

## Session, data handling, and accessibility

- Firebase proves identity; backend/MySQL decides ownership and authorization.
- Do not store payment secrets, raw callback payloads, or unnecessary PII in browser storage, URLs, or logs.
- On authentication loss, show a re-authentication state and preserve only non-sensitive form data.
- Every async action needs keyboard-accessible focus, a visible pending state, and an announced error/success result. Date/session controls must expose labels and validation text.

## Open decisions and dependencies

- **DRAFT**: record the exact Figma file revision/node IDs before visual regeneration.
- **Traceability gap**: map each screen row to the concrete `tasks.md` task and frontend verification case; do not infer a new task ID from the route name.
