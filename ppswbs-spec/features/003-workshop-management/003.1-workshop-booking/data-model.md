# Data Model: Customer Workshop Booking Lifecycle

## WorkshopBooking

One Guest or Member request for a package/session. Owns a unique booking code,
booking-contact snapshot, participant count, package/price/currency snapshots
and the current service/financial states. V1 does not create participant identity
or contact snapshots for the individual purchased seats.

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

The invoice freezes the total booking amount, deposit percentage, deposit amount
and currency. The total is calculated once from the package's approved
participant-pricing policy and the requested participant count. A payment attempt
stores safe provider references and attempt state; an attempt failure does not
fail the booking while its hold is active. One booking has one invoice and one
accepted deposit transaction, regardless of its participant count.

## PaymentEventReceipt

Immutable deduplication evidence for provider callbacks. Provider event identity
is unique; mismatched, unsigned or unknown events cannot confirm a booking.

## BookingTicket and NotificationIntent

`WorkshopBooking 1 → N BookingTicket`, where `N` is exactly the booking's
`participant_count` at confirmation. Tickets are individual purchased-seat
credentials, not separate bookings or payment obligations. Each ticket has a
unique `ticket_code` for manual fallback, an opaque signed QR token/reference,
its own revocation and later check-in state, and no PII, payment data, Magic
Link token or secret. A booking code identifies the booking and ticket set; it
does not identify a specific ticket for check-in.

Tickets are issued only after confirmation. Notification failure does not roll
back confirmation; notification intent retries are safe and auditable. Every
ticket remains valid until the associated workshop ends and supports explicit
revocation without mutating historical payment evidence. Resending confirmation
reuses the same active ticket set and does not create, revoke or rotate tickets.
V1 uses `BOOKING_CONTACT_RECEIVES_ALL_TICKETS`: all tickets and the Magic Link
are delivered only to the submitted booking contact, who may distribute tickets
to the group. No individual participant contact is required or inferred. A later
`EACH_PARTICIPANT_RECEIVES_TICKET` feature must introduce participant contact,
claim/delivery and transfer rules explicitly rather than altering this model.

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
