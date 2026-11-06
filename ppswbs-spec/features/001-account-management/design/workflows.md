# Workflow Design: Account Management (FE-001)

**Date created**: 2026-10-28
**Date updated**: 2026-11-06
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Design status**: APPROVED — workflows aligned with approved specification, data model, interfaces, and OpenAPI 3.1.0 contract (see `approval.md`).

## Workflow index

| ID | Workflow | Trigger and actor | Source requirements | Architecture flow | Status |
| --- | --- | --- | --- | --- | --- |
| WF-01 | Bearer admission and current actor | Every protected request | FR-026–027 | Architecture §Data flows 1 | Designed & implemented; minimal CurrentAccountActor |
| WF-02 | Bootstrap and self-service | Verified identity / Account owner | FR-001–008, 022, 025 | Flow 2 | Designed & implemented; governed by OpenAPI 3.1 |
| WF-03 | Provision and activate internal Account | Admin / intended internal identity | FR-011–018, 023–025 | Flow 4 | Designed; FE-07/Location contracts are dependencies |
| WF-04 | Suspend and restore | Admin / affected Account | FR-019–021, 023–027 | Flow 5 | Designed; provider reconciliation evidence pending |
| WF-05 | Target facts and roster | Authorized FE-003 actor/service | FR-028–033 | Flow 6 | Designed & implemented; contract defined in interfaces.md |
| WF-08 | Profile verification gate | Owner opens Profile / activates reload icon | FR-004–006 | Flow 3 | proposed |
| WF-06 | Browser transport and typed errors | Browser/API caller | FR-025, 027 | Flows 1–4 | Local policy approved; non-local operations open |
| WF-07 | Schema/configuration startup | Service runtime/operator | Constitution; data-model | Persistence/config boundary | Designed & implemented; V52 and V89 migrations verified |
| WF-09 | Member shipping address book | Authenticated Member | FR-036–039 | Flow 7 | Designed |
| WF-10 | Shipping address snapshot for FE-008 | FE-008 checkout process | FR-040 | Flow 8 | Designed |

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

1. Authentication is performed via Firebase Web SDK using either Email/Password or Google (`signInWithRedirect`). For Google sign-in, the browser redirects to Google and on return `getRedirectResult` resolves the Firebase user. `AccountBootstrapService.bootstrap` finds Account by verified Firebase UID. For an unknown UID with a valid identity email (verified or unverified), it creates exactly one `MEMBER`/`ACTIVE` Account and its Account Profile; the Profile full name is derived from the email local part. An unknown UID without a valid email creates neither record and receives safe alternative-method/recovery guidance.
2. If that UID owns a pending internal Account, it activates only when email is verified and effective Workshop scope meets the role rule; it never creates/replaces a Member Account.
3. `ProfileService` reads/replaces full name and optional phone only; `EmailSyncService` accepts the current verified identity email, never a browser value.
4. A committed Account mutation attempts a redacted `SUCCESS` audit event. Credential actions remain Firebase-owned; FE-001 never receives a password. For password change: available only for accounts where `user.providerData` contains `password`; form requires `currentPassword`, `newPassword` ($\ge 6$ chars), and `confirmPassword`. The FE performs in-place re-authentication via `reauthenticateWithCredential(user, EmailAuthProvider.credential(user.email, currentPassword))` before calling `updatePassword`, ensuring a fresh credential and avoiding `auth/requires-recent-login`.
5. Controller returns the current DTO specified by OpenAPI; UI confirms success only after server response.

### Failure, recovery, and integrity

1. Same-looking email with another UID does not merge/link/upgrade Accounts; return safe original-method/support guidance.
2. Repeated bootstrap returns existing Account without role/status/history mutation. Own profile save is last-successful-commit wins; Admin changes use version conflict rules in WF-03/04.
3. Password re-authentication failure (wrong current password): clear only `currentPassword`, display an inline error, and retain `newPassword` in ephemeral React state; do not write passwords to browser storage. On successful update, clear all fields and display a success notification.
4. After authorization and domain entry, validation/business/dependency failure attempts a redacted `FAILURE` audit event. Malformed or unauthenticated input creates no Account audit event. If audit persistence fails, emit a correlation-safe operational signal and preserve the original business outcome; UI keeps non-secret input where safe and no password, raw token or action link is restored.

