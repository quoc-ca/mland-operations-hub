---
description: "Dependency-ordered implementation tasks for FE-08"
---

# Tasks: Retail Order and Fulfillment

**Date created**: 2026-10-24
**Date updated**: 2026-11-26
**Author**: thinh
**Last updated by**: Thinh

**Input**: Design documents in ppswbs-spec/features/008-retail-order-and-fulfillment/

**Tests**: Required for API, persistence, authorization, payment, loyalty,
provider, frontend state, migration, and module-boundary changes.

**Path convention**: Repository-relative. Backend uses
features/feature/{controller,dto,dao,entity,service,facade}; UI uses the separate
React + Vite client (JavaScript/JSX), never Thymeleaf/htmx.

**Status (2026-11-13)**: the task list is reconciled with the code on `develop`
(commit `a3187b73`). File paths now name the real files; the original plan named
TypeScript files and service names that the implementation did not use. 56 of 70
tasks are done. The open tasks are frontend unit tests, live GHTK shipment
creation and callbacks (V1 uses a mock carrier label), recipient-data retention,
notifications, and the final verification records. They are marked
**Deferred** or **Open** with the reason.

`[x]` done and tested · `[ ]` open, deferred or partial (the reason is on the task).

## Phase 1: Setup and decision gates

- [x] T001 Synchronize automatic GHTK V1 and pickup-only production fallback in documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/05-system-fuctionalities/02-external-api-inventory.md and documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/05-system-fuctionalities/03-background-job-inventory.md.
- [x] T002 [P] Align FE-08 route/provider maturity with the feature contracts in ppswbs-spec/APIs.md.
- [x] T003 Resolve and approve FE-06 benefit preview, canonical `BenefitSelection` (`choice`, `voucherCode`, `requestedPoints`), decimal-string money/points, accepted-terms acknowledgement, quote reference, zero-total acceptance, and typed benefit errors in ppswbs-spec/features/008-retail-order-and-fulfillment/contracts/retail-order-and-fulfillment.openapi.yaml; lifecycle commands remain internal to loyaltypromotion::facade.
- [x] T004 Record pinned frontend versions and tools in ppswbs-spec/features/008-retail-order-and-fulfillment/implementation-readiness.md: React 19.3.1, react-router 7.18.5, Vite 7.3.7, Vitest 4.1.12, JavaScript/JSX (no TypeScript), shared `useResource`/`useCommand` state, bearer session from `shared/auth`.
- [x] T005 Record approved backend module relocation, schema, role/delegation mapping, facade graph, and migration allocation for the approved cart-first slice in ppswbs-spec/features/008-retail-order-and-fulfillment/implementation-readiness.md.
- [ ] T006 **Deferred (live GHTK)**: GHTK callback authentication, credentials channel, quotas and retry budget for live shipment creation. V1 uses the real GHTK fee API for quotes (`GhtkQuoteProperties`, `RestClientGhtkFeeTransport`) and a mock carrier label for shipments; prepaid/no-COD is enforced.

**Checkpoint**: T001–T005 reviewed; T006 gates only live GHTK shipment creation.

## Phase 2: Foundational prerequisites

- [x] T007 Frontend foundation in ppswbs_frontend/package.json, ppswbs_frontend/vite.config.js, and ppswbs_frontend/src/app/App.jsx.
- [x] T008 [P] REST client and bearer/error mapping in ppswbs_frontend/src/features/retail-order/infrastructure/retailApi.js and ppswbs_frontend/src/features/retail-order/application/useRetailApi.js.
- [x] T009 Separately detected modules in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/package-info.java and ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/fulfilment/package-info.java.
- [x] T010 [P] Minimal boundaries in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/facade/RetailOrderPaid.java and ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/fulfilment/facade/RetailShipmentStatusView.java.
- [x] T011 [P] Typed errors and ownership/branch/delegation guards in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailAuthorization.java, controller/RetailExceptionHandler.java and dto/RetailErrorResponse.java (tested by RetailAuthorizationTest, RetailExceptionHandlerPrecedenceTest).
- [x] T012 Cart-first migration ppswbs_backend/src/main/resources/db/migration/V13__retail_cart.sql; later FE-08 migrations: V14, V54–V59, V76–V85, V91–V95, V111, V112, V124.
- [x] T013 [P] Provider-neutral shipping quote port with local mock and fail-closed GHTK config in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/facade/RetailShippingQuotePort.java, service/LocalRetailShippingQuoteAdapter.java, service/GhtkRetailShippingQuoteAdapter.java and service/GhtkQuoteProperties.java.
- [x] T014 Verify separate modules and the facade graph in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/ModulithArchitectureTests.java.

