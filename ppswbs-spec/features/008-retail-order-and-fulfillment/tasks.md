---
description: "Dependency-ordered implementation tasks for FE-08"
---

# Tasks: Retail Order and Fulfillment

**Date created**: 2026-10-24
**Date updated**: 2026-10-27
**Author**: thinh
**Last updated by**: Thinh

**Input**: Design documents in ppswbs-spec/features/008-retail-order-and-fulfillment/

**Tests**: Required for API, persistence, authorization, payment, loyalty,
provider, frontend state, migration, and module-boundary changes.

**Path convention**: Repository-relative. Backend uses
features/feature/{controller,dto,dao,entity,service,facade}; UI uses the separate
React + Vite client, never Thymeleaf/htmx.

## Phase 1: Setup and decision gates

- [x] T001 Synchronize automatic GHTK V1 and pickup-only production fallback in documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/05-system-fuctionalities/02-external-api-inventory.md and documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/05-system-fuctionalities/03-background-job-inventory.md.
- [x] T002 [P] Align FE-08 route/provider maturity with the feature contracts in ppswbs-spec/APIs.md.
- [x] T003 Resolve and approve FE-06 benefit preview, canonical `BenefitSelection` (`choice`, `voucherCode`, `requestedPoints`), decimal-string money/points, accepted-terms acknowledgement, quote reference, zero-total acceptance, and typed benefit errors in ppswbs-spec/features/008-retail-order-and-fulfillment/contracts/retail-order-and-fulfillment.openapi.yaml; lifecycle commands remain internal to loyaltypromotion::facade.
- [ ] T004 Record pinned React/Vite/TypeScript versions, router, state/auth integration, API origin, and test tools in ppswbs-spec/features/008-retail-order-and-fulfillment/implementation-readiness.md.
- [x] T005 Record approved backend module relocation, schema, role/delegation mapping, facade graph, and migration allocation for the approved cart-first slice in ppswbs-spec/features/008-retail-order-and-fulfillment/implementation-readiness.md.
- [ ] T006 Record approved GHTK callback authentication, credentials channel, quotas, timeout/retry budget, and prepaid/no-COD mapping in ppswbs-spec/features/008-retail-order-and-fulfillment/implementation-readiness.md.

**Checkpoint**: T001–T006 reviewed; prototypes and mocks close no production gate.

## Phase 2: Foundational prerequisites

- [ ] T007 Create the approved frontend foundation in ppswbs_frontend/package.json, ppswbs_frontend/package-lock.json, ppswbs_frontend/vite.config.ts, and ppswbs_frontend/src/app/App.tsx after T004.
- [ ] T008 [P] Create the typed REST client and bearer/error/correlation mapping in ppswbs_frontend/src/features/retail-order/api/retailOrderApi.ts and ppswbs_frontend/src/features/retail-order/model/contracts.ts.
- [x] T009 Create separately detected modules in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/package-info.java and ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/fulfilment/package-info.java.
- [x] T010 [P] Define minimal boundaries in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/facade/RetailOrderPaid.java and ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/fulfilment/facade/RetailShipmentStatusView.java.
- [ ] T011 [P] Define typed errors and ownership/branch/delegation guards in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailAuthorization.java, features/fulfilment/service/FulfilmentAuthorization.java, and common/web/ApiErrorCode.java.
- [x] T012 Create `V6__retail_cart.sql` under ppswbs_backend/src/main/resources/db/migration/ for the approved cart-first slice; defer checkout, fulfilment, shipment, event, and retention records to the allocated V7-V9 migrations in implementation-readiness.md.
- [ ] T013 [P] Define provider-neutral GHTK port, deterministic test mock, and fail-closed config in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/fulfilment/facade/ShippingProviderPort.java, features/fulfilment/service/MockShippingProviderAdapter.java, and configuration/GhtkProperties.java.
- [ ] T014 Verify separate modules and the facade/event graph, including readyringsales to loyaltypromotion::facade, in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/ModuleArchitectureTest.java.

