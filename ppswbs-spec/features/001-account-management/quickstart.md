# Quickstart Validation Guide: Account Management (FE-001)

**Date created**: 2026-10-26
**Date updated**: 2026-11-01
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Purpose**: Runnable local validation scenarios that prove FE-001 works end-to-end. This is a validation guide, not implementation instructions. Implementation tasks are in [tasks.md](tasks.md).

**Scope**: Local environment only (`http://localhost:5173` frontend, Spring Boot backend, local MySQL, Firebase emulator or configured test project). Staging/production is a separately approved release scope.

---

## Prerequisites

1. **Backend**: Spring Boot application started with valid local configuration (no committed credential defaults).
   - MySQL running and migrated (all Flyway migrations applied, including the `AuditOutcome`/`VARCHAR(16)` migration)
   - `spring.jpa.hibernate.ddl-auto=validate` passes on startup
   - Firebase Admin SDK initialized with a test service account credential (via approved environment/secret channel)
2. **Frontend**: React + Vite dev server running at `http://localhost:5173`
3. **Firebase**: Test project configured; at least one email/password account available for test identity
4. **Admin account**: At least one `ADMIN` account bootstrapped via the operations procedure (outside web UI)
5. **Workshop Location**: At least one active `workshopLocationId` available from FE-003.1 for location-scoped tests

Refer to [data-model.md](data-model.md) for entity invariants and [contracts/interfaces.md](contracts/interfaces.md) for facade types.

---

## Scenario V-01: Email/password registration and bootstrap (US1)

**Covers**: FR-001, FR-002, FR-003, FR-007, SC-001

**Setup**: No existing Account for the test email.

```
1. Open http://localhost:5173/register
2. Enter a new valid email and password → submit
3. Firebase completes registration → backend /accounts/me (POST) is called
```

**Expected outcomes**:
- Exactly one Account row created; `firebase_uid` matches the Firebase UID; `role = MEMBER`; `status = ACTIVE`
- Exactly one AccountProfile row: `full_name` equals the email local part (before `@`); `phone` is null
- Browser navigates to Home (`/`)
- Repeated bootstrap (refresh / recall `/accounts/me`) returns the same Account without changing role, status, or profile

**Negative cases**:
- Submit without password → inline validation error; no Firebase or backend call
- Submit with malformed email → inline validation error; no Firebase or backend call

---

## Scenario V-02: Unverified email sign-in and verification gate (US2)

**Covers**: FR-004, SC-001

**Setup**: Account bootstrapped in V-01; email not yet verified in Firebase.

```
1. Sign in with the test email/password
2. Observe Home after bootstrap
```

**Expected outcomes**:
- Non-blocking verification reminder is visible on Home
- The user can navigate to `/profile`
- Attempting a verification-required action (e.g., profile save if configured as verification-required) is denied with clear guidance
- No role/status change occurs

---

## Scenario V-03: Profile save and email sync (US2)

**Covers**: FR-006, FR-007, FR-008, SC-002

**Setup**: An `ACTIVE` account with a verified email.

```
1. Navigate to /profile
2. Enter a valid full name (1–100 Unicode chars, no ISO control chars) and optional phone (7–30 chars)
3. Submit
```

**Expected outcomes**:
- PUT `/accounts/me/profile` returns the updated profile; page shows committed values
- `Account.login_email`, `role`, `status`, and location scope are unchanged
- Inline validation rejects: empty name, name > 100 chars, name with ISO control chars, phone < 7 or > 30 chars

**Email sync**:
```
1. Change login email via Firebase provider flow (outside FE-001 UI)
2. Sign in with the new email (new Firebase ID token)
3. POST /accounts/me/email-sync with the current bearer
```
**Expected**: `Account.login_email` updates to the provider email; browser-supplied email is never accepted

---

## Scenario V-04: Admin creates and activates a Staff account (US3)

**Covers**: FR-011, FR-012, FR-013, FR-014, FR-015, SC-003

**Setup**: Admin signed in; one active `workshopLocationId` available.

```
1. Navigate to /admin/accounts/new
2. Select role = STAFF, enter intended email, select one active workshopLocationId
3. Submit (with a fresh Idempotency-Key header)
4. POST /admin/accounts returns 201; one PENDING_SETUP Account created
```

**Expected outcomes**:
- Account: `status = PENDING_SETUP`; `role = STAFF`; one active AccountLocationAssignment
- NotificationIntent created (FE-07 delivery is separate)
- AccountAuditEvent: `action = CREATE_INTERNAL_ACCOUNT`; `outcome = SUCCESS`

**Idempotency**:
```
5. Retry the exact same request body with the same Idempotency-Key
```
**Expected**: Original result returned; no second Account or invitation

**Changed request retry**:
```
6. Retry with the same Idempotency-Key but different email
```
**Expected**: `409` conflict; no new Account

**Activation**:
```
7. The intended person signs in with their Firebase identity (email matches invitation)
8. POST /accounts/me (bootstrap)
```
**Expected**: Account transitions to `ACTIVE`; no Member Account created; audit records `ACTIVATION`

