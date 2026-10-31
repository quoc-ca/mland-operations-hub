# Tasks: Account Management (FE-001)

**Date created**: 2026-11-01
**Date updated**: 2026-11-01
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Input**: Design documents from `ppswbs-spec/features/001-account-management/`

**Prerequisites**: plan.md ✅ | spec.md ✅ (pass 16/16) | research.md ✅ | data-model.md ✅ | contracts/interfaces.md ✅ | design/architecture.md ✅ | design/workflows.md ✅ | design/ui-flow.md ✅

**Tests**: Not requested in spec. Only integration evidence items from quickstart.md are required before handoff; no TDD task sequence included.

**Organization**: Tasks grouped by user story. Each story is independently implementable and testable. All task IDs are globally sequential in execution order.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1–US5 maps to spec.md User Stories 1–5
- Paths are relative to the repository root

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Configuration, dependencies, and startup verification that must exist before any feature code.

- [ ] T001 Verify `spring.jpa.hibernate.ddl-auto=validate` is set and no `update`/Flyway bypass exists in `ppswbs_backend/src/main/resources/application*.properties`
- [ ] T002 Reserve the next repository-wide Flyway integer (check highest applied version) and create an append-only migration `V{N}__audit_outcome_varchar.sql` in `ppswbs_backend/src/main/resources/db/migration/` that changes `account_audit_events.outcome` from `ENUM('SUCCESS','FAILURE')` to `VARCHAR(16) NOT NULL` preserving existing rows
- [ ] T003 Verify fresh-database Flyway startup (V1 → current) and upgrade-path startup (V5 baseline → current) both succeed and Hibernate validate passes; retain evidence in `ppswbs-spec/features/001-account-management/verification.md`
- [ ] T004 [P] Create `AuditOutcome` Java enum with exactly `SUCCESS` and `FAILURE` in `ppswbs_backend/src/main/java/.../features/accountmanagement/entity/AuditOutcome.java`
- [ ] T005 [P] Confirm `ppswbs_frontend/` is a separate React + Vite project at `http://localhost:5173`; confirm `VITE_API_BASE_URL` and Firebase config are supplied via `.env.local` (not committed)

**Checkpoint**: Backend starts successfully with Hibernate validate on fresh and upgrade MySQL; `AuditOutcome` enum exists; frontend dev server runs.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core shared infrastructure that MUST be complete before ANY user story work can begin.

**⚠️ CRITICAL**: No user story implementation can start until this phase is complete.

- [ ] T006 Update `ppswbs_backend/.../entity/AccountAuditEvent.java` to map `outcome` column as `@Enumerated(EnumType.STRING)` using the `AuditOutcome` enum (replace free `String` field); verify Hibernate validate still passes after T003 migration
- [ ] T007 [P] Update `ppswbs_backend/.../service/AuditService.java` signature: `recordEvent(...)` must accept `AuditOutcome`, not an arbitrary `String`; callers outside this phase are updated in their respective user-story tasks
- [ ] T008 Implement/verify `ppswbs_backend/src/main/java/.../configuration/SecurityConfiguration.java`:
  - Exact CORS origin `http://localhost:5173`; `Access-Control-Allow-Credentials: false`
  - Allowed headers: `Authorization`, `Content-Type`, `Idempotency-Key`, `X-Correlation-ID`
  - No wildcard origin; no global CSRF disable; bearer transport CSRF scoping
  - Default-deny filter chain: only explicitly declared public routes skip auth
- [ ] T009 Implement/verify `ppswbs_backend/.../service/IdentityGateFilter.java` (WF-01):
  - Calls `TokenVerificationService.verify` to check Firebase bearer and revocation
  - On success: calls `AccountFacade.resolveActor`; attaches `CurrentAccountActor` to request context
  - On failure: returns `401` with approved error envelope; no token/UID in response
  - Never resolves Workshop Location scope; scope-independent paths unaffected by Location outage
