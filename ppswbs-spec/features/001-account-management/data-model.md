# Account Management Data Model

**Date created**: 2026-10-26
**Date updated**: 2026-11-01
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Status**: Proposed physical design; no migration is created by this document.

## Ownership and entities

| Entity | Owner | Key fields and invariants |
| --- | --- | --- |
| Account | `accountmanagement` | `id`, immutable unique `firebase_uid`, current `login_email`, `email_verified_at` nullable, role, status, `resume_status` nullable, `revocation_pending`, `version`, timestamps. Exactly one role; UID is never merged by email. |
| AccountProfile | `accountmanagement` | PK/FK `account_id`; required `full_name`, nullable `phone`, timestamps. It is created atomically with bootstrap only when an unknown verified UID has a valid identity email; initial `full_name` is that email's local part before `@`. Phone is optional/non-unique. |
| AccountLocationAssignment | `accountmanagement` | `account_id`, `location_id`, active flag/timestamps. No local location entity or FK to another module table. Staff has exactly one active assignment; Manager has one or more. |
| InternalAccountProvisioning | `accountmanagement` | Account creation request identity, idempotency key, normalized request digest, intended email/role/location snapshot, Firebase UID, Account ID, lifecycle/progress and retry diagnostics. Unique idempotency key; same key/different digest conflicts. |
| AccountAuditEvent | `accountmanagement` | actor/target Account IDs nullable, action, outcome, correlation ID, safe metadata, occurred time. Retain five years; never store password, raw token/action-link, full name, phone. |
| NotificationIntent | FE-07-owned delivery record | Account writes a referenceable intent in the same business commit for invitation, lock/unlock, or role change. No raw Firebase action link is persisted. |

## Enumerations and lifecycle

- `AccountRole`: `MEMBER`, `STAFF`, `MANAGER`, `ADMIN` only.
- `AccountStatus`: `ACTIVE`, `PENDING_SETUP`, `SUSPENDED` only.
- `resume_status` is populated only when suspending an eligible `ACTIVE` or
  `PENDING_SETUP` account and is consumed on successful restoration.
- Email verification is an identity assurance, not an Account status.
- Location deactivation leaves Account role/status unchanged but removes usable
  scope for commands in that location.

| Transition | Actor/condition | Result |
| --- | --- | --- |
| Unknown verified UID → Member | Bootstrap | Create one `MEMBER`/`ACTIVE` Account unless a UID-owned internal record exists. |
| `PENDING_SETUP` → `ACTIVE` | Intended verified identity + verified email + valid active scope | Activate internal Account and audit. |
| `ACTIVE` or `PENDING_SETUP` → `SUSPENDED` | Admin with reason | Preserve resume status; deny protected access immediately; queue revocation. |
| `SUSPENDED` → prior status | Admin, only after revocation completion | Restore prior eligible status; require fresh sign-in. |

## Constraints and consistency

- `firebase_uid` unique; account IDs are opaque positive IDs.
- Login email is a current identity snapshot, not an ownership key. A UID/email
  mismatch never causes automatic merge or role upgrade.
- Unknown-UID bootstrap creates its Account Profile in the same transaction only
  when the verified identity includes a valid email claim. The initial full name
  derives only from that email local part; browser input and provider display
  names are not an authority source. A missing-email identity creates no Account
  or Profile; an existing UID-owned Account is not overwritten from that absence.
- Admin mutations use `version` and return conflict on stale write. Own profile
  save is last-successful-commit wins.
- Provisioning records persist enough non-secret progress to reconcile Firebase
  partial failure. Firebase create/revoke/action-link calls are outside the DB
  transaction and are idempotently retried/reconciled.
- A `SUCCESS` or authorized-domain `FAILURE` audit attempt contains outcome and
  safe failure classification, never uncommitted state or sensitive detail. An
  audit persistence failure does not roll back, block, or replace the original
  Account outcome; it emits a non-sensitive operational signal keyed by the
  correlation ID. Only successfully retained audit evidence is subject to the
  five-year retention requirement.
- MySQL migration version is the next reserved integer after currently merged
  migrations; it must have empty and supported-upgrade verification.

## Audit outcome storage contract — approved baseline

`AccountAuditEvent.outcome` is a persistence contract owned by FE-001. Its
allowed business values are exactly `SUCCESS` and `FAILURE`; it is not a free
text field and is not exposed as a public API contract.

### Observed mismatch

- Applied baseline migration `V5__account_management.sql` declares
  `account_audit_events.outcome` as MySQL `ENUM('SUCCESS', 'FAILURE')`.
- Existing `AccountAuditEvent` maps the same column as Java `String`. With
  Hibernate schema validation, MySQL reports `ENUM` while the mapping expects
  `VARCHAR`, preventing `EntityManagerFactory` startup.
- This is a FE-001 persistence blocker, not a Workshop Operations business
  failure. It prevents any MySQL-dependent consumer test from starting.

### Decision record

**Approved by**: Tech Lead Phan Sơn, 2026-10-28.

The selected contract is **Option B**: define a Java `AuditOutcome` enum with
exactly `SUCCESS` and `FAILURE`, persist it as `VARCHAR(16)`, and add an
append-only Flyway migration changing the existing `outcome` column to
`VARCHAR(16) NOT NULL`. Existing values must be preserved. A Java `String`, a
MySQL `ENUM`, or another outcome value is outside this approved contract.

### Mandatory implementation guardrails after selection

1. Do not edit `V5__account_management.sql` once applied to a data-bearing
   environment. Reserve the next repository-wide Flyway integer and add an
   append-only migration that preserves existing rows.
2. `AuditService.recordEvent` must accept `AuditOutcome`;
   callers cannot persist arbitrary strings.
3. Keep `spring.jpa.hibernate.ddl-auto=validate`; never use `update`, Flyway
   ignore, Maven exclusion or a test-only schema workaround to mask mismatch.
4. Verify the selected schema/mapping on a fresh MySQL database and a supported
   V5 upgrade database before rerunning affected consumer integration tests,
   including `WorkshopOperationsMySQLIT`.

**Implementation resume condition**: regenerate the implementation plan, task
and verification artifacts with this baseline before a code agent changes the
migration, entity or service signature. Completion additionally requires fresh
MySQL fresh/upgrade schema-validation evidence.

## Public boundary types

`CurrentAccountActor` is a facade boundary record, not an entity or HTTP DTO:
`accountId`, fixed `role`, `status`, and `emailVerified`. It intentionally
excludes Firebase UID, email, token, audit, provisioning details and Workshop
Location scope, so a Workshop Location outage cannot deny scope-independent
protected actions at identity admission.

`TargetAccountFacts` is the separate typed facade outcome for a location-scoped
action. Its `FOUND` value contains effective `activeWorkshopLocationIds`: active
Account assignments intersected with currently active authoritative Workshop
Locations. `UNAVAILABLE` is returned when that effective scope cannot be
verified; only the dependent action fails closed.
