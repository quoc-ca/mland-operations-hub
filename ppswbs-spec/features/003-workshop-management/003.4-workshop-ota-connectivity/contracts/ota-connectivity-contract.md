# Contract Design: Workshop OTA Connectivity

**Date created**: 2026-10-24
**Date updated**: 2026-10-26
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Contract status

This document is a provider-neutral design contract for review. Mland-owned REST request/response schemas, validation, statuses, and safe errors are specified in [ota-connectivity.openapi.yaml](./ota-connectivity.openapi.yaml). Exact Klook/GetYourGuide routes, headers, signature checks, callback acknowledgements, timeouts, and error codes may be implemented only from the approved provider agreement/onboarding material.

## Internal OTA facade

Other modules interact with OTA through an application facade, not its entities or provider adapter classes. The facade needs these conceptual operations:

| Operation | Caller / purpose | Required result |
| --- | --- | --- |
| Receive authoritative OTA-observable session snapshot | Persistent internal Spring Modulith publication from Operations, recorded with a completed schedule, availability, or sale-state change | Accept only the next/current value in the session's unified source-version sequence, or suspend only the affected mapping and request authoritative resync for a gap or uncertain order. The publication is at-least-once; V1 uses no broker. |
| Request inventory publication | Authorized OTA workflow after an accepted Operations snapshot or approved-price change | Durable change reference; delivery occurs asynchronously. |
| Receive provider event | Authenticated provider adapter/callback endpoint | Safe acknowledgement plus prior/current idempotent outcome; no duplicate Mland effect. |
| Start/continue reconciliation | Authorized branch Manager or Admin as appropriate | Work-item reference with correlation/evidence links; no concealed correction. |
| Force a full re-sync | Authorized actor after incident review | Obtain the authoritative current snapshot for the affected session at its existing source version, then create auditable OTA-side reconciliation/delivery work. It is not a new Operations business change and cannot reopen booking until successful confirmation. |
| Record verified commercial evidence | Authorized Admin finance/connection workflow | Immutable reference to approved terms; never records raw credentials. |

The facade calls Booking, Operations, and Settlement using their public facades. It must not write booking, capacity, ticket, settlement, or personnel tables directly.

## Upstream facade dependency contract

Before implementation, owners of 003.1, 003.2, and 003.3 must verify that
their published facades provide these semantics. OTA stops at its facade
boundary if an operation is absent; it must not use another feature's entity,
repository, or service.

| Owner | OTA use case | Required semantics | Failure / idempotency rule |
| --- | --- | --- | --- |
| 003.1 Workshop Booking | Create-or-return Mland booking from an external correlation key. | Returns one Mland booking reference and no duplicate business booking for the same provider-scoped external booking reference. | Repeat returns the original result. A conflict is explicit and has no hidden capacity effect. |
| 003.2 Workshop Operations | Reserve `N` places and issue exactly `N` active ticket references for one accepted booking. | Capacity validation, reservation, and ticket issuance are one atomic outcome; every ticket is independently scannable. | Insufficient capacity returns no ticket/reservation. Repeat correlation returns original outcome. |
| 003.2 Workshop Operations | Apply confirmed cancellation consequence. | Revoke only eligible unused tickets and release only eligible future capacity exactly once. | A cancellation request alone has no effect; duplicate/out-of-order confirmation is idempotent. |
| 003.2 Workshop Operations | Supply OTA-observable session snapshot and authoritative resync. | For each Basic Ring session, supplies schedule as opaque session identity, branch, scheduled start/end, source change identity/version, effective time; or availability/sale state as opaque session identity, branch scope, sellable availability, sale state, source change identity/version, effective time. One monotonically increasing source-version sequence spans both snapshot kinds. Every source change is published through a persistent internal Spring Modulith publication in the same Operations local transaction; V1 uses no broker. No PII, Booking/payment/ticket detail, or OTA mapping is supplied. | Delivery is at-least-once. OTA ignores duplicates/older versions; a gap or uncertain order suspends only the affected mapping and requires authoritative resync. Resync returns the complete current session snapshot at its existing source version. `SALES_PAUSED` is normal closed/zero-availability publication, not suspension. |
| 003.3 Workshop Settlement | Create a direct-Mland paid add-on bill from an accepted quote. | Returns direct bill/payment correlation while keeping OTA channel payment, price, commission, payout, and channel refund unchanged. | Missing consent/invalid quote rejects with no bill. Repeated correlation returns the original result. |

OTA persists authenticated inbound-event receipt and correlation before side
effects. It invokes a facade with the same idempotency correlation. A provider
callback acknowledgement comes only from the persisted/original outcome; a
timeout or process crash must be recoverable by replaying the stored event.

Atomicity is required inside the responsible owner for capacity/tickets and
direct bill creation. This does not permit OTA to start a cross-feature database
transaction or write other modules' tables. If a facade cannot provide the
required result, the related OTA path remains disabled and the backend process
must report the gap under its AGENT rules.

### Identifier boundary

Identifiers created and owned by OTA (channel connection, mapping, channel
booking, work item, and quote) use the OTA contract's UUID format. References
to a workshop offer, session, Booking result, Operations ticket, or Settlement
bill are opaque non-empty strings at the OTA REST/facade boundary. The adapter
for each verified upstream facade performs the type conversion required by that
owner; it must not assume that 003.1's integer identifiers and 003.2's UUID
identifiers are interchangeable or leak either representation into a provider
contract.