- [ ] T010 [P] Define facade boundary types in `ppswbs_backend/.../facade/`:
  - `AccountId.java` — opaque wrapper for canonical positive-integer account ID
  - `CurrentAccountActor.java` — record: `accountId`, `role` (AccountRole), `status` (AccountStatus), `emailVerified` (boolean); no Workshop scope, no Firebase UID/token/email
  - `TargetAccountFacts.java` — FOUND value: `accountId`, `role`, `status`, `activeWorkshopLocationIds` (Set<Long>)
  - `TargetFactsOutcome.java` — sealed: `FOUND(TargetAccountFacts)` | `NOT_FOUND` | `UNAVAILABLE`
  - `InternalRosterEntry.java` — `accountId`, `displayName` (current Profile.full_name), `role`
  - `RosterOutcome.java` — sealed: `AVAILABLE(List<InternalRosterEntry>)` | `UNAVAILABLE`
- [ ] T011 [P] Confirm Spring Modulith module detection for `accountmanagement` in `ppswbs_backend/src/test/`; verify the module boundary detects `features/accountmanagement` (not just the grouping package); record evidence in `verification.md`
- [ ] T012 [P] Define shared error envelope in `ppswbs_backend/.../common/` (or equivalent shared kernel): `{ code, message, correlationId, timestamp, fieldErrors? }`; confirm no JPA entity, stack trace, secret, or PII may be included

**Checkpoint**: Backend starts, identity gate filters all protected routes, facade boundary types compile, Spring Modulith module boundary verified, AuditOutcome persisted as VARCHAR(16).

---

## Phase 3: User Story 1 — Register and use a personal account (Priority: P1) 🎯 MVP

**Goal**: A new or returning customer can register or sign in, reach Home, and bootstrap exactly one Account for their verified identity. Repeated bootstrap is idempotent.

**Independent Test**: New customer registers → one Account/Profile created → sent to Home. Returning customer bootstraps again → same Account returned; no role/status/data change. Different identity sharing the same-looking email does not merge accounts.

### Implementation for User Story 1

- [ ] T013 [P] [US1] Implement `AccountBootstrapService.bootstrap` in `ppswbs_backend/.../service/AccountBootstrapService.java`:
  - Given verified UID (from `CurrentAccountActor`): find Account by `firebase_uid`
  - If found: return existing Account (no mutation of role/status/history)
  - If not found + valid identity email (verified or unverified): create one `MEMBER`/`ACTIVE` Account AND one AccountProfile atomically; `full_name` = email local part (before `@`)
  - If not found + UID owns a pending internal record: do NOT create Member Account; evaluate activation (Phase 5/T037)
  - If not found + no valid email claim: no Account/Profile created; return safe alternative-method/recovery response
  - Same-looking email for a different UID: no merge; safe guidance
- [ ] T014 [P] [US1] Implement `AccountFacade.resolveActor` in `ppswbs_backend/.../facade/AccountFacadeImpl.java`:
  - Verifies Firebase bearer via `TokenVerificationService`; resolves `Account` by UID
  - Returns `CurrentAccountActor` (accountId, role, status, emailVerified); never includes Workshop scope
  - Returns typed failure outcome (not null) for missing/invalid/revoked bearer or missing/ineligible Account
- [ ] T015 [P] [US1] Implement `AccountController.bootstrapOrGetCurrentAccount` in `ppswbs_backend/.../controller/AccountController.java`:
  - Route: `POST /accounts/me` (bootstrap) and `GET /accounts/me` (get current)
  - Public route for `POST /accounts/me`? No — bearer is required; identity gate resolves actor first
  - Response DTO: `accountId` (opaque string), `role`, `status`, `emailVerified`; no Firebase UID, email, or token
- [ ] T016 [US1] [P] Implement `FirebaseAuthClient.ts` in `ppswbs_frontend/src/features/accountmanagement/auth/FirebaseAuthClient.ts`:
  - `register(email, password)`, `signIn(email, password)`, `signOut()`
  - `sendVerificationEmail()`, `sendPasswordReset(email)`
  - Optional: `signInWithGoogle()`, `signInWithFacebook()` — shown only when provider configured
  - Never makes role/permission decisions; no provider display name used as identity fact
- [ ] T017 [US1] Implement `useCurrentAccount.ts` in `ppswbs_frontend/src/features/accountmanagement/hooks/useCurrentAccount.ts`:
  - On Firebase auth state change: calls `POST /accounts/me` with current bearer
  - Active account → navigate to Home
  - Error states: `401` → sign-in; `403` → eligibility guidance; `503` → explicit retry; never infer old role/scope
