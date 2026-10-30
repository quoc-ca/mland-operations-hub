# Tasks: Customer Workshop Booking Core

**Date created**: 2026-10-07
**Date updated**: 2026-10-31
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Input**: [spec.md](./spec.md), [research.md](./research.md), [plan.md](./plan.md),
[data-model.md](./data-model.md), [quickstart.md](./quickstart.md), the
[booking OpenAPI](./contracts/workshop-booking.openapi.yaml), the
[shared facade contract](../contracts/workshop-interfeature-facades.md), and the
[shared VNPay ingress contract](../contracts/vnpay-payment-ingress.openapi.yaml).

**Scope rule**: This is a pre-code implementation checklist. **BLOCKED** tasks
require the named configuration or owner boundary; no task permits inventing a
public endpoint/DTO, database schema/Flyway version, locking mechanism, role
mapping, provider secret, or notification provider behavior.

## Phase 1: Setup and shared boundaries

**Purpose**: establish Booking as the direct-Mland owner and consume only
approved cross-feature contracts.

- [X] T001 [P] Establish the `workshopbooking` Spring Modulith module boundary and add `ApplicationModules.verify()` coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/WorkshopBookingModuleTest.java`.
- [X] T002 [P] Implement 003.1's named `WorkshopBookingFacade`, including `ApplyVerifiedDepositPaymentEvidenceCommand` and final-balance conclusion consumption, in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/facade/` exactly as limited by `ppswbs-spec/features/003-workshop-management/contracts/workshop-interfeature-facades.md`.
- [X] T003 Rebase `WorkshopOperationsFacade` adapters in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/facade/` to consume `PublishedPackageSelectionQuery`, opaque UUID session references and reserve commands containing `packageReference`; retain facade-only sale eligibility, all-or-nothing reserve/commit/release, ticket projection and current entitlement projection. Provider dependency is satisfied by 003.2 T067--T075.
- [X] T004 Consume canonical account role, status, ownership and location scope through the approved FE-001 boundary in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/service/WorkshopBookingAuthorizationService.java`; Booking applies its own support-action rules against the booking's canonical `workshop_location.id` and must not use display roles, Firebase claims or retired codes as authority or edit FE-001.
- [ ] T005 **BLOCKED — external VNPay configuration**: obtain the environment-approved Merchant identity, runtime-only HMAC-SHA512 secret, HTTPS IPN registration, outbound QueryDR/refund authority, and redacted sandbox evidence listed in `ppswbs-spec/features/003-workshop-management/contracts/vnpay-configuration-preflight.md`; keep every credential outside the repository.
- [ ] T006 Establish the shared technical `paymentintegration` Modulith module and its one `GET /api/v1/integrations/payments/vnpay/events` controller in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/paymentintegration/`; verify once, deduplicate, resolve immutable owner-qualified merchant reference, and dispatch redacted evidence to exactly one 003.1/003.3 facade.
- [X] T007 Implement the approved post-commit `BookingNotificationReady` handoff and immutable delivery-payload facade in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/facade/`; publish reference-only data after the local transaction, let Feature 007 deduplicate/read the payload, and never let delivery outcome reverse Booking state. Verified by `WorkshopBookingLifecycleIT` (11 tests passed).
- [ ] T008 Establish the approved React + Vite route shell in `ppswbs_frontend/src/`: `/workshops`, `/workshops/:packageId`, `/workshops/:packageId/book`, `/workshop-booking/lookup`, `/workshop-booking/access`, and `/workshop-booking/:bookingCode/payment-return`; keep Magic-Link bearer data in the fragment only and make payment-return display-only.

---

## Phase 2: Foundational booking integrity

**Purpose**: retained aggregate state, migration safety, authorization, and
idempotency before a customer flow begins.

