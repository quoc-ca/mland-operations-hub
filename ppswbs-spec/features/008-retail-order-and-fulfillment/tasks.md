---

description: "Dependency-ordered implementation tasks for FE-08"

---

# Tasks: Retail Order and Fulfillment

**Input**: Design documents from `features/008-retail-order-and-fulfillment/`

**Prerequisites**: [spec.md](spec.md), [plan.md](plan.md),
[research.md](research.md), [data-model.md](data-model.md),
[quickstart.md](quickstart.md), and [contracts/](contracts/)

**Tests**: Verification tasks are included because the project constitution
requires evidence for API, persistence, authorization, module-boundary and
integration changes.

**Path convention**: Backend paths below are relative to the repository root;
documentation paths are relative to `ppswbs-spec/` unless stated otherwise.

## Phase 1: Setup and scope synchronization

**Purpose**: Make the clarified FE-08 scope visible to the project catalogs and
prepare the existing module skeleton for implementation.

- [ ] T001 Update the approved FE-08 scope references in `documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/05-system-fuctionalities/02-external-api-inventory.md` and `documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/05-system-fuctionalities/03-background-job-inventory.md` to include GHTK fee quote, shipment creation, webhook, status fallback, retry, and the mock-provider boundary.
- [ ] T002 [P] Update the cross-feature API catalog in `ppswbs-spec/APIs.md` to align FE-08 routes and GHTK integration maturity with `features/008-retail-order-and-fulfillment/contracts/retail-order-and-fulfillment.openapi.yaml`.
- [ ] T003 [P] Add the FE-08 contract and provider references to `ppswbs-spec/features/008-retail-order-and-fulfillment/checklists/requirements.md` and preserve the explicit production gates for credentials, callback authentication, quotas, and prepaid/no-COD service mapping.

---

## Phase 2: Foundational prerequisites

**Purpose**: Blocking work required before any user story implementation.

**CRITICAL**: No user-story implementation should start until this phase is
complete and the scope/migration gates are approved.

- [ ] T004 Update Spring Modulith ownership metadata in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/package-info.java` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/package-info.java` so the permitted facade/event dependencies match the approved two-module design.
- [ ] T005 [P] Define the minimal per-branch-order paid event and shipment projection boundary in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/facade/RetailOrderPaid.java` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/facade/RetailShipmentStatusView.java`; expose branch-order identifiers, grouped-checkout correlation, version and safe correlation data only, never entities or provider payloads.
- [ ] T006 [P] Add typed FE-08 error codes, correlation handling, and server-side authorization hooks in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/common/web/` and the owning module web packages, covering ownership, branch scope, stale state, unsupported destination, provider unavailable, and idempotency conflict.
- [ ] T007 Create the next approved append-only Flyway migration under `ppswbs_backend/src/main/resources/db/migration/` for the physical mapping of carts/cart-line branch context, grouped checkouts, branch retail orders/items, checkout holds, fulfilment snapshots, shipments, status events, handoff evidence, and required uniqueness/retention indexes from `data-model.md`.
- [ ] T008 [P] Define the provider-neutral shipping port and deterministic mock adapter in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/facade/ShippingProviderPort.java` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/infrastructure/MockShippingProviderAdapter.java`, including fee, create, status, timeout, duplicate-partner-ID, and webhook-replay fixtures.
- [ ] T009 [P] Add server-side GHTK configuration binding and redacted diagnostics in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/infrastructure/GhtkProperties.java` and `ppswbs_backend/src/main/resources/application.yml`; keep token, partner code, callback secret/hash and URLs outside source control.
- [ ] T010 Add the durable post-commit event publication/consumption registration for grouped-payment-to-per-branch-fulfilment processing in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/payments/` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/application/`, with restart-safe processing markers for each branch order.

**Checkpoint**: Module verification, migration mapping, provider mock and safe
error/auth foundations are ready before user-story work begins.

---

## Phase 3: User Story 1 - Prepare a retail purchase (Priority: P1) 🎯 MVP

**Goal**: An authenticated active Member can select a processing branch and
manage an own cart using published, eligible configured variants without
creating an order or payment effect.