- [ ] T018 [US1] Implement `AuthPage.tsx` in `ppswbs_frontend/src/features/accountmanagement/components/AuthPage.tsx`:
  - Email/password registration and sign-in forms; show configured provider buttons only when available
  - Duplicate-submit pending state; do not expose Account existence in error messages
  - Provider failure is distinct from invalid credentials; always explicit retry/alternative-method guidance
- [ ] T019 [US1] Update all callers of `AuditService.recordEvent` in bootstrap path to pass `AuditOutcome.SUCCESS` or `AuditOutcome.FAILURE` (not String)

**Checkpoint**: New customer can register and reach Home. Returning customer bootstraps without data mutation. Email collision does not merge accounts. Audit records use `AuditOutcome` enum.

---

## Phase 4: User Story 2 — Account verification and self-service (Priority: P1)

**Goal**: A signed-in Account owner can see verification status, verify email, manage their own profile (full name, phone), and use supported credential recovery/change flows. The business system never receives their password.

**Independent Test**: Customer with unverified email can sign in but is blocked from verification-required functions; verified customer can update profile and credential info safely.

### Implementation for User Story 2

- [ ] T020 [P] [US2] Implement `ProfileService.getProfile` and `ProfileService.saveProfile` in `ppswbs_backend/.../service/ProfileService.java`:
  - `getProfile(accountId)`: returns full_name and phone for the requesting owner
  - `saveProfile(accountId, fullName, phone)`: replaces full_name (required; 1–100 Unicode chars, no ISO control chars; outer-whitespace trimmed) and optional phone (trimmed; 7–30 chars when present; not normalized/OTP-verified/required-unique)
  - Owner check: actor must own the Account; profile save must NOT change role, status, login_email, identity, or location scope
  - Last-successful-commit wins (no version conflict on own profile save)
- [ ] T021 [P] [US2] Implement `EmailSyncService.synchronizeEmail` in `ppswbs_backend/.../service/EmailSyncService.java`:
  - Accepts current verified identity email from the active Firebase token (never a browser-supplied value)
  - Updates `Account.login_email` when the email differs from the stored value; same account, same UID
  - Audit: `SUCCESS` on update; `FAILURE` on domain error
- [ ] T022 [P] [US2] Add routes to `AccountController.java`:
  - `PUT /accounts/me/profile` — save own profile (owner only, verified-email check per FR-007 requirements)
  - `GET /accounts/me/profile` — get own profile
  - `POST /accounts/me/email-sync` — synchronize email from current identity
  - `POST /accounts/me/password-change` — delegate to Firebase; FE-001 never receives password
  - `POST /auth/verification-email` — delegate to Firebase: send verification email; declare as public? No — bearer required for verified-owner context; applies to signed-in unverified accounts
  - `POST /auth/password-reset` — public route: delegate to Firebase; MUST NOT reveal email existence
- [ ] T023 [P] [US2] Implement `useProfile.ts` in `ppswbs_frontend/src/features/accountmanagement/hooks/useProfile.ts`:
  - Load profile, form state management, save with validation
  - Retain safe unsaved full name/phone after validation/dependency errors; never retain password/token
  - Success only after server response; `409` → reconcile current values; `401/403` → safe account recovery
- [ ] T024 [US2] Implement `ProfilePage.tsx` in `ppswbs_frontend/src/features/accountmanagement/components/ProfilePage.tsx`:
  - Show verification banner (non-blocking) when email unverified; allow explicit resend
  - Field inline validation: name length, phone length, control char
  - Credential sections: password change, email change via Firebase provider flow
  - No password field value retained after submission

**Checkpoint**: Verified customer can update profile and trigger credential flows. Unverified email shows reminder but does not block Home. Verification-required functions deny with guidance.

---

## Phase 5: User Story 3 — Admin provision and maintain an internal account (Priority: P1)

**Goal**: An Admin creates Staff/Manager accounts with fixed role and valid location scope. The invited person completes their own setup. Admin can edit profile, role/location, and status safely without accidentally touching security state. Duplicate create attempts are idempotent.