## Phase 3: User Story 1 — Prepare a retail purchase (Priority: P1) — MVP

**Goal**: An active Member manages an own branch-bound cart without purchase effects.

**Independent Test**: Cart changes work while order, payment, stock hold, and
benefit reservation counts remain unchanged.

- [x] T015 [P] [US1] Write ownership, branch, stale-version, maximum-50-line, and quantity-1–99 tests in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/CartJourneyTest.java.
- [ ] T016 [P] [US1] Write loading, empty, validation, conflict, retry, and grouping tests in ppswbs_frontend/src/features/retail-order/pages/CartPage.test.tsx.
- [x] T017 [P] [US1] Create RetailCart and CartLine mappings in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/entity/RetailCart.java and CartLine.java.
- [x] T018 [US1] Implement member/catalogue-facade cart commands in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/CartService.java and dao/RetailCartRepository.java; branch changes must not rewrite old lines.
- [x] T019 [US1] Implement validated cart DTOs/endpoints in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/dto/CartDtos.java and controller/CartController.java.
- [ ] T020 [US1] Implement branch selection and cart UI in ppswbs_frontend/src/features/retail-order/pages/CataloguePage.tsx, pages/CartPage.tsx, and components/BranchCartGroup.tsx.

## Phase 4: User Story 2 — Checkout and pay (Priority: P1)

**Goal**: Atomically create all branch orders, reserve FE-06 benefits, and apply
one verified grouped payment exactly once.

**Independent Test**: Mixed branches, voucher/points, rollback, zero-total,
VNPay, duplicate callback, deadlines, expiry, and late evidence create no partial
or duplicate source/loyalty effects.

- [ ] T021 [P] [US2] Write MySQL atomic checkout/concurrency tests in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/GroupedCheckoutIntegrationTest.java.
- [ ] T022 [P] [US2] Write FE-06 G-03 tests for preview invalidation and changed terms, reserve, paid/zero settle-then-grant, authoritative unpaid failure/expiry release with cart restoration, duplicate receipt, and unresolved evidence in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/LoyaltyRetailOwnerIntegrationTest.java.
- [ ] T023 [P] [US2] Write payment/idempotency tests rejecting browser success, bad signature, mismatched amount/currency, late evidence, key conflicts, and self-cancellation after `PAYMENT_INITIATED`; prove that `ACCEPTED_UNPAID` cancellation releases inventory/benefits and restores the cart in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/RetailPaymentIntegrationTest.java.
- [ ] T024 [P] [US2] Write React voucher/points/recalculation/acknowledgement/totals/payment/error tests, including canonical FE-06 `BenefitSelection` and decimal-string money/point rendering, in ppswbs_frontend/src/features/retail-order/pages/CheckoutPage.test.tsx.
- [ ] T025 [P] [US2] Create GroupedCheckout, RetailOrder, OrderItem, and CheckoutHold mappings with immutable snapshots, payment lifecycle/`paymentInitiatedAt`, and 10/15-minute deadlines under ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/entity/.
- [ ] T026 [US2] Implement trusted preview and MANDATORY same-transaction reserveAcceptedTransaction through loyaltypromotion::facade in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/LoyaltyCheckoutAdapter.java; shipping is excluded from discount/earn base.
- [ ] T027 [US2] Implement all-or-nothing grouping, catalogue/quote/benefit revalidation, snapshots, and replay in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailCheckoutService.java.
- [ ] T028 [US2] Implement one verified VNPay attempt, approved zero-total internal acceptance, and pre-initiation cancellation guard in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailPaymentService.java.
- [ ] T029 [US2] Implement source-serialized settle/grant/release/finish-ineligible outcomes, including pre-initiation cancellation and authoritative unpaid failure/expiry release with cart restoration, in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/LoyaltyOutcomeAdapter.java.
- [ ] T030 [US2] Implement source-locked expiry and late-evidence review in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailHoldExpiryJob.java.
- [ ] T031 [US2] Implement T003-approved cart-backed checkout, payment, zero-total, and pre-initiation cancellation DTOs/endpoints in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/dto/CheckoutDtos.java and controller/CheckoutController.java.
- [ ] T032 [US2] Integrate voucher/points controls, full breakdown, zero-total confirmation, and pre-initiation cancellation in ppswbs_frontend/src/features/retail-order/pages/CheckoutPage.tsx and components/CheckoutTotals.tsx, requiring reconfirmation after changes.