**Independent Test**: Select a branch, add/edit/remove a valid cart line, keep
the recorded branch when the selected branch changes, and reject another
Member's cart, invalid quantity, unpublished variant, and stale concurrent edit.

### Implementation for User Story 1

- [ ] T011 [P] [US1] Create the `RetailCart` and `CartLine` JPA/domain mapping in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/domain/` with Member ownership, the selected processing branch captured when a line is added, positive integer quantity, indicative price, and version/timestamp invariants.
- [ ] T012 [US1] Implement branch-scoped cart repositories and ownership-scoped application operations in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/infrastructure/` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/application/`, using the `members` and `catalogue` facades instead of direct repository access; changing the selected branch must not rewrite existing cart lines.
- [ ] T013 [US1] Implement the Member branch-selection, catalogue-context, and cart REST/Thymeleaf entry points in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/web/CartController.java` and `ppswbs_backend/src/main/resources/templates/readyringsales/cart/`, preserving a server-rendered/no-JavaScript form path.
- [ ] T014 [US1] Add Bean Validation and safe conflict handling in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/web/CartRequest.java` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/application/CartApplicationService.java` for active branch, positive integer quantity, published/eligible variants, ownership, stale version, and concurrent edit.
- [ ] T015 [US1] Verify the US1 scenarios from `quickstart.md` in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/CartJourneyTest.java`, including branch selection, branch preservation per cart line, no order/payment/hold effect from cart edits, and safe rejection of cross-member access.

**Checkpoint**: US1 is independently usable and testable as the MVP entry
point; cart changes have no purchase side effect.

---

## Phase 4: User Story 2 - Checkout and pay for a retail order (Priority: P1)

**Goal**: Revalidate a complete multi-branch cart, create one branch order per
branch group, freeze each branch quote, create one grouped payment attempt, and
mark every branch order paid only from verified VNPay evidence.

**Independent Test**: Given a valid cart containing one or more branch groups,
accept one grouped checkout, show each branch subtotal and shipping charge,
pay the combined amount once, reject invalid/duplicate/late callbacks, and
expire the hold without partial branch-order effects.

### Implementation for User Story 2

- [ ] T016 [P] [US2] Create `GroupedCheckout`, `RetailOrder`, `OrderItem`, and `CheckoutHold` JPA/domain mappings in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/domain/` with one grouped payment state, one branch-specific order per group, immutable snapshots, separate payment/fulfilment state, a 10-minute payment-link deadline, and a maximum 15-minute unpaid hold.
- [ ] T017 [US2] Implement atomic grouped checkout in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/application/RetailCheckoutService.java`, revalidating every line's recorded branch, variant, quantity, price, promotion/loyalty result, fulfilment input, package weight, quote, and benefit limits before creating all branch orders/holds or nothing.
- [ ] T018 [US2] Implement grouped checkout totals, per-branch item/shipping snapshots, immutable order/item/benefit snapshots, and idempotent checkout command handling in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/infrastructure/` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/application/`, rejecting conflicting reuse of an existing command key.
- [ ] T019 [US2] Integrate one grouped payment-attempt initiation with the existing payment boundary in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/payments/facade/PaymentGatewayPort.java` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/application/RetailPaymentApplicationService.java`; include the sum of branch subtotals and accepted per-branch shipping charges in the VNPay amount and never treat browser return as payment proof.
- [ ] T020 [US2] Implement the verified-grouped-payment event consumer in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/application/RetailPaymentEventConsumer.java` so matching full payment settles the grouped checkout and every branch order once, converts/releases all holds correctly, rejects invalid/late/duplicate evidence, and publishes one `RetailOrderPaid` event per paid branch order only after commit.
- [ ] T021 [US2] Implement automatic hold/payment-link expiry in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/application/RetailHoldExpiryJob.java`, releasing quantities and benefits after the 15-minute hold and routing late payment evidence to manual review without reopening the hold.
- [ ] T022 [US2] Implement grouped checkout, one-payment initiation, and per-branch summary REST/Thymeleaf endpoints in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/web/RetailOrderController.java` and `ppswbs_backend/src/main/resources/templates/readyringsales/checkout/`, showing each branch subtotal and GHTK fee separately, with typed errors, idempotency header handling, safe totals, and no-JavaScript fallback.
- [ ] T023 [US2] Verify grouped checkout/payment scenarios in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/RetailCheckoutPaymentTest.java`, including multiple branch groups, one VNPay payment, per-branch shipping display, no partial branch order, exact full-payment match, duplicate callback no-op, expiry release, and late-payment manual-review outcome.

**Checkpoint**: US1 and US2 work together; the system can create a paid
grouped checkout containing branch orders with frozen per-branch quotes and one
safe payment/hold lifecycle.

---

## Phase 5: User Story 3 - Review and choose order fulfilment (Priority: P1)

**Goal**: A Member can choose pickup or GHTK independently for each branch
order before payment, obtain separate valid quotes, and freeze each accepted
fulfilment/recipient snapshot.

**Independent Test**: Use a grouped checkout with multiple branch orders to
select pickup or GHTK per branch, validate recipient coverage, show each
accepted quote separately, and reject changes after payment or preparation.

### Implementation for User Story 3

- [ ] T024 [P] [US3] Extend the catalogue facade in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/catalogue/facade/` to return branch-contextual eligibility plus authoritative product/variant shipping weight and required package dimensions, rejecting eligible variants without required package data for GHTK quotation.
- [ ] T025 [P] [US3] Create branch-order-scoped `FulfilmentSelection`, recipient snapshot, and fee-quote value objects in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/domain/` with `PICKUP`/`GHTK` enums, required recipient fields, approved-fee components, and quote expiry.
- [ ] T026 [US3] Implement provider-neutral per-branch fee quotation in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/application/ShippingQuoteService.java` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/infrastructure/GhtkShippingProviderAdapter.java`, mapping each branch's weight in grams, declared value, pickup/destination and approved mandatory surcharges without automatic insurance charging.
- [ ] T027 [US3] Implement pre-payment fulfilment choice and per-branch quote orchestration in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/application/FulfilmentChoiceService.java`, requiring valid recipient data for each GHTK branch order, refreshing changed quotes, and freezing each method/recipient/fee at grouped-checkout acceptance.
- [ ] T028 [US3] Implement the quote and fulfilment-choice endpoints from `ppswbs-spec/features/008-retail-order-and-fulfillment/contracts/retail-order-and-fulfillment.openapi.yaml` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/web/FulfilmentChoiceController.java`.
- [ ] T029 [US3] Verify pickup/GHTK selection and per-branch fee scenarios in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/FulfilmentQuoteTest.java`, including multiple branch quotes, unsupported destination, missing shipping profile, quote expiry, approved surcharge calculation, separate fee display, and rejection of post-payment changes.