## Phase 3: User Story 1 — Prepare a retail purchase (Priority: P1) — MVP

**Goal**: An active Member manages an own branch-bound cart without purchase effects.

**Independent Test**: Cart changes work while order, payment, stock hold, and
benefit reservation counts remain unchanged.

- [x] T015 [P] [US1] Ownership, branch, stale-version, 50-line and quantity tests in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/CartJourneyTest.java, CartServiceTest.java and controller/CartControllerTest.java.
- [ ] T016 [P] [US1] **Open**: React cart tests (loading, empty, validation, conflict, retry, grouping) for ppswbs_frontend/src/features/retail-order/ui/cart/MemberCartPage.jsx.
- [x] T017 [P] [US1] RetailCart and CartLine mappings in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/entity/RetailCart.java and CartLine.java.
- [x] T018 [US1] Cart commands in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/CartService.java and dao/RetailCartRepository.java; branch changes never rewrite old lines.
- [x] T019 [US1] Cart DTOs/endpoints in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/dto/CartDtos.java and controller/CartController.java.
- [x] T020 [US1] Branch-grouped cart UI with auto-applied best voucher price in ppswbs_frontend/src/features/retail-order/ui/cart/MemberCartPage.jsx and ui/cart-shopee.css.

## Phase 4: User Story 2 — Checkout and pay (Priority: P1)

**Goal**: Atomically create all branch orders, reserve FE-06 benefits, and apply
one verified grouped payment exactly once.

**Independent Test**: Mixed branches, voucher/points, rollback, zero-total,
VNPay, duplicate callback, deadlines, expiry, and late evidence create no partial
or duplicate source/loyalty effects.

- [x] T021 [P] [US2] MySQL atomic checkout/concurrency tests in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/GroupedCheckoutIntegrationTest.java and RetailCatalogueMySqlIntegrationTest.java (`twoMembersCannotBothAcceptTheLastStock`, `changedCartRejectsAcceptanceWithoutCreatingOrderOrHold`).
- [x] T022 [P] [US2] FE-06 G-03 tests (changed terms rollback, reserve, settle-then-grant, release once on cancel/expiry, cart restoration) in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/RetailCatalogueMySqlIntegrationTest.java and RetailCartRestorationTest.java.
- [x] T023 [P] [US2] Payment/idempotency tests (wrong amount, late callback, concurrent callbacks, cancellation after initiation keeps the hold) in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/RetailCatalogueMySqlIntegrationTest.java, service/RetailPaymentEvidenceServiceTest.java and service/VnpayRetailPaymentAdapterTest.java.
- [ ] T024 [P] [US2] **Partial**: voucher/points tests exist in ppswbs_frontend/src/features/retail-order/tests/RetailBenefitPanel.test.jsx; tests for totals, payment method and errors on MemberCheckoutPage.jsx are open.
- [x] T025 [P] [US2] GroupedCheckout, RetailOrder, OrderItem, CheckoutHold, RetailVariantHold mappings with snapshots and deadlines in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/entity/.
- [x] T026 [US2] Trusted preview and same-transaction reserve through loyaltypromotion::facade in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailCheckoutQuotePreviewService.java and RetailCheckoutAcceptanceService.java; shipping is excluded from the discount/earn base.
- [x] T027 [US2] All-or-nothing grouping, revalidation, snapshots and replay in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailCheckoutAcceptanceService.java, RetailCheckoutQuoteValidator.java and RetailCheckoutService.java (local profile).
- [x] T028 [US2] One verified VNPay attempt, zero-total acceptance and pre-initiation cancellation guard in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailPaymentService.java, VnpayRetailPaymentAdapter.java and RetailCheckoutCancellationService.java.
- [x] T029 [US2] Settle/grant/release/finish-ineligible outcomes with cart restoration in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailPaymentEvidenceService.java, RetailCheckoutCancellationService.java and RetailBranchPartService.java.
- [x] T030 [US2] Expiry and late-evidence review in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailUnstartedExpiryJob.java, RetailConfirmedUnpaidExpiryJob.java, RetailQueryReconciliationService.java and ppswbs_frontend/src/features/retail-order/ui/manager/ManagerPaymentReconciliationPage.jsx.
- [x] T031 [US2] Checkout, payment, zero-total and cancellation DTOs/endpoints in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/dto/CheckoutDtos.java, controller/CheckoutController.java and controller/RetailCheckoutCancellationController.java.
- [x] T032 [US2] Voucher picker, points, automatic re-quote, full breakdown and payment method in ppswbs_frontend/src/features/retail-order/ui/checkout/MemberCheckoutPage.jsx, RetailBenefitPanel.jsx and VoucherPicker.jsx.

