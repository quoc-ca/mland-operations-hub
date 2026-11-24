# Feature Specification: FE-007 Notification Management

**Date created**: 2026-11-06
**Date updated**: 2026-11-24
**Last updated by**: Phan Sơn
**Status**: Historical V1 baseline plus R-INV revision pending final Tech Lead review; R-INV is not authorized for implementation.
**Input**: Consolidated technical decisions and architecture baseline for transactional email management.

## User Scenarios & Testing

### User Story 1 - Deliver a confirmed business email (Priority: P1)

When a Booking or Retail Order is validly confirmed, the owning business feature creates one durable notification request in the same transaction. The customer receives the relevant confirmation email after the business result commits, without SMTP delivery changing the accepted business result.

**Why this priority**: Confirmation is the minimum customer value of FE-007 and protects a committed booking/order from email-provider failures.

**Independent Test**: Confirm a valid booking or retail order using a controlled mail service and verify one request is created, then reaches a final delivery outcome without changing the business result.

**Acceptance Scenarios**:

1. **Given** a valid booking confirmation and an active `BOOKING_CONFIRMED` template, **When** the booking commits, **Then** one notification request with the approved recipient and frozen business snapshot is available for delivery after commit.
2. **Given** a business transaction that rolls back, **When** the notification is prepared, **Then** no request is available for delivery.
3. **Given** a repeated callback for the same committed business event, **When** it is processed again, **Then** no second original notification request is created.
4. **Given** a confirmed booking/order and an SMTP delivery failure, **When** the delivery worker records the failure, **Then** the accepted business result remains unchanged.

---

### User Story 2 - Manage global transactional templates (Priority: P1)

An Admin or Manager manages the presentation and lifecycle of globally shared, pre-registered transactional templates without creating a new workflow, recipient rule, or template code.

### Route masking synchronization (2026-11-20)

The current browser routes `/admin/notifications/*` have the explicit shared allowlist `ADMIN` and `MANAGER`, matching this feature's approved operational capability. Therefore the `/admin` prefix is not a general authorization rule. `MEMBER`, `STAFF`, and `CARRIER` direct navigation is replaced to `/404` before page requests; an eventual neutral route rename requires a separate compatibility decision. Server-side Admin/Manager distinctions, including Admin-only `UNKNOWN_OUTCOME` resend, remain enforced per action.

**Why this priority**: The dashboard must control the content customers see while business features remain the authority for the event and data.

**Independent Test**: Create a draft revision, validate/preview it with safe fixture data, activate it, and verify a newly created request uses the activated version while an earlier request remains pinned to its original version.

**Acceptance Scenarios**:

1. **Given** an active template version, **When** an authorized Admin or Manager edits its content, **Then** the system creates a new draft version and leaves the active version unchanged.
2. **Given** a validated draft version, **When** it is activated, **Then** it becomes the sole active version for new requests and the prior version is deactivated for new requests.
3. **Given** a version referenced by a request, **When** an authorized user deactivates another version, **Then** the referenced version remains available for the existing request's delivery and audit history.
4. **Given** dashboard preview, **When** an authorized user previews a template, **Then** the preview uses fixture or redacted data and never exposes a real recipient, credential, QR/ticket, or payment payload.

---

### User Story 3 - Operate a delivery safely (Priority: P2)

An authorized operator can inspect the minimal diagnostic state of an email delivery, resend eligible requests, or cancel a request that is still waiting, with an auditable reason and without re-running Booking or Retail business logic.

**Why this priority**: Customers need support when delivery fails, while operational actions must not create hidden duplicate or altered business outcomes.

**Independent Test**: Create controlled delivery outcomes and verify the permitted operator actions, audit trail, state changes, and denial of prohibited actions.

**Acceptance Scenarios**:

1. **Given** a sent request or a remediated failed request, **When** an authorized Admin or Manager supplies a reason for resend, **Then** a delivery linked to the original request is created using the original rendition and recipient.
2. **Given** a request in `PENDING` or `RETRY_SCHEDULED`, **When** an authorized Admin or Manager cancels it with a reason, **Then** its terminal state is `CANCELLED` and the worker does not send it.
3. **Given** a request in `PROCESSING`, **When** an operator attempts cancellation or resend, **Then** the request is not changed because SMTP may already be in progress.
4. **Given** an `UNKNOWN_OUTCOME`, **When** a Manager attempts to resend it, **Then** the action is denied; an Admin may proceed only after provider evidence is checked and the duplicate-delivery risk is explicitly accepted and audited.

