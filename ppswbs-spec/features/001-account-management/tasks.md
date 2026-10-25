# Tasks: 001-account-management

**Date created**: 2026-10-25
**Date updated**: 2026-10-25
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Input**: Design documents from `ppswbs-spec/features/001-account-management/`

**Prerequisites**: plan.md (v2026-10-25), spec.md (v2026-10-25), research.md

**Supersession note**: This file replaces tasks.md dated 2026-10-19 which described the legacy Member Profile/Thymeleaf design. Do not use those tasks for implementation.

> **Open items that block specific tasks are noted inline.** Tasks depending on [G01]–[G05] from plan.md cannot be marked DONE until the corresponding gap is resolved. They are included here so implementers know the full scope.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US5)
- File paths use the layout from plan.md

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and module registration.

- [ ] T001 Create backend module descriptor `AccountManagementModule.java` in `backend/src/main/java/vn/mland/ppswbs/features/accountmanagement/` with `@ApplicationModule` annotation to guarantee Spring Modulith detection
- [ ] T002 Create enum `AccountRole` (MEMBER, STAFF, MANAGER, ADMIN) in `backend/.../accountmanagement/entity/enums/AccountRole.java`
- [ ] T003 [P] Create enum `AccountStatus` (ACTIVE, PENDING_SETUP, SUSPENDED) in `backend/.../accountmanagement/entity/enums/AccountStatus.java`
- [ ] T004 [P] Create frontend feature directory `frontend/src/features/account/` with `services/accountApi.ts` stub exporting typed fetch wrappers (no business logic yet)
- [ ] T005 Run `ApplicationModules.verify()` in a test and confirm `accountmanagement` is detected as a distinct module (not collapsed into `features`); record result

**Checkpoint**: Module detected. Enum types defined. Frontend stub in place.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core entities, migration, and token verification — MUST complete before any User Story.

**CRITICAL**: No User Story work begins until this phase is complete.

- [ ] T006 Write Flyway migration `V5__account_management.sql` (append-only): tables `accounts`, `account_profiles`, `account_location_assignments`, `internal_account_provisionings`, `account_audit_events` with columns as defined in data-model.md. V1–V4 checksums must remain unchanged. **Blocked on [G05] role-code mapping** — use MEMBER/STAFF/MANAGER/ADMIN enum values; note TBD reconciliation with existing `members` table.
- [ ] T007 [P] Create JPA entity `Account.java` in `backend/.../entity/Account.java`: fields `id` (BIGINT PK), `firebaseUid` (UNIQUE NOT NULL), `loginEmail`, `role` (AccountRole), `status` (AccountStatus), `version` (optimistic lock `@Version`), `sessionRevocationConfirmedAt` (nullable Instant)
- [ ] T008 [P] Create JPA entity `AccountProfile.java`: `accountId` as PK/FK to Account (1:1), `fullName` VARCHAR(400) NOT NULL, `phone` VARCHAR(30) nullable, `updatedAt`
- [ ] T009 [P] Create JPA entity `AccountLocationAssignment.java`: `id`, `accountId` FK, `locationId` BIGINT (no FK to external table), `assignedAt`, `active` boolean
- [ ] T010 [P] Create JPA entity `InternalAccountProvisioning.java`: `id`, `accountId` FK, `idempotencyKey` UNIQUE, `status`, `invitationLastSentAt`, `createdByAdminId`, `firebaseSetupLinkIssuedAt`
- [ ] T011 [P] Create JPA entity `AccountAuditEvent.java`: `id`, `actorAccountId`, `targetAccountId`, `action` (string enum), `outcome`, `correlationId`, `occurredAt`, `metadata` JSON — no full name, phone, raw tokens, or action links
- [ ] T012 Create `TokenVerificationService.java` in `backend/.../service/`: wraps Firebase Admin SDK `FirebaseAuth.getInstance().verifyIdToken(token, true /* checkRevoked */)`. Returns a safe `VerifiedIdentity` record (uid, email, emailVerified, authTime). Throws typed `TokenRevokedException` and `TokenExpiredException`. Never logs the raw token.
- [ ] T013 [P] Create Spring Data repositories: `AccountRepository` (findByFirebaseUid, findById), `AccountProfileRepository` (findByAccountId), `AccountLocationAssignmentRepository` (findByAccountIdAndActive), `InternalAccountProvisioningRepository` (findByIdempotencyKey), `AccountAuditEventRepository` in `backend/.../dao/`
- [ ] T014 [P] Create `AuditService.java` in `backend/.../service/`: records `AccountAuditEvent` atomically within the calling transaction. Parameters: actorId, targetId, action enum, outcome, correlationId, optional metadata map (must strip prohibited fields before persisting).
- [ ] T015 Create `AccountFacade.java` in `backend/.../facade/`: public interface methods `resolveAccount(String uid): AccountSummary`, `getAccountStatus(Long accountId): AccountStatus`, `getLocationScope(Long accountId): List<Long>`. Mark with `@ApplicationModule` or equivalent to expose as the only public boundary. Internal implementations delegated to services.

