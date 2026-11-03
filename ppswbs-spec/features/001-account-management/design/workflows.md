# Workflow Design: Account Management (FE-001)

**Date created**: 2026-10-28
**Date updated**: 2026-11-01
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Design status**: DRAFT/PROPOSED. Workflow behavior is designed; it does not approve code or replace the missing interface/transport revisions.

## Workflow index

| ID | Workflow | Trigger and actor | Source requirements | Architecture flow | Status |
| --- | --- | --- | --- | --- | --- |
| WF-01 | Bearer admission and current actor | Every protected request | FR-026–027 | Architecture §Data flows 1 | Designed; actor field contract needs reconciliation |
| WF-02 | Bootstrap and self-service | Verified identity / Account owner | FR-001–008, 022, 025 | Flow 2 | Designed; OpenAPI regeneration pending |
| WF-03 | Provision and activate internal Account | Admin / intended internal identity | FR-011–018, 023–025 | Flow 3 | Designed; FE-07/Location contracts are dependencies |
| WF-04 | Suspend and restore | Admin / affected Account | FR-019–021, 023–027 | Flow 4 | Designed; provider reconciliation evidence pending |
| WF-05 | Target facts and roster | Authorized FE-003 actor/service | FR-028–033 | Flow 5 | BLOCKED for implementation by missing facade contract |
| WF-06 | Browser transport and typed errors | Browser/API caller | FR-025, 027 | Flows 1–4 | Local policy approved; non-local operations open |
| WF-07 | Schema/configuration startup | Service runtime/operator | Constitution; data-model | Persistence/config boundary | Designed; runtime verification pending |

## WF-01: Bearer admission and current actor

- **Trigger and actor**: Any request not explicitly declared public in its owned contract.
- **Preconditions and authorization**: Browser/client sends Firebase ID token in `Authorization: Bearer`. V1 does not use cookies, an application server session or a refresh-token system.
- **Related architecture flow**: Architecture §Data flows 1.

1. `SecurityConfiguration` treats public access as explicit exception and otherwise routes through `IdentityGateFilter`.
2. `IdentityGateFilter` asks `TokenVerificationService.verify` to check Firebase evidence and revocation.
3. `AccountFacade.resolveActor` returns current minimal Account actor from Account data without querying Workshop Location; provider UID/token remain inside the gate.
4. A location-scoped receiving service separately obtains current effective `activeWorkshopLocationIds` through target/scope facts immediately before authorization or mutation. A scope-independent service does not make that query.
5. The receiving controller/service makes its own resource/action decision. It must not grant authority from UI state, provider claims or a previous actor snapshot.
6. The successful request uses only current facts required for that operation.

### Failure, recovery, and integrity

1. Missing/invalid/revoked bearer is security-owned `401`; valid identity without eligible Account/lifecycle/role/scope is security-owned or receiving-feature `403` according to the approved error model. Neither exposes token/provider detail.
2. If the minimal actor cannot be resolved, fail closed for the protected request. If effective scope cannot be resolved, only the location-scoped action fails closed with `UNAVAILABLE`/`503`; there is no cached scope fallback.
3. Recheck occurs per request. A suspended Account cannot retain capability simply because a UI page or older bearer existed.

## WF-02: Bootstrap and owner self-service

- **Trigger and actor**: A Firebase-authenticated identity calls bootstrap; a current Account owner loads/saves profile or synchronizes identity email.
- **Preconditions and authorization**: Identity is verified by WF-01. Profile actions require the owner’s own Account; verified-email-required functions check that condition independently.
- **Related architecture flow**: Architecture §Data flows 2.

1. `AccountBootstrapService.bootstrap` finds Account by verified Firebase UID. For an unknown UID with a valid identity email (verified or unverified), it creates exactly one `MEMBER`/`ACTIVE` Account and its Account Profile; the Profile full name is derived from the email local part. An unknown UID without a valid email creates neither record and receives safe alternative-method/recovery guidance.
2. If that UID owns a pending internal Account, it activates only when email is verified and effective Workshop scope meets the role rule; it never creates/replaces a Member Account.
3. `ProfileService` reads/replaces full name and optional phone only; `EmailSyncService` accepts the current verified identity email, never a browser value.
4. A committed Account mutation attempts a redacted `SUCCESS` audit event. Credential actions remain Firebase-owned; FE-001 never receives a password.
5. Controller returns the current DTO specified by OpenAPI; UI confirms success only after server response.

