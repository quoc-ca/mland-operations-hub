# Data Model: Account Management (FE-001)

**Date created**: 2026-10-25
**Author**: Phan Sơn

> **Supersession note**: This file replaces the obsolete `Member Profile` data model
> (`data-model.md` dated 2026-10-24). That model described a legacy SSR/Thymeleaf
> `member_profiles` schema incompatible with Constitution v3.0.0. Do not use it as
> implementation guidance.

> **Open item [G05]**: The relationship between the new `accounts` table and the
> existing `members`/`member_profiles` tables must be reconciled by a Product + Tech Lead
> decision before `V5__account_management.sql` is written. See plan.md §Dependencies
> DEP-03. Do not silently drop or rename existing columns in V1–V4 migrations.

---

## 1. Overview

FE-001 owns five tables. Firebase Authentication remains the identity provider;
MySQL is authoritative for account state, role, location scope, and audit.

```
accounts  (1) ──────── (1)  account_profiles
    │
    │ (1) ──────── (0..*)  account_location_assignments
    │
    │ (1) ──────── (0..1)  internal_account_provisionings
    │
    │ (1..*)  account_audit_events  (actor / target — both reference accounts.id)
```

All timestamps are stored in UTC (`DATETIME(6)` in MySQL 8.4).
All string comparisons use the table's declared collation unless noted.

---

## 2. Table Definitions

### 2.1 `accounts`

Primary identity and authorization record. One row per Firebase UID.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | `BIGINT UNSIGNED` | NO | auto-increment | Surrogate PK; used as FK in all child tables |
| `firebase_uid` | `VARCHAR(128)` | NO | — | Firebase UID; UNIQUE; never updated; case-sensitive |
| `login_email` | `VARCHAR(320)` | YES | NULL | Last email confirmed from a verified Firebase ID token; updated only via `POST /accounts/me/email-sync`; never set from browser body |
| `role` | `ENUM('MEMBER','STAFF','MANAGER','ADMIN')` | NO | `'MEMBER'` | Closed vocabulary; never infer from provider claim or display label |
| `status` | `ENUM('ACTIVE','PENDING_SETUP','SUSPENDED')` | NO | `'ACTIVE'` | Email verification is a Firebase-backed condition, not a fourth status |
| `version` | `BIGINT` | NO | `0` | Optimistic-lock counter; increment on every Admin write; checked on Admin profile/role-location/suspend/restore |
| `session_revocation_confirmed_at` | `DATETIME(6)` | YES | NULL | Set after `revokeRefreshTokens` completes without error; NULL means revocation not yet confirmed; restoration is blocked while NULL |
| `created_at` | `DATETIME(6)` | NO | `CURRENT_TIMESTAMP(6)` | |
| `updated_at` | `DATETIME(6)` | NO | `CURRENT_TIMESTAMP(6)` ON UPDATE | |

**Constraints and indexes**:

```sql
PRIMARY KEY (id),
UNIQUE KEY uq_accounts_firebase_uid (firebase_uid),
KEY idx_accounts_email (login_email),
KEY idx_accounts_role_status (role, status),
KEY idx_accounts_status (status)
```

**Invariants**:
- `firebase_uid` is never updated after insert.
- `login_email` is updated only by the backend reading a verified Firebase ID token; never from a client-supplied body field.
- `role = 'MEMBER'` → `status` must be `'ACTIVE'` (Members are never `PENDING_SETUP` or `SUSPENDED` from this feature; suspension applies to internal roles and admins as per Admin action).
- When `status` transitions to `'SUSPENDED'`, `session_revocation_confirmed_at` is set to NULL to reset the gate.
- Restoration from `'SUSPENDED'` is only permitted when `session_revocation_confirmed_at IS NOT NULL`.

---

### 2.2 `account_profiles`

Name and contact snapshot. One-to-one with `accounts`.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `account_id` | `BIGINT UNSIGNED` | NO | — | PK and FK → `accounts.id`; no cascade delete |
| `full_name` | `VARCHAR(400)` | NO | — | 400 bytes reserves ≥ 100 Unicode code points at 4 bytes/code point; application enforces exactly 100-code-point maximum |
| `phone` | `VARCHAR(30)` | YES | NULL | Stored as-is after trim; 7–30 character application validation; not normalized, not OTP-verified, not required to be unique |
| `updated_at` | `DATETIME(6)` | NO | `CURRENT_TIMESTAMP(6)` ON UPDATE | |

