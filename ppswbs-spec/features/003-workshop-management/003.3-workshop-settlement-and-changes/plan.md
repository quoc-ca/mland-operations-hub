# Implementation Plan: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-10-24
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Branch**: `003.3-workshop-settlement-and-changes` | **Spec**: [spec.md](./spec.md)

## Summary

Create a dedicated settlement module for final item bills, charge/adjustment
evidence, final payment and release, deferred payment, whole-booking refunds,
reschedules, and Mland-caused item remedies. It consumes Booking, Operations,
and Account/personnel through facade boundaries.

## Technical Context

**Language/Version**: Java 21; TypeScript client
**Primary Dependencies**: Spring Boot, Spring Modulith, JPA, Flyway, Firebase, VNPay, OpenAPI
**Storage**: MySQL; append-only Flyway migrations
**Testing**: Module, migration, contract, authorization, lifecycle, callback-idempotency integration tests
**Target Platform**: REST service and separate React/Vite client
**Project Type**: Modular-monolith web application
**Performance Goals**: No approved numeric target
**Constraints**: Server-side capability/branch authorization; signed callback evidence; no raw token/provider payload logging
**Scale/Scope**: Direct Mland workshop bookings only; no OTA settlement

## Constitution Check

| Gate | Result | Evidence |
| --- | --- | --- |
| Module integrity | PASS | Settlement is a distinct facade-only Modulith module. |
| Identity/data | PASS | Firebase proves identity; Mland account/capability/branch scope authorizes. |
| REST/frontend | PASS | Versioned REST/OpenAPI and separate frontend. |
| Verification | PASS | Contract, authorization, migration and duplicate-event coverage planned. |
| State/simplicity | PASS | MySQL business state only; no session/JWT/new framework. |

Post-design constitution check: PASS. No exception is requested.

## Project Structure

### Documentation

```text
ppswbs-spec/features/003-workshop-management/003.3-workshop-settlement-and-changes/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
└── tasks.md
```

### Source Code

```text
ppswbs_backend/src/main/java/.../features/workshopsettlement/
├── controller/
├── dto/
├── dao/
├── entity/
├── service/
└── facade/
```

**Structure Decision**: Settlement owns bills, charges, adjustments, exceptions,
refunds and reschedules. Booking owns deposits; Operations owns sessions, tickets,
capacity and attendance; Account/personnel owns capability/branch authorization.

## Phase 0: Research

Research records the narrow 24-hour final-bill exception, branch-scoped capability
model, module ownership and idempotent callback recovery in [research.md](./research.md).

## Phase 1: Design and Contracts

[data-model.md](./data-model.md) defines financial entities and invariants.
[workshop-settlement.openapi.yaml](./contracts/workshop-settlement.openapi.yaml)
defines REST entry points. [quickstart.md](./quickstart.md) defines end-to-end
validation evidence.

## Design Decisions

- A Manager-approved final-bill link lasts 24 hours; deposit links stay at 10
  minutes with 15-minute capacity hold.
- Authorization requires active actor, branch scope and one of
  `workshop.settlement.defer`, `.reschedule`, or `.refund`; role codes are not inferred.
- Provider event reference/outcome ensures idempotent final-payment/refund handling;
  browser return never settles money.
- Refund stays `REFUND_PENDING` until verified; Operations then applies ticket/capacity
  consequences through its facade.
- Reschedule requires Operations atomic ticket/capacity transfer; failure preserves
  old session, tickets and bill.

## Complexity Tracking

No constitution violation or additional project is needed.

## Delivery and Verification

Use the next append-only Flyway migration after checking deployed history. Verify
empty/upgrade migrations, `ApplicationModules.verify()`, contract/authorization/error
coverage, callback replay/out-of-order behavior and quickstart scenarios. Deployment
is outside this plan.
