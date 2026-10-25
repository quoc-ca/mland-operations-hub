# Data Model: Workshop Booking Core

**Date created**: 2026-10-07
**Date updated**: 2026-10-25
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
| CapacityHold | 003.2 hold reference and safe outcome snapshot for a booking-session commitment | 003.2 is authoritative for the hold. Booking retains only the reference/status needed for its own pending, expiry and release behavior; one requested set is reserved/released atomically and exactly once. |
| BookingInvoice | booking; frozen even-VND price per participant; participant count; frozen total; 50% deposit | `total = participant_count × price_per_participant`; price is even VND and deposit is exactly total/2. |
| PaymentAttempt / PaymentEventReceipt | booking/invoice; safe provider references; attempt outcome; unique provider event identity | Only valid, matching, non-duplicate provider evidence settles the invoice. `FAILED` belongs to an attempt, not booking state. |
| BookingTicket | booking; delivery-safe ticket identity/QR reference and ticket code received from 003.2; delivery state | A booking-side delivery projection, not the authoritative Operations ticket. Exactly N tickets are available only after confirmation; no participant identity/contact is stored. |
| TicketSessionEntitlement | ticket delivery projection; booking-session commitment | A booking-side view of each 003.2 ticket/session entitlement. A Silverclay ticket has two; 003.2 owns entitlement validation and check-in/attendance state on that pair. |
| MagicLinkAccess | booking; protected token hash; expiry; revoke/consume metadata | Raw token is never persisted/logged. Access ends no later than associated workshop end. |
| NotificationIntent | booking; delivery channel/result/retry evidence | Delivery failure cannot reverse a confirmed payment. |

## Lifecycle

```text
PENDING_PAYMENT + UNPAID
  ├─ verified deposit event → CONFIRMED + DEPOSIT_PAID
  ├─ hold expiry → EXPIRED + UNPAID
  └─ customer release while active → CANCELLED + UNPAID
```

Only the listed transitions belong to 003.1. Final payment, refund,
reschedule, `CHECKED_IN`, `NO_SHOW`, and attendance finalization are outside it.

## Validation and concurrency

- Selected sessions must be customer-selectable and capacity-eligible through
  the 003.2 sale-eligibility snapshot; no direct access to 003.2 persistence is
  permitted.
- 003.2 atomically reserves the requested participant count in every selected
  session or creates no hold. It commits the complete active set or commits
  none, and releases active holds idempotently; `SALES_PAUSED` rejects new holds
  while preserving unexpired existing holds to their deadline.
- Silverclay sessions must be distinct, same branch and non-adjacent if on the
  same local day; different days have no maximum gap.
- All packages consume the one shared capacity pool per session. A multi-session
  booking consumes N seats in every selected session.
- Create, capacity reservation and expiry/release have one all-or-nothing
  business outcome through the 003.2 boundary. The cross-module transaction
  mechanism remains unselected. Provider-event receipt uniqueness and command
  idempotency prevent duplicate confirmation or release.

## Migration boundary

This document does not authorize a migration. Existing applied migrations stay
unchanged. Dev/test reset requires an environment preflight; production/customer
data needs an append-only preservation migration and upgrade/recovery evidence.
