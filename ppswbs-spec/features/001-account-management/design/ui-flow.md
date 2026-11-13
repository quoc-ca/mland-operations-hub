# UI Flow Design: Account Management (FE-001)

**Date created**: 2026-10-28
**Date updated**: 2026-11-14
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Design status**: Draft revision — the 2026-11-14 Manager-assignment wording and Staff-picker behavior are proposed and pending Tech Lead approval. Historical approval in `approval.md` remains limited to its recorded scope.

## Actors, routes and authorization

| Actor | Entry / route | Server-side rule | Status / source |
| --- | --- | --- | --- |
| Anonymous visitor | `/sign-in`, `/register`, `/forgot-password` | Only explicitly public operations support identity-provider flows. Browser routing does not authorize API. | proposed routes from plan; governed by OpenAPI 3.1 |
| Signed-in Account owner | `/`, `/profile`, `/profile/addresses`, `/security/password`, `/security/email` | WF-01 supplies current actor; server limits actions to owner and checks verification where required. | proposed; FR-003–008, FR-036–039 |
| Admin | `/admin/accounts`, `/admin/accounts/new`, `/admin/accounts/:accountId`, `/admin/roles` | Server checks current `ADMIN`, target and current resource constraints for every action. | proposed; FR-010–025 |
| Workshop-feature actor | FE-003 screen, not an FE-001 route | FE-003 authorizes actor/resource before roster/facts facade reads and before mutation. | approved behavior FR-028–032; UI owned by FE-003 |

The frontend may hide navigation as a usability affordance, but cannot decide role, status, scope or ownership. Firebase client state, a loaded roster and a rendered page may be stale; each protected command is decided on the server. A Workshop Location outage blocks only a command whose authorization requires effective Workshop scope, not Account self-service that has no scope dependency.

## Screen and interaction flows

| Screen / flow | Entry and actor | Workflow | Component/hook/API client | Contract reference | Loading / empty / validation / success / error / partial states | Recovery |
| --- | --- | --- | --- | --- | --- | --- |
| Sign-in / registration | Anonymous | WF-02, WF-06 | proposed `AuthPage` / `FirebaseAuthClient` | FR-002–006; Firebase client boundary | Email/password form plus prominent "Đăng nhập với Google" button (via `signInWithRedirect`). On mount: resolves `getRedirectResult`, bootstraps account, navigates Home. Duplicate-submit pending state. Do not expose Account existence. Provider collision (`account-exists-with-different-credential`) displays guidance to use password credential without merge. | Explicit retry/alternative method; preserve no secret beyond active secure control. |
| Bootstrap / landing | New authenticated user | WF-01, WF-02, WF-11 | proposed `useCurrentAccount` | governed by OpenAPI 3.1 (`/api/v1/accounts/me`) | Neutral loading while Account resolves. Active account goes Home; unverified account gets non-blocking reminder. No Account detail on failed resolution. | `401` follows shared session loss to Home; `403` keeps session and gives safe Home notice; `503` explicit retry; never infer old role/scope. |
| Profile verification gate | Signed-in Account owner opening Profile | WF-08, WF-11 | proposed `useVerifiedEmailGate`, `FirebaseAuthClient`, `AccountManagementApiClient` | FR-004–006, FR-041; `PUT /api/v1/accounts/me/email` | On Profile entry: neutral checking state; reload Firebase user; unverified state offers resend; verified state synchronizes before enabling save. A labelled/tooltip reload icon repeats the check. No custom email-action page/callback/polling state. | `401` discards draft through the shared boundary and returns Home; unverified stays blocked; `403` retains session/safe Home notice; `503` preserves safe input and offers explicit icon retry. |
| Profile / security | Current Account owner | WF-02, WF-08, WF-06, WF-11 | proposed `ProfilePage`, `useProfile`, `useVerifiedEmailGate` | FR-004–008, FR-041; OpenAPI profile/email endpoints | Field validation inline; save stays disabled until the Profile gate succeeds. Password change form has 3 fields (Current Password, New Password, Confirm Password) with in-place reauthentication before `updatePassword`; section hidden if no `password` in `user.providerData`. Email change form has 2 fields (Current Password, New Email) with in-place reauthentication before `verifyBeforeUpdateEmail`. Member resend uses Firebase SDK with 90s `localStorage` cooldown. | On wrong current password: clear only current password, show inline error, retain new password/email in ephemeral React state (never in storage). On success: clear all fields, show success notice. `401` discards draft and follows shared Home recovery; `403` retains session with safe Home notice; `409` reconciles current values. |
| Admin list/detail | Admin | WF-01, WF-03, WF-04 | proposed `AdminAccountListPage`, `AdminAccountDetailPage`, `useAdminAccounts` | FR-016–025; governed by OpenAPI 3.1 | Loading progress; explicit empty result; restricted detail data; success only after commit. A Manager's Location assignments are labelled organizational assignments, never Manager permission scope. | `409` reload before reapply; `503` keep safe form data for explicit retry. |
| Create/resend internal Account | Admin | WF-03 | proposed `InternalAccountCreatePage`, `useAdminAccounts` | FR-011–015, 023–025 | Validate role/location before submit. The Manager form requires one or more active Locations and states: “Assignments support organization; an active Manager's Manager-authorized operational scope is global.” The Staff form states that assignment limits Staff operational access. Pending setup is not active access. Repeat click/retry preserves one intended provisioning request. Never display raw action link. | Provider/notification failure gives safe recovery; resend/reconciliation does not create a second account. |
| Role/location/status action | Admin | WF-03, WF-04 | proposed detail page/hook | FR-017–021 | Explain the committed role/status and assignment effect. On Staff ↔ Manager change, state that authorization changes after the committed server response: Manager scope is global for Manager-authorized actions; Staff remains location-scoped. Location deactivation does not claim role/lifecycle change. | Version conflict reloads; denied access returns to allowed safe view. |
| Fixed role catalogue | Admin | WF-01 | proposed `RoleCataloguePage` | FR-009–010 | Loading/error state; display read-only fixed role descriptions, including Manager global operational scope and the Admin-only boundary. No permission/role editing controls. | `401/403` account recovery; `503` retry. |
| Workshop Staff picker | Authorized FE-003 actor | WF-05 | FE-003-owned component; proposed FE-001 facade client at module boundary | FR-029–031; defined in contracts/interfaces.md | Load active Staff candidates by canonical Location. Empty means no eligible Staff candidate, not directory failure. Show current display name and optional opaque Account ID only. It does not select or authorize Managers. | `503` preserves safe FE-003 input; `409 TARGET_NOT_ELIGIBLE` requires new selection without lifecycle disclosure. |
| Member shipping address book | Signed-in Member | WF-09 | proposed `AddressBookPage`, `AddressModal`, `useMemberAddresses`, `AddressApiClient` | FR-036–039; OpenAPI addresses endpoints | List up to 10 addresses with badge for default and type (HOME/OFFICE). Adding 1st address pre-fills name and phone from profile. Cascading dropdown for Province -> District -> Ward (-> Hamlet). Add disabled if 10 reached. Delete default blocked if multiple addresses exist. | `400` validation error; `409 ADDRESS_LIMIT_EXCEEDED` shows quota message; `409` default deletion blocked guidance; `503` explicit retry. |