**Independent Test**: Admin creates one pending Staff/Manager; resend invitation works (with cooldown); activation occurs only after intended identity signs in with verified email and valid scope; duplicate create returns original result.

### Implementation for User Story 3

- [ ] T025 [P] [US3] Implement `AdminAccountService.createInternalAccount` in `ppswbs_backend/.../service/AdminAccountService.java`:
  - Validates `role` ∈ {STAFF, MANAGER}; Staff: exactly one active `workshopLocationId`; Manager: one or more
  - Validates `workshopLocationId`(s) against authoritative Workshop Location facade (active IDs only)
  - Idempotency: stores key + normalized request digest; same key+digest → return preserved result; same key+changed digest → `409`
  - Creates `InternalAccountProvisioning` record; Firebase identity/action-link outside DB transaction (reconcilable)
  - Commits: one `PENDING_SETUP` Account, AccountLocationAssignment(s), `AccountAuditEvent (SUCCESS)`, `NotificationIntent`
  - Raw Firebase action link MUST NOT be in audit, log, or NotificationIntent
- [ ] T026 [P] [US3] Implement `AdminAccountService.resendInvitation`:
  - Pending-only; five-minute resend cooldown enforced; fresh Firebase setup link; no raw link retained
  - Audit `SUCCESS`/`FAILURE`; NotificationIntent created post-commit
- [ ] T027 [P] [US3] Implement PENDING_SETUP → ACTIVE activation in `AccountBootstrapService.bootstrap` (T013 extension):
  - Condition: intended UID signs in + email verified + effective Workshop scope valid for the assigned role rule
  - Activate the pending Account; never create/replace a Member Account; audit `ACTIVATION SUCCESS`
- [ ] T028 [P] [US3] Implement `AdminAccountService.updateInternalProfile`, `assignRoleLocation`, and admin profile/role/location/status edit endpoints:
  - Each action is a separate command with expected `version` (conflict-safe)
  - `updateInternalProfile(accountId, fullName, phone, version)`: Staff/Manager only; does not change role/status/email/identity/scope
  - `assignRoleLocation(accountId, role, locationIds, version)`: validates new location scope against Workshop Location facade
  - Version mismatch → `409`; attempt audit `FAILURE`; UI must reload before retry
- [ ] T029 [P] [US3] Add routes to `AdminAccountController.java`:
  - `POST /admin/accounts` — create internal account (ADMIN only, Idempotency-Key header required)
  - `GET /admin/accounts/{accountId}` — account detail (ADMIN only)
  - `POST /admin/accounts/{accountId}/resend-invitation` — resend (ADMIN only; pending only; cooldown enforced)
  - `PUT /admin/accounts/{accountId}/profile` — admin edit profile (ADMIN only; Staff/Manager targets only)
  - `PUT /admin/accounts/{accountId}/role-location` — admin assign role/location (ADMIN only; version required)
- [ ] T030 [P] [US3] Implement `AccountFacade.findTargetFacts` and `findEligibleRoster` in `AccountFacadeImpl.java` (T010 types already defined):
  - `findTargetFacts(AccountId)`: read-only; resolves Account + effective `activeWorkshopLocationIds`; typed outcome; no audit event
  - `findEligibleRoster(workshopLocationId, role)`: read-only; returns ACTIVE Accounts with effective scope; display name = Profile.full_name; no email/phone/UID/lifecycle; no audit event
  - Both: if Account or Workshop Location scope cannot be verified → `UNAVAILABLE`
- [ ] T031 [US3] Implement `InternalAccountCreatePage.tsx` in `ppswbs_frontend/src/features/accountmanagement/components/InternalAccountCreatePage.tsx`:
  - Role selector (STAFF/MANAGER only); location picker from Workshop Location source; email input
  - Generate and attach `Idempotency-Key` header on submit
  - Pending setup ≠ active access (UI must reflect PENDING_SETUP state)
  - Never display raw Firebase action link; repeat click/retry preserves one provisioning request
