# Rebase notes — Workshop Booking Core

**Status**: Required before additional implementation is planned from the
original V1 artifacts in this directory.

## Target aggregate

```text
Booking (commercial order and payer/contact)
├── Workshop item / booking line (one physical product or result; one session)
│   └── Attendance ticket × actual people attending that item
└── … additional workshop items, potentially in other sessions
```

- A **booking** is the commercial order. It has one booking code and the V1
  booking contact receives every issued QR/ticket code.
- A **workshop item** is the thing being made, such as one ring. Its frozen base
  price, deposit allocation, later assessed charges, and final bill belong to
  that item.
- An **attendance ticket** belongs to one actual attendee and carries an
  independent QR and ticket code. Capacity, check-in, attendance finalization,
  and no-show are counted per ticket and session.
- A booking may include several workshop items at different sessions.
- For direct Mland bookings, `BASE_PLUS_EXTRA` may represent several people
  making one item together. All such attendees use the same session and each
  gets a ticket. `PER_PERSON` normally creates one item and one ticket per
  purchased person.
- OTA bookings initially support only `PER_PERSON`; they do not expose the
  shared-item/co-maker model.

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
   `data-model.md` with booking, workshop item, ticket, allocation, and session
   relationships.
2. Change the OpenAPI request/response shapes to accept workshop items and
   return ticket sets, rather than treating all seats as one session.
3. Re-plan capacity holds, payment/deposit allocation, QR issuance, and lookup
   at the workshop-item/ticket levels.
4. Reconcile the completed V1 task list with the codebase before creating new
   implementation tasks; never retrofit the new model by silently reusing the
   old `participant_count` assumptions.