**Checkpoint**: US3 is independently testable before carrier shipment creation;
checkout displays a stable accepted charge and method.

---

## Phase 6: User Story 4 - Process and complete a retail order (Priority: P1)

**Goal**: Authorized Staff/Manager can prepare, pick up, create/reconcile the
GHTK shipment for a paid branch order, and record physical custody exactly once
within branch scope.

**Independent Test**: Process a paid pickup order or paid GHTK order through
preparation and the correct final handoff while rejecting wrong-branch,
unpaid, stale, duplicate, and provider-failure operations.

### Implementation for User Story 4

- [ ] T030 [P] [US4] Create `GhtkShipment`, `CarrierStatusEvent`, and `FulfilmentEvidence` domain/JPA mappings in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/domain/` with unique partner ID/provider identifiers, normalized status, duplicate/stale outcome, actor/branch/time evidence, and safe failure state.
- [ ] T031 [US4] Implement the per-branch-order `RetailOrderPaid` consumer in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/application/RetailShipmentCreationService.java` to create one GHTK shipment after grouped payment, persist provider identifiers once, preserve the paid branch order/shipment-pending state on ambiguity, and use the same partner order ID for retry.
- [ ] T032 [US4] Implement shipment retry and status-reconciliation jobs in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/application/GhtkReconciliationJob.java`, handling provider duplicate-partner responses, timeouts, stale shipments and status GET fallback without creating a second shipment.
- [ ] T033 [US4] Implement GHTK webhook authentication/validation, form parsing, deduplication, action-time ordering, normalized status mapping, and HTTP 200 acknowledgement in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/web/GhtkWebhookController.java` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/application/GhtkWebhookApplicationService.java`.
- [ ] T034 [US4] Implement Staff/Manager preparation, authenticated pickup confirmation, and physical carrier handoff use cases in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/application/` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/web/FulfilmentOperationsController.java`, enforcing current account status, branch, delegated action, payment state and valid transition.
- [ ] T035 [US4] Add branch/delegation authorization and safe audit evidence for preparation, pickup, shipment creation, webhook processing, retry, rejection and handoff in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/application/` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/infrastructure/`.
- [ ] T036 [US4] Verify shipment creation, retry, webhook, pickup and handoff scenarios in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/RetailFulfilmentJourneyTest.java`, including lost response recovery, duplicate/out-of-order webhook, wrong branch, duplicate terminal command, and no automatic refund/return workflow.

