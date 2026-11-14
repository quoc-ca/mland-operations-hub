# Tasks: FE-007 Notification Management

**Date created**: 2026-11-06
**Date updated**: 2026-11-06
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Input**: Design documents from `ppswbs-spec/features/007-notification-management/` (`spec.md`, `plan.md`, `data-model.md`, `contracts/`, `quickstart.md`, `design/`)

**Prerequisites**: `plan.md`, `spec.md`, `data-model.md`, `contracts/notification-facade.md`, `contracts/notification-api.md`, `design/architecture.md`, `design/workflows.md`, `design/ui-flow.md`

## Format: `- [ ] [TaskID] [P?] [Story?] Description with file path`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Package layout and baseline schema migration for FE-007

- [ ] T001 Create backend module directory structure at `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/` with sub-packages `controller`, `dto`, `dao`, `entity`, `service`, and `facade`
- [ ] T002 [P] Create frontend feature directory structure at `ppswbs_frontend/src/features/notification-management/` with sub-directories `ui`, `application`, `infrastructure`, and `routes`
- [ ] T003 Create the initial Flyway migration defining all 8 tables per `data-model.md` (§3.1 to §3.8) with constraints and indexes

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core JPA entities, repositories, and error codes that MUST be complete before any user story can be implemented

- [ ] T004 [P] Create `TemplateDefinition` and `TemplateVersion` JPA entities with constraints (`VARCHAR(64) code UNIQUE`, `allowed_placeholders JSON`, `version_number INT`) in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/entity/`
- [ ] T005 [P] Create `NotificationRequest`, `Rendition`, and `DeliveryAttempt` JPA entities with lifecycle states (`PENDING`, `PROCESSING`, `RETRY_SCHEDULED`, `SENT`, `FAILED`, `UNKNOWN_OUTCOME`, `CANCELLED`) in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/entity/`
- [ ] T006 [P] Create `OperationalFailure`, `OperationalAuditRecord`, and `Tombstone` JPA entities in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/entity/`
- [ ] T007 [P] Create Spring Data JPA repositories (`TemplateDefinitionRepository`, `TemplateVersionRepository`, `NotificationRequestRepository`, `DeliveryAttemptRepository`, `OperationalAuditRepository`, `OperationalFailureRepository`, `RenditionRepository`, `TombstoneRepository`) in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/dao/`
- [ ] T008 [P] Define `NotificationFacade` interface and boundary DTOs (`SubmitNotificationIntentCommand`, `NotificationIntentResult`, `NotificationSummaryDto`) in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/facade/` per `contracts/notification-facade.md`

**Checkpoint**: Foundation ready - User Story implementation can proceed.

---

## Phase 3: User Story 1 - Deliver a confirmed business email (Priority: P1) 🎯 MVP

**Goal**: When a Booking or Retail Order commits, create one durable notification request in the same transaction. The delivery worker polls every 30 seconds and safely delivers via SMTP with retry (1m, 5m; max 3 attempts) without altering the business outcome.

**Independent Test**: Execute `quickstart.md` Scenarios 1, 2, and 3 using mock SMTP server; verify rollback safety, idempotency, and 30s poll-based retry delivery.

### Implementation for User Story 1

- [ ] T009 [P] [US1] Implement `RenditionService` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/service/RenditionService.java` for variable substitution (`{{placeholder}}`) into subject, HTML, and plain-text
- [ ] T010 [US1] Implement `NotificationRequestService` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/service/NotificationRequestService.java` handling in-transaction validation, original-request idempotency (including unique-constraint collision recovery for concurrent calls), template version pinning, and saving `NotificationRequest` or `OperationalFailure`
- [ ] T011 [US1] Implement `NotificationFacadeImpl` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/service/NotificationFacadeImpl.java` delegating to `NotificationRequestService`
- [ ] T012 [US1] Implement `DeliveryWorkerService` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/service/DeliveryWorkerService.java` with `@Scheduled(fixedDelay = 30000)` polling for `PENDING` or `RETRY_SCHEDULED` requests, recording `smtp_invocation_started_at`, calling `JavaMailSender.send()`, recording `DeliveryAttempt`, and scheduling retries (1m, 5m, max 3 attempts)
- [ ] T013 [US1] Implement `RecoveryService` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/service/RecoveryService.java` with `@EventListener(ApplicationReadyEvent.class)` to transition interrupted `PROCESSING` requests with recorded SMTP invocation to `UNKNOWN_OUTCOME`
- [ ] T014 [US1] Seed initial `notification_template_definition` and active `notification_template_version` records for `BOOKING_CONFIRMED` and `RETAIL_ORDER_CONFIRMED` via Flyway migration in `V93__notification_management.sql`

