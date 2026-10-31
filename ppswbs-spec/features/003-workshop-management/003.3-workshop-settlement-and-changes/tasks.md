# Tasks: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-10-31
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
available. The shared QueryDR/refund adapter may be built and tested with fake
transport before T005; live provider calls remain gated by T005. No task
authorizes inventing a role mapping, provider secret, or notification mechanics.

## Phase 1: Setup and shared boundaries

**Purpose**: establish the `workshopsettlement` module and consume only approved
owner boundaries.

- [X] T001 [P] Establish the `workshopsettlement` Spring Modulith module boundary and add `ApplicationModules.verify()` coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/WorkshopSettlementModuleTest.java`.
- [X] T002 [P] Implement 003.3's named `WorkshopSettlementFacade`, including `ApplyVerifiedSettlementPaymentEvidenceCommand`, in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/facade/` exactly as limited by `ppswbs-spec/features/003-workshop-management/contracts/workshop-interfeature-facades.md`.
- [X] T003 Consume `WorkshopBookingFacade` confirmed financial projection and idempotent final-balance conclusion, and `WorkshopOperationsFacade` readiness/replacement/refund/cancellation outcomes, only through facade adapters in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/facade/`.
- [X] T004 Consume current canonical Mland account role/status, ownership and location facts through the approved FE-001 boundary in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/WorkshopSettlementAuthorizationService.java`; grant every active Manager global authority for Manager-authorized actions (including refund-policy administration), keep Staff actions location-scoped and Member ownership enforced, and do not use display roles, Firebase claims or retired codes as authority or edit FE-001.
- [ ] T005 **BLOCKED — external VNPay configuration**: obtain the environment-approved Merchant identity, runtime-only HMAC-SHA512 secret, HTTPS IPN registration, outbound QueryDR/refund authority, and redacted sandbox evidence listed in `ppswbs-spec/features/003-workshop-management/contracts/vnpay-configuration-preflight.md`; keep every credential outside the repository.
- [ ] T006 Implement the approved post-commit `SettlementNotificationReady` handoff and notification-delivery payload facade for verified final-payment/refund outcomes in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/facade/`; publish references only after the local transaction, let Feature 007 deduplicate/read the payload, retain recipient/content for at most 30 days, include no payment-action link, and never let delivery outcome reverse settlement truth. Remains blocked until the contract provides a safe source for the stored recipient without giving 003.3 access to Booking persistence or exposing PII in events/audits/logs.

---

## Phase 2: Foundational settlement integrity

**Purpose**: create shared retained state, authorization, audit, idempotency, and
provider-boundary behavior before any user-story command.

- [X] T007 Create approved JPA entities/repositories for one booking-scoped final bill, immutable charge/correction, settlement case, redacted provider-evidence conclusion, policy snapshot, business audit, and idempotency outcome in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/{entity,dao}/`.
- [X] T008 Create an append-only preservation migration and empty-database plus upgrade/recovery coverage in `ppswbs_backend/src/main/resources/db/migration/` and `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/WorkshopSettlementMigrationIT.java`; reset only dev/test with no retained booking/customer data after preflight.
- [X] T009 [P] Implement server-side authority denial and minimal business-audit evidence in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/WorkshopSettlementAuthorizationService.java`; denied commands have no side effect and audit excludes raw token, QR, callback payload, secret, or unnecessary PII.
- [X] T010 [P] Implement client `Idempotency-Key` handling in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/SettlementIdempotencyService.java`; same key plus semantically identical content returns the original outcome and changed reuse returns `409 IDEMPOTENCY_CONFLICT`.
- [X] T011 Implement the shared `paymentintegration` dispatch consumer in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/facade/`; it accepts only redacted verified `FINAL_PAYMENT`/`REFUND` evidence, checks frozen reference/amount/currency, and never exposes a VNPay HTTP controller in this module.
- [X] T012 [P] Implement typed safe `Problem` mapping, Bean Validation, Booking-facade customer ownership/contact-proof decisions, and controller-only transport adapters from `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/contracts/workshop-settlement.openapi.yaml` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/{controller,dto}/`; never return or log Guest contact PII. Verified with `WorkshopSettlementProblemHandlerTest`, `WorkshopFinalBillCustomerAccessTest`, `SettlementDtoValidationTest`, `WorkshopSettlementAuthorizationServiceTest`, and `CatalogueAddOnServiceTest` (13 tests passing).

