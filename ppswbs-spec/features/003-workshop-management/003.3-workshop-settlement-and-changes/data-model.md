# Data Model: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-10-27
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

> Conceptual ownership and invariants only. This is not an approved database
> schema, Java DTO set, migration, field type, index, or locking design.

## Ownership Map

| Information | Owner | 003.3 constraint |
| --- | --- | --- |
| Confirmed booking, frozen financial projection, booking contact | 003.1 | Projection/authorization proof only; 003.3 never mutates Booking directly. |
| Sessions, capacity, ticket entitlements, attendance and cancellation state | 003.2 | Semantic facade outcomes only. |
| Canonical account role/status, ownership and location scope | FE-001 | Current account facts only. Settlement applies its own action rule with booking location/resource scope. The location is the canonical `workshop_location.id`, directly comparable to `activeLocationIds`. |
| Final bill, charge, settlement evidence, case, policy snapshot and audit | 003.3 | Authoritative owner. |

## Conceptual Records

### Final Booking Bill

Exactly one final bill belongs to one direct booking/package. It retains the
booking reference; frozen post-promotion amount, accepted deposit and frozen
remaining amount supplied by 003.1; accepted charges; amount due; customer-safe
status; verified payment/refund conclusion; and redacted audit/correlation
references. It is not split by workshop session, participant, ticket or legacy
item and never recalculates voucher/point/deposit effects.

### Charge Evidence

An immutable accepted charge records its final-bill context, source category,
VND amount/currency, acceptance/confirmation state and audit linkage. A customer
charge is only an allowed package/location catalogue add-on at its server-derived
catalogue snapshot price; a variable material/service charge has internal
description, amount, reason and Staff/Manager confirmation. A correction is an
audited void/adjustment record, never an overwrite.

### Settlement Case

Every cancellation, required-session-change, deferred-payment and Mland-caused
remedy has one opaque case reference, booking reference, type, lifecycle state,
reason, safe correlation reference and redacted audit linkage. States are
`OPEN`, `RESOLVED`, `REJECTED` or `FAILED`; only a gateway-waiting refund or
deferred flow can be `PENDING_PROVIDER`, and only deferred payment can be
`EXPIRED`.

A required-session-change case retains only the complete target session-reference
set and the typed Operations outcome. An Mland-caused replacement/change and its
`UNAVAILABLE`/`DECLINED` remedy retain the same authoritative
`cancellationHandoffReference`. The case owns neither capacity, attendance,
ticket entitlement nor QR material.

### Payment Evidence and Deferred Payment

Settlement evidence retains only the safe provider transaction/reference,
matching outcome, correlation and idempotency conclusion required for
reconciliation. Signed, matched VNPay IPN/QueryDR is required; invalid,
mismatched, replayed, browser-return-only or late evidence cannot settle twice.
No raw signed payload, signature, secret, card data or browser token is retained.

A deferred-payment case retains authorized reason, fixed 24-hour expiry, at-most-
one reminder state, safe provider reference and audit. It creates no default
credit/release; reissue after expiry is a new authorized action.

### Settlement Notification Handoff

An authorized deferred-payment-link issue/reissue, its permitted reminder, and
each customer-facing verified settlement/refund outcome create one immutable
notification handoff in the same local transaction as that accepted Settlement
outcome. Its post-commit event carries references only. Feature 007 reads the
single immutable render-ready delivery snapshot through the Settlement facade
and owns provider attempts, retries and delivery logs. Recipient/contact data,
payment-action-link values, raw provider evidence and ticket/QR material do not
appear in the event or business audit. Delivery failure cannot reverse the
Settlement outcome.

### Refund Policy Revision and Snapshot

A central policy revision has its future effective time and integral
`wholeBookingRefundPercent` in `0..100`. It applies only to bookings confirmed
after its effective time. Each confirmed booking retains an immutable applicable
policy snapshot and computes any Mland-caused refund as
`floor(collectedAmount × wholeBookingRefundPercent / 100)` whole VND. Existing
snapshots are never rewritten.

### Authorization, Audit and Idempotency Evidence

Protected command evidence records the actor reference when applicable, booking,
canonical location when applicable, action, reason, outcome, correlation reference and time.
It excludes contact proof, raw Magic Link, QR, Firebase token, provider secret,
raw callback, card data, OTP and unnecessary PII.

For each client-originated mutation, retained idempotency evidence connects an
opaque operation/key, semantic request identity, original safe outcome and
correlation reference. The same key/content returns that outcome; changed reuse
is a conflict. VNPay replay protection instead uses the verified provider
transaction/reference.

## Integrity Rules

1. At most one final bill exists for one direct booking/package.
2. Frozen financial amounts from 003.1 are never recalculated by 003.3.
3. A customer-selected charge never supplies its own price and is rejected after
   bill finalization.
4. A charge creates no participant, ticket, capacity seat or booking.
5. Valid matched VNPay evidence resolves a final bill/refund exactly once.
6. Operations replacement is complete-set and all-or-none; 003.3 persists no
   partial capacity/entitlement outcome.
7. Refunds are whole-booking/package only; no V1 ticket/participant/session
   partial refund exists.
8. A Mland-caused remedy is traceable to the same cancellation handoff as its
   replacement attempt.
9. Policy revisions are prospective and cannot rewrite a confirmed booking's
   snapshot.
10. A client idempotency key cannot produce two settlement effects or be reused
    for different semantic input.
11. A notification handoff is immutable and reference-only; duplicate delivery
    events cannot create a second delivery request or alter settlement truth.
