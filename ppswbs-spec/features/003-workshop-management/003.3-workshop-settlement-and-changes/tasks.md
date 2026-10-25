# Tasks: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-10-24
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Input**: [spec.md](./spec.md), [plan.md](./plan.md), [data-model.md](./data-model.md), [research.md](./research.md), [contract](./contracts/workshop-settlement.openapi.yaml)

## Phase 1: Setup

- [ ] T001 Create `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/{controller,dto,dao,entity,service,facade}/` as a distinct Modulith module.
- [ ] T002 [P] Add module-boundary verification in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/WorkshopSettlementModuleTest.java`.

## Phase 2: Foundational

- [ ] T003 Complete request/result DTO schemas, required fields, validation rules, typed problem responses, and the existing notification facade/outbox contract, delivery retry behavior and safe audit outcome for the one deferred-payment reminder in `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/contracts/workshop-settlement.openapi.yaml` before controller implementation.
- [ ] T004 Add the next append-only settlement migration in `ppswbs_backend/src/main/resources/db/migration/` for bills, charges, exceptions, refunds, reschedules and audit references.
- [ ] T005 [P] Add empty/upgrade migration tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/WorkshopSettlementMigrationIT.java`.
- [ ] T006 Define Booking, Operations and Account/personnel facade boundaries in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/facade/`.
- [ ] T007 Implement active-account, branch-scope and settlement-capability authorization in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/WorkshopSettlementAuthorizationService.java`, and add denial/redacted-audit/safe-error coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/WorkshopSettlementAuthorizationIT.java` proving no raw Firebase token, payment payload, secret or unnecessary PII is exposed.
- [ ] T008 [P] [US1] Add bill, charge-line and adjustment entities/DAOs in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/{entity,dao}/`.

## Phase 3: User Story 1 — Counter settlement (P1)

**Goal**: Finalize item bills and release only after verified payment.

**Independent Test**: Replay valid callback and observe one payment/release; reject unpaid release and invalid/duplicate evidence.

- [ ] T009 [P] [US1] Add bill/charge contract tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/WorkshopFinalBillContractIT.java`.
- [ ] T010 [US1] Implement immutable bill calculation/finalization in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/FinalBillService.java`.
- [ ] T011 [US1] Implement idempotent verified final payment and release transition in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/FinalPaymentService.java`.
- [ ] T012 [US1] Implement final-bill REST handlers in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/controller/FinalItemBillController.java`.
- [ ] T013 [US1] Add callback replay, invalid evidence and unpaid-release tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/FinalPaymentIT.java`.

## Phase 4: User Story 2 — Whole-booking cancellation/refund (P1)

**Goal**: Apply policy-correct whole-booking direct refunds.

**Independent Test**: Verify 100%, 50% and rejection bands; ticket/capacity changes follow verified refund only.

- [ ] T014 [P] [US2] Add refund-case entity/DAO and policy snapshot in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/{entity,dao}/`.
- [ ] T015 [US2] Implement eligibility and earliest-session refund policy in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/WorkshopRefundService.java`.
- [ ] T016 [US2] Implement idempotent refund callback and Operations facade consequences in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/WorkshopRefundCallbackService.java`.
- [ ] T017 [US2] Implement customer cancellation request/result DTOs and `WorkshopCancellationController.java` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/controller/`, then add lifecycle tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/WorkshopRefundIT.java`.

## Phase 5: User Story 3 — Manual reschedule (P2)

**Goal**: Atomically move one eligible item to a compatible session.

**Independent Test**: Verify ticket/capacity transfer and QR replacement; no-capacity target changes nothing.

- [ ] T018 [P] [US3] Add reschedule-record entity/DAO in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/{entity,dao}/`.
- [ ] T019 [US3] Implement target validation, price rule and Operations atomic transfer in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/WorkshopRescheduleService.java`.
- [ ] T020 [US3] Implement reschedule request/result DTOs and `WorkshopRescheduleController.java` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/controller/`, then add atomic rollback tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/WorkshopRescheduleIT.java`.

## Phase 6: User Story 4 — Financial exceptions (P2)

**Goal**: Support audited deferred payment and Mland-caused item remedy.

**Independent Test**: Retain item through expiry; confirmed item refund changes no other booking item.

- [ ] T021 [P] [US4] Add deferred-payment and item-remedy entities/DAOs in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/{entity,dao}/`.
- [ ] T022 [US4] Implement the approved 24-hour exception, one reminder, expiry and audited reissue contract in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/DeferredPaymentService.java`.
- [ ] T023 [US4] Implement replacement-first item remedy in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/MlandCancellationRemedyService.java`.
- [ ] T024 [US4] Implement deferred-payment/remedy DTOs and `WorkshopSettlementExceptionController.java` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/controller/`, then add duplicate-event tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/WorkshopExceptionIT.java`.

## Phase 7: Polish

- [ ] T025 Run and retain evidence for `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/quickstart.md`.
- [ ] T026 Define the approved frontend repository/path, route ownership, and loading/empty/validation/success/error/recovery states for cancellation, final payment, deferred-payment expiry and reschedule in `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/plan.md` before creating frontend implementation tasks.

## Dependencies

T001–T007 block all stories. US1 and US2 are P1; US3 and US4 can proceed after
foundational facade/authorization work. T026 is a design prerequisite for
frontend implementation; T025 follows all intended stories.

## MVP

Complete T001–T013 first, then validate counter settlement independently.
