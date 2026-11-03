# Implementation Plan: Account Management (FE-001)

**Date created**: 2026-10-26
**Date updated**: 2026-11-01
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Branch**: `son` | **Spec**: [spec.md](spec.md)

**Input**: Regenerated from approved design artifacts: spec.md (pass 16/16), data-model.md, contracts/interfaces.md, design/architecture.md, design/workflows.md, design/ui-flow.md, checklists/requirements.md. Supersedes the legacy Member Profile plan entirely.

---

## Summary

FE-001 delivers the Mland account lifecycle: email/password and optional social sign-in, profile/credential self-service, Admin provisioning and maintenance of internal Staff/Manager accounts, account suspension/restoration, a shared backend identity gate, and a typed internal facade for Workshop consumers. All design artifacts pass the 16/16 spec-quality checklist; this plan reflects every clarification session through 2026-11-01.

**Technical approach**: React + Vite SPA (client-side); Java 21 / Spring Boot REST / Spring Modulith backend; Firebase Authentication for identity/credential management; MySQL + Flyway migrations; JPA/Spring Data persistence; FE-07 (Notification) and FE-003 (Workshop) as downstream consumers.

---

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript / React 18 + Vite (frontend)

**Primary Dependencies**:
- Backend: Spring Boot (latest approved), Spring Modulith, Spring Data JPA, Flyway, Firebase Admin SDK, Spring Security
- Frontend: React 18, Vite, Firebase Web SDK, React Router

**Storage**: MySQL (authoritative business state — Account, Profile, Assignment, Provisioning, Audit, NotificationIntent); Firebase Authentication (credentials, bearer tokens, session invalidation)

**Testing**: JUnit 5 / Spring Boot Test / Spring Modulith module verification (backend); Vitest / React Testing Library (frontend). No TDD mandate in spec; tests are required only for integration evidence items listed in `verification.md`.

**Target Platform**: Local development environment (`http://localhost:5173` frontend, Spring Boot backend). Staging/production is a separate approved release scope.

**Project Type**: Full-stack web service — separate React SPA + Spring Boot REST API modular monolith (Spring Modulith)

**Performance Goals**: Standard SPA/REST latency for a small-to-medium business; no explicit throughput target in V1 spec.

**Constraints**:
- Bearer-only credential transport; no browser auth cookie, server session, app JWT, or refresh-token system (V1)
- Local CORS origin is exactly `http://localhost:5173`; no wildcard; `Access-Control-Allow-Credentials: false`
- `spring.jpa.hibernate.ddl-auto=validate`; no `update`, Flyway bypass, or test-only schema workaround
- Flyway migrations use repository-wide append-only integer sequence; no editing of applied migrations
- `AccountAuditEvent.outcome` must use Java `AuditOutcome` enum (`SUCCESS`/`FAILURE`) persisted as `VARCHAR(16)`; append-only migration from V5 is required before any MySQL integration test
- No cache, event replication, cross-module transaction, or Workshop Location scope in the identity gate actor

**Scale/Scope**: 5 user stories; ~34 functional requirements; 7 workflows; ~6 backend entity types; 2 facade methods for Workshop consumers; local-only acceptance

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
    │   ├── InternalRosterEntry.java       # accountId, displayName, role (STAFF or MANAGER only)
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
| Workshop scope in identity gate | Gate resolves current actor WITHOUT Workshop scope. Scope is resolved per-action via facade immediately before mutation. |
| Target facts facade typed contract | `findTargetFacts(AccountId)` → `TargetFactsOutcome`; `findEligibleRoster(workshopLocationId, role)` → `RosterOutcome`. Named types in contracts/interfaces.md. |
| Audit outcome schema conflict | Approved baseline: Java `AuditOutcome` enum + `VARCHAR(16)` via append-only migration; `V5` must not be edited. |
| Idempotency for internal account create | Idempotency key + normalized digest; same key + same digest returns preserved result; same key + different digest → 409. |
| CORS and credential transport | Exact local origin `http://localhost:5173`; `Authorization: Bearer`; no cookies; `Allow-Credentials: false`. |
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
| `findEligibleRoster(workshopLocationId, role)` | Canonical location ID + STAFF\|MANAGER | `RosterOutcome`: AVAILABLE(entries) / UNAVAILABLE |

External boundaries:
- Firebase adapter: bearer verify/revocation, identity create/setup links, session invalidation
- Workshop Location facade: active location ID validation and display summary (owned by FE-003.1)
- FE-07 boundary: post-commit notification intent delivery

### REST Transport Contract

**Status**: `contracts/account-management.openapi.yaml` exists but is declared obsolete by spec.md. It must be regenerated to reflect the approved design before implementation. The plan records the approved routes; actual HTTP contract authority remains the regenerated OpenAPI file.

Approved route groups:
- `POST /accounts/me` — bootstrap current identity
- `GET /accounts/me`, `PUT /accounts/me/profile`, `POST /accounts/me/email-sync` — self-service
- `POST /accounts/me/password-change`, `POST /auth/password-reset`, `POST /auth/verification-email` — credential flows (Firebase-delegated)
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
| Frontend | React 18 + Vite | approved — Constitution | Client application |
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

## Open Blockers (from architecture.md gate review)

| Gate | Status | Required action before implementation |
|------|--------|---------------------------------------|
| D02 | OPEN | Regenerate `account-management.openapi.yaml` from approved routes |
| D03 | OPEN | Add Spring Modulith boundary verification evidence |
| D06 | OPEN | Publish regenerated OpenAPI (facade contract is now in interfaces.md) |
| D07 | OPEN | Implement append-only Flyway migration for `AuditOutcome`/`VARCHAR(16)`; retain fresh+upgrade evidence |
| D14 | OPEN | WCAG 2.2 AA + Chrome/Edge/Firefox latest 2 desktop — frontend foundation owner |
| D15 | OPEN | Append-only migration + fresh/upgrade MySQL evidence required |
| D16 | OPEN | Non-local origins remain unresolved; deployment owner must supply |
| D17 | OPEN | Observability/rollout owner absent |
| D18 | OPEN | `tasks.md` regenerated (this plan is the prerequisite) |
| D19 | OPEN | `verification.md` revision required |
| D20 | OPEN | No complete Tech Lead approval record; blockers prevent handoff |

**Implementation resume condition**: tasks.md regenerated from this plan; append-only Flyway migration implemented and MySQL evidence retained; OpenAPI regenerated; Spring Modulith verification evidence captured; then Tech Lead approval record created.