---

### User Story 4 - Redeliver with a renewed credential (Priority: P2)

When an email's access credential or ticket artifact has expired or been revoked, an Admin or Manager can request a redelivery. The owning Booking or Retail feature authorizes the request and supplies a fresh credential/artifact; FE-007 sends it without modifying the original request or business snapshot.

**Why this priority**: Support can restore customer access without retaining, reusing, or exposing invalid credentials.

**Independent Test**: Expire or revoke a credential, initiate an authorized redelivery, and verify the original history stays unchanged while the new delivery uses only the replacement credential.

**Acceptance Scenarios**:

1. **Given** an expired or revoked credential, **When** a regular resend is requested, **Then** the request is not resent with the invalid credential.
2. **Given** an authorized refreshed-credential request, **When** the source feature supplies a valid replacement, **Then** FE-007 creates a linked redelivery using the original business rendition and the replacement credential.
3. **Given** a redelivery request, **When** the source feature denies authorization or cannot provide a valid credential, **Then** FE-007 sends nothing and records a safe operational outcome.

### Edge Cases

- A template is inactive, recipient data is invalid, or required placeholder data is absent at enqueue time: no deliverable request is created; the business result is not rolled back and a safe failure/audit record is retained.
- SMTP is temporarily unavailable before it accepts a message: delivery is retried after one minute and then five minutes, with no more than three SMTP attempts in total.
- Connectivity is lost after message transmission may have started, or the application stops after SMTP may have been called: state becomes `UNKNOWN_OUTCOME`; no automatic retry occurs.
- The application restarts while a request is `PROCESSING`: a request with no recorded SMTP invocation returns to `PENDING`; a request with a recorded invocation becomes `UNKNOWN_OUTCOME`.
- A raw credential, Magic URL, or QR payload expires or is revoked: it is scrubbed without waiting for normal request retention.

## Requirements

### Functional Requirements