## WF-03: Provision and activate an internal Account

- **Trigger and actor**: Current `ADMIN` creates/resends/edits Staff or Manager; intended identity subsequently signs in.
- **Preconditions and authorization**: Admin authority is checked server-side. Create permits only `STAFF`/`MANAGER`; Staff has exactly one and Manager one-or-more effective active Workshop Locations. Request contains a fresh idempotency key.
- **Related architecture flow**: Architecture §Data flows 4.

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
- **Related architecture flow**: Architecture §Data flows 5.

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
- **Related architecture flow**: Architecture §Data flows 6.

1. FE-003 calls proposed `AccountFacade.findEligibleRoster(workshopLocationId, requiredRole)` to present only minimal ACTIVE candidates matching effective `activeWorkshopLocationIds`.
2. UI may display current profile display name plus Account ID only to disambiguate names; it receives no email, phone, provider UID or lifecycle detail.
3. Immediately before its sensitive mutation, FE-003 calls proposed `findTargetFacts(accountId)`.
4. `FOUND` returns Account ID, role, lifecycle status and effective `activeWorkshopLocationIds`; FE-003 applies its own approved rule and commits only if it passes.
5. FE-003 audits its own decision/mutation; FE-001 does not audit these read-only queries.

### Failure, recovery, and integrity

1. `NOT_FOUND` and `FOUND` failing consumer role/status/scope rule map to `409 TARGET_NOT_ELIGIBLE`, without lifecycle disclosure.
2. `UNAVAILABLE` means Account or Location scope cannot be verified; FE-003 returns `503`, preserves safe input, starts/commits no mutation and performs no background retry/cache/fallback.
3. There is intentionally no cross-module transaction/lock: a command whose recheck preceded an Account change commit may finish; every later command must use changed facts.
4. The typed contract is defined in `contracts/interfaces.md` (`TargetFactsOutcome`, `RosterOutcome`) and implemented in `AccountFacadeImpl`.

## WF-06: Browser transport and typed errors

- **Trigger and actor**: Browser/API caller invoking any FE-001 route.
- **Preconditions and authorization**: Local origin is exactly `http://localhost:5173`; bearer is in `Authorization`; browser credentials are disabled.

1. CORS preflight is allowed only for configured exact origin(s), required methods and headers. `Access-Control-Allow-Credentials` is false.
2. Security boundary owns admission errors; FE-001 owns its validation/business errors; another feature owns its business errors; shared technical fallback handles unanticipated technical failures only.
3. Every observable error uses `code`, safe `message`, `correlationId`, `timestamp`, and optional validation `fieldErrors`.

### Failure, recovery, and integrity

1. `401`: report shared session loss, discard private draft/state and return Home; do not retain navigation/form context for restoration. `403`: retain session, safe Home notice, and do not auto-retry. `409`: reload/reconcile. `503`: explicit retry with safe input retained.
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

## WF-08: Profile verification gate

- **Trigger and actor**: A signed-in Account owner opens Profile, or activates the accessible verification-status reload icon in Profile.
- **Preconditions and authorization**: Firebase Web SDK has a current signed-in user; Profile save has not started. This is the only FE-001 client-side email-verification gate; Staff/Manager activation and every protected command retain their independent server checks.
- **Related architecture data flow**: Architecture §Data flows 3.

1. `ProfilePage` invokes proposed `useVerifiedEmailGate.checkProfileVerification` on entry; the owner may run it again with the labelled/tooltip reload icon. It never polls, schedules a job, receives a Firebase callback/webhook, or uses an Mland email-action handler.
2. `FirebaseAuthClient.reloadCurrentUser` reloads the Firebase user. If no current user exists, the gate returns session recovery; if its observed `emailVerified` remains false, it returns blocked verification guidance without invoking a business command.
3. For an observed verified user, `FirebaseAuthClient.getFreshIdToken` forces a fresh Firebase ID token. `AccountManagementApiClient.synchronizeEmail` calls bodyless `PUT /api/v1/accounts/me/email` with that bearer only; it sends no email address, verification boolean, UID or Firebase action code.
4. `IdentityGateFilter` verifies token/revocation. `AccountController.syncOwnEmail` and `EmailSyncService` verify same-UID current identity and verified-email condition, then update only the Account email/verification observation snapshot. Role, Account status, scope and ownership do not change.
5. Only after successful synchronization does the gate enable Profile save. The save independently passes WF-01/server authorization and verified-email enforcement.

