# Account Management Data Model

**Date created**: 2026-10-26
**Date updated**: 2026-10-26
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Status**: Proposed physical design; no migration is created by this document.

## Ownership and entities

| Entity | Owner | Key fields and invariants |
| --- | --- | --- |
| Account | `accountmanagement` | `id`, immutable unique `firebase_uid`, current `login_email`, `email_verified_at` nullable, role, status, `resume_status` nullable, `revocation_pending`, `version`, timestamps. Exactly one role; UID is never merged by email. |
| AccountProfile | `accountmanagement` | PK/FK `account_id`; nullable stored `full_name`, `phone`, timestamps. Name is required only on successful profile save; phone is optional/non-unique. |
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
- Admin mutations use `version` and return conflict on stale write. Own profile
  save is last-successful-commit wins.
- Provisioning records persist enough non-secret progress to reconcile Firebase
  partial failure. Firebase create/revoke/action-link calls are outside the DB
  transaction and are idempotently retried/reconciled.
- MySQL migration version is the next reserved integer after currently merged
  migrations; it must have empty and supported-upgrade verification.

## Public boundary types

`CurrentAccountActor` is a facade boundary record, not an entity or HTTP DTO:
`accountId`, fixed `role`, `status`, `emailVerified`, and `activeLocationIds`.
It intentionally excludes Firebase UID, email, token, audit, and provisioning
details. Consumers map this context to their own action rules.
