# Implementation Plan: Workshop OTA Connectivity

**Date created**: 2026-10-24
**Date updated**: 2026-10-29
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Branch**: `003.4-workshop-ota-connectivity` | **Spec**: [spec.md](./spec.md)

## Summary

Design a provider-neutral OTA-connectivity module that lets Mland publish approved workshop schedule, availability, and sales-closed state while Mland remains the source of truth for capacity, bookings, and tickets. Klook and GetYourGuide are candidate adapters, not enabled integrations. Each provider can move from preparation synchronization to booking only after its separate onboarding, commercial, callback, ticket/QR, and late-booking gates are evidenced.

The planned design uses an adapter boundary, durable change delivery, idempotent inbound processing, audit-safe reconciliation, and facade calls to Booking, Operations, Settlement, and Account Management. Account Management supplies canonical account facts while OTA Connectivity applies its own action rules. It deliberately does not promise a real-time SLA or accept an OTA booking before the provider gate is approved.

### Resolved Operations Snapshot Consumer Boundary

003.4 consumes, but never constructs or recalculates, the authoritative
schedule snapshot and availability/sale-state snapshot from 003.2. The schedule
input is limited to opaque session identity, canonical location, scheduled start/end, source
change identity/version, and effective time. The availability/sale-state input
is limited to opaque session identity, canonical location scope, sellable availability, sale
state, source change identity/version, and effective time. The input excludes
PII, Booking/payment/ticket detail, and OTA mapping. Sellable availability
already subtracts confirmed ticket seats and active, unexpired direct-Mland holds.
Each session has one monotonically increasing source-version sequence shared by
both snapshot kinds. 003.4 uses it to ignore duplicates or older updates and
requests an authoritative 003.2 resync when it detects a gap or cannot establish
ordering. This internal boundary is not a public REST API or provider payload.

If provider publication fails, only the affected 003.4 mapping may be
sales-closed/suspended and reconciled. It must not change a 003.2 session's
capacity or sale state, or alter direct-Mland booking behaviour. Basic Ring is
the only OTA package in V1; Silverclay remains direct-Mland only. A normal
`SALES_PAUSED` snapshot closes sale or publishes zero availability for the
affected session while retaining its mapping; only a source gap/order failure or
provider delivery failure suspends the mapping.

## Technical Context

**Language/Version**: Java 21 backend; TypeScript React/Vite client when the frontend is implemented.

**Primary Dependencies**: Spring Boot 3.4.1, Spring Modulith 1.3.1, Spring Data JPA, Flyway, Spring Security, Firebase Admin; provider HTTP clients and OpenAPI generation are implementation decisions after contract approval.

**Storage**: MySQL with append-only Flyway migrations. Operational delivery, reconciliation, financial-position, and audit state are durable business data; credentials and full external payloads are not stored or logged in this module.

**Testing**: JUnit/Spring Boot, Spring Modulith module verification, JPA/Flyway migration tests, authorization tests, adapter-contract tests with provider simulators, idempotency/replay/out-of-order tests, and end-to-end quickstart evidence. Production-provider tests require approved sandbox credentials.

**Target Platform**: Versioned REST API and a separate React/Vite operations client. The current frontend repository has no application baseline; UI delivery is explicitly deferred until that baseline is approved rather than scaffolded implicitly by this feature.

**Project Type**: Modular-monolith web application with external OTA adapters.

**Performance Goals**: No approved numeric latency, retry, or throughput target. Correctness, idempotency, auditability, and safe sales closure take priority over apparent immediacy.

**Constraints**: Mland is authoritative for schedule, capacity and ticket
issuance; 003.4 consumes the 003.2 schedule and availability/sale-state
snapshots without recalculating or writing them; no provider secret, customer
card data, or raw callback payload may appear in logs; server-side capability
and location scope decide authorization; all OTA booking effects must be
idempotent; a failed mapping closes OTA sales until confirmed re-sync.