### Failure, recovery, and integrity

1. A still-unverified user sees verification/resend guidance; Firebase Web SDK sends a new verification email only after explicit owner action. After successful send, `localStorage` retains only `emailVerificationResendUntil.<accountId>` expiry timestamp for 90 seconds, including browser restart; it stores no email, provider UID, token, or action code. FE-07 does not deliver this identity-provider email.
2. Firebase reload/token failure, `401`, `403`, or `503` during synchronization keeps Profile save unstarted. Missing/expired session discards Profile draft and follows WF-11 to Home; `403` retains the session and uses the safe Home notice; other temporary failures retain safe input and offer explicit reload-icon retry. The UI never automatically retries Profile save.
3. A verification completed on a different browser/device has no local Firebase session/action callback requirement. On the owner’s next sign-in and Profile entry, this workflow observes the verified identity and synchronizes it.
4. Email change uses Firebase `verifyBeforeUpdateEmail` after in-place re-authentication via `reauthenticateWithCredential` using current password and identity email (form requires current password and proposed new email). Until the new address verifies, Firebase and Account retain the old address; no pending address is persisted in Mland. On re-authentication failure, only current password is cleared and new email is retained in ephemeral state. The owner can repeat the same identity-provider flow to resend/change the proposed address.
5. Raw Firebase email action links/codes are not rendered, stored, logged, forwarded to the backend, or included in audit metadata. There is no concurrency-sensitive business mutation in the synchronization itself; repeated successful checks are idempotent snapshot writes.

## Open decisions and dependencies

| Item | Impact | Owner / resume condition |
| --- | --- | --- |
| Named target-facts/roster facade contract | WF-05 cannot be implemented compatibly | RESOLVED: Defined in `contracts/interfaces.md` and implemented |
| `activeWorkshopLocationIds` reconciliation | Current actor/data-model/code use another name/semantics | Current actor omits scope; target/scope facade exposes effective `activeWorkshopLocationIds` before location-dependent work |
| Audit outcome schema/mapping | Implementation and MySQL evidence remain pending | Approved `AuditOutcome`/`VARCHAR(16)` baseline; implement append-only migration and retain fresh/upgrade validation evidence |
| FE-07 and Workshop Location contracts | Integration details cannot be invented | Link approved owning contracts |
| Non-local origins, deployment and observability | No production transport/operations claim | Deployment owner provides approved source |
| Accessibility/browser baseline | UI testable acceptance incomplete | Frontend foundation owner provides approved source |
| Revised verification-required gate | Existing draft did not specify refresh, sync, recovery or no-job constraint | Tech Lead approves the revised workflow and canonical OpenAPI route |


## WF-09: Member shipping address book management

- **Trigger and actor**: Authenticated Member in Profile (`/profile/addresses`) or during Checkout in FE-08 modal.
- **Preconditions and authorization**: WF-01 resolves caller with role `MEMBER`. Only the owning Member can view or mutate addresses.
- **Related architecture flow**: Flow 7.

1. **List/Default Query**: `GET /api/v1/accounts/me/addresses` returns all active addresses for the Member. `GET /api/v1/accounts/me/addresses/default` returns the default address or empty.
2. **Add Address**: `POST /api/v1/accounts/me/addresses`:
   - Checks existing count: if already >= 10, rejects with `409 ADDRESS_LIMIT_EXCEEDED`.
   - Normalizes phone number: strips spaces/delimiters, converts `+84`/`84` to `0`, validates against `^0[35789]\d{8}$`.
   - Validates province, district, ward (and optional level-4 hamlet) against administrative division catalogue.
   - If first address, auto-sets `is_default = true`.
   - If `is_default == true` requested, atomically clears `is_default` on previous default address in the same transaction.
   - Persists into `member_addresses` with timestamps in `Asia/Ho_Chi_Minh`.
