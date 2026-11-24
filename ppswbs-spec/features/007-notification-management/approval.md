# Tech Lead Approval Record: Notification Management (FE-007)

## R-INV invitation extension (2026-11-24)

**Decision state:** APPROVED FOR IMPLEMENTATION of R-INV T038–T043 and T045; T044 is documentation-only completion. **Approved by (Tech Lead):** Phan Sơn. **Approval date:** 2026-11-25. **Authority/evidence:** the decision owner reviewed the amended FE-001/FE-007 plan and explicitly instructed “oke có vẻ ổn r, hãy chuyển thành approve cho tôi” in this specification session. This replaces the earlier “chờ chốt” state; it does not claim implementation or delivery verification is complete.

**Approved revision and paths:** FE-001 `design/revision-2026-11-24.md` (R-INV, amended 2026-11-25); FE-007 `spec.md` (FR-029–034), `plan.md` (R-INV section), `data-model.md` (R-INV extension), `design/architecture.md`, `design/workflows.md`, `design/ui-flow.md` (R-INV sections), `contracts/notification-facade.md` (§6), `contracts/notification-api.md` and `contracts/r-inv.openapi.yaml` (manual-content operation), `tasks.md` (T038–T045), `quickstart.md` and `verification.md` (R-INV verification plan). The revision covers `INTERNAL_ACCOUNT_INVITATION`, durable FE-001 outbox event consumption, a one-way FE-007-to-FE-001 named facade for transient URL materialization, 48-hour expiry from creation, two definite SMTP attempts, ADMIN-only manual content retrieval after FAILED, UNKNOWN_OUTCOME reconciliation, and secret-free persistence/diagnostics. Generic FE-007 manual resend/redeliver does not apply to this template. FE-007's durable attempt state is the manual-content authority; FE-001 rechecks current invitation/ADMIN identity. FE-001 T117 live MEMBER replacement is excluded; no delivery verification is claimed.

---

## Revision R-007-DELIVERY-FAILED — Booking Notification Delivery-Failed Signal & Retry Policy Alignment (FR-029)

**Revision date**: 2026-11-16  
**Approved by (Tech Lead)**: **Phan Sơn**  
**Authority/evidence**: Design review session on 2026-11-16 resolving inter-feature blocker between Feature 003.1 (T147 / FR-015a, FR-015b) and Feature 007 (FR-029) documented in `local-notes/handover-007-delivery-failed.md` and `local-notes/handover-007-delivery-failed-response.md`.  
**Decision**: **APPROVED FOR IMPLEMENTATION OF T037 IN 007 AND T147 IN 003.1**.

### Approved Behavior and Boundaries:
1. **Event definition and Modulith encapsulation (Zero Cycle)**:
   - `BookingNotificationDeliveryFailed` is defined in package `edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade` (Named Interface `workshopbooking :: notification`).
   - Feature 007 declares `allowedDependencies = "workshopbooking :: notification"`, imports this record, and publishes it upon final failure.
   - Feature 003.1 listens to this event internally. Module dependency remains unidirectional (`007 -> 003`), strictly satisfying Spring Modulith encapsulation and preventing bidirectional dependency cycles.

2. **Retry Policy alignment**:
   - Feature 007 worker retry policy is tightened to at most 2 technical attempts (initial send + 1 scheduled technical retry after ~1 minute) to survive transient SMTP drops.
   - When attempt 2 fails, 007 records terminal state `FAILED` in `notification_deliveries` and publishes `BookingNotificationDeliveryFailed`.
   - Feature 003.1 controls business-level retry scheduling (5 minutes, 30 minutes, 2 hours per FR-015a/b) and escalation to Manager. Each business retry initiates a fresh notification handoff with a new `notificationReference`.

3. **Payload and Sensitive Data Protection (D13)**:
   - Record: `(notificationReference, bookingReference, notificationKind, failureCode, failedAt, correlationReference)`.
   - `failureCode` enum: `PROVIDER_UNAVAILABLE`, `PROVIDER_REJECTED`, `RECIPIENT_INVALID`, `PAYLOAD_MISSING`.
   - Strictly contains no email address, rendered HTML/text, Magic-Link token, ticket/QR artifact data, or payment secrets.

4. **Implementation Authorization**:
   - Unblocks Feature 007 implementation of Task T037 in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/BookingMailDeliveryService.java`.
   - Unblocks Feature 003.1 implementation of Task T147 in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
