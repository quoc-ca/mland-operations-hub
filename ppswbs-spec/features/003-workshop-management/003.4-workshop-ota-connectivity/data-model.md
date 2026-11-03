# Data Model: Workshop OTA Connectivity

**Date created**: 2026-10-24
**Date updated**: 2026-11-01
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Status and ownership

The Feature 003 owner approved the physical schema boundary recorded in this section on 2026-10-29. This approval covers the V1 storage decisions below; provider-specific contact fields remain deny-by-default and require provider readiness evidence before persistent storage or booking enablement. The OTA module owns the records below; Booking, Operations, Settlement, and Account/Personnel remain the systems of record for their existing responsibilities.

### Approved V1 storage conventions

- OTA-owned primary keys use `BIGINT AUTO_INCREMENT`; OTA tables use InnoDB,
  `utf8mb4_unicode_ci`, and `DATETIME(6)` timestamps.
- References to Booking, Operations, Settlement, and Account are opaque
  boundary references with no cross-module foreign keys. Foreign keys between
  OTA-owned records are required where listed below.
- One channel connection is allowed per provider and environment (`TEST` or
  `PRODUCTION`). A connection stores only an opaque credential reference; the
  secret itself remains in external configuration/credential storage.
- Money is stored as signed `BIGINT` minor units with an ISO currency code. OTA API decimal values use the ISO 4217 minor-unit exponent exposed by the JDK `java.util.Currency` registry; unknown codes or currencies without a minor-unit exponent are rejected. Conversion is exact and rejects non-zero excess fractional digits without rounding.
- Internal OTA relations retain numeric `BIGINT` primary/foreign keys. HTTP identifiers are separate immutable public UUID references for channel connections, mappings, price snapshots, and reconciliation work items.
  Currency exponent follows the JDK ISO currency registry; no floating-point
  amount is persisted.
- Booking, payment/refund, payout, delivery-attempt, inbound-event deduplication,
  and reconciliation evidence is not automatically deleted in V1. Raw callback
  and provider request/response payloads, credentials, payment-card data, and
  unnecessary contact data are not retained.
- OTA contact is accepted only where the provider flow requires it. Persistent
  contact fields are deny-by-default and require a documented operational
  purpose, provider permission, access rule, and retention evidence before
  enablement. This does not change 003.1 direct-booking contact requirements.
- Mapping is one provider offer/option to one Mland offer/session. Each session
  has its own mapping and synchronization lifecycle.
- Each outbound change records a minimized canonical snapshot of one session's
  accepted source version/change. Retries use that same snapshot; each provider
  call is a separate append-only delivery attempt. No contact, ticket, or
  payment detail is included.
- A booking ticket link is per participant ordinal, not per purchaser. Each
  channel booking has one link per ordinal, and an Operations ticket reference
  can be linked only once within OTA data.
- Reconciliation work items are actionable by an authorized active Manager
  across locations, without checking `activeLocationIds`; canonical location is
  retained on each item for operational context and audit. A work-item
  assignment is for routing/accountability, not an authorization prerequisite.
  Assignment, decision, and resolution are audited. Work items contain
  references, not copied raw payload/contact data.

### Approved V1 record columns and keys

The following is the approved logical physical-column contract across the
append-only OTA migrations. `created_at`/`updated_at` use `DATETIME(6)` where
applicable; immutable evidence records use `recorded_at`/event timestamps.
Opaque cross-module references are `VARCHAR(128)` unless the owning facade
contract specifies a narrower stable representation. Provider references are
`VARCHAR(255)`; currency is `CHAR(3)`.