- [ ] T032 [US3] Implement `useAdminAccounts.ts` (create/resend/detail portions) in `ppswbs_frontend/src/features/accountmanagement/hooks/useAdminAccounts.ts`:
  - `create`, `resend`, `loadDetail`; retain safe form data on `503`; reload on `409`

**Checkpoint**: Admin creates one pending internal account; resend works with cooldown; intended person activates; duplicate create is idempotent; profile/role/location edit is version-safe.

---

## Phase 6: User Story 4 — Admin protect the business from an unsafe account (Priority: P1)

**Goal**: An Admin can suspend an allowed account immediately (independent of delivery or revocation completion) and restore it only after sessions are invalidated. Restoration requires a fresh sign-in.

**Independent Test**: Suspended account's next protected request is denied immediately. Restoration is blocked until Firebase revocation completes. Restored user must sign in again.

### Implementation for User Story 4

- [ ] T033 [P] [US4] Implement `SuspensionService.suspend` in `ppswbs_backend/.../service/SuspensionService.java`:
  - Targets: MEMBER, STAFF, or MANAGER only; Admin accounts cannot be suspended via this feature
  - `suspend(adminActorId, targetAccountId, reason, version)`:
    - Validates current target account status (`ACTIVE` or `PENDING_SETUP`) and version
    - Sets `status = SUSPENDED`, `resume_status = priorStatus`, `revocation_pending = true`
    - Commits in one transaction: Account update + `AccountAuditEvent(SUSPEND, SUCCESS, reason in safe metadata, no full_name/phone/token)` + `NotificationIntent`
    - Protected access is denied immediately via WF-01 identity gate (status check); does not depend on Firebase revocation completing
    - Firebase revocation is requested/reconciled outside the DB transaction as durable external progress
- [ ] T034 [P] [US4] Implement `SuspensionService.restore` in `SuspensionService.java`:
  - `restore(adminActorId, targetAccountId, version)`:
    - Only allowed when `revocation_pending = false` (revocation complete)
    - Sets `status = resume_status`; clears `resume_status`; requires fresh sign-in
    - Commits: Account update + `AccountAuditEvent(RESTORE, SUCCESS)` + `NotificationIntent`
    - Attempt before revocation complete → `409` (not a bypass)
- [ ] T035 [P] [US4] Implement `SuspensionService.reconcileRevocation` (durable reconciliation):
  - Retryable/idempotent update of Firebase revocation progress; sets `revocation_pending = false` on confirmed completion
  - Failure is durable reconciliation; a pending revocation does not silently make a suspended account usable
- [ ] T036 [P] [US4] Add routes to `AdminAccountController.java`:
  - `PUT /admin/accounts/{accountId}/status` — suspend or restore (ADMIN only; version required; reason required for suspend)
- [ ] T037 [US4] Implement `AdminAccountDetailPage.tsx` suspension/restoration UI in `ppswbs_frontend/src/features/accountmanagement/components/AdminAccountDetailPage.tsx`:
  - Explain impact; show current committed status/effective scope
  - Suspension requires non-empty reason; restoration shows revocation-pending state when blocked
  - Location deactivation does not claim role/lifecycle change in UI
  - Version conflict → reload before reapply; `503` → keep safe form data for explicit retry

**Checkpoint**: Suspended account denied on next protected request without waiting for Firebase or email. Restoration blocked until revocation complete. Restored user signs in again.

---

## Phase 7: User Story 5 — Understand fixed access without configuring permissions (Priority: P2)

**Goal**: An Admin can search/filter accounts and view the fixed role catalogue. Admin cannot create new roles, edit permissions, or manage Admin accounts.

**Independent Test**: Admin can filter account list and view static role descriptions. Every attempt to configure dynamic permissions, assign unsupported role, or manage Admin accounts is rejected.

### Implementation for User Story 5

- [ ] T038 [P] [US5] Implement `AdminAccountService.listAccounts` and `AdminAccountService.getAccountDetail` in `AdminAccountService.java`:
  - `listAccounts(email?, role?, status?, page, size)`: paginated; ordered by recent change/creation first
  - Excludes credentials, Firebase UIDs, and unrelated business data from list/detail responses
  - Account ID in response as opaque positive-integer string
  - `getAccountDetail(accountId)`: returns safe profile summary, role, status, assignment summary; no credential/UID
