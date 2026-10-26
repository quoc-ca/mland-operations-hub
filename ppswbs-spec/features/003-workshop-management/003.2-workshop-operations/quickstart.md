# Quickstart: Workshop Operations Verification

**Date created**: 2026-10-24
**Date updated**: 2026-10-27
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

This guide verifies the planned feature after implementation. It does not
authorize a production migration or a reset of existing workshop data.

## Prerequisites

- Java 21 and Maven available for `ppswbs_backend`.
- A non-production MySQL database configured for the Spring test/integration
  profile; Flyway must run against that database, not a shared or production
  database.
- Firebase test identity configuration and Account Management fixtures for an
  active scoped Manager, assigned active Staff, inactive Staff and an unscoped
  Manager.

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

1. Create the three default non-overlapping slots at a workshop location. Attempt an
   overlapping slot and verify it is rejected. Deactivate a slot and verify
   existing sessions remain intact while new generation cannot use it.
2. Create a future `DRAFT` session, then publish it. Run a bulk generation for
   at most 90 future days twice: the first creates sessions and the second
   reports duplicates rather than creating more. Verify `DRAFT` creates no
   OTA-observable snapshot. On publication, verify the mapped Basic Ring session
   exposes its schedule and availability/sale-state snapshots using one unified
   source-version sequence.
3. Configure capacity for two published sessions. Request N seats for both and
   verify holds exist for both sessions. Repeat with one session full or in
   `SALES_PAUSED` and verify no hold exists for either session. Create
   simultaneous holds whose total exceeds a session capacity and verify only
   capacity-safe requests succeed. Pause sales: a new hold is rejected, while
   a previously unexpired hold can still confirm.
   Verify the availability/sale-state snapshot exposed to 003.4 subtracts both
   confirmed ticket seats and active unexpired direct-Mland holds. Verify active
   hold creation, release and expiry produce an update only when sellable
   availability changes; verify hold-to-ticket commitment produces none when the
   exposed post-state is unchanged. Change a session schedule and verify the
   schedule snapshot shares the same per-session version sequence. Change only
   Staff assignment and verify it produces neither source-version advance nor
   OTA-observable snapshot.
4. After valid commercial payment evidence, commit a booking's complete active
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
5. Assign active Staff or record an operational lead. Verify transition to
   `IN_PROGRESS` and normal check-in fail with neither responsible operator.
   Verify inactive or unassigned Staff cannot check in.
6. Before `check_in_opens_at`, a valid QR/ticket-code check-in is rejected.
   Once open, scan a two-session ticket at session one successfully. Scan it
   concurrently or twice at that session and verify exactly one check-in is
   recorded. Verify the same QR can still check in once at session two, while a
   scan in a session/location without entitlement is rejected. Confirm a booking
   code never by itself checks a ticket in.
7. Perform an authorised override and a post-finalisation Manager correction.
   Verify both require a reason and create immutable audit events. Verify an
   override cannot move a ticket to another session or location.
8. Finalise the session after actual end time. Checked-in tickets remain
   checked-in and unchecked active tickets become `NO_SHOW`. Verify a regular
   Staff member cannot correct this result after finalisation.
   For a multi-session booking, verify Settlement receives readiness only after
   every required session is `ATTENDANCE_FINALIZED`, never from scheduled end
   time or attendance detail.
9. Request a Manager change of one or both future required sessions through the
   003.3 boundary. Verify every target is future, `PUBLISHED`, customer-
   selectable and has capacity for the full participant count; if one check
   fails, no session entitlement or capacity changes. On success, verify the
   same persistent QR gains the complete resulting session set, capacity moves
   atomically, and a replaced session has no remaining entitlement. Verify a
   `SALES_PAUSED`, started, finalized or cancellation-requested session is not
   accepted by this ordinary change flow.
10. Request cancellation on a session with commitments. Verify normal check-in
   stops, a 003.3 handoff exists, and no automatic refund, reschedule or ticket
   replacement occurs in this feature.
11. Pause/resume sales and cancel/close a session. Verify each resulting
    OTA-observable sale-state change is available to 003.4 with no customer,
    booking, payment or ticket-detail data. Verify `SALES_PAUSED` is a normal
    closed/zero-availability snapshot, not a mapping failure. Deliver duplicate
    and older updates and verify 003.4 keeps the newer source version; simulate
    a gap and verify it obtains one authoritative complete-session Operations
    resync at the existing source version. Simulate 003.4 sync failure and
    verify only its affected OTA mapping is suspended: Operations capacity,
    session sale state and direct-Mland booking behaviour remain unchanged.
    Verify the internal snapshot publication is committed with the successful
    Operations change, survives a consumer failure/restart for at-least-once
    handling, and does not make an external OTA call inside the Operations
    transaction.

## Release Safety

Before a production rollout, review the legacy booking/session/ticket inventory
and approve a mapping or customer reissue run. The new Flyway migration must be
append-only and must not use the old development reset migration. Record the
approved run, affected source references and any reissued ticket deliveries in
the audit trail.
