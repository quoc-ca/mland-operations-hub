# Feature-011 Tasks

Date created: 2026-11-03  
Date updated: 2026-11-04  
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

## Phase 3 — Implementation handoff

- [x] Implement source ports/adapters (`WorkshopAttendanceReviewPort`, `RetailFulfilmentReviewPort`, `InternalWorkshopAttendanceAdapter`, `InternalRetailFulfilmentAdapter`) and review application services (`ReviewEligibilityService`, `ReviewApplicationService`, `ReviewModerationService`, `ReviewReplyService`, `ReviewPublicReadFacade`, `ReviewMaskingService`, `ReviewIdempotencyService`).
- [x] Implement persistence, unique source constraint and audit history (`ReviewRepository`, `ReviewRevisionRepository`, `ReviewReplyRepository`, `ReviewModerationEventRepository`, `GuestEligibilityHandleRepository`, `ReviewIdempotencyRecordRepository`).
- [x] Implement API and authorization boundaries (`ReviewEligibilityController`, `ReviewController`, `ReviewModerationController`, `ReviewManagementExceptionHandler`, Security routes).
- [ ] Implement public/operational UI.

## Phase 4 — Verification

- [x] Unit and integration tests for eligibility, validation, lifecycle and permission rules (21/21 tests pass).
- [x] Concurrent duplicate-create and idempotency tests.
- [x] Privacy tests for masking and source enumeration.
- [x] Migration clean/upgrade/re-run tests (`V91__review_management.sql`).
- [x] Guest handle expiry, source binding, replay and raw-source rejection tests.
- [x] Realtime public aggregate excludes `HIDDEN` and `REMOVED` rows.
- [ ] OpenAPI, architecture, `git diff --check` and manual acceptance review.