- [ ] T039 [P] [US5] Implement `RoleCatalogueService.listRoles` and the fixed role definitions:
  - Returns exactly MEMBER, STAFF, MANAGER, ADMIN with read-only descriptions of scope and constraints
  - No permission/role editing endpoints; no dynamic RBAC; no multiple-role assignment
- [ ] T040 [P] [US5] Add routes to `AdminAccountController.java` and `RoleCatalogueController.java`:
  - `GET /admin/accounts` — list with filters and pagination (ADMIN only)
  - `GET /roles` — fixed role catalogue (ADMIN only)
- [ ] T041 [P] [US5] Implement `AdminAccountListPage.tsx` in `ppswbs_frontend/src/features/accountmanagement/components/AdminAccountListPage.tsx`:
  - Search by email or full name; filter by role and status; paginated results
  - No credentials, provider identifiers, or unrelated business records shown
  - ADMIN accounts excluded from suspension/edit actions
- [ ] T042 [P] [US5] Implement `RoleCataloguePage.tsx` in `ppswbs_frontend/src/features/accountmanagement/components/RoleCataloguePage.tsx`:
  - Read-only display of fixed role descriptions; loading/error state
  - No permission/role editing controls rendered
- [ ] T043 [US5] Implement `useAdminAccounts.ts` (list/search portions) in `ppswbs_frontend/src/features/accountmanagement/hooks/useAdminAccounts.ts`:
  - `search(filters, page)`, `loadDetail(accountId)`
  - `updateProfile`, `assignRoleLocation`, `changeStatus` with version-safe retry on `409`

**Checkpoint**: Admin can filter and paginate account list. Fixed role catalogue displays without edit controls. Admin account management scope is enforced.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Items that span multiple user stories or require all previous phases to be complete.

- [ ] T044 [P] Implement `AccountExceptionHandler.java` in `ppswbs_backend/.../controller/AccountExceptionHandler.java`:
  - Maps FE-001 validation/business exceptions to approved error envelope (`code`, `message`, `correlationId`, `timestamp`, optional `fieldErrors`)
  - No JPA entity, stack trace, Firebase UID, raw token, or unauthorized target detail in any response
  - FE-003 business outcomes are NOT converted to 500 by this handler
- [ ] T045 [P] Validate all `AccountId` representations at controller and facade boundaries:
  - REST/UI input: reject non-positive, padded, signed, blank, provider-UID, or alternate-ID representations → `400`
  - Java facade: all boundary crossings use `AccountId` type; no raw `Long` or `String` at facade boundary
- [ ] T046 [P] Wire `AccountManagementApiClient.ts` in `ppswbs_frontend/src/features/accountmanagement/api/AccountManagementApiClient.ts`:
  - Covers all approved routes from plan.md; attaches Firebase bearer via authenticated request client
  - `Idempotency-Key` and `X-Correlation-ID` headers included where required
  - No raw token/UID/password stored or logged by the client