## Phase 5: User Story 3 — Choose fulfilment and track delivery (Priority: P1)

**Goal**: Pickup or GHTK per branch with server-issued pre-order quotes and safe recovery.

**Independent Test**: Recipient limits, quote binding/expiry/change, per-branch
failure, no-access fallback, draft preservation, and own tracking satisfy QS-06–08/22–25.

- [ ] T033 [P] [US3] Write quote/provider tests for package data, fee components, timeout, binding, and expiry in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/fulfilment/ShippingQuoteContractTest.java.
- [ ] T034 [P] [US3] Write React recovery tests for in-memory recipient draft, contact-Staff guidance, affected-branch pickup, unaffected branches, fee difference, and reconfirmation in ppswbs_frontend/src/features/retail-order/pages/CheckoutFulfilment.test.tsx.
- [ ] T035 [P] [US3] Consume FE-02 eligibility and authoritative package data in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/CatalogueShippingProfileAdapter.java.
- [ ] T036 [P] [US3] Create bound ShippingQuote and immutable FulfilmentSelection mappings in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/entity/ShippingQuote.java and FulfilmentSelection.java.
- [ ] T037 [US3] Implement provider-neutral quote and fail-closed capability in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/fulfilment/service/ShippingQuoteService.java and GhtkShippingProviderAdapter.java.
- [ ] T038 [US3] Implement quote DTOs/endpoints, exact recipient limits, and the OpenAPI conditional rule that a GHTK checkout choice includes both `shippingQuoteId` and recipient data in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/dto/FulfilmentDtos.java and controller/FulfilmentQuoteController.java.
- [ ] T039 [US3] Revalidate quote IDs and freeze selection in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/FulfilmentSelectionService.java; never trust client fees or create orders during quote.
- [ ] T040 [US3] Implement fulfilment choice, recipient/quote/recovery states, and normalized tracking in ppswbs_frontend/src/features/retail-order/components/FulfilmentSelector.tsx and pages/OrderTrackingPage.tsx.

## Phase 6: User Story 4 — Process and hand over orders (Priority: P1)

**Goal**: Authorized branch actors prepare paid orders and record pickup/carrier custody.

**Independent Test**: Redelivery, ambiguous create, duplicate partner/webhook,
wrong branch/state, pickup, and handoff create no duplicate terminal effect.

- [ ] T041 [P] [US4] Write GHTK create/retry/webhook/reconciliation tests in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/fulfilment/GhtkLifecycleIntegrationTest.java.
- [ ] T042 [P] [US4] Write branch/delegation/preparation/pickup/handoff tests in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/fulfilment/FulfilmentOperationsTest.java.
- [ ] T043 [P] [US4] Create shipment, status-event, and evidence mappings in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/fulfilment/entity/GhtkShipment.java, CarrierStatusEvent.java, and FulfilmentEvidence.java.
- [ ] T044 [US4] Implement durable paid-event consumption and same-partner-ID retry in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/fulfilment/service/RetailShipmentCreationService.java.
- [ ] T045 [US4] Implement reconciliation, authenticated callback, deduplication, and non-regressing mapping in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/fulfilment/service/GhtkReconciliationJob.java and controller/GhtkWebhookController.java.
- [ ] T046 [US4] Implement authorized preparation, pickup, and handoff in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/fulfilment/service/FulfilmentOperationsService.java and controller/FulfilmentOperationsController.java.
- [ ] T047 [US4] Implement scoped operations UI in ppswbs_frontend/src/features/retail-order/pages/RetailOperationsPage.tsx and components/OrderOperationActions.tsx.