**Checkpoint**: No story command may bypass authorization, immutable evidence,
one-final-bill integrity, owner facade, or shared VNPay ingress.

---

## Phase 3: User Story 1 — Staff settles a completed booking/package (P1) 🎯 MVP

**Goal**: Staff creates and finalizes one bill only after the final required
session; valid final-payment evidence settles it once, and package output is
handed over only after the final balance is verified as paid.

**Independent Test**: Basic Ring and Silverclay create exactly one final bill;
valid final evidence resolves it once, while invalid, duplicate, mismatched,
late, browser-return-only evidence, or an unpaid balance cannot release output.

- [X] T013 [US1] Implement one-final-bill creation from the 003.1 frozen projection and 003.2 `ready` conclusion in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/FinalBookingBillService.java`; never calculate voucher, points, deposit, or participant/session/ticket bills again.
- [X] T014 [P] [US1] Implement customer fixed catalogue add-on selection in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/CatalogueAddOnService.java`; verify Member ownership or Guest booking code plus exactly one stored phone/email through the Booking facade and derive the eligible package/location price snapshot through the Operations facade before recording an immutable charge.
- [X] T015 [P] [US1] Implement Staff/Manager variable-charge confirmation in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/VariableChargeService.java`; accept description, whole-VND amount, and reason only, then retain an immutable audited correction path rather than editing evidence.
- [X] T016 [US1] Implement finalization and package-output release gating in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/FinalBookingBillService.java`; require final required-session completion, active booking-location Staff authority, and zero balance for every role. No Manager or Staff exception may waive or defer the balance. Verified zero-balance transition and unpaid denial without mutation in `FinalBookingBillServiceTest` (5), and Manager/Staff API denial before idempotency or finalization in `WorkshopFinalBillUnpaidFinalizationTest` (1); included in the focused 22-test passing run.
- [X] T017 [US1] Implement idempotent final-payment/refund evidence application in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/FinalPaymentService.java`; consume T011 only, record immutable safe evidence once, and send 003.1 only `RecordFinalBalanceResolvedCommand` after a valid final-balance resolution. Verified by `WorkshopSettlementEvidenceConsumerTest` (5), `WorkshopSettlementContractIT` (2), and `WorkshopSettlementModuleTest` (2): 9 tests passing; no provider calls or database changes.
- [ ] T018 [US1] Implement the specified separate `FINAL_PAYMENT` checkout through the `paymentintegration` owner boundary. Staff/Manager starts an idempotent attempt for the server-derived outstanding VND balance; the response supplies a VNPay URL/QR that can be shown to the customer. Bind owner registration to booking reference, unique merchant reference, exact amount and VND currency; dispatch only signed verified payment evidence. The attempt expires 10 minutes after creation. Do not create another attempt while the prior one is pending/unknown; after expiry, require QueryDR confirmation that it did not succeed before allowing a new attempt. No credit period, reminders or link reissues. Implement acceptance tests against the OpenAPI operation; see the T018 resolution in `ppswbs_backend/ERROR-LOG.md`.
- [X] T019 [US1] Implement `GET/POST /workshop-bookings/{bookingCode}/final-bill`, `POST /final-bill/charges`, and `POST /final-bill/finalizations` exactly from `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/contracts/workshop-settlement.openapi.yaml` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/controller/`; finalization accepts an empty command and has no Manager-exception flag. Verified route binding, customer-safe projections and rejection of the legacy Manager override field in `WorkshopFinalBillControllerTest` (4), plus DTO validation (3); included in the focused 22-test passing run.
- [ ] T020 [US1] Add final-bill, charge, finalization, valid/duplicate/mismatched/late evidence, unpaid-finalization denial for Staff and Manager, verified-payment-before-output-handover, ownership/proof, browser-return-denial, and reference-only notification-handoff/delivery-failure-isolation coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/FinalBookingSettlementIT.java`; do not test deferred expiry, reminders, or reissues because those flows are out of scope.

---

## Phase 4: User Story 2 — Customer cancels a whole booking (P1)

**Goal**: An owning Member or Guest with booking proof opens an auditable
whole-booking cancellation case; ticket/capacity effects occur only after a
verified refund outcome.

**Independent Test**: Eligible and ineligible requests receive safe case
projections; no ticket/capacity consequence appears until the shared ingress
dispatches verified refund evidence.

- [ ] T021 [US2] Implement whole-booking customer-cancellation case opening and safe read projection in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/CustomerBookingCancellationService.java`; use the approved Operations eligibility and Booking verified-deposit facades, reject session/ticket/participant partial cancellation and Magic-Link-only authority, snapshot the applicable policy and aggregate refund amount, and reject a computed 0 VND refund without changing booking/ticket/capacity state. Since the workshop product is delivered only after its session, requiring every required session to remain unstarted also prevents cancellation after product handover; do not add a separate delivery-state query. H2 evidence passes: `CustomerCancellationIT` (5), `CustomerBookingCancellationServiceTest` (6), and `CustomerBookingCancellationControllerTest` (4); keep unchecked until V45's isolated MySQL migration/upgrade test passes.
- [ ] T022 [US2] Implement automatic policy-calculated refund orchestration in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/WorkshopRefundService.java`; calculate aggregate once then allocate oldest-payment-first, persist one operation per original payment before dispatch, treat accepted/unknown as pending, QueryDR immediately after acceptance and every five minutes for up to 24 hours when the refund transaction number is known, and call `WorkshopOperationsFacade` only after signed QueryDR evidence matches merchant reference, provider transaction number, refund type, exact allocated amount, and terminal transaction status `00` (`05`/`06` pending; `09` rejected). Never resend a refund; an unknown request without a refund transaction number remains pending for manual Merchant Admin investigation. Fake-provider/H2 evidence passes: `WorkshopRefundServiceTest` (5), `VnpayMerchantOperationsAdapterTest` (14), `VnpayPaymentIngressIT` (3), and `SettlementPaymentEvidenceIT` (3); keep unchecked until V45's isolated MySQL migration/upgrade test passes.
- [X] T023 [US2] Implement booking-scoped cancellation-case POST/GET routes from `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/contracts/workshop-settlement.openapi.yaml` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/controller/`. Verified member body omission, guest proof binding/non-disclosure, email validation, and customer-safe GET binding with `CustomerBookingCancellationControllerTest` (4 passing); covered with Modulith boundary test.
- [X] T024 [US2] Add Guest proof, Member ownership, partial-cancellation denial, aggregate rounding/oldest-first allocation, partial and full provider request, pending/unknown/replay, verified-only resolution, and no-pre-success ticket/capacity-effect coverage across `CustomerCancellationIT`, `CustomerBookingCancellationServiceTest`, and `WorkshopRefundServiceTest`. Verified the complete acceptance set with the focused cancellation/refund/VNPay/Modulith command: 42 tests passed, 0 failures/errors/skips.

