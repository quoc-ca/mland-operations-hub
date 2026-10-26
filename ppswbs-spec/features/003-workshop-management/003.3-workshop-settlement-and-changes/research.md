# Research: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-10-27
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Purpose and inherited baseline

This self-contained research translates confirmed 003.3 decisions into design
constraints. It is not an API, database, migration, or implementation instruction.

| Source | Inherited decision |
| --- | --- |
| 003.1 Booking | One direct booking buys one package; required sessions may be one or more. Package price and deposit evidence freeze at confirmation. |
| 003.2 Operations | Owns sessions, capacity, ticket entitlement and attendance. 003.3 uses its facade only. |
| 003.3 `spec.md` | One final bill covers the whole booking/package, never a session, participant or legacy workshop item. |
| FE-001 deferred decisions | Firebase proves identity only; Mland decides account status, role and scope. Vocabulary: `MEMBER`, `STAFF`, `MANAGER`, `ADMIN`. |

## Confirmed decisions

### Settlement and charges

One final bill is issued after the final required session. Its balance is frozen
package price plus accepted charges less accepted deposit. A future internal
per-session allocation cannot change V1 checkout, final bill, or refund.

A customer may select only an allowed fixed-price catalogue add-on for the
package/location. A variable material/service charge enters the bill only after
Staff/Manager confirmation. Customers cannot enter arbitrary price. This follows
the controlled-option / merchant-confirmed pattern seen in [Square modifiers](https://squareup.com/help/us/en/article/5119-create-and-manage-item-modifiers)
and [Shopify draft orders](https://help.shopify.com/en/manual/fulfillment/managing-orders/create-orders/create-draft).

### Final payment

VNPay is V1's only final-payment channel. Settlement accepts only valid signed
VNPay IPN or QueryDR evidence matching final-bill reference, amount and currency.
Browser return and manually recorded counter payment are not settlement evidence;
an in-store customer may still use a VNPay link/QR. Results are idempotent and
retain safe reconciliation references only, never raw signed payloads/secrets.
See [VNPay integration](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html)
and its [FAQ](https://sandbox.vnpayment.vn/apis/docs/faqs/).

### Required-session changes

There is no customer self-service reschedule. An authorized Manager requests one
complete booking-level replacement set (one or both future sessions); 003.2 must
validate capacity and entitlement and apply all changes or none. Persistent QR is
kept. A Silverclay result remains same-location, distinct, chronological, with at
least one slot between same-day sessions.

The approved `WorkshopOperationsFacade` now exposes the semantic complete-set
replacement operation. It remains an in-process named-interface boundary, not a
REST endpoint; concrete Java type details, locks and transaction protocol remain
implementation-owned.

### Refund and cancellation policy

Customer cancellation/refund is whole-booking only under existing timing and
attendance rules. For Mland-caused cancellation, offer replacement first; if it
is unavailable/declined, refund the whole package under its confirmation-time
policy snapshot. The current default is 100% of every collected payment,
including deposit/final payment, even if an earlier session was attended.

One designated central Manager may change this policy prospectively. Existing
snapshots stay immutable; location-scoped Managers resolve individual cases under the
existing snapshot. This is business authority, not a new role mapping. Booking
level handling matches course-booking patterns such as [Bookwhen cancellation guidance](https://support.bookwhen.com/en/articles/3790128-cancelling-an-event).

## Ownership and audit

| Concern | Owner | Rule |
| --- | --- | --- |
| Booking, package price, deposit | 003.1 | 003.3 consumes projection only. |
| Sessions, capacity, ticket, attendance | 003.2 | Semantic facade only. |
| Final bill, charge, settlement/refund/change case | 003.3 | Authoritative owner. |
| Identity and authorization | FE-001 | Query at command time. |
| Delivery | Feature 007 | 003.3 persists an immutable handoff with accepted Settlement state, then publishes a reference-only post-commit event. Feature 007 deduplicates and reads an immutable payload only through `WorkshopSettlementFacade`; delivery is not financial truth. |

Audit protected financial/change commands with actor reference when available,
booking reference, applicable canonical location, action, outcome, required reason,
correlation and time. Exclude raw provider payload/signature/secret, Firebase
token, card data, OTP and unnecessary PII. Operational retries are separate.

## Public REST decision

The approved public contract is resource-oriented: one booking-scoped final bill
with nested charges/finalization, and auditable booking-scoped cancellation,
required-session-change, deferred-payment and Mland-remedy cases. Prospective
Mland cancellation refund policy is a central resource because it applies across
locations only to later-confirmed bookings. Client commands use an
`Idempotency-Key`; VNPay IPN remains signed provider evidence replay-protected by
its provider transaction reference. This separates a browser/client retry from a
gateway callback and preserves the rule that browser return is never payment
truth.

## Implementation dependencies

1. 003.2 must expose semantic all-or-none replacement of a complete required-session set.
2. FE-001 must expose current canonical role/status, ownership and location-scope
   facts; 003.3 applies its own approved location/resource action rules and
   central-policy-editor rule.
3. VNPay merchant, IPN/QueryDR and refund configuration must be provider-approved.
4. Notification owner must provide the post-commit link/reminder/outcome handoff.

None authorizes endpoints, schema, role mapping or provider policy to be invented.