**Checkpoint**: Migration runs. All entities load. Token verification tested against Firebase Auth Emulator. Module boundary verified.

---

## Phase 3: User Story 1 — Register and use a personal account (Priority: P1) MVP

**Goal**: A customer can register or sign in via any allowed method, bootstrap exactly one account by UID, and reach Home. Repeated bootstrap is idempotent.

**Independent Test**: Two sequential `PUT /api/v1/accounts/me` calls with the same Firebase ID token produce one account row; DB count = 1. A different UID produces a separate account. Suspended account returns 403.

### Implementation for User Story 1

- [ ] T016 [US1] Create `AccountBootstrapService.java` in `backend/.../service/`: method `bootstrapAccount(VerifiedIdentity identity): Account`. Logic: find by UID → if absent create with role=MEMBER, status=ACTIVE, loginEmail from token; if SUSPENDED throw `AccountSuspendedException`; if PENDING_SETUP check internal provisioning record and complete activation if conditions met (see US3). Uses `@Transactional` with UID unique-constraint conflict retry outside failed transaction (never by email). Calls `AuditService.record(...)` after create.
- [ ] T017 [US1] Create request/response DTOs: `AccountBootstrapRequest.java` (empty body), `AccountResponse.java` (id, role, status, loginEmail, emailVerified, revocationStatus) in `backend/.../dto/`
- [ ] T018 [US1] Create `AccountController.java` in `backend/.../controller/`: `PUT /api/v1/accounts/me` — extract Bearer token, call `TokenVerificationService.verify()`, call `AccountBootstrapService.bootstrapAccount()`, return `AccountResponse`. Return 200 for both new and existing accounts. Error mapping: ACCOUNT_SUSPENDED → 403, invalid token → 401.
- [ ] T019 [US1] Create `RegisterPage.tsx` and `SignInPage.tsx` in `frontend/src/features/account/pages/`: Firebase Web SDK sign-in flows (email/password; Google/Facebook only when configured). On success: send `PUT /api/v1/accounts/me` with Bearer token; on 200 navigate to `/`; on error show typed guidance (no raw error messages). Lock submit button during processing. Never send password to backend.
- [ ] T020 [US1] Create `useCurrentAccount.ts` hook in `frontend/src/features/account/hooks/`: manages Firebase auth state, token refresh, and `AccountResponse` cache. Exposes `account`, `loading`, `error`, `signOut()`.
- [ ] T021 [US1] Add `EmailVerificationBanner.tsx` component in `frontend/src/features/account/components/`: non-blocking banner shown on Home when `emailVerified=false`; includes "Resend verification email" button calling Firebase SDK `sendEmailVerification(user)`.

**Checkpoint**: US1 independently testable. New customer can register, return customer can sign in, both reach Home. Duplicate bootstrap returns same account.

---

## Phase 4: User Story 2 — Account verification and self-service (Priority: P1)

**Goal**: A verified account owner can update their profile (full name, phone); an unverified account can sign in and see a reminder but cannot use profile functions. Email change is synced from Firebase.

**Independent Test**: `PUT /accounts/me/profile` with unverified token returns 403 EMAIL_NOT_VERIFIED. With verified token and valid name (1–100 Unicode chars, no ISO control chars) returns 200 with saved values. Role, status, email, identity unchanged.

### Implementation for User Story 2

