---
id: UTC-007-001
feature: 007-notification-management
test_level: unit
test_case_type: N
source_ref: spec.md#FR-003
source_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.NotificationRequestService
source_method: submitNotificationIntent
test_class: edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationRequestServiceTest
test_method: shouldEnqueueValidIntent
---

# UTC-007-001: Enqueue valid booking notification intent into pending state

**Date created**: 2026-11-09
**Date updated**: 2026-11-09
**Author**: Senior QA Engineer
**Last updated by**: Senior Java/Spring Backend Implementation Engineer

## Test design

- Function code/name: `NotificationRequestService#submitNotificationIntent` — In-transaction notification enqueue
- Test requirement: FR-003, FR-004, FR-007, SC-001 — A valid notification intent submitted with an active template definition pins version and saves a new request in PENDING state.
- Case type: N

### Preconditions

- Mock `TemplateDefinitionRepository` returns an ACTIVE `BOOKING_CONFIRMED` template definition.
- Mock `TemplateVersionRepository` returns an ACTIVE `TemplateVersion` (versionNumber = 1).
- Mock `RenditionService#extractPlaceholders` returns required placeholders: `bookingCode`, `customerName`.
- Mock `NotificationRequestRepository` reports no existing request for this business event and recipient fingerprint.

### Test data and actions

- Action: Invoke `service.submitNotificationIntent(cmd)` with:
  - `businessEventReference = "BK-001"`
  - `templateCode = "BOOKING_CONFIRMED"`
  - `recipientEmail = "customer@example.com"`
  - `recipientName = "Customer A"`
  - `businessDataSnapshot = Map.of("bookingCode", "BK-001", "customerName", "Customer A")`

### Expected assertions

- Return result status is `NotificationIntentResult.Status.ENQUEUED`.
- Captured `NotificationRequest` has:
  - `businessEventReference` = "BK-001"
  - `deliveryState` = "PENDING"
  - `attemptCount` = 0
  - `recipientAddressMasked` = "c***r@example.com"
  - `originalRequest` is null

### Exception and log expectation

- None — Method executes cleanly within caller transaction.

## Execution

<!-- EXECUTION:START -->
- Latest status: PASS
- Executed at: 2026-11-09T09:43:36Z
- Executed by: Senior Java/Spring Backend Implementation Engineer
- Command: `mvn -q -Dtest=NotificationRequestServiceTest#shouldEnqueueValidIntent test`
- Surefire evidence: `target/surefire-reports/TEST-edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.NotificationRequestServiceTest.xml` testcase `shouldEnqueueValidIntent`
- Actual result: PASS
- Runs: 1
- PASS: 1
- FAIL: 0
- ERROR: 0
- SKIPPED: 0
<!-- EXECUTION:END -->

