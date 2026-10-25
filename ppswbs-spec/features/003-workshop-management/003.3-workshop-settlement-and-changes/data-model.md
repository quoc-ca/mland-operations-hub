# Data Model: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-10-25
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

> Conceptual ownership/invariants only: not approved table, field, enum, migration,
> DTO or public contract design.

## Ownership map

| Information | Owner | 003.3 constraint |
| --- | --- | --- |
| Booking, frozen package price, deposit | 003.1 | Projection only. |
| Sessions, capacity, ticket entitlement, attendance | 003.2 | Facade effects only. |
| Account/capability/branch scope | FE-001 | Current authorization truth. |
| Bill, charge, settlement/refund/change case, audit | 003.3 | Authoritative owner. |

## Conceptual records

### Final booking bill

Exactly one final bill belongs to one direct booking/package. It retains frozen
package price, accepted deposit credit, accepted additional charges, amount due,
settlement outcome and redacted audit linkage. It is never split by session,
participant, ticket or legacy item.

### Additional charge and correction

An accepted charge keeps its basis, amount/currency, confirmation/assessment
evidence and audit linkage. Customer selection must be a permitted fixed-price
package/branch catalogue choice. Variable material/service charge needs
Staff/Manager confirmation. A correction appends audited void/adjustment evidence;
it does not overwrite history.

### Payment evidence and deferred case

Payment evidence has only safe references/outcomes sufficient for idempotency and
reconciliation. Success requires signed matched VNPay IPN/QueryDR. Replayed,
invalid, mismatched, browser-return-only or late results cannot settle again.

A deferred case records authorized exception, expiry/reminder state, reason,
actor/audit and safe provider reference. It creates no default credit/release;
reissue after expiry is a new authorized action.

### Required-session change case

Retains booking reference, complete prior/requested sets, reason, authorization,
correlation and Operations outcome. It owns neither capacity, attendance,
entitlement nor QR. It succeeds only when 003.2 reports the whole set applied.

### Refund case and policy snapshot

Whole booking/package only: reason, applicable snapshot, requested/verified
amount, provider reference, outcome and audit linkage. Mland-cancellation uses
the confirmation-time snapshot. Only verified success permits Operations to revoke
unused tickets/release future capacity; failed refund never restores use.

### Business audit evidence

Immutable redacted evidence holds actor reference when applicable, booking,
branch, action, outcome, required reason, correlation and time. Exclude raw
provider/Firebase secrets or tokens, card data, OTP and unnecessary PII.

## Integrity rules

1. At most one final bill per booking/package in V1.
2. A session change never changes frozen package price.
3. Valid matched VNPay payment settles a final bill exactly once.
4. Charges create no participant, ticket, capacity seat or booking.
5. Operations replacement is atomic; 003.3 records no partial outcome.
6. No V1 ticket/participant/session-scoped customer refund.
7. Policy revisions are prospective; confirmed bookings retain their snapshot.
