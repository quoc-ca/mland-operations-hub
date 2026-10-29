# Tasks: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-10-29
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature 003 authorization decision (2026-10-29)**: Active Manager actions in
this feature have global location scope independent of `activeLocationIds`.
Staff-authorized actions retain booking/session location scope; Member ownership
and explicitly Admin-only actions remain unchanged.

**Input**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md),
[data-model.md](./data-model.md), [quickstart.md](./quickstart.md),
[settlement OpenAPI](./contracts/workshop-settlement.openapi.yaml), the
[shared facade contract](../contracts/workshop-interfeature-facades.md), and the
[shared VNPay ingress contract](../contracts/vnpay-payment-ingress.openapi.yaml).

**Scope rule**: This is a pre-code implementation checklist. A **BLOCKED** task
may not be implemented until its stated configuration/provider dependency is
available. No task authorizes inventing a database schema, Flyway version,
locking mechanism, role mapping, provider secret, or notification mechanics.

## Phase 1: Setup and shared boundaries

**Purpose**: establish the `workshopsettlement` module and consume only approved
owner boundaries.

- [X] T001 [P] Establish the `workshopsettlement` Spring Modulith module boundary and add `ApplicationModules.verify()` coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/WorkshopSettlementModuleTest.java`.
- [ ] T002 [P] Implement 003.3's named `WorkshopSettlementFacade`, including `ApplyVerifiedSettlementPaymentEvidenceCommand`, in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/facade/` exactly as limited by `ppswbs-spec/features/003-workshop-management/contracts/workshop-interfeature-facades.md`.
- [ ] T003 Consume `WorkshopBookingFacade` confirmed financial projection and idempotent final-balance conclusion, and `WorkshopOperationsFacade` readiness/replacement/refund/cancellation outcomes, only through facade adapters in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/facade/`.
- [ ] T004 Consume current canonical Mland account role/status, ownership and location facts through the approved FE-001 boundary in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/WorkshopSettlementAuthorizationService.java`; grant every active Manager global authority for Manager-authorized actions (including refund-policy administration), keep Staff actions location-scoped and Member ownership enforced, and do not use display roles, Firebase claims or retired codes as authority or edit FE-001.
- [ ] T005 **BLOCKED — external VNPay configuration**: obtain the environment-approved Merchant identity, runtime-only HMAC-SHA512 secret, HTTPS IPN registration, outbound QueryDR/refund authority, and redacted sandbox evidence listed in `ppswbs-spec/features/003-workshop-management/contracts/vnpay-configuration-preflight.md`; keep every credential outside the repository.
- [ ] T006 Implement the approved post-commit `SettlementNotificationReady` handoff and immutable notification-delivery payload facade for deferred-payment links/reissues, their permitted reminder, and verified settlement/refund outcomes in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/facade/`; publish references only after the local transaction, let Feature 007 deduplicate/read the payload, and never let delivery outcome reverse settlement truth.

---

## Phase 2: Foundational settlement integrity

**Purpose**: create shared retained state, authorization, audit, idempotency, and
provider-boundary behavior before any user-story command.

- [ ] T007 Create approved JPA entities/repositories for one booking-scoped final bill, immutable charge/correction, settlement case, redacted provider-evidence conclusion, policy snapshot, business audit, and idempotency outcome in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/{entity,dao}/`.
- [ ] T008 Create an append-only preservation migration and empty-database plus upgrade/recovery coverage in `ppswbs_backend/src/main/resources/db/migration/` and `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/WorkshopSettlementMigrationIT.java`; reset only dev/test with no retained booking/customer data after preflight.
- [ ] T009 [P] Implement server-side authority denial and minimal business-audit evidence in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/WorkshopSettlementAuthorizationService.java`; denied commands have no side effect and audit excludes raw token, QR, callback payload, secret, or unnecessary PII.
- [ ] T010 [P] Implement client `Idempotency-Key` handling in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/SettlementIdempotencyService.java`; same key plus semantically identical content returns the original outcome and changed reuse returns `409 IDEMPOTENCY_CONFLICT`.
- [ ] T011 Implement the shared `paymentintegration` dispatch consumer in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/facade/`; it accepts only redacted verified `FINAL_PAYMENT`/`REFUND` evidence, checks frozen reference/amount/currency, and never exposes a VNPay HTTP controller in this module.
- [ ] T012 [P] Implement typed safe `Problem` mapping, Bean Validation, customer proof/ownership extraction, and controller-only transport adapters from `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/contracts/workshop-settlement.openapi.yaml` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/{controller,dto}/`.

