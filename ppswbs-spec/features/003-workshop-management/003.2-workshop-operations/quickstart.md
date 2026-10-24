# Quickstart: Workshop Operations Verification

**Date created**: 2026-10-24
**Date updated**: 2026-10-24
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

This guide verifies the planned feature after implementation. It does not
authorize a production migration or a reset of existing workshop data.

## Prerequisites

- Java 21 and Maven available for `ppswbs_backend`.
- A non-production MySQL database configured for the Spring test/integration
  profile; Flyway must run against that database, not a shared or production
  database.
- Firebase test identity configuration and account/personnel fixtures for an
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

1. Create the three default non-overlapping slots at a branch. Attempt an
   overlapping slot and verify it is rejected. Deactivate a slot and verify
   existing sessions remain intact while new generation cannot use it.
2. Create a future `DRAFT` session, then publish it. Run a bulk generation for
   at most 90 future days twice: the first creates sessions and the second
   reports duplicates rather than creating more.
3. Configure a capacity, create simultaneous holds whose total exceeds that
   capacity, and confirm only the capacity-safe requests succeed. Pause sales:
   a new hold is rejected, while a previously unexpired hold can still confirm.
4. Confirm a commercial booking through the Operations facade and verify it
   creates one distinct attendance ticket per participant. Verify Booking can
   obtain a delivery-safe view but cannot change ticket attendance.
5. Assign active Staff or record an operational lead. Verify transition to
   `IN_PROGRESS` and normal check-in fail with neither responsible operator.
   Verify inactive or unassigned Staff cannot check in.
6. Before `check_in_opens_at`, a valid QR/ticket-code check-in is rejected.
   Once open, scan the QR successfully. Scan it concurrently or twice and
   verify exactly one check-in is recorded. Confirm a booking code never by
   itself checks a ticket in.
7. Perform an authorised override and a post-finalisation Manager correction.
   Verify both require a reason and create immutable audit events. Verify an
   override cannot move a ticket to another session or branch.
8. Finalise the session after actual end time. Checked-in tickets remain
   checked-in and unchecked active tickets become `NO_SHOW`. Verify a regular
   Staff member cannot correct this result after finalisation.
9. Request cancellation on a session with commitments. Verify normal check-in
   stops, a 003.3 handoff exists, and no automatic refund, reschedule or ticket
   replacement occurs in this feature.

## Release Safety

Before a production rollout, review the legacy booking/session/ticket inventory
and approve a mapping or customer reissue run. The new Flyway migration must be
append-only and must not use the old development reset migration. Record the
approved run, affected source references and any reissued ticket deliveries in
the audit trail.
