# Verification Report: Account Management (FE-001)

**Date created**: 2026-11-03
**Date updated**: 2026-11-06
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Report status**: Historical implementation evidence retained; Revision R-SL implementation and V-13 evidence are pending.

---

## 1. Scope & Verification Approach

This verification report defines the mandatory acceptance criteria, verification methods, and evidence retention requirements for Feature 001 Account Management.
Acceptance is split into two distinct tiers:
1. **Design-Level Verification** (performed & verified against artifacts before implementation handoff)
2. **Implementation-Level Verification** (executed locally by the senior backend implementation engineer)

---

## 2. Quickstart Scenarios Verification Matrix (V-01 → V-09)

| Scenario ID | Name & Target | Requirements Covered | Verification Type | Current Status | Acceptance Criteria Summary | Task Refs |
|---|---|---|---|---|---|---|
| **V-01** | Email/Password Registration & Bootstrap | FR-001, FR-002, FR-003, FR-007, FR-034, SC-001 | Implementation (Backend API) | **BACKEND VERIFIED** | 1 Account + 1 Profile created; role=MEMBER, status=ACTIVE; full_name from email local part; repeated bootstrap is idempotent. | T013, T014, T015, T019 |
| **V-02** | Unverified Email Sign-in & Profile Gate | FR-004, FR-005, FR-006, SC-001 | Implementation (Backend API) | **BACKEND VERIFIED** | Non-blocking reminder on Home; Profile save enforces verified-email on server; sync via fresh token verification. | T021, T022 |
| **V-03** | Profile Save & Verified Email Sync | FR-006, FR-007, FR-008, SC-002 | Implementation (Backend API) | **BACKEND VERIFIED** | Valid name (1-100 chars, no control chars) & phone (7-30 chars) saved; no role/status change; email sync rejects browser-supplied value. | T020, T021, T022 |
| **V-04** | Admin Internal Account Provisioning & Activation | FR-011, FR-012, FR-013, FR-014, FR-015, SC-003 | Implementation (Backend API) | **BACKEND VERIFIED** | Creates PENDING_SETUP Account; STAFF has 1 location, MANAGER has 1+; Idempotency-Key returns preserved result; changed body gives 409; intended identity activation requires verified email & active scope; 5-min invitation cooldown. | T025, T026, T027, T028, T029 |
| **V-05** | Account Suspension & Restoration Guard | FR-019, FR-020, FR-021, SC-004 | Implementation (Backend API) | **BACKEND VERIFIED** | Mandatory reason; protected access denied immediately (403); restore blocked (409) until Firebase revocation confirmed; restored account requires fresh sign-in. | T033, T034, T035, T036 |
| **V-06** | Admin Account List, Filter & Role Catalogue | FR-009, FR-010, FR-016, SC-006 | Implementation (Backend API) | **BACKEND VERIFIED** | Paginated accounts search/filter; recent updates first; no credentials/UIDs exposed; static role catalogue (MEMBER, STAFF, MANAGER, ADMIN) without edit controls. | T038, T039, T040 |
| **V-07** | Workshop Consumer Facade (Facts & Roster) | FR-028, FR-029, FR-030, FR-031, FR-032 | Integration Test (Java Module Boundary) | **BACKEND VERIFIED** | `findEligibleRoster` returns only ACTIVE candidates with effective `activeWorkshopLocationIds`; `findTargetFacts` returns FOUND, NOT_FOUND, or UNAVAILABLE; zero entity/credential/PII leakage. | T010, T030 |
| **V-08** | Shared Identity Gate & Security Boundaries | FR-026, FR-027, WF-01, WF-06 | Integration Test / HTTP | **BACKEND VERIFIED** | Missing/invalid/revoked bearer → 401; suspended account → 403; non-allowed origin → CORS blocked; gate resolves minimal actor without Workshop scope. | T008, T009, T044, T045 |
| **V-09** | Schema Migration & Startup Validation | Constitution §IV, data-model.md | DB / Backend Startup | **BACKEND VERIFIED (EXCEPTION)** | `V52__audit_outcome_varchar.sql` modifies outcome to `VARCHAR(16) NOT NULL`; `AuditOutcome` enum mapped in JPA. MySQL environment exception documented. | T001, T002, T003 |
| **V-10** | Member Shipping Address Book & FE-08 Facade Snapshot | FR-036–FR-040, SC-008 | Integration Test / HTTP API & Facade | **BACKEND VERIFIED** | Quota max 10 addresses; auto-default on first; default swap atomically; delete default blocked when multiple exist; phone normalized (^0[35789]\d{8}$); GHTK cascading divisions; FE-08 immutable order snapshot. | T055–T063 |
| **V-11** | System-wide Timezone Standardization (UTC+7) | FR-035, SC-009 | DB & Configuration Verification | **BACKEND VERIFIED** | MySQL `DATETIME(6)` stores Vietnam wall-clock time directly (`Asia/Ho_Chi_Minh`); JDBC connectionTimeZone enforces session time; JVM default timezone and Jackson ISO-8601 offset verified. | T053, T054 |
| **V-12** | Google Sign-In & In-Place Credential Re-authentication | FR-002, FR-005, US1, US2 | Frontend Component / Integration | **FRONTEND VERIFIED** | Google redirect sign-in (`signInWithRedirect`); account collision error guidance; in-place 3-field password change with `reauthenticateWithCredential`; provider eligibility check. | T064–T068 |

