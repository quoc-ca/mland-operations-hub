# Quality Checklist: Feature-04

- [ ] `spec.md` has no unresolved business decision that blocks implementation.
- [ ] OpenAPI routes, access rules and error responses match `spec.md`.
- [ ] JPA/Flyway mapping preserves existing domain ownership and does not use raw SQL in application code.
- [ ] Provider calls have timeout, safe mapping, correlation and retry behavior.
- [ ] Raw tokens, credentials, images, provider payloads and unnecessary PII are excluded from logs/audit.
- [ ] Guest/Member/Staff/Manager/Admin authorization tests cover allowed and denied paths.
- [ ] Concurrent duplicate submissions and review decisions are safe.
- [ ] Temporary image cleanup is retry-safe and observable.
- [ ] Text retention cleanup is tested against the 30-day boundary.
- [ ] AI provider failure never creates approval, price publication, payment or custom-order mutation.
- [ ] Server-rendered forms and links work without htmx/JavaScript for required core actions.
- [ ] Spring Modulith verification passes without undeclared cross-module access.
- [ ] MySQL migration/integration tests run against the selected development/test target.
- [ ] Actual verification commands and limitations are recorded before completion.