## Phase 5: User Story 3 — Choose fulfilment and track delivery (Priority: P1)

**Goal**: Pickup or GHTK per branch with server-issued pre-order quotes and safe recovery.

**Independent Test**: Recipient limits, quote binding/expiry/change, per-branch
failure, no-access fallback, draft preservation, and own tracking satisfy QS-06–08/22–25.

- [x] T033 [P] [US3] Quote/provider tests in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RestClientGhtkFeeTransportTest.java, service/LocalRetailShippingQuoteAdapterTest.java and RetailCheckoutQuoteTest.java.
- [ ] T034 [P] [US3] **Open**: React tests for the address picker, quote expiry notice and per-branch pickup fallback.
- [x] T035 [P] [US3] FE-02 eligibility and package data in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/CatalogueRetailAdapter.java and LocalCatalogueFacts.java.
- [x] T036 [P] [US3] Bound shipping quote and frozen selection in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/entity/RetailShippingQuote.java, RetailCheckoutQuote.java and RetailCheckoutQuoteBranch.java.
- [x] T037 [US3] Provider-neutral quote with fail-closed capability in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/GhtkRetailShippingQuoteAdapter.java and DurableShippingQuoteWriter.java.
- [x] T038 [US3] Quote DTOs/endpoints and recipient rules in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/dto/CheckoutDtos.java and controller/CheckoutController.java.
- [x] T039 [US3] Quote revalidation and frozen selection in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailCheckoutQuoteValidator.java; client fees are never trusted.
- [x] T040 [US3] Fulfilment choice, saved-address picker and tracking in ppswbs_frontend/src/features/retail-order/ui/checkout/MemberCheckoutPage.jsx and ui/orders/MemberOrdersPage.jsx.

## Phase 6: User Story 4 — Process and hand over orders (Priority: P1)

**Goal**: Authorized branch actors prepare paid orders and record pickup/carrier custody.

**Independent Test**: Redelivery, ambiguous create, duplicate partner/webhook,
wrong branch/state, pickup, and handoff create no duplicate terminal effect.

- [ ] T041 [P] [US4] **Partial**: mock shipment lifecycle is tested in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/RetailShipmentLifecycleTest.java, service/RetailShipmentCreationServiceTest.java and service/RetailOrderShipmentFulfilmentSyncTest.java; live GHTK create/retry/webhook tests are deferred with T006.
- [x] T042 [P] [US4] Branch, delegation, pickup and handoff tests in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/StaffRetailShipmentServiceTest.java, service/RetailAuthorizationTest.java and RetailCatalogueMySqlIntegrationTest.java (`pickupRequiresPaidOrderAndAssignedStaff...`).
- [x] T043 [P] [US4] Shipment, status history and evidence mappings in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/entity/RetailShipment.java, RetailShipmentStatusHistory.java and ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/fulfilment/entity/PickupEvidence.java.
- [x] T044 [US4] Paid-order shipment creation, one per branch order, in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailShipmentCreationService.java (mock carrier label).
- [ ] T045 [US4] **Deferred (live GHTK)**: carrier reconciliation job and authenticated GHTK callback. Staff record status changes by hand in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/StaffRetailShipmentService.java.
- [x] T046 [US4] Pickup, cash at counter and shipment transitions in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/fulfilment/service/PickupOperationsService.java, ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailCashCollectionService.java and StaffRetailShipmentService.java.
- [x] T047 [US4] Staff order desk (awaiting cash, awaiting pickup, to ship, in transit, history) in ppswbs_frontend/src/features/retail-order/ui/manager/StaffRetailOrdersPage.jsx.

## Phase 7: User Story 5 — View order history/details (Priority: P2)

**Goal**: Members see only own frozen purchase and permitted fulfilment facts.

**Independent Test**: Own reads work; foreign references reveal nothing; raw
callbacks, secrets, and expired recipient PII are absent.

