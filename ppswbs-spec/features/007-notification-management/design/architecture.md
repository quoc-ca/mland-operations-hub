# Architecture Design: FE-007 Notification Management

**Date created**: 2026-11-06
**Date updated**: 2026-11-06
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Data Model**: [data-model.md](../data-model.md) | **Contracts**: [contracts/](../contracts/)

---

## 1. Scope and Source Map

| Requirement / Scenario | Authoritative Source | Architecture Response | Task | Verification |
|---|---|---|---|---|
| In-transaction durable request creation | spec.md FR-003, FR-006 | `NotificationFacadeImpl` called synchronously in caller transaction; persists an original `NotificationRequest` protected by an original-only unique constraint | T-BE-01 | quickstart.md Scenario 1, 2 |
| Safe failure on invalid/inactive template | spec.md FR-010, FR-024 | `NotificationRequestService.validateAndPrepareRequest()` persists `OperationalFailure` without rolling back business tx | T-BE-02 | quickstart.md Scenario 1 |
| Poll-based delivery worker (30s) | spec.md FR-013, FR-028 | `DeliveryWorkerService.pollAndDeliver()` annotated with `@Scheduled(fixedDelay = 30000)` | T-BE-03 | quickstart.md Scenario 3 |
| Max 3 SMTP attempts & retry (1m, 5m) | spec.md FR-013 | `DeliveryWorkerService` handles `MailException`, calculates `nextAttemptAt`, sets `RETRY_SCHEDULED` | T-BE-04 | quickstart.md Scenario 3 |
| Unknown outcome & crash recovery | spec.md FR-014 | `RecoveryService.recoverInterruptedRequests()` scans on startup for uncommitted `smtpInvocationStartedAt` | T-BE-05 | quickstart.md Scenario 4 |
| Template version management & immutability | spec.md FR-008, FR-009 | `NotificationTemplateController`, `TemplateManagementService` creates drafts; activating deactivates previous active | T-BE-06 | quickstart.md Scenario 5 |
| Rendition generation on first attempt | spec.md FR-011 | `RenditionService.renderAndPersist()` saves `Rendition` on first attempt; retry/resend reuses it | T-BE-07 | quickstart.md Scenario 5 |
| Operational resend, cancel, redelivery | spec.md FR-015 to FR-019 | `NotificationRequestController`, `OperationalService` creates audited derived requests linked to their root; redelivery obtains a replacement credential from the owning feature | T-BE-08 | quickstart.md Scenarios 4, 8 |
| Data retention scrub & tombstone | spec.md FR-025, FR-026 | `RetentionService.sweepExpiredData()` runs scheduled daily sweep | T-BE-09 | quickstart.md Scenario 8 |
| Frontend Admin/Manager Dashboard | spec.md FR-009, FR-022, FR-027 | React components under `src/features/notification-management/` for templates and delivery diagnostics | T-FE-01..05 | quickstart.md Scenario 6 |

---

## 2. Implementation Tree and Ownership