| Table | Required columns and constraints |
| --- | --- |
| `ota_channel_connections` | `id`; unique `public_reference CHAR(36)` UUID; `provider_code VARCHAR(32)`; `environment VARCHAR(16)`; `status VARCHAR(24)`; `credential_reference VARCHAR(255)`; timestamps. Unique `(provider_code, environment)`. No credential value. |
| `ota_channel_mappings` | `id`; unique `public_reference CHAR(36)` UUID; `version BIGINT`; `connection_id` FK; `offer_reference VARCHAR(128)`; `session_reference VARCHAR(128)`; `location_reference VARCHAR(128)`; `external_product_id VARCHAR(255)`; `external_option_id VARCHAR(255)`; `status VARCHAR(32)`; timestamps. Unique `(connection_id, external_product_id, external_option_id)` and `(connection_id, session_reference)`. |
| `ota_readiness_assessments` | `id`; unique `assessment_reference CHAR(36)`; `mapping_id` FK; `provider_code VARCHAR(32)`; `environment VARCHAR(16)`; `mapping_fingerprint CHAR(64)`; `readiness_record_reference VARCHAR(255)`; `decision_status VARCHAR(16)`; `reason VARCHAR(500)`; `reviewer_account_reference VARCHAR(128)`; `recorded_at DATETIME(6)`. Append-only. Each enablement decision records one assessment, including rejected/incomplete reviews. |
| `ota_readiness_check_evidence` | `id`; `assessment_id` FK; `check_code VARCHAR(48)`; `outcome VARCHAR(16)`; `evidence_reference VARCHAR(255) NULL`. Unique `(assessment_id, check_code)`. Append-only evidence snapshot; no underlying document, credential, or provider payload is copied. |
| `ota_channel_price_snapshots` | `id`; unique `public_reference CHAR(36)` UUID; `mapping_id` FK; `retail_amount_minor BIGINT`; `target_net_amount_minor BIGINT`; `commission_amount_minor BIGINT NULL`; `currency_code CHAR(3)`; `terms_reference VARCHAR(255)` (safe reference to provider-confirmed commercial evidence); `effective_from DATETIME(6)`; `effective_to DATETIME(6) NULL`; `created_at`. Created only after external provider confirmation; immutable after creation and after use by a booking or payout position. |
| `ota_outbound_changes` | `id`; `mapping_id` FK; `source_version BIGINT`; `source_change_reference VARCHAR(128)`; `change_kind VARCHAR(32)`; `canonical_snapshot` containing only allowlisted session schedule/availability/sale-state values; `status VARCHAR(24)`; timestamps. Unique `(mapping_id, source_version, change_kind)`. |
| `ota_control_outbox` | `id`; unique `operation_reference CHAR(36)`; `mapping_id` FK; `command_kind VARCHAR(32)`; `actor_account_reference VARCHAR(128)`; `reason VARCHAR(1000)`; `idempotency_key_hash CHAR(64)`; `request_fingerprint CHAR(64)`; `correlation_reference CHAR(36)`; `status VARCHAR(24)`; provider correlation/failure category; timestamps. Unique actor+idempotency key. Separately queues OTA-owned control commands and is never keyed by an Operations source version. The immutable Manager decision is also appended to `ota_reconciliation_decisions`. |
| `ota_delivery_attempts` | `id`; `outbound_change_id` FK; `attempt_number INT`; `started_at DATETIME(6)`; `completed_at DATETIME(6) NULL`; `outcome VARCHAR(24)`; `provider_correlation_reference VARCHAR(255) NULL`; `failure_category VARCHAR(64) NULL`. Unique `(outbound_change_id, attempt_number)`. Append-only. |
| `ota_inbound_events` | `id`; `connection_id` FK; `external_event_reference VARCHAR(255) NULL`; `payload_fingerprint CHAR(64)`; `dedupe_key CHAR(64)`; `event_kind VARCHAR(32)`; `authentication_result VARCHAR(24)`; `processing_status VARCHAR(24)`; `outcome_reference VARCHAR(128) NULL`; `received_at DATETIME(6)`; `processed_at DATETIME(6) NULL`. Unique `(connection_id, dedupe_key)`. The service hashes the external event reference when present, otherwise uses the canonical allowlisted payload fingerprint, so MySQL can enforce both cases with one non-null unique key. No raw payload. |
| `ota_channel_bookings` | Internal `id BIGINT` PK; unique immutable `public_reference CHAR(36)` UUID for the OTA API/facade boundary, backfilled for existing rows by an append-only migration; `mapping_id` FK; `external_booking_reference VARCHAR(255)`; `provider_status VARCHAR(32)`; `participant_count INT`; `mland_booking_reference VARCHAR(128) NULL`; policy/version and booking-cutoff/timezone snapshot; retail/target-net/commission/fee/accepted amount snapshots as `BIGINT` minor units with currency; timestamps. Unique `(mapping_id, external_booking_reference)`. Internal FKs continue to use numeric IDs. |
| `ota_booking_ticket_links` | `id`; `channel_booking_id` FK; `participant_ordinal INT`; `operations_ticket_reference VARCHAR(128)`; `linked_at DATETIME(6)`. Unique `(channel_booking_id, participant_ordinal)` and unique `operations_ticket_reference`. |
| `ota_customer_payment_evidence` | `id`; `channel_booking_id` FK; `external_evidence_reference VARCHAR(255)`; `evidence_kind VARCHAR(24)`; `evidence_status VARCHAR(24)`; `amount_minor BIGINT`; `currency_code CHAR(3)`; `occurred_at DATETIME(6)`; `recorded_at DATETIME(6)`; `correlation_reference VARCHAR(128) NULL`. Append-only; unique provider-scoped external evidence reference. `PAID_ON_CHANNEL` is recorded only when authenticated provider evidence matches the full accepted amount and currency for that channel booking; it is not Mland collection or payout evidence. |
| `ota_payout_positions` | `id`; `channel_booking_id` FK; `price_snapshot_id` FK nullable; `expected_amount_minor BIGINT`; `currency_code CHAR(3)`; `status VARCHAR(24)`; timestamps. Position is separate from guest payment evidence. |
| `ota_payout_ledger_entries` | `id` internal PK; `public_reference CHAR(36)` unique immutable UUID (V50 backfills existing rows); `payout_position_id` FK; `entry_kind VARCHAR(24)` (new facts use `GROSS_SALE`, `COMMISSION_FEE`, `CUSTOMER_REFUND`, `EXPECTED_PAYOUT`, `RECEIVED_PAYOUT`, `FX_VARIANCE`, `COMPENSATION`, `ADJUSTMENT`, `REVERSAL`, or `DISPUTE`); signed `amount_minor BIGINT`; `currency_code CHAR(3)`; `external_evidence_reference VARCHAR(255)`; `occurred_at DATETIME(6)`; `recorded_at DATETIME(6)`; `reason VARCHAR(1000) NULL`; `related_record_reference VARCHAR(128) NULL`; `evidence_type VARCHAR(24) NULL`; `allocation_reference VARCHAR(128) NULL`; `payout_reference VARCHAR(200) NULL` (V51); `correlation_reference VARCHAR(128) NULL`. Append-only; unique `(payout_position_id, entry_kind, external_evidence_reference)`. New Manager reconciliation facts require evidence type and nonblank reason and related-record reference; historical rows retain unknown values rather than invented explanations. `related_record_reference` holds a prior entry UUID for adjustments/reversals and the booking UUID for stage evidence. `allocation_reference` identifies the booking-specific share of a payout. REMITTANCE and BANK_RECEIPT carry the same `payout_reference`; a mismatched bank reference creates a dispute and cannot advance to `PAID_OUT`. Historical entry kinds remain readable without rewriting old rows. `correlation_reference` remains a separate technical trace reference. |
| `ota_addon_bill_links` | `id`; `channel_booking_id` FK; `settlement_quote_reference VARCHAR(128)`; `settlement_bill_reference VARCHAR(128) NULL`; `amount_minor BIGINT`; `currency_code CHAR(3)`; `status VARCHAR(24)`; timestamps. Settlement remains authoritative for bill/payment/refund truth. |
| `ota_reconciliation_work_items` | `id`; unique `public_reference CHAR(36)` UUID; nullable FK references to affected OTA mapping/event/booking/payout position; `location_reference VARCHAR(128)`; `category VARCHAR(32)`; `status VARCHAR(24)` (`OPEN`, `IN_PROGRESS`, `RESOLVED`, `ESCALATED`); `assigned_manager_reference VARCHAR(128) NULL`; `decision VARCHAR(64) NULL`; `resolution_reference VARCHAR(255) NULL`; `audit_reference VARCHAR(128) NULL`; timestamps. Active Managers may act across locations; retain location context and audit evidence. |
| `ota_reconciliation_decisions` | `id`; unique `decision_reference CHAR(36)`; nullable FK references to `work_item_id` and `mapping_id` (at least one required); `provider_code VARCHAR(32)`; `location_reference VARCHAR(128)`; `actor_account_reference VARCHAR(128)`; `actor_role VARCHAR(16)`; `action_code VARCHAR(32)`; `action_scope VARCHAR(32)`; `reason VARCHAR(1000)`; `outcome VARCHAR(32)`; `operation_reference CHAR(36)`; `correlation_reference CHAR(36)`; `idempotency_key_hash CHAR(64)`; `request_fingerprint CHAR(64)`; `source_version BIGINT NULL`; `recorded_at DATETIME(6)`. Append-only audit evidence for each sync or reconciliation action; unique `(actor_account_reference, idempotency_key_hash)` returns the original action result for an exact replay and rejects key reuse with a different request. |