**Checkpoint**: User Story 1 complete — transactional email enqueue and delivery worker functional.

---

## Phase 4: User Story 2 - Manage global transactional templates (Priority: P1)

**Goal**: Admins and Managers manage content, drafts, and lifecycle of pre-registered transactional templates via dashboard without code changes.

**Independent Test**: Execute `quickstart.md` Scenarios 5 and 6; verify draft revision creation, preview with safe fixture data, activation, and pinning immutability.

### Implementation for User Story 2

- [ ] T015 [P] [US2] Implement `TemplateManagementService` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/service/TemplateManagementService.java` for listing definitions, creating/updating draft revisions, preview rendering, and activating a draft (which deactivates the previous active version)
- [ ] T016 [US2] Implement `NotificationTemplateController` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/controller/NotificationTemplateController.java` exposing `/api/v1/notification-templates` per `contracts/notification-api.md` §1
- [ ] T017 [P] [US2] Create API client `notificationApiClient.js` in `ppswbs_frontend/src/features/notification-management/infrastructure/notificationApiClient.js`
- [ ] T018 [P] [US2] Create custom hooks `useTemplates.js` and `useTemplateVersions.js` in `ppswbs_frontend/src/features/notification-management/application/`
- [ ] T019 [US2] Build `TemplateListPage.jsx` and `TemplateDetailView.jsx` in `ppswbs_frontend/src/features/notification-management/ui/` displaying template registry, status badges (`ACTIVE`, `DRAFT`, `EXTERNAL`), and version history
- [ ] T020 [US2] Build `TemplateVersionEditorModal.jsx` and `TemplatePreviewModal.jsx` in `ppswbs_frontend/src/features/notification-management/ui/` allowing draft editing, placeholder validation against allowlist, and safe iframe preview

**Checkpoint**: User Story 2 complete — Admin/Manager can manage template versions and preview them safely.

---

## Phase 5: User Story 3 - Operate a delivery safely (Priority: P2)

**Goal**: Operators inspect diagnostic metadata with masked recipients, resend eligible requests with an auditable reason, or cancel pending/scheduled requests without re-running source business logic.

**Independent Test**: Execute `quickstart.md` Scenario 4; verify diagnostic display, resend of `SENT`/`FAILED`, denial of `PROCESSING` cancellation, and Admin-only override for `UNKNOWN_OUTCOME`.

### Implementation for User Story 3

- [ ] T021 [P] [US3] Implement `OperationalService` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/service/OperationalService.java` handling manual resend (cloning the selected request while linking the new request to its root and preserving valid snapshot/rendition/credential), cancellation (`CANCELLED` terminal state), and writing `OperationalAuditRecord`
- [ ] T022 [US3] Implement `NotificationRequestController` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/controller/NotificationRequestController.java` exposing `/api/v1/notification-requests` per `contracts/notification-api.md` §2
- [ ] T023 [P] [US3] Create custom hooks `useNotificationRequests.js` and `useNotificationOperations.js` in `ppswbs_frontend/src/features/notification-management/application/`
- [ ] T024 [US3] Build `NotificationRequestListPage.jsx` and `NotificationRequestDetailDrawer.jsx` in `ppswbs_frontend/src/features/notification-management/ui/` with state filters, masked recipients (`a***b@example.com`), and attempt logs
- [ ] T025 [US3] Build `ResendConfirmationModal.jsx` and `CancelConfirmationModal.jsx` in `ppswbs_frontend/src/features/notification-management/ui/` requiring mandatory reason and enforcing Admin-only checkbox for `UNKNOWN_OUTCOME`

