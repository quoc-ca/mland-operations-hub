# Implementation Plan: Account Management (FE-001)

**Date created**: 2026-10-25
**Date updated**: 2026-10-25
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Branch**: `001-account-management` | **Spec**: [spec.md](spec.md)

**Input**: Feature specification FE-001 Account Management v2026-10-25, replacing the superseded Member Profile plan.

> **Supersession note**: This plan replaces `plan.md` dated 2026-10-19 which described the legacy Member Profile/Thymeleaf/cookie-session design. That plan is incompatible with Constitution v3.0.0 (separate React frontend, no SSR/htmx). Do not use the previous plan, tasks.md, data-model.md, or `contracts/member-profile.openapi.yaml` as implementation guidance.

---

## Summary

FE-001 delivers the full account lifecycle for Mland: registration and sign-in (email/password and optional Google/Facebook), profile self-service, credential recovery, email-change synchronization, Admin-driven internal account provisioning (Staff/Manager), role/location assignment, suspension/restoration, fixed-role catalogue, and lifecycle audit. It is the identity and authorization boundary for all other features.

**Technical approach**: Firebase Authentication proves identity and manages credentials. The backend verifies every Firebase ID token with `checkRevoked=true` before reading its UID. MySQL (via Spring Data JPA) is authoritative for account status, role, location scope, and audit. The feature is a Spring Modulith module `accountmanagement` with controller, dto, dao, entity, service, and facade packages. The frontend is a separate React + Vite SPA communicating over REST.

---

## Technical Context

**Backend Language/Version**: Java 21, Spring Boot 3.4.x (latest stable compatible with Spring Modulith)

**Frontend Language/Version**: TypeScript, React 18, Vite 5.x

**Primary Backend Dependencies**:
- Spring Modulith (module detection and verification)
- Spring Data JPA / Hibernate
- Firebase Admin SDK for Java (latest stable, >= 9.x)
- Spring Security (REST-layer authorization; no Thymeleaf/form login)
- Bean Validation (Jakarta Validation)
- Spring Boot Actuator (health/observability)
- Flyway (schema migration)

**Primary Frontend Dependencies**:
- Firebase Web SDK v10.x (authentication client)
- React Router v6 (client-side routing)
- Axios or Fetch API (authenticated REST calls)

**Storage**: MySQL 8.4 (primary); no Redis, no application-level session store

**Testing**:
- Backend: JUnit 5, Spring Boot Test, MockMvc, Firebase Auth Emulator, Testcontainers (MySQL)
- Frontend: Vitest, React Testing Library (scope TBD for implementation phase)

**Target Platform**: Linux server (backend); browser (frontend). Production origin, hosting, and CDN are not selected in this plan.

**Identity Provider**: Firebase Authentication — email/password always; Google and Facebook only when provider is configured for the environment.

**Performance Goals**: Standard web-app expectations (< 500 ms p95 for API responses under normal load); no unusual throughput requirement documented in spec.

**Constraints**:
- `checkRevoked=true` on every Firebase ID token verification (required by spec and constitution)
- No raw SQL in application code
- No applied Flyway migration may be edited or reused
- Audit records retained 5 years (SC-005 / FR-023)
- Idempotency key TTL for internal account creation: at least 30 days (covers re-invite window); exact value requires product decision — **TBD [G01]**

**Scale/Scope**: Small-to-medium business; no extreme concurrency requirement. Optimistic locking on `accounts`; pessimistic write lock on `account_profiles` create/update.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I — Module Integrity | PASS | Feature packaged as `features/accountmanagement/` with controller, dto, dao, entity, service, facade. Module boundary verified via `ApplicationModules.verify()`. `common` contains no account business entities or DTOs. |
| I — No raw SQL | PASS | All persistence via JPA/Spring Data. |
| I — Flyway append-only | PASS | New migrations appended; existing V1–V4 untouched. |
| II — Firebase proves identity only | PASS | Token verified with `checkRevoked=true`; UID extracted; MySQL determines role/status. |
| II — No credential capture | PASS | Backend never receives passwords, reset codes, or raw tokens. |
| II — No PII in logs | PASS | Audit records exclude names, phone, raw tokens, and action links (FR-023). |
| III — Separate React frontend | PASS | No Thymeleaf, no htmx, no SSR of application UI. HTML email templates are not UI. |
| III — Versioned REST, typed errors | PASS | Routes versioned under `/api/v1`; typed error codes; OpenAPI contract. |
| III — Server-side authorization | PASS | Every protected endpoint re-checks role/status from MySQL, not from frontend state or token claims. |
| III — No entity exposure | PASS | JPA entities stay internal; DTOs define transport. |
| IV — Evidence-based verification | PASS | Verification plan covers module boundary, contract, migration, Firebase Emulator, and suspension flow. |
| V — Hybrid state design | PASS | Stateless token-per-request for most flows; suspension invalidation uses Firebase `revokeRefreshTokens()` — backend enforces `validSince` check on subsequent tokens. No Redis or app-signed JWTs added. |
| V — No unapproved frameworks | PASS | No new external system boundary beyond approved baseline. FE-07 and FE-03 boundaries used via facade contracts (TBD readiness; see dependencies). |

