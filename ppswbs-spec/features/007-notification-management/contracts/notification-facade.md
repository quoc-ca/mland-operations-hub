# Interface Contract: NotificationFacade

**Date created**: 2026-11-06
**Date updated**: 2026-11-24
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Package**: `edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade`

## 1. Overview

`NotificationFacade` is the **sole public Java interface** provided by `notificationmanagement` for consumption by other business features (`workshopbooking`, `readyringsales`, etc.).

Per PPSWBS Constitution Principle I:
- Cross-feature calls MUST use facade interfaces and explicit boundary types.
- Controllers, DAOs, entities, internal services, and technical adapters remain strictly internal.
- Communication is one-way: business features call `NotificationFacade` inside their own transaction.

---

## 2. Java Interface Definition

```java
package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade;

import java.util.Map;
import java.util.Optional;

public interface NotificationFacade {

    /**
     * Enqueues a notification intent inside the caller's active database transaction.
     *
     * <p>Invariants:
     * 1. Executed synchronously within the caller's JPA/Spring transaction.
     * 2. Creates at most one original request per (businessEventReference, templateCode,
     *    recipientRole, recipientAddressFingerprint), including concurrent calls. This
     *    invariant does not apply to operational resend or redelivery requests.
     * 3. If validation fails (e.g. inactive template, invalid recipient, missing placeholder),
     *    the method DOES NOT throw an exception and DOES NOT roll back the business transaction;
     *    it creates a durable OperationalFailure record and returns a failed NotificationIntentResult.
     * 4. SMTP delivery is NOT executed in this call; it occurs asynchronously post-commit via worker polling.
     *
     * @param command The notification intent command containing business event reference, template code,
     *                recipient details, and frozen business snapshot.
     * @return NotificationIntentResult indicating whether a request was enqueued or an operational failure recorded.
     */
    NotificationIntentResult submitNotificationIntent(SubmitNotificationIntentCommand command);

    /**
     * Checks if a notification request exists for a given business event.
     *
     * @param businessEventReference Unique business event ID (e.g. bookingCode or orderCode).
     * @param templateCode           Registered template code (e.g. "BOOKING_CONFIRMED").
     * @return Minimal diagnostic summary if present.
     */
    Optional<NotificationSummaryDto> findNotificationSummary(String businessEventReference, String templateCode);
}
```

---

## 3. Boundary Types (DTOs)

### 3.1. `SubmitNotificationIntentCommand`

```java
package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubmitNotificationIntentCommand {
    /** Unique source event ID (e.g. booking code, order reference). Mandatory. */
    private final String businessEventReference;

    /** Registered template code (e.g. "BOOKING_CONFIRMED", "RETAIL_ORDER_CONFIRMED"). Mandatory. */
    private final String templateCode;

    /** Authoritative recipient email approved by source feature. Mandatory. */
    private final String recipientEmail;

    /** Recipient display name approved by source feature. Optional. */
    private final String recipientName;

    /** Frozen map of template variables/placeholders matching template definition allowlist. Mandatory. */
    private final Map<String, Object> businessDataSnapshot;

    /** Optional safe non-credential artifact reference (e.g. ticket ID, order link slug). */
    private final String artifactReference;

    /** Optional short-lived credential reference (scrubbed on expiry). */
    private final String credentialReference;

    /** Optional expiration time of the credential reference. */
    private final Instant credentialExpiresAt;
}
```

### 3.2. `NotificationIntentResult`

```java
package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationIntentResult {
    public enum Status {
        ENQUEUED,
        DUPLICATE_IGNORED,
        OPERATIONAL_FAILURE
    }

    private final Status status;
    private final Long notificationRequestId;   // Non-null if ENQUEUED or DUPLICATE_IGNORED
    private final Long operationalFailureId;     // Non-null if OPERATIONAL_FAILURE
    private final String failureCode;            // Non-null if OPERATIONAL_FAILURE
    private final String message;
}
```

### 3.3. `NotificationSummaryDto`

```java
package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationSummaryDto {
    private final Long requestId;
    private final String templateCode;
    private final String deliveryState; // PENDING, PROCESSING, RETRY_SCHEDULED, SENT, FAILED, UNKNOWN_OUTCOME, CANCELLED
    private final int attemptCount;
    private final Instant createdAt;
    private final Instant terminalAt;
}
```

---

## 4. Contract Guarantees & Non-Guarantees

### Guarantees:
1. **Transaction Safety**: Rollback of the caller's transaction automatically rolls back any inserted `notification_request` or `notification_operational_failure`.
2. **Original-request idempotency**: Repeated or concurrent invocation with the exact same business event reference, template code, recipient role, and recipient fingerprint returns `Status.DUPLICATE_IGNORED` referencing the existing original `notificationRequestId`.
3. **No Unrendered Leaks**: The caller cannot pass pre-rendered HTML/text. The layout and phrasing remain strictly governed by `notificationmanagement` template versions.
4. **Resilience**: Caller will never receive an unhandled exception because of SMTP outage or invalid recipient format.

---

## 3. Outbound Refreshed-Credential Boundary

This is a behavioral contract for the owning Booking or Retail facade called by FE-007 during redelivery; the owning source feature defines its Java type and DTO names.

