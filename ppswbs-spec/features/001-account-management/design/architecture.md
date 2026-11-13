# Architecture Design: Account Management (FE-001)

**Date created**: 2026-10-28
**Date updated**: 2026-11-14
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Design status**: Draft revision — the 2026-11-14 Manager-global authorization flow is proposed and pending Tech Lead approval. Historical approval, including Revision R-SL, remains limited to the scopes recorded in `approval.md`.

## Scope and source map

| Requirement / scenario | Authoritative source | Architecture response | Task | Verification |
| --- | --- | --- | --- | --- |
| Account lifecycle, self-service, internal administration | [spec.md](../spec.md) FR-001–FR-027 | FE-001 owns Account, Profile, assignment, lifecycle, identity reconciliation, audit and notification intent | Implemented in [tasks.md](../tasks.md) | Verified in [verification.md](../verification.md) (V-01–V-12 pass) |
| Shared request actor | FR-026–FR-027; [interfaces.md](../contracts/interfaces.md) | Identity gate verifies Firebase bearer then resolves current Account actor; consumers make their own business decision | Implemented & verified | Invalid/revoked bearer, suspended Account, unavailable actor resolution |
| Current target facts and roster for Workshop | FR-028–FR-032 | Named, typed, read-only Account facade; never a REST directory or capability service | Implemented & verified | FOUND/NOT_FOUND/UNAVAILABLE; scope change following recheck |
| Opaque identifier | FR-033; [OpenAPI](../contracts/account-management.openapi.yaml) | REST/UI uses canonical positive-integer string; Java facade uses a boundary type | Implemented & verified | Reject padding, sign, decimal, blank, UID and alternate identifier |
| Schema and persistence | [data-model.md](../data-model.md) | Data model remains schema/ownership authority; no duplicate Workshop Location entity | Implemented & verified | UID uniqueness, state, version, idempotency, migration/upgrade |
| Public HTTP transport | [OpenAPI](../contracts/account-management.openapi.yaml) | Existing routes remain transport authority pending contract regeneration; no route is inferred by this document | Implemented & verified | OpenAPI/error validation |
| Member shipping address book | [spec.md](../spec.md) FR-036–FR-039 | FE-001 owns MemberAddress entity, CRUD, quota (max 10), default address rules, phone normalization, and administrative division catalogue | T038–T045 | V-10 |
| Timezone standardization (UTC+7) | [spec.md](../spec.md) FR-035 | System-wide UTC+7 (`Asia/Ho_Chi_Minh`) for MySQL `DATETIME(6)`, JDBC connection, JVM default timezone and Jackson | T001–T003 | V-11 |
| Shipping address facade for FE-008 | [spec.md](../spec.md) FR-040; [interfaces.md](../contracts/interfaces.md) | Modulith facade methods returning immutable `MemberAddressSnapshot` for consumer checkout snapshotting | T043 | V-10 |

## Approved decisions and non-decisions

| Topic | Status / source | Decision |
| --- | --- | --- |
| Effective Workshop scope | **approved** — Tech Lead Phan Sơn, 2026-10-29 | Field name is `activeWorkshopLocationIds`. Its exact meaning is active Account assignments intersected with currently active authoritative Workshop Locations; it is not raw assignment data. It is resolved only for a location-scoped action, never by the identity gate. |
| Browser credential transport | **approved** — Tech Lead Phan Sơn, 2026-10-28 | V1 sends Firebase ID token in `Authorization: Bearer`; no browser auth cookie, server session, application JWT or refresh-token system. |
| Local CORS origin | **approved** — Tech Lead Phan Sơn, 2026-10-28 | Local environment permits exactly `http://localhost:5173`. No wildcard origin or credentials mode. |
| CORS behavior | **approved** — Tech Lead Phan Sơn, 2026-10-28 | Allow only required methods/headers, including `Authorization`, `Content-Type`, `Idempotency-Key`, `X-Correlation-ID` when used; preflight is limited to configured exact origins; `Access-Control-Allow-Credentials` is false. CSRF must be redesigned before any future cookie flow. |
| Error ownership/envelope | **approved** — Tech Lead Phan Sơn, 2026-10-28 | Identity gate owns admission `401/403`; each feature owns its business errors; shared technical fallback owns only unhandled technical failures. Envelope is `code`, safe `message`, `correlationId`, `timestamp`, optional `fieldErrors`. |
| Non-local origins / operations | **open dependency** | Staging, preview and production origins, deployment owner, observability and recovery ownership have no source; they are not inferred from local CORS. |