## Phase 7: User Story 5 — View order history/details (Priority: P2)

**Goal**: Members see only own frozen purchase and permitted fulfilment facts.

**Independent Test**: Own reads work; foreign references reveal nothing; raw
callbacks, secrets, and expired recipient PII are absent.

- [ ] T048 [P] [US5] Write ownership/snapshot/status/redaction tests in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/RetailOrderQueryTest.java.
- [ ] T049 [P] [US5] Write React history/detail states in ppswbs_frontend/src/features/retail-order/pages/OrderHistoryPage.test.tsx and OrderDetailPage.test.tsx.
- [ ] T050 [US5] Implement ownership-scoped projections/endpoints in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailOrderQueryService.java, dto/RetailOrderView.java, and controller/RetailOrderQueryController.java.
- [ ] T051 [US5] Implement history/detail views in ppswbs_frontend/src/features/retail-order/pages/OrderHistoryPage.tsx and OrderDetailPage.tsx.
- [ ] T052 [US5] Implement/test 30-day recipient obscuring in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/fulfilment/service/RecipientDataRetentionJob.java and ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/fulfilment/RecipientRetentionTest.java.

## Phase 8: Polish and cross-cutting verification

- [ ] T053 [P] Add post-commit notification intents in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailNotificationPublisher.java and features/fulfilment/service/FulfilmentNotificationPublisher.java; email is never proof.
- [ ] T054 [P] Validate OpenAPI, Bean Validation, canonical FE-06 selection/decimal-string mapping, errors, and frontend mapping in ppswbs-spec/features/008-retail-order-and-fulfillment/contracts/retail-order-and-fulfillment.openapi.yaml and ppswbs_frontend/src/features/retail-order/model/contracts.ts.
- [ ] T055 [P] Add MySQL empty/upgrade migration verification in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/RetailMigrationTest.java.
- [ ] T056 Run module/backend/frontend/API checks and record exact evidence in ppswbs-spec/features/008-retail-order-and-fulfillment/verification.md; prototype checks are not production evidence.
- [ ] T057 Execute QS-01–QS-27, including a timed QS-03 checkout-review walkthrough proving the under-three-minute target excluding external payment, and record environment, result, failure, duration, and mock/live status in ppswbs-spec/features/008-retail-order-and-fulfillment/verification.md.
- [ ] T058 Record final security/scope review in ppswbs-spec/features/008-retail-order-and-fulfillment/verification.md.

## Dependencies and execution order

- Phase 1 gates Phase 2: T003 blocks loyalty APIs, T004 frontend, T005 backend,
  and T006 live GHTK.
- The approved cart-first slice may proceed through US1 after T005, the
  `readyringsales` module, and T012. It does not authorize checkout, payment,
  inventory holds, GHTK, or Staff/Manager operations before their deferred
  migration and boundary gates are complete.
- Phase 2 blocks every story. US1 is the MVP. US2 depends on US1 and T003.
- US3 quote work may proceed after Phase 2, but checkout freezing integrates with US2.
- US4 depends on US2 paid orders and US3 GHTK choice. US5 depends on US2.
- Phase 8 follows all selected stories.

## Parallel opportunities

- T001/T002, T008/T010/T011/T013, and each story's [P] tests touch separate files.
- US3 quote work can proceed beside US1 after Phase 2 but cannot bypass US2 acceptance.
- US5 UI may use contract fixtures while US4 is built; fixtures prove no backend integration.

## Implementation strategy

1. Complete gates and foundation.
2. Deliver US1 as the independently testable MVP.
3. Add US2 loyalty/payment, US3 quotation, US4 operations, and US5 reads.
4. Complete Phase 8 before claiming readiness.

## Reality-check constraints

- A voucher widget does not prove points/quota atomicity.
- A mock GHTK label does not prove custody.
- A browser VNPay return does not prove payment.
- A timer cannot release benefits while payment is unresolved.
