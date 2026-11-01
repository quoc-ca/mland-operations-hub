# Data Model: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-10-31
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

> Business ownership and invariants are normative. The approved cancellation
> persistence is recorded below; unrelated conceptual records do not define
> unlisted physical schema or locking behavior.

## Ownership Map

| Information | Owner | 003.3 constraint |
| --- | --- | --- |
| Confirmed booking, frozen financial projection, booking contact | 003.1 | Projection/authorization proof only; 003.3 never mutates Booking directly. |
| Sessions, capacity, ticket entitlements, attendance and cancellation state | 003.2 | Semantic facade outcomes only. |
| Canonical account role/status, ownership and location scope | FE-001 | Current account facts only. Settlement grants active Managers global authority for Manager actions; Staff actions compare the booking/session canonical `workshop_location.id` directly with `activeLocationIds`. |
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
catalogue snapshot price returned by 003.2 Operations for the exact published
package and active canonical location; Settlement retains the selected
description, whole-VND amount and currency as immutable charge evidence. A
variable material/service charge has internal description, amount, reason and
Staff/Manager confirmation. A correction is an audited void/adjustment record,
never an overwrite. Legacy charges created before description retention may
have no customer-visible description.

### Settlement Case

Every cancellation, required-session-change and Mland-caused
remedy has one opaque case reference, booking reference, type, lifecycle state,
reason, safe correlation reference and redacted audit linkage. States are
`OPEN`, `RESOLVED`, `REJECTED` or `FAILED`; only a gateway-waiting refund can be
`PENDING_PROVIDER`. There is no deferred-payment case or `EXPIRED` case state.

A customer cancellation has at most one case per booking, including when the
case is rejected. Its dedicated snapshot is unique by booking and links to the
generic case by unique case reference. A new idempotency key cannot open a second
case or refund; same-key replay returns the retained safe outcome.

A required-session-change case retains only the complete target session-reference
set and the typed Operations outcome. An Mland-caused replacement/change and its
`UNAVAILABLE`/`DECLINED` remedy retain the same authoritative
`cancellationHandoffReference`. The case owns neither capacity, attendance,
ticket entitlement nor QR material.

### Payment Evidence and Output Handover

Settlement evidence retains only the safe provider transaction/reference,
matching outcome, correlation and idempotency conclusion required for
reconciliation. Signed, matched VNPay IPN/QueryDR is required; invalid,
mismatched, replayed, browser-return-only or late evidence cannot settle twice.
No raw signed payload, signature, secret, card data or browser token is retained.
Verified final-payment evidence also retains the provider transaction number and
the immutable PAY URL creation timestamp needed to identify/refund that exact
original payment. Booking supplies verified deposits through its named facade;
Settlement does not reach into Booking storage for payment rows.

Each refund allocation is an immutable child operation of its settlement case
and identifies one original payment, provider transaction number, original
provider create date, currency, allocated amount, operation reference and
provider lifecycle (`PREPARED`, `ACCEPTED`, `UNKNOWN`, `VERIFIED`, or
`REJECTED`). The operation is persisted before a provider call. `ACCEPTED` and
`UNKNOWN` do not resolve the case. Retries reuse the same operation reference;
an unknown provider outcome is reconciled before any new call. A case resolves
only when every nonzero child operation has verified refund evidence.

For VNPay, a refund response with `vnp_ResponseCode=00` is request acceptance
only. The provider refund transaction number returned by that signed response
identifies the refund allocation for QueryDR reconciliation. A refund is
`VERIFIED` only from a signed QueryDR response with API `vnp_ResponseCode=00`,
matching merchant reference and refund transaction number, matching refund
transaction type (`02` full or `03` partial), exact allocated amount, and
`vnp_TransactionStatus=00`. Transaction statuses `05` and `06` remain pending;
`09` is rejected; missing, mismatched, or otherwise nonterminal evidence remains
unknown/pending and cannot trigger ticket/capacity consequences. After a refund
request is accepted with its provider transaction number, Settlement queries
immediately and polls QueryDR every five minutes for at most 24 hours. `05` and
`06` stay pending; no refund request is resent. An `UNKNOWN` request with no
provider refund transaction number stays `PENDING_PROVIDER` for manual VNPay
Merchant Admin investigation. Manual investigation or a screenshot is not
verified payment evidence and cannot resolve the case; only signed matching
QueryDR evidence can do so.

The append-only cancellation migration adds
`settlement_customer_cancellation_snapshots` and
`settlement_refund_allocations`. The snapshot retains an opaque snapshot
reference, unique case reference, unique booking reference, policy revision and
percent, earliest session reference/start, verified paid-deposit total,
calculated refund total, VND currency, request time and eligibility outcome.
The allocation table retains an opaque allocation reference, case reference,
original merchant reference and provider transaction facts, original create
date, original and allocated whole-VND amounts, currency, stable provider
operation reference, optional refund transaction number, provider lifecycle and
redacted response/correlation facts. It has a unique key on case plus original
merchant reference and a unique provider operation reference. Payment
integration's operation journal is the provider-call idempotency boundary. An
allocation is committed before dispatch; provider calls occur after the local
transaction; `UNKNOWN` is reconciled and never automatically resent.