---

## Phase 5: User Story 3 — Manager changes future required sessions (P2)

**Goal**: A Manager replaces the complete future required-session set atomically
without changing package price or persistent ticket QR.

**Independent Test**: Operations applies the complete compatible replacement or
none; Silverclay retains its location, distinctness, chronological, and same-day
gap constraints.

- [X] T025 [US3] Implement complete required-session-change case orchestration via `WorkshopOperationsFacade` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/RequiredSessionChangeService.java`; pass only the complete target set and preserve frozen price/deposit. Verified `RequiredSessionChangeServiceTest` (3 tests): complete target set forwarded, partial set and unlinked Mland cancellation rejected before Operations, Manager authorization/audit, and case projection; no bill/Booking mutation. No migration or MySQL write.
- [X] T026 [US3] Implement required-session-change POST/GET routes from `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/contracts/workshop-settlement.openapi.yaml` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/controller/`. POST returns 201 and binds Idempotency-Key/correlation/validated command; GET is customer-safe and authorizes Member ownership, Guest exactly-one-contact proof through the Booking facade, global Manager, or location-scoped Staff. Verified `RequiredSessionChangeControllerTest` (3), `RequiredSessionChangeServiceTest` (5), and `WorkshopSettlementModuleTest` (2): 10 tests pass; no DB migration or write.
- [X] T027 [US3] After Operations T082 persists booking-time package policy, add one/two-session change, no-capacity rollback, persistent QR, current ticket/session projection, and Silverclay same-location/distinct/chronological/same-day-gap acceptance coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/RequiredSessionChangeIT.java`; include fail-closed behavior for a legacy booking without a policy snapshot. Evidence: `RequiredSessionChangeIT` passed on MySQL 8.0.43 against the already-migrated isolated `ppswbs_workshop_test_20261029` schema with `ddl-auto=none` and Flyway disabled: 5 tests, 0 failures/errors/skips. Covers one/two-session replacement, current entitlement projection and persistent QR, insufficient-capacity rollback, missing legacy policy snapshot, and Silverclay location, distinctness, chronology, and same-day slot-gap rejection.

---

## Phase 6: User Story 4 — Manager remedies Mland-caused package disruption (P2)

**Goal**: Mland offers a replacement first; `UNAVAILABLE` or `DECLINED` then
allows one auditable whole-package remedy/refund using the confirmation-time
policy snapshot.

**Independent Test**: Remedy requires the same cancellation handoff as the
replacement; prospective policy changes do not rewrite historical bookings.

- [X] T028 [US4] Implement Mland-caused remedy case orchestration in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/MlandCancellationRemedyService.java`; require the matching 003.2 `cancellationHandoffReference` and accept only `UNAVAILABLE` or `DECLINED` replacement outcomes. The service must require the durable booking-time policy snapshot produced by 003.1 T061, calculate from verified collected payment evidence, retain an immutable remedy amount snapshot, and dispatch per-original-payment refund allocations only after the remedy transaction commits. Verified by `MlandCancellationRemedyServiceTest` (2/2: mismatched handoff rejected; aggregate 50% of 100,001 VND becomes 50,000 VND and is allocated oldest payment first) and `WorkshopRefundServiceTest` (6/6: refund dispatch/QueryDR path); broader replacement-first and persistence acceptance remains T031.
- [X] T029 [US4] Implement prospective refund-policy revisions by any active Manager in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopsettlement/service/MlandCancellationPolicyService.java`; accept a future `effectiveAt` and integer `wholeBookingRefundPercent` from 0 through 100, and compute `floor(collectedAmount × wholeBookingRefundPercent / 100)` whole VND without rewriting existing snapshots.
- [X] T030 [US4] Implement remedy-case POST/customer-safe GET and central refund-policy GET/POST routes from `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/contracts/workshop-settlement.openapi.yaml`. `MlandCancellationRemedyController` delegates opens to the Manager-authorized service and permits safe reads only after Member ownership, Guest contact proof, or scoped Staff/global Manager authorization; GET is anonymously reachable only so Guest proof can be evaluated. Refund-policy routes use the server-resolved `CurrentAccountActor` (not the raw Authorization header) and retain Manager-only global authorization. Verified by the focused T030 suite: 16 tests passed, 0 failures/errors/skips across both controller tests, policy service (3), remedy service (4), and remedy acceptance IT (5); Spring Boot context startup also discovered the new mappings.
- [X] T031 [US4] Add replacement-first, handoff mismatch, unavailable/declined, default-100%, policy effective-time, historical-snapshot, and policy-derived partial-refund allocation acceptance coverage across `MlandCancellationRemedyIT`, `BookingConfirmationIT`, and `MlandBookingRefundPolicySnapshotServiceTest`. Partial allocations follow the approved aggregate-rounding, oldest-payment-first rule; a future revision must not rewrite a booking-time snapshot. Verified by the focused suite: 19 tests passed, 0 failures/errors/skips (MlandCancellationRemedyIT 5/5; BookingConfirmationIT 3/3; policy snapshot 3/3; remedy service 2/2; refund/QueryDR 6/6). H2 evidence only; V46 MySQL migration remains unverified.

---

## Phase 7: Cross-cutting verification and handoff

- [X] T032 [P] Contract-test every settlement public route, typed-error status, safe projection, authorization alternative, and `Idempotency-Key` replay in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/WorkshopSettlementContractIT.java`. Verified 13 controller operations and required POST idempotency headers against the published route contract in `WorkshopSettlementContractIT` (3 tests); focused route/problem/controller suite passed (20 tests before the route-mapping assertion was added), then the updated contract IT passed (3); authorization, idempotency replay, customer access, cancellation, session-change, and remedy service tests passed (24). Existing customer-safe controller assertions cover Guest proof non-disclosure and Member access. All runs had 0 failures/errors/skips; no database/provider calls.
- [X] T033 [P] After 003.1 T006/T032 establishes the shared ingress, contract-test only the 003.3 settlement consumer's verified-evidence acceptance, owner-qualified dispatch, and no-raw-provider-payload boundary in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/SettlementPaymentEvidenceIT.java`. Verified by `SettlementPaymentEvidenceIT` (3), `WorkshopSettlementEvidenceConsumerTest` (5), and `WorkshopSettlementModuleTest` (2): 10 tests passing; invalid signature and amount mismatch do not dispatch, verified FINAL_PAYMENT dispatches only redacted owner-qualified evidence.
- [ ] T034 Run the non-production scenarios in `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/quickstart.md` and retain only redacted verification evidence under `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/`.
- [ ] T035 Review the approved configuration evidence for T005 before enabling real VNPay provider-dependent work; verify facade-only access, the reference-only Settlement-to-007 handoff boundary, and data-bearing upgrade/recovery evidence in `ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/tasks.md`. Feature 007 provider configuration remains that feature's deployment responsibility.
- [ ] T036 Extend `WorkshopSettlementFacade` with an idempotent operation to create or return a direct-Mland add-on bill from an accepted OTA quote and explicit consent; Settlement validates consent and derives amount/currency from its accepted server-side quote, while leaving OTA customer-payment/refund/payout facts unchanged. Cover replay, rejected quote/consent and finance separation in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopsettlement/OtaAddonBillFacadeIT.java`.
- [X] T037 [P] Implement the shared VNPay Merchant API QueryDR/refund adapter owned by `paymentintegration`, exposed through typed `common.payment` technical ports to avoid a Modulith cycle. Persist the caller operation reference, request fingerprint, one generated provider RequestId and safe state before network I/O; identical replay returns the recorded state and changed reuse conflicts. Use official per-command request HMAC and verify response HMAC plus terminal/transaction references. No automatic retries: transport uncertainty or response `99` is `UNKNOWN`; refund response `00` is request acceptance only. QueryDR uses the original payment `vnp_CreateDate` and returns verified reconciliation status without treating browser Return as payment truth. Add an append-only Flyway migration and fake-HTTP/unit acceptance for signing, response tampering, idempotency replay/conflict, timeout/`99` UNKNOWN, and accepted-versus-settled distinction. Live provider calls require T005. Code: `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/common/payment/` and `features/paymentintegration/`; tests: `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/paymentintegration/`. Evidence: append-only `V34__vnpay_merchant_operations.sql`; `VnpayMerchantApiSignerTest` (4), `VnpayMerchantOperationsAdapterTest` (6), and H2 `VnpayPaymentIngressIT` (3) passed. No live QueryDR/refund request was sent.
- [X] T038 [P] Extend T037 to VNPay partial refunds: full allocations use transaction type `02`, partial allocations use `03`, request amount is the exact allocated VND amount multiplied by 100, signed refund responses must match merchant reference, amount, and type but code `00` remains acceptance only, and no allocation may exceed the immutable original payment amount. Add refund-specific QueryDR by the returned provider transaction number; only a signed matching query with API response `00` and transaction status `00` is verified (`05`/`06` pending; `09` rejected). Persist/validate the immutable original payment create date in owner registration and retain provider transaction number/date for Settlement-owned final-payment evidence. Verified with `VnpayMerchantOperationsAdapterTest` (14), `VnpayPaymentIngressIT` (3), and `SettlementPaymentEvidenceIT` (3): all pass using fake VNPay transport/H2; no live provider acceptance is claimed.

## Dependencies and execution order

- Approved refund-payment boundary work: 003.2 Operations cancellation facts;
  003.1 verified deposits; 003.3 verified final payments; and paymentintegration
  original provider create date/transaction number. VNPay partial refund adapter
  acceptance is tracked by T038 and is required before T022 may dispatch.

```text
T001–T006 → T007–T012
T037 can be implemented with fake transport independently of T005; any live QueryDR/refund operation requires T005.
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

Task markers above reflect implementation evidence recorded in this file; an unchecked task may still have partial code and must be verified against its acceptance criteria.