**Constitution Check: PASS (pre-design).** Re-check required after Phase 1 design artifacts are finalized.

---

## Phase 0: Research Findings

> `research.md` in this feature directory contains detailed UX/flow research compiled during clarification sessions (2026-10-24–2026-10-25). The decisions below consolidate that research. Items marked TBD require explicit product or technical decision before implementation.

### Decision log

| ID | Topic | Decision | Rationale | Alternatives considered |
|----|-------|----------|-----------|------------------------|
| R01 | Token verification | `checkRevoked=true` on every protected request | FR-020 requires suspension to deny access immediately; revocation must be detected on token use | `checkRevoked=false` with DB-only check — rejected: token may be used before next DB-checked request |
| R02 | Suspension / session invalidation | Firebase `revokeRefreshTokens(uid)` at suspension time; backend checks `auth_time`/`validSince` on every subsequent token | Only supported mechanism for Firebase-backed identity | Custom token blacklist — rejected (complexity, not aligned with constitution) |
| R03 | Restoration gate | Restoration blocked until `revokeRefreshTokens` completes without error; backend records completion status in `accounts.session_revocation_confirmed_at` | FR-020: "Restoration MUST wait until invalidation is complete" | Immediate restore with delayed check — rejected (violates FR-020) |
| R04 | Account bootstrap idempotency | `PUT /api/v1/accounts/me` upsert on UID; unique constraint on `firebase_uid`; conflict returns existing account | FR-001, FR-013, research.md Backend common flow | Email-based lookup — rejected (FR-001, FR-006, FR-022 all prohibit email as ownership proof) |
| R05 | Internal account creation idempotency | Client-supplied `Idempotency-Key` header stored per-Admin request; unchanged repeat returns original result; changed repeat with same key returns 409 | FR-014 | DB-level duplicate email check — insufficient (same email could be different intent) |
| R06 | Email synchronization | Backend updates `login_email` only when current Firebase ID token (verified with `checkRevoked=true`) carries a verified email for the same UID | FR-006 | Accepting browser-supplied email — rejected (FR-006 explicitly forbids) |
| R07 | Phone field | Stored as-is after trimming and length validation (7–30 chars); not normalized, not OTP-verified, not required to be unique | FR-007 | E.164 normalization — rejected (spec says "neither normalized") |
| R08 | Location validation source | Account feature validates Staff/Manager location scope against FE-03 facade; does not own a duplicate location record | FR-012 | Local copy of location table — rejected (violates module data ownership) |
| R09 | Notification integration | Post-commit notification request persisted in same DB transaction; FE-07 job delivers after commit | FR-024 | Synchronous SMTP in request — rejected (FR-024 forbids blocking on delivery) |
| R10 | Audit retention | Audit events retained 5 years in DB; no automatic purge mechanism in V1 scope | FR-023 | 90-day retention — rejected (FR-023 specifies 5 years) |
| R11 | MFA for internal roles | spec-general.md requires MFA for privileged internal roles; role-code mapping and enforcement mechanism remain TBD (product/security owner) | spec-general.md | Not implementing MFA — blocked pending TBD resolution |
| R12 | Resend cooldown | 5-minute resend cooldown for invitation resend enforced server-side by comparing last-sent timestamp | FR-015 | Client-side throttle — rejected (bypassable) |

### Open items (require decision before implementation)

