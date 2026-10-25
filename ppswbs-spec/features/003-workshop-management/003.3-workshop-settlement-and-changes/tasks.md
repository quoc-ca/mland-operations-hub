# Tasks: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-10-25
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Input**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md), [data-model.md](./data-model.md), [quickstart.md](./quickstart.md), and [contract status](./contracts/workshop-settlement.openapi.yaml).

**Scope rule**: This is an implementation handoff checklist, not approval to code.
Each **BLOCKED** item remains unchecked until its named owner supplies the missing
boundary/configuration. No task authorizes inventing endpoints, DTOs, schema,
migration, role mapping, locking mechanism, or payment-provider policy.

## Phase 1: Setup and boundary handoff

**Purpose**: retire item/session financial assumptions and establish legitimate dependencies.

- [ ] T001 Replace legacy item/session financial vocabulary with one booking/package final-bill vocabulary in `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/tasks.md`.
- [ ] T002 [P] Confirm a distinct `workshopsettlement` Spring Modulith module and retain boundary verification in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/WorkshopSettlementModuleTest.java`.
- [ ] T003 [P] Implement the consumed `WorkshopBookingFacade`, `WorkshopOperationsFacade`, and Account boundary dependencies plus the published `WorkshopSettlementFacade` from `ppswbs-spec/features/003-workshop-management/contracts/workshop-interfeature-facades.md`, without exposing entities, repositories, or HTTP DTOs.
- [ ] T004 Consume the published Account Management active-account, capability, branch/resource-scope, and central-policy-editor authority through its facade; do not map roles or edit `features/001-account-management/`.
- [ ] T005 Consume the fixed `WorkshopBookingFacade` confirmed booking/package/deposit projection and invoke its idempotent final-balance-resolved conclusion; only 003.1 records `FULLY_PAID`.
- [ ] T006 Consume the fixed `WorkshopOperationsFacade` settlement-readiness, all-or-none complete required-session replacement, post-refund ticket/capacity consequence, and cancellation-completion outcomes.
- [ ] T007 **BLOCKED — integration owner**: approve VNPay merchant, signed IPN/QueryDR verification and refund-operation configuration without credentials in `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/research.md`.
- [ ] T008 **BLOCKED — notification owner**: publish post-commit final-payment-link, one-reminder and outcome-delivery handoff in `ppswbs-spec/features/007-notification-management/`.

## Phase 2: Foundational settlement integrity

**Purpose**: shared financial/audit foundation; no user story bypasses current authorization or an approved facade.

- [ ] T009 Create approved persistence design for final booking bill, charge/correction, provider evidence, deferred case, refund case, required-session-change case and redacted audit in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/entity/`.
- [ ] T010 Create append-only preservation migration plus empty/upgrade/recovery verification in `ppswbs_backend/src/main/resources/db/migration/` and `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/WorkshopSettlementMigrationIT.java`; reset only empty dev/test after preflight.
- [ ] T011 [P] Implement current-account/capability/branch authorization and redacted business-audit recording in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/WorkshopSettlementAuthorizationService.java` after T004, with denial/no-side-effect coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/WorkshopSettlementAuthorizationIT.java`.
- [ ] T012 [P] Implement safe idempotency/reconciliation support for final-payment/refund outcomes in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/SettlementProviderEvidenceService.java` after T007; retain no raw payload, checksum, token or secret.
- [ ] T013 **BLOCKED — public transport decision**: approve REST operations, DTOs, validation and safe errors, then replace the empty contract in `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/contracts/workshop-settlement.openapi.yaml` before controller work.

## Phase 3: User Story 1 — Staff settles a completed booking/package (P1) 🎯 MVP

**Goal**: After the final required session, Staff finalizes one package bill and releases output only after verified payment or approved exception.

**Independent Test**: Basic Ring and Silverclay each create one final bill after their final required session; valid VNPay settles once, while invalid/duplicate/mismatched/late/browser-return-only evidence cannot release output.

- [ ] T014 [US1] Implement one-final-bill eligibility and balance calculation (frozen package price + accepted charges − accepted deposit) in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/FinalBookingBillService.java`; require only Operations' settlement-readiness conclusion, never create item/session/participant/ticket bills.
- [ ] T015 [P] [US1] Implement permitted fixed-price package/branch add-on and Staff/Manager-confirmed variable-charge handling in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/AdditionalChargeService.java`; reject arbitrary customer amounts and preserve correction evidence.
- [ ] T016 [US1] Implement matched signed VNPay IPN/QueryDR once-only settlement/release in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/FinalPaymentService.java` after T005 and T007; send 003.1 only the idempotent final-balance-resolved conclusion, never mutate Booking directly; browser redirect/manual counter record cannot settle.
- [ ] T017 [US1] Implement authorized 24-hour deferred-payment case, at-most-one reminder and audited reissue after expiry in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/DeferredPaymentService.java` after T008; delivery failure cannot change financial truth.
- [ ] T018 [US1] Add bill, charge, payment replay, unpaid-release and deferred-expiry coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/FinalBookingSettlementIT.java`.
- [ ] T019 [US1] **BLOCKED — T013**: implement approved final-bill transport and React + Vite flow states only in paths approved by `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/contracts/workshop-settlement.openapi.yaml`.

