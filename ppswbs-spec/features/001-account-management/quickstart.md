# Quickstart — Account Management (FE-001)

**Date created**: 2026-10-25
**Author**: Phan Sơn

> **Supersession note**: This file replaces the obsolete `Member Profile — planned
> verification` quickstart (dated 2026-10-19). That file described legacy SSR/cookie
> session scenarios incompatible with the current React + Firebase REST design.

> **Status**: Specification only. Executable verification tooling is created during
> the implementation phase (tasks T049–T051). All scenarios require:
> - Firebase Auth Emulator (never use the production Firebase project)
> - A dedicated test MySQL database (Testcontainers or local)
> - No production credentials, tokens, or customer data

---

## Verification Scenarios

These eight scenarios map to the Verification Plan in [plan.md §Verification Plan](plan.md#verification-plan).
Each scenario describes the input, the expected observable outcome, and the requirements it validates.

---

### V-01 — Spring Modulith module detection

**What it checks**: The `accountmanagement` package is detected as a distinct named
Spring Modulith module, not collapsed into the parent `features` package.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Run `ApplicationModules.verify()` in a Spring Boot test (`@SpringBootTest`) | No exception; log output shows `accountmanagement` as a named module |
| 2 | Confirm no other feature module's internal types appear in `accountmanagement`'s dependency graph | `verify()` passes without cross-module internal access violations |

**Requirements**: Constitution Principle I — Module Integrity.

---

### V-02 — Account bootstrap idempotency (`PUT /accounts/me`)

**What it checks**: Calling `PUT /api/v1/accounts/me` twice with the same Firebase UID
produces exactly one database row.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Obtain a Firebase Emulator ID token for UID `test-uid-001` | Token issued |
| 2 | `PUT /api/v1/accounts/me` with `Authorization: Bearer <token>` | `200 OK`; response body contains `accountId` |
| 3 | `PUT /api/v1/accounts/me` with a fresh token for the same UID | `200 OK`; **same** `accountId` as step 2 |
| 4 | `SELECT COUNT(*) FROM accounts WHERE firebase_uid = 'test-uid-001'` | `1` |

**Requirements**: FR-001, FR-013, R04.

---

### V-03 — Internal account creation idempotency (`POST /admin/accounts/internal`)

**What it checks**: `POST /api/v1/admin/accounts/internal` with the same `Idempotency-Key`
twice creates exactly one account and one provisioning row.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Obtain an Admin Firebase Emulator token | Token issued |
| 2 | `POST /api/v1/admin/accounts/internal` with `Idempotency-Key: idem-key-001` and payload `{ role: STAFF, … }` | `200 OK`; `accountId` returned |
| 3 | Repeat step 2 identically (same key, same payload) | `200 OK`; **same** `accountId` |
| 4 | `SELECT COUNT(*) FROM accounts WHERE id = <accountId>` | `1` |
| 5 | `SELECT COUNT(*) FROM internal_account_provisionings WHERE idempotency_key = 'idem-key-001'` | `1` |
| 6 | `POST /api/v1/admin/accounts/internal` with same key `idem-key-001` but **different** payload | `409 IDEMPOTENCY_KEY_CONFLICT` |

**Requirements**: FR-014, R05.

---

### V-04 — Suspend → 403 ACCOUNT_SUSPENDED

**What it checks**: After an Admin suspends an account, any subsequent request with
a previously valid token is denied immediately.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Bootstrap account `test-uid-002` (`PUT /accounts/me`); record `accountId` | `200 OK` |
| 2 | Obtain Admin token; `POST /api/v1/admin/accounts/{accountId}/suspend` with `{ reason: "test" }` | `200 OK`; account `status = SUSPENDED` |
| 3 | Attempt `GET /api/v1/accounts/me` with the original `test-uid-002` token | `403 ACCOUNT_SUSPENDED` |
| 4 | `SELECT status FROM accounts WHERE id = <accountId>` | `SUSPENDED` |
| 5 | `SELECT session_revocation_confirmed_at FROM accounts WHERE id = <accountId>` | `NULL` (revocation not yet confirmed in test; depends on Emulator behavior) |

**Requirements**: FR-019, FR-020, R01, R02.

---

### V-05 — Restoration blocked until revocation confirmed

**What it checks**: `POST /admin/accounts/{id}/restore` returns `409 REVOCATION_PENDING`
when `session_revocation_confirmed_at IS NULL`.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Use a suspended account from V-04 (or create and suspend a new one) | `status = SUSPENDED`, `session_revocation_confirmed_at = NULL` |
| 2 | Admin calls `POST /api/v1/admin/accounts/{id}/restore` | `409 REVOCATION_PENDING` |
| 3 | Manually update `session_revocation_confirmed_at = NOW()` (simulating confirmed revocation in test) | — |
| 4 | Admin calls `POST /api/v1/admin/accounts/{id}/restore` again | `200 OK`; `status = ACTIVE` |

**Requirements**: FR-020, R03.

---

### V-06 — Email sync blocked for unverified email

**What it checks**: `PUT /api/v1/accounts/me/profile` is denied when the caller's
Firebase ID token carries an unverified email.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Obtain a Firebase Emulator token for `test-uid-003` with `email_verified = false` | Token issued |
| 2 | `PUT /api/v1/accounts/me/profile` with `Authorization: Bearer <unverified token>` | `403 EMAIL_NOT_VERIFIED` |
| 3 | Verify no profile row was created or modified | `SELECT COUNT(*) FROM account_profiles WHERE account_id = <id>` → unchanged |

> Note: This also applies to `POST /api/v1/accounts/me/email-sync` which additionally
> requires a verified email in the token before updating `accounts.login_email`.

**Requirements**: FR-006, FR-007, R06.

---

### V-07 — Optimistic lock conflict on role/location assignment

**What it checks**: `PUT /api/v1/admin/accounts/{id}/role-location` with a stale
`version` returns `409 OPTIMISTIC_LOCK_CONFLICT`.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Bootstrap and provision an internal `STAFF` account; note `version = 0` | — |
| 2 | Admin calls `PUT /api/v1/admin/accounts/{id}/role-location` with `version: 0` and valid location | `200 OK`; `version` incremented to `1` |
| 3 | Admin calls `PUT /api/v1/admin/accounts/{id}/role-location` again with stale `version: 0` | `409 OPTIMISTIC_LOCK_CONFLICT` |
| 4 | Admin calls with correct `version: 1` | `200 OK` |

**Requirements**: FR-021, R05 (version tracking).

---

### V-08 — Flyway migration integrity

**What it checks**: `V5__account_management.sql` runs cleanly on an empty schema,
and V1–V4 checksums remain byte-for-byte unchanged.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Start Testcontainers with a fresh MySQL 8.4 image | Container running |
| 2 | Spring Boot application starts; Flyway runs all migrations V1→V5 | No migration error; Flyway `flyway_schema_history` shows V1–V5 all `success = true` |
| 3 | `SELECT checksum FROM flyway_schema_history WHERE version IN ('1','2','3','4')` and compare with known-good checksums | Checksums unchanged |
| 4 | `SHOW TABLES` | All five new tables (`accounts`, `account_profiles`, `account_location_assignments`, `internal_account_provisionings`, `account_audit_events`) present |

**Requirements**: Constitution Principle I — Flyway append-only; FR-023 (migration must not touch existing data).

---

## Running Order

For a clean verification pass, run in this order:

1. V-08 (migration integrity) — must pass before any other test can run
2. V-01 (module detection) — architectural gate
3. V-02 (bootstrap idempotency)
4. V-03 (internal account idempotency)
5. V-04 (suspension denial)
6. V-05 (restoration gate)
7. V-06 (email verification guard)
8. V-07 (optimistic lock)

## Post-implementation checks (full test phase)

After V-01–V-08 pass, the following additional checks should be exercised as
part of the full implementation test pass (tasks T049–T051):

- Strict profile validation edge cases (max-length name, boundary phone values)
- Concurrent bootstrap calls for same UID (race condition on unique constraint)
- Admin list pagination, search by email/name, filter by role/status
- 5-minute invitation resend cooldown enforcement
- Audit event row count and `metadata` JSON schema compliance
- No PII leak in audit: verify `metadata` contains no `full_name`, `phone`, raw tokens
- Frontend: `EmailVerificationBanner` shown for unverified accounts; non-blocking
- Firebase Auth Emulator teardown: no credentials or tokens persist between test runs
