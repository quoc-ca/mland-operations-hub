# Tasks: Customer Workshop Booking Core

**Date created**: 2026-10-07
**Date updated**: 2026-10-25
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature Branch**: `003.1-workshop-booking`
**Status**: Rebased task plan — implementation is blocked until listed contract
and environment gates are approved.

**Input**: [spec.md](spec.md), [research.md](research.md), [plan.md](plan.md),
[data-model.md](data-model.md), [quickstart.md](quickstart.md), and
[workshop-booking.openapi.yaml](contracts/workshop-booking.openapi.yaml).

**Scope rule**: Never invent a public endpoint, DTO field, schema, migration
version, locking mechanism, role mapping, or provider configuration. Cross-feature
facades are internal synchronous boundaries, not HTTP APIs.

## Phase 1: Approval and delivery gates

**Purpose**: Resolve decisions that implementation agents must not infer.

- [ ] T001 Approve 003.2 capacity-facade and ticket-projection-facade boundary types plus result/error categories in `ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/plan.md`.
- [ ] T002 Approve public REST request/response, validation, error, and compatibility details in `ppswbs-spec/features/003-workshop-management/003.1-workshop-booking/contracts/workshop-booking.openapi.yaml`.
- [ ] T003 Approve VNPay environment values, callback authentication operation, QueryDR configuration, and safe secret channel in `ppswbs-spec/features/003-workshop-management/003.1-workshop-booking/plan.md`.
- [ ] T004 Approve data-bearing-environment release owner, preservation migration approach, upgrade evidence, and recovery/rollback runbook in `ppswbs-spec/features/003-workshop-management/003.1-workshop-booking/plan.md`.
- [ ] T005 Approve React booking route, screen-state, validation, and recovery design beyond Magic-Link fragment exchange in `ppswbs_frontend/`.
- [ ] T006 Synchronize approved FE-001 role vocabulary and Mland account/scope authority, and reconcile shared booking/ring-design language with the approved 003.1 V1 boundary, in `ppswbs-spec/spec-general.md` and `ppswbs-spec/APIs.md`.

**Checkpoint**: T001–T006 block code. An implementation agent must report the
relevant design blocker rather than guess.

---

## Phase 2: Foundational booking module

- [ ] T007 [BLOCKED until T001] Establish the `workshopbooking` Modulith boundary and permitted 003.2 facade dependencies in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T008 [P] [BLOCKED until T004] Add only the approved append-only aggregate migration and MySQL preservation/upgrade coverage in `ppswbs_backend/src/main/resources/db/migration/` and `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T009 [BLOCKED until T001] Implement approved persistence for Booking, session commitment, invoice, payment evidence, delivery state, and safe audit evidence in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T010 [BLOCKED until T001] Implement the synchronous 003.2 capacity-facade consumer for eligibility, all-or-nothing reserve/commit, and idempotent release in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/facade/`.
- [ ] T011 [BLOCKED until T001] Implement the synchronous 003.2 ticket-projection-facade consumer retaining only ticket/QR reference, ticket code, schedule, branch, and entitlement projection in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/facade/`.
- [ ] T012 [BLOCKED until T006] Implement Firebase identity verification and Mland account status, ownership, entitlement, and branch-scope checks through approved Account boundaries in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T013 [P] [BLOCKED until T002] Implement approved safe problem responses, correlation, validation, and Idempotency-Key handling in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T014 [P] [BLOCKED until T001,T004] Add MySQL tests proving every booking-critical command is one local transaction across Booking writes and the capacity facade, rolls back partial local work, and excludes gateway I/O in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T015 [P] [BLOCKED until T001] Add Modulith tests proving Booking reads no Operations entities/repositories and depends only on approved facades in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/`.

---

## Phase 3: User Story 1 — Discover a workshop package (Priority: P1)

**Goal**: Browse customer-selectable published packages and their booking entry.

**Independent Test**: Published packages are visible; unavailable ones cannot
begin booking.

- [ ] T016 [US1] [BLOCKED until T001,T002] Implement approved package/session browse-detail reads in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T017 [P] [US1] [BLOCKED until T001] Add integration tests for published filtering, customer-selectable details, unavailable packages, and safe not-found outcomes in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T018 [US1] [BLOCKED until T005,T002] Implement approved React package listing/detail states and booking entry, including approved user-visible E2E coverage that proves listing-to-booking entry takes no more than three primary interactions, in `ppswbs_frontend/src/`.

---

## Phase 4: User Story 2 — Create a pending workshop booking (Priority: P1)