## Implementation tree and ownership

```text
ppswbs_frontend/src/features/accountmanagement/                         [proposed — spec FR-001–FR-025]
  api/AccountManagementApiClient.ts                                     [proposed]
  auth/FirebaseAuthClient.ts                                             [proposed]
  hooks/{useCurrentAccount,useProfile,useVerifiedEmailGate,useAdminAccounts}.ts [proposed]
  components/{AuthPage,ProfilePage,AdminAccountListPage,
              AdminAccountDetailPage,InternalAccountCreatePage,
              RoleCataloguePage,AddressBookPage,AddressModal}.tsx      [proposed]
  api/AddressApiClient.ts                                               [proposed]
  hooks/useMemberAddresses.ts                                           [proposed]

ppswbs_backend/src/main/java/.../configuration/                         [existing — repository inspection 2026-10-28]
  SecurityConfiguration.java                                             [existing; currently divergent CORS credentials/origins]
ppswbs_backend/src/main/java/.../features/accountmanagement/            [existing — repository inspection 2026-10-28]
  controller/{Account,AdminAccount,RoleCatalogue}Controller.java        [existing]
  controller/AccountExceptionHandler.java                               [existing; must conform to approved ownership model]
  service/{IdentityGateFilter,TokenVerification,AccountBootstrap,
           Profile,EmailSync,AdminAccount,Suspension,Audit}Service.java [existing]
  facade/{AccountFacade,AccountFacadeImpl,CurrentAccountActor}.java     [existing; target-facts/roster contract proposed]
  facade/{AccountId,TargetAccountFacts,TargetFactsOutcome,
           InternalRosterEntry,RosterOutcome}.java                      [proposed]
  dao/{Account,AccountProfile,AccountLocationAssignment,
       InternalAccountProvisioning,AccountAuditEvent}Repository.java    [existing]
  entity/{Account,AccountProfile,AccountLocationAssignment,
          InternalAccountProvisioning,AccountAuditEvent}.java           [existing]
  dto/request/* and dto/response/*                                      [existing; transport remains governed by OpenAPI]
```

Dependency direction is UI component → hook → API/Firebase client → controller → service → repository or named facade. FE-003 may call only the named FE-001 facade boundary. FE-001 may call the authoritative Workshop Location facade only to determine active scope. Neither feature imports the other's entities, DAOs, services, Firebase types, contacts or raw provider identifiers. Existing files are inventory facts only, not compliance evidence.

## Production-unit blueprint