| ID | Gap | Impact | Owner | Condition to proceed |
|----|-----|--------|-------|---------------------|
| G01 | Idempotency key TTL for internal account creation | Tasks cannot specify purge/expiry logic | Product | Owner sets TTL (suggested: 30 days) |
| G02 | MFA for Staff/Manager/Admin roles | Affects activation flow and login sequence | Product + Security | Explicit decision to defer to V2 or specify mechanism |
| G03 | FE-03 facade readiness | `AccountLocationAssignment` validation depends on FE-03's public boundary | FE-03 team | FE-03 publishes a facade method returning active location IDs |
| G04 | FE-07 facade readiness | Post-commit notification request pattern requires FE-07's accepted DTO/event contract | FE-07 team | FE-07 publishes facade or event interface for notification requests |
| G05 | Role-code mapping (MANAGER vs OWNER, ADMIN vs ADMIN_TECHNICAL) | spec-general.md notes TBD; misalignment would break existing permission checks | Product | Explicit role-code decision document |

---

## Phase 1: Design

### Project Structure

#### Documentation (this feature)

```text
ppswbs-spec/features/001-account-management/
├── plan.md              (this file)
├── research.md          (Phase 0 — existing, see above)
├── data-model.md        (Phase 1 — to be regenerated from this plan)
├── quickstart.md        (Phase 1 — to be regenerated)
├── contracts/
│   └── account-management.openapi.yaml  (Phase 1 — replaces member-profile.openapi.yaml)
└── tasks.md             (Phase 2 — generated by speckit-tasks, not by this plan)
```

#### Backend source layout

```text
backend/src/main/java/vn/mland/ppswbs/features/accountmanagement/
├── controller/
│   ├── AccountController.java
│   ├── AdminAccountController.java
│   └── RoleCatalogueController.java
├── dto/
│   ├── request/
│   │   ├── AccountBootstrapRequest.java
│   │   ├── ProfileUpdateRequest.java
│   │   ├── EmailSyncRequest.java
│   │   ├── InternalAccountCreateRequest.java
│   │   ├── InternalProfileUpdateRequest.java
│   │   ├── RoleLocationAssignRequest.java
│   │   └── AccountSuspendRequest.java
│   └── response/
│       ├── AccountResponse.java
│       ├── AccountDetailResponse.java
│       ├── AccountListResponse.java
│       ├── InternalAccountResponse.java
│       └── RoleCatalogueResponse.java
├── dao/
│   ├── AccountRepository.java
│   ├── AccountProfileRepository.java
│   ├── AccountLocationAssignmentRepository.java
│   ├── InternalAccountProvisioningRepository.java
│   └── AccountAuditEventRepository.java
├── entity/
│   ├── Account.java
│   ├── AccountProfile.java
│   ├── AccountLocationAssignment.java
│   ├── InternalAccountProvisioning.java
│   ├── AccountAuditEvent.java
│   └── enums/
│       ├── AccountRole.java      (MEMBER, STAFF, MANAGER, ADMIN)
│       └── AccountStatus.java   (ACTIVE, PENDING_SETUP, SUSPENDED)
├── service/
│   ├── AccountBootstrapService.java
│   ├── ProfileService.java
│   ├── EmailSyncService.java
│   ├── AdminAccountService.java
│   ├── SuspensionService.java
│   ├── AuditService.java
│   └── TokenVerificationService.java
└── facade/
    └── AccountFacade.java   (PUBLIC boundary: resolveAccount, getAccountStatus, getLocationScope)
```

#### Frontend source layout

```text
frontend/src/features/account/
├── pages/
│   ├── RegisterPage.tsx
│   ├── SignInPage.tsx
│   ├── ProfilePage.tsx
│   ├── ChangeEmailPage.tsx
│   └── admin/
│       ├── AccountListPage.tsx
│       ├── AccountDetailPage.tsx
│       ├── CreateInternalAccountPage.tsx
│       └── RoleCataloguePage.tsx
├── components/
│   ├── EmailVerificationBanner.tsx
│   ├── ProfileForm.tsx
│   ├── SuspendRestoreDialog.tsx
│   └── RoleLocationAssignForm.tsx
├── hooks/
│   ├── useCurrentAccount.ts
│   └── useAdminAccounts.ts
└── services/
    └── accountApi.ts
```

**Structure Decision**: Web application layout. Backend module package is `accountmanagement` (valid Java identifier). Only `facade/AccountFacade` is public between features. Internal packages (controller, dto, dao, entity, service) remain internal to the module.

---

### State Design (Hybrid)