Check constraints enforce the approved `TEST`/`PRODUCTION` environment values,
positive participant counts/ordinals and attempt numbers, non-negative
commercial price components where applicable, and effective windows where an
end is present. Other status vocabularies remain service-validated until their
provider-specific lifecycle values are approved.
No automatic cleanup job is authorized by this V1 design. The exact canonical
snapshot encoding is JSON with a strict typed allowlist; provider-specific
wire-format request/response bodies are never stored.

### Reconciliation and recovery lifecycle

For mapping-linked work items, `audit_reference` must contain a canonical UUID
and supplies the required REST `correlationId`. New mapped items receive a UUID
before persistence if no correlation was supplied; an explicitly malformed value
is rejected. Reads return the persisted value without generating or changing it.
V29 assigns tracking UUIDs to development records with missing/malformed values,
preserves valid UUIDs and decision history, and enforces the conditional database
constraint. These assigned UUIDs do not reconstruct historical log evidence.
Mapping-independent work items retain their nullable audit-reference contract.
An audited resolution may replace the current correlation projection with its
decision correlation; the append-only decision history retains previous actions.

Only active Managers and Admins may list or act on reconciliation work items,
globally. Preparation sync and sales-close are Manager business actions; Admin
may perform technical or recovery actions globally. Every action appends an
immutable decision record with canonical actor, role, provider/channel,
mapping, canonical location, action scope, reason, outcome, operation and
correlation references, and timestamp. The work-item decision and resolution
reference are only the current projection; they do not replace decision history.