## Session, data handling and accessibility

### Approved local browser/security baseline

- Firebase bearer is attached in `Authorization`; FE-001 does not use browser auth cookies, app server session, app JWT or refresh-token system.
- Local browser origin is exactly `http://localhost:5173`. CORS allows only explicit configured origins, necessary methods/headers and preflight; credentials mode is disabled. No wildcard is allowed.
- A later cookie-based flow requires a new approved CORS/CSRF/session/logout design; it is not enabled by this decision.
- On `401`, the shared boundary discards private draft/state and returns Home with generic session-ended notice. On `403`, retain session, do not auto-retry, and show only the safe Home notice. On `409`, reload/reconcile. On `503`, retain safe unsaved input and provide explicit retry.
- Do not render/store raw ID token, provider UID, password, reset/action link, another Account’s contact data or lifecycle detail to simplify recovery.
- Email verification email is sent through Firebase Web SDK after an explicit signed-in owner request. A 90-second resend expiry is stored under an Account-ID-keyed `localStorage` key; no email, UID, token, or action code is stored. V1 uses Firebase’s default action handler, does not configure an Mland action page/callback route, and does not poll Firebase in the background. The only proactive check is WF-08 on Profile entry or its reload icon.

### Still-unsourced UI acceptance baseline

Accessibility standard, supported browser/device matrix, localization behavior, visual component library and exact route-guard transition behavior have no approved source. These are not inferred from a prototype or existing code. They block objective UI acceptance, but do not change the observable recovery/security behavior above.

## Error presentation contract

| Source | Owner | Safe presentation rule |
| --- | --- | --- |
| Bearer/security admission | shared identity gate | `401/403`, no token/provider detail; route to session or eligibility recovery |
| FE-001 validation/business outcome | FE-001 | Stable `code`, safe message, correlation reference and optional field errors |
| FE-003 business outcome | FE-003 | FE-003 keeps ownership, including `TARGET_NOT_ELIGIBLE`; FE-001 must not convert it to `500` |
| Dependency unavailable | boundary-owning feature | `503`, explicit retry, no mutation and preserve safe input |
| Unexpected technical failure | shared technical fallback | stable safe envelope and correlation reference; no stack trace/PII |

The approved shared envelope is `code`, `message`, `correlationId`, `timestamp`, and optional `fieldErrors`.

## UI verification matrix