- **FR-001**: The historical V1 scope supports `BOOKING_CONFIRMED` and `RETAIL_ORDER_CONFIRMED` as activatable SMTP templates. R-INV specifies `INTERNAL_ACCOUNT_INVITATION` as a separate type pending final approval; it adds no other account-change template.
- **FR-002**: The system MUST treat Firebase verification and password-reset email as Firebase-managed, read-only references; it MUST NOT send, retry, or report delivery for those emails.
- **FR-003**: A source feature MUST submit a structured notification intent in the same transaction as its accepted business result. The intent MUST identify the registered template, stable business event, safe business reference, approved recipient snapshot, frozen template-data snapshot, and any approved artifact reference.
- **FR-004**: FE-007 MUST own template version selection, notification requests, delivery state, rendition, delivery attempts, resend history, and operational audit. It MUST NOT re-read Booking/Retail data, choose a recipient, or decide that a business event is valid.
- **FR-005**: A source feature MUST NOT submit rendered subject, HTML, or plain-text email content as the notification intent.
- **FR-006**: The system MUST create no more than one original request for the same business event, template, recipient role, and recipient fingerprint, including when equivalent source intents arrive concurrently. This idempotency rule applies only to original requests; it MUST NOT prohibit eligible resend or refreshed-credential redelivery requests linked to that original request.
- **FR-007**: A request MUST pin its template version, recipient snapshot, and business-data snapshot at creation. Changes to the source business record or recipient afterward MUST NOT alter that request.
- **FR-008**: Editing a template MUST create a draft version. A version that is active or referenced by a request MUST remain immutable and MUST NOT be hard deleted.
- **FR-009**: A globally shared template MUST have no more than one active version. Admins and Managers may manage its content, version lifecycle, activation, and deactivation across the system.
- **FR-010**: The system MUST validate each notification intent against the registered template's allowed placeholders and required data before creating a deliverable request. It MUST record a safe operational failure when validation cannot produce a request.
- **FR-011**: The first delivery processing of a request MUST create and retain its rendered subject, HTML, and plain-text rendition. Automatic retry and ordinary resend MUST use that rendition rather than re-rendering from current source data or a newer template.
- **FR-012**: Delivery state MUST distinguish `PENDING`, `PROCESSING`, `RETRY_SCHEDULED`, `SENT`, `FAILED`, `UNKNOWN_OUTCOME`, and `CANCELLED`. `SENT` means the SMTP provider accepted the message, not that it entered an inbox or was read.
- **FR-013**: The V1 single worker MUST retry only when it can determine that SMTP did not accept the message. It MUST make at most three SMTP attempts, waiting one minute before the second and five minutes before the third.
- **FR-014**: The system MUST classify potential SMTP acceptance with lost confirmation, or a restart after SMTP invocation may have begun, as `UNKNOWN_OUTCOME` and MUST NOT retry it automatically.
- **FR-015**: Admins and Managers MAY resend eligible sent or remediated failed requests in their authorized operational scope, with a required reason and audit. Each eligible manual resend creates a linked derived request and repeated eligible resends are permitted. They MUST NOT resend a pending, processing, or scheduled-retry request.
- **FR-016**: Only an Admin MAY authorize a resend from `UNKNOWN_OUTCOME`, after provider evidence is reviewed and the risk of duplicate delivery is recorded.
- **FR-017**: Admins and Managers MAY cancel only a pending or scheduled-retry request with a reason. A processing request MUST NOT be cancelled through normal operations.
- **FR-018**: Ordinary resend MUST retain the original recipient, rendition, snapshot, and credential only while that credential remains valid. It MUST NOT change recipient, source data, template version, or credential.
- **FR-019**: For expired or revoked credentials, an Admin or Manager MAY request redelivery with a refreshed credential. The owning source feature MUST authorize the request and supply the replacement credential/artifact; FE-007 MUST neither create nor revoke credentials, MUST NOT copy the expired or revoked credential into the new delivery, and MUST preserve the original delivery history. Repeated eligible redeliveries are permitted.
- **FR-020**: `BOOKING_CONFIRMED` MUST use the Booking-approved contact email and may contain an approved ticket/QR artifact and booking access link. `RETAIL_ORDER_CONFIRMED` MUST use the Retail-approved order-notification email and may contain an authorized order-details link. The system MUST NOT infer either recipient from unrelated profile or shipping-address data.
- **FR-021**: The system MUST never store in logs, business audit, dashboard preview, or normal operational diagnostics a password, raw token, raw Magic URL, QR payload, payment payload, or unnecessary internal note.
- **FR-022**: Dashboard diagnostics MUST expose only the information required for support, including masked recipient, template, safe business reference, delivery state, attempt count, timestamps, safe error category, and correlation reference.
- **FR-023**: SMTP provider, sender identity, reply-to address, support contact, credentials, and rate limits MUST be environment configuration. Local and non-production environments MUST prevent uncontrolled delivery to real customers.
- **FR-024**: Missing or invalid SMTP configuration MUST cause a safe delivery failure without rolling back the business result or issuing futile automatic retries.
- **FR-025**: The system MUST scrub raw credentials as soon as they expire or are revoked, no later than the next cleanup sweep. It MUST retain recipient snapshot, rendition, and sensitive error details for 90 days after terminal state, then scrub them and retain only a minimized tombstone.
- **FR-026**: The system MUST retain redacted delivery metadata, attempt history, and Admin/Manager operational audit for 12 months, unless a binding business or legal policy requires a longer period.
- **FR-027**: In V1, Admin and Manager authorization for template management, resend, cancellation, and operational actions MUST apply at global scope. Workshop Location–scoped authorization is out of scope for V1.
- **FR-028**: The V1 delivery worker MUST poll for processable requests (PENDING or RETRY_SCHEDULED whose scheduled time has arrived) every 30 seconds. The retry wait times specified in FR-013 are measured from the failed attempt, not from the poll cycle.
- **FR-029**: For asynchronous post-commit event handoffs from business modules (such as Workshop Booking `BookingNotificationReady`), the delivery worker MUST perform at most 2 technical retry attempts (initial send + 1 retry after ~1 minute) for transient network/provider failures. When technical retries are exhausted without successful provider acceptance, the system MUST record the terminal state `FAILED` and publish the failure event `BookingNotificationDeliveryFailed` defined in `workshopbooking :: notification` to enable the owning business module to drive business-level retry policies (FR-015a/b) and operator escalation.

### R-INV invitation extension (pending final review, 2026-11-24)

