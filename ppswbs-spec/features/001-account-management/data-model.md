# Account Management Data Model

**Date created**: 2026-10-26
**Date updated**: 2026-11-24
**Author**: Phan Sơn
**Last updated by**: Phan Sơn

**Status**: Approved physical-design baseline through Revision R-NAD (2026-11-16) and Revision R-CAR (2026-11-20). R-INV scoped design was approved on 2026-11-25; its migrations are not yet applied. T117 retirement schema remains deferred.

**R-INV model under review:** Retained/retired MEMBER Account IDs remain stable for all commerce, workshop and audit references; retire/de-identify the login identity without cascading business rows. Add a unique, versioned invitation record with digest-only secret, key version, `createdAt`, `expiresAt = createdAt + 48 hours`, use state and safe delivery reference; a resend invalidates the prior version and starts a new window at creation. Provisioning holds real Firebase UID, normalized full request digest and resumable provider/DB/mail stages. Retirement holds target/version, actor, correlation and resumable revocation/provider/database stages without copying JSON export or sensitive history. Existing synthetic pending UIDs require explicit quarantine/reconciliation, not email-based backfill. FE-001 owns a durable invitation outbox event; FE-007 consumes it after commit. See [R-INV](design/revision-2026-11-24.md); use new append-only migrations in T106 after approval.

The deferred T117 retirement command requires definitive nonterminal-obligation outcomes from FE-008 and FE-003.1 and an approved acceptance/retirement fence before its first irreversible stage; a populated server retail cart alone is nonblocking. A fresh read alone does not close the race with a new accepted order or booking. Local mock `DEMO/UNAVAILABLE` data never permits retirement or email release. The browser-only workshop cart has no server entity to migrate or export. Historical orders/bookings continue to reference the retired Account ID.

## Ownership and entities

| Entity | Owner | Key fields and invariants |
| --- | --- | --- |
| Account | `accountmanagement` | `id`, unique immutable `firebase_uid`, current immutable CARRIER `login_email`, `email_verified_at` nullable, role, status, `resume_status` nullable, `revocation_pending`, `version`, timestamps. Exactly one role; the Firebase identity (including a CARRIER email/password identity) exists before Account commit; UID is never merged by email. |
| AccountProfile | `accountmanagement` | PK/FK `account_id`; required `full_name`, nullable `phone`, timestamps. It is created atomically with bootstrap only when an unknown verified UID has a valid identity email; initial `full_name` is that email's local part before `@`. Phone is optional/non-unique. |
| AccountLocationAssignment | `accountmanagement` | `account_id`, `location_id`, active flag/timestamps. No local location entity or FK to another module table. Staff has exactly one active assignment, which defines Staff location scope. Manager has one or more organizational assignments; they do not narrow Manager-authorized actions. |
| InternalAccountProvisioning | `accountmanagement` | Account creation request identity, idempotency key, normalized request digest, intended email/role/location snapshot, Firebase UID, Account ID, lifecycle/progress and retry diagnostics. Unique idempotency key; same key/different digest conflicts. |
| AccountAuditEvent | `accountmanagement` | actor/target Account IDs nullable, action, outcome, correlation ID, safe metadata, occurred time. Retain five years; never store password, raw token/action-link, full name, phone. |
| NotificationIntent | FE-07-owned delivery record | Account writes a referenceable intent in the same business commit for invitation, lock/unlock, or role change. No raw Firebase action link is persisted. |
| AdministrativeDirectoryVersion | `accountmanagement` | Versioned reference-data set: `version` (unique), `source_url`, `effective_from`, `status` (`DRAFT`, `ACTIVE`, `ARCHIVED`), creator/time. Exactly one `ACTIVE` version. An active version is immutable; Admin creates a new draft by copying it. Active version `VN-2026-V1` provides complete baseline reference data for all 63 provinces and ~10,500 nationwide communes/special zones. |
| AdministrativeProvince | `accountmanagement` | Directory-version-scoped official `code` and resolved `name`. Code is unique within its directory version. Covers all 63 provinces/centrally-run cities of Vietnam in `VN-2026-V1`. |
| AdministrativeCommune | `accountmanagement` | Directory-version-scoped official `code`, `name`, `type` (`WARD`, `COMMUNE`, `SPECIAL_ZONE`) and parent province. Commune code is unique within its directory version and must have a parent province in the same version. Covers ~10,500 units in `VN-2026-V1` including island/special zones (Hoàng Sa, Trường Sa, Phú Quốc, Côn Đảo, Lý Sơn, Cô Tô...). |
| MemberAddress | `accountmanagement` | `id`, FK `account_id`, recipient contact, `province_code`/resolved `province_name`, `commune_code`/resolved `commune_name`/`commune_type`, `detail_address`, `directory_version`, `address_type` (`HOME`/`OFFICE`), `is_default`, optimistic-lock `version`, timestamps. Hard delete; maximum 10 addresses per account; exactly one default address if address count > 0. |