- [ ] T022 [US2] Create `ProfileService.java` in `backend/.../service/`: methods `getProfile(Long accountId): AccountProfile` (returns null fields if absent; never creates on GET), `updateProfile(Long accountId, String fullName, String phone): AccountProfile` (pessimistic write lock on Account; validates fullName: required, 1–100 Unicode code points, no ISO control chars; phone: optional, 7–30 chars after trim; calls AuditService). Fails cleanly on validation; does not change role/status/email.
- [ ] T023 [US2] Create `EmailSyncService.java` in `backend/.../service/`: method `syncEmail(Long accountId, VerifiedIdentity identity): Account`. Updates `accounts.login_email` only when identity email is verified and matches the same UID. Never accepts browser-supplied email. Calls AuditService.
- [ ] T024 [US2] Create request/response DTOs: `ProfileUpdateRequest.java` (`fullName` required; `phone` optional), `EmailSyncRequest.java` (no body) in `backend/.../dto/request/`
- [ ] T025 [US2] Add endpoints to `AccountController.java`: `GET /api/v1/accounts/me` (return account + profile; no verified email required), `PUT /api/v1/accounts/me/profile` (require verified email in token; call ProfileService), `POST /api/v1/accounts/me/email-sync` (require verified email; call EmailSyncService), `DELETE /api/v1/accounts/me/session` (sign-out; clear any server state)
- [ ] T026 [US2] Create `ProfilePage.tsx` in `frontend/src/features/account/pages/`: fetch and display profile; `ProfileForm.tsx` component with fullName and phone fields. On save: validate client-side then `PUT /api/v1/accounts/me/profile`. Preserve form data on error. Show EMAIL_NOT_VERIFIED guidance if backend rejects. Do not send UID/email/role/status in form body.
- [ ] T027 [US2] Create `ChangeEmailPage.tsx` in `frontend/src/features/account/pages/`: re-authenticate with current password, call Firebase `verifyBeforeUpdateEmail(user, newEmail)`, then on return call `POST /api/v1/accounts/me/email-sync`. Show status at each step. Handle Firebase-already-changed-but-backend-failed recovery path (retry sync with current token; do not restart email change flow).

**Checkpoint**: US2 independently testable. Verified user can update profile; unverified is denied profile edits. Email sync from Firebase token works.

---

## Phase 5: User Story 3 — Admin provision and maintain internal account (Priority: P1)

**Goal**: An Admin creates Staff/Manager accounts with valid role and location scope. The invited person completes setup before gaining internal access. Duplicate create is idempotent.

**Independent Test**: `POST /admin/accounts/internal` twice with same `Idempotency-Key` → same account ID, DB count = 1. Different key → new account. Same key with changed payload → 409. A PENDING_SETUP account bootstrap completes only when UID matches, email verified, location scope still active.

### Implementation for User Story 3

- [ ] T028 [US3] Create `AdminAccountService.java` in `backend/.../service/`: method `createInternalAccount(AdminId, idempotencyKey, fullName, email, role, locationIds): InternalAccountCreateResult`. Logic: check idempotency key; validate role is STAFF or MANAGER; validate locationIds against FE-03 facade **[DEP-01 / G03]** (if FE-03 absent, log warning and reject); create Firebase identity via Admin SDK; create Account (PENDING_SETUP) and InternalAccountProvisioning atomically; call FE-07 notification request **[DEP-02 / G04]** (if FE-07 absent, log warning; do not fail commit); call AuditService. Handles: Firebase create fails → do not commit DB; DB fails after Firebase → record orphan for recovery.
- [ ] T029 [US3] Add `AccountBootstrapService.bootstrapAccount()` activation path (from T016): when UID belongs to an InternalAccountProvisioning in PENDING_SETUP status, and email is verified, and locationScope still active → set status=ACTIVE, clear provisioning pending flag, call AuditService. Must not create a Member account or overwrite the internal account's role.
- [ ] T030 [US3] Create `InternalAccountCreateRequest.java` (fullName, email, role, locationIds), `InternalAccountResponse.java` (id, role, status, locationIds, idempotencyKey, invitationSentAt) in `backend/.../dto/`
- [ ] T031 [US3] Create `AdminAccountController.java` in `backend/.../controller/`: `POST /api/v1/admin/accounts/internal` (extract `Idempotency-Key` header; required; call AdminAccountService); `POST /api/v1/admin/accounts/{id}/invitation/resend` (check 5-min cooldown via `invitationLastSentAt`; generate new Firebase setup link; do not store raw link; record via FE-07 **[DEP-02]**; update `invitationLastSentAt`); `PUT /api/v1/admin/accounts/{id}/profile` (fullName/phone only; call AdminAccountService.updateInternalProfile; AuditService); `GET /api/v1/admin/accounts` (paginated list with search/filter by name/email/role/status; no credentials or provider IDs in response); `GET /api/v1/admin/accounts/{id}` (detail with allowed operations per current status)
- [ ] T032 [US3] Create `CreateInternalAccountPage.tsx`, `AccountListPage.tsx`, `AccountDetailPage.tsx` in `frontend/src/features/account/pages/admin/`: Admin UI for creating and viewing internal accounts. Include `Idempotency-Key` generation (UUID per submit intent, not per page load). Show invitation status and resend button with cooldown feedback.

