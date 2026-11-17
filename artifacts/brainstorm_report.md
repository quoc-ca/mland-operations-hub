# 003.2 T088 — Booking attendance finalization publication

## Decision

Approved on 2026-11-05: Operations will calculate the booking-wide attendance
outcome only after the 24-hour correction window closes, persist one durable
outbox record per booking, and publish the reference-only event asynchronously.

## Design

- Add a `BookingAttendanceOutcomeQuery` facade query returning only `terminal`
  and `ATTENDED`, `NO_SHOW`, or `PARTIAL`.
- Add a booking-scoped outbox with a unique business identity of
  `(booking_reference, event_type)`, where the event type is
  `BOOKING_ATTENDANCE_FINALIZED`.
- Use an Operations scheduler with a configurable 60-second default delay to
  locate booking-wide correction windows that have closed. It creates the
  outbox record transactionally; duplicate scans are no-ops through the unique
  key.
- Publish `BookingAttendanceFinalizedEvent(bookingReference, finalizedAt)`
  from pending outbox records only after their creating transaction commits.
  `finalizedAt` is the correction-window close instant: the latest required
  session finalization instant plus 24 hours.
- Marking an event published happens after publish. Recovery can redeliver an
  event after a crash, so consumers must deduplicate by booking reference. The
  outbox record itself is created exactly once; this avoids falsely claiming
  impossible exactly-once external delivery.

## Boundaries

- The facade query and event include no ticket, participant, contact, payment,
  or other PII.
- The change is Operations-owned. Settlement remains a consumer and decides
  any folio consequence separately.
- Existing cancellation outbox conventions are reused; no broker or external
  call occurs in the scheduler's creating transaction.

## Acceptance evidence

Tests must cover all-no-show, all-attended, partial attendance, a still-open
window, a correction before closure, duplicate scheduler/recovery behavior,
and reference-only event payload.