- **Input**: the selected notification request's safe business identity, the authenticated Admin/Manager actor, and the operator's required reason.
- **Authorized result**: a replacement credential/artifact reference and its expiry, scoped to the same business event. FE-007 attaches this result only to the new derived notification request.
- **Denied or unavailable result**: FE-007 creates no derived request and returns the safe `422 Unprocessable Entity` outcome defined by `notification-api.md` §2.5.
- **Boundary rules**: the source feature owns authorization and credential/artifact issuance; FE-007 neither issues, revokes, nor reuses an expired/revoked credential. The boundary must not expose raw credentials in logs, audit records, or diagnostics.

### Non-Guarantees:
1. Does NOT guarantee real-time delivery: delivery worker processes requests in 30-second poll cycles.
2. Does NOT guarantee email inbox placement (only SMTP server receipt).

---

## 5. Inter-feature Event Boundaries (Workshop Booking & Settlement)

In addition to synchronous facade calls, Feature 007 participates in asynchronous post-commit event handoffs:
- **Consumes**: `BookingNotificationReady` and `SettlementNotificationReady` (from `workshopbooking :: notification` and `workshopsettlement :: facade`).
- **Publishes**: `BookingNotificationDeliveryFailed(notificationReference, bookingReference, notificationKind, failureCode, failedAt, correlationReference)` when technical retries are exhausted.
- **Contract Reference**: Documented canonically in `ppswbs-spec/features/003-workshop-management/contracts/workshop-interfeature-facades.md` § "Booking notification delivery-failed signal — Feature 007 to 003.1".

---

## 6. R-INV internal-account invitation boundary (2026-11-24)

The generic `submitNotificationIntent` path above remains for existing notification kinds. The invitation uses a one-way module dependency `notificationmanagement -> accountmanagement :: invitationdelivery`: FE-001 defines the published event and facade types; FE-007 imports them. FE-001 MUST NOT import or call `NotificationFacade` for this flow.

1. In the same transaction that creates or rotates a `PENDING_SETUP` invitation, FE-001 persists the invitation and an outbox record keyed by `(invitationId, version)`. After commit, FE-001 publishes `InternalAccountInvitationRequested(invitationId, version, accountId, recipientEmail, recipientName, eventReference, correlationReference)`. No secret or URL appears in this event. Replay is allowed and FE-007 deduplicates by the event reference plus template and recipient fingerprint. A durable outbox replay handles process failure between commit and event consumption.
2. FE-007 consumes the event, pins `INTERNAL_ACCOUNT_INVITATION` template version, approved recipient and safe business snapshot, and creates a notification request. Validation failures become durable operational failures; they do not roll back the FE-001 Account. The request's `credential_reference` is an opaque invitation ID/version only; `credential_expires_at` copies FE-001's creation-based `expiresAt`. The persisted rendition contains a sentinel placeholder for the setup URL and never the expanded URL.
3. Immediately before each SMTP attempt, FE-007 checks `expiresAt`, verifies that its request belongs to the pinned FE-001 outbox event/version, and calls `InvitationDeliveryFacade.materialize(MaterializationRequest(invitationId, version, eventReference, requestId, SMTP_ATTEMPT, attemptId, null))`. FE-001 authorizes that exact current, unexpired, non-consumed version and its own event reference; it need not have received a first request-status callback. It returns a transient setup URL derived from the server key version. The URL is substituted into the outgoing MIME in memory, never saved to rendition, request, attempt, failure, preview, audit or log. A retry for the same version obtains the same URL. A rotated/consumed/expired version returns `STALE` and the worker cancels the old request without SMTP invocation.
4. On definite SMTP acceptance FE-007 records its `SENT` attempt and reports the delivery state to FE-001 idempotently. This report does not alter FE-001 `expiresAt` or gate setup exchange. FE-001's `createdAt` and `expiresAt = createdAt + 48 hours` were persisted when the invitation version was created. FE-007 retries a failed status report from durable attempt evidence; a late report for a rotated version is recorded for audit without reactivating it.
5. A definite pre-acceptance failure gets one technical retry after about one minute with the same invitation version. After two definite failures FE-007 records terminal `FAILED` and reports it to FE-001. `UNKNOWN_OUTCOME` MUST NOT automatically resend, rotate or unlock manual retrieval; provider/operator evidence must reconcile acceptance or definite failure first. The generic FE-007 resend/redeliver/cancel endpoints do not apply to this template. Only FE-001 ADMIN invitation resend, after five-minute cooldown and reconciliation, rotates the version and enqueues a new event.

6. After verifying its own two definite failed attempts and terminal `FAILED`, the FE-007 ADMIN-only manual-content API calls `materialize(MaterializationRequest(invitationId, version, eventReference, requestId, MANUAL_CONTENT, null, adminActorAccountId))`; the actor ID comes from the authenticated server principal, never the browser body. FE-007 checks the actor, request/outbox-event ownership/type and delivery evidence; FE-001 independently checks active ADMIN, its own event reference, current invitation version and unused/unexpired state. FE-001's callback status may lag and is not the authority for FE-007's FAILED decision. FE-007 substitutes the URL into the pinned subject/HTML/plain text only in the `Cache-Control: no-store` response to that ADMIN. The request, rendition, attempt, log, general diagnostic and audit remain secret-free. The API refuses expired/used/rotated links and any request still pending, sent or `UNKNOWN_OUTCOME`; opening/copying does not mark delivery `SENT` or extend expiry.

The FE-001 named interface returns typed `AVAILABLE(url, expiresAt)` or `STALE/UNAVAILABLE` for materialization and accepts idempotent safe delivery-state reports. General dashboard/API diagnostics never return the URL. FE-007 owns attempt state; FE-001 owns invitation state and expiry. Cross-module calls use published facade/event types only, and `ApplicationModules.verify()` must pass.