- [X] T009 Rebase approved Booking session-commitment entities/repositories in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/{entity,dao}/` to store only opaque UUID session references and the frozen Package selection snapshot; retain invoice, payment attempt, safe evidence receipt, delivery state, Magic-Link access, notification intent, business audit and idempotency outcome without Operations entities.
- [ ] T010 Create the append-only preservation migration and empty-database plus upgrade/recovery coverage for T009 in `ppswbs_backend/src/main/resources/db/migration/` and `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/WorkshopBookingMigrationIT.java`; reset only dev/test with no retained booking/customer data after preflight and do not choose a migration version without actual Flyway history.
- [X] T011 [P] Implement server-side authorization denial and minimal booking audit evidence in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/service/WorkshopBookingAuthorizationService.java`; denied commands have no side effect and audit excludes contact proof, raw Magic Link, QR, token, callback payload, and secret.
- [X] T012 [P] Implement client `Idempotency-Key` handling in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/service/BookingIdempotencyService.java`; same key plus semantically identical content returns the original outcome and changed reuse returns `409 IDEMPOTENCY_CONFLICT`.
- [X] T013 [P] Rebase typed safe `Problem`, Bean Validation, correlation and controller-only DTO adapters in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/{controller,dto}/` to the booking OpenAPI's `packageId`, UUID `sessionReferences` and removed Booking-owned discovery paths; do not expose Operations entities or duplicate 003.2 public discovery.
- [X] T014 Implement local transaction and facade-orchestration rules in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/service/BookingLifecycleService.java`; Booking writes plus reserve/commit/release must be all-or-none, gateway I/O is outside that transaction, and ticket projection follows successful confirmation only. Verified by `WorkshopBookingLifecycleIT` (11 tests passed, including zero-side-effect rejection, rollback on rejected reserve/commit, and projection only after committed holds).

**Checkpoint**: Booking has no direct Operations entity/repository access, no
business callback controller, and no confirmation path based on a browser redirect.

---

## Phase 3: User Story 1 — Discover a workshop package (P1)

**Goal**: Guests and Members see only published customer-selectable workshop
packages/sessions and can begin a booking flow.

**Independent Test**: Published eligible packages are visible; non-selectable
packages/sessions cannot start booking and do not disclose internal state.

- [ ] T015 [US1] Implement the React client adapter in `ppswbs_frontend/src/` that consumes the 003.2-owned public `/api/v1/workshop-packages` and `/api/v1/workshop-packages/{packageId}/sessions` routes; use required `locationId` and optional `from`/`to`, treat `remainingCapacity` as a non-guaranteed snapshot, and create no Booking backend discovery service.
- [ ] T016 [US1] Add client contract coverage in `ppswbs_frontend/src/` for public Package/session discovery, empty/unavailable results and opaque UUID `sessionReference`; prove that Booking publishes no duplicate discovery controller or route.
- [X] T017 [US1] Add Booking module-boundary coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/WorkshopDiscoveryBoundaryIT.java`; reject direct Operations persistence access and verify Package/session discovery remains owned by 003.2.
- [ ] T018 [US1] Implement React package listing/detail, location/date filtering, loading, empty, validation and booking-entry states under the approved T008 routes in `ppswbs_frontend/src/` using the T015 adapter.

---

## Phase 4: User Story 2 — Create a pending workshop booking (P1)

**Goal**: A customer creates one booking for one package and atomically holds N
seats in every required session.

**Independent Test**: Basic Ring holds N seats in one session; Silverclay holds
N seats in exactly two valid sessions; an invalid second session leaves neither
booking nor hold.