```text
ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/  [proposed — plan.md]
  ├── controller/                                                                                [proposed]
  │   ├── NotificationTemplateController.java                                                    [proposed]
  │   ├── NotificationRequestController.java                                                     [proposed]
  │   └── dto/                                                                                   [proposed]
  │       ├── TemplateResponseDto.java                                                           [proposed]
  │       ├── CreateTemplateVersionRequest.java                                                  [proposed]
  │       ├── TemplatePreviewRequest.java                                                        [proposed]
  │       ├── TemplatePreviewResponse.java                                                       [proposed]
  │       ├── NotificationRequestQueryFilter.java                                                [proposed]
  │       ├── NotificationRequestSummaryDto.java                                                 [proposed]
  │       ├── NotificationRequestDetailDto.java                                                  [proposed]
  │       ├── ResendRequestCommand.java                                                          [proposed]
  │       └── CancelRequestCommand.java                                                          [proposed]
  ├── facade/                                                                                    [proposed]
  │   ├── NotificationFacade.java                                                                [approved — contracts/notification-facade.md]
  │   ├── SubmitNotificationIntentCommand.java                                                   [approved — contracts/notification-facade.md]
  │   ├── NotificationIntentResult.java                                                          [approved — contracts/notification-facade.md]
  │   └── NotificationSummaryDto.java                                                            [approved — contracts/notification-facade.md]
  ├── service/                                                                                   [proposed]
  │   ├── NotificationFacadeImpl.java                                                            [proposed]
  │   ├── NotificationRequestService.java                                                        [proposed]
  │   ├── TemplateManagementService.java                                                         [proposed]
  │   ├── DeliveryWorkerService.java                                                             [proposed]
  │   ├── RenditionService.java                                                                  [proposed]
  │   ├── OperationalService.java                                                                [proposed]
  │   ├── RecoveryService.java                                                                   [proposed]
  │   └── RetentionService.java                                                                  [proposed]
  ├── dao/                                                                                       [proposed]
  │   ├── TemplateDefinitionRepository.java                                                      [proposed]
  │   ├── TemplateVersionRepository.java                                                         [proposed]
  │   ├── NotificationRequestRepository.java                                                     [proposed]
  │   ├── RenditionRepository.java                                                               [proposed]
  │   ├── DeliveryAttemptRepository.java                                                         [proposed]
  │   ├── OperationalFailureRepository.java                                                      [proposed]
  │   ├── OperationalAuditRepository.java                                                        [proposed]
  │   └── TombstoneRepository.java                                                               [proposed]
  └── entity/                                                                                    [proposed]
      ├── TemplateDefinition.java                                                                [approved — data-model.md §3.1]
      ├── TemplateVersion.java                                                                   [approved — data-model.md §3.2]
      ├── NotificationRequest.java                                                               [approved — data-model.md §3.3]
      ├── Rendition.java                                                                         [approved — data-model.md §3.4]
      ├── DeliveryAttempt.java                                                                   [approved — data-model.md §3.5]
      ├── OperationalFailure.java                                                                [approved — data-model.md §3.6]
      ├── OperationalAuditRecord.java                                                            [approved — data-model.md §3.7]
      └── Tombstone.java                                                                         [approved — data-model.md §3.8]

ppswbs_backend/src/main/resources/db/migration/
  └── V93__notification_management.sql                                                           [proposed — plan.md]

ppswbs_frontend/src/features/notification-management/                                           [proposed — plan.md]
  ├── ui/                                                                                        [proposed]
  │   ├── TemplateListPage.jsx                                                                   [proposed]
  │   ├── TemplateDetailView.jsx                                                                 [proposed]
  │   ├── TemplateVersionEditorModal.jsx                                                         [proposed]
  │   ├── TemplatePreviewModal.jsx                                                               [proposed]
  │   ├── NotificationRequestListPage.jsx                                                        [proposed]
  │   ├── NotificationRequestDetailDrawer.jsx                                                    [proposed]
  │   ├── ResendConfirmationModal.jsx                                                            [proposed]
  │   └── CancelConfirmationModal.jsx                                                            [proposed]
  ├── application/                                                                               [proposed]
  │   ├── useTemplates.js                                                                        [proposed]
  │   ├── useTemplateVersions.js                                                                 [proposed]
  │   ├── useNotificationRequests.js                                                             [proposed]
  │   └── useNotificationOperations.js                                                           [proposed]
  ├── infrastructure/                                                                            [proposed]
  │   └── notificationApiClient.js                                                               [proposed]
  └── routes/                                                                                    [proposed]
      └── notificationRoutes.jsx                                                                 [proposed]
```

### Dependency Rules:
- Backend: External business modules (`workshopbooking`, `readyringsales`, etc.) ONLY depend on `NotificationFacade` and boundary DTOs.
- `notificationmanagement` internally follows: `controller -> service -> dao -> entity`.
- In compliance with Modulith: `ApplicationModules.of(PpswbsBackendApplication.class).verify()` will verify that no internal classes of `notificationmanagement` leak to other modules.

---

## 3. Production-Unit Blueprint

