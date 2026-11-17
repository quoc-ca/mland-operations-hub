# Implementation Plan: Account Management (FE-001)

**Date created**: 2026-10-26
**Date updated**: 2026-11-14
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Branch**: `son` | **Spec**: [spec.md](spec.md)

**Input**: Historical approved design artifacts plus the 2026-11-14 Manager-global authorization revision in spec.md, data-model.md, contracts/interfaces.md, design/architecture.md, design/workflows.md, and design/ui-flow.md. Revision R-MGA in `approval.md` approves this revision for T078–T081; it supersedes no other approval record. This plan otherwise supersedes the legacy Member Profile plan entirely.

---

## Summary

FE-001 delivers the Mland account lifecycle: email/password and Google redirect sign-in, in-place credential re-authentication, profile/credential self-service, Admin provisioning and maintenance of internal Staff/Manager accounts, account suspension/restoration, a shared backend identity gate, a typed internal facade for Workshop consumers, and member shipping address book management (Shopee model) with GHTK-compatible administrative divisions and order snapshot facade for FE-008 Retail Order & Fulfillment. It also establishes system-wide timezone standardization to Vietnam local time (`Asia/Ho_Chi_Minh`, UTC+7) for MySQL persistence, JDBC connection, and API responses. Email verification is observed on entry to a capability that requires it: the frontend reloads Firebase user state, forces a fresh bearer, synchronizes the Account snapshot, and the backend still decides the command. No Firebase callback/webhook, custom action handler, or background verification job is part of V1. This plan reflects every clarification session through 2026-11-06, including the R-SL browser-session revision.

**Technical approach**: React + Vite SPA (client-side); Java 21 / Spring Boot REST / Spring Modulith backend; Firebase Authentication for identity/credential management; MySQL + Flyway migrations; JPA/Spring Data persistence; FE-07 (Notification) and FE-003 (Workshop) as downstream consumers.

---

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript / React 19 + Vite (frontend)

**Primary Dependencies**:
- Backend: Spring Boot (latest approved), Spring Modulith, Spring Data JPA, Flyway, Firebase Admin SDK, Spring Security
- Frontend: React 19, Vite, Firebase Web SDK, React Router

**Storage**: MySQL (authoritative business state — Account, Profile, MemberAddress, Assignment, Provisioning, Audit, NotificationIntent; all `DATETIME(6)` values standardized on `Asia/Ho_Chi_Minh` UTC+7); Firebase Authentication (credentials, bearer tokens, session invalidation)

**Testing**: JUnit 5 / Spring Boot Test / Spring Modulith module verification (backend); Vitest / React Testing Library (frontend). No TDD mandate in spec; tests are required only for integration evidence items listed in `verification.md`.

**Target Platform**: Local development environment (`http://localhost:5173` frontend, Spring Boot backend). Staging/production is a separate approved release scope.

**Project Type**: Full-stack web service — separate React SPA + Spring Boot REST API modular monolith (Spring Modulith)

**Performance Goals**: Standard SPA/REST latency for a small-to-medium business; no explicit throughput target in V1 spec.

**Constraints**:
- Bearer-only credential transport; no browser auth cookie, server session, app JWT, or refresh-token system (V1)
- Local CORS origin is exactly `http://localhost:5173`; no wildcard; `Access-Control-Allow-Credentials: false`
- `spring.jpa.hibernate.ddl-auto=validate`; no `update`, Flyway bypass, or test-only schema workaround
- Flyway migrations use repository-wide append-only integer sequence; no editing of applied migrations; new `member_addresses` table created via append-only migration
- System timezone is strictly standardized to `Asia/Ho_Chi_Minh` (UTC+7): JDBC URL enforces `connectionTimeZone=Asia/Ho_Chi_Minh&forceConnectionTimeZoneToSession=true`, application default timezone is `Asia/Ho_Chi_Minh`, and Jackson serializers output ISO-8601 with `+07:00` offset
- `AccountAuditEvent.outcome` must use Java `AuditOutcome` enum (`SUCCESS`/`FAILURE`) persisted as `VARCHAR(16)`; append-only migration from V5 is required before any MySQL integration test
- No cache, event replication, cross-module transaction, or Workshop Location scope in the identity gate actor
- Member verification email is sent by Firebase Web SDK on the signed-in owner’s explicit request; V1 uses Firebase’s default action handler and does not persist email action codes

**Scale/Scope**: 6 user stories; 40 functional requirements; 10 workflows; 7 backend entity types; 4 facade methods (2 for Workshop consumers, 2 for FE-008 shipping address snapshot); local-only acceptance

---

