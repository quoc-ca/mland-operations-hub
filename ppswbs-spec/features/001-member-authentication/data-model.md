# Data model: Member Authentication

## As-built V1

V1__init_member_auth.sql remains immutable after it has been applied. It creates
members, policy_documents, member_policy_acceptances,
member_auth_audit_events, workshop_bookings, and
booking_email_confirmations. Existing data and foreign keys are preserved by
later append-only migrations.

| Table | Owner | Current purpose | Maturity |
| --- | --- | --- | --- |
| members | members | Firebase UID, email snapshot, verification snapshot, Member status | As-built |
| policy_documents | members | versioned Terms/Privacy metadata | As-built |
| member_policy_acceptances | members | immutable consent evidence | As-built |
| member_auth_audit_events | members | redacted auth evidence | As-built |
| workshop_bookings | workshopbooking | Guest/Member booking linkage and confirmation state | As-built |
| booking_email_confirmations | workshopbooking | one hashed token per booking | As-built |

Existing booking tables establish only persistence foundations; they do not prove
that the complete booking workflow, payment gate, or expiry process is working.

## Forward identity and entitlement model

| Table/change | Owner | Key relationships and invariants | Maturity |
| --- | --- | --- | --- |
| accounts | members | unique external_user_id; MySQL account status is authoritative | Proposed V2 |
| account_roles | members | account FK; unique account/role; grant/revoke state is auditable | Proposed V2 |
| members.account_id | members | nullable compatibility FK to accounts, then unique after backfill validation | Proposed V2 |
| policy_documents.effective_policy_type | members | stored generated value: policy_type only when state is EFFECTIVE, otherwise NULL; unique index enforces one current policy per type | Proposed V2 |

Backfill creates one account for every existing Member without deriving identity
from email. External_user_id remains during the compatibility period. A Member
entitlement requires a verified Firebase UID mapped to an active account with
active MEMBER role and acceptance records for both effective policies.

## Booking-related invariants

- A booking has at most one Member linkage; Guest records may remain unlinked.
- A confirmation record is unique per booking and stores only token_hash.
- Token state is PENDING, CONFIRMED, or EXPIRED; booking confirmation state is
  EMAIL_CONFIRMATION_PENDING, CONFIRMED, or EXPIRED/CANCELLED as owned by
  workshopbooking.
- Import selection requires a verified matching email, explicit Member
  confirmation, and current entitlement. Import is idempotent.
- Expiry and confirmation transitions are transactional and idempotent. The
  booking owner defines capacity and invoice release details before implementing
  their integration.

## Audit and sensitive data

Member and booking audit records are append-only, include correlation_id, and
record only redacted metadata. Raw passwords, Firebase tokens, provider codes,
plaintext confirmation tokens, and avoidable PII are forbidden.

## Migration order and compatibility tests

1. Run V1 unchanged on empty MySQL 9.7.2.
2. Apply the accounts/roles/policy-effective-key migration to a V1 database.
3. Verify every pre-existing Member is represented by one account and no booking
   or consent relationship changes.
4. Switch authorization reads to accounts/account_roles only after backfill
   checks pass.
5. Verify duplicate EFFECTIVE policies of the same type are rejected while
   archived/draft versions remain allowed.