**Checkpoint**: US4 provides auditable operational completion for pickup and
GHTK handoff while preserving payment and shipment idempotency.

---

## Phase 7: User Story 5 - View retail order history and details (Priority: P2)

**Goal**: A Member can view only owned grouped checkouts and branch orders with
frozen purchase facts, one grouped payment state, per-branch fulfilment
milestones, and latest normalized GHTK statuses.

**Independent Test**: List and open own grouped checkout/branch orders, verify
frozen item/payment data, separate branch totals and latest shipment status,
and confirm another Member's order is not disclosed.

### Implementation for User Story 5

- [ ] T037 [P] [US5] Implement the Member-safe grouped checkout and branch-order read projection in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/application/RetailOrderQueryService.java`, exposing only frozen item/payment facts, per-branch totals, and each branch's latest normalized status, update time, and provider estimate.
- [ ] T038 [US5] Implement grouped checkout/branch-order reads through `GET /api/v1/members/me/ready-ring-orders` and `GET /api/v1/members/me/ready-ring-orders/{orderId}` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/web/RetailOrderQueryController.java`, enforcing Member ownership and safe not-found behavior.
- [ ] T039 [US5] Add the Member order-history/detail Thymeleaf views and htmx enhancement in `ppswbs_backend/src/main/resources/templates/readyringsales/orders/`, preserving a no-JavaScript link/form fallback and not exposing raw provider callbacks or recipient PII.
- [ ] T040 [US5] Implement post-handoff recipient-data deletion/irreversible-obscuring in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/application/RecipientDataRetentionJob.java`, preserving minimized order/payment/custody evidence after 30 days.
- [ ] T041 [US5] Verify Member grouped-history/detail authorization and per-branch latest-status display in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/RetailOrderQueryTest.java`, including cross-member access, separate branch totals, shipment-pending state, delivery estimate absence, and raw callback/credential redaction.

**Checkpoint**: All five user stories are independently demonstrable with the
Member read journey and the authorized shop workflow complete.

---

## Phase 8: Polish and cross-cutting verification

**Purpose**: Complete integration evidence, documentation, security review and
operational readiness without expanding FE-08 scope.

- [ ] T042 [P] Add post-commit workflow notification intents for payment accepted, shipment pending, shipment created, and permitted status changes in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/fulfilment/`, keeping email delivery out of payment proof.
- [ ] T043 [P] Validate the generated OpenAPI contract and synchronize route maturity/documentation in `ppswbs-spec/features/008-retail-order-and-fulfillment/contracts/retail-order-and-fulfillment.openapi.yaml`, `ppswbs-spec/features/008-retail-order-and-fulfillment/contracts/ghtk-integration.md`, and `ppswbs-spec/APIs.md`.
- [ ] T044 [P] Add migration fresh/upgrade verification and database invariant coverage under `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/` for grouped-checkout atomicity, one-payment-to-many-branch-order mapping, unique partner order IDs, payment/hold deadlines, shipment status ordering, retention boundary, and required nullability.
- [ ] T045 Run Spring Modulith `ApplicationModules.verify()` and resolve any undeclared access in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/` without adding broad package exposure.
- [ ] T046 Run the scenarios in `ppswbs-spec/features/008-retail-order-and-fulfillment/quickstart.md` with the mock providers and record actual commands/results in the feature verification handoff; do not enable live GHTK credentials for routine tests.
- [ ] T047 Perform final security and scope review in `ppswbs-spec/features/008-retail-order-and-fulfillment/` and `ppswbs_backend/` confirming no provider secret/raw callback/avoidable recipient PII is logged or returned, no COD/refund/return/accounting workflow was added, and all unresolved provider gates are documented.