`INTERNAL_ACCOUNT_INVITATION` is a separate, ADMIN-initiated transactional template. FE-001 supplies the intended recipient and a durable invitation/version reference through its outbox after the pending Account decision. FE-007 owns template version, queue, attempts and delivery outcome; it requests a transient setup URL from an authorized FE-001 materialization interface immediately before SMTP send. The intent, database rendition, general diagnostics and audit MUST NOT contain the raw URL, setup secret or Firebase custom token. A retry of the same invitation reuses its prepared credential; FE-001's explicit resend rotates it. FE-001 starts the 48-hour validity when the invitation version is created, even if SMTP fails. FE-007 attempts definite delivery at most twice, then records `FAILED`; `UNKNOWN_OUTCOME` requires provider/operator reconciliation before another send or rotation. Only after two definite failures may ADMIN retrieve/copy the current unexpired message for outside delivery. This does not prove delivery or change FE-007 state to `SENT`. Existing Booking/Retail snapshot and retry rules remain in force for their own templates. See [FE-001 R-INV](../001-account-management/design/revision-2026-11-24.md). This revision remains pending final approval.

### R-INV invitation requirements (2026-11-24)

These requirements extend FE-007 for FE-001's STAFF/MANAGER setup. They do not describe behavior already implemented by the historical two-template notification baseline. [FE-001 R-INV](../001-account-management/design/revision-2026-11-24.md) and [delivery boundary](contracts/notification-facade.md) §6 are normative for this extension.

- **FR-030**: FE-007 MUST register `INTERNAL_ACCOUNT_INVITATION`, consume the durable FE-001 invitation event idempotently after Account commit, pin its template version/approved recipient/safe snapshot and record a durable operational failure if validation fails. FE-001 has no Java dependency on FE-007 for this handoff.
- **FR-031**: For this template only, FE-007 MUST obtain an authorized setup URL from FE-001 for the current invitation ID/version immediately before SMTP send. The persisted rendition may contain only a static sentinel; the expanded URL and raw secret MUST never be persisted in notification requests, renditions, attempts, audits, previews, diagnostics or logs. Stale or unavailable materialization blocks sending.
- **FR-032**: FE-001 MUST start the 48-hour lifetime when it creates the invitation version. FE-007 MUST check that expiry before each send and make at most two definite SMTP attempts (initial plus one retry after approximately one minute); after two definite failures it MUST record `FAILED` and report that state to FE-001. Possible acceptance is `UNKNOWN_OUTCOME` and MUST NOT trigger automatic repeat or manual-content retrieval without provider/operator reconciliation.
- **FR-033**: Generic FE-007 resend/redeliver/cancel actions MUST reject `INTERNAL_ACCOUNT_INVITATION`. Only FE-001's ADMIN resend command, after its cooldown and reconciliation checks, may rotate the setup secret and emit a new delivery intent. Diagnostics show masked recipient, safe reference and delivery status only.
- **FR-034**: Once an invitation request reaches terminal `FAILED` after two definite attempts, FE-007 MUST allow an authenticated ADMIN to retrieve the pinned subject, HTML and plain-text email with the current unexpired FE-001 setup URL through a dedicated `no-store` API response. It MUST use FE-001's transient materialization facade, audit access without content or URL, and never persist the expanded message. An expired, rotated, used or `UNKNOWN_OUTCOME` invitation MUST be refused. Retrieval MUST NOT change `FAILED` to `SENT`, extend validity or assert outside delivery.

**R-INV acceptance scenarios:**

1. **Given** FE-001 commits an invitation, **when** FE-007 consumes the outbox event, **then** it stores one pinned request with FE-001's creation-based expiry and no raw URL.
2. **Given** two definite SMTP failures, **when** the second attempt ends, **then** FE-007 records `FAILED`; only ADMIN can retrieve the current unexpired pinned message transiently, while delivery state remains `FAILED`.
3. **Given** `UNKNOWN_OUTCOME`, a rotated/used version or an expired link, **when** manual content is requested, **then** FE-007 rejects the request without revealing a URL.
4. **Given** an ADMIN manually copies the message, **when** diagnostics, audit and database rows are inspected, **then** they contain no expanded URL or secret and no claim of outside delivery.

### Key Entities