## Enumerations and lifecycle

- `AccountRole`: `MEMBER`, `STAFF`, `MANAGER`, `CARRIER`, `ADMIN` only. CARRIER has no AccountLocationAssignment and cannot transition to/from another role.
- `AccountStatus`: `ACTIVE`, `PENDING_SETUP`, `SUSPENDED` only.
- `resume_status` is populated only when suspending an eligible `ACTIVE` or
  `PENDING_SETUP` account and is consumed on successful restoration.
- Email verification is an identity assurance, not an Account status. `email_verified_at` is an Account-owned observation snapshot written only after FE-001 verifies a fresh Firebase ID token; it is not permission authority and is never set from a browser boolean or email action code.
- Location deactivation leaves Account role/status unchanged but removes usable
  scope for commands in that location.

| Transition | Actor/condition | Result |
| --- | --- | --- |
| Unknown verified UID → Member | Bootstrap | Create one `MEMBER`/`ACTIVE` Account unless a UID-owned internal record exists. |
| `PENDING_SETUP` → `ACTIVE` | Intended verified identity + verified email + valid active scope | Activate internal Account and audit. |
| New CARRIER → `ACTIVE` | Admin creates valid immutable email, password, display name, phone and no location | Provision Firebase email/password identity, then persist CARRIER immediately. No invitation, Google binding, or enrollment record exists. |
| `ACTIVE` or `PENDING_SETUP` → `SUSPENDED` | Admin with reason | Preserve resume status; deny protected access immediately; queue revocation. |
| `SUSPENDED` → prior status | Admin, only after revocation completion | Restore prior eligible status; require fresh sign-in. |

## Constraints and consistency

- `firebase_uid` unique; account IDs are opaque positive IDs.
- Login email is a current identity snapshot, not an ownership key. A UID/email
  mismatch never causes automatic merge or role upgrade.
- A verified-email synchronization is idempotent: for the same UID and current
  verified identity it may update `login_email` and records the observation time;
  it cannot alter role, Account status, location scope or identity ownership.
- `localStorage` is not Account data. The only permitted verification-resend
  browser state is an opaque-Account-ID-keyed expiry timestamp lasting 90
  seconds; it stores no email, Firebase UID, token, action code, or proposed
  email-change value. A proposed email remains only in Firebase's verified-
  before-update flow and is never a pending Account attribute.
- Unknown-UID bootstrap creates its Account Profile in the same transaction only
  when the verified identity includes a valid email claim. The initial full name
  derives only from that email local part; browser input and provider display
  names are not an authority source. A missing-email identity creates no Account
  or Profile; an existing UID-owned Account is not overwritten from that absence.
- Admin mutations use `version` and return conflict on stale write. Own profile
  save is last-successful-commit wins. Member address update, delete and default
  mutations also use address `version`; address-book mutations serialize by
  account to preserve the quota and single-default invariant.
- Provisioning records persist enough non-secret progress to reconcile Firebase
  partial failure. Firebase create/revoke/action-link calls are outside the DB
  transaction and are idempotently retried/reconciled.
- A CARRIER creation password is sent only to Firebase as a write-only input. It
  is excluded from Account/Profile/provisioning records, audit events, logs,
  traces, error bodies, and all API responses.
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

`MemberAddressSnapshot` is an immutable facade boundary record for a future approved
consumer: `id`, `recipientName`, `phoneNumber`, `provinceCode`, `provinceName`,
`communeCode`, `communeName`, `communeType`, `detailAddress`, `directoryVersion`,
`addressType`, and address `version`. Consumers copy the snapshot into their own
persistence so later Account Management changes never alter historical records.

## Address-directory migration decision

This development-stage revision may reset mock `member_addresses` rows. The next
reserved Flyway migration replaces the legacy province/district/ward/hamlet shape
with the two-level province/commune shape, adds `directory_version` and the
optimistic-lock version, and creates the directory-version/province/commune tables.
It must affect only FE-001-owned address/reference-data tables, never reset Account,
cart, checkout, order or another feature's records. Production data-preservation
policy is not selected by this development-only reset decision.

## Timezone storage contract — approved baseline

All database date-time values are standardized on Vietnam local time (`Asia/Ho_Chi_Minh`, UTC+7).
- JDBC URL enforces `connectionTimeZone=Asia/Ho_Chi_Minh&forceConnectionTimeZoneToSession=true`.
- Application JVM default timezone is configured to `Asia/Ho_Chi_Minh`.
- Jackson serializers output ISO-8601 with `+07:00` offset.
- MySQL `DATETIME(6)` columns store wall-clock time in UTC+7 directly.