## Provider adapter port

Each provider adapter translates only between provider messages and the adapter-neutral commands/events below:

| Direction | Adapter-neutral message | Minimum semantics |
| --- | --- | --- |
| Outbound | Publish inventory snapshot/change | Mapping identity, the accepted unified Operations source version, schedule/availability/sales status, and only provider-approved pricing data. |
| Outbound | Close/reopen sale | Makes the mapping unavailable when Mland requires it; reopening needs confirmed re-sync. |
| Inbound | Booking request/update | External booking/event references, entitled participant count, mapped offer/session, and permitted operational data. |
| Inbound | Cancellation/refund update | External reference, requested/confirmed status, reason/evidence if provided. |
| Inbound | Payment/payout update | Evidence reference and amount/currency only where contractually provided. |
| Inbound/outbound | Delivery result | Authenticated correlation, sanitized outcome, and retryability classification. |

The adapter port has no universal provider SLA or field list. A provider cannot be marked booking-enabled until its concrete mapping, authentication, ticket delivery, idempotency, cancellation, and late-booking decisions have review evidence.

## Mland REST surface

These paths are specified in the proposed OpenAPI contract. Responses expose safe correlation/status, never credentials, raw provider payloads, or unrestricted customer data.

| Method and path | Intent | Actor boundary |
| --- | --- | --- |
| `GET /api/v1/ota-channels` | List visible connection/mapping readiness and safe status. | OTA read action rule plus branch scope. |
| `POST /api/v1/ota-channels/{channelId}/mappings` | Create a draft mapping; no implicit publish. | OTA mapping action rule for canonical Admin/Manager roles; connection changes remain Admin-only. |
| `PUT /api/v1/ota-channel-mappings/{mappingId}` | Update a draft/sales-closed mapping with optimistic versioning. | Authorized actor; stale version is a conflict. |
| `POST /api/v1/ota-channel-mappings/{mappingId}/price-terms` | Submit price terms for commercial review; it does not publish or enable sale. | Authorized actor; unconfirmed Klook terms remain pending. |
| `POST /api/v1/ota-channel-mappings/{mappingId}/sync` | Queue preparation/full re-sync. | Branch Manager for business sync; Admin for cross-branch recovery. |
| `POST /api/v1/ota-channel-mappings/{mappingId}/sales-close` | Queue sales closure and record reason. | Authorized branch actor. |
| `POST /api/v1/ota-channel-mappings/{mappingId}/booking-enable` | Request controlled booking enablement after evidence validation. | Admin-controlled approval; rejects without provider readiness record. |
| `GET /api/v1/ota-reconciliation-work-items` | List reconciliation work in authorized scope. | Branch Manager or Admin by scope. |
| `POST /api/v1/ota-reconciliation-work-items/{id}/resolve` | Record a reviewed resolution; may queue re-sync. | Authorized actor; immutable decision audit. |
| `GET /api/v1/ota-channel-bookings/{channelBookingId}/financial-evidence` | Read separated channel-payment and payout evidence. | Authorized finance/reconciliation scope. |
| `POST /api/v1/ota-channel-bookings/{channelBookingId}/payout-reconciliation` | Append payout, variance, dispute, adjustment, or reversal evidence. | Authorized finance/reconciliation scope. |
| `POST /api/v1/ota-channel-bookings/{channelBookingId}/add-on-quotes` | Create a free engraving record or paid direct-Mland add-on quote. | Authorized Staff scope. |
| `POST /api/v1/ota-addon-quotes/{quoteId}/acceptance` | Record acceptance for a paid quote. | Authorized Staff scope; no bill before acceptance. |
| `POST /api/v1/ota-addon-quotes/{quoteId}/direct-bill` | Create the separate Settlement-owned direct-Mland bill. | Authorized Staff scope; quote must be accepted. |

The provider callback entry point is intentionally excluded from the shared
OpenAPI contract because its route, authentication, signature, body, response,
and retry behavior are provider-specific onboarding facts. It is not a Mland
user-session endpoint; each adapter publishes its approved contract separately.

Account Management supplies canonical role/status/location facts; OTA Connectivity owns the exact action rules over its mappings and reconciliation resources. The implementation must return standard safe errors without exposing mapping/booking existence outside scope, and must not authorize from a display role, Firebase claim or retired code.

## Callback, error, and replay rules

1. Verify the provider-specific authentication before parsing for business effects; redact body/header data from logs and responses.
2. Persist a safe inbound-event receipt and deduplication key before invoking downstream effects. A duplicate gets the same semantic acknowledgement as the first completed event.
3. Correlate every outbound delivery and inbound event with a safe opaque ID. Return an operator-safe message; detailed diagnostics belong in authorized reconciliation records.
4. If delivery retries are exhausted, close sale for the affected mapping and create reconciliation work. Do not claim that the OTA is synchronized.
5. A late booking received while incident sales closure is active follows only the written provider rule approved at go-live. Until then the mapping cannot accept bookings.

## Provider readiness record required before booking enablement

For each provider and mapping, retain review evidence for: signed/onboarded commercial terms; production/sandbox connection authority; authentication and callback verification; product/option/session mapping; availability and sales-close test; successful test booking; exactly-one-Mland-ticket-per-person and scan proof; cancellation/refund flow; payment/payout semantics; retry, idempotency, quota, and late-booking/capacity responsibility; and a permitted-data allowlist with operational purpose. Missing evidence is a failed enablement precondition, not a warning.
