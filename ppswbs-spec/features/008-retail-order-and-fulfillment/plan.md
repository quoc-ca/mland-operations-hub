# Implementation Plan: Retail Order and Fulfillment

**Branch**: `008-retail-order-and-fulfillment` | **Date**: 2026-10-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `features/008-retail-order-and-fulfillment/spec.md`

## Summary

Implement FE-08 as a bounded retail-sales and fulfilment capability. The
`readyringsales` module owns Member cart/grouped-checkout/order facts and the
`fulfilment` module owns preparation, GHTK shipment creation, status
synchronization, and physical handoff evidence. A grouped checkout is paid
once, then each paid branch order can start its own shipment creation after
commit. The GHTK adapter is contract-first and uses a mock provider until the
partner account, callback security, and prepaid/no-COD service are approved.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot project baseline, Spring Modulith 1.3.1,
Spring Data JPA, Flyway, MySQL, Firebase Admin SDK, Thymeleaf and htmx

**Storage**: MySQL through JPA/Spring Data; append-only Flyway migrations

**Testing**: JUnit/Spring Boot tests, Spring MVC contract tests, provider mock
contract tests, MySQL migration/integration tests, and Spring Modulith
`ApplicationModules.verify()`

**Target Platform**: Server-rendered Java web application with versioned REST
endpoints and server-to-server GHTK integration

**Project Type**: Spring Modulith modular monolith

**Performance Goals**: Preserve the approved Member checkout journey target of
under three minutes excluding external payment time; external calls must use
bounded timeouts and never hold a business transaction open while waiting.

**Constraints**: No raw SQL in application code; no cross-module entity,
repository, controller, DTO, or adapter access; Firebase proves identity only;
GHTK credentials remain server-side; payment/hold/order/shipment operations
are retry-safe; no COD, refund, return-resolution, accounting, or executive
reporting scope.

**Scale/Scope**: Five FE-08 user stories covering Member cart/marketplace-style
grouped checkout/order history, per-branch Staff/Manager preparation and
handoff, catalogue shipping metadata, one grouped VNPay payment, and one GHTK
carrier integration.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle/gate | Result | Plan evidence |
| --- | --- | --- |
| Module integrity and data ownership | PASS with boundary update required | `readyringsales` owns retail order facts; `fulfilment` owns shipment/custody; events/facades only. Update package metadata and verify with Spring Modulith. |
| JPA/Flyway persistence | PASS | Logical model is separated from physical migration; all schema work uses JPA and append-only Flyway. |
| Identity and sensitive-data boundary | PASS | Firebase UID is verified; Mland roles/status authorize actions; provider secrets, raw callbacks and unnecessary recipient data are excluded. |
| Versioned REST and safe errors | PASS | Feature OpenAPI uses `/api/v1`, plural kebab-case, DTO projections, validation and correlation-linked safe errors. |
| Progressive web baseline | PASS | Thymeleaf forms/links remain usable without htmx; REST is an explicit contract for enhanced clients/integrations. |
| Evidence-based verification | PASS | Quickstart and tasks must cover API, migration, idempotency, provider mock, authorization and module verification. |
| Unknown provider/lifecycle decisions | PASS with go-live gates | GHTK provider auth, credentials, quotas and prepaid/no-COD service mapping remain explicit gates; mock provider is used before approval. |

No constitution violation requires a complexity exception. The existing
baseline conflict about manual GHTK versus this clarified automatic integration
must be ratified and synchronized before production activation.

## Project Structure

### Documentation (this feature)

```text
features/008-retail-order-and-fulfillment/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── retail-order-and-fulfillment.openapi.yaml
│   └── ghtk-integration.md
└── checklists/requirements.md
```

### Source Code (repository root)