### Failure, recovery, and integrity

1. Same-looking email with another UID does not merge/link/upgrade Accounts; return safe original-method/support guidance.
2. Repeated bootstrap returns existing Account without role/status/history mutation. Own profile save is last-successful-commit wins; Admin changes use version conflict rules in WF-03/04.
3. After authorization and domain entry, validation/business/dependency failure attempts a redacted `FAILURE` audit event. Malformed or unauthenticated input creates no Account audit event. If audit persistence fails, emit a correlation-safe operational signal and preserve the original business outcome; UI keeps non-secret input where safe and no password, raw token or action link is restored.

## WF-03: Provision and activate an internal Account

- **Trigger and actor**: Current `ADMIN` creates/resends/edits Staff or Manager; intended identity subsequently signs in.
- **Preconditions and authorization**: Admin authority is checked server-side. Create permits only `STAFF`/`MANAGER`; Staff has exactly one and Manager one-or-more effective active Workshop Locations. Request contains a fresh idempotency key.
- **Related architecture flow**: Architecture §Data flows 3.

1. `AdminAccountService.create` validates role, canonical `workshopLocationId` assignments and idempotency key/digest through the authoritative Location boundary.
2. It records durable provisioning progress; Firebase identity/action-link work is outside the DB transaction and must be reconciled, not assumed rollback-safe.
3. It commits `PENDING_SETUP` Account, assignment, redacted audit and FE-07 notification intent together when the Account decision succeeds.
4. FE-07 owns delivery/retry outcome after commit. The raw Firebase action link is never retained in audit/log/notification intent.
5. On later intended, verified sign-in, bootstrap activates the same pending Account only when scope is still valid.
6. Profile edit, role/location assignment and status change are distinct commands with expected version; all Account-admin mutations audit safely.

### Failure, recovery, and integrity

1. Same idempotency key plus same request returns preserved result; same key plus changed digest yields `409`, never a second account/invitation.
2. Location/Firebase/FE-07 failure never creates a hidden Member fallback. Notification delivery failure does not undo a committed Account decision.
3. A stale Admin version is rejected, attempts a redacted `FAILURE` audit, and UI must reload before retry. Audit failure does not replace that conflict outcome. Resend is pending-only and has a five-minute cooldown.

## WF-04: Suspend and restore

- **Trigger and actor**: Authorized Admin acts on a non-Admin target Account.
- **Preconditions and authorization**: Current Admin actor, valid reason and current target/version. Admin accounts are outside feature administration.
- **Related architecture flow**: Architecture §Data flows 4.

1. `SuspensionService.suspend` changes `ACTIVE` or `PENDING_SETUP` to `SUSPENDED`, retains prior eligible state, and commits redacted audit plus notification intent.
2. Protected server actions are denied immediately through WF-01, independently of email delivery or Firebase revocation completion.
3. Firebase revocation is requested/reconciled as durable external progress.
4. `SuspensionService.restore` is available only after revocation completion; it restores recorded prior eligible state and requires new sign-in.

### Failure, recovery, and integrity

1. Provider failure remains durable reconciliation; it cannot silently make a suspended Account usable.
2. Restore before revocation completion is a conflict/unavailable outcome, not a bypass.
3. Concurrent Admin changes use expected version; authorized success/failure audit records outcome/correlation but no password, token, action link, full name or phone.

## WF-05: Consumer target facts and roster

- **Trigger and actor**: An approved FE-003 service after it authorizes its own actor and Workshop resource.
- **Preconditions and authorization**: FE-003 authorization happens before facade invocation. `workshopLocationId` is canonical positive opaque Account-independent site/scope ID.
- **Related architecture flow**: Architecture §Data flows 5.