**Checkpoint**: No story command may bypass authorization, immutable evidence,
one-final-bill integrity, owner facade, or shared VNPay ingress.

---

## Phase 3: User Story 1 — Staff settles a completed booking/package (P1) 🎯 MVP

**Goal**: Staff creates and finalizes one bill only after the final required
session; valid final-payment evidence settles it once, while an authorized
deferred exception retains the package output.

**Independent Test**: Basic Ring and Silverclay create exactly one final bill;
valid final evidence resolves it once, while invalid, duplicate, mismatched,
late, or browser-return-only evidence cannot release output.

- [ ] T013 [US1] Implement one-final-bill creation from the 003.1 frozen projection and 003.2 `ready` conclusion in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/FinalBookingBillService.java`; never calculate voucher, points, deposit, or participant/session/ticket bills again.
- [ ] T014 [P] [US1] Implement customer fixed catalogue add-on selection in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/CatalogueAddOnService.java`; Member ownership or Guest booking code plus exactly one stored phone/email may supply only `catalogueAddOnReference`, and the server derives its permitted package/location snapshot price before bill finalization.
- [ ] T015 [P] [US1] Implement Staff/Manager variable-charge confirmation in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/VariableChargeService.java`; accept description, whole-VND amount, and reason only, then retain an immutable audited correction path rather than editing evidence.
- [ ] T016 [US1] Implement finalization and package-output release gating in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/FinalBookingBillService.java`; require final required-session completion, active booking-location Staff authority, and zero balance unless an audited Manager exception exists.
- [ ] T017 [US1] Implement idempotent final-payment/refund evidence application in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/FinalPaymentService.java`; consume T011 only, record immutable safe evidence once, and send 003.1 only `RecordFinalBalanceResolvedCommand` after a valid final-balance resolution.
- [ ] T018 [US1] Implement Manager deferred-payment case in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/DeferredPaymentService.java`; server derives outstanding amount and fixed 24-hour expiry, retains output, permits at most one reminder, creates the approved notification handoff for issue/reissue/reminder, and requires audited reissue after expiry.
- [ ] T019 [US1] Implement `GET/POST /workshop-bookings/{bookingCode}/final-bill`, `POST /final-bill/charges`, and `POST /final-bill/finalizations` exactly from `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/contracts/workshop-settlement.openapi.yaml` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/controller/`.
- [ ] T020 [US1] Add final-bill, charge, finalization, valid/duplicate/mismatched/late evidence, unpaid-release, deferred-expiry, ownership/proof, browser-return-denial, and reference-only notification-handoff/delivery-failure-isolation coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/FinalBookingSettlementIT.java`.

---

## Phase 4: User Story 2 — Customer cancels a whole booking (P1)

**Goal**: An owning Member or Guest with booking proof opens an auditable
whole-booking cancellation case; ticket/capacity effects occur only after a
verified refund outcome.

**Independent Test**: Eligible and ineligible requests receive safe case
projections; no ticket/capacity consequence appears until the shared ingress
dispatches verified refund evidence.

- [ ] T021 [US2] Implement whole-booking customer-cancellation case opening and safe read projection in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/CustomerBookingCancellationService.java`; reject session/ticket/participant partial cancellation and Magic-Link-only authority.
- [ ] T022 [US2] Implement verified whole-booking refund consequence orchestration in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/WorkshopRefundService.java`; call `WorkshopOperationsFacade` only after successful verified refund, never before.
- [ ] T023 [US2] Implement booking-scoped cancellation-case POST/GET routes from `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/contracts/workshop-settlement.openapi.yaml` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/controller/`.
- [ ] T024 [US2] Add Guest proof, Member ownership, partial-cancellation denial, refund pending/failure/replay, and no-pre-success ticket/capacity-effect coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/CustomerCancellationIT.java`.

---

## Phase 5: User Story 3 — Manager changes future required sessions (P2)

**Goal**: A Manager replaces the complete future required-session set atomically
without changing package price or persistent ticket QR.

**Independent Test**: Operations applies the complete compatible replacement or
none; Silverclay retains its location, distinctness, chronological, and same-day
gap constraints.