**Constraints and indexes**:

```sql
PRIMARY KEY (account_id),
CONSTRAINT fk_account_profiles_account FOREIGN KEY (account_id) REFERENCES accounts (id)
```

**Invariants**:
- Row is created on first valid profile save; absence renders as null fields in API response.
- Pessimistic write lock (`SELECT … FOR UPDATE`) on `account_id` serializes concurrent creation/update.
- `GET` never creates or modifies a profile row.
- Own-profile writes (self-service) follow last-successful-commit-wins (no optimistic lock).
- Admin profile writes require `accounts.version` optimistic-lock check.

---

### 2.3 `account_location_assignments`

Records which workshop locations a Staff or Manager account is assigned to.
Location existence and active status are validated against the FE-03 facade at write time;
no FK to a location table is defined here (module data-ownership boundary).

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | `BIGINT UNSIGNED` | NO | auto-increment | Surrogate PK |
| `account_id` | `BIGINT UNSIGNED` | NO | — | FK → `accounts.id` |
| `location_id` | `BIGINT UNSIGNED` | NO | — | Reference only — **no FK** to workshop-location table (owned by FE-03) |
| `assigned_at` | `DATETIME(6)` | NO | `CURRENT_TIMESTAMP(6)` | Set at assignment time; never updated |
| `active` | `TINYINT(1)` | NO | `1` | 1 = active assignment; 0 = deactivated; used to distinguish historical records |

**Constraints and indexes**:

```sql
PRIMARY KEY (id),
CONSTRAINT fk_ala_account FOREIGN KEY (account_id) REFERENCES accounts (id),
UNIQUE KEY uq_ala_account_location_active (account_id, location_id, active),
KEY idx_ala_account_active (account_id, active)
```

**Invariants**:
- `STAFF` accounts have **exactly one** active assignment (`active = 1`) at any time.
- `MANAGER` accounts have **one or more** active assignments.
- `MEMBER` and `ADMIN` accounts have no location assignments.
- When a new assignment is created, any previous active assignment for the same `account_id` (and same `location_id`) must be deactivated in the same transaction.
- Deactivating a `workshop_location` in FE-03 does **not** automatically deactivate assignments here; it only causes denial of commands scoped to that location.
- Validation of `location_id` against FE-03 facade occurs at write time. If the FE-03 facade is absent (stub), the write is blocked with a clear error.

---

### 2.4 `internal_account_provisionings`

Tracks the lifecycle of Admin-created (internal) accounts from invitation through activation.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | `BIGINT UNSIGNED` | NO | auto-increment | Surrogate PK |
| `account_id` | `BIGINT UNSIGNED` | NO | — | FK → `accounts.id` |
| `idempotency_key` | `VARCHAR(128)` | NO | — | UNIQUE; client-supplied UUID per create intent; prevents duplicate provisioning |
| `status` | `ENUM('INVITATION_SENT','SETUP_COMPLETE','INVITATION_FAILED')` | NO | `'INVITATION_SENT'` | |
| `invitation_last_sent_at` | `DATETIME(6)` | YES | NULL | Updated on initial send and each resend; used to enforce 5-minute resend cooldown |
| `created_by_admin_id` | `BIGINT UNSIGNED` | NO | — | FK → `accounts.id`; the Admin who created the provisioning |
| `firebase_setup_link_issued_at` | `DATETIME(6)` | YES | NULL | Timestamp when a Firebase action link was most recently generated; the raw link is **never stored** |
| `created_at` | `DATETIME(6)` | NO | `CURRENT_TIMESTAMP(6)` | |

**Constraints and indexes**:

```sql
PRIMARY KEY (id),
UNIQUE KEY uq_iap_idempotency_key (idempotency_key),
CONSTRAINT fk_iap_account FOREIGN KEY (account_id) REFERENCES accounts (id),
CONSTRAINT fk_iap_admin FOREIGN KEY (created_by_admin_id) REFERENCES accounts (id),
KEY idx_iap_account (account_id)
```