1. FE-003 calls proposed `AccountFacade.findEligibleRoster(workshopLocationId, requiredRole)` to present only minimal ACTIVE candidates matching effective `activeWorkshopLocationIds`.
2. UI may display current profile display name plus Account ID only to disambiguate names; it receives no email, phone, provider UID or lifecycle detail.
3. Immediately before its sensitive mutation, FE-003 calls proposed `findTargetFacts(accountId)`.
4. `FOUND` returns Account ID, role, lifecycle status and effective `activeWorkshopLocationIds`; FE-003 applies its own approved rule and commits only if it passes.
5. FE-003 audits its own decision/mutation; FE-001 does not audit these read-only queries.

### Failure, recovery, and integrity

1. `NOT_FOUND` and `FOUND` failing consumer role/status/scope rule map to `409 TARGET_NOT_ELIGIBLE`, without lifecycle disclosure.
2. `UNAVAILABLE` means Account or Location scope cannot be verified; FE-003 returns `503`, preserves safe input, starts/commits no mutation and performs no background retry/cache/fallback.
3. There is intentionally no cross-module transaction/lock: a command whose recheck preceded an Account change commit may finish; every later command must use changed facts.
4. Implementation is blocked until `contracts/interfaces.md` names the types, inputs, outcomes and compatibility rules.

## WF-06: Browser transport and typed errors

- **Trigger and actor**: Browser/API caller invoking any FE-001 route.
- **Preconditions and authorization**: Local origin is exactly `http://localhost:5173`; bearer is in `Authorization`; browser credentials are disabled.

1. CORS preflight is allowed only for configured exact origin(s), required methods and headers. `Access-Control-Allow-Credentials` is false.
2. Security boundary owns admission errors; FE-001 owns its validation/business errors; another feature owns its business errors; shared technical fallback handles unanticipated technical failures only.
3. Every observable error uses `code`, safe `message`, `correlationId`, `timestamp`, and optional validation `fieldErrors`.

### Failure, recovery, and integrity

1. `401`: obtain usable sign-in; retain only safe navigation/form context. `403`: do not auto-retry. `409`: reload/reconcile. `503`: explicit retry with safe input retained.
2. No response contains stack trace, raw token, Firebase UID or unauthorized target detail.
3. Preview/staging/production origin and operational behavior are not defined by this local decision and remain blocked.

## WF-07: Schema and configuration startup

- **Trigger and actor**: Service runtime/operator starts backend.
- **Preconditions**: Approved environment/secret channel provides configuration; no committed fallback credential is accepted.

1. Flyway applies the repository-wide append-only integer sequence.
2. Hibernate validates mapping against the resulting schema; it does not create/update/drop schema.
3. `AccountAuditEvent.outcome` uses the approved `AuditOutcome` Java enum (`SUCCESS`, `FAILURE`) persisted as `VARCHAR(16)` through an append-only migration. A free Java `String`, MySQL `ENUM` or any other value is not permitted.
4. Service starts only if required configuration, migration and mapping checks succeed.

### Failure, recovery, and integrity

1. Missing configuration or migration/mapping mismatch prevents startup; recovery is authorized config/migration correction, never schema auto-generation, Flyway bypass, test exclusion or secret fallback.
2. For the audit-outcome contract, verify both a fresh MySQL database and an upgrade from V5 before any dependent MySQL integration test is accepted.
3. The local `.env` helper may check file presence only and must not inspect content.
4. Production rollout/rollback and observability owner are not sourced; this blocks production readiness, not this draft design.

## Open decisions and dependencies

| Item | Impact | Owner / resume condition |
| --- | --- | --- |
| Named target-facts/roster facade contract | WF-05 cannot be implemented compatibly | Revise `contracts/interfaces.md` from FR-028–032 |
| `activeWorkshopLocationIds` reconciliation | Current actor/data-model/code use another name/semantics | Current actor omits scope; target/scope facade exposes effective `activeWorkshopLocationIds` before location-dependent work |
| Audit outcome schema/mapping | Implementation and MySQL evidence remain pending | Approved `AuditOutcome`/`VARCHAR(16)` baseline; implement append-only migration and retain fresh/upgrade validation evidence |
| FE-07 and Workshop Location contracts | Integration details cannot be invented | Link approved owning contracts |
| Non-local origins, deployment and observability | No production transport/operations claim | Deployment owner provides approved source |
| Accessibility/browser baseline | UI testable acceptance incomplete | Frontend foundation owner provides approved source |
