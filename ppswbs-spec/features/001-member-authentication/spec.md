# Feature Specification: Member Registration and Sign-in

**Feature Branch**: 001-member-authentication
**Status**: Draft — implementation baseline reset 2026-09-30
**Purpose**: define the Member identity, consent, entitlement, and safe Guest-booking import contract before implementation.

## Scope and ownership

The members module owns Firebase identity verification, Member/account state,
policy acceptance, entitlement decisions, and Member-auth audit evidence. It
exposes only its facade to other business modules. Workshop-booking linkage is
requested through workshopbooking.facade; the members module never writes
booking persistence directly.

Firebase proves an identity only. MySQL is the source of application account
status and roles MEMBER, STAFF, OWNER, and ADMIN_TECHNICAL. A Firebase custom
claim, provider, or email text must never grant an Mland role or merge two
accounts.

This feature defines a reusable entitlement/route-authorization seam for
Member-only journeys. It does not create ready-ring checkout or Member-history
routes; their owning modules apply that seam. Public workshop booking and
booking lookup remain public.

## Functional requirements

- FR-001: Verify every Firebase bearer token server-side before using its UID;
  never capture, persist, or log credentials or raw tokens.
- FR-002: Provision exactly one account/Member per verified UID and resolve
  application roles, status, and entitlement from MySQL only.
- FR-003: Preserve the seven documented HTTP paths, validate their DTOs, and
  return typed safe errors with correlationId rather than entities or traces.
- FR-004: Require immutable acceptance of exactly the current Terms and Privacy
  versions, structurally enforcing one EFFECTIVE document per policy type.
- FR-005: Require a one-time, hashed booking-email confirmation before payment,
  with an enabled idempotent 15-minute expiry transition.
- FR-006: Import Guest bookings only after verified matching email and explicit
  confirmed true request; preserve booking status and idempotency.
- FR-007: Preserve V1 data through append-only Flyway migrations and use
  separated non-root migrator/runtime database principals.
- FR-008: Treat MySQL 9.7.2 migration/integration evidence as the release
  compatibility gate; H2 fast tests are supplemental only.

## Success criteria

- SC-001: Repeat/concurrent provisioning of one UID results in exactly one
  account/Member without email-based merge.
- SC-002: Every protected request is denied when token, MySQL role/status, or
  required policy acceptance is invalid, and returns no protected data.
- SC-003: Valid email confirmation is consumed once; invalid, expired, used,
  and malformed token inputs are indistinguishable to the client.
- SC-004: V1-to-forward migration preserves all existing Members, policy
  acceptances, bookings, and confirmation records on MySQL 9.7.2.
- SC-005: Fresh verification records an exact command and passing result for
  MySQL migration/integration, Firebase browser smoke, and leakage checks.

## User stories and acceptance criteria

### US1 — Register or sign in as a Member (P1)

A Guest can use Google or Firebase email/password sign-in, then provision or
reuse exactly one Member identity and return to a safe same-origin journey.

- A verified Firebase UID provisions one Member/account idempotently; concurrent
  first-use requests result in one identity.
- Different Firebase UIDs are never merged because their email text matches.
- Browser credential actions use the Firebase Web SDK. The server receives only
  a fresh Firebase ID token in Authorization: Bearer and verifies it with the
  Firebase Admin SDK before use.
- The UI provides a recoverable, bilingual outcome for cancelled/provider/input
  failure and does not reveal whether an email belongs to another person.
- No password, raw Firebase token, credential code, or full email is written to
  an API error, audit event, or application log.

### US2 — Receive and retain entitlement (P1)

An authenticated Member can use a Member-only journey only while its MySQL
account is active and current policy acceptance exists.

- A protected request verifies its Firebase token, resolves its MySQL account
  and role/status, and evaluates current Terms and Privacy acceptance.
- A suspended account receives a neutral denial. Missing/stale policy acceptance
  receives typed POLICY_ACCEPTANCE_REQUIRED without protected content.
- Sign-out removes browser identity state. Return-to values are allow-listed
  same-origin paths; external and malformed values are ignored.
- A Member can accept exactly the current Terms and Privacy versions. Acceptance
  is immutable, idempotent, correlation-linked evidence.

### US3 — Recover email access and import eligible Guest bookings (P2)

An email/password Member can request Firebase recovery/verification with generic
outcomes. Only after Firebase reports the matching email verified and the Member
explicitly confirms may eligible Guest bookings be linked once.

- The preview is empty/denied safely when email is unverified, entitlement is
  absent, or the account is suspended.
- Guest booking import requires the explicit JSON confirmation declared in the
  API contract; omission or false is a validation failure.
- All eligible unlinked bookings, regardless of their business status, are
  linked once and retain their true status. Repeating import is idempotent.
- No automatic email-based association occurs, including after a profile email
  change.

### US4 — Link an additional sign-in method safely (P2)

A signed-in Member can link a second Firebase method only after the provider
requires proof of the current identity.

- Provider collision, cancellation, and reauthentication failure preserve the
  existing Member/account and return safe bilingual guidance.
- No manual identity transfer and no automatic Firebase/MySQL join is in V1.
- Provider-link success and failure are audited without credential material.

### US5 — Confirm a Guest booking email before payment (P1)

A Guest booking remains temporary until its email confirmation token is consumed.

- A booking starts EMAIL_CONFIRMATION_PENDING with one hashed, one-time token
  that expires after 15 minutes.
- Payment, invoice settlement, and committed capacity are refused while
  confirmation is pending.
- Valid, unexpired token consumption confirms once and makes the normal booking
  flow eligible. Invalid, expired, used, or malformed tokens receive one generic
  safe client error.
- An enabled, idempotent expiry process cancels overdue temporary bookings,
  releases temporary capacity/invoice state, and never reopens payment.

## Public HTTP contract

The detailed source of wire truth is contracts/member-auth.openapi.yaml. Its
seven existing paths and declared success/error statuses are preserved. All JSON
responses use a typed safe error envelope when an error occurs:

- code: stable machine-readable error code.
- message: safe, localized user-facing message.
- correlationId: request correlation value for support.

Validation returns 400 without stack traces, raw tokens, credentials, or
unnecessary personal data. Authentication errors return 401; entitlement and
policy denials return 403. A missing booking remains 404. Controllers use DTOs,
never JPA entities.

## Data, policy, and audit invariants

- MySQL contains the authoritative accounts, roles, Member status, policy
  acceptance, and audit data; Firebase is not an authorization store.
- Existing V1 data is preserved. Future schema changes are append-only Flyway
  migrations; V1__init_member_auth.sql is never edited after application.
- Exactly one EFFECTIVE policy document exists per policy type. This is enforced
  in a forward migration by a stored generated effective-policy key and unique
  index, not merely by application convention.
- Audit evidence is append-only and stores redacted metadata only. Booking
  confirmation stores a token hash, never the plaintext token.

## Out of scope and dependencies

- Ready-ring routes, orders, payments, payment provider callback behavior, stock
  reservation, and delivery are owned by later features.
- Transactional-email provider selection, sender identity, Firebase project
  credentials, authorized domains, and policy publication approval remain
  release prerequisites; no secret or provider payload belongs in this spec.
- Accessibility is best effort; browser baseline is the latest two desktop
  Chrome, Edge, Firefox, and Safari releases, plus Chrome Android and Safari
  iPhone.