**Resend (5-min cooldown)**:
```
9. Admin navigates to /admin/accounts/{accountId}
10. POST /admin/accounts/{accountId}/resend-invitation
```
**Expected**: Fresh setup link sent (via FE-07 intent); no raw link in audit or NotificationIntent
**After 5 min**: Second resend succeeds; within 5 min: `429` or cooldown error

---

## Scenario V-05: Account suspension and restoration (US4)

**Covers**: FR-019, FR-020, FR-021, SC-004

**Setup**: An `ACTIVE` Member or Staff account.

```
1. Admin navigates to /admin/accounts/{accountId}
2. Submit suspension with a non-empty reason
3. PUT /admin/accounts/{accountId}/status → Account status = SUSPENDED
```

**Expected outcomes**:
- `Account.status = SUSPENDED`; `resume_status` = `ACTIVE` (prior state)
- `AccountAuditEvent`: `action = SUSPEND`; `outcome = SUCCESS`; `reason` in safe metadata; no full_name/phone/token
- Next protected request from the suspended account → `403` (immediate, independent of Firebase revocation or email delivery)

**Restoration guard**:
```
4. Attempt POST /admin/accounts/{accountId}/status (restore) before Firebase revocation completes
```
**Expected**: `409` or `503` — restoration blocked until revocation complete

**Restoration**:
```
5. Firebase revocation completes (reconciled)
6. Admin submits restore
```
**Expected**: `Account.status = ACTIVE` (restored prior state); owner must sign in again; prior sessions are invalid

---

## Scenario V-06: Admin list, filter, and role catalogue (US5)

**Covers**: FR-016, FR-010, SC-006

```
1. GET /admin/accounts?email=&role=STAFF&status=ACTIVE&page=0
```
**Expected**:
- Paginated list, ordered by recent change/creation
- No credentials, Firebase UIDs, or unrelated business data in response
- `accountId` is opaque positive-integer string

```
2. GET /roles
```
**Expected**:
- Fixed role catalogue: MEMBER, STAFF, MANAGER, ADMIN with descriptions
- No permission/role editing controls available in UI

---

## Scenario V-07: Workshop facade — target facts and roster (FR-028–FR-032)

**Covers**: FR-028–FR-032; requires FE-003 integration test

> These are Java module-boundary calls, not REST endpoints. Validate via integration test or a controlled FE-003 stub.

**findEligibleRoster**:
```java
RosterOutcome result = accountFacade.findEligibleRoster(
    workshopLocationId,   // canonical active location ID
    AccountRole.STAFF
);
// AVAILABLE: entries contain {accountId, displayName (= Profile.full_name), role}
// No email, phone, provider UID, or lifecycle detail
```
**Expected**: `AVAILABLE` with ACTIVE Staff accounts whose effective scope includes the location

**findTargetFacts**:
```java
TargetFactsOutcome result = accountFacade.findTargetFacts(AccountId.of(42L));
// FOUND: {accountId, role, status, activeWorkshopLocationIds}
// NOT_FOUND: account conclusively absent
// UNAVAILABLE: Account or Workshop Location scope cannot be verified
```
**Expected on UNAVAILABLE**: consumer does not start/commit mutation; returns `503`; safe input preserved

---

## Scenario V-08: Identity gate and security boundaries

**Covers**: FR-026, FR-027; WF-01, WF-06

```
1. Call any protected route without Authorization header → 401
2. Call with a revoked/expired Firebase token → 401
3. Call from http://localhost:3000 → CORS blocked (not allowed origin)
4. Call with a valid token for a SUSPENDED account → 403
5. Suspended account attempts a previously rendered page action → 403 (no stale UI bypass)
```

---

## Scenario V-09: Schema startup validation (WF-07)

**Covers**: Constitution §IV; data-model.md §Audit outcome

```
1. Start backend on a fresh MySQL database (all Flyway migrations applied from V1)
   → Application starts successfully; Hibernate validate passes
2. Start backend on a V5-migrated database (upgrade path)
   → Application starts successfully; existing rows preserved; AuditOutcome column = VARCHAR(16)
```

**Expected**: Both fresh and upgrade startup succeed. If either fails, the audit-outcome migration is incorrect.

---

## Error Presentation Spot-Checks

| Trigger | Expected response |
|---------|-------------------|
| Malformed `accountId` in URL (non-positive, padded, blank) | `400` with stable error code; no stack trace |
| Profile save with stale Admin version | `409` with reload guidance; no data overwrite |
| Location becomes unavailable during location-scoped action | `503`; no partial mutation; safe input preserved |
| Duplicate account create for existing identity | Safe guidance to use original sign-in method; no 2nd Account |
| Password reset for non-existent email | Response does not reveal whether email account exists |

---

## Acceptance Evidence Required

Before handoff, retain evidence for:

1. Flyway fresh-database migration (V1 to current) — startup log screenshot or CI run
2. Flyway upgrade migration (V5 baseline → current `AuditOutcome`/`VARCHAR(16)`) — startup log + sample query
3. Spring Modulith boundary verification — test output confirming `accountmanagement` module detects correctly
4. Scenarios V-01 through V-09 executed locally — annotated screenshots or test output per scenario
5. WCAG 2.2 AA baseline — accessibility scan output for all FE-001 screens (frontend foundation owner)