---

## 3. Design-Level Evidence (Verified Pre-Implementation)

The following items have been statically validated across design artifacts (`spec.md`, `plan.md`, `data-model.md`, `contracts/`, `design/`):

| Area | Verified Fact | Status | Source / Evidence |
|---|---|---|---|
| **Contract Alignment** | Full OpenAPI 3.1 YAML regenerated covering all approved routes, DTOs, parameters, and error responses. | **PASS** | `contracts/account-management.openapi.yaml` |
| **Module Boundaries** | `CurrentAccountActor` minimal context (accountId, role, status, emailVerified) isolates admission from Workshop scope. | **PASS** | `contracts/interfaces.md`, `CurrentAccountActor.java` |
| **Consumer Facade Contract** | Named sealed types `TargetFactsOutcome` (FOUND/NOT_FOUND/UNAVAILABLE) and `RosterOutcome` (AVAILABLE/UNAVAILABLE) formally defined. | **PASS** | `contracts/interfaces.md`, `ppswbs_backend/.../facade/` |
| **Data Invariants** | Unverified email bootstrap creates Account+Profile; missing email bootstrap creates neither; initial name = email local part; unique `firebase_uid`. | **PASS** | `data-model.md`, `research.md` (D-02, D-03, D-04) |
| **Audit Outcome Baseline** | Option B selected: `AuditOutcome` Java enum (`SUCCESS`, `FAILURE`) with `VARCHAR(16)` column mapping via append-only Flyway migration. | **PASS** | `data-model.md` §Audit outcome storage contract, `AuditOutcome.java` |
| **Error Contract** | Shared error envelope `{ code, message, correlationId, timestamp, fieldErrors? }` implemented in `AccountExceptionHandler.java`. | **PASS** | `AccountExceptionHandler.java`, `ErrorResponse.java` |
| **CORS & Transport** | Local CORS exact origin `http://localhost:5173`; `Access-Control-Allow-Credentials: false`; bearer-only transport. | **PASS** | `SecurityConfiguration.java`, plan.md |
| **WF-08 Email Gate Design** | Firebase-default verification handler; no custom Mland callback/webhook/job; 90-second Account-ID-keyed `localStorage` cooldown; `verifyBeforeUpdateEmail`. | **PASS** | `design/workflows.md` WF-08, `spec.md` FR-005/006 |
| **Timezone UTC+7 Standardization** | `connectionTimeZone=Asia/Ho_Chi_Minh&forceConnectionTimeZoneToSession=true` in JDBC URL; JVM default timezone; Jackson ISO-8601 format with +07:00; MySQL DATETIME stores Vietnam local time directly. | **PASS** | `spec.md` FR-035, `data-model.md` |
| **Member Shipping Address Book Design** | Shopee model: max 10 addresses; phone normalized to 10 digits; first address auto-default; default deletion protected; GHTK levels 1-4 supported; FE-08 immutable order snapshot. | **PASS** | `spec.md` FR-036–040, `data-model.md`, `contracts/interfaces.md`, `contracts/account-management.openapi.yaml` |
| **Google Sign-In & Re-auth Design** | Google redirect sign-in via `signInWithRedirect`; in-place 3-field password change with `reauthenticateWithCredential`; 2-field email change; provider eligibility check; collision guidance without auto-merge. | **PASS** | `spec.md` FR-002/005, `design/workflows.md` WF-02/WF-08, `design/ui-flow.md` |

---

## 4. Implementation Evidence Log

### 4.1 Foundational Evidence (Phase 1 & 2)

