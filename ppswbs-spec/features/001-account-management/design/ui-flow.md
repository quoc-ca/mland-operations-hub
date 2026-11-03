# UI Flow Design: Account Management (FE-001)

**Date created**: 2026-10-28
**Date updated**: 2026-10-29
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Design status**: DRAFT/PROPOSED. This records observable UI behavior, not visual design or frontend implementation approval.

## Actors, routes and authorization

| Actor | Entry / route | Server-side rule | Status / source |
| --- | --- | --- | --- |
| Anonymous visitor | `/sign-in`, `/register`, `/forgot-password` | Only explicitly public operations support identity-provider flows. Browser routing does not authorize API. | proposed routes from plan; public-route contract must be regenerated |
| Signed-in Account owner | `/`, `/profile`, `/security/password`, `/security/email` | WF-01 supplies current actor; server limits actions to owner and checks verification where required. | proposed; FR-003–008 |
| Admin | `/admin/accounts`, `/admin/accounts/new`, `/admin/accounts/:accountId`, `/admin/roles` | Server checks current `ADMIN`, target and current resource constraints for every action. | proposed; FR-010–025 |
| Workshop-feature actor | FE-003 screen, not an FE-001 route | FE-003 authorizes actor/resource before roster/facts facade reads and before mutation. | approved behavior FR-028–032; UI owned by FE-003 |

The frontend may hide navigation as a usability affordance, but cannot decide role, status, scope or ownership. Firebase client state, a loaded roster and a rendered page may be stale; each protected command is decided on the server. A Workshop Location outage blocks only a command whose authorization requires effective Workshop scope, not Account self-service that has no scope dependency.

## Screen and interaction flows

| Screen / flow | Entry and actor | Workflow | Component/hook/API client | Contract reference | Loading / empty / validation / success / error / partial states | Recovery |
| --- | --- | --- | --- | --- | --- | --- |
| Sign-in / registration | Anonymous | WF-02, WF-06 | proposed `AuthPage` / `FirebaseAuthClient` | FR-002–006; Firebase client boundary | Show configured provider availability, validation and duplicate-submit pending state. Do not expose Account existence. Provider failure is distinct from invalid credentials. | Explicit retry/alternative method; preserve no secret beyond active secure control. |
| Bootstrap / landing | New authenticated user | WF-01, WF-02 | proposed `useCurrentAccount` | `/accounts/me` contract must be regenerated | Neutral loading while Account resolves. Active account goes Home; unverified account gets non-blocking reminder. No Account detail on failed resolution. | `401` sign in again; `403` eligibility guidance; `503` explicit retry; never infer old role/scope. |
| Profile / security | Current Account owner | WF-02, WF-06 | proposed `ProfilePage`, `useProfile` | FR-005–008; OpenAPI profile/email endpoints | Field validation inline; committed success only after server response. Retain safe unsaved full name/phone after validation/dependency errors. Password/token never retained. | `409` reconcile current values when applicable; `401/403` safe account recovery. |
| Admin list/detail | Admin | WF-01, WF-03, WF-04 | proposed `AdminAccountListPage`, `AdminAccountDetailPage`, `useAdminAccounts` | FR-016–025; OpenAPI requires regeneration | Loading progress; explicit empty result; restricted detail data; success only after commit. | `409` reload before reapply; `503` keep safe form data for explicit retry. |
| Create/resend internal Account | Admin | WF-03 | proposed `InternalAccountCreatePage`, `useAdminAccounts` | FR-011–015, 023–025 | Validate role/location before submit. Pending setup is not active access. Repeat click/retry preserves one intended provisioning request. Never display raw action link. | Provider/notification failure gives safe recovery; resend/reconciliation does not create a second account. |
| Role/location/status action | Admin | WF-03, WF-04 | proposed detail page/hook | FR-017–021 | Explain impact, show current committed role/status/effective scope. Location deactivation does not claim role/lifecycle change. | Version conflict reloads; denied access returns to allowed safe view. |
| Fixed role catalogue | Admin | WF-01 | proposed `RoleCataloguePage` | FR-009–010 | Loading/error state; display read-only fixed role descriptions. No permission/role editing controls. | `401/403` account recovery; `503` retry. |
| Workshop target picker | Authorized FE-003 actor | WF-05 | FE-003-owned component; proposed FE-001 facade client at module boundary | FR-029–031; internal interface contract missing | Load candidates by canonical location/role. Empty means no eligible candidate, not directory failure. Show current display name and optional opaque Account ID only. | `503` preserves safe FE-003 input; `409 TARGET_NOT_ELIGIBLE` requires new selection without lifecycle disclosure. |

## Session, data handling and accessibility

### Approved local browser/security baseline

- Firebase bearer is attached in `Authorization`; FE-001 does not use browser auth cookies, app server session, app JWT or refresh-token system.
- Local browser origin is exactly `http://localhost:5173`. CORS allows only explicit configured origins, necessary methods/headers and preflight; credentials mode is disabled. No wildcard is allowed.
- A later cookie-based flow requires a new approved CORS/CSRF/session/logout design; it is not enabled by this decision.
- On `401`, return to sign-in after retaining only safe navigation/form context. On `403`, do not auto-retry. On `409`, reload/reconcile. On `503`, retain safe unsaved input and provide explicit retry.
- Do not render/store raw ID token, provider UID, password, reset/action link, another Account’s contact data or lifecycle detail to simplify recovery.

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

## Open decisions and dependencies

| Decision / dependency | Impact on UI | Owner / condition to close |
| --- | --- | --- |
| Staging/preview/production origins and deployment recovery | Frontend cannot claim those environments are enabled | Deployment owner supplies explicit origin/operations source |
| `activeWorkshopLocationIds` interface/data-model revision | Consumer must not present raw Account assignments as effective scope | Contract/data-model owner reconciles source and code |
| FE-003 facade interface revision | Workshop picker cannot be built against a stable boundary | Account/Workshop owners approve `interfaces.md` revision |
| Accessibility/browser baseline | Acceptance cannot be measured consistently | Frontend foundation owner approves source |
| Regenerated OpenAPI/routes | API client implementation cannot rely on obsolete contract | Contract owner regenerates after design approval |