| Path | Type / status | Responsibility | Public/use-case methods or props | Allowed dependencies | Source |
| --- | --- | --- | --- | --- | --- |
| `frontend/.../api/AccountManagementApiClient.ts` | API client / proposed | Approved FE-001 REST routes; bearer attachment | bootstrap, get/update profile, sync email, list/detail/create internal, resend, update role/status, role catalogue | authenticated request client; OpenAPI DTOs | OpenAPI; FR-001–027 |
| `frontend/.../auth/FirebaseAuthClient.ts` | client / proposed | Firebase registration/sign-in/out, Google redirect sign-in, in-place re-authentication, verification resend, verified-before-email-change, user reload and forced bearer refresh; never role decision or action-code handling | register, signIn, signInWithGoogleRedirect, getRedirectAuthResult, reauthenticateWithCredential, signOut, sendVerification, verifyBeforeUpdateEmail, reloadCurrentUser, getFreshIdToken | Firebase client foundation | FR-002–006, research D-19, D-20 |
| `frontend/.../hooks/useCurrentAccount.ts` | hook / proposed | Bootstrap/load current Account and safe recovery | bootstrap, refresh | Auth/API client | FR-001–006 |
| `frontend/.../hooks/useProfile.ts` | hook / proposed | Own-profile form state/validation/retry | load, save, synchronizeEmail | API client | FR-007–008, 025 |
| `frontend/.../hooks/useVerifiedEmailGate.ts` | hook / proposed | On Profile entry or accessible status-reload icon only, observe Firebase verification, synchronize Account snapshot, and expose allow/blocked/retry state | checkProfileVerification | FirebaseAuthClient; AccountManagementApiClient; no business mutation dependency | FR-004–006, research D-19 |
| `frontend/.../hooks/useAdminAccounts.ts` | hook / proposed | Admin list/detail/form state/version recovery | search, load, create, resend, updateProfile, assignRoleLocation, changeStatus | API client | FR-011–025 |
| `frontend/.../components/*Page.tsx` | components / proposed | Render routes/states from `ui-flow.md`; retain safe input only | documented component props | feature hooks/UI foundation | ui-flow.md |
| `configuration/SecurityConfiguration.java` | configuration / existing | Default-deny chain, exact-origin CORS, bearer-compatible CSRF | securityFilterChain, corsConfigurationSource | identity gate; environment config | FR-027; approved decisions |
| `service/IdentityGateFilter.java` | filter / existing | Verify bearer/revocation, resolve actor, default deny and security errors | filter admission | Token verifier; Account facade | FR-026–027 |
| `service/TokenVerificationService.java` | adapter / existing | Verify Firebase identity evidence | verify | Firebase Admin SDK | FR-001, 027 |
| `facade/AccountFacade.java` | facade / existing+proposed revision | Current actor and narrow consumer reads | resolveActor; findTargetFacts; findEligibleRoster | repositories; Workshop Location facade | FR-027–032 |
| `facade/CurrentAccountActor.java` | boundary record / existing+proposed revision | Minimal admission actor: Account ID, role, status and email-verification only | record accessors | no entity/provider types or Location facade | FR-027 |
| `facade/TargetAccountFacts`, `TargetFactsOutcome` | boundary types / proposed | FOUND, NOT_FOUND, UNAVAILABLE without PII | outcome accessors | no controller/entity/DAO exposure | FR-028, 030–031 |
| `facade/InternalRosterEntry`, `RosterOutcome` | boundary types / proposed | Minimal Staff-selection data/unavailable | outcome accessors | no contacts/lifecycle detail | FR-029–032 |
| `service/AccountBootstrapService.java` | service / existing+proposed revision | Find/create Account and required Profile from verified identity email; activate eligible internal account | bootstrap | Account/Profile repositories, audit/location validator | FR-001, 007, 013, 022 |
| `service/ProfileService.java`, `EmailSyncService.java` | services / existing | Own profile and current-identity email sync | get/save profile; synchronizeEmail | repositories; verified identity | FR-006–008 |
| `service/AdminAccountService.java` | service / existing | Provisioning, invitation, internal profile/role/location/list/detail | create; resend; list/detail; updateProfile; assignRoleLocation | repositories; Firebase, Location, notification boundaries | FR-011–018, 024 |
| `service/SuspensionService.java` | service / existing | Suspend/restore/revocation reconciliation | suspend; restore; reconcile | Account/Firebase/audit/notification | FR-019–021, 024 |
| `service/AuditService.java` | service / existing+proposed revision | Best-effort redacted audit for authorized domain commands; emit safe operational signal on persistence failure | record success/failure | audit repository; correlation-safe operational signal | FR-023, 032 |
| `controller/*Controller.java` | controllers / existing | HTTP transport, Bean Validation and actor-to-use-case routing | OpenAPI route handlers | service/DTO only | OpenAPI |
| `controller/AccountExceptionHandler.java` | error boundary / existing+proposed revision | FE-001 validation/business mapping only | exception mapping | FE-001 exceptions/shared envelope | approved error model |
| `dao/*Repository.java`, `entity/*` | persistence / existing | FE-001 persistence/concurrency/idempotency state | repository operations | JPA/Spring Data | data-model.md |

## Libraries and integrations