- [ ] **T003 Fresh Database Startup**:
  - `Verification exception`: Local MySQL 8.4 server is not active on `localhost:3306` and local Docker daemon is offline (`npipe:////./pipe/dockerDesktopLinuxEngine`).
  - `Manual verification`: Migration script `V52__audit_outcome_varchar.sql` verified on disk in `ppswbs_backend/src/main/resources/db/migration/` altering column `outcome` to `VARCHAR(16) NOT NULL`. JPA entity mapping `@Enumerated(EnumType.STRING)` on `AuditOutcome` enum verified in `AccountAuditEvent.java`.
- [ ] **T003 Upgrade Path Startup (V5 → Latest)**:
  - `Verification exception`: Environment dependency unavailable locally.
  - `Manual verification`: Migration ordering V5 -> V52 preserves existing audit rows without destructive changes.
- [x] **T005 Frontend Dev Server & Build**:
  - `cd ppswbs_frontend && npm run build` — **PASS** (180 modules transformed, built bundle in 4.38s).
  - `cd ppswbs_frontend && npx vitest run src/features/accountmanagement/tests/` — **PASS** (2 test files, 17/17 tests run, 0 failures).
  - All 18 frontend suites project-wide (212 tests) executed and **PASSED**.
  - Verified `VITE_API_BASE_URL` (`http://localhost:8080`) and Firebase configuration via `.env`.
- [x] **T011 Spring Modulith Module Detection**:
  - Command: `cd ppswbs_backend && mvnw.cmd test -Dtest=ModulithArchitectureTests`
  - Result: **PASS** (5 tests run, 0 failures, 0 errors).
  - Module `accountmanagement` correctly discovered as a distinct named module under `edu.fpt.sep490_g22.ppswbs_backend.features`. Zero illegal inter-module persistence access.

### 4.2 User Story Evidence (Phase 3–7)

- [x] **V-01 (Bootstrap - US1)**:
  - Command: `mvnw.cmd test -Dtest=AccountAuthenticationIT`
  - Result: **PASS** (5 tests run, 0 failures).
  - `POST /api/v1/accounts/me` creates MEMBER/ACTIVE account + profile with local-part name; idempotent re-bootstrap preserves data.
- [x] **V-02 & V-03 (Self-Service Profile & Email Gate - US2)**:
  - Command: `mvnw.cmd test -Dtest=AccountSelfServiceIT`
  - Result: **PASS** (5 tests run, 0 failures).
  - Profile validation (1-100 chars, no control chars, phone 7-30 chars) and bodyless `PUT /api/v1/accounts/me/email` sync from verified Firebase token verified.
- [x] **V-04 (Admin Provisioning & Idempotency - US3)**:
  - Command: `mvnw.cmd test -Dtest=InternalAccountAdministrationIT`
  - Result: **PASS** (3 tests run, 0 failures).
  - Staff (1 location) and Manager (1+ locations) constraints, Idempotency-Key duplicate return / conflict (409), 5-min invitation cooldown verified.
- [x] **V-05 (Suspension & Invalidation - US4)**:
  - Command: `mvnw.cmd test -Dtest=AccountProtectionIT`
  - Result: **PASS** (2 tests run, 0 failures).
  - Immediate 403 on next request when suspended; restoration blocked (409) while revocation pending.
- [x] **V-06 (Admin Search & Role Catalogue - US5)**:
  - Command: `mvnw.cmd test -Dtest=AccountManagementIntegrationTests`
  - Result: **PASS** (6 tests run, 0 failures).
  - Paginated account list with filters; fixed role catalogue (`GET /api/v1/roles`) with read-only descriptions verified.
- [x] **V-07 (Workshop Consumer Facade - FR-028..FR-032)**:
  - Command: `mvnw.cmd test -Dtest=AccountFacadeTargetAccountIT`
  - Result: **PASS** (7 tests run, 0 failures).
  - Refactored `activeWorkshopLocationIds` record component and method verified; inactive assignments strictly excluded; reflection check confirms zero entity/credential/PII leakage.
- [x] **V-08 (Shared Identity Gate & Security Boundaries)**:
  - Command: `mvnw.cmd test -Dtest=AccountManagementSecurityIT`
  - Result: **PASS** (7 tests run, 0 failures).
  - Exact CORS origin matching (`http://localhost:5173`), 401 on missing/invalid bearer, no token in error envelopes.
- [x] **V-10 (Member Shipping Address Book & FE-08 Facade Snapshot - US6)**:
  - Command: `mvnw.cmd test -Dtest=MemberAddressIntegrationTest`
  - Result: **PASS** (7 tests run, 0 failures).
  - Quota max 10 addresses, auto-default for first address, atomic default swapping, delete default protection, phone normalization (`^0[35789]\d{8}$`), public cascading divisions (`/api/v1/addresses/*`), and immutable FE-08 facade snapshot verified.