- [x] T048 [P] [US5] Ownership/snapshot tests in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailOrderQueryServiceTest.java, controller/RetailOrderQueryControllerTest.java and RetailCatalogueMySqlIntegrationTest.java (`orderHistoryReturnsOnlyOwnersSavedItemsAndAmounts`).
- [ ] T049 [P] [US5] **Open**: React tests for ppswbs_frontend/src/features/retail-order/ui/orders/MemberOrdersPage.jsx.
- [x] T050 [US5] Ownership-scoped projections/endpoints in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailOrderQueryService.java, dto/RetailOrderHistoryView.java and controller/RetailOrderQueryController.java.
- [x] T051 [US5] Order list with status tabs, order code, search and inline detail in ppswbs_frontend/src/features/retail-order/ui/orders/MemberOrdersPage.jsx.
- [ ] T052 [US5] **Open**: 30-day recipient obscuring job and test.

## Phase 8: Polish and cross-cutting verification

- [ ] T053 [P] **Open**: post-commit notification intents for retail orders and shipments; email is never proof.
- [ ] T054 [P] **Partial** (2026-11-20: payment link, cancellation in PAYMENT_INITIATED, refusal codes, productId, paymentDeadline/paidAt/completedAt, READY_FOR_CARRIER and the carrier endpoints added): revalidate retail-order-and-fulfillment.openapi.yaml against the current DTOs (order code, discount share, per-branch cancellation endpoint, counter cash).
- [ ] T055 [P] **Partial**: every MySQL integration test migrates an empty `ppswbs_test` schema through V130 with Flyway, and the dev database upgraded to V124; there is no dedicated RetailMigrationTest.
- [ ] T056 **Partial**: evidence from 2026-11-13 is recorded in ppswbs-spec/features/008-retail-order-and-fulfillment/verification.md; a full module/backend/frontend run is still to record.
- [ ] T057 **Open**: execute QS-01–QS-27 with the timed QS-03 walkthrough and record them in verification.md.
- [ ] T058 **Open**: final security/scope review in verification.md.

## Dependencies and execution order

- Phase 1 gates Phase 2: T003 blocks loyalty APIs, T004 frontend, T005 backend,
  and T006 live GHTK.
- Phase 2 blocks every story. US1 is the MVP. US2 depends on US1 and T003.
- US3 quote work may proceed after Phase 2, but checkout freezing integrates with US2.
- US4 depends on US2 paid orders and US3 GHTK choice. US5 depends on US2.
- Phase 8 follows all selected stories.

## Reality-check constraints

- A voucher widget does not prove points/quota atomicity.
- A mock GHTK label does not prove custody.
- A browser VNPay return does not prove payment.
- A timer cannot release benefits while payment is unresolved.

## Phase 9: Convergence

- [x] T059 [P] MySQL tests for held quantities, guard rows, concurrent holds, release and settlement in ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/RetailBranchHoldIT.java, service/RetailAvailabilityGuardServiceTest.java and RetailCatalogueMySqlIntegrationTest.java (`concurrentGuardedReservationsCannotBothHoldTheSameStock`).
- [x] T060 Guard and hold schema in ppswbs_backend/src/main/resources/db/migration/V55__retail_variant_holds.sql and V112__retail_branch_holds.sql with ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/entity/RetailVariantAvailabilityGuard.java, RetailBranchAvailabilityGuard.java and RetailVariantHold.java.
- [x] T061 Holds are created, released, expired and settled through FE-02 `AvailabilityGuardPort.withBranchGuard` in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailAvailabilityGuardService.java and RetailVariantHoldLifecycleService.java.
- [x] T062 `readSaleFacts` for cart/preview only; `withLockedHistoricalBranchContext` and `recordAcceptedSale` at acceptance and settlement in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailCheckoutAcceptanceService.java and RetailBranchPartService.java.

## Phase 10: Session 2026-11 additions

