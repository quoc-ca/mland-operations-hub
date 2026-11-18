# Feature-013 Quality Checklist

**Date created**: 2026-11-18  
**Date updated**: 2026-11-18  
**Author**: Ngô Tiến Đạt  
**Last updated by**: Ngô Tiến Đạt

- [ ] `git diff --check` is clean.
- [ ] OpenAPI parses and contains no duplicate paths/schemas.
- [ ] Fresh and upgrade migrations are tested after version approval.
- [ ] Concurrent claim, send retry and rate-limit tests pass.
- [ ] Staff cannot cross Workshop Location scope.
- [ ] Manager can route and participate globally.
- [ ] Foreign/fake/stale context references are denied safely.
- [ ] Logs/audit contain no raw message or credentials.
- [ ] Vietnamese text survives API, DB, polling and browser rendering.
- [ ] Frontend tests cover all specified states.
- [ ] Browser MCP is available before E2E execution.
- [ ] Every E2E scenario has isolation, cleanup, observable assertions and JPEG evidence.

