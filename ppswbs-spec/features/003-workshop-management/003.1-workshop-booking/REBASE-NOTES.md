# Rebase notes — Workshop Booking Core

**Status**: Required before additional implementation is planned from the
original V1 artifacts in this directory.

## Target aggregate

```text
Booking (commercial order and payer/contact)
└── One selected workshop package/course
    ├── Required session × one or more (for example, Silverclay has two)
    └── Attendance ticket × actual people attending the package
```

- A **booking** is the commercial order. It has one booking code and the V1
  booking contact receives every issued QR/ticket code.
- A booking selects one package/course. It is not a generic cart and cannot
  contain unrelated packages. A package may require one or more sessions; all
  required sessions are part of the same purchased entitlement.
- An **attendance ticket** belongs to one actual attendee and carries an
  independent QR and ticket code. Capacity, check-in, attendance finalization,
  and no-show are counted per ticket and session.
- Capacity, check-in, attendance finalization, and no-show remain per
  ticket/session even when one package contains several required sessions.
- **Superseding V1 pricing decision — 2026-10-25, Hồ Hoàng Quốc:** direct
  Mland booking supports `PER_PERSON` only. Each purchased participant has one
  ticket and price is calculated from the package price per participant.
  `BASE_PLUS_EXTRA` and shared-item/co-maker pricing are not V1 behaviour.
- OTA bookings use the same per-participant pricing baseline.

## Boundary of feature 002

Feature 002 finishes when an eligible booking has a valid confirmed deposit,
capacity has been held/confirmed, and the appropriate attendance tickets are
issued. It supports only pre-payment-hold cancellation (`PENDING_PAYMENT`).

It does **not** implement the following:

- session and Staff configuration, individual ticket check-in, exception audit,
  or attendance finalization/no-show: feature 005;
- post-session final bills, counter payment, exceptional payment links, manual
  reschedule, customer cancellation/refund, or Mland-caused item cancellation:
  feature 006;
- Klook/GetYourGuide or other OTA inventory, voucher, webhook, and settlement
  integration: feature 007.

## Required artifact changes before implementation resumes

1. Replace the single `participant_count` booking/session model in
   `data-model.md` with one booking/package, its required-session set, and
   ticket relationships.
2. Change the OpenAPI request/response shapes to represent one selected package
   with its required session set and return ticket sets, rather than a generic
   multi-package cart or a single-session-only booking.
3. Re-plan capacity holds, payment/deposit, QR issuance, and lookup around that
   aggregate.
4. Reconcile the completed V1 task list with the codebase before creating new
   implementation tasks; never retrofit the new model by silently reusing the
   old `participant_count` assumptions.
