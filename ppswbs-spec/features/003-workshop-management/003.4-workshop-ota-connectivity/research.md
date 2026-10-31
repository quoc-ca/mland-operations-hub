# Research: Workshop OTA Connectivity

**Date created**: 2026-10-24
**Date updated**: 2026-10-29
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Purpose and provenance

This file preserves approved OTA decisions migrated from the shared `ppswbs-spec/research.md` before its retirement. It supports [spec.md](spec.md) and does not approve provider onboarding, contracts, or implementation.

## Retained decisions

- Mland owns package/session availability and capacity. Klook/GetYourGuide only receive published availability and send booking requests to Mland.
- Each provider has its own adapter. Booking references and idempotency keys prevent duplicate effects; authenticated callbacks and retry/reconciliation protect Mland from external delivery failure.
- Initial OTA sale is `PER_PERSON` only; `BASE_PLUS_EXTRA`, workshop-item, co-maker, and group-pricing remain excluded until separately validated.
- Channel price records retain retail price, target net, currency, fee/commission version, and validity. Klook stays unpublished without commercial confirmation.
- Klook and GetYourGuide each require proved ticket-per-person delivery and Mland scanning before sale. A shared voucher is only an audited incident fallback; neither provider is enabled until its own evidence exists.
- Mland may synchronize schedule, availability, and sales-closed status to an authorized candidate OTA before ticket proof so catalogue setup can be checked. This is not permission to accept a booking or consume capacity.
- Confirmed failure posture: after automatic retry is exhausted, close sales for the affected OTA mapping, retain inbound events for reconciliation, and reopen only after successful confirmed re-sync.
- Confirmed go-live boundary: public documentation is insufficient to decide whether Mland confirms or rejects a booking delivered after sales close during a sync incident. The provider must confirm this rule, timeout/retry/idempotency, and capacity responsibility in writing before Mland enables OTA bookings.
- OTA customer payment/refund and OTA payout owed to Mland are distinct, append-only financial models. Laser engraving is free; other services require consent and a separate direct-Mland bill.
- Store only contractually permitted operational customer data; do not create Mland identities or marketing audiences from OTA contacts.

## Public provider evidence checked on 2026-10-24

- Klook's public Open API integration process requires live test bookings, voucher issuance, and supplier verification by scanning the voucher QR code or barcode before go-live. This demonstrates platform capability, not Mland product certification: [Klook integration process](https://klook.gitbook.io/openapi/getting-started/integration-process).
- Klook's public product model supports `QRCODE` delivery by `VOUCHER` and supplier-controlled availability. Its exact applicable contract and mapping must still be confirmed during onboarding: [Klook products](https://klook.gitbook.io/openapi/api-core-mandatory/products).
- GetYourGuide documents automatic availability updates and adding supplier ticket barcodes/QR codes to GetYourGuide vouchers: [API features](https://supply.getyourguide.support/hc/en-us/articles/14150246193181-API-Features-and-Functionalities).
- GetYourGuide's public Supplier API documents single-ticket redemption and booking-level redemption separately. Mland must use the former for its per-participant check-in gate: [GYG redemption endpoints](https://integrator.getyourguide.com/documentation/gyg_endpoints).
- These public sources do not determine Mland's commercial terms, production credentials, exact mapping, or rule for a booking delivered after a sync incident; those remain provider onboarding gates.

## Plan research decisions

### Decision: provider-neutral port plus one adapter per OTA

**Rationale**: Klook and GetYourGuide expose similar business capabilities but their onboarding, authentication, payloads, ticket semantics, and commercial rules are not interchangeable. A port keeps provider payloads outside Mland's workshop modules and lets one mapping be suspended without changing another.

**Alternatives considered**: A shared `channel` DTO passed directly through Booking/Operations was rejected because it would leak provider fields and make each later adapter a cross-module change. A single generic webhook was rejected because authentication and idempotency evidence is provider-specific.

### Decision: durable outbound change and explicit reconciliation work

**Rationale**: Channel systems commonly distinguish queued/failed delivery and manual resync rather than assuming a successful request means the OTA reflects inventory. Travel operators also document manual resync as an incident recovery tool: [RezStream channel sync](https://support.rezstream.com/knowledge-base/how-channel-sync-works-in-rezstream-channels-timing-frequency-expectations), [RMS manual OTA resync](https://support.rmscloud.com/hc/en-gb/articles/12111033737103-Manual-OTA-Resync).

**Alternatives considered**: Updating a mapping synchronously in the manager UI was rejected because a timeout leaves Mland unable to prove provider state. Unlimited retry was rejected because it can hide a mapping outage while sales remain open.

### Decision: preparation synchronization is sales-closed; booking needs its own gate

**Rationale**: It permits useful catalogue and availability validation while preventing capacity consumption before voucher/QR and late-booking behavior are proved. Public Klook and GYG documents show relevant capabilities but do not ratify Mland's production rules.

**Alternatives considered**: Blocking all connectivity until contracts arrive was rejected because it prevents low-risk mapping validation. Treating public documentation as go-live approval was rejected because it does not settle the commercial or incident responsibilities.

### Decision: per-participant Mland tickets are the required redemption proof

**Rationale**: GetYourGuide documents single-ticket redemption separately from whole-booking redemption, matching Mland's one participant/one ticket rule. Klook's public process also requires voucher scan verification before launch.

**Alternatives considered**: A single booking voucher was rejected as normal flow because scanning it could admit all participants; it remains only a separately audited incident fallback after approval.

### Decision: do not invent operational constants

**Rationale**: Retry counts/backoff, provider timeouts, data retention, credentials, quota, cancellation semantics, FX, taxes, commission, and the late-booking decision must be evidenced per provider. These values affect money, capacity, and contractual liability.

**Alternatives considered**: Adopting default numbers from other channel managers was rejected because their contracts and failure responsibilities do not bind Klook or GetYourGuide.

## Open gates

- Provider contract/onboarding, credentials, callback authentication, quotas, supported ticket delivery, and scan evidence.
- Klook price semantics, commission, taxes/fees, currency/FX, and payout terms.
- Provider-written decision for a booking received after a sales-close update, including timeout/retry/idempotency and capacity responsibility.
- Account Management supplies canonical role/status/location facts; OTA Connectivity owns action rules for channel configuration, reconciliation and incident fallback. Display role labels, Firebase claims and retired codes do not grant authority. Every active Manager has global location scope for Manager-authorized OTA business actions, including finance-evidence read and payout reconciliation. Admin retains provider credentials/connection and explicitly technical actions; Staff-only add-on actions remain location-scoped.