## Phase 4: User Story 2 — Customer cancels a whole booking (P1)

**Goal**: Guest/authorized Member obtains a whole-booking cancellation/refund decision, never a per-session/ticket/participant refund.

**Independent Test**: 72+ hour and 24–72 hour requests yield correct 100%/50% deposit outcome; ineligible requests fail; capacity/tickets remain unchanged before verified refund success.

- [ ] T020 [US2] Implement whole-booking eligibility, earliest-session policy snapshot and refund request in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/CustomerBookingCancellationService.java` using approved 003.1/003.2 projections only.
- [ ] T021 [US2] Implement idempotent verified-refund handling and request Operations consequence only after success in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/WorkshopRefundService.java` after T006 and T007.
- [ ] T022 [US2] Add timing bands, checked-in/no-show denial, refund retry/out-of-order and no-pre-success-capacity-effect tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/CustomerCancellationIT.java`.
- [ ] T023 [US2] **BLOCKED — T013**: implement approved customer cancellation transport and React + Vite recovery states in the paths approved by `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/contracts/workshop-settlement.openapi.yaml`.

## Phase 5: User Story 3 — Manager changes future required sessions (P2)

**Goal**: Manager replaces one/two future required sessions as one complete booking-level result while ticket QR persists.

**Independent Test**: Operations atomically applies a complete compatible replacement or applies none; Silverclay retains branch/order/same-day-gap constraints.

- [ ] T024 [US3] Implement complete required-session-change orchestration in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/RequiredSessionChangeService.java` after T006's `WorkshopOperationsFacade` integration.
- [ ] T025 [US3] Add one/two-session change, no-capacity rollback, persistent QR, current customer-projection handoff, and Silverclay branch/distinct/chronological/gap coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/RequiredSessionChangeIT.java` after T024.
- [ ] T026 [US3] **BLOCKED — T013**: implement approved Manager-change transport and React + Vite recovery states in paths approved by `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/contracts/workshop-settlement.openapi.yaml`.

## Phase 6: User Story 4 — Manager remedies Mland-caused package disruption (P2)

**Goal**: replacement is offered first; unresolved disruption becomes an audited whole-package refund under its retained policy snapshot.

**Independent Test**: unavailable/declined replacement refunds all collected funds under confirmation-time snapshot; default is 100%; later revision cannot change historical bookings.

- [ ] T027 [US4] **BLOCKED — T006**: implement replacement-first cancellation-case orchestration in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/MlandCancellationRemedyService.java` after Operations supplies the cancellation/replacement boundary.
- [ ] T028 [US4] Implement prospective central-policy revision authorization and immutable snapshot selection in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/MlandCancellationPolicyService.java` through T004's published Account authority.
- [ ] T029 [US4] Add replacement-first, declined/unavailable, default-100%, historical-snapshot and no-partial-refund coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/MlandCancellationRemedyIT.java` after T027–T028.
- [ ] T030 [US4] **BLOCKED — T013**: implement approved remedy/policy transport and React + Vite states in paths approved by `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/contracts/workshop-settlement.openapi.yaml`.

## Phase 7: Cross-cutting verification and handoff

- [ ] T031 [P] Run [quickstart validation](./quickstart.md) non-production scenarios and retain redacted evidence in `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/quickstart.md`.
- [ ] T032 [P] Verify `ApplicationModules.verify()`, authorization denial, provider idempotency, facade-only access and data-bearing upgrade/recovery path in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/`.
- [ ] T033 Review T004, T007, T008 and T013 against owner artifacts before unblocking their dependent implementation work in `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/tasks.md`; verify T003/T005/T006 against the shared 003 facade contract.

## Dependencies and execution order

```text
T001–T003 → T004/T005/T006/T007/T008/T009–T013
T009–T013 → US1 (T014–T019) and US2 (T020–T023)
T006 + T013 → US3 (T024–T026)
T004 + T006 + T013 → US4 (T027–T030)
All intended stories → T031–T033
```

## Parallel opportunities

- T002 and T003 can run in parallel.
- Once approved, T011 and T012 use different services and can run in parallel.
- T015 can proceed alongside T016 after the common foundation is available.
- US2 has no dependency on US3/US4 beyond its explicit Operations refund facade.

## Implementation strategy

**MVP**: T001–T019, but only after the named Booking facade implementation, VNPay, Notification
and public-transport blockers are actually resolved. Add US2, then US3 and US4.
All 33 tasks are intentionally unchecked.
