# Implementation Plan: FE-007 Notification Management

**Date created**: 2026-11-06
**Date updated**: 2026-11-06
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Branch**: `007-notification-management` | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from [features/007-notification-management/spec.md](spec.md).

## Summary

FE-007 builds the minimum viable email infrastructure for Mland's transactional business emails. The feature provides: durable notification requests created in the same transaction as business results (booking confirmation, retail order confirmation), a poll-based SMTP delivery worker with retry/recovery, template version management via an admin dashboard, manual resend/cancel/redelivery operations with audit, and credential/PII lifecycle management. Firebase Auth continues to own verification and password-reset emails; FE-007 handles only registered business transactional templates.

The system is full-stack: backend owns all email processing (request creation, template/version management, delivery worker, retention/scrub); frontend provides an Admin/Manager dashboard for template content editing, version lifecycle, delivery diagnostics, and operational actions.

## Technical Context

**Language/Version**: Java 21 (backend), JavaScript/JSX (frontend)

**Primary Dependencies**:
- Backend: Spring Boot 3.x, Spring Modulith, Spring Data JPA, Jakarta Mail / Spring Mail (`JavaMailSender`), Flyway, MySQL
- Frontend: React + Vite, client-side rendering, REST API consumption

**Storage**: MySQL — notification requests, template definitions/versions, delivery attempts, renditions, operational audit, tombstones

**Testing**: JUnit 5 / Spring Boot Test (backend), Vitest or equivalent (frontend). SMTP verification via fake/sandbox SMTP (e.g., GreenMail or MailHog in test).

**Target Platform**: Web application (separate React frontend + Spring Boot backend REST API)

**Project Type**: Full-stack web service — backend module `notificationmanagement` within Spring Modulith, frontend feature `notification-management` within React app

**Performance Goals**: Worker polls every 30 seconds (FR-028). SMTP retry: 1 min then 5 min, max 3 attempts (FR-013). No latency SLA specified (TBD per spec-general §4).

**Constraints**: V1 single instance, single sequential worker. No distributed lease, no multi-worker. Global scope for Admin/Manager authorization (FR-027).

**Scale/Scope**: V1: 2 activatable templates (`BOOKING_CONFIRMED`, `RETAIL_ORDER_CONFIRMED`). Single worker processing requests sequentially.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
| --- | --- | --- |
| I. Module Integrity | ✅ PASS | FE-007 backend as `notificationmanagement` package under `features/`, with standard sub-packages (controller, dto, dao, entity, service, facade). `NotificationFacade` is the only public inter-feature interface. Constitution §I requires valid Java package names and facade-only dependencies. |
| I. Data Ownership | ✅ PASS | FE-007 owns notification requests, template definitions/versions, delivery state, renditions, audit. Source features own business events, recipients, snapshots. No shared business entity in `common`. Constitution §I data ownership preserved. |
| I. Facade Direction | ✅ PASS | Source features call `NotificationFacade` (one-way). FE-007 does not call back into source features for request creation. Redelivery with refreshed credential uses source feature facade (one-way, initiated by operator action). Constitution §I one-way facade + events for async. |
| II. Identity Boundary | ✅ PASS | FE-007 does not handle Firebase identity. Dashboard authentication uses the shared identity gate (`CurrentAccountActor`). No credential storage, no password, no token logging. Constitution §II preserved. |
| II. Sensitive Data | ✅ PASS | FR-021/FR-022/FR-025 explicitly prohibit logging raw tokens, QR payloads, payment data. Retention with scrub at 90 days/12 months. Constitution §II preserved. |
| III. REST Contract | ✅ PASS | Dashboard and operational APIs will use versioned plural kebab-case routes, explicit DTOs, Bean Validation, server-side authorization, typed safe errors, and OpenAPI contract. Constitution §III preserved. |
| III. Separate Frontend | ✅ PASS | Dashboard is a React feature under the separate frontend app. No SSR/Thymeleaf. HTML email templates are separate from UI rendering per constitution §III. |
| IV. Verification | ✅ PASS | FR acceptance scenarios require controlled SMTP, transaction verification, state/audit assertion, and authorization testing. Constitution §IV proportionate evidence requirement met by spec §12 and SC-001 through SC-007. |
| V. Simplicity | ✅ PASS | V1 uses single instance, single worker, poll-based, no Redis/distributed lock. Smallest solution with clear ownership. Constitution §V preserved. |
| V. Hybrid State | ✅ PASS | FE-007 is stateless per HTTP interaction. Retained state is the notification request lifecycle in MySQL, managed by the delivery worker. No application session store needed. Authentication state uses the shared Firebase bearer gate. |

No constitution violations. No complexity justification needed.

## Project Structure

### Documentation (this feature)

```text
features/007-notification-management/
├── spec.md              # Feature specification (completed)
├── plan.md              # This file
├── data-model.md        # Data model and state machines
├── quickstart.md        # Verification and test scenarios
├── contracts/           # Interface contracts
│   ├── notification-facade.md
│   └── notification-api.md
├── design/              # Feature design pack
│   ├── architecture.md
│   ├── workflows.md
│   └── ui-flow.md
├── checklists/
│   └── requirements.md  # Spec quality checklist (completed)
└── tasks.md             # Implementation tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/mland/ppswbs/features/notificationmanagement/
├── controller/
│   ├── NotificationTemplateController.java
│   ├── NotificationRequestController.java
│   └── dto/                                   # Transport DTOs shared with controller
├── dto/
│   ├── request/                               # Inbound DTOs
│   └── response/                              # Outbound DTOs
├── dao/
│   ├── TemplateDefinitionRepository.java
│   ├── TemplateVersionRepository.java
│   ├── NotificationRequestRepository.java
│   ├── DeliveryAttemptRepository.java
│   └── OperationalAuditRepository.java
├── entity/
│   ├── TemplateDefinition.java
│   ├── TemplateVersion.java
│   ├── NotificationRequest.java
│   ├── Rendition.java
│   ├── DeliveryAttempt.java
│   ├── OperationalFailure.java
│   ├── OperationalAuditRecord.java
│   └── Tombstone.java
├── service/
│   ├── NotificationRequestService.java        # Request creation, idempotency
│   ├── TemplateManagementService.java         # Template/version CRUD, lifecycle
│   ├── DeliveryWorkerService.java             # Poll-based worker, SMTP send, retry
│   ├── RenditionService.java                  # Template rendering
│   ├── RetentionService.java                  # Scrub/tombstone lifecycle
│   └── OperationalService.java               # Resend, cancel, redelivery
└── facade/
    └── NotificationFacade.java                # Public inter-feature interface

backend/src/main/resources/db/migration/
└── V<next>__create_notification_management_tables.sql

frontend/src/features/notification-management/
├── ui/
│   ├── TemplateListPage.jsx
│   ├── TemplateVersionEditor.jsx
│   ├── TemplatePreview.jsx
│   ├── NotificationRequestList.jsx
│   └── NotificationRequestDetail.jsx
├── application/
│   ├── useTemplateManagement.js
│   └── useNotificationOperations.js
├── infrastructure/
│   └── notificationApiClient.js
└── routes/
    └── index.jsx
```

**Structure Decision**: Full-stack web application with backend Modulith module `notificationmanagement` under `features/` and frontend vertical-slice under `src/features/notification-management/`. Backend follows constitution §I package conventions. Frontend follows proposed frontend architecture baseline from constitution §III.