## Constitution Check

*GATE: Must pass before Phase 0. Re-checked after Phase 1 design.*

| Principle | Status | Evidence |
|-----------|--------|----------|
| I — Module Integrity | ✅ MET | FE-001 owns all Account entities/DAOs/services; only `facade/` is exposed cross-feature. Spring Modulith package structure enforced: `features/accountmanagement/{controller,dto,dao,entity,service,facade}`. No raw SQL; Flyway append-only. `workshopLocationId` is canonical Workshop scope identifier; no Branch ID or mapping. |
| II — Identity & Sensitive Data | ✅ MET | Firebase verifies identity only; MySQL is authoritative for roles/status/audit. No password/token capture or storage. Credentials via approved environment/secret channel. Audit records are redacted. |
| III — Contract & Separate Frontend | ✅ MET | Versioned OpenAPI governs REST; separate React + Vite SPA. CORS exact-origin; CSRF scoped to bearer transport. Every route has a security policy; deny-by-default identity gate. |
| IV — Evidence-Based Verification | ⚠️ PARTIAL | Design artifacts complete; implementation evidence pending. Flyway fresh/upgrade validation, Spring Modulith boundary verification, and UI/API integration tests are required before handoff. `verification.md` must be revised. |
| V — Simplicity & Explicit Decisions | ✅ MET | Smallest approved scope (fixed roles, 3 statuses, no RBAC, no caching, no cross-module lock). All open decisions recorded in architecture.md and workflows.md. Non-local origins/operations explicitly deferred. |

**Complexity Tracking** (Constitution §V — items requiring justification):

| Item | Why Needed | Simpler Alternative Rejected Because |
|------|------------|--------------------------------------|
| Firebase Admin SDK (external) | Identity/credential management is outside Mland domain; Firebase owns revocation, verification links, and session invalidation | Building in-house credential store conflicts with Principle II and is out of scope |
| FE-07 notification boundary | Account owns business decision/audit; delivery ownership must be separate to avoid coupling commitment to delivery outcome | Direct email delivery would couple Account transaction to mail SMTP availability |
| Workshop Location facade dependency | FE-001 must validate canonical location IDs and resolve effective scope; it must not own location data | Owning a duplicate location record or branch mapping violates Constitution §I |

---

## Project Structure

### Documentation (this feature)

```text
ppswbs-spec/features/001-account-management/
├── spec.md                    # Approved specification (pass 16/16)
├── plan.md                    # This file (speckit-plan output)
├── research.md                # Phase 0 decisions (speckit-plan output)
├── data-model.md              # Approved data model (speckit-plan output)
├── quickstart.md              # Validation guide (speckit-plan output)
├── contracts/
│   ├── interfaces.md          # Internal facade contract (speckit-plan output)
│   └── account-management.openapi.yaml  # REST transport contract (regeneration required)
├── design/
│   ├── architecture.md        # Architecture design (source)
│   ├── workflows.md           # Workflow design (source)
│   └── ui-flow.md             # UI flow design (source)
├── checklists/
│   └── requirements.md        # Spec quality checklist (pass 16/16)
├── tasks.md                   # Dependency-ordered tasks (speckit-tasks output)
└── verification.md            # Verification plan (revision required)
```

### Source Code (repository root)