**Checkpoint**: User Story 3 complete — delivery diagnostics and audited manual operations functional.

---

## Phase 6: User Story 4 - Redeliver with a renewed credential (Priority: P2)

**Goal**: When a credential/ticket artifact in an email has expired or been revoked, Admin/Manager can request redelivery with a replacement credential authorized by the owning source feature without modifying original business snapshot.

**Independent Test**: Execute redelivery test where expired ticket is replaced with a new ticket reference from mock booking feature; verify linked delivery uses new credential while original request history remains unchanged.

### Implementation for User Story 4

- [ ] T026 [US4] Add `redeliverWithRefreshedCredential` method to `OperationalService` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/service/OperationalService.java` that validates with source feature, preserves business snapshot, and attaches fresh credential reference
- [ ] T027 [US4] Expose `POST /api/v1/notification-requests/{id}/redeliver` in `NotificationRequestController.java` per `contracts/notification-api.md` §2.5
- [ ] T028 [US4] Add "Gửi lại với link/vé mới" action in `NotificationRequestDetailDrawer.jsx` in `ppswbs_frontend/src/features/notification-management/ui/`

**Checkpoint**: User Story 4 complete — redelivery with refreshed credentials functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Retention management, privacy cleanup, security checks, and Modulith architecture verification

- [ ] T029 [P] Implement `RetentionService` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/service/RetentionService.java` with daily `@Scheduled` sweep to scrub sensitive recipient/rendition data after 90 days and create `Tombstone` records (FR-025, FR-026)
- [ ] T030 Add Spring Modulith verification test `NotificationModuleTest.java` in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/notificationmanagement/` asserting that `ApplicationModules.of(PpswbsBackendApplication.class).verify()` passes without boundary violations
- [ ] T031 [P] Register frontend routes in `ppswbs_frontend/src/app/App.jsx` pointing to `notificationRoutes.jsx` under `/admin/notifications/`
- [ ] T032 Verify full end-to-end flow using scenarios in `quickstart.md`
- [ ] T033 Add an append-only corrective Flyway migration that normalizes existing derived-request chains to their root, preserves historical audit/delivery records, replaces `uq_nr_idempotency` with the original-only constraint from `data-model.md`, and updates the mapped JPA entity accordingly
- [ ] T034 Add integration coverage for concurrent original intents (one `ENQUEUED`, one `DUPLICATE_IGNORED`) and for multiple eligible resend/redelivery actions linked to the same root

---

## Dependencies & Execution Order

### Phase Dependencies
- **Phase 1 (Setup)**: Can start immediately.
- **Phase 2 (Foundational)**: Depends on Phase 1; BLOCKS all user stories.
- **Phase 3 (User Story 1 - MVP)**: Depends on Phase 2; can proceed independently.
- **Phase 4 (User Story 2)**: Depends on Phase 2; can proceed in parallel with US1.
- **Phase 5 (User Story 3)**: Depends on Phase 3 (needs notification request entity & states) and Phase 2.
- **Phase 6 (User Story 4)**: Depends on Phase 5.
- **Phase 7 (Polish)**: Depends on User Story 4 for the corrective lineage/redelivery verification tasks.

### Parallel Opportunities
- Backend entities & repositories (T004, T005, T006, T007) can be developed in parallel.
- Frontend API client & hooks (T017, T018, T023) can be developed in parallel with backend controllers.
- User Story 1 (Backend delivery engine) and User Story 2 (Template management dashboard) can be developed in parallel once Phase 2 is complete.

