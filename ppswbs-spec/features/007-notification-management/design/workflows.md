# Workflow Design: FE-007 Notification Management

**Date created**: 2026-11-06
**Date updated**: 2026-11-06
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Architecture**: [architecture.md](architecture.md)

---

## 1. Workflow Index

| ID | Workflow | Trigger and Actor | Source Requirements | Architecture Link | Status |
|---|---|---|---|---|---|
| WF-01 | In-Transaction Notification Enqueue | Booking/Retail Commit (System) | FR-003, FR-004, FR-006, FR-007 | architecture.md §5 Flow 1 | Approved |
| WF-02 | Scheduled Worker Polling and SMTP Delivery | 30-Second Timer (Worker) | FR-011, FR-012, FR-013, FR-028 | architecture.md §5 Flow 2 | Approved |
| WF-03 | SMTP Temporary Failure & Retry Scheduling | SMTP MailException (Worker) | FR-013, FR-028 | architecture.md §5 Flow 2 | Approved |
| WF-04 | Unknown Outcome Detection and System Recovery | Application Boot / Crash (System) | FR-014 | architecture.md §3 RecoveryService | Approved |
| WF-05 | Template Draft Creation and Modification | Admin / Manager in Dashboard | FR-008, FR-009, FR-027 | contracts/notification-api.md §1.3 | Approved |
| WF-06 | Template Version Activation | Admin / Manager in Dashboard | FR-008, FR-009, FR-027 | contracts/notification-api.md §1.5 | Approved |
| WF-07 | Safe Template Preview | Admin / Manager in Dashboard | FR-021, FR-022 | contracts/notification-api.md §1.6 | Approved |
| WF-08 | Manual Operator Resend | Admin / Manager in Dashboard | FR-015, FR-016, FR-018 | architecture.md §5 Flow 3 | Approved |
| WF-09 | Operator Delivery Cancellation | Admin / Manager in Dashboard | FR-017 | contracts/notification-api.md §2.4 | Approved |
| WF-10 | Redelivery with Refreshed Credential | Admin / Manager in Dashboard | FR-019 | contracts/notification-api.md §2.5 | Approved |
| WF-11 | Data Retention Scrub & Tombstone Sweep | Daily Scheduled Job (System) | FR-025, FR-026 | architecture.md §3 RetentionService | Approved |

---

## 2. Detailed Workflows

