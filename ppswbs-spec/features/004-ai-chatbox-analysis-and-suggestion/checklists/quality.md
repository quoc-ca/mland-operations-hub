# Quality Checklist: Independent Feature-04

- [ ] Ownership/review/retention decisions are approved and not hidden in code.
- [ ] Independent flow has no booking prerequisite or booking side effect.
- [ ] Account facade and ownership proof are the only identity boundary.
- [ ] OpenAPI routes and fields match the independent data model.
- [ ] Provider commands are normalized, allowlisted and policy-validated.
- [ ] Guest/Member/Staff/Manager/Admin permissions and denial paths are tested.
- [ ] Consent, image validation, rate limiting, idempotency and deletion are tested.
- [ ] Analysis and review states are separate and version-safe.
- [ ] Price suggestion remains advisory and cannot publish automatically.
- [ ] Raw tokens, images, provider payloads and unnecessary PII are not exposed.
- [ ] Flyway clean-install and upgrade paths are tested without duplicate-version workaround.
- [ ] Spring Modulith verification passes with only approved facade dependencies.
- [ ] Exact compile/test commands and limitations are recorded in `version.md`.