**Checkpoint**: US3 independently testable. Admin can create exactly one internal account per intent. Invited user can activate on bootstrap. Duplicate create is safe.

---

## Phase 6: User Story 4 — Protect the business from an unsafe account (Priority: P1)

**Goal**: Admin can suspend any Member/Staff/Manager account immediately. Suspension takes effect on backend before Firebase session invalidation completes. Restoration is blocked until session invalidation is confirmed.

**Independent Test**: Suspend account → subsequent `GET /accounts/me` with old token returns 403. `POST /restore` while `sessionRevocationConfirmedAt` is null returns 409. After revocation confirmed, restore succeeds and user must re-login.

### Implementation for User Story 4

- [ ] T033 [US4] Create `SuspensionService.java` in `backend/.../service/`: method `suspendAccount(adminId, targetId, reason, version): SuspendResult`. Logic: validate target is MEMBER/STAFF/MANAGER (not ADMIN); optimistic lock check using `version`; set status=SUSPENDED atomically; call `FirebaseAuth.revokeRefreshTokens(uid)` — record attempt (success or failure) in `accounts.session_revocation_confirmed_at`; call FE-07 ACCOUNT_LOCKED notification **[DEP-02]**; call AuditService with reason (reason stored; name/phone not stored). Method `restoreAccount(adminId, targetId, version): RestoreResult`: check `session_revocation_confirmed_at` IS NOT NULL (else 409 REVOCATION_PENDING); restore to pre-suspension eligible state (ACTIVE or PENDING_SETUP); call FE-07 ACCOUNT_UNLOCKED **[DEP-02]**; call AuditService.
- [ ] T034 [US4] Add endpoints to `AdminAccountController.java`: `POST /api/v1/admin/accounts/{id}/suspend` (`AccountSuspendRequest` with reason required, version for optimistic lock); `POST /api/v1/admin/accounts/{id}/restore` (version for optimistic lock; returns 409 REVOCATION_PENDING if not confirmed)
- [ ] T035 [US4] Update `TokenVerificationService.java` to check `accounts.status = SUSPENDED` from DB after token verification succeeds (before any service method). Return 403 ACCOUNT_SUSPENDED regardless of whether `revokeRefreshTokens` has propagated to Firebase yet. This is the immediate enforcement mechanism.
- [ ] T036 [US4] Create `SuspendRestoreDialog.tsx` in `frontend/src/features/account/components/`: Admin UI with reason input for suspend; restoration button disabled with explanation when `revocationStatus != CONFIRMED`. Reload account detail after action to reflect updated status.

**Checkpoint**: US4 independently testable. Suspension is immediate. Restoration gate enforced. Firebase invalidation tracked.

---

## Phase 7: User Story 5 — Understand fixed access without configuring permissions (Priority: P2)

**Goal**: Admin can search/filter accounts and view the fixed role catalogue. No dynamic permission configuration.

**Independent Test**: `GET /admin/roles` returns fixed role list; no create/edit endpoint exists. `GET /admin/accounts` with role/status filter returns paginated results without credentials.

### Implementation for User Story 5

