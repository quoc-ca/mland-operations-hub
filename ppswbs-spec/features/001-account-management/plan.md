# Implementation Plan: Account Management

**Date created**: 2026-10-26
**Date updated**: 2026-10-26
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Feature**: `001-account-management` | **Branch**: `001-account-management`
**Status**: Draft design — implementation requires separate approval.
**Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md)

## Summary

Build FE-001 as the Account Management module and the shared backend identity
gate. Firebase proves bearer identity; Account Management resolves the current
Mland Account and exposes only `CurrentAccountActor` to other modules. Each
business feature maps that actor to its own action authority; FE-001 does not
implement dynamic RBAC or cross-feature capabilities.

The frontend is a separate React + Vite application. This plan assumes its
foundation supplies router, Firebase client, authenticated request client, and
environment configuration; FE-001 supplies feature views and API integration,
not application scaffold or Home/dashboard implementation.

## Technical Context

| Item | Selected design |
| --- | --- |
| Backend | Java 21, Spring Boot REST, Spring Modulith, JPA/Spring Data, Flyway, MySQL; no backend-rendered UI. |
| Identity | Firebase ID token in `Authorization: Bearer`; signature, issuer, audience, expiry, and revocation verification. No local password, application JWT, refresh-token system, MFA, or fixed eight-hour re-login in V1. |
| Shared request security | One application security chain and identity gate. Explicit contract-declared public routes only; all other routes deny by default. Gate resolves `CurrentAccountActor(accountId, role, status, emailVerified, activeLocationIds)`. |
| CORS/CSRF | CORS uses configured frontend-origin allowlist. CSRF is scoped only to a future approved cookie flow; bearer APIs do not disable either control globally. |
| Configuration | Required DB/provider values come from approved environment/secret channels, with no credential default/fallback in committed properties. |
| Migration | Repository-wide append-only integer sequence. Existing V5/V6 remain; reserve the next number before a new migration. |
| State | Stateless bearer verification per request; retained Account/profile/location/provisioning/audit/revocation state in MySQL. |

## Constitution Check

| Principle | Design response |
| --- | --- |
| I. Module ownership | `accountmanagement` owns persistence and exposes only named `facade` types; other features never import entity, DAO, service, Firebase adapter, or controller types. |
| II. Identity/data boundary | Firebase remains credential/identity provider; MySQL owns role/status/scope/audit. UID/token/password/action-link never enters public actor/audit/log payloads. |
| III. REST/frontend | Versioned plural REST DTOs, typed correlation-linked errors, server authorization, separate React UI, default-deny security and explicit public routes. |
| IV. Evidence | Contract, authorization, Modulith boundary, MySQL migration/upgrade, Auth Emulator, frontend/API, concurrency and failure-path evidence are required. |
| V. Simplicity/state | One gate and one Actor Context replace per-feature token parsing. Retained state is limited to Account lifecycle/provisioning/revocation/audit; no global session store. |

## Module and dependency design

`features/accountmanagement` has `controller`, `dto`, `dao`, `entity`,
`service`, `facade`, and an internal Firebase adapter. `facade` is a named
Spring Modulith interface and publishes boundary records/enums only.

The identity gate calls an Account-owned verifier/resolver, puts the immutable
`CurrentAccountActor` in request security context, and returns typed `401` or
`503` without leaking token details. It permits only routes marked public by
the owning API contract. Feature controllers/services consume the actor and
apply their own action/resource checks; they do not derive authority from
Firebase claims or frontend state.

Account Management calls the workshop-location facade to validate current active
locations. It emits idempotent post-commit notification intents to FE-07; it
does not call SMTP inside an account transaction. It has no dependency on
product management; FE-002 consumes the Account facade unidirectionally.

## Main flows and state

1. Frontend completes an allowed Firebase sign-in and calls account bootstrap
   with bearer token. The gate verifies identity/revocation and resolves UID.
2. Bootstrap returns the existing Account or creates exactly one `MEMBER` for a
   previously unknown UID. It never merges by email or alters a pre-existing
   Staff/Manager/Admin account.
3. An unverified user reaches `/` with a reminder. Functions requiring verified
   email deny with a typed recovery outcome. Profile/email synchronization read
   only current verified Firebase identity.
4. Admin creation stores an idempotent provisioning request, creates/reconciles
   one Firebase identity, creates `PENDING_SETUP` Account and audit/notification
   intent, then FE-07 sends after commit. Resend uses a fresh action link and a
   five-minute cooldown.
5. A pending Staff/Manager becomes `ACTIVE` only after the intended verified UID
   signs in and active location assignment validates. Staff has one location;
   Manager has one or more.
6. Suspension immediately sets `SUSPENDED`, audits and schedules Firebase
   revocation. Protected APIs deny regardless of revocation/email delivery
   outcome. Restoration waits for completed revocation, restores the recorded
   pre-suspension state, and requires fresh sign-in.

## API and error design

The public contract is [account-management.openapi.yaml](contracts/account-management.openapi.yaml).
Important routes are bootstrap/profile/email sync, Admin list/detail/internal
creation/invitation/profile/role-assignment/status, and role catalogue.

All protected endpoints use bearer identity. `401` is missing/invalid/revoked
identity, `403` is verified identity without eligible account/role/status/scope,
`409` covers stale version/idempotency conflict, and `503` is a dependency
failure. Error body is `code`, safe `message`, `correlationId`, timestamp and
optional field errors. Account mutations audit success/failure safely.

## Data, transactions, and concurrency

See [data-model.md](data-model.md). `Account` is UID-unique and versioned;
admin profile/role/status commands require expected version. Own profile writes
use last successfully committed value. Internal create uses a persisted
idempotency key and request digest. Account mutation, redacted audit, and
notification intent commit atomically; Firebase create/action-link/revoke are
reconciled by durable provisioning/revocation state instead of assumed rollback.

## UI design

Feature routes are `/sign-in`, `/register`, `/forgot-password`, `/profile`,
`/security/password`, `/security/email`, `/admin/accounts`,
`/admin/accounts/new`, `/admin/accounts/:id`, and `/admin/roles`. Successful
sign-in always goes to `/`. Each screen supplies loading, empty, validation,
success, failure/retry, `401`, `403`, `409`, and `503` recovery. Route guards
are UX only; server authorization is decisive.

## Verification plan

Run the scenarios in [quickstart.md](quickstart.md): Auth Emulator identity
paths, MySQL empty/upgrade migrations, concurrency/idempotency, suspension and
revocation recovery, FE-07 failure isolation, API/error/authorization contract,
Modulith boundary, and frontend/API states. Record actual commands/environment
and failures; no fixture or mock-only result proves provider integration.

## Design risks and handoff gates

- FE-07 notification intent/outbox contract and workshop-location facade must be
  available before their integrations are enabled; failures remain typed and do
  not grant access.
- Frontend origin values and Admin bootstrap runbook are deployment/operations
  inputs. Their absence blocks enablement, not the design of the feature.
- Current backend code and legacy artifacts are not evidence of compliance. The
  first implementation work must replace global `permitAll`, default runtime
  credentials, and entity-leaking Account facade before FE-002 consumes it.

No code, migration, deployment, or approval is authorized by this plan.