```text
ppswbs_frontend/src/features/accountmanagement/
├── api/
│   └── AccountManagementApiClient.ts      # REST routes per OpenAPI; bearer attachment
├── auth/
│   └── FirebaseAuthClient.ts              # Firebase registration/sign-in/out; never role decision
├── hooks/
│   ├── useCurrentAccount.ts               # Bootstrap/load current Account; safe recovery
│   ├── useProfile.ts                      # Own-profile form state/validation/retry
│   └── useAdminAccounts.ts               # Admin list/detail/form state/version recovery
└── components/
    ├── AuthPage.tsx                        # Sign-in / registration
    ├── ProfilePage.tsx                     # Own profile and security
    ├── AdminAccountListPage.tsx            # Admin account list/filter
    ├── AdminAccountDetailPage.tsx          # Admin account detail/actions
    ├── InternalAccountCreatePage.tsx       # Admin create/resend internal account
    └── RoleCataloguePage.tsx              # Fixed role catalogue (read-only)

ppswbs_backend/src/main/java/.../
└── features/accountmanagement/
    ├── controller/
    │   ├── AccountController.java          # /accounts/me — bootstrap, profile, email sync
    │   ├── AdminAccountController.java     # /admin/accounts — list, detail, create, resend, update
    │   └── RoleCatalogueController.java    # /roles — fixed role catalogue
    ├── controller/
    │   └── AccountExceptionHandler.java    # FE-001 validation/business error mapping
    ├── dto/
    │   ├── request/                        # Input DTOs per OpenAPI
    │   └── response/                       # Response DTOs per OpenAPI
    ├── service/
    │   ├── IdentityGateFilter.java         # Bearer verify → actor resolve → default deny
    │   ├── TokenVerificationService.java   # Firebase Admin SDK verification adapter
    │   ├── AccountBootstrapService.java    # Find/create Account + Profile from verified identity
    │   ├── ProfileService.java             # Own profile get/save
    │   ├── EmailSyncService.java           # Verified identity email sync
    │   ├── AdminAccountService.java        # Provision/invite/edit/list/detail
    │   ├── SuspensionService.java          # Suspend/restore/revocation reconciliation
    │   └── AuditService.java              # Best-effort redacted audit; safe operational signal
    ├── facade/
    │   ├── AccountFacade.java             # Interface: resolveActor, findTargetFacts, findEligibleRoster
    │   ├── AccountFacadeImpl.java         # Implementation
    │   ├── CurrentAccountActor.java       # Boundary record: accountId, role, status, emailVerified
    │   ├── AccountId.java                 # Opaque canonical positive-integer boundary type
    │   ├── TargetAccountFacts.java        # FOUND value: accountId, role, status, activeWorkshopLocationIds
    │   ├── TargetFactsOutcome.java        # Sealed: FOUND | NOT_FOUND | UNAVAILABLE
    │   ├── InternalRosterEntry.java       # accountId, displayName, role (STAFF only)
    │   └── RosterOutcome.java             # Sealed: AVAILABLE(entries) | UNAVAILABLE
    ├── dao/
    │   ├── AccountRepository.java
    │   ├── AccountProfileRepository.java
    │   ├── AccountLocationAssignmentRepository.java
    │   ├── InternalAccountProvisioningRepository.java
    │   └── AccountAuditEventRepository.java
    └── entity/
        ├── Account.java                    # firebase_uid, login_email, role, status, resume_status, version
        ├── AccountProfile.java             # account_id (PK/FK), full_name, phone
        ├── AccountLocationAssignment.java  # account_id, location_id, active flag
        ├── InternalAccountProvisioning.java # idempotency key, digest, lifecycle progress
        └── AccountAuditEvent.java          # actor/target, action, AuditOutcome, correlation_id

ppswbs_backend/src/main/java/.../configuration/
└── SecurityConfiguration.java             # Default-deny filter chain, exact-origin CORS, bearer CSRF
```

**Structure Decision**: Web application (Option 2) — separate React + Vite frontend and Spring Boot backend. Backend is a Spring Modulith modular monolith; FE-001 is one module under `features/accountmanagement/`. Dependency direction: UI component → hook → API/Firebase client → controller → service → repository or named facade.

---

## Phase 0: Research Summary

> Resolved during clarification sessions 2026-10-24 to 2026-11-01. See [research.md](research.md) for full decision records.

| Unknown / Risk | Resolution |
|----------------|------------|
| Account identity vs. email ownership | Verified Firebase UID is the identity key; email is a current snapshot. No merge by email alone. |
| Bootstrap behavior for unverified / missing email | Unverified email → create Account+Profile, apply verification gates. No email claim → no Account/Profile; safe recovery guidance. |
| PENDING_SETUP activation rule | Requires intended UID sign-in + verified email + valid active Workshop scope. Never creates a Member Account. |
| Workshop scope in identity gate | Gate resolves current actor WITHOUT Workshop scope. An active Manager's Manager-authorized action uses current role/status without a Location lookup; a Staff location-scoped action resolves effective scope per action via facade immediately before mutation. |
| Target facts facade typed contract | `findTargetFacts(AccountId)` → `TargetFactsOutcome` for targets/Staff scope; `findEligibleRoster(workshopLocationId)` → Staff-only `RosterOutcome`. Named types in contracts/interfaces.md. |
| Audit outcome schema conflict | Approved baseline: Java `AuditOutcome` enum + `VARCHAR(16)` via append-only migration; `V5` must not be edited. |
| Idempotency for internal account create | Idempotency key + normalized digest; same key + same digest returns preserved result; same key + different digest → 409. |
| CORS and credential transport | Exact local origin `http://localhost:5173`; `Authorization: Bearer`; no cookies; `Allow-Credentials: false`. |
| Member verification synchronization | Only on Profile entry or its accessible verification-status reload icon: `reload` Firebase user → forced-fresh bearer → bodyless `PUT /accounts/me/email` → enable Profile save. Resend cooldown is 90 seconds in account-scoped `localStorage`; Firebase remains quota authority. Email change uses `verifyBeforeUpdateEmail`; no custom handler, callback/webhook, or background job. |
| Flyway version convention | Repository-wide append-only integer sequence; reserve next number before creating migration. |