| Interaction | Stateless / Stateful | Mechanism | Owner | Lifecycle |
|-------------|---------------------|-----------|-------|-----------|
| Every API request | Stateless | Firebase ID token in Authorization: Bearer; verified per-request with `checkRevoked=true` | Firebase | Token expiry ~1 hour; client refreshes via SDK |
| Account status / role | Stateful (business state) | MySQL `accounts` table | Backend | Persists until Admin action |
| Suspension invalidation | Stateful (security) | Firebase `revokeRefreshTokens(uid)` + `validSince` check on subsequent tokens | Firebase + Backend | Until next successful login post-restoration |
| Restoration gate | Stateful (security) | `accounts.session_revocation_confirmed_at` timestamp | Backend | Set on confirmed `revokeRefreshTokens` success |
| Idempotency key | Stateful (operational) | `internal_account_provisionings.idempotency_key` UNIQUE; TTL [G01 TBD] | Backend | Per-Admin create request |
| Invitation resend cooldown | Stateful (operational) | `internal_account_provisionings.invitation_last_sent_at` | Backend | 5-minute window enforced server-side |

No application-signed JWT, no Redis session store, no cookie-based session for API authentication.

---

### Module Boundary and Verification

- Package: `vn.mland.ppswbs.features.accountmanagement`
- `ApplicationModules.verify()` must pass with `accountmanagement` detected as a distinct named module (not collapsed into a parent `features` module).
- Detection method: `@ApplicationModule` annotation on a module descriptor class or on `AccountFacade` to guarantee detection independent of package scanning order.
- Public API surface: `AccountFacade` only. No entity, DAO, controller, or internal service crosses the module boundary.
- Consumers (workshopmanagement, loyalty, etc.) call only `AccountFacade` methods.

---

### REST API Summary

Base path: `/api/v1`

| Method | Route | Actor | Purpose |
|--------|-------|-------|---------|
| PUT | `/accounts/me` | Any authenticated | Bootstrap or return existing account (idempotent by UID) |
| GET | `/accounts/me` | Authenticated | Get own account + profile |
| PUT | `/accounts/me/profile` | Authenticated (verified email) | Update own full name / phone |
| POST | `/accounts/me/email-sync` | Authenticated | Sync login email from current token |
| DELETE | `/accounts/me/session` | Authenticated | Sign out (server-side if applicable) |
| GET | `/admin/accounts` | ADMIN | List / search / filter accounts (paginated) |
| GET | `/admin/accounts/{id}` | ADMIN | Account detail |
| POST | `/admin/accounts/internal` | ADMIN | Create Staff/Manager account (idempotent via Idempotency-Key) |
| POST | `/admin/accounts/{id}/invitation/resend` | ADMIN | Resend setup invitation (5-min cooldown) |
| PUT | `/admin/accounts/{id}/profile` | ADMIN | Edit internal account full name / phone |
| PUT | `/admin/accounts/{id}/role-location` | ADMIN | Assign role and location scope (optimistic lock) |
| POST | `/admin/accounts/{id}/suspend` | ADMIN | Suspend with reason |
| POST | `/admin/accounts/{id}/restore` | ADMIN | Restore (blocked until session_revocation_confirmed_at is set) |
| GET | `/admin/roles` | ADMIN | Fixed role catalogue (read-only) |

Full contracts including request/response DTOs, error codes, and OpenAPI spec in `contracts/account-management.openapi.yaml` (to be generated; replaces legacy `member-profile.openapi.yaml`).

---

### Data Model Summary

Full schema in [data-model.md](data-model.md) (to be regenerated). Summary:

| Table | Key columns | Notes |
|-------|-------------|-------|
| `accounts` | `id`, `firebase_uid` UNIQUE NOT NULL, `login_email`, `role` ENUM(MEMBER/STAFF/MANAGER/ADMIN), `status` ENUM(ACTIVE/PENDING_SETUP/SUSPENDED), `version` (optimistic lock), `session_revocation_confirmed_at` | UID is the only identity key; no email-based merge |
| `account_profiles` | `account_id` PK/FK, `full_name` VARCHAR(400) (reserves 100 Unicode code points up to 4 bytes), `phone` VARCHAR(30) nullable, `updated_at` | No separate identity; owned 1:1 by Account |
| `account_location_assignments` | `id`, `account_id` FK, `location_id` BIGINT (reference only — no FK to location table), `assigned_at`, `active` | Validated against FE-03 facade at write time; STAFF exactly one active; MANAGER one or more |
| `internal_account_provisionings` | `id`, `account_id` FK, `idempotency_key` UNIQUE, `status`, `invitation_last_sent_at`, `created_by_admin_id` FK, `firebase_setup_link_issued_at` | Tracks creation and resend state; setup link not stored raw |
| `account_audit_events` | `id`, `actor_account_id`, `target_account_id`, `action`, `outcome`, `correlation_id`, `occurred_at`, `metadata` JSON (redacted) | Retained 5 years; no names, phone, raw tokens, or action links |

