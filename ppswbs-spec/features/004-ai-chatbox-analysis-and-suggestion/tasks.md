# Tasks: Feature-004 Frontend and Contract Synchronization

**Date created**: 2026-10-21
**Date updated**: 2026-11-05
**Author**: Ngô Tiến Đạt
**Last updated by**: Ngô Tiến Đạt

Tasks below distinguish completed frontend delivery from remaining contract work.

## Phase A — Boundary and frontend baseline

- [x] T001 Read `ppswbs-spec/AGENT.md` and frontend `AGENT.md`.
- [x] T002 Confirm independent Design Request boundary.
- [x] T003 Confirm Guest Basic AI only and Member-only Design Request creation.
- [x] T004 Confirm description-only, image-only and combined request behavior.
- [x] T005 Document React/Vite routes, shared modules and UI state rules.

## Phase B — Frontend delivery

- [x] T006 Register Feature-004 routes in the application router.
- [x] T007 Implement Basic AI Support screen and bilingual messages.
- [x] T008 Implement Member Design Workspace, image validation and consent.
- [x] T009 Implement owner-safe status/result display.
- [x] T010 Implement Staff/Manager review queue/detail screens.
- [x] T011 Implement Manager price suggestion and Admin technical-audit projection.
- [x] T012 Add frontend API and screen tests for current behavior.

## Phase C — Contract and integration blockers

- [x] T013 Synchronize OpenAPI field names and status codes with the backend, or approve a versioned compatibility contract (`ReviewQueueResponse`, `requestVersion`, `202 Accepted`).
- [x] T014 Add account bootstrap role/status contract if frontend role-aware navigation is required (`AccountFacade`, `CurrentAccountActor`, `IdentityGateFilter`).
- [x] T015 Harden backend consent-purpose validation and verify image deletion/retention (`DataRetentionCleanupJob`, `ImageConsentRepository`).
- [x] T016 Verify live provider, backend integration, migration upgrade and E2E evidence (44/44 backend IT tests passed, E2E-004-001 PASS).

## Phase D — Optional architecture normalization

- [ ] T017 Plan a separate refactor from the current flat `designreview` slice to `ui/application/domain/infrastructure`.
- [ ] T018 Preserve route/API behavior and tests during any folder normalization.

Implementation must not use the optional refactor to hide unresolved API or business
contract mismatches. No booking, payment or catalogue publication behavior may be
introduced by these tasks.