**Goal**: Create one pending booking for one package and atomically hold every
required session.

**Independent Test**: Basic Ring holds N seats in one session; Silverclay holds
N seats in two valid sessions; invalid input leaves no booking or partial hold.

- [ ] T019 [US2] [BLOCKED until T001,T002,T012] Implement one-package contact, positive participant-count, and frozen even-VND `PER_PERSON` validation in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T020 [US2] [BLOCKED until T001,T002] Implement Basic Ring one-session and direct-Mland Silverclay exactly-two-session selection rules, including distinct, same-branch, chronological, and non-adjacent-same-day validation in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T021 [US2] [BLOCKED until T001,T009,T010] Implement one local-transaction create-and-reserve command that persists one `PENDING_PAYMENT` booking, one exact 50-percent invoice, and every required hold or rolls back all work in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T022 [US2] [BLOCKED until T002] Implement approved pending-booking REST transport without a multi-package cart or server-rendered flow in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T023 [P] [US2] [BLOCKED until T001,T009,T010] Add MySQL concurrency and rollback tests for full capacity, `SALES_PAUSED`, duplicate session, cross-branch, adjacent same-day, and last-seat races in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T024 [P] [US2] [BLOCKED until T001,T009] Add tests proving `total = participant_count × price_per_participant`, even VND price, and exact 50-percent deposit in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T025 [US2] [BLOCKED until T005,T002] Implement approved React Guest/Member contact, session-selection, validation, pending, and capacity-failure states in `ppswbs_frontend/src/`.

---

## Phase 5: User Story 3 — Pay the workshop deposit (Priority: P1)

**Goal**: A valid VNPay result confirms one booking once and makes its ticket
set deliverable.

**Independent Test**: Only valid signed matching evidence confirms; redirect,
unsigned, duplicate, expired, and late evidence cannot confirm.

- [ ] T026 [US3] [BLOCKED until T003,T002] Implement approved VNPay payment initiation and 10-minute payment-link lifecycle using frozen 50-percent VND deposit in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T027 [US3] [BLOCKED until T003] Implement approved VNPay HMAC-SHA512 IPN, bounded QueryDR recovery, redacted payment evidence, and provider-event idempotency in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/payments/`.
- [ ] T028 [US3] [BLOCKED until T001,T009,T010,T011] Implement post-validation local confirmation: commit every active hold or none, confirm only on success, then obtain the N-ticket delivery-safe projection in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T029 [US3] [BLOCKED until T002] Implement approved payment initiation and provider-callback REST transport, with browser return reading only committed state, in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T030 [US3] [BLOCKED until T002] Implement booking-contact confirmation delivery intent with complete active ticket set and reusable Magic Link, independent from notification delivery outcome, in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T031 [P] [US3] [BLOCKED until T003,T009,T010,T011] Add tests for signed success, invalid checksum, reference/amount/currency mismatch, duplicate/out-of-order result, redirect-only result, QueryDR timing, expiry, and late-payment follow-up in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T032 [P] [US3] [BLOCKED until T001,T009,T011] Add tests proving one aggregate deposit yields N persistent tickets and each Silverclay ticket carries both session entitlements without attendance data in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T033 [P] [US3] [BLOCKED until T002] Add tests proving delivery retry/resend reuses active ticket/Magic-Link access and delivery failure never rolls back confirmation in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T034 [US3] [BLOCKED until T005,T002] Implement approved React payment-pending, gateway-return, confirmation-ticket, and retry states in `ppswbs_frontend/src/`.

---

## Phase 6: User Story 4 — Look up and recover a booking (Priority: P2)

**Goal**: Return only an authorized booking/ticket projection and provide safe
recovery without client credential persistence.

**Independent Test**: Valid Guest proof, Member ownership, or Magic Link works;
invalid proof/link reveals no booking data.

- [ ] T035 [US4] [BLOCKED until T002,T012] Implement Guest code plus one stored contact proof and authenticated Member ownership lookup with safe no-disclosure outcomes in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T036 [US4] [BLOCKED until T002] Implement reusable Magic-Link hash, expiry/revocation, fragment-token HTTPS exchange, memory-only projection contract, and stored-phone recovery outcome in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T037 [US4] [BLOCKED until T006] Implement self-service/internal confirmation resend, Manager-only Magic-Link revoke/rotate, rate limits, late-payment follow-up, and minimal business audit evidence in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T038 [US4] [BLOCKED until T001,T010] Implement idempotent pending-hold release through the capacity facade, preserving `CANCELLED/UNPAID`, releasing capacity once, and issuing no ticket in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T039 [P] [US4] [BLOCKED until T002,T012] Add tests for Guest proof, Member ownership, pending/confirmed/cancelled projections, expired holds, and pending-hold release in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T040 [P] [US4] [BLOCKED until T002,T006] Add Magic-Link and audit tests for reusable access, expiry, revoke/rotate, resend reuse, raw-credential redaction, branch scope, denial, and delivery-log separation in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/`.
- [ ] T041 [US4] [BLOCKED until T005,T002] Implement approved React lookup, pending-resume, confirmed-ticket, cancelled, and Magic-Link fragment-exchange/recovery states in `ppswbs_frontend/src/`.