Migration: `V5__account_management.sql` (append-only; V1–V4 untouched). Existing `members` table relationship and enum values must be reconciled [G05 TBD] before migration is written.

---

### Verification Plan

See [quickstart.md](quickstart.md) for runnable scenarios. Key checks:

1. `ApplicationModules.verify()` passes with `accountmanagement` as a named module distinct from the `features` grouping package.
2. `PUT /accounts/me` called twice with same Firebase UID returns the same account; DB row count = 1.
3. `POST /admin/accounts/internal` with same `Idempotency-Key` twice returns the same account ID; DB count = 1.
4. Suspend account then any subsequent protected request with previously valid token returns 403 ACCOUNT_SUSPENDED.
5. `POST /admin/accounts/{id}/restore` while `session_revocation_confirmed_at` IS NULL returns 409 REVOCATION_PENDING.
6. `PUT /accounts/me/profile` with unverified email in token returns 403 EMAIL_NOT_VERIFIED.
7. `PUT /admin/accounts/{id}/role-location` with stale `version` returns 409 OPTIMISTIC_LOCK_CONFLICT.
8. Flyway migration on empty DB succeeds; existing V1–V4 migration checksums unchanged.

---

## Complexity Tracking

> No constitution violations identified. No entry required.

---

## Dependencies and Risks

| ID | Dependency | Impact | Mitigation |
|----|------------|--------|------------|
| DEP-01 | FE-03 Workshop Management facade [G03] | `AccountLocationAssignment` validation fails at runtime if FE-03 facade absent | Design a stub interface; implement validation as conditional check that logs warning if FE-03 module not present; stub must be replaced before Staff/Manager creation is enabled in production |
| DEP-02 | FE-07 Notification Management facade [G04] | Post-commit notification request cannot be persisted if FE-07 contract unknown | Design notification request table independently; FE-07 consumes it; if FE-07 contract changes, only notification preparation method changes |
| DEP-03 | Role-code mapping [G05] | Wrong enum value in existing data causes authorization errors | Do not rename existing enum values; add new values as needed; resolve before enabling internal roles |
| DEP-04 | Firebase `revokeRefreshTokens` async behavior | Suspension is immediate on backend; Firebase token invalidation can take up to 1 hour for cached tokens | `checkRevoked=true` forces server-side Firebase check on each token use, eliminating the cache window. Accepted per R01/R02. |
| DEP-05 | Admin bootstrap operations process | First Admin account cannot be created through this feature | Out of scope by spec; document the expected operations script separately |

---

## $fomo-cc Critique Summary

Five assumptions stress-tested before finalizing this plan:

1. **"Firebase token verification is fast"** — `checkRevoked=true` adds a network round-trip to Firebase on every protected request. Under high load this is a latency risk. Accepted for V1 given small-business scale and the spec's hard requirement on immediate suspension enforcement. At scale, a short-lived (< 5 min) local revocation cache could reduce calls without violating FR-019.

2. **"Idempotency key is simple"** — Without a TTL decision [G01], the `internal_account_provisionings` table grows unbounded. Key uniqueness also creates a subtle bug: if an Admin reuses an old key for a genuinely new account, the server returns the old result. Mitigation: document key format convention (UUID per create intent) and add a TTL purge.

3. **"FE-03 and FE-07 will be ready"** — Both are independent features with their own timelines. This plan does not assume their facades exist. Mitigation: stub strategies in DEP-01 and DEP-02.

4. **"Restoration is simple"** — The FR-020 restoration gate (wait for revocation confirmation) requires the backend to track `session_revocation_confirmed_at`. An Admin UI that does not expose this status will show a confusing "cannot restore" error. Mitigation: API response includes `revocation_status` field so the frontend can show appropriate guidance.

5. **"5 User Stories in one feature is manageable"** — US1–US4 are all P1, creating implementation pressure. The task list phases US1 (bootstrap) and US2 (self-service) before US3 (admin provisioning) and US4 (suspension) to allow independent testing of each increment. US5 (role catalogue, P2) follows last.