---

## Phase 1: Design & Contracts

> All Phase 1 artifacts are generated/approved. See source documents for authoritative content.

### Data Model

See [data-model.md](data-model.md) — authoritative physical schema design for all entities.

Key invariants:
- `Account.firebase_uid` unique; account IDs are opaque positive integers (canonical string in REST/UI)
- `AccountProfile` created atomically with bootstrap when verified UID has valid email; `full_name` from email local part
- `AuditOutcome` enum: exactly `SUCCESS` and `FAILURE`; persisted as `VARCHAR(16)` (append-only migration required)
- `AccountLocationAssignment`: no local location entity; validates against authoritative Workshop Location facade
- `resume_status` populated on suspension of `ACTIVE` or `PENDING_SETUP`; consumed on successful restoration

### Internal Interface Contracts

See [contracts/interfaces.md](contracts/interfaces.md) — named facade types for cross-feature boundaries.

Facade methods (Java module boundary only — no REST endpoints):

| Method | Inputs | Typed result |
|--------|--------|--------------|
| `resolveActor(bearerToken)` | Firebase ID token | `CurrentAccountActor` or typed identity outcome |
| `findTargetFacts(AccountId)` | Opaque canonical Account ID | `TargetFactsOutcome`: FOUND / NOT_FOUND / UNAVAILABLE |
| `findEligibleRoster(workshopLocationId)` | Canonical location ID | Staff-only `RosterOutcome`: AVAILABLE(entries) / UNAVAILABLE |

External boundaries:
- Firebase adapter: bearer verify/revocation, identity create/setup links, session invalidation
- Workshop Location facade: active location ID validation and display summary (owned by FE-003.1)
- FE-07 boundary: post-commit notification intent delivery

### REST Transport Contract

**Status**: `contracts/account-management.openapi.yaml` regenerated to full OpenAPI 3.1.0 specification (2026-11-03). It defines all request/response DTOs, parameters, error schemas, headers (Idempotency-Key, X-Correlation-ID), and security schemes matching the backend implementation and approved plan.

Approved route groups:
- `POST /accounts/me` — bootstrap current identity
- `GET /accounts/me`, `PUT /accounts/me/profile`, `PUT /accounts/me/email` — self-service; the email route has no body and synchronizes only current verified Firebase identity
- Credential recovery, password change, and Member verification/resend are Firebase Web SDK actions; FE-001 exposes no backend credential-flow or Member-email-delivery route
- `GET /admin/accounts`, `POST /admin/accounts`, `GET /admin/accounts/{accountId}` — Admin list/detail/create
- `PUT /admin/accounts/{accountId}/profile`, `PUT /admin/accounts/{accountId}/role-location`, `PUT /admin/accounts/{accountId}/status` — Admin maintenance (version-safe)
- `POST /admin/accounts/{accountId}/resend-invitation` — pending-only resend with 5-min cooldown
- `GET /roles` — fixed role catalogue (Admin only)

All routes use `Authorization: Bearer`; public-route allowlist is explicit in `SecurityConfiguration`. Error envelope: `code`, `message`, `correlationId`, `timestamp`, optional `fieldErrors`.

### Validation Guide

See [quickstart.md](quickstart.md) — runnable local validation scenarios proving end-to-end behavior.

---

## Libraries and Integrations

| Layer | Library / version | Status | Purpose |
|-------|-------------------|--------|---------|
| Frontend | React 19 + Vite | approved — Constitution | Client application |
| Frontend identity | Firebase Web SDK | approved baseline; config dependency | Client identity flows |
| Frontend router | React Router | approved | Client-side routing |
| Backend | Java 21, Spring Boot | approved — Constitution | REST service |
| Backend modulith | Spring Modulith | approved | Module boundary enforcement |
| Backend persistence | JPA/Spring Data, Flyway, MySQL | approved — Constitution | ORM, migrations, storage |
| Backend identity | Firebase Admin SDK | existing integration; config dependency | Bearer/revocation verify |
| Notification | FE-07 boundary | dependency; contract absent | Post-commit delivery request |
| Workshop Location | FE-003.1 facade | dependency; contract must be cited | Active location validation |

No cache, event replication, personnel identifier, generic RBAC store, app session store, or cross-module transaction is approved for V1.

---

## Open Blockers & Gate Status

