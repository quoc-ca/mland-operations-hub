# Feature-013 Tasks

**Date created**: 2026-11-18  
**Date updated**: 2026-11-18  
**Author**: Ngô Tiến Đạt  
**Last updated by**: Ngô Tiến Đạt

## Specification and approval

- [x] T013-001 Review research, scope and actor matrix.
- [x] T013-002 Approve Feature-004 context projection facade.
- [x] T013-003 Approve Feature-012 context projection facade.
- [x] T013-004 Approve unassigned-context Manager routing policy.
- [x] T013-005 Approve data model, retention and audit policy.
- [x] T013-006 Verify Flyway baseline and approve next migration version.
- [x] T013-007 Complete `approval.md` with revision, approver and date.

## Backend

- [x] T013-010 Create Modulith module boundary and package structure.
- [x] T013-011 Implement context-options facade adapter and ownership checks.
- [x] T013-012 Implement conversation create and immutable snapshot.
- [x] T013-013 Implement message validation, NFC normalization and UTF-8 persistence.
- [x] T013-014 Implement idempotent message command.
- [x] T013-015 Implement rolling 30-message/minute Member rate limit.
- [x] T013-016 Implement scoped inbox and safe projections.
- [x] T013-017 Implement claim/release/Manager assignment with optimistic locking.
- [x] T013-018 Implement read state and polling-compatible list/detail responses.
- [x] T013-019 Implement close/reopen state transitions.
- [x] T013-020 Implement audit metadata without raw message logging.
- [x] T013-021 Create and test approved Flyway migration after V182.

## Frontend

- [x] T013-030 Create feature routes, API adapter and bilingual messages.
- [x] T013-031 Build context selector and preview integrity confirmation.
- [x] T013-032 Build Member list/detail/composer flow.
- [x] T013-033 Build Staff/Manager inbox and conversation detail.
- [x] T013-034 Build claim/release/assign and close/reopen controls.
- [x] T013-035 Add visible-tab polling and unread badge.
- [x] T013-036 Add validation, 401/403/404/409/429/503 states and retry.
- [x] T013-037 Verify Vietnamese text rendering and safe text-only output.

## Tests and E2E

- [x] T013-040 Add backend authorization and ownership tests.
- [x] T013-041 Add context reference/version integrity tests.
- [x] T013-042 Add Unicode, length, rate-limit and XSS-safe rendering tests.
- [x] T013-043 Add idempotency, concurrency and stale-version tests.
- [x] T013-044 Add audit/log redaction tests.
- [x] T013-045 Add frontend API/screen tests.
- [x] T013-046 Prepare E2E-013-001 through E2E-013-005 with isolation/cleanup.
- [x] T013-047 Execute Browser MCP scenarios and archive screenshots.