`RESYNC_REQUESTED` is valid for an open mapping reconciliation. It keeps the
mapping `SYNC_SUSPENDED`, sets the work item `IN_PROGRESS`, reads the complete
current Operations snapshot at its existing source version, and queues the
full schedule, availability, and sale-state snapshot for that mapping. It does
not create an Operations business change. `ACCEPTED` can resolve a full-resync
work item only after all three outbound changes for the recorded source version
are `DELIVERED` and Operations still reports that same current version; it then
moves the mapping to `SALES_CLOSED`. A fresh Admin readiness assessment is
required before booking can be enabled again. `REJECTED` retains the work item
as `OPEN` and leaves the mapping suspended. `ESCALATED` changes the work item
status to `ESCALATED` and leaves the mapping suspended. All decisions remain in
the append-only audit history.

If an authoritative Operations snapshot at an existing source version differs
from any already-recorded immutable outbound snapshot for that mapping/version,
the resync command fails closed. It must not overwrite or requeue the conflicting
record, resolve the work item, or move the mapping out of `SYNC_SUSPENDED`.
The work item remains available for explicit escalation and investigation of the
Operations source-of-truth integrity failure. OTA must not invent a recovery
generation or alter the Operations source version to bypass the conflict.

### Commercial price snapshot selection

Price snapshots are append-only. For a booking at instant `t`, select the
snapshot for the mapping with greatest `effective_from <= t`. A newly recorded
snapshot supersedes all earlier snapshots starting at its `effective_from`;
`effective_to` is an exclusive upper bound for that selected snapshot. If the
selected snapshot has expired, there is no active price and the system must not
fall back to an older snapshot. This permits future terms to be added without
changing prior evidence.