```text
../ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/
├── readyringsales/
│   ├── web/             # Member cart/order/checkout REST and Thymeleaf entry points
│   ├── application/     # checkout, hold, payment projection, order read use cases
│   ├── domain/          # cart/order/item/hold and fulfilment-choice invariants
│   ├── infrastructure/  # JPA repositories and event projections
│   └── facade/          # named catalogue/member/payment-facing boundary
├── fulfilment/
│   ├── web/             # Staff operations and GHTK webhook endpoint
│   ├── application/     # shipment orchestration, status reconciliation, handoff
│   ├── domain/          # shipment/status/handoff state and invariants
│   ├── infrastructure/  # GHTK adapter, mock adapter, JPA repositories, jobs
│   └── facade/          # shipment projection/status boundary
├── payments/            # existing VNPay port and verified-payment events
├── catalogue/           # product/variant and shipping-profile authority
├── members/             # identity, entitlement and ownership facade
└── common/              # technical kernel only; no FE-08 business entities

../ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/
├── readyringsales/      # checkout, ownership, hold and payment projection tests
├── fulfilment/          # GHTK contract, retry, webhook, status and handoff tests
└── ...                  # existing module and migration verification
```

**Structure Decision**: Extend the existing `readyringsales` and `fulfilment`
Spring Modulith modules. Do not create a new service or shared business
package. `readyringsales` publishes a minimal paid-order event; `fulfilment`
consumes it and publishes normalized shipment status/handoff events or a
read-only projection update without importing the other module's internals.

## Phase 0: Research Outputs

Completed in [research.md](research.md): module ownership, transaction/event
boundary, GHTK operations, fee/package inputs, retry/idempotency, status mapping,
scope reconciliation, and verification strategy.

## Phase 1: Design Outputs

Completed in [data-model.md](data-model.md),
[retail-order-and-fulfillment.openapi.yaml](contracts/retail-order-and-fulfillment.openapi.yaml),
[ghtk-integration.md](contracts/ghtk-integration.md), and
[quickstart.md](quickstart.md).

## Delivery Slices

1. Ratify baseline synchronization and update the cross-feature API/background
   job inventory for automatic GHTK scope.
2. Confirm module ownership, event names, facade dependencies and the physical
   database mapping; add append-only migrations only after mapping review.
3. Implement or complete catalogue shipping-profile facade consumption and the
   Member cart/branch grouping/grouped-checkout snapshot with 15-minute holds
   and 10-minute payment links.
4. Integrate verified VNPay payment events without direct provider coupling;
   make payment/hold/order effects idempotent.
5. Implement the mock `ShippingProviderPort`, per-branch GHTK fee quotes,
   shipment-create retry using the same partner ID, and normalized shipment
   projections for each branch order.
6. Implement webhook validation/deduplication, status GET reconciliation and
   the Member latest-status view.
7. Implement Staff/Manager preparation, pickup verification and physical GHTK
   handoff evidence with branch/delegation authorization.
8. Add retention cleanup for recipient data, notification intents, OpenAPI and
   all quickstart verification evidence.

## Internal and External Boundary Design

- `readyringsales` calls catalogue/member/payment facades only; it does not
  access their repositories or entities.
- A verified grouped-payment projection contains only the paid branch-order
  ID, accepted fulfilment method, version and correlation data needed by
  `fulfilment`; one event is processed independently for each branch order.
- `fulfilment` calls the provider only through `ShippingProviderPort`; the GHTK
  adapter maps provider DTOs to provider-neutral results.
- The webhook handler validates provider authentication and required fields,
  persists an idempotent status event, then returns the provider-required
  acknowledgement. It never accepts a Firebase bearer token as provider proof.
- Notifications are post-commit workflow intents and never payment evidence.

## Complexity Tracking

No constitution violations. The two-module event/facade design is required to
preserve existing ownership and to keep remote GHTK calls outside local
transactions; a new service or message broker is not introduced.

## Post-Design Constitution Check

PASS, subject to the following pre-implementation gates:

- synchronize the clarified automatic GHTK scope with the older API/SRS/
  background-job baseline;
- approve the physical schema/migration mapping and exact role/delegation
  mapping;
- approve GHTK callback authentication, partner credentials, quotas and a
  prepaid/no-COD service contract;
- generate dependency-ordered tasks and run `speckit-analyze` before code.