| Path | Type / Status | Responsibility | Class / Export | Draft Public / Use-Case Methods | Allowed Dependencies | Source |
|---|---|---|---|---|---|---|
| `.../facade/NotificationFacade.java` | Interface / Approved | Inter-module facade | `NotificationFacade` | `submitNotificationIntent(cmd)`, `findNotificationSummary(ref, code)` | Boundary DTOs only | contracts/notification-facade.md |
| `.../service/NotificationFacadeImpl.java` | Service / Proposed | Facade implementation | `NotificationFacadeImpl` | `submitNotificationIntent(cmd)`, `findNotificationSummary(ref, code)` | `NotificationRequestService` | plan.md |
| `.../service/NotificationRequestService.java` | Service / Proposed | In-tx request creation & idempotency | `NotificationRequestService` | `enqueueRequest(cmd)`, `recordFailure(eventRef, code, reason)` | `NotificationRequestRepository`, `TemplateDefinitionRepository`, `TemplateVersionRepository`, `OperationalFailureRepository` | spec.md FR-003, FR-006, FR-010 |
| `.../service/TemplateManagementService.java` | Service / Proposed | Template & version lifecycle | `TemplateManagementService` | `listTemplates()`, `getTemplate(code)`, `createDraft(code, cmd)`, `updateDraft(code, ver, cmd)`, `activateVersion(code, ver)`, `renderPreview(code, ver, sample)` | `TemplateDefinitionRepository`, `TemplateVersionRepository`, `RenditionService` | spec.md FR-008, FR-009 |
| `.../service/DeliveryWorkerService.java` | Service / Proposed | Poll worker & SMTP invocation | `DeliveryWorkerService` | `@Scheduled pollAndDeliver()`, `deliverSingleRequest(req)` | `NotificationRequestRepository`, `RenditionService`, `DeliveryAttemptRepository`, `JavaMailSender` | spec.md FR-012, FR-013, FR-028 |
| `.../service/RenditionService.java` | Service / Proposed | Variable substitution & rendering | `RenditionService` | `getOrCreateRendition(request)`, `renderSample(subject, html, text, data)` | `RenditionRepository` | spec.md FR-011 |
| `.../service/OperationalService.java` | Service / Proposed | Resend, cancel, redeliver | `OperationalService` | `resendRequest(id, actor, reason, acceptRisk)`, `cancelRequest(id, actor, reason)`, `redeliverWithRefreshedCredential(id, actor, reason)` | `NotificationRequestRepository`, `OperationalAuditRepository` | spec.md FR-015 to FR-019 |
| `.../service/RecoveryService.java` | Service / Proposed | Interrupted request recovery | `RecoveryService` | `@EventListener(ApplicationReadyEvent) recoverInterruptedRequests()` | `NotificationRequestRepository`, `DeliveryAttemptRepository` | spec.md FR-014 |
| `.../service/RetentionService.java` | Service / Proposed | 90-day retention scrub | `RetentionService` | `@Scheduled sweepExpiredData()` | `NotificationRequestRepository`, `RenditionRepository`, `TombstoneRepository` | spec.md FR-025, FR-026 |
| `.../controller/NotificationTemplateController.java` | Controller / Proposed | Template REST API | `NotificationTemplateController` | `getTemplates()`, `getTemplate(code)`, `createDraft()`, `updateDraft()`, `activate()`, `preview()` | `TemplateManagementService` | contracts/notification-api.md §1 |
| `.../controller/NotificationRequestController.java` | Controller / Proposed | Diagnostics & Ops REST API | `NotificationRequestController` | `listRequests()`, `getRequest(id)`, `resend()`, `cancel()`, `redeliver()` | `OperationalService`, `NotificationRequestRepository` | contracts/notification-api.md §2 |
| `frontend/.../infrastructure/notificationApiClient.js` | API Client / Proposed | HTTP requests to backend | `notificationApiClient` | `fetchTemplates()`, `activateVersion()`, `fetchRequests()`, `resendRequest()`, `cancelRequest()` | Axios / fetch client with Firebase Token | contracts/notification-api.md |
| `frontend/.../ui/TemplateListPage.jsx` | Component / Proposed | Template list & status UI | `TemplateListPage` | Props: none | `useTemplates`, `TemplateDetailView` | ui-flow.md §1 |
| `frontend/.../ui/NotificationRequestListPage.jsx` | Component / Proposed | Request diagnostic & actions | `NotificationRequestListPage` | Props: none | `useNotificationRequests`, `useNotificationOperations` | ui-flow.md §2 |

---

## 4. Libraries and Integrations