### Manager sales-close control

A Manager sales-close action immediately moves an enabled mapping to
`SALES_CLOSED` so Mland stops accepting new OTA bookings, and atomically appends
an immutable decision plus a distinct OTA control-outbox command. The command
does not consume an Operations source version. Provider delivery and
acknowledgement update the outbox projection; exhaustion keeps the mapping
sales-closed and creates reconciliation work. Provider-specific retries remain
governed by the approved T021 adapter policy.

## Core records

| Record | Purpose | Key relationships and invariants |
| --- | --- | --- |
| Channel connection | Identifies an approved provider configuration without storing the secret itself. | One provider connection is Admin-owned; credential reference is opaque and never returned/logged. It may be disabled independently of mappings. |
| Channel mapping | Links an eligible Basic Ring Mland workshop offer/session sellable to one provider product/option. | Unique provider + external product/option mapping. Cross-module offer/session references are stored as opaque values at the OTA boundary and resolved through verified facades; it can be `DRAFT`, `PREPARATION_SYNC`, `SALES_CLOSED`, `BOOKING_ENABLED`, `SYNC_SUSPENDED`, or `DISABLED`. Only an Admin's approved readiness assessment for the current mapping fingerprint may move it from `SALES_CLOSED` to `BOOKING_ENABLED`; Silverclay has no V1 OTA mapping. |
| Readiness assessment and check evidence | Append-only Admin attestation that a provider/mapping/environment's written go-live evidence was checked. | Assessment captures a safe reference to the controlled evidence record, the exact mapping fingerprint, reviewer identity, decision/reason and time. It snapshots every required checklist check and its outcome/reference. The backend validates the complete fixed checklist and stores the decision atomically with the mapping transition; it does not fetch or copy external evidence documents. A missing/failed check is retained as `REJECTED` and leaves the mapping sales-closed. A changed mapping fingerprint makes an older approval stale. |
| Channel price snapshot | Records the approved commercial terms used for a mapped offer. | A Manager creates it only after the provider has confirmed the terms outside Mland, and stores the safe confirmation reference in `terms_reference`. Stores retail/net/currency/fee-or-commission/effective window. The snapshot is immediately approved and immutable after creation and use by a booking/payout position. No unconfirmed price draft/submission is persisted. |
| Outbound sync change | Durable consumer record to publish an accepted Operations schedule, availability/sale-state change, or permitted price data. | References mapping and the unified authoritative per-session source change identity/version. Same mapping + source version + change kind is unique. It contains a minimized/redacted canonical payload, never a credential; it does not recreate capacity arithmetic or retain Booking/payment/ticket detail. |
| Delivery attempt | Append-only evidence for each adapter call. | References one outbound change; records safe outcome/correlation/time and sanitized failure category. It must not store raw provider headers/body if those can contain secrets or PII. |
| Inbound channel event | Audit/deduplication record for a callback or provider poll result. | Unique provider + external event reference; safe fingerprint covers events without a stable reference. Authentication result is stored before side effects. |
| Channel booking | Provider booking correlated to Mland booking/tickets. | Unique provider + external booking reference. It records provider booking status, idempotency correlation, and an immutable booking snapshot: sales channel, displayed policy/version, booking cutoff and timezone, retail/target-net amounts, commission/fee facts, currency, accepted amounts, and a Mland booking reference only after successful facade acceptance. Later mapping or price changes cannot rewrite this snapshot. |
| Channel booking ticket link | Connects each OTA participant entitlement to one Mland-issued ticket. | A confirmed `PER_PERSON` channel booking requires exactly one Operations ticket per participant; no ticket may be linked twice. |
| Customer payment evidence | Tracks OTA-reported guest payment/refund evidence. | References channel booking; it is evidence, not Mland card processing. State transitions are append-only/auditable and never infer a refund from a browser redirect. |
| Payout position and ledger entry | Tracks what the OTA owes Mland and the evidence that changes the position. | References booking/price snapshot where applicable. Ledger entries are append-only; payout settlement cannot overwrite prior evidence. |
| Direct Mland shop add-on bill link | Connects an accepted paid shop add-on to the Settlement-owned direct-Mland bill. | Only quotation/acceptance correlation is OTA-owned; card/payment/refund truth stays Settlement. Laser engraving has no bill. |
| Reconciliation work item | Makes unresolved delivery, booking, ticket, payment, or payout discrepancies actionable. | References the affected mapping/event/booking/position; includes canonical location scope, safe evidence links, assignee/decision audit, and resolution state. |