- [x] T063 Cash at the counter for store pickup (FR-08-045/046): ppswbs_backend/src/main/resources/db/migration/V111__retail_counter_cash_payment.sql, ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailCashCollectionService.java, controller/RetailCounterCashController.java; Staff enter the amount received and the change is computed.
- [x] T064 Order display code `ML` + yyMMdd + id prefix (FR-08-050/051) in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailOrderCodes.java and ppswbs_frontend/src/features/retail-order/ui/orderCode.jsx.
- [x] T065 Discount split per branch: voucher share over eligible lines, points over the rest, largest-remainder rounding with no 1 đ drift, in ppswbs_backend/src/main/resources/db/migration/V124__retail_quote_branch_discount.sql and ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailDiscountAllocation.java (RetailDiscountAllocationTest).
- [x] T066 FE-06 settlement parts (006 D-011): one part per branch order for multi-branch cash; each branch order is paid, cancelled or expired on its own and the checkout resolves from its orders, in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailBranchPartService.java and entity/GroupedCheckout.java (RetailBranchOrderStateTest; verified in the browser with two branches).
- [x] T067 Member cancels one branch order before paying at the counter: `POST /api/v1/ready-ring-orders/{orderId}/cancellations` in controller/RetailCheckoutCancellationController.java and LocalCheckoutCancellationController.java.
- [x] T068 Shopee-style cart, checkout bottom bar, voucher picker with greyed ineligible vouchers, and saved-address picker in ppswbs_frontend/src/features/retail-order/ui/cart/, ui/checkout/ and the related CSS.
- [x] T069 Member and Staff order pages with status tabs, order code, and a detail panel that shows the discount line only when applied and a note when the discount was split across branches.
- [x] T070 Local demo data: ppswbs_backend/scripts/local-seed-demo-catalogue-vouchers-points.sql (5 products, 3 vouchers, points, stock at every active branch).

## Phase 11: Sessions 2026-11-18 and 2026-11-20