3. **Update Address**: `PUT /api/v1/accounts/me/addresses/{addressId}`:
   - Validates ownership (`account_id == currentActor.id`); returns `404` or `403` if mismatch.
   - Normalizes phone and updates fields. If set to default, unsets previous default atomically.
4. **Delete Address**: `DELETE /api/v1/accounts/me/addresses/{addressId}`:
   - Validates ownership.
   - If `is_default == true` AND total address count > 1: rejects deletion with `400/409` (must set another default first).
   - If single address or non-default address: physically deletes row (Hard Delete).
5. **Set Default**: `PATCH /api/v1/accounts/me/addresses/{addressId}/default`:
   - Atomically updates target to `is_default = true` and previous default to `false`.

## WF-10: Shipping address snapshot for FE-008

- **Trigger and actor**: FE-008 Retail Order & Fulfillment checkout service.
- **Preconditions**: Customer is checking out an order.
- **Related architecture flow**: Flow 8.

1. FE-008 invokes Java Modulith facade: `AccountFacade.findDefaultShippingAddress(accountId)` or `findShippingAddressById(accountId, addressId)`.
2. Facade queries `member_addresses` for the given account ID and returns `Optional<MemberAddressSnapshot>`.
3. FE-008 clones all snapshot fields (`recipientName`, `phoneNumber`, `provinceId`, `provinceName`, `districtId`, `districtName`, `wardId`, `wardName`, `hamletId`, `hamletName`, `detailAddress`, `addressType`) directly into its order persistence.
4. Subsequent modifications or deletions of the address in FE-001 have zero side effects on existing FE-008 orders.

---

## WF-11: Shared browser session lifecycle and route recovery

- **Trigger and actor**: Any browser user; triggers are explicit sign-out, Firebase observer null after initialization, first protected-request `401`, protected-route resolution, `403`, `loading`, or `unconfigured` state.
- **Preconditions and authorization**: `AuthProvider` is the sole session source. Backend remains the authority for every protected request and feature-specific role/scope decision.

1. `AuthProvider` exposes `loading`, `unconfigured`, `signed-out`, or `signed-in`; `RequireSession` renders no PII during `loading` and safe unavailable UI during `unconfigured` without navigation.
2. For a resolved `signed-out` user attempting a Member/internal route, `RequireSession` performs `replace('/login')`. `/sign-in` independently performs `replace('/login')`. A successful email/password or Google redirect sign-in always `replace`s to `/`; no `returnTo` state is carried.
3. Explicit sign-out disables the initiating entry point. On Firebase success it emits `USER_SIGN_OUT`. `SessionNavigationBoundary` consumes it once, clears registered private state/drafts, and `replace`s to `/` with transient “Đã đăng xuất” notice. A Firebase-null observer callback caused by that call is ignored for navigation.
4. Firebase-null after initial resolution or the first authenticated `401` emits `SESSION_LOST`. The boundary consumes the first such outcome only until a later signed-in snapshot, discards private state/drafts, and `replace`s to `/` with generic “Phiên đã kết thúc, vui lòng đăng nhập lại” notice.
5. A sign-out failure emits no terminal outcome: the current page remains interactive, the pending control re-enables, and a safe retry error is shown.
6. A `403` is not a sign-out. The boundary retains session/private input, `replace`s to `/`, and passes only a generic access-denied notice. It reveals no role, lifecycle, or Workshop-scope detail.
7. Home displays the navigation-state notice until dismissal or leaving Home. It never writes the notice to storage. Cleanup and recovery never store/restore passwords, ID tokens, refresh credentials, or private drafts.

**Google branch:** AuthPage starts only `signInWithRedirect`; on return it calls `getRedirectResult`, bootstraps the account, then follows step 2. `auth/account-exists-with-different-credential` directs to Email/Password with no merge/link. Any other redirect error is safe generic retry/alternative-method guidance. No popup branch exists.
