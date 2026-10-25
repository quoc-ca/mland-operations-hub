# Data Model: Workshop OTA Connectivity

**Date created**: 2026-10-24
**Date updated**: 2026-10-24
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Status and ownership

This is a proposed implementation model for review, not an approved database schema. Names, physical column types, indexes, retention, and provider-specific fields remain implementation decisions. The OTA module owns the records below; Booking, Operations, Settlement, and Account/Personnel remain the systems of record for their existing responsibilities.

## Core records

| Record | Purpose | Key relationships and invariants |
| --- | --- | --- |
| Channel connection | Identifies an approved provider configuration without storing the secret itself. | One provider connection is Admin-owned; credential reference is opaque and never returned/logged. It may be disabled independently of mappings. |
| Channel mapping | Links an Mland workshop offer/session sellable to one provider product/option. | Unique provider + external product/option mapping. Cross-module offer/session references are stored as opaque values at the OTA boundary and resolved through verified facades; it can be `DRAFT`, `PREPARATION_SYNC`, `SALES_CLOSED`, `BOOKING_ENABLED`, `SYNC_SUSPENDED`, or `DISABLED`. Only written evidence may move it to `BOOKING_ENABLED`. |
| Channel price snapshot | Records the approved commercial terms used for a mapped offer. | References mapping; stores retail/net/currency/fee-or-commission version/effective window only after provider confirmation. It is immutable once used by a booking/payout position. |
| Outbound sync change | Durable request to publish availability, schedule, sales state, or permitted price data. | References mapping and source version. Same mapping + source version + change kind is unique. It contains a minimized/redacted canonical payload, never a credential. |
| Delivery attempt | Append-only evidence for each adapter call. | References one outbound change; records safe outcome/correlation/time and sanitized failure category. It must not store raw provider headers/body if those can contain secrets or PII. |
| Inbound channel event | Audit/deduplication record for a callback or provider poll result. | Unique provider + external event reference; safe fingerprint covers events without a stable reference. Authentication result is stored before side effects. |
| Channel booking | Provider booking correlated to Mland booking/tickets. | Unique provider + external booking reference. It records provider booking status, idempotency correlation, and an immutable booking snapshot: sales channel, displayed policy/version, booking cutoff and timezone, retail/target-net amounts, commission/fee facts, currency, accepted amounts, and a Mland booking reference only after successful facade acceptance. Later mapping or price changes cannot rewrite this snapshot. |
| Channel booking ticket link | Connects each OTA participant entitlement to one Mland-issued ticket. | A confirmed `PER_PERSON` channel booking requires exactly one Operations ticket per participant; no ticket may be linked twice. |
| Customer payment evidence | Tracks OTA-reported guest payment/refund evidence. | References channel booking; it is evidence, not Mland card processing. State transitions are append-only/auditable and never infer a refund from a browser redirect. |
| Payout position and ledger entry | Tracks what the OTA owes Mland and the evidence that changes the position. | References booking/price snapshot where applicable. Ledger entries are append-only; payout settlement cannot overwrite prior evidence. |
| Direct OTA add-on bill link | Connects an accepted paid add-on to the Settlement-owned direct-Mland bill. | Only quotation/acceptance correlation is OTA-owned; card/payment/refund truth stays Settlement. Laser engraving has no bill. |
| Reconciliation work item | Makes unresolved delivery, booking, ticket, payment, or payout discrepancies actionable. | References the affected mapping/event/booking/position; includes branch scope, safe evidence links, assignee/decision audit, and resolution state. |

## Lifecycle rules

### Channel mapping

`DRAFT` -> `PREPARATION_SYNC` -> `SALES_CLOSED` is permitted after mapping validation. `SALES_CLOSED` -> `BOOKING_ENABLED` requires provider-specific go-live evidence. Retry exhaustion or an unresolved data mismatch moves an affected enabled mapping to `SYNC_SUSPENDED`/sales-closed, creates a reconciliation item, and blocks new capacity commitments. It may reopen only after a confirmed full re-sync. `DISABLED` is terminal for new business effects but retained evidence remains auditable.

The final state names are proposed. Their required behavior is not: an unconfirmed or failed mapping must not accept a booking.

### Inbound booking and ticket issuance

1. Authenticate and deduplicate the inbound event before any Mland side effect.
2. Reject or hold according to the provider-approved late-booking rule. Before that rule exists, booking enablement is forbidden.
3. Through the Booking/Operations facades, create or locate the Mland booking, reserve capacity, and issue `N` tickets for `N` entitled participants as one idempotent outcome.
4. Persist the channel booking/ticket links and a safe provider response correlation. A replay returns the prior outcome rather than reserving more capacity.

### Cancellation, payment, and payout

- A cancellation request is not a confirmed cancellation. Provider policy, redemption/check-in state, and the provider-approved workflow decide whether it can be confirmed; Operations effects occur only after that decision.
- Customer payment/refund evidence and Mland payout position are separate streams. Both retain their original external reference and evidence history.
- An accepted non-free add-on creates/links a direct-Mland Settlement bill; it never changes the OTA customer-payment record.

## Cross-module boundary

| Need | Owning module | OTA interaction |
| --- | --- | --- |
| Capacity, session, attendance, ticket scan | Workshop Operations | Request a guarded reservation/ticket operation and consume a ticket reference; OTA never writes capacity/tickets directly. |
| Customer booking and booking lifecycle | Workshop Booking | Request idempotent booking creation/lookup; retain only correlation. |
| Direct-Mland bill and payment/refund | Workshop Settlement | Request a quote/bill link for accepted paid add-ons; do not own money processing. |
| Mland actor/branch/capability | Account/Personnel | Ask facade to authorize; provider identity is not mapped to a Mland user. |

## Integrity and privacy rules

- External reference, source version, and event correlation keys are unique in the appropriate provider scope; duplicate delivery is normal input, not an error path that repeats effects.
- Outbound changes and delivery attempts are never deleted as a shortcut for retry; a new attempt or reconciliation decision is new evidence.
- Store only operationally necessary customer data permitted by the provider agreement. Do not create a Mland customer account, marketing audience, or reusable identity from OTA data.
- Secrets, access tokens, payment-card data, and raw sensitive callback payloads are excluded from entities, error text, and audit views.

### Provider-data allowlist

Before a provider is booking-enabled, its readiness record must name the
permitted operational fields, their use, and the adapter mapping. The default
is deny: a field not listed is discarded before entity persistence, audit
logging, error rendering, and frontend DTO mapping. The initial permitted
categories are limited to provider/external correlation references, mapped
offer/session, participant entitlement count, ticket references, and the
financial/policy facts explicitly required by this feature. Contact data is
included only when the signed provider terms and a documented operational use
require it. No retention period is invented here; it follows the approved
baseline/provider terms.
