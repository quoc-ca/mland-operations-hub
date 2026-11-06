# Interface Contract: NotificationFacade

**Date created**: 2026-11-06
**Date updated**: 2026-11-06
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
     * 2. Idempotent per (businessEventReference, templateCode, recipientRole, recipientAddressFingerprint).
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
2. **Idempotency**: Repeated invocation with the exact same business event reference, template code, and recipient returns `Status.DUPLICATE_IGNORED` referencing the existing `notificationRequestId`.
3. **No Unrendered Leaks**: The caller cannot pass pre-rendered HTML/text. The layout and phrasing remain strictly governed by `notificationmanagement` template versions.
4. **Resilience**: Caller will never receive an unhandled exception because of SMTP outage or invalid recipient format.

### Non-Guarantees:
1. Does NOT guarantee real-time delivery: delivery worker processes requests in 30-second poll cycles.
2. Does NOT guarantee email inbox placement (only SMTP server receipt).

