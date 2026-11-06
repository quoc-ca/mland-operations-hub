# Feature Specification: FE-007 Notification Management

**Date created**: 2026-11-06
**Date updated**: 2026-11-06 (FR-027, FR-028 added per clarification session)
**Status**: Draft — working decisions only; not design-pack approval or authorization to implement.
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

- **FR-001**: The system MUST support only `BOOKING_CONFIRMED` and `RETAIL_ORDER_CONFIRMED` as activatable SMTP templates in V1.
- **FR-002**: The system MUST treat Firebase verification and password-reset email as Firebase-managed, read-only references; it MUST NOT send, retry, or report delivery for those emails.
- **FR-003**: A source feature MUST submit a structured notification intent in the same transaction as its accepted business result. The intent MUST identify the registered template, stable business event, safe business reference, approved recipient snapshot, frozen template-data snapshot, and any approved artifact reference.
- **FR-004**: FE-007 MUST own template version selection, notification requests, delivery state, rendition, delivery attempts, resend history, and operational audit. It MUST NOT re-read Booking/Retail data, choose a recipient, or decide that a business event is valid.
- **FR-005**: A source feature MUST NOT submit rendered subject, HTML, or plain-text email content as the notification intent.
- **FR-006**: The system MUST create no more than one original request for the same business event, template, recipient role, and recipient fingerprint.
- **FR-007**: A request MUST pin its template version, recipient snapshot, and business-data snapshot at creation. Changes to the source business record or recipient afterward MUST NOT alter that request.
- **FR-008**: Editing a template MUST create a draft version. A version that is active or referenced by a request MUST remain immutable and MUST NOT be hard deleted.
- **FR-009**: A globally shared template MUST have no more than one active version. Admins and Managers may manage its content, version lifecycle, activation, and deactivation across the system.
- **FR-010**: The system MUST validate each notification intent against the registered template's allowed placeholders and required data before creating a deliverable request. It MUST record a safe operational failure when validation cannot produce a request.
- **FR-011**: The first delivery processing of a request MUST create and retain its rendered subject, HTML, and plain-text rendition. Automatic retry and ordinary resend MUST use that rendition rather than re-rendering from current source data or a newer template.
- **FR-012**: Delivery state MUST distinguish `PENDING`, `PROCESSING`, `RETRY_SCHEDULED`, `SENT`, `FAILED`, `UNKNOWN_OUTCOME`, and `CANCELLED`. `SENT` means the SMTP provider accepted the message, not that it entered an inbox or was read.
- **FR-013**: The V1 single worker MUST retry only when it can determine that SMTP did not accept the message. It MUST make at most three SMTP attempts, waiting one minute before the second and five minutes before the third.
- **FR-014**: The system MUST classify potential SMTP acceptance with lost confirmation, or a restart after SMTP invocation may have begun, as `UNKNOWN_OUTCOME` and MUST NOT retry it automatically.
- **FR-015**: Admins and Managers MAY resend eligible sent or remediated failed requests in their authorized operational scope, with a required reason and audit. They MUST NOT resend a pending, processing, or scheduled-retry request.
- **FR-016**: Only an Admin MAY authorize a resend from `UNKNOWN_OUTCOME`, after provider evidence is reviewed and the risk of duplicate delivery is recorded.
- **FR-017**: Admins and Managers MAY cancel only a pending or scheduled-retry request with a reason. A processing request MUST NOT be cancelled through normal operations.
- **FR-018**: Ordinary resend MUST retain the original recipient, rendition, snapshot, and credential only while that credential remains valid. It MUST NOT change recipient, source data, template version, or credential.
- **FR-019**: For expired or revoked credentials, an Admin or Manager MAY request redelivery with a refreshed credential. The owning source feature MUST authorize the request and create the replacement credential/artifact; FE-007 MUST neither create nor revoke credentials and MUST preserve the original delivery history.
- **FR-020**: `BOOKING_CONFIRMED` MUST use the Booking-approved contact email and may contain an approved ticket/QR artifact and booking access link. `RETAIL_ORDER_CONFIRMED` MUST use the Retail-approved order-notification email and may contain an authorized order-details link. The system MUST NOT infer either recipient from unrelated profile or shipping-address data.
- **FR-021**: The system MUST never store in logs, business audit, dashboard preview, or normal operational diagnostics a password, raw token, raw Magic URL, QR payload, payment payload, or unnecessary internal note.
- **FR-022**: Dashboard diagnostics MUST expose only the information required for support, including masked recipient, template, safe business reference, delivery state, attempt count, timestamps, safe error category, and correlation reference.
- **FR-023**: SMTP provider, sender identity, reply-to address, support contact, credentials, and rate limits MUST be environment configuration. Local and non-production environments MUST prevent uncontrolled delivery to real customers.
- **FR-024**: Missing or invalid SMTP configuration MUST cause a safe delivery failure without rolling back the business result or issuing futile automatic retries.
- **FR-025**: The system MUST scrub raw credentials as soon as they expire or are revoked, no later than the next cleanup sweep. It MUST retain recipient snapshot, rendition, and sensitive error details for 90 days after terminal state, then scrub them and retain only a minimized tombstone.
- **FR-026**: The system MUST retain redacted delivery metadata, attempt history, and Admin/Manager operational audit for 12 months, unless a binding business or legal policy requires a longer period.
- **FR-027**: In V1, Admin and Manager authorization for template management, resend, cancellation, and operational actions MUST apply at global scope. Workshop Location–scoped authorization is out of scope for V1.
- **FR-028**: The V1 delivery worker MUST poll for processable requests (PENDING or RETRY_SCHEDULED whose scheduled time has arrived) every 30 seconds. The retry wait times specified in FR-013 are measured from the failed attempt, not from the poll cycle.

### Key Entities

- **Template Definition**: A registered transactional email type, its ownership, allowed placeholders, and lifecycle eligibility.
- **Template Version**: An immutable draft, active, or historical presentation revision for one template definition.
- **Notification Request**: The durable, idempotent record of one requested business email, with frozen template, recipient, business-data, and safe artifact references.
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