**Scale/Scope**: Initial `PER_PERSON` workshop offers only. Preparation synchronization may cover schedule, availability, and sales-closed state. Booking, ticket delivery, cancellation/refund handling, and payout reconciliation are enabled per provider only after the go-live gates in [spec.md](./spec.md) are approved.

## Constitution Check

| Gate | Pre-design | Post-design | Evidence / condition |
| --- | --- | --- | --- |
| Module integrity | PASS | PASS | A dedicated OTA facade owns channel state and calls Booking, Operations, Settlement, and Account/Personnel only through their facades. |
| Identity and data | PASS | PASS | Firebase establishes Mland user identity; Account Management supplies canonical role/status/location facts. OTA Connectivity applies action rules from canonical role/status; active Managers have global OTA business-action scope without `activeLocationIds` filtering, while location-scoped Staff rules (where specified) use canonical location facts. Provider callback identity is separate from a user identity. |
| REST and frontend separation | PASS | PASS | Proposed REST paths are versioned/plural; a separate React/Vite client consumes API contracts only. |
| Persistence and migration safety | PASS | PASS | JPA-owned records and append-only Flyway migrations; uniqueness and state invariants are tested. No raw SQL persistence design is required. |
| Verification | PASS | PASS | Module, migration, contract, authorization, replay, failure-recovery, and reconciliation scenarios are specified below. |
| State and security simplicity | PASS | PASS | Durable business state is limited to mappings, changes, deliveries, inbound events, bookings, financial positions, and reconciliation work. No new session/JWT/stateful frontend mechanism is introduced. |
| Provider readiness | CONDITIONAL | CONDITIONAL | Public documentation proves possible API capabilities only. It cannot approve commercial terms, production credentials, ticket proof, or late-booking responsibility; those remain explicit release gates. |

No constitution exception is requested. The conditional provider gate constrains release behavior, not the documentation plan: the only permitted pre-gate mode is sales-closed/preparation synchronization.

## Project Structure

### Documentation

```text
ppswbs-spec/features/003-workshop-management/003.4-workshop-ota-connectivity/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    ├── ota-connectivity-contract.md
    └── ota-connectivity.openapi.yaml
```

`tasks.md` is the dependency-ordered implementation handoff generated after
this plan. It must be rebased when an approved design decision changes.

### Intended implementation boundaries

```text
ppswbs_backend/src/main/java/.../features/otaconnectivity/
├── facade/                 # Only entry point for other backend modules
├── application/            # publish, delivery, inbound, reconciliation use cases
├── domain/                 # mappings, lifecycle rules, adapter-neutral models
├── persistence/            # JPA entities/repositories owned by this module
├── adapter/                # provider-neutral port and Klook/GYG implementations
├── api/                    # versioned REST controllers and DTOs
└── integration/            # authenticated callback endpoint and delivery workers

ppswbs_frontend/src/.../ota-connectivity/  # Deferred until a React/Vite baseline exists
├── pages/                  # channel configuration and reconciliation views
└── api/                    # generated/typed API client only
```

The source tree is a proposed boundary, not an instruction to create it before the next implementation phase. The current frontend has no application source tree to extend, so feature UI tasks are blocked by the approved frontend baseline. OTA owns only its channel records and delivery/reconciliation state; workshop capacity/tickets remain Operations, workshop booking remains Booking, and direct-Mland bills remain Settlement.

## Phase 0: Research

[research.md](./research.md) records the provider evidence and the resulting decisions: adapter-per-provider, durable synchronization, sales-closed staging, per-ticket redemption proof, and explicit evidence gaps. The key negative finding is that a public API page is not a commercial or operational contract.

## Phase 1: Design and Contracts

- [data-model.md](./data-model.md) proposes owned records, relationships, lifecycles, and invariants.
- [ota-connectivity-contract.md](./contracts/ota-connectivity-contract.md) defines the provider-neutral boundary and required 003.1/003.2/003.3 facade semantics; [ota-connectivity.openapi.yaml](./contracts/ota-connectivity.openapi.yaml) specifies Mland-owned REST DTOs, validation, statuses, and safe error envelopes without freezing Klook or GetYourGuide payloads.
- [quickstart.md](./quickstart.md) defines the test/sandbox evidence needed for preparation sync, booking enablement, incident handling, and reconciliation.

