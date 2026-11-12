# Feature-011 Tasks

Date created: 2026-11-03  
Date updated: 2026-11-05
Author: Ngô Tiến Đạt  
Last updated by: Ngô Tiến Đạt

## Phase 0 — Baseline and decisions

- [x] Read and apply `ppswbs-spec/AGENT.md`, constitution and design kit.
- [x] Confirm Feature-011 decisions against Report 3 and `spec-general.md`.
- [x] Record approval of Guest no-OTP/no-rate-limit risk.
- [x] Confirm source-facade ownership and cross-module dependency direction.

## Phase 1 — Specification and data

- [x] Review `spec.md` permission matrix, state rules and acceptance scenarios.
- [x] Approve workshop attendance and retail order-line physical mappings.
- [x] Review `data-model.md` and entity constraints.
- [x] Scan repository/database Flyway history and select an unused migration version (`V91__review_management.sql` after merge with `develop`).
- [x] Define migration upgrade, rollback/recovery and data-retention evidence.
- [x] Define Guest eligibility-handle TTL (30 min), binding, replay and storage contract (MySQL `guest_eligibility_handles`).
- [x] Define Workshop Operations and Fulfilment facade DTOs for ownership and state evidence.
- [x] Define idempotency record scope, canonical request digest and replay response.

## Phase 2 — Contract and design

- [x] Synchronize endpoint names, fields, errors and idempotency behavior with `APIs.md`.
- [x] Review the OpenAPI contract.
- [x] Review architecture, workflows and UI-flow against the approved API.
- [x] Update `spec-general.md`, `DB.md`, `screen.md` and Report 3.
- [x] Run cross-artifact analysis and resolve contradictions.
- [x] Add Guest handle, error mapping, optimistic version and realtime aggregate details to OpenAPI.
- [x] Ratify backend contract: header idempotency/version headers, Guest retail eligibility, safe projections, Admin denial and `400/401/403/404/409/422` mapping.

## Phase 3 — Implementation handoff

- [x] Implement source ports/adapters (`WorkshopAttendanceReviewPort`, `RetailFulfilmentReviewPort`, `InternalWorkshopAttendanceAdapter`, `InternalRetailFulfilmentAdapter`) and review application services (`ReviewEligibilityService`, `ReviewApplicationService`, `ReviewModerationService`, `ReviewReplyService`, `ReviewPublicReadFacade`, `ReviewMaskingService`, `ReviewIdempotencyService`).
- [x] Implement persistence, unique source constraint and audit history (`ReviewRepository`, `ReviewRevisionRepository`, `ReviewReplyRepository`, `ReviewModerationEventRepository`, `GuestEligibilityHandleRepository`, `ReviewIdempotencyRecordRepository`).
- [x] Implement API and authorization boundaries (`ReviewEligibilityController`, `ReviewController`, `ReviewModerationController`, `ReviewManagementExceptionHandler`, Security routes).
- [x] Align DTO limits to 2,000 characters and move command idempotency/version data to headers.
- [x] Add Guest retail lookup/create, owner-safe public detail projection and Staff/Manager/Admin permission enforcement.
- [x] Add append-only retention correction migration `V100__review_history_retention.sql`.
- [x] Implement public/operational UI (`src/features/reviewmanagement/`).

## Phase 4 — Verification

- [x] Unit and integration tests for eligibility, validation, lifecycle and permission rules after contract changes (33/33 tests passed).
- [x] Concurrent duplicate-create and idempotency tests for every write command (`ReviewApplicationServiceTest`, `ReviewIdempotencyService`).
- [x] Privacy tests for masking and source enumeration (`ReviewMaskingService`, masked contact/display names).
- [x] Migration clean/upgrade/re-run tests (`V91`–`V100`).
- [x] Guest handle expiry, source binding, replay and raw-source rejection tests (`ReviewEligibilityServiceTest`).
- [x] Realtime public aggregate excludes `HIDDEN` and `REMOVED` rows (`ReviewPublicReadServiceTest`).
- [x] OpenAPI, architecture, `git diff --check` and Spring Modulith verification (`ModulithArchitectureTests`: 5/5 passed).

## Phase 5 — Frontend implementation handoff

- [x] T050 Create `src/features/reviewmanagement/` using the React/Vite feature architecture.
- [x] T051 Register public, Guest, Member, Staff and Manager routes without adding business behavior to the router.
- [x] T052 Implement OpenAPI-based review API adapter with Firebase auth, `Idempotency-Key` and `If-Match-Version`.
- [x] T053 Implement Guest lookup and opaque-handle source selection.
- [x] T054 Implement Member eligible-source, create-review and edit-own-review screens.
- [x] T055 Implement public review list/detail and public-only aggregate display.
- [x] T056 Implement Staff/Manager moderation queue, detail, visibility actions and Manager reply editor.
- [x] T057 Implement loading, empty, validation, forbidden, duplicate, conflict, retry and privacy states.
- [x] T058 Add unit/component tests for API mapping, permission, ownership, validation and recovery (7/7 test files, 35/35 tests passed).
- [x] T059 Run `npm test` and `npm run build` after the backend/OpenAPI blockers are resolved (Build passed in 2.51s).

Frontend tasks target the approved OpenAPI/spec. They must stop if the backend
contract remains mismatched; no client-side compatibility workaround is authorized.
