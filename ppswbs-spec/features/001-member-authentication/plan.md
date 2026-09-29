# Implementation Plan: Member Registration and Sign-in

**Branch**: `001-member-authentication` | **Date**: 2026-09-29 | **Spec**: [spec.md](./spec.md)

## Summary

Deliver Member-only authentication through Firebase Authentication (Google and email/password), while MySQL remains authoritative for Member state, entitlement, consent, Guest-booking association, and audit records. The browser obtains a Firebase ID token; Spring Boot verifies it with Firebase Admin before every Member API request. No password, Firebase token, or account derived merely from matching email is stored.

This feature also adds two cross-domain gates: explicit current-policy acceptance before Member access, and booking-email confirmation before a Guest workshop booking can reserve capacity or enter payment. A verified Member may preview and explicitly import eligible Guest bookings exactly once.

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot, Spring Security, Spring MVC, Spring Data JPA, Bean Validation, Thymeleaf, htmx, Firebase Admin SDK, Firebase Web Auth SDK, Flyway  
**Storage**: MySQL 8.4 LTS; Firebase Authentication is external identity storage  
**Testing**: JUnit 5, MockMvc, Testcontainers MySQL, Firebase Auth Emulator, browser smoke tests  
**Target Platform**: Server-rendered web; latest two desktop Chrome/Edge/Firefox/Safari, Chrome Android, Safari iPhone  
**Project Type**: Spring Boot web application with Thymeleaf/htmx UI and REST APIs  
**Performance Goals**: 95% normal Member create/return flows within two minutes; returning sign-in within one minute  
**Constraints**: no custom JWT/session or password storage; Firebase Admin token verification; MySQL owns roles/status/audit; no email-based merge; JPA plus Flyway; bilingual UI; no committed identifiers/secrets  
**Scope**: Member role only; Google and email/password; consent, recovery/verification, Guest history import, booking email confirmation. Staff/Owner/Admin, profile/email changes, deletion, loyalty, and multi-device management are out of scope.

## Constitution Check

### Requirements satisfied

- Firebase Web SDK alone handles credentials, reset/verification emails, and provider linking. Spring receives only an ID token and verifies it using Firebase Admin.
- A Member is keyed by Firebase `uid` (`external_user_id`); email is an attribute/correlation signal, never a merge key.
- MySQL owns Member role/status, policy acceptance, booking linkage, authorization, and immutable audit events.
- Persistence uses JPA repositories and Flyway migrations. APIs are versioned, plural, kebab-case.
- Errors are typed and non-enumerating. Raw ID tokens, reset codes, booking-confirmation tokens, and passwords are neither logged nor stored in plaintext.

### Blocking architecture exception requiring ratification

`AGENT.md` requires core forms and links to work without JavaScript, but Firebase Web Authentication needs JavaScript to obtain the credential/ID token for Google and email/password login. A server-side fallback would make Mland receive credentials or replace the approved Firebase-client identity agreement.

Proposed bounded exception: auth pages remain Thymeleaf server-rendered; Firebase credential actions require JavaScript; a `noscript` view clearly explains the requirement and gives a safe support/retry path. Booking-email confirmation remains a server-rendered JavaScript-independent link flow. This must be ratified before implementation. No Mland password-capture workaround is allowed.

### Release prerequisites outside code

1. Ratified exception above.
2. Approved versioned Vietnamese/English Terms and Privacy content plus an effective-version publishing process.
3. Firebase configuration: enabled Google and Email/Password providers, authorized domains, and non-production Emulator setup.
4. Approved transactional-email adapter and sender identity for booking confirmation; development/test use a fake/captured-mail adapter.

## Proposed Structure

```text
ppswbs_backend/
├── src/main/java/edu/fpt/sep490_g22/ppswbs_backend/
│   ├── configuration/             # Firebase, security, locale, properties
│   ├── common/                    # typed errors, correlation IDs, audit support
│   ├── members/{facade,web,application,domain,infrastructure}/
│   └── workshopbooking/           # confirmation/expiry/capacity-payment gate
├── src/main/resources/
│   ├── db/migration/
│   ├── templates/member-auth/     # server-rendered pages/fragments
│   ├── templates/workshop-bookings/
│   ├── static/js/member-auth.js   # Firebase browser integration
│   └── i18n/messages*.properties
└── src/test/java/.../

ppswbs-spec/features/001-member-authentication/
├── spec.md  plan.md  research.md  data-model.md  quickstart.md
├── contracts/member-auth.openapi.yaml
└── tasks.md
```

## Design

### Architecture convention alignment

Member authentication is implemented inside the `members` business module, not a standalone `memberauth` module. Firebase adapters, controllers, HTTP DTOs, JPA entities, repositories, and audit records remain internal. Other domains may use only `members.facade` for a synchronous Member entitlement or summary use case. Guest-history import must use `workshopbooking.facade`, never direct booking persistence.