## Design Decisions

- Use one provider-neutral adapter port per candidate OTA. A provider-specific payload never leaks into Booking, Operations, Settlement, UI, or public REST DTOs.
- Record a versioned outbound change from the authoritative 003.2 source change
  identity/version before attempting delivery. Delivery attempts are append-only
  evidence; retry exhaustion moves only the affected mapping to sales-closed and
  creates reconciliation work rather than silently retrying forever.
- Apply at-least-once Operations schedule and availability/sale-state snapshots
  through persistent internal Spring Modulith event publications recorded in
  the same Operations local transaction as the authoritative source change;
  V1 uses no message broker. Consume them idempotently through one
  monotonically increasing per-session source version: ignore duplicates or
  older updates, and request an authoritative Operations resync for a gap or
  uncertain ordering. Before any provider call, materialize an accepted update
  as this module's durable outbound change record. Do not create a provider
  call or mutate a mapping from an update that has not been accepted as current.
  Publish `SALES_PAUSED` as normal closed/zero availability while retaining the
  mapping.
- Deduplicate inbound callbacks by provider, provider event reference, and safe payload fingerprint before invoking Mland booking/ticket effects. A duplicate therefore returns the prior outcome and never consumes additional capacity.
- Permit only `PER_PERSON` sale and require one successful Mland ticket for each participant. A provider booking cannot become confirmed until the Operations facade atomically reserves capacity and returns those tickets.
- Separate OTA guest payment evidence, OTA payout owed to Mland, and any direct Mland add-on bill. None is derived from a browser redirect or from another ledger.
- Treat provider-specific timeout, retries, idempotency header, cancellation,
  and voucher behavior as adapter configuration backed by written evidence, not
  constants inferred from public docs. Mland's late-booking rule is fixed:
  reject/not confirm after mapping sales-close or suspension, reserve no
  capacity, issue no ticket, and create reconciliation; provider acknowledgement
  and retry behavior must evidence compatibility before booking enablement.
- Treat Mland REST API details as an explicit contract now, but keep provider callback wire formats out of shared OpenAPI until each adapter's onboarding material is approved.
- Gate booking/cancellation/add-on integration on verified upstream facades: OTA records/replays correlations, while the owning module provides atomic capacity/ticket or direct-bill effects.

## Risk Review: shortcuts rejected

Calling this merely a calendar sync would conceal the failure paths that create
overbooking: stale inventory, duplicate callbacks, missing ticket delivery, and
bookings arriving after a sales-close update. Recalculating availability in
003.4 would be another hidden oversell path because holds and confirmed tickets
belong to Operations. Likewise, enabling both Klook and GetYourGuide from public
API examples would make Mland responsible for terms it does not know. The plan
therefore permits useful catalogue/preparation sync but keeps booking disabled
until each provider passes the evidence checklist. No invented SLA, retention
period, role code, credential store, or pricing rule is treated as an approved
requirement.

## Complexity Tracking

No constitution violation or additional project is required.

## Delivery and Verification

Before implementation, approve the proposed schema/REST/adapter boundary and
obtain a provider readiness record for each provider to be enabled. Implement
using the next append-only Flyway migration after checking deployed history.
Verify `ApplicationModules.verify()`, empty/upgrade migration behavior,
authorization, callback authentication/redaction, duplicate and out-of-order
delivery, authoritative Operations resync after any schedule/availability/sale-
state version gap, normal pause/resume publication, outage-to-mapping-sales-
close, confirmed re-sync, per-ticket check-in, and the quickstart scenarios.
Verify that mapping suspension never mutates Operations capacity/sale state or
direct-Mland booking behaviour. Also verify that a failed or restarted 003.4
consumer receives the persistent internal Operations publication again and
creates no duplicate logical outbound change before its separately durable
provider delivery. Do not deploy booking capability merely because preparation
synchronization succeeds.
