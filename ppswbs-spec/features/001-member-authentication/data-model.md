# Data Model: Member Authentication

Firebase owns credentials, password hashes, provider linkage, action codes, and browser persistence. MySQL owns all Mland entitlement and audit data.

| Table | Core fields | Invariants |
|---|---|---|
| `members` | `id`, `external_user_id`, `email`, `email_verified`, `status`, timestamps | UID unique/immutable; status is `PENDING_POLICY_ACCEPTANCE`, `ACTIVE`, or `SUSPENDED`; email is never merge evidence. |
| `policy_documents` | `id`, `policy_type`, `version`, `content_uri`, `effective_at`, `state` | One effective document per type; future governance owns publication. |
| `member_policy_acceptances` | `id`, `member_id`, `policy_document_id`, `accepted_at`, `locale`, `correlation_id` | Immutable; unique `(member_id, policy_document_id)`. Current entitlement needs acceptance of both effective policies. |
| `member_auth_audit_events` | `id`, nullable `member_id`, `event_type`, `occurred_at`, `correlation_id`, `provider`, `safe_metadata` | Append-only; excludes raw token/code/password/full email. |
| `workshop_bookings` (extended) | nullable `member_id`, canonical contact email, confirmation state/expiry, cancellation fields | At most one Member; linkage changes the existing row only. |
| `booking_email_confirmations` | `id`, `booking_id`, `token_hash`, `expires_at`, `confirmed_at`, `state` | One per booking; hash only; `PENDING`, `CONFIRMED`, `EXPIRED`. |

```text
Firebase uid → members → member_policy_acceptances → policy_documents
                  ├──→ member_auth_audit_events
                  └──→ workshop_bookings → booking_email_confirmations
```

## State rules

```text
verified UID → PENDING_POLICY_ACCEPTANCE → ACTIVE
ACTIVE + newer policy → POLICY_ACCEPTANCE_REQUIRED entitlement gate
any state + suspension → SUSPENDED

Guest booking → EMAIL_CONFIRMATION_PENDING
  → EMAIL_CONFIRMED → ordinary invoice/payment flow
  → after 15 min → CANCELLED_EMAIL_UNCONFIRMED + capacity released
```

Import eligibility is evaluated in one transaction: valid non-suspended/currently-consented Member, Firebase-verified Member email, matching canonical email, confirmed booking email, explicit user confirmation, and no different `member_id`. Repeated imports yield zero new links.

## Migration order

1. Create Member, policy, consent, and audit tables/indexes.
2. Require approved effective policy data at readiness; do not grant access without it.
3. Add nullable booking linkage and confirmation structures.
4. Add unique UID/booking-confirmation indexes plus email-confirmation import lookup and audit correlation indexes.
5. Deploy code after migrations. Never auto-backfill email associations.