- [X] T019 [US2] Implement one-package booking validation in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/service/BookingCreationService.java`; require positive participant count and reject a generic multi-package cart or `BASE_PLUS_EXTRA` pricing.
- [X] T020 [US2] Implement Package/session selection pass-through in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/service/BookingCreationService.java`; forward package reference, participant count and distinct opaque UUID session references without hard-coding Basic Ring/Silverclay names or local count/location/gap rules, because Operations authorizes the current generic Package selection atomically.
- [X] T021 [US2] Implement frozen money snapshots in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/service/BookingPricingService.java`; `package_total = participant_count × price_per_participant`, `deposit = ceil(post_promotion_amount / 2)`, and `remaining = post_promotion_amount − deposit` are immutable whole-VND values.
- [X] T022 [US2] Implement all-or-nothing create-and-reserve through `WorkshopOperationsFacade` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/service/BookingCreationService.java`; send `packageReference`, participant count and UUID session references, map `PACKAGE_NOT_PUBLISHED`, `PACKAGE_SELECTION_INVALID` and `INSUFFICIENT_CAPACITY` safely, and persist one `PENDING_PAYMENT` booking/invoice only after every required session holds N seats.
- [X] T023 [US2] Implement `POST /api/v1/workshop-bookings` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/controller/` with `packageId` and UUID `sessionReferences` only; do not add Booking-owned discovery routes or locally authorize generic Package rules.
- [X] T024 [US2] Add Package-not-published, Package-selection-invalid (participant/count/location/minimum same-day slot gap), `INSUFFICIENT_CAPACITY`, `SALES_PAUSED`, concurrent-last-seat and local-rollback coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/BookingCreationIT.java`; assert Operations decides selection rules and Booking creates no partial booking/hold. Verified by `BookingCreationIT` (3 tests: Operations rejection variants, simulated local rollback, and concurrent last-seat single acceptance), `BookingCreationServiceTest` (3), `BookingPricingIT` (2), and `WorkshopBookingCreationControllerTest` (1); isolated H2 run passed all 9 tests.
- [X] T025 [US2] Add post-promotion odd-VND, deposit-ceiling, and exact-remaining-amount coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/BookingPricingIT.java`.
- [ ] T026 [US2] Implement React Guest/Member contact, Package-backed required-session selection, pending, validation and capacity-failure states under the approved T008 routes in `ppswbs_frontend/src/`; surface server selection errors without reimplementing Package rules client-side.

---

## Phase 5: User Story 3 — Pay the workshop deposit (P1) 🎯 MVP

**Goal**: A valid deposit confirms the one booking, commits all active holds,
and makes exactly N delivery-safe tickets available.

**Independent Test**: Valid matching signed evidence confirms once; browser
return, invalid, duplicate, mismatched, expired, and late evidence cannot
confirm or issue another ticket set.

- [X] T027 [US3] Implement deposit-attempt initiation and the 10-minute link lifecycle in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/service/DepositPaymentService.java`; use only the frozen 50-percent VND deposit while the 15-minute hold is active. Verified by `DepositPaymentServiceTest` (6), `BookingDepositPaymentAttemptServiceTest` (1), and `DepositPaymentControllerTest` (2), including cached idempotent replay, active-hold expiry and safe refusal after payment.
- [X] T028 [US3] Implement `POST /api/v1/workshop-bookings/{bookingCode}/payment-attempts` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/controller/`; the browser return reads committed state only and never confirms payment. Verified by `DepositPaymentControllerTest` (2 tests: response contract and required `Idempotency-Key`) and `DepositPaymentServiceTest` (6 tests); the application has no Booking browser-return confirmation endpoint, so provider redirect remains outside the confirmation path.
- [X] T029 [US3] Implement the `DEPOSIT` consumer for shared verified payment evidence in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/facade/`; verify frozen booking reference/amount/currency, commit all expected active holds or none, then confirm exactly once. Verified by `WorkshopBookingDepositEvidenceIT` (2 expected holds committed, receipt linked to the exact attempt, same-event replay commits once, unrelated event rejected), `WorkshopBookingFacadePaymentAttemptTest` (amount mismatch, wrong replay, and expired attempt), and `WorkshopBookingLifecycleIT` (multi-hold commit rejection leaves both holds RESERVED and booking pending); tests ran against isolated H2 only.
- [ ] T030 [US3] **BLOCKED — missing terminal IPN outcome state**: implement bounded booking-owned QueryDR recovery only after paymentintegration exposes whether a verified terminal IPN outcome was recorded. Current `PaymentIngressService` acknowledges failed provider responses without persisting or routing their outcome, so 003.1 cannot distinguish “no terminal IPN” from “terminal failure” safely. Then request only at minute 10 and minute 14 while the hold remains active, and consume only shared verified evidence.
- [X] T031 [US3] Implement post-confirmation ticket projection and delivery intent in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/service/BookingConfirmationService.java`; obtain only the 003.2 delivery-safe ticket/session entitlement projection, require exactly the participant count and expected valid session entitlements, reject duplicate/blank identities, create no participant identity or ticket fallback, and let post-commit delivery failure leave confirmation unchanged. Verified by `WorkshopBookingLifecycleIT` (2 participants × 2 sessions, UUID/code preservation, two entitlements per ticket, missing projection does not fabricate/partially confirm, and delivery failure isolation) and `WorkshopBookingDepositEvidenceIT` (two-session ticket projection); isolated H2 run passed 24 tests across lifecycle, evidence, deposit and payment-attempt suites.
- [X] T032 [US3] Add a single-ingress contract test for checksum failure, duplicate delivery, unknown/ambiguous reference, one-owner dispatch, and no broadcast in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/paymentintegration/VnpayPaymentIngressIT.java`. Verified with actual H2 repositories and mocked verified checksum/consumers: invalid signature has no write/dispatch, unknown owner is not recorded or broadcast, and duplicate deposit plus final-payment deliveries route only to their registered consumer (`VnpayPaymentIngressIT`, 3 tests; `PaymentIngressServiceTest`, 9; controller test, 1).
- [ ] T033 [US3] Add signed-valid, invalid, reference/amount/currency mismatch, duplicate/out-of-order, redirect-only, QueryDR timing, expiry, and late-payment-follow-up coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/DepositPaymentIT.java`.
- [X] T034 [US3] Add N-ticket and Silverclay two-entitlement, no-attendance-leak, all-holds-or-none, and delivery-failure-isolation coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/BookingConfirmationIT.java`. Verified by `BookingConfirmationIT` (3 tests: two safe tickets with both session entitlements and no attendance/QR disclosure, rejected multi-hold commit leaves booking pending without tickets, and post-commit delivery failure preserves confirmation); isolated H2 run passed all 3 tests.
- [ ] T035 [US3] Implement React payment-pending, display-only gateway-return, confirmation-ticket, and retry states under the approved T008 routes in `ppswbs_frontend/src/`.

