# Data Model: Workshop Booking Core

**Date created**: 2026-10-07
**Date updated**: 2026-10-29
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Status**: Draft design — schema names and migrations require approval before implementation.

## Ownership and aggregate

`WorkshopBooking` is one direct-Mland commercial aggregate for one package. It
is not a cart and does not own session configuration, check-in, attendance,
settlement, refund, reschedule, or OTA facts.

```text
WorkshopBooking (1) ── (1) BookingInvoice
        ├── (0..N) MemberBookingQuote (single-use acceptance proof)
        │             └── (N) PaymentAttempt / (N) PaymentEventReceipt
         ├── (Package-required set) BookingSessionCommitment ── WorkshopSession (003.2)
        ├── (N) BookingTicket ── (1..2) TicketSessionEntitlement
        └── (0..N) NotificationIntent / (0..N) MagicLinkAccess
```

## Entities and invariants

| Entity | Owned data | Required invariant |
| --- | --- | --- |
| WorkshopBooking | booking code; frozen Package selection snapshot; contact email/phone snapshot; optional Member owner reference; participant count; service/financial status; expiry | Exactly one Package and one contact. The snapshot comes only from 003.2's published-Package boundary; `participant_count` is positive and determines ticket count. |
| MemberBookingQuote | quote reference; owning Member; package ID/name/price snapshot; ordered session UUID references with location/date facts; participant count; requested benefit selection; Loyalty terms digest and amount breakdown; acceptance state and accepted booking reference | A quote is bound to its authenticated Member and exact selection. It reserves no capacity, voucher quota, or points. Acceptance reloads authoritative Operations facts and Loyalty revalidates terms in the same transaction that reserves all session capacity and held benefits. A quote is single-use; changed terms require a fresh preview and explicit customer confirmation. |
| BookingSessionCommitment | booking; opaque UUID `sessionReference`; chronological display ordinal; canonical location snapshot; held/committed seat count | The accepted commitment set is decided atomically by 003.2 from the selected Package rules. Booking does not retain a numeric session ID or independently authorize session count, location or slot gap. |
| CapacityHold | 003.2 capacity-facade hold reference and safe outcome snapshot for a booking-session commitment | 003.2 is authoritative for the hold. Booking retains only the reference/status needed for its own pending, expiry and release behavior; one requested set is reserved/released atomically and exactly once. |
| BookingInvoice | booking; frozen package price/participant count; frozen post-promotion amount; deposit; remaining amount | `package_total = participant_count × price_per_participant`; `deposit = ceil(post_promotion_amount / 2)` and `remaining = post_promotion_amount − deposit`. All three monetary snapshots are whole VND and immutable after acceptance. |
| PaymentAttempt / PaymentEventReceipt | booking/invoice; safe provider references; attempt outcome; unique provider event identity | Only valid, matching, non-duplicate provider evidence settles the invoice. `FAILED` belongs to an attempt, not booking state. |
| BookingTicket | booking; delivery-safe ticket identity/QR reference and ticket code received from `WorkshopOperationsFacade`; delivery state | A booking-side delivery projection, not the authoritative Operations ticket. Exactly N tickets are available only after confirmation; no participant identity/contact is stored. |
| TicketSessionEntitlement | `WorkshopOperationsFacade` delivery-safe view; booking-session commitment | A booking-side delivery view of each 003.2 ticket/session entitlement. A Silverclay ticket has two; 003.2 owns entitlement validation and check-in/attendance state on that pair. Customer reads obtain the current projection from 003.2, so this copy never becomes the authority for a replacement/refund schedule. |
| MagicLinkAccess | booking; protected token hash; issued/expiry/revocation lifecycle state | Raw token is never persisted/logged. Access is reusable until associated workshop end or revocation; rotation revokes the old link and creates its replacement for the stored booking-contact email. |
| NotificationIntent | booking; immutable notification reference, kind, correlation reference, and immutable render-ready delivery snapshot | Created transactionally with confirmed booking delivery, accepted resend, or accepted Magic-Link rotation. Its post-commit event carries references only; Feature 007 obtains the snapshot through the approved facade and owns delivery attempts/retries as operational logs. Delivery failure cannot reverse the accepted Booking outcome. |
| BookingAuditEvidence | safe actor type or identity reference when available; booking code; applicable canonical location; action; outcome; correlation reference; timestamp; Manager reason where required | Covers accepted self-service resend, every internal resend, Magic-Link revoke/rotate, and Manager late-payment `RECORD`/`ESCALATE` follow-up. It excludes contact proof, raw Magic Link, QR, token material and unnecessary PII. |