**Invariants**:
- `idempotency_key` is never updated after insert.
- A request with a duplicate `idempotency_key` and **identical** payload returns the existing record (`200`).
- A request with a duplicate `idempotency_key` and a **different** payload returns `409 IDEMPOTENCY_KEY_CONFLICT`.
- The Firebase action link is regenerated for each resend call; it is **never written to this table or to audit records**.
- Resend is blocked for 5 minutes after `invitation_last_sent_at`; enforced server-side.
- TTL for purging old keys: **[G01 TBD — Product decision]**. Suggested minimum: 30 days. Until decided, no automatic purge runs.

---

### 2.5 `account_audit_events`

Immutable audit trail for all Admin-driven and security-significant Account mutations.
Retained for **5 years** (SC-005 / FR-023).

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | `BIGINT UNSIGNED` | NO | auto-increment | Surrogate PK |
| `actor_account_id` | `BIGINT UNSIGNED` | YES | NULL | The account that performed the action; NULL for system-generated events |
| `target_account_id` | `BIGINT UNSIGNED` | YES | NULL | The account affected; NULL for non-account-scoped events |
| `action` | `VARCHAR(80)` | NO | — | Enum-like string constant, e.g. `ACCOUNT_CREATED`, `ROLE_CHANGED`, `STATUS_SUSPENDED`, `STATUS_RESTORED`, `LOCATION_ASSIGNED`, `PROFILE_UPDATED_ADMIN`, `INVITATION_RESENT`, `SESSION_REVOKED` |
| `outcome` | `ENUM('SUCCESS','FAILURE')` | NO | — | |
| `correlation_id` | `VARCHAR(64)` | NO | — | Request trace ID propagated from the inbound request header or generated if absent |
| `occurred_at` | `DATETIME(6)` | NO | `CURRENT_TIMESTAMP(6)` | |
| `metadata` | `JSON` | YES | NULL | Before/after values for role, status, or location assignment **only**. MUST NOT contain: full name, phone, login_email, firebase_uid, raw tokens, Firebase action links, passwords, or any unrelated PII. |

**Constraints and indexes**:

```sql
PRIMARY KEY (id),
KEY idx_aae_target (target_account_id, occurred_at),
KEY idx_aae_actor (actor_account_id, occurred_at),
KEY idx_aae_occurred_at (occurred_at)
-- No FK constraints on actor/target (audit records must survive account deletion or future tombstoning)
```

**Invariants**:
- Rows are **insert-only**; no UPDATE or DELETE in application code.
- `metadata` JSON schema is strictly controlled: only keys `previous_role`, `new_role`, `previous_status`, `new_status`, `assigned_location_ids`, `removed_location_ids`, `lock_reason` are permitted.
- 5-year retention: no automatic purge in V1; purge mechanism is a post-V1 operational task.

---

## 3. Flyway Migration

**File**: `backend/src/main/resources/db/migration/V5__account_management.sql`

- Appended to the existing migration chain; V1–V4 checksums must remain byte-for-byte unchanged.
- Creates tables: `accounts`, `account_profiles`, `account_location_assignments`, `internal_account_provisionings`, `account_audit_events`.
- **[G05 TBD]**: Relationship between `accounts` and the existing `members` table must be resolved by Product + Tech Lead before this migration is written. Until resolved, `V5__account_management.sql` is a placeholder only. Do not drop, rename, or migrate data from `members` without an explicit approved decision.

---

## 4. Enum Values Reference

### `AccountRole`

| Code | Display | Notes |
|------|---------|-------|
| `MEMBER` | Member (customer) | Default for self-registered accounts |
| `STAFF` | Staff | Internal; created by Admin; must have exactly one active location assignment |
| `MANAGER` | Manager | Internal; created by Admin; must have one or more active location assignments |
| `ADMIN` | Admin | Internal; created by Admin; no location assignment required |

### `AccountStatus`

| Code | Notes |
|------|-------|
| `ACTIVE` | Normal operational state |
| `PENDING_SETUP` | Internal account created by Admin; Firebase UID not yet linked; awaiting invitation completion |
| `SUSPENDED` | Account locked by Admin; Firebase session revocation queued/confirmed |

### `InternalProvisioningStatus`

| Code | Notes |
|------|-------|
| `INVITATION_SENT` | Invitation email dispatched; Firebase action link generated |
| `SETUP_COMPLETE` | Invitee has signed in with verified email and valid location; account transitioned to `ACTIVE` |
| `INVITATION_FAILED` | FE-07 notification delivery failed; Admin must resend |