## Lifecycle rules

### Channel mapping

`DRAFT` -> `PREPARATION_SYNC` -> `SALES_CLOSED` is permitted after mapping validation. Only `SALES_CLOSED` may move to `BOOKING_ENABLED`, in the same transaction that appends an Admin readiness assessment whose fixed checklist is complete and all checks are verified. `DRAFT`, `PREPARATION_SYNC`, `SYNC_SUSPENDED`, and `DISABLED` cannot be enabled directly. An incomplete or failed assessment is retained as `REJECTED` and leaves the mapping sales-closed. An approved assessment is valid only for its recorded mapping fingerprint; changing mapping identity/details requires a new assessment. Retry exhaustion or an unresolved data mismatch moves an affected enabled mapping to `SYNC_SUSPENDED`/sales-closed, creates a reconciliation item, and blocks new capacity commitments. It may return to `SALES_CLOSED` only after a confirmed full re-sync and then needs a new readiness assessment to reopen. `DISABLED` is terminal for new business effects but retained evidence remains auditable.

The final state names are proposed. Their required behavior is not: an unconfirmed or failed mapping must not accept a booking.

### Operations OTA-observable snapshot consumer semantics

003.4 consumes 003.2 semantic snapshots only after Operations completes a local
change that alters the OTA-observable result. The schedule snapshot contains
opaque session identity, canonical location, scheduled start/end, source change
identity/version, and effective time. The availability/sale-state snapshot
contains opaque session identity, canonical location scope, sellable availability, sale
state, source change identity/version, and effective time. Both exclude PII,
Booking, payment, ticket detail, and OTA mapping. Sellable availability is
authoritative: it already accounts for total capacity, confirmed ticket seats,
and active, unexpired direct-Mland holds.

Each session uses one monotonically increasing source-version sequence across
both snapshot kinds. Operations carries each snapshot through a persistent
internal Spring Modulith event publication recorded in the same local
Operations transaction as the source change; V1 does not use a message broker.
Delivery to this module is at-least-once. 003.4 accepts a newer source
version/change identity, ignores a duplicate or older update, and requests an
authoritative 003.2 resync for a gap or uncertain order in either snapshot.
Before it calls a provider, it materializes an accepted input as its own durable
outbound sync change, so a repeated internal publication cannot create another
logical provider update. It does not calculate schedule/availability, write
Operations capacity or sale state, or turn this internal input into a public
REST contract. `SALES_PAUSED` publishes normal closed/zero availability while
retaining a valid mapping. A source gap/order failure or provider delivery
failure may suspend only the affected channel mapping and create reconciliation
work; it cannot mutate Operations or direct-Mland booking behaviour.