### WF-01: In-Transaction Notification Enqueue
- **Trigger and Actor**: Source feature service (e.g. `BookingConfirmationService`) inside an active transaction.
- **Preconditions**: Valid business event has occurred; recipient email has been validated by source feature.
- **Related Data Flow**: [architecture.md §5 Flow 1](architecture.md#flow-1-in-transaction-request-submission).

1. Source feature calls `NotificationFacade.submitNotificationIntent(command)`.
2. `NotificationRequestService` computes SHA-256 fingerprint of `recipientEmail`.
3. Checks idempotency: queries `notification_request` by `(business_event_reference, template_code, 'CUSTOMER', fingerprint)` where `original_request_id IS NULL`.
   - If found: returns `NotificationIntentResult.builder().status(DUPLICATE_IGNORED).requestId(existing.id).build()`.
4. Queries `notification_template_definition` by `templateCode`.
   - If not found or `lifecycle_status != 'ACTIVE'`: saves `OperationalFailure` (`TEMPLATE_INACTIVE`), returns `OPERATIONAL_FAILURE`.
5. Queries `notification_template_version` where `status = 'ACTIVE'` for definition.
   - If no active version: saves `OperationalFailure` (`NO_ACTIVE_VERSION`), returns `OPERATIONAL_FAILURE`.
6. Validates all keys in `command.businessDataSnapshot` against `allowed_placeholders`.
   - If missing required placeholders: saves `OperationalFailure` (`REQUIRED_PLACEHOLDER_MISSING`), returns `OPERATIONAL_FAILURE`.
7. Instantiates and saves `NotificationRequest` with `delivery_state = 'PENDING'`, `attempt_count = 0`, pinning `template_version_id`.
8. Returns `NotificationIntentResult.builder().status(ENQUEUED).requestId(saved.id).build()`.
9. Caller transaction commits; notification request is now durable.

#### Failure, Recovery, and Integrity:
- If caller transaction rolls back, all DB inserts in this workflow roll back automatically.
- Operational failure records never throw runtime exceptions and never cause the caller's business transaction to roll back.

---

### WF-02: Scheduled Worker Polling and SMTP Delivery
- **Trigger and Actor**: Delivery worker triggered every 30 seconds via Spring `@Scheduled(fixedDelay = 30000)`.
- **Preconditions**: Single instance active; SMTP configuration present.
- **Related Data Flow**: [architecture.md §5 Flow 2](architecture.md#flow-2-30-second-delivery-worker-poll--smtp-delivery).

1. `DeliveryWorkerService.pollAndDeliver()` queries up to 20 requests ordered by `id ASC` where:
   `delivery_state IN ('PENDING', 'RETRY_SCHEDULED') AND (next_attempt_at IS NULL OR next_attempt_at <= now())`.
2. For each request sequentially:
   a. Sets `delivery_state = 'PROCESSING'`, `processing_started_at = now()`, `smtp_invocation_started_at = now()`.
   b. Flushes transaction so restart recovery can detect state.
   c. Checks if `Rendition` exists; if not, calls `RenditionService.getOrCreateRendition(request)` to substitute `{{variables}}` into subject, HTML, and text.
   d. Prepares `MimeMessage` using configured sender, reply-to, and recipient.
   e. Calls `JavaMailSender.send(mimeMessage)`.
   f. On 250 OK: saves `DeliveryAttempt` with `outcome_category = 'SUCCESS'`, `provider_message_id = messageId`.
   g. Updates request: `delivery_state = 'SENT'`, `terminal_at = now()`.

---

### WF-03: SMTP Temporary Failure & Retry Scheduling
- **Trigger and Actor**: `JavaMailSender.send()` throws `MailException` during WF-02.
- **Preconditions**: Request currently in `PROCESSING`.

1. Worker catches `MailException`.
2. Inspects exception:
   - If permanent rejection (e.g. 5xx syntax error, invalid mailbox from server):
     - Saves `DeliveryAttempt` with `outcome_category = 'REJECTED_PERMANENT'`.
     - Sets request `delivery_state = 'FAILED'`, `terminal_at = now()`.
   - If network / connection timeout / temporary 4xx:
     - Saves `DeliveryAttempt` with `outcome_category = 'NETWORK_TEMPORARY'`.
     - Increment `attempt_count`:
       - If `attempt_count < 3`:
         - If `attempt_count == 1`: sets `next_attempt_at = now() + 1 minute`.
         - If `attempt_count == 2`: sets `next_attempt_at = now() + 5 minutes`.
         - Sets request `delivery_state = 'RETRY_SCHEDULED'`.
       - If `attempt_count >= 3`:
         - Sets request `delivery_state = 'FAILED'`, `terminal_at = now()`.
3. Worker saves request and continues to next request.

---

### WF-04: Unknown Outcome Detection and System Recovery
- **Trigger and Actor**: Spring `ApplicationReadyEvent` on startup.
- **Preconditions**: System crashed or restarted during delivery.

1. `RecoveryService.recoverInterruptedRequests()` runs once at application startup.
2. Finds all requests with `delivery_state = 'PROCESSING'`.
3. For each request:
   - If `smtp_invocation_started_at` is NULL: sets `delivery_state = 'PENDING'`, logs safe recovery info.
   - If `smtp_invocation_started_at` is NOT NULL: sets `delivery_state = 'UNKNOWN_OUTCOME'`, saves `DeliveryAttempt` with `outcome_category = 'UNKNOWN'`.
4. Saves all updated requests.
5. Invariant: `UNKNOWN_OUTCOME` requests are NOT retried automatically by the worker.

---

### WF-05: Template Draft Creation and Modification
- **Trigger and Actor**: Admin or Manager on Dashboard clicks "Chỉnh sửa" or "Tạo bản nháp".
- **Preconditions**: Authenticated with `ADMIN` or `MANAGER` role.

1. Operator submits subject, HTML, and text via `POST /api/v1/notification-templates/{code}/versions`.
2. `TemplateManagementService` finds template definition.
3. Computes next `version_number = max(version_number) + 1`.
4. Validates that template placeholders match `allowed_placeholders`.
5. Inserts `notification_template_version` with `status = 'DRAFT'`.
6. Returns new version representation.

---

### WF-06: Template Version Activation
- **Trigger and Actor**: Admin or Manager clicks "Kích hoạt" on a draft version.
- **Preconditions**: Version is currently in `DRAFT` status.

1. Operator submits `POST /api/v1/notification-templates/{code}/versions/{versionNumber}/activate`.
2. In a single transaction:
   a. Finds currently `ACTIVE` version for this definition (if any) and sets `status = 'DEACTIVATED'`.
   b. Updates target version: `status = 'ACTIVE'`, `activated_at = now()`, `activated_by = currentActor.accountId`.
3. Commits transaction. New incoming requests will pin this activated version; existing requests remain pinned to their original versions.

---

### WF-07: Safe Template Preview
- **Trigger and Actor**: Admin or Manager clicks "Xem trước" on a template version.

1. Operator provides sample values for placeholders.
2. `RenditionService.renderSample()` substitutes variables into HTML and plain-text.
3. System verifies that no production secrets or real recipient emails are used.
4. Returns rendered HTML and subject for iframe preview in modal.

---

### WF-08: Manual Operator Resend
- **Trigger and Actor**: Admin or Manager clicks "Gửi lại" on a request.
- **Preconditions**:
  - Request state is `SENT` or `FAILED`.
  - If state is `UNKNOWN_OUTCOME`, actor must be `ADMIN` and provide `duplicateRiskAccepted = true`.
  - Non-empty `reason` required.

1. Operator submits `POST /api/v1/notification-requests/{id}/resend`.
2. `OperationalService.resendRequest()` verifies actor role and state eligibility.
3. If `UNKNOWN_OUTCOME` and actor is `MANAGER`, throws `403 Forbidden`.
4. Clones original request into a new `NotificationRequest`:
   - Same `business_event_reference`, `template_code`, `template_version_id`, `recipient_snapshot`, `business_data_snapshot`.
   - `original_request_id = originalRequest.id`.
   - `delivery_state = 'PENDING'`, `attempt_count = 0`.
5. Persists new request and writes `OperationalAuditRecord` (`action_type = 'RESEND'`).
6. Returns new request ID. Delivery worker will pick it up on next 30s cycle.

---

### WF-09: Operator Delivery Cancellation
- **Trigger and Actor**: Admin or Manager clicks "Hủy gửi" on a pending/scheduled request.
- **Preconditions**: Request state is `PENDING` or `RETRY_SCHEDULED`. Non-empty `reason` required.

1. Operator submits `POST /api/v1/notification-requests/{id}/cancel`.
2. `OperationalService.cancelRequest()` checks state:
   - If `PROCESSING`, rejects with `409 Conflict` (SMTP invocation may already be underway).
   - If `SENT` or `FAILED`, rejects with `400 Bad Request`.
3. Updates request: `delivery_state = 'CANCELLED'`, `terminal_at = now()`.
4. Persists `OperationalAuditRecord` (`action_type = 'CANCEL'`).
5. Returns updated request details.

---

### WF-10: Redelivery with Refreshed Credential
- **Trigger and Actor**: Admin or Manager clicks "Gửi lại với link/vé mới".
- **Preconditions**: Original request had a credential/ticket that has expired or been revoked.

1. Operator submits `POST /api/v1/notification-requests/{id}/redeliver`.
2. `OperationalService` calls source feature facade (e.g. `BookingFacade` / `RetailFacade`) to authorize replacement credential issuance.
3. If source feature denies or fails, returns `422 Unprocessable Entity`.
4. On success: creates linked `NotificationRequest` with fresh credential reference, preserving original business snapshot.
5. Persists `OperationalAuditRecord` (`action_type = 'REDELIVER_REFRESHED_CREDENTIAL'`).
6. New request will be rendered with the fresh credential on delivery attempt.

---

### WF-11: Data Retention Scrub & Tombstone Sweep
- **Trigger and Actor**: Daily scheduled job (`@Scheduled(cron = "0 0 2 * * ?")`).

1. `RetentionService.sweepExpiredData()` queries requests where `terminal_at <= now() - 90 days`.
2. For each request:
   a. Inserts row into `notification_tombstone`.
   b. Deletes corresponding `notification_rendition`.
   c. Clears `recipient_snapshot` and sensitive columns in `notification_request`.
3. Queries requests where `credential_expires_at <= now()` and `credential_reference IS NOT NULL`:
   - Sets `credential_reference = NULL`.
4. Commits cleanup batch.

