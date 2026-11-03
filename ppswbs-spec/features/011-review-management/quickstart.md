# Feature-011 Review Quickstart

Date created: 2026-11-03  
Date updated: 2026-11-03  
Author: Ngô Tiến Đạt  
Last updated by: Ngô Tiến Đạt

## Review order

Read `spec.md` → `research.md` → `data-model.md` → `plan.md` → `contracts/review-management.openapi.yaml` → `design/` → `tasks.md`. Then compare the synchronized project documents listed in `tasks.md`.

## Readiness checks

- Confirm the source facades can prove `CHECKED_IN`, `PICKED_UP` and `DELIVERED`.
- Confirm the API names and error codes in `APIs.md`.
- Confirm a free Flyway version and database-history upgrade path.
- Confirm Staff global hide and Manager remove/reply permissions.
- Confirm all public DTOs mask display identity and exclude transaction/contact data.

This artifact set is review-ready, not implementation-ready until the blockers above are resolved.