- [ ] T025 [US3] Implement complete required-session-change case orchestration via `WorkshopOperationsFacade` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/RequiredSessionChangeService.java`; pass only the complete target set and preserve frozen price/deposit.
- [ ] T026 [US3] Implement required-session-change POST/GET routes from `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/contracts/workshop-settlement.openapi.yaml` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/controller/`.
- [ ] T027 [US3] Add one/two-session change, no-capacity rollback, persistent QR, current ticket/session projection, and Silverclay same-location/distinct/chronological/same-day-gap coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/RequiredSessionChangeIT.java`.

---

## Phase 6: User Story 4 — Manager remedies Mland-caused package disruption (P2)

**Goal**: Mland offers a replacement first; `UNAVAILABLE` or `DECLINED` then
allows one auditable whole-package remedy/refund using the confirmation-time
policy snapshot.

**Independent Test**: Remedy requires the same cancellation handoff as the
replacement; prospective policy changes do not rewrite historical bookings.

- [ ] T028 [US4] Implement Mland-caused remedy case orchestration in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/MlandCancellationRemedyService.java`; require the matching 003.2 `cancellationHandoffReference` and accept only `UNAVAILABLE` or `DECLINED` replacement outcomes.
- [ ] T029 [US4] Implement prospective refund-policy revisions by any active Manager in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/MlandCancellationPolicyService.java`; accept a future `effectiveAt` and integer `wholeBookingRefundPercent` from 0 through 100, and compute `floor(collectedAmount × wholeBookingRefundPercent / 100)` whole VND without rewriting existing snapshots.
- [ ] T030 [US4] Implement remedy-case and central refund-policy routes from `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/contracts/workshop-settlement.openapi.yaml` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/controller/`.
- [ ] T031 [US4] Add replacement-first, handoff mismatch, unavailable/declined, default-100%, policy effective-time, historical-snapshot, and no-partial-refund coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/MlandCancellationRemedyIT.java`.

---

## Phase 7: Cross-cutting verification and handoff

- [ ] T032 [P] Contract-test every settlement public route, typed-error status, safe projection, authorization alternative, and `Idempotency-Key` replay in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/WorkshopSettlementContractIT.java`.
- [ ] T033 [P] After 003.1 T006/T032 establishes the shared ingress, contract-test only the 003.3 settlement consumer's verified-evidence acceptance, owner-qualified dispatch, and no-raw-provider-payload boundary in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/SettlementPaymentEvidenceIT.java`.
- [ ] T034 Run the non-production scenarios in `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/quickstart.md` and retain only redacted verification evidence under `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/`.
- [ ] T035 Review the approved configuration evidence for T005 before enabling real VNPay provider-dependent work; verify facade-only access, the reference-only Settlement-to-007 handoff boundary, and data-bearing upgrade/recovery evidence in `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/tasks.md`. Feature 007 provider configuration remains that feature's deployment responsibility.
- [ ] T036 Extend `WorkshopSettlementFacade` with an idempotent operation to create or return a direct-Mland add-on bill from an accepted OTA quote and explicit consent; Settlement validates consent and derives amount/currency from its accepted server-side quote, while leaving OTA customer-payment/refund/payout facts unchanged. Cover replay, rejected quote/consent and finance separation in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/OtaAddonBillFacadeIT.java`.

## Dependencies and execution order

```text
T001–T006 → T007–T012
T007–T012 → US1 (T013–T020), US2 (T021–T024), US3 (T025–T027), US4 (T028–T031)
T005 + T011 are required before provider evidence can settle/refund state.
T006 establishes Settlement's post-commit notification handoff and is independent of Feature 007 provider configuration.
All intended stories → T032–T035
```

## Parallel opportunities

- T001, T002, T004, T005, and T006 have separate owners/paths.
- After state foundations, T009, T010, and T012 affect separate components.
- T014 and T015 can proceed in parallel after T013's bill aggregate exists.
- US2, US3, and US4 can proceed in parallel once T007–T012 are complete.

## Implementation strategy

**MVP**: finish T001–T020, excluding only actions genuinely blocked by T005.
This delivers Staff bill/finalization with the provider boundary designed and
testable using controlled evidence. Then add whole-booking customer cancellation
(US2), session changes (US3), and Mland-caused remedies (US4).

All 35 tasks are intentionally unchecked and follow the required checklist format.