---

## Phase 7: Cross-cutting verification and release evidence

- [ ] T042 [BLOCKED until T001–T006] Run approved quickstart scenarios and the approved listing-to-booking-entry E2E assertion; retain observed evidence only in the approved CI report/artifact, not in `quickstart.md`.
- [ ] T043 [BLOCKED until T004] Run approved empty-baseline or preservation-upgrade migration plus recovery/rollback evidence in `ppswbs-spec/features/003-workshop-management/003.1-workshop-booking/`.
- [ ] T044 [BLOCKED until T001–T006] Run contract, MySQL concurrency, payment idempotency, authorization/audit, React client, and `ApplicationModules.verify()` checks from `ppswbs_backend/` and `ppswbs_frontend/`.
- [ ] T045 Run `$speckit-analyze` against `ppswbs-spec/features/003-workshop-management/003.1-workshop-booking/spec.md`, `plan.md`, and this `tasks.md` before implementation approval.

---

## Phase 8: Final-settlement status facade handoff

**Purpose**: allow 003.1 to remain owner of Booking financial status without implementing Settlement inside Booking.

- [ ] T046 **BLOCKED — approved 003.3 facade type**: implement the approved idempotent final-balance-resolved consumer in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/facade/`; Booking alone records `FULLY_PAID` once from a safe correlation/reference, never raw final-payment evidence or a deferred-link delivery.
- [ ] T047 **BLOCKED — T046**: add replay, raw-data non-disclosure, and deferred-link-not-settlement coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/FinalSettlementStatusIT.java`.

- [ ] T048 **BLOCKED — approved 003.2 current-projection facade type**: make every authorized Guest, Member, and Magic-Link booking read obtain the current delivery-safe ticket/session-entitlement projection from Operations; add replacement/refund freshness coverage without reading Operations entities in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/CurrentTicketProjectionIT.java`.

## Dependencies and execution order

- Phase 1 precedes all implementation; no task may bypass a listed gate.
- Phase 2 requires approved 003.2 facades and blocks every user story.
- US1 provides discovery; US2 is the booking/hold MVP; US3 depends on US2;
  US4 needs the aggregate and authorization boundary plus confirmed data for
  complete tests.
- 003.2 must expose approved capacity types before T010, T021, T023, T028, or
  T038; it must expose ticket-projection types before T011, T028, T032, or T033.
- No 003.1 task implements Operations capacity, entitlement, check-in, or
  attendance internals.
- T046–T047 depend on an approved 003.3-to-Booking internal facade type; they do
  not authorize 003.1 to verify final VNPay evidence or calculate settlement.
- T048 depends on an approved 003.2 read-only current-projection facade; it does
  not authorize Booking to own entitlement, attendance, or session schedule.

## Parallel opportunities

- T001–T006 can progress in parallel by their decision owners.
- After their gates, T008, T012–T015 can proceed in parallel.
- T023–T024, T031–T033, and T039–T040 are parallel test groups after the
  corresponding implementation behavior exists.

## Implementation strategy

1. Resolve Phase 1 rather than scaffolding around missing contracts.
2. Establish Phase 2 and the 003.2 facade dependencies.
3. Deliver US1, then US2 as the booking MVP.
4. Add US3 payment/ticket delivery, then US4 lookup/recovery/support safety.
5. Complete Phase 7 before requesting implementation approval.

## Legacy tasks deliberately removed

- Historical `[x]` completion claims.
- Thymeleaf/SSR/htmx tasks and `OWNER`/`ADMIN_TECHNICAL` assumptions.
- Single-session-only booking, generic cart, `BASE_PLUS_EXTRA`, and single-use
  Magic-Link behavior.
- Direct cross-feature entity/repository access and 003.2/003.3/003.4 work
  incorrectly assigned to 003.1.
