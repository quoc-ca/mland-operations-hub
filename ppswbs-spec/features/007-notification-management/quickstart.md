# Quickstart & Verification Guide: FE-007 Notification Management

**Date created**: 2026-11-06
**Date updated**: 2026-11-06
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Contracts**: [contracts/](contracts/)

## 1. Overview & Setup

This guide defines end-to-end and integration verification procedures for Feature FE-007 (`notificationmanagement`). All scenarios test observable behavior using test fixtures, controlled database transactions, and a mock/fake SMTP server (e.g. GreenMail in test profile or MailHog container).

### Prerequisites
1. Local database initialized through Flyway migrations up to `V93__notification_management.sql`.
2. Mock SMTP service running on `localhost:3025` (e.g., GreenMail or MailHog).
3. Backend running with profile `test` or `local` (`mail.host=localhost`, `mail.port=3025`).

---

## 2. Verification Scenarios

### Scenario 1: Rollback Safety (SC-001 / FR-003)
**Objective**: Verify that if a business transaction rolls back, no notification request is persisted.

**Steps**:
1. Execute a mock `workshopbooking` confirmation test that calls `NotificationFacade.submitNotificationIntent(...)`.
2. Trigger an unhandled business exception immediately after the facade call before transaction commit (e.g. simulated payment gateway discrepancy).
3. Assert the transaction rolls back.

**Expected Outcome**:
- `SELECT COUNT(*) FROM notification_request WHERE business_event_reference = 'BK-TEST-ROLLBACK'` returns `0`.
- Mock SMTP server receives 0 emails.

---

### Scenario 2: Idempotency & Repeated Event (SC-002 / FR-006)
**Objective**: Verify that repeating the same business event callback creates exactly one original notification request.

**Steps**:
1. Execute two `NotificationFacade.submitNotificationIntent(...)` calls concurrently with `businessEventReference = "BK-IDEM-001"` and otherwise identical arguments; commit both transactions.
2. Check the return status of both invocations after they complete.

**Expected Outcome**:
- Exactly one call returns `Status.ENQUEUED`.
- The other call returns `Status.DUPLICATE_IGNORED` with the winning `notificationRequestId`.
- `SELECT COUNT(*) FROM notification_request WHERE business_event_reference = 'BK-IDEM-001' AND original_request_id IS NULL` returns exactly `1`.

---

### Scenario 3: Temporary SMTP Failure and Retry Schedule (SC-003 / FR-013 / FR-028)
**Objective**: Verify that a temporary SMTP failure retries at 1 minute and 5 minutes, capping at 3 attempts.

**Steps**:
1. Stop the mock SMTP server (simulate connection refusal).
2. Enqueue a valid notification request (`businessEventReference = "BK-RETRY-001"`).
3. Trigger the delivery worker poll (first run):
   - Worker attempts delivery, catches `MailException`, updates state to `RETRY_SCHEDULED`, sets `attempt_count = 1`, and `next_attempt_at = now() + 1 minute`.
4. Advance system clock by 30 seconds:
   - Worker poll runs, ignores request because `next_attempt_at` has not arrived.
5. Advance system clock to `+1 minute`:
   - Worker poll runs, executes attempt 2, catches failure, sets `attempt_count = 2`, `next_attempt_at = now() + 5 minutes`.
6. Restart mock SMTP server. Advance system clock to `+6 minutes`:
   - Worker poll runs, executes attempt 3, successfully delivers to mock SMTP.
   - State becomes `SENT`, `terminal_at` is recorded.

**Expected Outcome**:
- Exactly 3 delivery attempt records in `notification_delivery_attempt`.
- Final state in `notification_request` is `SENT`.
- Email message received in mock SMTP inbox.

---

### Scenario 4: Unknown Outcome and Restart Recovery (SC-004 / FR-014)
**Objective**: Verify that an interrupted delivery where SMTP invocation may have begun transitions to `UNKNOWN_OUTCOME` on restart without automatic redelivery.

**Steps**:
1. Enqueue a valid notification request.
2. Simulate process termination or thread interruption after `smtp_invocation_started_at` is written to DB but before SMTP acknowledgment is committed.
3. Trigger application startup / worker recovery scan.

**Expected Outcome**:
- Request state is updated to `UNKNOWN_OUTCOME`.
- Worker poll does NOT attempt automatic redelivery.
- A Manager cannot resend this request via API (returns 403 Forbidden).
- An Admin can resend only with `duplicateRiskAccepted: true`, which creates a new linked request and writes an audit record.

---

### Scenario 5: Template Version Pinning & Immutability (SC-005 / FR-007 / FR-008)
**Objective**: Verify that editing or activating a new template version does not alter previously created requests.

**Steps**:
1. Template `BOOKING_CONFIRMED` has active Version 1.
2. Enqueue Request #1 (pins Version 1).
3. Admin edits template, creating draft Version 2, then activates Version 2.
4. Enqueue Request #2 (pins Version 2).
5. Trigger worker delivery for Request #1 and Request #2.

**Expected Outcome**:
- Request #1 rendition uses Version 1 presentation.
- Request #2 rendition uses Version 2 presentation.
- Version 1 status is `DEACTIVATED`, but still retrievable for Request #1 audit and resend.

---

### Scenario 6: Preview & Diagnostic Data Sanitization (SC-006 / FR-021 / FR-022)
**Objective**: Verify that dashboard preview and diagnostics redact sensitive data and raw tokens.

**Steps**:
1. Call `POST /api/v1/notification-templates/{code}/preview` with sample payload containing mock tokens or PII.
2. Query `GET /api/v1/notification-requests/{id}`.

**Expected Outcome**:
- Preview response never exposes production customer data or raw secrets.
- Request diagnostic response shows `recipientAddressMasked` (e.g. `n***a@gmail.com`).
- No raw passwords, card data, or raw QR payload appears in logs or HTTP responses.

---

### Scenario 7: Repeated Resend and Refreshed-Credential Redelivery (FR-015 / FR-018 / FR-019)
**Objective**: Verify that eligible manual operations can create multiple derived requests for one original without altering original history or reusing an expired credential.

**Steps**:
1. Seed root request `#100` in `SENT` state with a valid credential and rendition.
2. Call `POST /api/v1/notification-requests/100/resend` twice after each preceding derived request reaches a terminal state.
3. Expire the root credential. Call `POST /api/v1/notification-requests/{derivedId}/redeliver`; configure the owning source feature to authorize the action and return a replacement credential reference.

**Expected Outcome**:
- Both resend actions create distinct `PENDING` requests whose `original_request_id` is `100`.
- The redelivery creates another `PENDING` request whose `original_request_id` is `100`, even though the selected request was derived.
- The redelivery uses the replacement credential reference and does not copy the expired credential.
- The root request and all earlier delivery/audit records remain unchanged.

---

### Scenario 8: Retention Scrub & Tombstone Creation (SC-007 / FR-025 / FR-026)
**Objective**: Verify that requests older than 90 days are scrubbed of PII and replaced with tombstones.

**Steps**:
1. Seed a completed request with `terminal_at = now() - 91 days`.
2. Execute the retention sweep job (`RetentionService.sweepExpiredRequests()`).

**Expected Outcome**:
- `notification_rendition` and `recipient_snapshot` for this request are deleted/scrubbed.
- A row is inserted in `notification_tombstone` preserving original request ID, business event ref, template code, terminal state, and timestamps.