- [ ] T047 [P] Accessibility baseline: run WCAG 2.2 AA scan against all FE-001 screens (AuthPage, ProfilePage, AdminAccountListPage, AdminAccountDetailPage, InternalAccountCreatePage, RoleCataloguePage) on Chrome, Edge, Firefox (2 latest desktop); record evidence in `verification.md`
- [ ] T048 Revise `verification.md` to document acceptance evidence for all quickstart scenarios V-01 through V-09; mark each scenario's evidence status
- [ ] T049 Execute all quickstart scenarios from `quickstart.md` locally; annotate evidence (screenshots or test output) and append to `verification.md`
- [ ] T050 [P] Final review: confirm no committed credential defaults, no raw SQL in feature code, no cross-module entity/DAO import outside facade boundary, no `ddl-auto=update` anywhere, no wildcard CORS origin

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1: Setup              → No dependencies; start immediately
Phase 2: Foundational       → Depends on Phase 1; BLOCKS all user story phases
Phase 3: User Story 1 (P1)  → Depends on Phase 2; no dependency on US2–US5
Phase 4: User Story 2 (P1)  → Depends on Phase 2; no dependency on US1/US3–US5
Phase 5: User Story 3 (P1)  → Depends on Phase 2 + T013/T014 (AccountFacade.resolveActor for admin auth)
Phase 6: User Story 4 (P1)  → Depends on Phase 2 + T013/T014 + T029 (AdminAccountController base)
Phase 7: User Story 5 (P2)  → Depends on Phase 2
Phase 8: Polish             → Depends on all user story phases complete
```

### User Story Dependencies

- **US1 (P1)**: Can start after Phase 2 — foundational bootstrap, self-service, identity gate
- **US2 (P1)**: Can start after Phase 2 — self-service profile/email; independent of US1 implementation (uses same entity/service layer)
- **US3 (P1)**: Can start after Phase 2; T030 (facade) can start after T010 (boundary types)
- **US4 (P1)**: Can start after Phase 2; suspension/restore service is standalone; T027 (activation) shares bootstrap from T013
- **US5 (P2)**: Can start after Phase 2; list/catalogue are read-only and independent

### Within Each User Story

- Backend service/entity changes before controller routes
- Controller routes before frontend hook consumption
- Foundation types (T010) before facade implementation (T030)
- Audit callers (T019) updated after T007 (AuditService signature)

### Parallel Opportunities

All `[P]`-marked tasks within the same phase can run in parallel (different files, no incomplete dependencies). Once Phase 2 completes, US1–US5 backend services can be developed in parallel across team members.

---

## Parallel Example: Phase 5 (User Story 3)

```
Parallel group A (all after T010 types defined):
  T025 AdminAccountService.createInternalAccount
  T026 AdminAccountService.resendInvitation
  T030 AccountFacade.findTargetFacts + findEligibleRoster

Parallel group B (after T025–T027 complete):
  T028 AdminAccountService profile/role/location edit
  T029 AdminAccountController routes

Parallel group C (frontend, after T029):
  T031 InternalAccountCreatePage.tsx
  T032 useAdminAccounts.ts (create/resend)
```

---

## Implementation Strategy

### MVP (User Stories 1–4, all P1)

1. **Phase 1** — Schema migration + AuditOutcome + startup verification
2. **Phase 2** — Identity gate, CORS, facade types, Spring Modulith boundary
3. **Phase 3** — Bootstrap + sign-in → customer can access Home ✅
4. **Phase 4** — Self-service profile/email/credential flows ✅
5. **Phase 5** — Internal account provisioning + activation ✅
6. **Phase 6** — Suspension/restoration ✅
7. **STOP and VALIDATE**: All quickstart scenarios V-01 through V-08 pass; evidence recorded

### Full Delivery

8. **Phase 7** — Admin list/filter + role catalogue (US5, P2)
9. **Phase 8** — Polish, accessibility baseline, full verification evidence

### Parallel Team Strategy (if staffed)

After Phase 2 completes:
- Developer A: US1 (bootstrap) + US2 (self-service)
- Developer B: US3 (internal provisioning) + US4 (suspension)
- Developer C: US5 (list/catalogue) + Phase 8 (accessibility/polish)

---

## Notes

- `[P]` tasks operate on different files and have no dependency on other incomplete tasks in the same group
- Story labels map directly to spec.md User Stories 1–5
- Each user story phase is independently completable and testable; validate each before moving to the next
- AuditOutcome migration (T002–T003) is a HARD prerequisite — any MySQL integration test will fail without it
- `findTargetFacts` and `findEligibleRoster` (T030) are Java-only module-boundary methods; no REST endpoint is created for them
- OpenAPI regeneration is a required pre-handoff step (see plan.md open blockers D02/D06); its tasks are tracked in `verification.md`, not here
- All quickstart scenarios in `quickstart.md` must be run and evidence retained before Tech Lead handoff (T048–T049)

**Total tasks**: 50
**By story**: Setup 5 | Foundational 7 | US1 7 | US2 5 | US3 8 | US4 5 | US5 6 | Polish 7
**Parallel opportunities**: 32 tasks marked [P]
**MVP scope**: Phases 1–6 (T001–T042, excluding T038–T043 US5 tasks)
**Suggested MVP**: Complete US1 (Phase 3) alone first → validate independently → proceed to US2, US3, US4