The final bill remains unpaid until signed, matching VNPay evidence confirms the
complete remaining balance. No role can waive collection, defer the balance,
create a payment reminder, or reissue a deferred-payment link. Package output is
withheld until the balance is verified as paid. The final-payment checkout
uses one immutable `FINAL_PAYMENT` owner per attempt in `paymentintegration`.
Settlement requests the exact server-derived outstanding amount in VND with an
idempotency reference; each provider URL expires 10 minutes after creation. A
pending or unknown attempt blocks another attempt. After expiry, a new attempt
is allowed only after QueryDR confirms that the previous attempt did not
succeed. A URL expiry alone never proves non-payment; late signed success and
ambiguous outcomes remain subject to verified ingress/QueryDR evidence. This is
a collection step during settlement, not a credit period.

### Settlement Notification Handoff

Each customer-facing verified settlement/refund outcome may create one durable
notification handoff in the same local transaction as that accepted Settlement
outcome. Its post-commit event carries references only. Feature 007 reads the
render-ready delivery snapshot through the Settlement facade and owns provider
attempts, retries and delivery logs. The stored recipient and rendered content
are retained for at most 30 days after handoff. Payment-action links are not
included. The delivery snapshot is immutable until its 30-day retention ends.
Raw provider evidence and ticket/QR material
do not appear in the event or business audit. Delivery failure cannot reverse
the Settlement outcome.

The physical owner table is `settlement_notification_handoffs`: a unique
`notification_reference`, settlement and correlation references, notification
kind, recipient, rendered content, payload expiry, creation time, and nullable
event-dispatch time. Existing nullable action-link columns from the initial
append-only migration are legacy and are no longer mapped, written, or exposed.
Indexes support reference lookup, expiry cleanup, and retrying undispatched
reference events. Recipient/content are readable only through the named
Settlement delivery payload facade; the event dispatcher emits references only.

### Refund Policy Revision and Snapshot

A central policy revision has its future effective time and integral
`wholeBookingRefundPercent` in `0..100`. It applies only to bookings confirmed
after its effective time. Each confirmed booking retains an immutable applicable
policy snapshot and computes any Mland-caused refund as
`floor(collectedAmount × wholeBookingRefundPercent / 100)` whole VND. Existing
snapshots are never rewritten.

This policy is not consulted for ordinary customer cancellation. A direct
customer cancellation has a zero-refund snapshot, is handed to Operations for
whole-booking cancellation, and retains the deposit; no refund allocation or
VNPay QueryDR/refund operation is created. The policy remains for Mland-caused
remedy cases and other explicitly approved provider-remedy flows.

Booking publishes a durable `MlandBookingConfirmedEvent` containing only the
booking reference and committed confirmation timestamp in the same transaction
as the `CONFIRMED` transition. Settlement consumes it after commit and writes a
unique booking-time snapshot with the effective revision (or the explicit
initial 100% default). Duplicate delivery is idempotent; a different timestamp
for an already-snapshotted booking is rejected for reconciliation. A remedy
command fails closed while the durable confirmation snapshot is unavailable; it
never substitutes the latest policy at remedy time.

### Authorization, Audit and Idempotency Evidence

Protected command evidence records the actor reference when applicable, booking,
canonical location when applicable, action, reason, outcome, correlation reference and time.
Finalization never has a role-based unpaid-balance exception. Active Manager
authority is global across Feature 003, while Staff checks the booking's
canonical location scope. Business audit records only accepted or denied
commands and never records a waiver because no waiver operation exists.
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
7. Cancellation scope remains the whole booking/package. A policy may produce
   a partial monetary refund; that aggregate is calculated once and allocated
   oldest original payment first, capped by each original payment. No
   ticket/participant/session-scoped cancellation is introduced. A customer
   request whose computed refund is zero is rejected and leaves booking,
  ticket and capacity state unchanged. The workshop product is handed over only
  after its workshop session; requiring every required session to remain
  unstarted already prevents cancellation after product handover, so no second
  output-release state is needed in the cancellation snapshot.
8. A Mland-caused remedy is traceable to the same cancellation handoff as its
   replacement attempt.
9. Policy revisions are prospective and cannot rewrite a confirmed booking's
   snapshot.
10. A client idempotency key cannot produce two settlement effects or be reused
    for different semantic input.
11. A notification handoff is immutable and reference-only; duplicate delivery
    events cannot create a second delivery request or alter settlement truth.
