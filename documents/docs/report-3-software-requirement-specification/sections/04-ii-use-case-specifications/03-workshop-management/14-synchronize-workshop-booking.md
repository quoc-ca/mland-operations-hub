### 3.14 Synchronize Workshop Booking

#### Primary Actors

Manager

#### Secondary Actors

External Registration System

#### Description

Sends enabled confirmed Mland workshop-booking updates to Klook and records Klook bookings that were confirmed through the Mland availability/hold flow.

#### Preconditions

1. The Klook integration is approved and enabled.
2. A Mland booking is confirmed or a Klook booking has passed the Mland availability/hold confirmation flow.
3. The booking payload contains the required branch, session, participant, and reference information.


#### Postconditions

- The eligible booking synchronization result is recorded with an idempotency reference.
- Mland booking and capacity state remains consistent.
- Klook cancellation events do not automatically release Mland capacity in V1.

#### Normal Flow

**Synchronize Workshop Booking**

1. The system detects a confirmed Mland booking or an approved Klook confirmation callback.
2. The system verifies that the booking was confirmed through the required Mland availability/hold flow.
3. The system builds the enabled booking synchronization payload.
4. The system sends or records the booking update with Klook.
5. The system validates the response, prevents duplicate processing, and records the synchronization result.
6. The system makes the synchronized booking available to authorized workshop operations.

#### Alternative Flows

**Step 2 — Booking has no valid Mland hold/confirmation**

The system rejects the synchronization and does not consume capacity or confirm the booking.

**Step 3 — Required booking data is missing**

The system records a validation failure and queues the booking for operational correction.

**Step 4 — Klook rejects or cannot receive the update**

The system records the failure and retries without creating a duplicate booking.

**Step 5 — Duplicate event**

The system returns the previously recorded result and does not create a second booking or capacity deduction.Klook cancellation receivedThe system does not consume the cancellation event or automatically release Mland capacity.

#### Business Rules

GBR-11, BR-28-01, BR-28-02

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| GBR-11 | Klook obtains Mland availability/hold before it confirms a workshop booking; Klook cancellation events do not release Mland capacity automatically. |
| BR-28-01 | Only confirmed bookings from an approved Mland availability/hold flow may be synchronized as workshop bookings. |
| BR-28-02 | Booking synchronization must be idempotent and must not create duplicate bookings or transactions. |
