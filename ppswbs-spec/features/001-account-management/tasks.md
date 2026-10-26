# Tasks: Account Management

**Date created**: 2026-10-26
**Date updated**: 2026-10-26
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

All tasks are pending. They require separate implementation approval and the
designs in [spec.md](spec.md), [plan.md](plan.md), [data-model.md](data-model.md),
and [contracts](contracts/).

## Phase 1: Shared foundation

- [ ] T001 Replace global security configuration at `ppswbs_backend/.../configuration/SecurityConfiguration.java` with one explicit-public/default-deny chain, configured CORS origins, scoped CSRF, and no generated default user/password.
- [ ] T002 Create Firebase bearer/revocation identity gate and request actor context under `ppswbs_backend/.../features/accountmanagement/service/` and `facade/` per `CurrentAccountActor` contract.
- [ ] T003 [P] Mark `accountmanagement.facade` as a named Modulith interface and replace entity-enum leaking facade types with boundary records/enums at `ppswbs_backend/.../features/accountmanagement/facade/`.
- [ ] T004 [P] Remove committed runtime credential defaults from `ppswbs_backend/src/main/resources/application*.properties`; require approved environment/secret configuration.
- [ ] T005 Add authorization/identity-gate/Modulith regression coverage at `ppswbs_backend/src/test/java/.../AccountManagementSecurityIT.java` and `ModulithArchitectureTests.java`.

## Phase 2: Persistence and core Account flows

- [ ] T006 Reserve the next Flyway integer, then create Account/Profile/Location/Provisioning/Audit schema migration in `ppswbs_backend/src/main/resources/db/migration/` with UID uniqueness, role/state/version constraints, and no duplicate location table.
- [ ] T007 Implement Account/Profile/Location/Provisioning/Audit entities and repositories in `ppswbs_backend/src/main/java/.../features/accountmanagement/{entity,dao}/` according to `data-model.md`.
- [ ] T008 Implement bootstrap, verified-email profile/email sync, identity collision denial, and `CurrentAccountActor` resolution services in `ppswbs_backend/src/main/java/.../features/accountmanagement/service/`.
- [ ] T009 Implement typed Account errors/correlation-safe responses and strict DTO validation in `ppswbs_backend/src/main/java/.../features/accountmanagement/{controller,dto}/`.
- [ ] T010 Implement `/api/v1/accounts/me`, own profile, and email-sync operations from `contracts/account-management.openapi.yaml`.
- [ ] T011 Add MySQL migration/upgrade/UID-race/profile validation/API error tests in `ppswbs_backend/src/test/java/.../features/accountmanagement/`.

## Phase 3: US1 — Personal account bootstrap

**Goal**: A customer can register/sign in and bootstrap exactly one Account.

- [ ] T012 [P] [US1] Implement Firebase client registration/sign-in/sign-out/provider-error flows in `ppswbs_frontend/src/features/accountmanagement/auth/`.
- [ ] T013 [US1] Add Auth Emulator registration/bootstrap/collision coverage at `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/accountmanagement/AccountAuthenticationIT.java`.

## Phase 4: US2 — Verification and self-service

**Goal**: A signed-in customer can verify email and safely manage own profile/credentials.

- [ ] T014 [P] [US2] Implement verification reminder, password reset/change, and email-change/sync flows in `ppswbs_frontend/src/features/accountmanagement/security/`.
- [ ] T015 [US2] Implement profile page/API integration and `401`/`403`/`503` recovery in `ppswbs_frontend/src/features/accountmanagement/profile/`.
- [ ] T016 [US2] Add unverified/profile/email/password integration coverage at `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/accountmanagement/AccountSelfServiceIT.java`.

## Phase 5: US3 — Internal account administration

**Goal**: Admin can safely provision Staff/Manager without duplicate identity or premature access.

- [ ] T017 [P] [US3] Implement workshop-location facade adapter validation and Account location-scope rules in `ppswbs_backend/src/main/java/.../features/accountmanagement/service/`.
- [ ] T018 [US3] Implement idempotent internal provisioning, Firebase reconciliation/action links, five-minute resend cooldown, and FE-07 post-commit intent in `ppswbs_backend/src/main/java/.../features/accountmanagement/service/`.
- [ ] T019 [US3] Implement pending-to-active activation and Admin list/detail/create/invitation endpoints in `ppswbs_backend/src/main/java/.../features/accountmanagement/controller/`.
- [ ] T020 [P] [US3] Implement Admin accounts list/detail/create/invitation UI in `ppswbs_frontend/src/features/accountmanagement/admin/`.
- [ ] T021 [US3] Add idempotency, partial-Firebase-failure, activation, location-scope, and FE-07 isolation tests in `ppswbs_backend/src/test/java/.../features/accountmanagement/`.

## Phase 6: US4/US5 — Account protection and fixed roles

- [ ] T022 [P] [US4] Implement versioned internal profile and role/location assignment services/controllers with safe before/after audit at `ppswbs_backend/src/main/java/.../features/accountmanagement/`.
- [ ] T023 [US4] Implement suspension, durable revocation retry, restore-after-revocation, and lock/unlock notification intent at `ppswbs_backend/src/main/java/.../features/accountmanagement/service/`.
- [ ] T024 [P] [US5] Implement fixed role catalogue endpoint and Admin UI action confirmation/version-conflict recovery in `ppswbs_backend/src/main/java/.../features/accountmanagement/` and `ppswbs_frontend/src/features/accountmanagement/admin/`.
- [ ] T025 [US4] Add stale-version, suspension/revocation/recovery, no-Admin-management, and redacted-audit tests at `ppswbs_backend/src/test/java/.../features/accountmanagement/`.

## Phase 7: Verification and handoff

- [ ] T026 Run contract validation against `contracts/account-management.openapi.yaml`, verify all protected/public route decisions, and update `APIs.md` only with approved final route evidence.
- [ ] T027 Run Auth Emulator, disposable MySQL empty/upgrade/concurrency, FE-07 failure, Modulith, backend regression, and frontend build/API tests listed in `quickstart.md`.
- [ ] T028 Record actual commands/results/limitations in `features/001-account-management/verification.md`; do not mark tasks complete without evidence.

## Dependencies and MVP

| Requirement group | Tasks |
| --- | --- |
| FR-001–FR-006 identity/bootstrap/self-service | T001–T005, T008–T016 |
| FR-007–FR-008 profile boundaries | T007–T011, T015–T016 |
| FR-009–FR-013 fixed role/location/activation | T003, T016–T021 |
| FR-014–FR-015 provisioning/idempotency/invitation | T017–T021 |
| FR-016–FR-018 admin list and version conflicts | T018–T025 |
| FR-019–FR-021 suspension/revocation/state | T022–T025 |
| FR-022–FR-027 collision/audit/errors/shared gate | T001–T005, T008–T011, T013, T020–T028 |
| SC-001–SC-006 | T005, T011, T013, T016, T021, T025–T028 |

T001–T005 precede all protected routes. T006–T011 precede story work. US1 can
ship as the smallest customer increment after Phase 2. US3 requires the
workshop-location and FE-07 boundaries. US4/US5 require the same core model and
must not bypass the shared gate. T026–T028 follow all implemented scopes.