---

## Dependencies and execution order

### Phase dependencies

- **Phase 1**: Can begin immediately; scope synchronization must be approved.
- **Phase 2**: Depends on Phase 1 and blocks all user stories.
- **US1**: Depends on Phase 2; MVP cart journey.
- **US2**: Depends on US1 cart behavior and Phase 2 payment/event foundation.
- **US3**: Depends on US1/US2 order model and the catalogue shipping-profile facade.
- **US4**: Depends on verified payment events and US3 accepted fulfilment data.
- **US5**: Depends on the order/shipment projections from US2–US4, but its read
  projection work can begin after the foundational schema is stable.
- **Polish**: Depends on the desired user stories being implemented.

### User story dependencies

- **US1 (P1)**: No user-story dependency after Phase 2.
- **US2 (P1)**: Requires US1 cart commands and the shared payment boundary.
- **US3 (P1)**: Requires the US2 order/quote snapshot and FE-02 shipping data.
- **US4 (P1)**: Requires US2 verified payment and US3 fulfilment selection.
- **US5 (P2)**: Reads projections produced by US2–US4; can be implemented in
  parallel with late US4 work if projection contracts are frozen.

### Parallel opportunities

- T002–T003 can run in parallel after T001's scope decision.
- T005–T009 can run in parallel once the module ownership decision is fixed;
  T010 depends on the event shape from T005.
- T011 and T013 can be split after the branch-aware cart command shape is agreed;
  T012/T014 remain dependent on the domain/repository work.
- T016 and T017 can be split by grouped-checkout domain mapping versus branch
  grouping/payment orchestration.
- T024–T026 can run in parallel after the FE-02 facade shape is approved.
- T030 and T033 can run in parallel after the shipment/status schema is fixed.
- T037 and T039 can run in parallel after the read projection contract is fixed.
- T042–T044 can run in parallel after the core story flows stabilize.

## Parallel example: MVP

```text
T001 -> T004/T005/T006/T007/T008/T009/T010
        -> T011/T012/T013/T014/T015 (US1)
        -> T016/T017/T018/T019/T020/T021/T022/T023 (US2 grouped checkout)
```

US2 should not be started in production implementation until US1 cart behavior
and the foundational event/migration decisions are complete. The first demo
scope is US1 plus the minimum US2 checkout/payment path.

## Implementation strategy

### MVP first

1. Complete scope synchronization and foundational module/schema/provider-mock
   work.
2. Complete US1 and validate cart ownership/concurrency.
3. Complete the minimum US2 grouped-checkout/payment path and validate the
   10/15-minute deadlines, per-branch fee display, one payment, and exact
   verified payment.
4. Stop and review before enabling carrier integration.

### Incremental delivery

1. Add US3 fulfilment choice and pre-payment GHTK fee quote.
2. Add US4 post-payment shipment creation, webhook/status reconciliation, and
   Staff handoff.
3. Add US5 Member order history/latest tracking status.
4. Run polish, migration, module, contract and quickstart verification.

### Parallel team strategy

1. One developer owns the `readyringsales` cart/checkout/order path.
2. One developer owns `fulfilment` provider adapter/shipment/webhook path after
   the foundational event and schema contracts are frozen.
3. One developer owns Member/Staff REST and Thymeleaf projections after the
   OpenAPI contract is stable.
4. All contributors use the same mock provider fixtures and do not add live
   credentials.

## Notes

- Every task uses the required checkbox, sequential ID, story label where
  applicable, and an explicit file path.
- Do not implement production GHTK activation until the documented partner,
  callback-authentication, quota, and prepaid/no-COD gates are approved.
- Run `speckit-analyze` after this task list is reviewed and before
  `speckit-implement`.

