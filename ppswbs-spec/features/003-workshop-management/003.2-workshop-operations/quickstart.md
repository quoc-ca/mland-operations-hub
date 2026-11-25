# Quickstart: Workshop Operations Verification

**Date created**: 2026-10-24
**Date updated**: 2026-11-17
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

This guide verifies the planned feature after implementation. It does not
authorize a production migration or a reset of existing workshop data.

## Re-based authorization and scheduled start

- Use an active Staff fixture with `activeLocationIds` containing the session
  location **and an active assignment to the session** (an unassigned Staff
  gets 403; 2026-11-06 — "no session assignment is required" is superseded).
- Use an active Manager fixture and verify it can operate sessions in any
  location without an operational-lead record.
- Set a future `checkInOpensAt`; at that instant verify check-in becomes
  available while the session remains `PUBLISHED` or `SALES_PAUSED` *(extended 2026-11-15, Q2, T114; was "`PUBLISHED`")*. At the session's scheduled
  start time verify the scheduler moves it to `IN_PROGRESS` and records a
  system audit event. A repeated scheduler run must not create a second transition or audit row.

## Scheduler repeat checks

- Repeat scheduler execution/restart and verify no duplicate transition or
  duplicate audit side effect occurs.
- Verify Staff outside the session location is denied and Staff cannot perform
  attendance correction; an active Manager can perform correction globally.

## Prerequisites

- Java 21 and Maven available for `ppswbs_backend`.
- A non-production MySQL database configured for the Spring test/integration
  profile; Flyway must run against that database, not a shared or production
  database.
- Firebase test identity configuration and Account Management fixtures for an
  active global Manager, active Staff in and outside a session location, and
  inactive actors.

## Automated Checks

From the repository root:

```powershell
Set-Location ppswbs_backend
mvn test
Set-Location ..
git diff --check
```

The implementation test suite must include MySQL/Flyway coverage; an H2-only
run is not sufficient proof of index, lock or migration behaviour.

## Acceptance Walkthrough

1. With an active canonical `MANAGER` whose `activeLocationIds` cover only one
   location, create a `DRAFT` Workshop Package for another active location with
   its public name, description, per-participant price, inclusive participant
   bounds, required-session count, same-location rule, same-day slot-gap and
   foundry-working-days-between-sessions rules. Verify another active Manager with different location assignments can
   also perform this action. Verify `packageId` is
   system-generated and no package code is required.
2. Publish the valid Package through its explicit lifecycle command. Verify an
   unauthenticated public Package read returns it only after publication;
   `DRAFT` and `RETIRED` records are not disclosed to public callers. Edit an
   already published Package and verify the change is prospective: a prior
   Booking retains its frozen Package and financial snapshots. Retire the
   Package and verify it cannot be newly selected or deleted while its Booking
   history remains readable. Verify the catalogue offers in
   [catalogue.md](./catalogue.md) work as generic rules rather than as a closed
   Package enum. The former Silverclay same-day-gap example is retired. Verify
   Wax Casting with at least two intervening foundry-working dates: Monday
   16:00–19:00 to Thursday 09:30–12:00 is valid; Monday to Wednesday and
   Friday to Tuesday are invalid. Saturday/Sunday do not count, so Friday to
   Wednesday is valid (Monday and Tuesday are the two intervening dates).
3. Verify that the approved Package-discovery facade is the only 003.1
   cross-module read boundary and that Booking cannot read or mutate Package
   entities/repositories. Call
   `GET /api/v1/workshop-packages/{packageId}/sessions` with a required
   `locationId` and optional date filters. Verify it returns only future,
   `PUBLISHED`, customer-selectable sessions for that Package and location,
   using opaque UUID session references plus schedule, sale state and exact
   non-guaranteed remaining capacity. Verify the Manager/Staff-scoped
   `GET /api/v1/workshop-sessions` remains protected and is not a public
   discovery fallback.
4. Create the three default non-overlapping slots at a workshop location. Attempt an
   overlapping slot and verify it is rejected; an overlap with an inactive slot is allowed, and re-activating it then is rejected. Deactivate a slot and verify
   existing sessions remain intact while new generation cannot use it.
5. Create a future `DRAFT` session, then publish it. Run a bulk generation for
   at most 90 future days twice: the first creates sessions and the second
   reports duplicates rather than creating more.