## Lifecycle

```text
PENDING_PAYMENT + UNPAID
  ├─ verified deposit event → CONFIRMED + DEPOSIT_PAID
  ├─ hold expiry → EXPIRED + UNPAID
  └─ customer release while active → CANCELLED + UNPAID

CONFIRMED + DEPOSIT_PAID
  └─ idempotent final-balance-resolved conclusion from 003.3 → financial status FULLY_PAID
```

The final conclusion changes only Booking's financial status. Booking retains a
safe correlation/reference and does not receive raw VNPay/final-payment detail,
calculate final balance, or treat a deferred-payment-link delivery as settlement.

Only the listed transitions belong to 003.1. Final payment, refund,
reschedule, `CHECKED_IN`, `NO_SHOW`, and attendance finalization are outside it.

## Validation and concurrency

- Booking obtains the frozen published-Package selection snapshot and sale
  eligibility only through `WorkshopOperationsFacade`; no direct access to
  003.2 persistence is permitted. It sends `packageReference`, the complete
  UUID session-reference set and participant count to the atomic reserve
  command; Operations returns the safe Package/capacity refusal outcome.
- Member preview calls Loyalty `previewBenefits` using Package price and all
  authoritative selected-session facts, then persists the immutable quote
  selection and returned `termsDigest`. Guest booking remains available without
  Loyalty benefits. Member accept sends only `quoteRef` plus contact details;
  the server binds the quote to the authenticated Member and does not accept
  client-supplied prices, discounts, point amounts or session facts.
- In Member accept, Booking revalidates the stored quote against current
  Operations facts, reserves capacity for the complete session set, then calls
  `reserveAcceptedTransaction` in the same MySQL transaction. The returned
  `postPromotionAmount` is frozen into the invoice; Booking computes
  `deposit = ceil(post / 2)` and `remaining = post - deposit`. A failure in
  either boundary rolls back both capacity and Loyalty holds.
- A verified deposit settles held benefits. Earned points are granted only
  after every required session is completed and the invoice is fully paid.
  Expiry releases held benefits; an eligible cancellation restores benefits;
  a final no-show finishes earn eligibility without reversing consumed
  benefits. These calls use the source outcome version and proof references
  from Booking/Operations/Settlement, never raw gateway payloads.
- 003.2 atomically reserves the requested participant count in every selected
  session or creates no hold. It commits the complete active set or commits
  none, and releases active holds idempotently; `SALES_PAUSED` rejects new holds
  while preserving unexpired existing holds to their deadline.
- Booking preserves chronological display ordinals only after Operations accepts
  the selected set. Operations owns enforcement of the Package's distinctness,
  location and same-day slot-gap rules.
- All packages consume the one shared capacity pool per session. A multi-session
  booking consumes N seats in every selected session.
- Create, capacity reservation and expiry/release have one all-or-nothing
  business outcome through the synchronous 003.2 `WorkshopOperationsFacade`. Each
  booking-critical command shares one local MySQL transaction across Booking's
  own writes and that capacity call; gateway communication is outside it. Only
  after a successful commit does Booking call the separate ticket-projection
  facade. Provider-event receipt uniqueness and command idempotency prevent
  duplicate confirmation or release. Events may run only after a local Booking
  state transition and cannot determine capacity or confirmation.
- Before internal resend, Magic-Link incident handling, or late-payment follow-up,
  Mland rechecks canonical active account facts and booking's own support-action
  rule against booking-location scope, using the same `workshop_location.id` as Account `activeLocationIds`.
  A failed check has no booking/payment/ticket/capacity side effect and records
  only the required minimal denied-attempt audit evidence.

## Migration boundary

This document does not authorize a migration. Existing applied migrations stay
unchanged. Dev/test reset requires a preflight confirming no booking/customer
data needs retention. Staging, production, or any data-bearing environment needs
an append-only preservation migration and upgrade/recovery evidence.
