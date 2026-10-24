# Implementation Plan: Retail Order and Fulfillment

**Branch**: `008-retail-order-and-fulfillment` | **Date**: 2026-10-23 | **Updated**: 2026-10-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `features/008-retail-order-and-fulfillment/spec.md`

## Summary

Implement FE-08 as a bounded retail-sales and fulfilment capability. The
`readyringsales` module owns Member cart/grouped-checkout/order facts and the
`fulfilment` module owns preparation, GHTK shipment creation, status
synchronization, and physical handoff evidence. A grouped checkout is paid
once, then each paid branch order can start its own shipment creation after
commit. The GHTK adapter is contract-first; a mock provider is permitted only
in development and automated tests while production fails closed to pickup
until partner access, callback security, and prepaid/no-COD service are approved.
The 2026-10-24 clarification makes automatic GHTK integration official V1
scope and defines pickup-only recovery when production carrier access is absent.

## Technical Context

**Language/Version**: Java 21 for the backend; TypeScript with React + Vite for
the separate frontend. Exact frontend dependency versions must be pinned by the
approved frontend-foundation task before scaffold because no frontend package or
lockfile currently exists.

**Primary Dependencies**: Spring Boot project baseline, Spring Modulith 1.3.1,
Spring Data JPA, Flyway, MySQL and Firebase Admin SDK in the backend; React and
Vite in the frontend. No SSR, Thymeleaf application page, htmx, client state
library, router, UI kit or frontend test framework is added by this plan without
an approved compatible version and foundation decision.

**Storage**: MySQL through JPA/Spring Data; append-only Flyway migrations

**Testing**: JUnit/Spring Boot tests, Spring MVC REST contract tests, provider
mock contract tests, MySQL migration/integration tests, Spring Modulith
`ApplicationModules.verify()`, plus frontend build/type checks and UI/API-state
tests using the tools selected by the approved frontend foundation.

**Target Platform**: Separate React + Vite client-side web application consuming
versioned Spring Boot REST endpoints; server-to-server GHTK integration only

**Project Type**: Separate React frontend and Spring Modulith modular-monolith
backend in the existing repository

**Performance Goals**: Preserve the approved Member checkout journey target of
under three minutes excluding external payment time; external calls must use
bounded timeouts and never hold a business transaction open while waiting.

**Constraints**: No raw SQL in application code; no cross-module entity,
repository, controller, DTO, or adapter access; Firebase proves identity only;
GHTK credentials remain server-side; payment/hold/order/shipment operations
are retry-safe; no COD, refund, return-resolution, accounting, or executive
reporting scope. Frontend route guards and hidden controls never replace backend
authorization. The browser never receives GHTK credentials or calls GHTK.

**Scale/Scope**: Five FE-08 user stories covering Member cart/marketplace-style
grouped checkout/order history, per-branch Staff/Manager preparation and
handoff, catalogue shipping metadata, one grouped VNPay payment, and one GHTK
carrier integration.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle/gate | Result | Plan evidence |
| --- | --- | --- |
| Module integrity and data ownership | PASS with migration gate | `readyringsales` owns retail order facts; `fulfilment` owns shipment/custody; events/facades only. Both move under `features/` with the mandated package layout and must remain separately detected by Spring Modulith. Direct dependency facades still outside `features/` require an approved compatibility/relocation decision before implementation. |
| JPA/Flyway persistence | PASS | Logical model is separated from physical migration; all schema work uses JPA and append-only Flyway. |
| Identity and sensitive-data boundary | PASS | Firebase UID is verified; Mland roles/status authorize actions; provider secrets, raw callbacks and unnecessary recipient data are excluded. |
| Versioned REST and safe errors | PASS | Feature OpenAPI uses `/api/v1`, plural kebab-case, DTO projections, validation and correlation-linked safe errors. |
| Separate frontend baseline | PASS with foundation gate | Application UI is React + Vite CSR in `ppswbs_frontend`; backend serves REST and HTML email only. Frontend versions, router, state approach and test tools must be approved before scaffold. |
| UI state and authorization | PASS | React handles loading, empty, validation, pending, success, error and recovery states from the REST contract; backend rechecks Member ownership and branch/action scope on every protected operation. |
| Evidence-based verification | PASS | Quickstart and tasks must cover backend API/migration/idempotency/provider mock/authorization/module verification and frontend build/UI/API-state verification. |
| Provider/lifecycle decisions | PASS with go-live gates | Automatic GHTK is approved V1 scope. Without approved production access, GHTK is disabled with contact-Staff guidance and pickup remains available; mock responses stay development/test only. Exact provider authentication, credentials, quotas and prepaid/no-COD service mapping remain integration gates. |