| Scenario | Expected observable result | Workflow |
| --- | --- | --- |
| Expired/revoked bearer while editing | Safe session-recovery state; secret fields are not restored | WF-01, WF-06 |
| Account suspended after page render | Next protected command denied; UI does not claim previous page authority remains valid | WF-01, WF-04 |
| Stale Admin edit | No overwrite; latest data is loaded before another change | WF-03, WF-04 |
| Target becomes ineligible after roster load | Consumer mutation does not occur; generic ineligible result and selection recovery | WF-05 |
| Account/Workshop Location facts unavailable | No consumer mutation; retryable response and preserved safe input | WF-05 |
| No eligible roster candidates | Empty state without directory/contact/lifecycle exposure | WF-05 |
| Request from `localhost:3000` | Browser is not CORS-authorized under approved local policy | WF-06 |
| Cookie-authenticated request attempted | Not an approved V1 transport; no implicit credential fallback | WF-06 |
| Member opens Profile after verified email | Gate reloads Firebase user, forces fresh bearer, synchronizes Account snapshot, then enables save | WF-08 |
| Member remains unverified | Save does not start; Member receives verify/resend guidance and safe form input remains | WF-08 |
| Member verified on another device | On next sign-in/Profile entry, the same gate synchronizes; no webhook/job is required | WF-08 |
| Firebase reload or email synchronization unavailable | Command does not start; explicit retry is available and no mutation is auto-retried | WF-08 |
| Profile session expired | Draft is discarded and shared UI returns Home; no Profile PII is browser-persisted | WF-08, WF-11 |
| Verification resend followed by browser restart | Resend remains disabled only until the Account-specific 90-second expiry; Firebase remains quota authority | WF-08 |
| Unverified email-change request | Current email stays visible/current; retry invokes `verifyBeforeUpdateEmail` again and no pending email is persisted in Mland | WF-08 |
| Member changes password with wrong current password | In-place re-auth fails: clear only current password, display inline error, retain new password values in component memory | WF-02, WF-08 |
| Member changes password successfully | In-place re-auth succeeds, password updated via Firebase SDK, all form fields cleared, success alert displayed | WF-02, WF-08 |
| Member without password provider views Profile | Password change section and quick action trigger are hidden; only eligible providers see credential mutations | WF-02 |
| Member changes email with in-place re-auth | Current password verifies identity, `verifyBeforeUpdateEmail` sends verification link, current email remains active | WF-08 |

## Open decisions and dependencies

| Decision / dependency | Impact on UI | Owner / condition to close |
| --- | --- | --- |
| Staging/preview/production origins and deployment recovery | Frontend cannot claim those environments are enabled | Deployment owner supplies explicit origin/operations source |
| `activeWorkshopLocationIds` interface/data-model revision | Consumer must not present raw Account assignments as effective scope | RESOLVED: `activeWorkshopLocationIds` refactored across entities, DTOs, and facade |
| FE-003 facade interface revision | Workshop picker cannot be built against a stable boundary | RESOLVED: `interfaces.md` approved |
| Accessibility/browser baseline | Acceptance cannot be measured consistently | Frontend foundation owner approves source |
| Regenerated OpenAPI/routes | API client implementation cannot rely on obsolete contract | RESOLVED: OpenAPI 3.1 regenerated |
| Verification-required gate design | `useVerifiedEmailGate` and its error/retry behavior are proposed until the revised design/contract is approved | RESOLVED: Approved by Tech Lead; frontend implements T051–T052 |

---

## Revision R-SL: session UI and route inventory

| State/action | Visible UI | Navigation | Data rule |
| --- | --- | --- |
| Guard loading | Neutral loading, no account/profile content | none | No PII/private route content |
| Firebase unconfigured | Safe unavailable/configuration state | none | No redirect loop; no private content |
| Guest opens authenticated route | No protected screen | `/login` with `replace` | Login succeeds to `/`, never `returnTo` |
| User sign-out pending | Initiating control disabled with pending state | none | No duplicate request |
| User sign-out succeeds | Home flash “Đã đăng xuất” | `/` with `replace` | Clear all drafts/private UI state |
| Sign-out fails | Safe inline/page error and retry | stay | Keep only safe current-page input |
| Firebase null / first `401` | Home flash “Phiên đã kết thúc, vui lòng đăng nhập lại” | `/` with `replace` | Clear all drafts/private UI state once |
| Valid `403` | Safe generic access-denied notice | `/` with `replace` | Retain session; no role/scope disclosure |

Route categories: (1) public/guest-capable: public browsing, Basic AI, public/guest review, Feature-003.1 booking/lookup/access/payment-return; (2) authenticated Member: Account/Profile/Address/Security aliases, retail/history, and `/design-requests/new`; (3) internal: session required here, RBAC and Workshop scope remain feature/backend-owned. UI-generated Account links are `/profile`, `/profile/addresses`, `/security/password`, `/security/email`; legacy aliases stay functional and guarded. `/sign-in` replaces to `/login`.

Google uses redirect only. The local precondition shown to implementers/testers is existing Firebase configuration plus enabled Google provider, Firebase-authorized `localhost`, and `http://localhost:5173`. Popup UI, helper, handler and test acceptance are not part of this flow.