- [ ] T037 [US5] Create `RoleCatalogueController.java` in `backend/.../controller/`: `GET /api/v1/admin/roles` — returns static list of role definitions (MEMBER, STAFF, MANAGER, ADMIN with description of each). No POST/PUT/DELETE endpoints on roles.
- [ ] T038 [US5] Create `RoleCatalogueResponse.java` in `backend/.../dto/response/`: list of role objects with `role`, `displayName`, `description` (no permission matrix editing).
- [ ] T039 [US5] Update `GET /api/v1/admin/accounts` (from T031) to include search by email or full name, filter by role and status, paginate (page/size), order by `updatedAt DESC NULLS LAST`. Response must exclude `firebaseUid`, `loginEmail` hashed equivalent, any token, or unrelated business records.
- [ ] T040 [P] [US5] Add `RoleLocationAssignForm.tsx` component in `frontend/src/features/account/components/`: Admin form to set role (STAFF or MANAGER) and locations. For STAFF: exactly one location (dropdown). For Manager: multi-select with at least one. Confirm before submitting. Wire to `PUT /api/v1/admin/accounts/{id}/role-location`.
- [ ] T041 [US5] Add `PUT /api/v1/admin/accounts/{id}/role-location` to `AdminAccountController.java`: `RoleLocationAssignRequest` (role, locationIds, version); validate role is STAFF/MANAGER (not ADMIN/MEMBER); validate locationIds against FE-03 **[DEP-01 / G03]**; optimistic lock; call AdminAccountService.assignRoleLocation; call AuditService; call FE-07 ACCOUNT_ROLE_CHANGED notification **[DEP-02]**.
- [ ] T042 [US5] Create `RoleCataloguePage.tsx` in `frontend/src/features/account/pages/admin/`: displays fixed role descriptions. No edit controls. Accessible from Admin sidebar.

**Checkpoint**: US5 independently testable. Admin can search/filter accounts. Role catalogue is read-only. Location assignment validates constraints.

---

## Phase 8: Polish and Cross-Cutting Concerns

**Purpose**: Security hardening, error contracts, observability, and final verification.

- [ ] T043 [P] Implement global error response mapping in `accountmanagement` controllers: typed error codes (ACCOUNT_SUSPENDED, EMAIL_NOT_VERIFIED, REVOCATION_PENDING, OPTIMISTIC_LOCK_CONFLICT, IDEMPOTENCY_KEY_CONFLICT, DUPLICATE_FIREBASE_UID, LOCATION_VALIDATION_FAILED, ROLE_ASSIGNMENT_FORBIDDEN, RECORD_NOT_FOUND, INVALID_INPUT); no stack traces, no entity internals, no PII; include `correlationId` in every error response.
- [ ] T044 [P] Add Bean Validation annotations to all request DTOs: `@NotBlank` on required strings; custom `@ValidFullName` constraint (1–100 Unicode code points, no ISO control chars) on `fullName` fields; `@Size(min=7, max=30)` on optional phone (when present); `@NotEmpty` on locationIds.
- [ ] T045 [P] Add Spring Security configuration for the `accountmanagement` module: require Bearer token on all `/api/v1/accounts/**` and `/api/v1/admin/**` routes; ADMIN role required for `/admin/**`; no CSRF for stateless API (CSRF not applicable to Bearer-token REST); no form login, no Thymeleaf login page.
- [ ] T046 Run full `ApplicationModules.verify()` to confirm no circular dependencies and that only `AccountFacade` is exported from the module. Record output.
- [ ] T047 Run Flyway migration on empty Testcontainers MySQL: verify V5 applies cleanly and V1–V4 checksums are unchanged. Record output.
- [ ] T048 Run Firebase Auth Emulator smoke tests: bootstrap with new UID creates account; bootstrap with existing UID returns same account; suspended account token returns 403; `revokeRefreshTokens` called on suspension and result recorded.
- [ ] T049 [P] Implement frontend error handling in `accountApi.ts`: map typed backend error codes to user-friendly messages (bilingual if required by UX spec); never display raw stack trace or backend error object; preserve form data on validation errors.
- [ ] T050 [P] Verify no raw PII, tokens, or action links appear in backend logs during all test scenarios. Add structured logging with `correlationId` only.
- [ ] T051 Update `quickstart.md` with the 8 verification scenarios from plan.md and results from T046–T048 runs. Mark as verification evidence, not design artifact.
- [ ] T052 Update `data-model.md` with finalized column constraints, indexes, and invariants from V5 migration (post T006 implementation). Mark as Phase 1 output now complete.
- [ ] T053 [P] Document Admin bootstrap operations process (separate from this feature scope): link from plan.md §DEP-05. Document expected Firebase Admin SDK commands to create initial ADMIN identity.
- [ ] T054 Review all tasks for open items [G01]–[G05] compliance; update task status and notes once gaps are resolved by decision owners.