No constitution violation is accepted as an exception. Implementation remains
gated by (1) frontend-foundation versions/tools, (2) the package-layout migration
for touched modules and dependencies, and (3) synchronization of Report 3 and
cross-feature inventories with the now-approved automatic GHTK V1 decision.
No implementation may preserve contradictory manual-only behavior.

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
├── features/
│   ├── readyringsales/
│   │   ├── controller/  # Member cart, checkout and order REST transport
│   │   ├── dto/         # request/response DTOs matching the OpenAPI contract
│   │   ├── dao/         # Spring Data repositories owned by retail sales
│   │   ├── entity/      # cart, checkout, order, item and hold persistence
│   │   ├── service/     # use cases, transactions, jobs and event handlers
│   │   └── facade/      # named boundary types/events for other features
│   └── fulfilment/
│       ├── controller/  # Staff operations and provider webhook REST transport
│       ├── dto/         # provider-neutral HTTP and operation DTOs
│       ├── dao/         # shipment/status/handoff repositories
│       ├── entity/      # shipment, event and custody persistence
│       ├── service/     # quote, creation, retry, reconciliation and handoff
│       └── facade/      # named shipment/status boundary
├── configuration/       # approved technical configuration only
└── common/              # technical kernel only; no FE-08 business entities

../ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/
├── features/readyringsales/ # checkout, ownership, hold and payment tests
├── features/fulfilment/     # provider, retry, webhook, status and handoff tests
└── ...                  # existing module and migration verification