| Layer | Library / Version | Status | Purpose | Why Needed | Approval / Source |
|---|---|---|---|---|---|
| Backend | `spring-boot-starter-mail` (3.x) | Existing | Jakarta Mail / `JavaMailSender` | Sending emails over SMTP | Existing in backend `pom.xml` |
| Backend | `spring-modulith-starter-core` (1.3.1) | Existing | Module verification | Enforces facade boundaries | Existing in backend `pom.xml` |
| Backend | `com.icegreen:greenmail-junit5` (1.6.x) | Proposed (test) | Embedded test SMTP server | Integration verification without live SMTP | constitution §IV, quickstart.md |
| Frontend | React + Vite | Existing | UI Rendering | Dashboard admin views | constitution §III |

---

## 5. Data Flows

### Flow 1: In-Transaction Request Submission
```text
SourceFeatureService 
  → NotificationFacade.submitNotificationIntent(cmd)
    → NotificationRequestService.enqueueRequest(cmd)
      → finds existing original by business identity, if any
      → TemplateDefinitionRepository.findByCode(cmd.templateCode)
      → TemplateVersionRepository.findActiveByDefinitionId(def.id)
      → NotificationRequestRepository.save(originalRequest [PENDING, original_request_id=NULL])
  → Transaction Commits
```
*Concurrent duplicate branch*: if another transaction commits the same original first, the original-only unique constraint rejects this insert; `NotificationRequestService` re-reads that original and returns `DUPLICATE_IGNORED`.
*Error Branch*: Inactive template / missing placeholder:
```text
NotificationRequestService.validate() fails
  → OperationalFailureRepository.save(failure)
  → returns Status.OPERATIONAL_FAILURE
  → Caller business transaction continues and commits successfully
```

### Flow 2: 30-Second Delivery Worker Poll & SMTP Delivery
```text
@Scheduled(fixedDelay = 30000) DeliveryWorkerService.pollAndDeliver()
  → NotificationRequestRepository.findPollableRequests(states=['PENDING', 'RETRY_SCHEDULED'], time<=now())
  → Loop sequentially:
    → RenditionService.getOrCreateRendition(request)
    → request.setProcessingStartedAt(now())
    → request.setSmtpInvocationStartedAt(now())
    → NotificationRequestRepository.save(request [PROCESSING])
    → JavaMailSender.send(mimeMessage)
    → DeliveryAttemptRepository.save(attempt [SUCCESS])
    → request.setDeliveryState(SENT)
    → NotificationRequestRepository.save(request)
```
*Error Branch*: Temporary SMTP Connection Exception (Attempt < 3):
```text
JavaMailSender.send() throws MailException
  → DeliveryAttemptRepository.save(attempt [NETWORK_TEMPORARY])
  → request.setAttemptCount(attemptCount + 1)
  → request.setNextAttemptAt(now() + (attempt==1 ? 1min : 5min))
  → request.setDeliveryState(RETRY_SCHEDULED)
  → NotificationRequestRepository.save(request)
```

### Flow 3: Manual Resend by Admin/Manager
```text
Admin/Manager in UI 
  → NotificationRequestListPage (Click "Gửi lại", enter reason)
    → notificationApiClient.resendRequest(id, reason)
      → NotificationRequestController.resend(id, cmd)
        → OperationalService.resendRequest(id, actor, cmd.reason)
          → Verifies request is SENT or FAILED
          → Resolves root request (selected request if original; otherwise selected.originalRequest)
          → OperationalAuditRepository.save(audit [RESEND])
          → NotificationRequestRepository.save(newLinkedRequest [PENDING, original_request_id=root.id])
          → returns newLinkedRequest ID
```

### Flow 4: Redelivery with Refreshed Credential
```text
Admin/Manager in UI
  → notificationApiClient.redeliverRequest(id, reason)
    → NotificationRequestController.redeliver(id, cmd)
      → OperationalService.redeliverWithRefreshedCredential(id, actor, cmd.reason)
        → Resolves root request
        → Owning Booking/Retail facade authorizes and supplies replacement credential/artifact
        → If denied or unavailable: returns safe 422; no child request is created
        → NotificationRequestRepository.save(newLinkedRequest [PENDING, original_request_id=root.id])
        → OperationalAuditRepository.save(audit [REDELIVER_REFRESHED_CREDENTIAL])
        → returns newLinkedRequest ID
```