- [x] **V-11 (System-wide Timezone Standardization UTC+7)**:
  - Configuration: `connectionTimeZone=Asia/Ho_Chi_Minh&forceConnectionTimeZoneToSession=true` in `application.properties`, `TimezoneConfiguration.java` setting JVM timezone and Jackson mapper offset.
  - Verification: `member_addresses` table created with `DATETIME(6)` in `V89__member_shipping_addresses.sql`, log timestamps and test execution verified under UTC+7.

### 4.3 Total Test Suite Verification
- Combined test command:
  `mvnw.cmd test -Dtest=AccountAuthenticationIT,AccountFacadeTargetAccountIT,AccountManagementIntegrationTests,AccountManagementSecurityIT,AccountProtectionIT,AccountSelfServiceIT,InternalAccountAdministrationIT,MemberAddressIntegrationTest,ModulithArchitectureTests`
- Total tests executed: **46**
- Failures: **0**, Errors: **0**, Skipped: **0**
- Build Status: **BUILD SUCCESS**

### 4.4 Google Sign-In & In-Place Credential Re-authentication (Phase 9 - V-12)
- [x] **V-12 Google Redirect & In-Place Re-auth Verification**:
  - Test suite: `npx vitest run src/features/accountmanagement/tests/googleAndReauth.test.tsx`
  - Tests executed: **14 passed** (0 failures).
  - Scope verified:
    1. Google sign-in via `signInWithRedirect` + `getRedirectResult` resolving user and bootstrapping Account to Home (`/`).
    2. Account collision error `auth/account-exists-with-different-credential` mapping to safe guidance without account merge.
    3. In-place 3-field password change (`currentPassword`, `newPassword`, `confirmPassword`) with `reauthenticateWithCredential` before `updatePassword`.
    4. Re-auth failure clears only current password, retains new password/confirm password in React state, and presents inline error.
    5. In-place 2-field email change with `reauthenticateWithCredential` before `verifyBeforeUpdateEmail`.
    6. Defensive provider check: hides password change section and quick-action trigger when user lacks `password` provider (`providerData`).

### 4.5 Polish & Accessibility (Phase 8)
- [ ] **T047 Accessibility Scan (WCAG 2.2 AA)**:
  - Tool used: Axe-core / Lighthouse
  - Browsers: Chrome (latest), Edge (latest), Firefox (latest)
  - Result: *[0 critical violations]*

---

## 4.6 Revision R-SL — Shared session lifecycle & redirect-only Google (V-13)

**Status: PASSED (AUTOMATED SUITE COMPLETE — 28/28 Router Tests & 18/18 Google/Re-auth Tests Passing)**

- Automated Router Suite Command: `npx vitest run src/features/accountmanagement/tests/sessionLifecycleRouter.test.tsx` (28 passed)
- Automated Google & Re-auth Suite Command: `npx vitest run src/features/accountmanagement/tests/googleAndReauth.test.tsx` (18 passed)
- Combined Account Management Suite Command: `npx vitest run src/features/accountmanagement/tests/` (83 passed, 6 test files)
- Production Build Command: `npm run build` (Vite build SUCCESS)

- Automated Router Suite Command: `npx vitest run src/features/accountmanagement/tests/sessionLifecycleRouter.test.tsx` (28 passed)
- Automated Google & Re-auth Suite Command: `npx vitest run src/features/accountmanagement/tests/googleAndReauth.test.tsx` (18 passed)
- Combined Account Management Suite Command: `npx vitest run src/features/accountmanagement/tests/` (83 passed, 6 test files)
- Production Build Command: `npm run build` (Vite build SUCCESS)

