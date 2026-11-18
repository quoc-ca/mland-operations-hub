# Feature-013 Version Log — Staff Consultation Chat

**Date created**: 2026-11-18  
**Date updated**: 2026-11-18  
**Author**: Ngô Tiến Đạt  
**Last updated by**: Ngô Tiến Đạt

## Version 1.0 — Approved, Implemented and Verified

- Confirmed separate Feature-013 boundary for human consultation chat.
- Confirmed Member-only customer access; Guest chat is out of scope.
- Confirmed Staff and Manager participation with shared inbox and claim mechanism.
- Preserved Staff Workshop Location scope; Manager has global access and assignment authority.
- Implemented REST + 10s visible-tab polling; no WebSocket/SSE.
- Restricted content to immutable UTF-8 text, maximum 2,000 Unicode code points.
- Enforced 30-message/minute Member rate limit, idempotency and optimistic version control (`If-Match-Version`).
- Enforced context attachment for Feature-012 workshop designs and Feature-004 independent requests.
- Server-owned context preview/code/version projection with immutable JSON snapshot.
- 3-year transcript retention and metadata-only audit logging (no raw message text in audit/logs).
- Created database migration `V183__staff_consultation_chat.sql`.
- Added OpenAPI contract, domain entities, repositories, application services, web controllers, exception handlers, and frontend components.
- Executed Spring Modulith architectural verification (`ModulithArchitectureTests`: BUILD SUCCESS).
- Executed Backend unit tests (`ConsultationApplicationServiceTest`: 6/6 tests PASSED).
- Executed Frontend unit tests (`vitest run src/features/consultationchat`: 9/9 tests PASSED).
- Executed Browser MCP E2E test scenarios (`E2E-013-001` through `E2E-013-005`: 5/5 PASSED, all screenshot evidence recorded).

## Current Blockers

- None.

## Status

`COMPLETED — FULLY IMPLEMENTED AND VERIFIED`
