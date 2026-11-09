---
id: UTC-007-023
feature: 007-notification-management
test_level: unit
test_case_type: N
source_ref: spec.md#FR-025
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.RetentionService
source_method: sweepExpiredData
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.RetentionServiceTest
test_method: shouldScrubOldRequests
---

# UTC-007-023: Retention sweep scrubs requests older than 90 days and persists tombstones

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior QA Engineer

## Test design

- Function code/name: `RetentionService#sweepExpiredData` — Data retention & privacy scrub
- Test requirement: FR-025, FR-026, SC-007, WF-11 — Requests reaching terminal state older than 90 days have recipient snapshot and rendition scrubbed/deleted, replaced with minimal retained Tombstone records.
- Case type: N

### Preconditions

- Mock `NotificationRequestRepository` returns a completed request with `terminalAt = now() - 92 days`.
- Mock `RenditionRepository` returns associated rendition (ID = 10L).
- Mock `TombstoneRepository#findByOriginalRequestId(99L)` returns empty.

### Test data and actions

- Action: Invoke `retentionService.sweepExpiredData(90)`.

### Expected assertions

- Scrub count returned is 1.
- Original request `recipientSnapshot` and `businessDataSnapshot` become `"[SCRUBBED]"`.
- Verify `renditionRepository.delete(rendition)` is called.
- Verify `tombstoneRepository.save(any(Tombstone.class))` persists minimal audit tombstone for request 99L.

### Exception and log expectation

- None — Scheduled retention sweep executes and commits batch quietly.

## Execution

<!-- EXECUTION:START -->
- Latest status: NOT_RUN
- Executed at: NOT_RUN
- Executed by: NOT_RUN
- Command: NOT_RUN
- Surefire evidence: NOT_RUN
- Actual result: NOT_RUN
- Runs: 0
- PASS: 0
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->

