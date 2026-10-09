# Data Model: Customer Workshop Booking Lifecycle

## WorkshopBooking

One Guest or Member request for a package/session. Owns a unique booking code,
contact and participant snapshots, package/price/currency snapshots and the
current service/financial states.

- Current service states: `PENDING_PAYMENT`, `CONFIRMED`, `EXPIRED`, `CANCELLED`.
- `CANCELLED` is only used when the Guest or Member explicitly releases an
  active pre-payment hold. Post-confirmation cancellation and refund remain
  deferred to a later feature.
- Future reserved states: `CHECKED_IN`, `COMPLETED`, `NO_SHOW`.
- Booking financial states: `UNPAID`, `DEPOSIT_PAID`, `FULLY_PAID`,
  `REFUND_PENDING`, `REFUNDED`. `FAILED` belongs to `PaymentAttempt`, not to
  the booking financial state.
- Confirmation requires one valid, signed, non-duplicate deposit result.

## WorkshopSession and CapacityHold

The session is a dated location/session with configured capacity. A hold stores
participant count, booking/session identity, creation time, expiry time and a
release generation. Holds are released exactly once and availability is based
on participants, not booking-row count.

## WorkshopInvoice and PaymentAttempt

The invoice freezes package amount, deposit percentage, deposit amount and
currency. A payment attempt stores safe provider references and attempt state;
an attempt failure does not fail the booking while its hold is active.

## PaymentEventReceipt

Immutable deduplication evidence for provider callbacks. Provider event identity
is unique; mismatched, unsigned or unknown events cannot confirm a booking.

## BookingTicket and NotificationIntent

The ticket is issued only after confirmation. Notification failure does not roll
back confirmation; notification intent retries are safe and auditable. The QR
payload is a signed opaque token/reference only and contains no PII, payment
data, Magic Link token or secret.
The ticket remains valid until the associated workshop ends and supports explicit
revocation without mutating historical payment evidence.
Resending a confirmation notification reuses the same active ticket and does
not create a duplicate ticket.

## ExternalRegistrationHandoff

A durable post-confirmation handoff for the later synchronization feature. Its
processing cannot block local confirmation and must be idempotent later.

## Transitions

```text
PENDING_PAYMENT + UNPAID
  ├─ valid deposit callback → CONFIRMED + DEPOSIT_PAID
  ├─ failed/cancelled attempt → PENDING_PAYMENT + UNPAID
  ├─ explicit customer release while hold is active → CANCELLED + UNPAID
  └─ hold expiry → EXPIRED + UNPAID; any failed payment attempt remains
     recorded as `FAILED` on `PaymentAttempt`

CONFIRMED + DEPOSIT_PAID
  └─ CHECKED_IN / COMPLETED / NO_SHOW are later-feature transitions
```

The `workshopbooking` module owns these records. Payment provider access is
behind a payments facade/adapter; no module accesses another module's
repositories or entities directly.