---

## Phase 6: User Story 4 — Look up and recover a booking (P2)

**Goal**: Guests, owning Members, and a valid Magic Link can read only their
safe booking/ticket projection and recover without credential persistence.

**Independent Test**: Valid Guest proof, Member ownership, or Magic Link works;
invalid/revoked/expired proof reveals no booking data and recovery stays safe.

- [X] T036 [US4] Implement Guest booking-code plus exactly one stored contact proof and authenticated Member ownership lookup in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/service/BookingLookupService.java`; build the schedule only from the current Operations projection keyed by session UUID and current start/end, pending reads contain no ticket, and unauthorised reads disclose nothing. Verified by `BookingLookupServiceTest` (4/4: Guest proof and PII exclusion, denial, Member ownership, current confirmed QR projection), `BookingConfirmationIT` (3/3), `WorkshopBookingLifecycleIT` (12/12), `WorkshopBookingDepositEvidenceIT` (1/1), and `WorkshopBookingModuleTest` (7/7); isolated H2 runs.
- [ ] T037 [US4] Implement reusable Magic-Link hash, expiry, revoke/rotate, fragment-token HTTPS exchange, and memory-only projection semantics in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/service/MagicLinkService.java`; never persist/log the raw token and retain Guest code-plus-stored-phone recovery.
- [ ] T038 [US4] Implement pending-hold release through `WorkshopOperationsFacade` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/service/BookingHoldReleaseService.java`; release every active hold once, preserve `CANCELLED/UNPAID`, and issue no ticket.
- [ ] T039 [US4] Implement self-service and Staff-in-location or global Manager internal confirmation resend, global Manager-only Magic-Link `REVOKE`/`ROTATE`, separate rate limits, global Manager-only late-payment `RECORD`/`ESCALATE`, and minimal business audit in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/service/BookingRecoveryService.java`.
- [ ] T040 [US4] Implement booking lookup, Magic-Link exchange, Member booking read, pending-hold release, confirmation-delivery, Magic-Link incident, and late-payment-follow-up routes from `ppswbs-spec/features/003-workshop-management/003.1-workshop-booking/contracts/workshop-booking.openapi.yaml` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/controller/`.
- [ ] T041 [US4] Add Guest proof, Member ownership, pending/confirmed/cancelled projection, Magic-Link reuse/expiry/revoke/rotate, resend/audit separation, location-scope denial, and release idempotency coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/BookingLookupAndRecoveryIT.java`.
- [ ] T042 [US4] Implement React lookup, pending-resume, confirmed-ticket, cancelled, Magic-Link fragment-exchange, and recovery states under the approved T008 routes in `ppswbs_frontend/src/`.

---

## Phase 7: Cross-cutting verification and handoff