- [x] T071 Real VNPay without the `retail-vnpay` profile: `VNPAY_ENABLED=true` as for workshop payments; ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/VnpayRetailPaymentAdapter.java is `@Profile("!local")`, the unpaid-expiry job runs only when VNPay is enabled.
- [x] T072 Specific refusal codes RETAIL_VOUCHER_REFUSED and RETAIL_POINTS_CHANGED in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailConflictException.java and RetailCheckoutAcceptanceService.java; the checkout shows the notice above the button with "Cập nhật lại" and drops the refused benefit.
- [x] T073 Product images: `productId` on cart lines and order items, shown by ppswbs_frontend/src/features/retail-order/ui/ProductThumb.jsx in the cart, checkout and order pages.
- [x] T074 "Đặt hàng" with VNPay goes straight to VNPay; the stored link (ppswbs_backend/src/main/resources/db/migration/V188__retail_checkout_payment_link.sql, entity/GroupedCheckout.java) lives for the 15-minute hold and "Thanh toán" reopens it (GroupedCheckoutPaymentLinkTest). "Hủy đơn" also works in PAYMENT_INITIATED.
- [x] T075 Member order tabs, newest first, opening the tab of the order after payment, with a countdown for Chờ thanh toán, in ppswbs_frontend/src/features/retail-order/ui/orders/MemberOrdersPage.jsx (orderStages.test.js). A cash order goes straight to Đơn mua with a notice.
- [x] T076 Staff stages: shipment `READY_FOR_CARRIER` in ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/entity/RetailShipmentStatus.java and RetailOrder.syncShipmentFulfilmentState; Staff tabs in ppswbs_frontend/src/features/retail-order/ui/manager/StaffRetailOrdersPage.jsx (StaffRetailShipmentServiceTest walks PREPARING → READY_FOR_CARRIER → HANDED_TO_CARRIER → DELIVERED).
- [x] T077 Cart opens with nothing ticked; "Mua lại" ticks only the rings it added back and lists the skipped ones; pages open at the top (`ScrollRestoration` in ppswbs_frontend/src/app/AppLayout.jsx).
- [x] T078 Received time: ppswbs_backend/src/main/resources/db/migration/V189__retail_order_completed_at.sql and `RetailOrder.completedAt` set by the counter handover and the GHTK delivery; `paidAt` and `completedAt` in dto/RetailOrderHistoryView.java, shown in Đơn mua and the Staff history.
- [x] T079 GHTK partner portal (`/carrier/orders`, `/carrier/stats`, linked from the FE-01 partner home `/carrier`): ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/controller/CarrierShipmentController.java, service/CarrierShipmentService.java and dto/CarrierShipmentDtos.java (`/api/v1/carrier/shipments`, CarrierShipmentServiceTest); ppswbs_frontend/src/features/retail-order/ui/carrier/CarrierPages.jsx with `/carrier` and `/carrier/stats` (carrier.test.jsx).
- [x] T080 FE-01 role `CARRIER` (Sơn, V190__carrier_role.sql, admin account creation, login redirect to `/carrier`); the Staff simulated courier buttons are removed and ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/StaffRetailShipmentService.java refuses courier steps (StaffRetailShipmentServiceTest).
- [x] T083 Cash at the counter in one step: ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/fulfilment/service/PickupOperationsService.java `collectCashAndHandOver` with the readyringsales facade RetailCounterCashFacade (PickupCounterCashTest); the Staff page has only "Xác nhận đã thu <amount>".
- [x] T082 Duplicate migration version 188 on `develop`: V188 stays FE-08 `retail_checkout_payment_link`; `carrier_role` renumbered to V190 and `design_request_human_review` to V191.
- [ ] T081 **Open**: deployed server sets `VNPAY_RETURN_URL` and `VNPAY_IPN_URL` to its own domain.
- [x] T084 VNPay hold 10 minutes (Session 2026-11-22): ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailPaymentMethods.java `VNPAY_HOLD`, LocalRetailPaymentAdapter.java uses the same hold (RetailPaymentMethodsTest); Đơn mua countdown starts at 10:00 (ppswbs_frontend/src/features/retail-order/ui/orders/MemberOrdersPage.jsx).
- [x] T085 FR-08-039 restated: cancelling after opening VNPay releases the hold and restores the cart (RetailCatalogueMySqlIntegrationTest `cancellationAfterOpeningVnpayReleasesHoldAndRestoresCart`).
- [x] T087 Stacked automatic discount + voucher from FE-06 (V195): ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/service/RetailCheckoutQuotePreviewService.java allocates by `lineDiscounts`, returns `autoDiscountAmount`/`voucherDiscountAmount` (dto/CheckoutDtos.java, RetailStackedDiscountTest); checkout shows Ưu đãi sản phẩm / Voucher giảm giá / Tiết kiệm and translates `VOUCHER_NOT_SAVED`, `VOUCHER_NO_EXTRA_DISCOUNT` (MemberCheckoutPage.jsx, RetailBenefitPanel.jsx, voucherOptions.js; stackedDiscount.test.js).
- [x] T086 Staff home `/staff/home` with Workshop and Đơn hàng cards (ppswbs_frontend/src/features/operatorhome/StaffHome.jsx, staffWork.js; ppswbs_frontend/src/features/retail-order/ui/manager/staffTabs.js; operatorHome.test.jsx).
- [x] T088 **Closed by decision** (Thinh, 2026-11-25): the order code given to Staff is enough; FR-08-017 is amended. Original note (from the Report 3 handover of 2026-11-25): FR-08-017 requires the signed-in owning Member to be verified at the counter before pickup is recorded. `PickupOperationsService.handOver` and `collectCashAndHandOver` only require an active Staff of the branch, so an order code read out loud is enough. Not changed; the verification mechanism (for example the Member confirming on their own signed-in device) needs a decision from the FE-08 owner. Report 3 UC 7.9 and FR-08-017 keep the requirement.
- [x] T089 Decision 2026-11-25 (Thịnh): for retail the Manager acts only within the delegated branches and the counter handover stays with Staff, as in spec 008 and the code; the cross-feature permission-matrix footnote 1 ("global Manager scope") does not apply to retail, and Quốc's footnote 9 records the exception. Code (2026-11-25): `RetailCashCollectionService.confirm` now requires an active Staff (`requireActiveStaff`), a Manager gets 403; the Manager daily cash view (FR-08-047, `RetailCounterCashQueryService.daily`) is unchanged (RetailCashCollectionAuthorizationTest).
- [x] T090 Route access and counter buttons, handover from Quốc 2026-11-26 (R1, U4, C2). R1: `/carrier/orders` and `/carrier/stats` use `RoleArea roles={['CARRIER']}` (another role or a suspended account is replaced to /404 before any API call). C2 decided (Thinh, 2026-11-26): STAFF and MANAGER open `/staff/retail-*`; U4: "Xác nhận đã thu" and "Giao cho khách" show only for STAFF (`RetailCashCollectionService.requireActiveStaff`, `PickupOperationsService.handOver`); "Đã chuẩn bị xong" (GHTK parcel and pickup order) shows for STAFF and MANAGER. Backend: `PickupOperationsService.prepare` now accepts an active Manager of the branch (PickupPreparationAuthorizationTest). Spec 008 clarification and route allowlist, route-access-matrix.md updated. Guard `RoleArea` taken from Quốc's branch (0700ef41, 14d67ed4). Code: ppswbs_backend/.../features/fulfilment/service/PickupOperationsService.java, ppswbs_frontend/src/features/retail-order/routes/routes.jsx, ui/manager/StaffRetailOrdersPage.jsx; tests retailRouteAccess.test.jsx, staffCounterButtons.test.jsx. Open: R3c (`/member/cart`, `/member/checkout`, `/member/orders` Member only) waits for Quốc's shared guard. `/carrier` (GHTK home) is guarded by FE-01 (`accountmanagement/routes.tsx`).