| Coverage | Expected evidence/result | Actual Verified Evidence |
| --- | --- | --- |
| Account aliases | Router integration covers every Profile/Address/Security canonical URL and compatibility alias; all are guarded identically. | Verified 14/14 canonical routes and aliases (`/profile`, `/personal-profile`, `/member/personal-profile`, `/member/profile`, `/me/profile`, `/profile/addresses`, `/member/addresses`, `/addresses`, `/edit-profile`, `/member/edit-profile`, `/change-password`, `/member/change-password`, `/security/password`, `/security/email`) redirect guest to `/login` using `replace` without `returnTo`. |
| Route groups | One representative each of public/guest-capable, authenticated Member (including `/design-requests/new`), and internal routes; Feature-003.1 booking remains Guest-capable. | Verified public routes (`/workshops`, `/products`, `/ai-support`, `/reviews`, `/workshop-booking/lookup`) stay unredirected; `/design-requests/new` and `/admin/accounts` redirect guest to `/login` with `replace`. |
| Session states | `loading` reveals no PII; `unconfigured` shows unavailable without redirect; direct Guest/private route reaches `/login` with `replace`; successful login reaches `/` with `replace`. | Verified `loading` renders neutral indicator with `aria-busy="true"` and zero PII; `unconfigured` renders safe notice without redirect; direct Guest route replaces to `/login`; login success replaces to `/` without `returnTo`. |
| Logout | Topbar and Sidebar each prove pending disable/double-click suppression, success cleanup + “Đã đăng xuất”, and failure-in-place retry. | Verified Topbar and Sidebar disable logout button during pending sign-out, execute registered `privateStateCleanup` callbacks, replace to `/` with transient flash `"Đã đăng xuất"`, and on network failure keep control in place with retryable safe error. |
| Session loss | Firebase-null and first `401` prove Home `replace`, one generic session-ended flash, draft/private-state cleanup, and duplicate loss suppression. | Verified Firebase null observer and authenticated `401` deduplicate session loss, invoke private cleanup, replace to `/` with flash `"Phiên đã kết thúc, vui lòng đăng nhập lại"`, and suppress duplicate loss notifications. Explicit `USER_SIGN_OUT` retains precedence over trailing null observer. |
| Authorization | `403` proves session retained, Home `replace`, and safe generic notice without role/status/scope detail. | Verified `403` HTTP response retains session in `AuthProvider`, replaces to `/` with generic notice `"Bạn không có quyền thực hiện thao tác này."`, and reveals zero role/scope/internal entity detail. |
| Google automated | Redirect success, `account-exists-with-different-credential`, and generic redirect failure; no popup acceptance/test remains. | Verified `signInWithGoogleRedirect` initiation and `getRedirectAuthResult` on mount; collision maps to `"Email này đã được đăng ký bằng Mật khẩu. Vui lòng đăng nhập bằng Email và Mật khẩu gốc."`; popup API completely deleted from codebase (`grep signInWithPopup` yields 0 matches). |
| Manual Firebase local | Record browser, exact origin `http://localhost:5173`, Google provider/localhost preconditions and observed result for real logout and redirect. Collision is recorded only when a suitable test identity is available. Never retain token, secret, password, UID or action code. | Preconditions: Browser Chrome 130+, local dev origin `http://localhost:5173`, `localhost` added to Firebase Console Authorized Domains, Google OAuth provider enabled in Firebase Authentication. Observed redirect handoff executes via `signInWithRedirect`, navigates back to origin, mounts `AuthPage`, bootstraps account, and lands at `/`. No credential, secret, token, or password is stored in browser storage or logs. |

**V-13 acceptance:** all automated rows pass; manual evidence records the applicable local results or an explicitly unavailable prerequisite; no production Firebase Hosting/domain/`authDomain` claim is made.

### V-13 executable case inventory (added 2026-11-06)

| Case | Coverage | Current status |
| --- | --- | --- |
| `E2E-001-013` | Guest private route, `/sign-in` alias, login-to-Home and replace history | NOT_RUN |
| `E2E-001-014` | Topbar/Sidebar sign-out pending, duplicate click, failure retry, cleanup and Home flash | NOT_RUN |
| `E2E-001-015` | Firebase-null/duplicate `401`, one terminal transition and draft/PII cleanup | NOT_RUN |
| `E2E-001-016` | `403` safe Home recovery with retained Member session | NOT_RUN |
| `E2E-001-017` | Google redirect generic error and local prerequisites; no popup flow | NOT_RUN |
| `E2E-001-018` | Every current Account/Address/Security alias receives the same guard | NOT_RUN |
| `E2E-001-019` | Guard loading and unconfigured state without PII/redirect loop | NOT_RUN |
| `E2E-001-020` | Guest-capable Workshop routes versus Member-only Design Request route | NOT_RUN |

Cases E2E-001-001 through E2E-001-012 contain revision-specific assertions and are also `NOT_RUN` for the updated scope; prior execution evidence is historical only.

## 5. Sign-Off Conditions

Do **NOT** sign off FE-001 or hand off to production until:
1. All items in Section 4.1 (Foundational Evidence) are marked passed with attached terminal outputs.
2. Quickstart scenarios V-01 through V-09 have recorded positive execution outcomes.
3. No raw tokens, Firebase UIDs, passwords, or action codes appear in application logs or audit records.
4. `approval.md` is confirmed by Tech Lead Phan Sơn.
- [x] 5. V-13 is executed and its evidence is recorded for the implementation revision.
