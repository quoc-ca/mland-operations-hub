# Feature-011 Tasks

Date created: 2026-11-03  
Date updated: 2026-11-03  
Author: Ngô Tiến Đạt  
Last updated by: Ngô Tiến Đạt

## Phase 0 — Baseline and decisions

- [ ] Read and apply `ppswbs-spec/AGENT.md`, constitution and design kit.
- [ ] Confirm Feature-011 decisions against Report 3 and `spec-general.md`.
- [ ] Record approval of Guest no-OTP/no-rate-limit risk.
- [ ] Confirm source-facade ownership and cross-module dependency direction.

## Phase 1 — Specification and data

- [ ] Review `spec.md` permission matrix, state rules and acceptance scenarios.
- [ ] Approve workshop attendance and retail order-line physical mappings.
- [ ] Review `data-model.md` and entity constraints.
- [ ] Scan repository/database Flyway history and select an unused migration version.
- [ ] Define migration upgrade, rollback/recovery and data-retention evidence.

## Phase 2 — Contract and design

- [ ] Synchronize endpoint names, fields, errors and idempotency behavior with `APIs.md`.
- [ ] Review the OpenAPI contract.
- [ ] Review architecture, workflows and UI-flow against the approved API.
- [ ] Update `spec-general.md`, `DB.md`, `screen.md` and Report 3.
- [ ] Run cross-artifact analysis and resolve contradictions.

## Phase 3 — Implementation handoff

- [ ] Implement source ports/adapters and review application services.
- [ ] Implement persistence, unique source constraint and audit history.
- [ ] Implement API and authorization boundaries.
- [ ] Implement public/operational UI.

## Phase 4 — Verification

- [ ] Unit and integration tests for eligibility, validation, lifecycle and permission rules.
- [ ] Concurrent duplicate-create and idempotency tests.
- [ ] Privacy tests for masking and source enumeration.
- [ ] Migration clean/upgrade/re-run tests.
- [ ] OpenAPI, architecture, `git diff --check` and manual acceptance review.