- [ ] T043 [P] Contract-test every Booking public route, UUID `sessionReferences`, typed Package-selection errors, proof/ownership alternative, Idempotency-Key replay and safe projection in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/WorkshopBookingContractIT.java`; assert public Package discovery is absent because 003.2 owns it.
- [ ] T044 [P] Verify facade-only dependencies, Published Package-selection consumption, no direct Operations persistence access, no duplicate discovery route and paymentintegration-to-owner dispatch boundaries in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/WorkshopBookingModuleBoundaryIT.java`.
- [ ] T045 Run non-production [quickstart scenarios](./quickstart.md), preservation-upgrade/recovery checks, React UI/API flow checks, and retain only redacted evidence in the approved CI artifact rather than `quickstart.md`.
- [ ] T046 Review approved configuration evidence for T005 before enabling real VNPay provider-dependent work in `ppswbs-spec/features/003-workshop-management/003.1-workshop-booking/tasks.md`; Feature 007 provider configuration remains that feature's deployment responsibility.
- [ ] T047 Add OTA create-or-return booking operation to `WorkshopBookingFacade` using provider plus external booking reference as stable business identity; coordinate only with 003.2's atomic capacity-and-ticket operation, return accepted only with exactly one booking reference and N ticket references, and prove replay/conflict/rejection behavior in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/OtaBookingFacadeIT.java`. OTA payment must not change direct-Mland financial state, and contact remains absent unless provider allowlist approval exists. Depends on 003.2 T076.
- [X] T048 Implement authenticated Member `POST /api/v1/workshop-booking-quotes` preview and persistence in `features/workshopbooking/`; derive Member identity from canonical Account authorization, read the published Package and every selected session through `WorkshopOperationsFacade`, call Loyalty `previewBenefits`, and persist `quoteRef`, exact package/session facts, benefit selection, `termsDigest`, returned breakdown and `expiresAt = createdAt + 30 minutes`. Expose `expiresAt`; preview reserves no capacity, points or voucher quota; Guest flow is unchanged.
- [ ] T049 Implement Member quote acceptance in `BookingCreationService` and `BookingLifecycleService`; lock and bind the quote to its Member, reject acceptance at or after `expiresAt` with `409 QUOTE_EXPIRED` and no booking/hold/benefit reservation, revalidate authoritative Operations facts and Loyalty terms, reserve all selected session capacity and invoke `reserveAcceptedTransaction` in one local transaction, then freeze returned `postPromotionAmount`, `deposit = ceil(post / 2)` and `remaining = post - deposit`. Pass canonical session UUID references throughout; remove the synthetic `new UUID(0L, sessionId)` path from the new flow. Depends on T019-T023 and 003.2 T078.
- [ ] T050 Connect authoritative Booking/Settlement lifecycle outcomes to Loyalty: verified deposit → `settleAcceptedBenefits`; fully completed sessions plus fully paid invoice → `grantEarnedPoints`; confirmed unpaid expiry → `releaseHeldBenefits`; eligible cancellation → `restoreCancelledWorkshopBenefits`; final no-show → `finishIneligibleEarn`. Lock and version the source outcome, pass stable source keys/proof references, and never call Loyalty with raw payment payloads. Depends on T049 and the relevant 003.3 outcome contracts.
- [ ] T051 Add `LoyaltyWorkshopSourceIntegrationIT` for Member quote preview/ownership, multi-session digest, changed quote rejection, atomic rollback across capacity and Loyalty, ceil deposit, verified-deposit settlement, one-time earned points, expiry/cancellation/no-show outcomes, and Guest no-benefit compatibility. Run after datasource-backed test configuration is available; 006 reviews and runs T057.

## Dependencies and execution order

```text
T001–T008 → T009–T014
T009–T014 → US1 (T015–T018), US2 (T019–T026), US3 (T027–T035), US4 (T036–T042)
003.2 T067--T075 are required before T003, T009--T010, T013, US1 and US2 Package-dependent work.
T005 + T006 + T029 are required before a provider result may confirm Booking.
T007 precedes the post-commit handoff and is independent of Feature 007's provider configuration; T008 precedes React story tasks.
All intended stories → T043–T046
003.2 T076 → T047
T048 → T049 → T050 → T051; T048 and T049 require 003.2 T078; T050 requires 003.3 authoritative outcome integration.
```

## Parallel opportunities

- T001, T002, T004, T005, T007, and T008 have distinct owners/paths.
- T011, T012, and T013 affect distinct components after persistence is available.
- T021 and T020 can be implemented in parallel after booking validation exists.
- T024 and T025 are independent test files; T032 and T033 are independent payment-boundary tests.

## Implementation strategy

**MVP**: complete T001–T035, with real-provider payment actions waiting only
on T005. This delivers discovery, one-package atomic booking, and
deposit-confirmed ticket delivery. Then add safe lookup/recovery (US4).

All checklist items remain unchecked until implementation evidence is available.