| Gate | Status | Resolution / Action |
|------|--------|---------------------|
| D01 | MET | Spec purpose, scope, stories, acceptance scenarios defined |
| D02 | MET | `contracts/account-management.openapi.yaml` regenerated to full OpenAPI 3.1.0 |
| D03 | MET | Spring Modulith module structure verified in backend code |
| D04 | MET | Workflows WF-01 through WF-08 defined in `design/workflows.md` |
| D05 | MET | Server-side authorization & denial behavior specified |
| D06 | MET | Full OpenAPI 3.1 + facade contract in `contracts/interfaces.md` |
| D07 | PENDING EVIDENCE | Append-only migration V{N} exists; T003 fresh+upgrade MySQL evidence must be run by implementation persona |
| D08 | MET | Lifecycle states & resume_status semantics specified |
| D09 | MET | Bearer-only transport, no server session |
| D10 | MET | Transaction boundaries & post-commit notifications defined |
| D11 | MET | Concurrency (version) & idempotency (key + digest) defined |
| D12 | MET | External boundaries (Firebase, FE-07, Workshop Location) defined |
| D13 | MET | Sensitive data minimization & audit redaction specified |
| D14 | OPEN | WCAG 2.2 AA scan (Phase 8 T047, deferred for MVP) |
| D15 | PENDING EVIDENCE | T003 fresh/upgrade MySQL evidence required |
| D16 | OUT OF SCOPE | Non-local origins explicitly deferred to production release scope |
| D17 | OUT OF SCOPE | Observability & rollout deferred to production release scope |
| D18 | MET | `tasks.md` regenerated and aligned with plan |
| D19 | MET | `verification.md` revised to comprehensive verification plan |
| D20 | READY | `approval.md` drafted for Tech Lead Phan Sơn sign-off |

**Implementation resume condition**: OpenAPI 3.1 regenerated ✅; verification.md revised ✅; tasks.md aligned ✅; Tech Lead approval record confirmed.

---

## Revision 2026-11-06 — shared browser-session implementation handoff

This revision adds the frontend-only shared lifecycle required by FR-041 and `spec-general.md`; it creates no REST route, OpenAPI change, database change, Firebase project, emulator, Hosting configuration, or backend authorization change.

| Proposed frontend unit | Responsibility and public use | Boundary/invariant |
| --- | --- | --- |
| `shared/auth/AuthProvider` (revised) | Publishes the authoritative Firebase session state (`loading`, `unconfigured`, `signed-out`, `signed-in`) and emits an explicit terminal outcome for user sign-out or session loss. | Does not navigate or own account/role data. A user-sign-out outcome wins its matching Firebase-null observation. |
| `shared/auth/SessionNavigationBoundary` (new) | Consumes the provider outcome once; clears registered private UI/draft state; invokes Router navigation and Home flash state. | The only client unit permitted to execute terminal session navigation. First `SESSION_LOST` wins until a new signed-in session. |
| `shared/auth/RequireSession` (revised) | Guards declared authenticated routes after `AuthProvider` resolves. | `loading` renders neutral non-PII UI; `unconfigured` renders unavailable with no redirect; signed-out uses `/login` with `replace`. |
| Router route configuration (revised) | Declares public/Member/internal route categories, canonical login and Account URLs, compatibility aliases. | `/sign-in` replaces to `/login`; aliases keep the same guard; public Feature-003.1 booking routes remain public. |
| `Home` session notice presentation (new/revised) | Reads transient `location.state` session notice, renders localized safe notice, lets user dismiss it. | No browser storage; clear on dismiss/leave; never render token/provider detail. |
| `FirebaseAuthClient` / `AuthPage` (revised) | Supports Google `signInWithRedirect` and `getRedirectResult`; maps redirect errors. | Popup APIs, popup handlers and popup acceptance are removed from supported scope. Collision directs to Email/Password; other errors offer safe retry/alternative method. |

**Data flow:** UI sign-out / Firebase observer / authenticated HTTP client → `AuthProvider` outcome → `SessionNavigationBoundary` → registered private-state cleanup → `navigate('/', { replace: true, state: { sessionNotice } })` → Home notice. A guest route resolution flows `Router → RequireSession → navigate('/login', { replace: true })`; successful authentication flows `AuthPage → navigate('/', { replace: true })`. A `403` is an authorization outcome sent to the same boundary as a non-terminal safe-home notice and does not call Firebase sign-out.

**Revision-specific gate:** implementation starts only after the approval revision in `approval.md` is present. Completion requires the new unstarted tasks and V-13 verification evidence; historical task checkmarks and V-12 evidence do not prove FR-041 or redirect-only scope.