### Identity, provisioning, entitlement

1. Thymeleaf renders Google as the primary action and email/password as fallback. Runtime Firebase configuration is injected from environment-backed server settings; it is never committed.
2. Firebase Web SDK signs in/registers, dispatches recovery/verification email, or explicitly links a provider; it then calls `PUT /api/v1/members/me` with an ID token.
3. Spring Security verifies the token through Firebase Admin. No client-posted email or unverified claim is identity proof.
4. Provisioning creates/reuses exactly one Member by verified UID transactionally. A matching email on another UID is never merged; the user must sign in with the existing method then use explicit Firebase linking.
5. A Member is `PENDING_POLICY_ACCEPTANCE` until both current policies are accepted, then `ACTIVE`. `SUSPENDED` always denies Member access.
6. Authorization reevaluates status/current consent on each protected request. Firebase controls supported browser persistence; logout calls Firebase sign-out and clears UI state.
7. Return paths use a same-origin allow-list; external, protocol-relative, and malformed values are rejected.

### Policy, recovery, and linked methods

- `GET /api/v1/policies/current` exposes public current policy metadata/content locations. `POST /api/v1/members/me/policy-acceptances` accepts only the effective Terms/Privacy pair and writes immutable acceptance evidence.
- A newer effective policy causes protected APIs to return `403 POLICY_ACCEPTANCE_REQUIRED`; browser flow signs out, re-signs in, then accepts. Guest flows are unaffected.
- Email/password recovery and verification use Firebase facilities and return generic outcomes. Google-only recovery is through Google; no manual identity transfer exists.
- Provider linking begins only from an authenticated Firebase identity. Collisions, cancellation, and reauthentication errors are recoverable bilingual messages; no automatic Firebase/MySQL user join occurs.

### Booking confirmation and Guest history import

1. Guest workshop booking enters `EMAIL_CONFIRMATION_PENDING`: capacity is temporary and no invoice/payment/deposit checkout exists.
2. The booking service stores only a hash of a random one-time token and sends a server-rendered confirmation link. Valid confirmation marks email confirmed and unlocks the normal booking payment path.
3. An idempotent expiry job cancels pending bookings at 15 minutes, releases temporary capacity, and ensures no invoice/payment remains. No resend exists.
4. After Firebase reports the Member email verified, an import preview includes bookings with matching canonical email and confirmed booking email, across all booking statuses. The Member must explicitly confirm.
5. Import is transactional/idempotent: it sets the existing booking's sole `member_id`, never duplicates a row, and audits linked/skipped outcomes.

### API/error contract

Concrete endpoint shapes and status codes are in [contracts/member-auth.openapi.yaml](./contracts/member-auth.openapi.yaml). All errors have a stable code, safe bilingual message key, correlation ID, and applicable field errors. Account/recovery/provider errors do not reveal whether an email has an account or which method owns it.

## Data and Migration Strategy

See [data-model.md](./data-model.md). Flyway creates Member, policy, acceptance, audit, booking-confirmation, and linkage structures. No historic association runs from typed email; imports require verified claims and explicit confirmation.

## Delivery Sequence

1. Ratify exception and prepare policy/email/Firebase inputs.
2. Establish migrations, configuration, Firebase verification, typed errors/audit foundation, test infrastructure.
3. Deliver signup/signin, provisioning, consent, logout, and protected return (US1/US2).
4. Deliver booking email confirmation/expiry payment gate (US5) with booking-domain ownership.
5. Deliver recovery/verification and explicit history import (US3), then explicit provider linking (US4).
6. Validate suspension, policy version invalidation, non-enumeration, token handling, idempotency, auditability, accessibility, and browsers.

## Complexity Tracking

| Decision | Why needed | Rejected alternative |
|---|---|---|
| Firebase JS for credentials | Preserves approved Firebase trust boundary and prevents server password custody | Server no-JS password form |
| MySQL entitlement per protected request | Provider identity alone cannot grant Mland role/status | Trust provider claims for roles |
| Explicit history import | Avoids surprising association and creates an audit action | Auto-import on sign-in/typed email |
| Hashed one-time booking token + expiry job | Enforces 15-minute business gate safely | Plaintext tokens, payment while pending, resend loops |

## Verification Strategy

- Unit: UID-only provisioning, entitlement, policy gate, return path validation, hash handling, import idempotency, expiry transitions.
- Integration: migrations/constraints, Auth Emulator token verification, API boundaries, typed errors, expiry idempotency.
- MVC/browser: primary/fallback sign-in, generic recovery, bilingual UI, `noscript`, import confirmation, logout, browser matrix.
- Security: malformed/expired/revoked token rejection, no email merge, no secrets in logs, open-redirect rejection, CSRF policy, authorization on every Member API.