6. Configure capacity for two published sessions. Request N seats for both and
   verify holds exist for both sessions. Repeat with one session full or in
   `SALES_PAUSED` and verify no hold exists for either session. Create
   simultaneous holds whose total exceeds a session capacity and verify only
   capacity-safe requests succeed. Pause sales: a new hold is rejected, while
   a previously unexpired hold can still confirm.
7. After valid commercial payment evidence, commit a booking's complete active
   hold set and verify that an absent or expired hold commits none of the set.
   Send a repeated or late release and verify capacity is returned no more than
   once. Confirm a two-session commercial booking through the Operations facade
   and verify it creates one persistent QR ticket per participant with an
   entitlement to both sessions. Verify the capacity boundary returns the
   reserve/commit outcome synchronously, the separate ticket-projection boundary
   returns only the delivery-safe projection after commit, and Booking cannot
   change attendance. Inject a local persistence failure in either module and
   verify neither side commits a partial booking-capacity outcome.
   For every state-changing facade command, repeat an equivalent command with
   the same `facadeAction` and `operationReference` and verify the original typed
   outcome returns without a second hold, ticket, attendance or audit side
   effect. Reuse the same identity with different semantic content and verify a
   safe rejection. Submit concurrent equivalents: one may own the claim and the
   other must receive `IN_PROGRESS`, never a second business execution. Simulate
   an expired in-progress lease and reclaim with the same fingerprint; verify a
   stale original claimant cannot complete or commit after claim version changes.
   Verify completed ledger records remain retryable for at least 24 hours and
   cleanup affects only eligible completed records; verify the ledger contains no
   raw request payload, Firebase token, PII, QR value or audit JSON.
8. Set `checkInOpensAt` on a `PUBLISHED` session and verify check-in opens while
   the session remains `PUBLISHED`; repeat with a `SALES_PAUSED` session
   (spec Clarifications 2026-11-15 Q2: check-in and scheduled start still apply); at scheduled start verify the scheduler
   transitions it to `IN_PROGRESS` idempotently. Verify an
   active Staff inside the session location **and actively assigned** can
   operate (an unassigned Staff gets 403; 2026-11-06), Staff outside the
   location is denied, and an active Manager can operate globally. For a
   `SALES_PAUSED` session verify the auto-start writes a system audit event,
   publishes the complete closed snapshot and advances the version exactly once *(F04 reopened 2026-11-16, option B)*, and unexpired holds still complete payment
   (Q2); verify a start later than 5 minutes is logged at ERROR level, a metric increases and no email is sent (F14), and it is still
   caught up (Q7); verify a session with no Staff is flagged from
   `checkInOpensAt` and revoking its last assignment needs explicit Manager
   confirmation (Q3). Verify an inactive actor is denied.
9. Before `check_in_opens_at`, a valid QR/ticket-code check-in is rejected.
   Once open, scan a two-session ticket at session one successfully. Scan it
   concurrently or twice at that session and verify exactly one check-in is
   recorded. Verify the same QR can still check in once at session two, while a
   scan in a session/location without entitlement is rejected. Confirm a booking
   code never by itself checks a ticket in.
10. Perform an authorised override and a post-finalisation Manager correction.
   Verify both require a reason and create immutable audit events. Verify an
   override cannot move a ticket to another session or location.
