# Data Model: Workshop Booking Core

**Date created**: 2026-10-07
**Date updated**: 2026-10-26
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Status**: Draft design — schema names and migrations require approval before implementation.

## Ownership and aggregate

`WorkshopBooking` is one direct-Mland commercial aggregate for one package. It
is not a cart and does not own session configuration, check-in, attendance,
settlement, refund, reschedule, or OTA facts.

```text
WorkshopBooking (1) ── (1) BookingInvoice
        │             └── (N) PaymentAttempt / (N) PaymentEventReceipt
        ├── (1..2 for V1) BookingSessionCommitment ── WorkshopSession (003.2)
        ├── (N) BookingTicket ── (1..2) TicketSessionEntitlement
        └── (0..N) NotificationIntent / (0..N) MagicLinkAccess
```

## Entities and invariants

| Entity | Owned data | Required invariant |
| --- | --- | --- |
| WorkshopBooking | booking code; package snapshot; contact email/phone snapshot; optional Member owner reference; participant count; service/financial status; expiry | Exactly one package and one contact. `participant_count` is positive and determines ticket count. |
| BookingSessionCommitment | booking; session reference; chronological ordinal; branch snapshot; held/committed seat count | Basic Ring has one commitment. Silverclay has exactly two, distinct, same-branch commitments; ordinal is chronological; each seat count equals booking participant count. |
| CapacityHold | 003.2 capacity-facade hold reference and safe outcome snapshot for a booking-session commitment | 003.2 is authoritative for the hold. Booking retains only the reference/status needed for its own pending, expiry and release behavior; one requested set is reserved/released atomically and exactly once. |
| BookingInvoice | booking; frozen package price/participant count; frozen post-promotion amount; deposit; remaining amount | `package_total = participant_count × price_per_participant`; `deposit = ceil(post_promotion_amount / 2)` and `remaining = post_promotion_amount − deposit`. All three monetary snapshots are whole VND and immutable after acceptance. |
| PaymentAttempt / PaymentEventReceipt | booking/invoice; safe provider references; attempt outcome; unique provider event identity | Only valid, matching, non-duplicate provider evidence settles the invoice. `FAILED` belongs to an attempt, not booking state. |
| BookingTicket | booking; delivery-safe ticket identity/QR reference and ticket code received from `WorkshopOperationsFacade`; delivery state | A booking-side delivery projection, not the authoritative Operations ticket. Exactly N tickets are available only after confirmation; no participant identity/contact is stored. |
| TicketSessionEntitlement | `WorkshopOperationsFacade` delivery-safe view; booking-session commitment | A booking-side delivery view of each 003.2 ticket/session entitlement. A Silverclay ticket has two; 003.2 owns entitlement validation and check-in/attendance state on that pair. Customer reads obtain the current projection from 003.2, so this copy never becomes the authority for a replacement/refund schedule. |
| MagicLinkAccess | booking; protected token hash; issued/expiry/revocation lifecycle state | Raw token is never persisted/logged. Access is reusable until associated workshop end or revocation; rotation revokes the old link and creates its replacement for the stored booking-contact email. |
| NotificationIntent | booking; immutable notification reference, kind, correlation reference, and immutable render-ready delivery snapshot | Created transactionally with confirmed booking delivery, accepted resend, or accepted Magic-Link rotation. Its post-commit event carries references only; Feature 007 obtains the snapshot through the approved facade and owns delivery attempts/retries as operational logs. Delivery failure cannot reverse the accepted Booking outcome. |
| BookingAuditEvidence | safe actor type or identity reference when available; booking code; applicable branch; action; outcome; correlation reference; timestamp; Manager reason where required | Covers accepted self-service resend, every internal resend, Magic-Link revoke/rotate, and Manager late-payment `RECORD`/`ESCALATE` follow-up. It excludes contact proof, raw Magic Link, QR, token material and unnecessary PII. |

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

- Selected sessions must be customer-selectable and capacity-eligible through
  the 003.2 `WorkshopOperationsFacade` sale-eligibility snapshot; no direct access to
  003.2 persistence is permitted.
- 003.2 atomically reserves the requested participant count in every selected
  session or creates no hold. It commits the complete active set or commits
  none, and releases active holds idempotently; `SALES_PAUSED` rejects new holds
  while preserving unexpired existing holds to their deadline.
- Silverclay sessions must be distinct, same branch and non-adjacent if on the
  same local day; different days have no maximum gap.
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
  rule against booking-branch scope.
  A failed check has no booking/payment/ticket/capacity side effect and records
  only the required minimal denied-attempt audit evidence.

## Migration boundary

This document does not authorize a migration. Existing applied migrations stay
unchanged. Dev/test reset requires a preflight confirming no booking/customer
data needs retention. Staging, production, or any data-bearing environment needs
an append-only preservation migration and upgrade/recovery evidence.