- **Template Definition**: A registered transactional email type, its ownership, allowed placeholders, and lifecycle eligibility.
- **Template Version**: An immutable draft, active, or historical presentation revision for one template definition.
- **Notification Request**: The durable record of one requested business email, with frozen template, recipient, business-data, and safe artifact references. An original request is idempotent per FR-006; a resend or redelivery is a derived request linked to that original.
- **Rendition**: The subject, HTML, and plain-text content rendered for a request's first delivery processing.
- **Delivery Attempt**: One recorded SMTP attempt, including safe timing, outcome category, and provider evidence reference where available.
- **Operational Failure**: A safe record that explains why a deliverable request could not be created or completed without changing the source business result.
- **Operational Audit Record**: An actor, reason, state change, and correlation record for resend, cancellation, credential redelivery, or duplicate-risk acceptance.
- **Tombstone**: The minimized retained delivery record after sensitive request data has been scrubbed.

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% of verified rollback scenarios create no deliverable notification request.
- **SC-002**: 100% of verified repeated-event scenarios retain exactly one original request for the same event/template/recipient identity.
- **SC-003**: 100% of controlled temporary SMTP-failure scenarios make no more than three attempts and follow the one-minute then five-minute retry schedule.
- **SC-004**: 100% of controlled uncertain-delivery and post-invocation restart scenarios reach `UNKNOWN_OUTCOME` without automatic redelivery.
- **SC-005**: 100% of verified template-version and source-data change scenarios preserve the request's pinned template, recipient, business snapshot, and rendition.
- **SC-006**: 100% of verified preview, audit, diagnostic, and retention scenarios exclude raw credentials, QR payloads, payment payloads, and full email content where not required.
- **SC-007**: 100% of verified credential expiry/revocation scenarios scrub the raw credential no later than the next cleanup sweep.

## Dependencies and Compatibility

- Booking and Settlement currently use post-commit handoff payloads that include rendered content. This legacy behavior is not the FE-007 target contract; any migration requires separate approved design and compatibility verification.
- The detailed source contracts and placeholder sets for `BOOKING_CONFIRMED` and `RETAIL_ORDER_CONFIRMED` remain design-pack work. This specification does not authorize an implementer to invent DTO fields or data mappings.
- Admin/Manager refreshed-credential requests require an approved Booking/Retail authorization boundary. Existing Booking behavior that grants Magic Link rotation only to Managers is not evidence that the Admin-and-Manager decision in this spec is already implemented.
- Production SMTP domain, provider, sender, reply-to, support contact, and rate limit require domain/operations owner input. A governing retention policy with longer requirements supersedes the stated retention baseline.

## Assumptions

- V1 runs one application instance with one sequential delivery worker; multi-instance worker coordination is out of scope.
- Only transactional workflow emails are in scope. Push, SMS, Zalo, notification centre, marketing campaigns, segmentation, unsubscribe, open/click tracking, and dashboard workflow building are out of scope.
- An email request with a missing or invalid recipient, inactive template, or incomplete required data does not roll back a valid business result; it produces a safe operational failure instead.
- This specification is a pre-design baseline. Detailed data model, API contracts, authorization scope enforcement, migration, scheduler behavior, and approval evidence are produced and approved in later artifacts.

## Clarifications

### Session 2026-11-06

- Q: Template registry state machine detail (6 states) — where to handle? → A: Keep spec with observable behavior only (draft/active/deactivated); full 6-state semantics is design-pack work.
- Q: Authorization scope for Admin/Manager — global or Workshop Location? → A: V1 uses global scope; Workshop Location scoping is out-of-scope V1. Added FR-027.
- Q: Redelivery contract gap (Staff/Manager vs Admin/Manager, sync vs async) — block plan? → A: Spec (Admin or Manager) is authoritative; sync/async is design-pack detail. FR-019 sufficient.
- Q: Worker trigger mechanism and delivery latency? → A: Poll-based, every 30 seconds. Added FR-028.

### Session 2026-11-14

- Q: Does idempotency apply to resend/redelivery? → A: No. FR-006 protects only original requests; eligible resend and redelivery actions may create multiple derived requests.
- Q: What does `original_request_id` mean for a derived request? → A: It always references the root/original request, including when an operator acts on a previously derived request. No `parent_request_id` is introduced in V1.
- Q: Is a separate client-operation idempotency mechanism needed? → A: No. V1 does not introduce an `Idempotency-Key` contract or an operation table for manual resend/redelivery.