### Inbound booking and ticket issuance

1. Authenticate and deduplicate the inbound event before any Mland side effect.
2. If the mapping is sales-closed or suspended, reject/not confirm the late
   booking, reserve no capacity, issue no ticket, and create an audited
   reconciliation case. Mland never overbooks or auto-moves the customer.
3. Through the Booking/Operations facades, create or locate the Mland booking, reserve capacity, and issue `N` tickets for `N` entitled participants as one idempotent outcome.
4. Persist the channel booking/ticket links and a safe provider response correlation. A replay returns the prior outcome rather than reserving more capacity.

### Cancellation, payment, and payout

- A cancellation request is not a confirmed cancellation. Provider policy, redemption/check-in state, and the provider-approved workflow decide whether it can be confirmed; Operations effects occur only after that decision.
- Customer payment/refund evidence and Mland payout position are separate streams. Both retain their original external reference and evidence history.
- An accepted non-free add-on creates/links a direct-Mland Settlement bill; it never changes the OTA customer-payment record.

## Cross-module boundary

| Need | Owning module | OTA interaction |
| --- | --- | --- |
| Schedule, capacity, session, attendance, ticket scan | Workshop Operations | Consume only authoritative schedule and availability/sale-state snapshots for publication, using their one per-session source-version sequence; use approved Operations facades for booking/cancellation effects. OTA never calculates or writes schedule/capacity/tickets directly. |
| Customer booking and booking lifecycle | Workshop Booking | Request idempotent booking creation/lookup; retain only correlation. |
| Direct-Mland bill and payment/refund | Workshop Settlement | Request a quote/bill link for accepted paid add-ons; do not own money processing. |
| Canonical Mland actor role/status/location scope | Account Management | Consume facts through facade; OTA Connectivity applies its own action rule. Active Manager OTA business actions are global and do not filter by `activeLocationIds`; Staff remains location-scoped where explicitly authorized. Retain canonical location for resource identity/audit; provider identity is not mapped to a Mland user. |

## Integrity and privacy rules

- External reference, source version, and event correlation keys are unique in the appropriate provider scope; duplicate delivery is normal input, not an error path that repeats effects.
- For either Operations schedule or availability/sale-state handoff, accept only
  a newer value in the one per-session source-version sequence. A duplicate or
  older handoff is ignored; a gap or uncertain order requires authoritative
  003.2 resync and suspends only the affected OTA mapping before publication can
  claim current inventory or schedule.
- A persistent internal Operations publication may be delivered again after a
  003.4 consumer failure or restart. The accepted source-version/change-identity
  uniqueness rule prevents that replay from creating a second logical outbound
  change; provider attempts remain the separate durable responsibility of 003.4.
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

**Approved implementation decision (2026-10-29):** The provider readiness
evidence is the authoritative allowlist approval record. OTA adapters implement
that reviewed list as explicit provider-specific code policy with default-deny
behavior; a change to the reviewed list requires a corresponding code change
and release. Do not persist a duplicate mutable allowlist in MySQL. Tests must
prove fields outside the reviewed list are discarded before persistence, audit,
error rendering, and frontend projection. Do not enable booking without the
provider's completed readiness evidence.

Commercial approval is not a two-step internal review. Before a confirmed
price snapshot exists, a mapping reports `PENDING_COMMERCIAL_CONFIRMATION` and
remains sales-closed. A Manager records provider-confirmed terms and their
evidence reference in one action; that action creates the immutable approved
snapshot. Unconfirmed proposals and external rejections are not persisted as
price submissions or snapshots. The mapping's commercial status is derived
from the existence of an approved snapshot, while booking enablement remains
subject to all separate readiness gates.