../ppswbs_frontend/
├── package.json         # created only by the approved foundation task
├── vite.config.*
├── src/
│   ├── app/             # application bootstrap, routing and approved providers
│   ├── features/retail-order/
│   │   ├── api/         # FE-08 REST client and contract mapping
│   │   ├── components/  # branch groups, cart lines, totals and tracking status
│   │   ├── pages/       # catalogue handoff, cart, checkout, orders and Staff views
│   │   └── model/       # frontend-only view types/state transitions
│   └── shared/          # technical client/layout primitives only
└── tests/               # structure follows the approved frontend test tool
```

**Structure Decision**: Relocate/implement `readyringsales` and `fulfilment` as
separate modules under the Constitution v3 `features/` grouping while preserving
their business ownership. Do not create a new service or shared business
package. `readyringsales` publishes a minimal paid-order event; `fulfilment`
consumes it idempotently without importing retail controllers, DTOs, DAOs,
entities or internal services. The current root-level package shells are legacy
evidence, not the target layout.

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

1. Synchronize Report 3 and the cross-feature API/background-job inventories
   with the approved automatic GHTK V1 scope.
2. Approve the React/Vite foundation (pinned versions, route/state/auth
   integration, API base/origin handling and test tools) without adding an
   unapproved UI kit or state library.
3. Confirm the `features/` module relocation, facade dependencies, module
   detection and physical database mapping; add append-only migrations only
   after mapping review.
4. Implement or complete catalogue shipping-profile facade consumption, embed
   FE-06 benefit preview, and call `loyalty::facade` to reserve the accepted
   benefit in the same transaction that atomically creates the grouped checkout,
   branch orders, inventory holds, and 15-minute benefit hold.
5. Integrate verified VNPay payment events without direct provider coupling;
   make payment/hold/order effects idempotent and call the FE-06 outcome facade
   to settle then grant after verified full payment or zero-total acceptance,
   and to release held benefits after authoritative unpaid expiry.
6. Implement the mock `ShippingProviderPort`, per-branch GHTK fee quotes,
   shipment-create retry using the same partner ID, and normalized shipment
   projections for each branch order.
7. Implement webhook validation/deduplication, status GET reconciliation and
   the Member latest-status view.
8. Implement Staff/Manager preparation, pickup verification and physical GHTK
   handoff evidence with branch/delegation authorization.
9. Implement React pages against the approved REST contract, including loading,
   empty, validation, pending, error, stale-session and retry states; do not copy
   prototype fixtures into production state.
10. Add retention cleanup, notification intents, contract validation, frontend
    build/UI verification and all quickstart evidence.

## Internal and External Boundary Design

- `readyringsales` calls catalogue, member, payment, and `loyalty::facade`
  boundaries only; it does not access their repositories, entities, or internal
  services. This is the FE-06 G-03 retail-owner adapter boundary.
- Before acceptance, `readyringsales` supplies server-priced line facts and
  shipping as a separate non-discountable amount to FE-06 benefit preview. The
  browser may select benefits and acknowledge quoted terms but is never trusted
  for Member ownership, price, discount, points, shipping, or payment facts.
- During grouped-checkout acceptance, `readyringsales` owns the transaction and
  calls `reserveAcceptedTransaction` through `loyalty::facade` with mandatory
  transaction participation. Benefit reservation, all branch orders, inventory
  holds, and source snapshots commit together or roll back together. A changed
  benefit quote returns a reconfirmation result instead of silently changing the
  payable amount.
- Under the source lifecycle guard, a verified full-payment or approved
  zero-total outcome calls `settleAcceptedBenefits` and then
  `grantEarnedPoints` idempotently in the same outcome transaction. A confirmed
  unpaid expiry calls `releaseHeldBenefits` and `finishIneligibleEarn` before
  completing source expiry. Duplicate outcomes replay the committed receipt.
  Ambiguous, late, or conflicting payment evidence remains unresolved for
  reconciliation and must not release, consume, grant, or extend benefits.
- A source-authoritative retail cancellation outcome, if supplied by an
  approved retail cancellation workflow, follows the same FE-06 outcome adapter
  and version guard. This plan does not add refund or cancellation UX and does
  not infer cancellation from a browser action or carrier status.
- A verified grouped-payment projection contains only the paid branch-order
  ID, accepted fulfilment method, version and correlation data needed by
  `fulfilment`; one event is processed independently for each branch order.
- `fulfilment` calls the provider only through `ShippingProviderPort`; the GHTK
  adapter maps provider DTOs to provider-neutral results.
- Shipping quotes are requested against the authenticated Member's branch/cart
  context before any order exists. Checkout acceptance revalidates the current
  cart and the referenced server-issued quote and then creates all branch orders
  or none; a quote endpoint never creates an order or payment attempt. The
  client submits only the quote identifier, never a trusted shipping amount.
- The webhook handler validates provider authentication and required fields,
  persists an idempotent status event, then returns the provider-required
  acknowledgement. It never accepts a Firebase bearer token as provider proof.
- Notifications are post-commit workflow intents and never payment evidence.
- The React client calls Mland REST APIs only. It treats payment browser return
  and visible route state as untrusted until the backend returns verified state.
- Authenticated client state follows the project-wide approved Firebase/token
  transport contract; FE-08 does not invent a second token/session mechanism.
- On quote timeout the React client keeps cart state and the recipient draft in
  current in-memory checkout state, creates no payment attempt, and offers retry
  or explicit pickup for only the affected branch. Recipient drafts are not put
  in browser persistence by this feature.
- If a quote expires or changes, the backend returns a safe stale-quote result;
  React displays the difference/new grouped total and requires confirmation
  before payment initiation.

## Complexity Tracking

No constitution violations. The two-module event/facade design is required to
preserve existing ownership and to keep remote GHTK calls outside local
transactions; a new service or message broker is not introduced.

## Post-Design Constitution Check

CONDITIONALLY PASS for design, but NOT READY FOR IMPLEMENTATION until these
pre-implementation gates are resolved and approved:

- synchronize the approved automatic GHTK scope into the older API/SRS/
  background-job baseline so no active source still mandates manual-only retail
  fulfilment;
- approve pinned React/Vite/TypeScript versions, router, frontend state/auth
  integration, API origin handling and frontend test tools;
- approve migration of touched business modules into `features/` and prove that
  Spring Modulith detects `readyringsales` and `fulfilment` separately rather
  than detecting only the grouping package;
- approve the physical schema/migration mapping and exact role/delegation
  mapping;
- approve GHTK callback authentication, partner credentials, quotas and a
  prepaid/no-COD service contract;
- regenerate dependency-ordered tasks for Constitution v3 and run
  `speckit-analyze` before code.