| Layer | Library / version | Status | Purpose | Source |
| --- | --- | --- | --- | --- |
| Frontend | React 19 + Vite | approved — Constitution | Client application boundary | Constitution |
| Frontend identity | Firebase Web SDK | approved baseline; configuration dependency | Client identity flow only | FR-002–006 |
| Backend | Java 21, Spring Boot REST, Spring Modulith, JPA/Spring Data, Flyway, MySQL | approved — Constitution | Module/persistence/migration | Constitution |
| Backend identity | Firebase Admin SDK | existing integration; configuration dependency | Bearer/revocation verification | FR-027 |
| Notification | FE-07 boundary | dependency, contract absent | Post-commit delivery request/outcome | FR-024 |
| Workshop Location | FE-003.1 facade | dependency, contract must be cited | Active canonical location validation | FR-012, 028–031 |

No cache, event replication, personnel identifier, generic RBAC store, app session store or cross-module transaction is designed for V1.

## Data flows

1. **WF-01 admission/current actor:** `FirebaseAuthClient → authenticated request client → SecurityConfiguration → IdentityGateFilter → TokenVerificationService → AccountFacade.resolveActor → receiving controller/service`; the gate does not call Workshop Location; security error: gate → approved envelope → UI recovery.
2. **WF-02 bootstrap/self-service:** `AuthPage/useCurrentAccount → AccountManagementApiClient → AccountController → AccountBootstrapService/ProfileService/EmailSyncService → repositories → response`; validation/conflict: FE-001 boundary → typed envelope → retained safe form state.
3. **WF-08 Profile verification gate:** `ProfilePage entry or status-reload icon → useVerifiedEmailGate → FirebaseAuthClient.reloadCurrentUser/getFreshIdToken → AccountManagementApiClient.synchronizeEmail → AccountController.syncOwnEmail → EmailSyncService → Account repository → gate result → Profile save`; unverified/provider/sync failure → explicit verify/resend/retry state, with no background poll or mutation retry.
4. **WF-03 internal provision:** `InternalAccountCreatePage/useAdminAccounts → API client → AdminAccountController → AdminAccountService → Location facade + provisioning/account/audit repositories → post-commit FE-07 intent → response`; audit persistence failure emits a correlation-safe operational signal but preserves the original business outcome.
5. **WF-04 suspend/restore:** `AdminAccountDetailPage → API client → AdminAccountController → SuspensionService → Account/Audit repositories → post-commit Firebase revocation + FE-07 intent → response`.
6. **WF-05 Manager authorization and Staff roster:** `FE-003 Manager action → CurrentAccountActor role/status check → FE-003 resource decision`; no Location facade participates. `FE-003 Staff target/location action → AccountFacade.findEligibleRoster/findTargetFacts → Account repositories + Location facade → typed outcome → FE-003 Staff decision/error`; there is no Account HTTP route and `UNAVAILABLE` prevents only the dependent Staff mutation.

## Reconciliation gaps and handoff blockers

| Gap | Impact | Owner / resume condition |
| --- | --- | --- |
| Data model, plan, interfaces and current code use `activeLocationIds` on the current actor | Raw assignments can be misread as usable scope and a Location outage can block unrelated requests | Current actor must omit scope; typed target/scope facts use `activeWorkshopLocationIds` effective semantics |
| FR-028–032 lack named facade types in `contracts/interfaces.md` | Consumer boundary is not stable | RESOLVED: Named types defined in `contracts/interfaces.md` and implemented in `AccountFacade` |
| OpenAPI/plan/tasks are declared obsolete by `spec.md` | Work could implement stale behavior | RESOLVED: OpenAPI 3.1, `plan.md`, and `tasks.md` regenerated and aligned |
| `account_audit_events.outcome` is MySQL `ENUM` but current entity/service use free `String` | Hibernate validation prevents application startup; unrelated MySQL integration suites cannot start | RESOLVED: `AuditOutcome` enum + `VARCHAR(16)` via `V52` migration; verified |
| Current code permits `localhost:3000` and enables CORS credentials | Conflicts with approved local bearer-only policy | Future task aligns configuration and proves it; no code changed here |
| Non-local origins, operations, accessibility/browser baseline have no source | Cannot claim production/UI acceptance | Deployment/frontend foundation owner supplies source |
| Verification-required gate is a new proposed frontend hook and requires revised OpenAPI/task evidence | Implementation could rely on stale Firebase token and treat verification as complete incorrectly | RESOLVED: Approved by Tech Lead; frontend implements and verifies T051–T052 |

