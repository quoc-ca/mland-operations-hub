# Feature-013 Implementation Plan

**Date created**: 2026-11-18  
**Date updated**: 2026-11-18  
**Author**: Ngô Tiến Đạt  
**Last updated by**: Ngô Tiến Đạt

Status: documentation baseline only; implementation is blocked by facade and migration approval.

## Phase 1 — Contract and dependency gate

1. Confirm Account facade actor/status/location contract.
2. Add safe context projection ports for Feature-004 and Feature-012.
3. Decide and verify branch routing for contexts without a branch.
4. Verify Flyway history and select a new version after `V182`.
5. Approve OpenAPI, data model and retention/audit rules.

## Phase 2 — Backend

1. Create the `consultationchat` Spring Modulith module.
2. Implement context option lookup through ports only.
3. Implement conversation creation with immutable context snapshot.
4. Implement message append, normalization, idempotency and rate limiting.
5. Implement scoped inbox, claim/release, Manager assignment and optimistic locking.
6. Implement read state, close/reopen and safe projections.
7. Add append-only audit events and UTF-8/retention handling.
8. Add the approved Flyway migration only after repository/history verification.

## Phase 3 — Frontend

1. Create `src/features/consultationchat/` using React/Vite shared auth, HTTP,
   i18n and UI primitives.
2. Build Member context selector with atomic code/name/version/preview cards.
3. Build Member conversation list/detail and message composer.
4. Build Staff/Manager inbox/detail, claim/release/assign and lifecycle actions.
5. Add ten-second visible-tab polling, unread state, retry and conflict recovery.
6. Render all message content as text and verify Vietnamese UTF-8 display.

## Phase 4 — Verification and rollout gate

1. Run backend unit/integration tests and frontend tests/build.
2. Run OpenAPI/schema/Flyway validation.
3. Prepare isolated Member, two Staff branches and Manager data.
4. Run Browser MCP E2E scenarios with screenshots and cleanup.
5. Resolve all authorization, context-integrity, encoding and audit defects before
   marking the feature implementation-ready.

