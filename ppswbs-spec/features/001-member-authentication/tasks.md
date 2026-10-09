# Tasks: Member Registration and Sign-in

**Rule**: every item is open until fresh verification evidence is recorded in
verification.md. Existing source files do not constitute completion.

## Phase 1 — Environment and migration boundary

- [ ] T001 Provision ppswbs_migrator and ppswbs_app outside the repository; keep
  all credential values in secret injection only.
- [ ] T002 Add a local-mysql runtime profile using JDBC_* and a separate Flyway
  migration configuration using FLYWAY_*; prevent deployed profiles from falling
  back to H2.
- [ ] T003 Pin Testcontainers and local/integration documentation to mysql:9.7.2.
- [ ] T004 Add an empty-database Flyway test and a V1-upgrade preservation test
  on MySQL 9.7.2.
- [ ] T005 Add append-only migrations for accounts/account_roles, Member account
  backfill, and the generated effective-policy unique key; never edit V1.

## Phase 2 — Identity, role, and API foundation

- [ ] T006 Add failing tests for UID-only idempotent provisioning, account
  backfill, MySQL role/status resolution, and Firebase-claim non-authority.
- [ ] T007 Implement MySQL accounts/account_roles authorization behind
  members.facade; retain compatible Member UID lookups during migration.
- [ ] T008 Add tests and implementation for policy-current database enforcement,
  stale acceptance denial, and immutable acceptance evidence.
- [ ] T009 Align all seven controller mappings and request DTO validation with
  member-auth.openapi.yaml; no JPA entity may cross HTTP.
- [ ] T010 Verify typed error responses contain code, message, and correlationId
  while redacting tokens, passwords, provider codes, and avoidable PII.

## Phase 3 — Browser identity and protected journeys

- [ ] T011 Add Firebase Web SDK Google/email-password registration, sign-in,
  persistent-session, sign-out, recovery, verification, and provider-link flows.
- [ ] T012 Send fresh bearer tokens for provisioning/entitlement; retain safe
  bilingual fallback guidance when JavaScript is unavailable.
- [ ] T013 Add browser/Emulator smoke tests for success, cancellation,
  reauthentication, collision, invalid token, and no-email-merge paths.
- [ ] T014 Implement and test an allow-listed same-origin return-to flow.
- [ ] T015 Expose a reusable Member entitlement/route-guard seam; do not create
  ready-ring/history routes in this feature.

## Phase 4 — Booking confirmation and import

- [ ] T016 Add failing tests for required/nonblank confirmationToken, hashed
  persistence, one-time use, generic 400 failure, and 404 booking absence.
- [ ] T017 Implement booking confirmation endpoint/page and captured-mail port
  through workshopbooking.facade boundaries.
- [ ] T018 Enable the expiry scheduler and test transactional, idempotent
  cancellation plus payment/capacity/invoice gating.
- [ ] T019 Add failing tests that Guest booking import rejects absent/false
  confirmed, requires verified email/current entitlement, and is idempotent.
- [ ] T020 Implement preview/import DTO handling and audit evidence through
  members and workshopbooking facades only.

## Phase 5 — Final evidence and convergence

- [ ] T021 Run H2 fast tests and record exact command/result as non-release
  evidence.
- [ ] T022 Run MySQL 9.7.2 Flyway, upgrade, and Testcontainers integration
  suites; record exact command/image/result.
- [ ] T023 Run Firebase Emulator/non-production browser matrix and verify
  no secret/token/PII leakage in errors, logs, and audits.
- [ ] T024 Compare every OpenAPI path/status/schema with controllers and tests.
- [ ] T025 Run SpecKit analyze and converge; append only newly evidenced
  remaining work.