## Gate review (D01–D20)

| Gate | Status | Evidence / gap |
| --- | --- | --- |
| D01 | MET | spec purpose, scope, stories and acceptance |
| D02 | MET | contracts/account-management.openapi.yaml regenerated to full OpenAPI 3.1.0 |
| D03 | MET | Spring Modulith module structure verified in ModulithArchitectureTests |
| D04 | MET | [workflows.md](workflows.md) |
| D05 | MET | FR-026–027 and WF-01 define actor/authorization and denial behavior |
| D06 | MET | Full OpenAPI 3.1 + facade contract in contracts/interfaces.md |
| D07 | MET | Data model aligned; activeWorkshopLocationIds refactored; migrations V52/V89 exist |
| D08 | MET | FR-019–021 and workflows |
| D09 | MET | Approved bearer-per-request/no app session decision |
| D10 | MET | Workflows identify transaction and external-side-effect boundaries |
| D11 | MET | Version/idempotency/recheck rule is explicit |
| D12 | MET | Firebase/FE-07/Location boundaries and recovery behavior are explicit |
| D13 | MET | FR-023, 025, 032 define minimization and audit handling |
| D14 | OPEN | accessibility/browser baseline (Phase 8 T047, deferred for MVP) |
| D15 | MET | AuditOutcome enum + VARCHAR(16) V52 and V89 migrations verified; tests pass |
| D16 | OUT OF SCOPE | Non-local origins explicitly deferred to production release scope |
| D17 | OUT OF SCOPE | Operations/rollout/observability deferred to production release scope |
| D18 | MET | tasks.md regenerated and aligned with plan |
| D19 | MET | verification.md revised to comprehensive verification plan |
| D20 | MET | approval.md confirmed by Tech Lead Phan Sơn (2026-11-03, 2026-11-06) |

## Reality check

Existing backend paths and completed task markers only show that code exists. They do not prove revised effective-scope semantics, the FE-003 facade contract, local CORS policy, error ownership, or that MySQL audit schema matches JPA validation. The minimum credible next path is contract/data-model reconciliation (including the explicit audit-outcome choice), plan/tasks/verification regeneration, then a scoped Tech Lead approval record. Adding a cache, event copy, new identity type, cross-module lock, `ddl-auto=update`, or a Flyway bypass would add complexity or hide failure without closing these gaps.

---

## Revision R-SL: shared session-navigation boundary (proposed units; approved design scope)

| Unit | Status | Public responsibility | Allowed dependencies |
| --- | --- | --- | --- |
| `shared/auth/AuthProvider` | existing, revision required | Firebase session snapshot and terminal outcome emission | Firebase client only; no Router navigation, account data or role decision |
| `shared/auth/SessionNavigationBoundary` | proposed | one-time cleanup, flash construction and `replace` navigation for `USER_SIGN_OUT`, `SESSION_LOST`, or safe `403` recovery | Router, AuthProvider, registered private-state cleanup interface |
| `shared/auth/RequireSession` | existing, revision required | resolved guest guard and neutral/unavailable states | AuthProvider, Router only |
| Router route registry | existing, revision required | applies public/Member/internal inventory, canonical URLs and aliases | RequireSession; feature route declarations |
| Home notice component | proposed | renders/dismisses transient navigation-state flash | Router location state and shared i18n; no storage |
| `FirebaseAuthClient` / `AuthPage` | existing, revision required | redirect Google initiation/result/error mapping | Firebase Web SDK redirect API only |

The session source has one direction: `Firebase observer or HTTP outcome → AuthProvider → SessionNavigationBoundary → cleanup/navigation → Router/Home`. `useCurrentAccount` is downstream of a signed-in snapshot and cannot emit terminal navigation. Server responses remain authoritative: `401` reports session loss; `403` reports authorization denial and retains the session. No module stores a password, token, refresh credential, notice, or discarded private draft.