---

## Dependencies and Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — BLOCKS all User Stories
- **Phase 3 (US1)**: Depends on Phase 2
- **Phase 4 (US2)**: Depends on Phase 2; can run in parallel with US3/US4 after Phase 2
- **Phase 5 (US3)**: Depends on Phase 2 and US1 bootstrap activation path (T029 extends T016)
- **Phase 6 (US4)**: Depends on Phase 2 and T035 (which extends T012 TokenVerificationService)
- **Phase 7 (US5)**: Depends on Phase 2; extends US3 admin list/filter (T039 extends T031)
- **Phase 8 (Polish)**: Depends on all desired User Stories being complete

### Within Each User Story

- Entities/repositories → Services → Controllers → Frontend
- T006 (migration) before any entity can be tested against MySQL
- T012 (TokenVerificationService) before any controller endpoint
- T015 (AccountFacade) before any cross-feature consumer can call account module

### Dependency Map

```text
T001 → T005 (verify module detection)
T002, T003 → T007, T008, T009, T010, T011 (enums before entities)
T006 → all entities (migration must run before DB-backed tests)
T012 → T016, T018, T025, T031, T034, T035 (token verification used everywhere)
T013 → T016, T022, T028, T033 (repositories used by services)
T014 → T016, T022, T028, T033 (AuditService used by all mutation services)
T015 → [cross-feature consumers only]
T016 → T029 (activation extends bootstrap)
T031 → T039 (US5 extends US3 list endpoint)
```

### Parallel Opportunities

- T002, T003 (enums) — parallel
- T007–T011 (entities) — all parallel after T002/T003
- T013 (repositories) — parallel after T007–T011
- T016, T022, T028, T033 (services) — parallel after T013 and T014
- T017, T023, T024, T030 (DTOs) — parallel with services
- T019, T026, T027, T032 (frontend pages) — parallel after corresponding backend endpoints
- T043–T045, T049, T050, T053 (polish) — parallel in Phase 8

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T005)
2. Complete Phase 2: Foundational (T006–T015) — critical, blocks all stories
3. Complete Phase 3: User Story 1 (T016–T021)
4. **STOP and VALIDATE**: Run Firebase Emulator tests, confirm idempotency, confirm Home navigation
5. Demo MVP: customer can register, sign in, reach Home

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. Add US1 (bootstrap/register/sign-in) → Test independently → Demo
3. Add US2 (profile/email sync) → Test independently → Demo
4. Add US3 (admin provision) → Test independently → Demo
5. Add US4 (suspend/restore) → Test independently → Demo
6. Add US5 (search/role catalogue) → Test independently → Demo
7. Polish phase → Full verification

### Open Items Blocking Partial Tasks

| Blocked task(s) | Blocking gap | Unblocked work available |
|----------------|-------------|--------------------------|
| T028 location validation | G03 (FE-03 facade) | All other US3 logic |
| T041 location validation | G03 (FE-03 facade) | All other US5 logic |
| T028, T031, T033, T034, T041 notification | G04 (FE-07 facade) | All DB/audit/Firebase logic |
| T006 enum reconciliation | G05 (role-code mapping) | Entity design; can use provisional enums |
| T028 idempotency TTL purge | G01 (TTL value) | All other idempotency logic |
| T033 activation for MFA | G02 (MFA decision) | All non-MFA suspension/restore logic |

---

## Notes

- `[P]` tasks operate on different files with no incomplete-task dependencies — safe to parallelize
- `[Story]` label maps each task to its user story for traceability: requirement → decision → design → task
- Each User Story phase is a complete, independently testable increment
- Do not start implementation tasks until plan.md and data-model.md are agreed
- Record verification evidence in `verification.md`; do not claim a scenario passes without running it
- Firebase Auth Emulator must be used for all backend integration tests involving token verification or session invalidation
- `checkRevoked=true` is mandatory on all token verifications per Constitution Principle II