11. Finalise the session after actual end time. Checked-in tickets remain
   checked-in and unchecked active tickets become `NO_SHOW`. Verify a regular
   Staff member cannot correct this result after finalisation.
   *(Superseded 2026-11-15 by FR-032/T097: the booking-wide readiness check below is removed.)*
   For each ticket, verify `getTicketAttendanceOutcome` is terminal only after
   every session of that ticket is `ATTENDANCE_FINALIZED` and the ticket's own
   24-hour correction window has closed, never from scheduled end time or
   attendance detail. *(Original: "For a multi-session booking, verify Settlement
   receives readiness only after every required session is `ATTENDANCE_FINALIZED`".)*
12. *(Superseded 2026-11-15 by FR-034/T094/T097: the change is per ticket
   (`replaceTicketSessions`), not a complete booking-wide required-session set.)*
   Request a Manager change of one or both future required sessions through the
   003.3 boundary. Verify every target is future, `PUBLISHED`, customer-
   selectable and has one seat per selected ticket (FR-034); if one check
   fails, no session entitlement or capacity changes. On success, verify the
   same persistent QR gains the complete resulting session set, capacity moves
   atomically, and a replaced session has no remaining entitlement. Verify a
   `SALES_PAUSED`, started, finalized or cancellation-requested session is not
   accepted by this ordinary change flow.
12a. Error and access checks (spec "Error catalogue", Checklist follow-up
   2026-11-15; tasks T126–T131, T135):
   - Send a normal check-in before `checkInOpensAt` and verify `409
     CHECK_IN_NOT_OPEN` with `checkInOpensAt` in the body (there is no
     early-check-in override; a Manager sets an earlier `checkInOpensAt`); a session not open for check-in answers `409
     INVALID_STATE`.
   - Request a ticket resend four times for one booking within 15 minutes and
     verify the fourth answers `429 REDELIVERY_THROTTLED` with a `Retry-After`
     header in whole seconds, sends nothing and is audited as a denied attempt.
   - Request a resend for a booking with no `ACTIVE` unused ticket and verify `409
     INVALID_STATE` with `reason` `NO_ELIGIBLE_TICKET`; make Booking refuse the
     redelivery and verify `409 INVALID_STATE` with `reason`
     `REDELIVERY_REJECTED_BY_BOOKING` (task T139; names are final, owner 2026-11-16). Neither is 400
     or 404.
   - Make the Account facade fail (stub an exception) and verify `503
     DEPENDENCY_UNAVAILABLE` with no side effect and no audit row; a missing or
     expired bearer token is `401`, never a missing account.
   - Call a 003.2 business route as an Admin (location create or edit, session
     list, publish, finalize, `GET /workshop-sessions/{id}`) and verify `403`;
     verify a Manager creates (`POST`) and edits (`PATCH
     /workshop-locations/{locationId}`) a location and that a Staff caller gets
     `403`.
   - Assign a Staff session with each ineligible account (missing, not `STAFF`,
     not `ACTIVE`, location out of scope) and verify `409 STAFF_NOT_ELIGIBLE`
     with `reason` `ACCOUNT_NOT_FOUND`, `NOT_STAFF`, `NOT_ACTIVE` or
     `LOCATION_NOT_IN_SCOPE`; assign outside `DRAFT`/`PUBLISHED`/`SALES_PAUSED`/
     `IN_PROGRESS` and verify `409 INVALID_STATE`; repeat an assignment and verify
     a `200` no-op.
13. Request cancellation on a session with commitments. Verify normal check-in
   stops, a 003.3 handoff exists, and no automatic refund, reschedule or ticket
   replacement occurs in this feature.
14. Pause/resume sales and cancel/close a session. Verify
    no customer, booking, payment or ticket-detail data is exposed.

## Package availability per location (proposed 2026-11-17)

Validation scenarios for spec FR-037–FR-045 (contract: `PUT
/workshop-packages/{packageId}/locations/{locationId}/availability`, `GET
/workshop-package-availability`; data: data-model "Package availability per
location"). Prerequisites: an active Manager, a Staff member and a Member
account; the seeded matrix (Hoàn Kiếm all five Packages, Tây Hồ Signature Brass,
Signature Silver 925 and Wax Casting, Đà Nẵng none).

1. Manager reads the matrix: 5 Packages × 3 locations, 8 cells `ACTIVE`; Staff and
   Admin get `403` on read and write.
2. Silver Clay 5g discovery at Tây Hồ returns `409
   PACKAGE_NOT_AVAILABLE_AT_LOCATION`; at Hoàn Kiếm it returns sessions; the
   Package's locations list excludes Tây Hồ.
3. Manager sets Silver Clay 5g `ACTIVE` at Tây Hồ twice: the first call writes one
   audit event, the second changes nothing and writes none.
4. Manager sets Wax Casting `INACTIVE` at Hoàn Kiếm while a booking exists: the
   call succeeds, the booking, tickets and an unexpired hold are untouched, a new
   reserve there is refused.
5. A ticket change moves a one-session ticket from Hoàn Kiếm to Tây Hồ (active
   there) as Manager; a Staff caller is refused; a Wax Casting change to Tây Hồ
   moves both sessions or none; a target at an `INACTIVE` pair is never offered and
   is refused at commit.
6. Concurrent deactivation and reserve give either a committed reserve or a
   refusal, never a partial reserve.

## Release Safety

Before a production rollout, review the legacy booking/session/ticket inventory
and approve a mapping or customer reissue run. The new Flyway migration must be
append-only and must not use the old development reset migration. Record the
approved run, affected source references and any reissued ticket deliveries in
the audit trail.
