# Implementation Plan: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-10-25
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Branch**: `003.3-workshop-settlement-and-changes` | **Spec**: [spec.md](./spec.md)

## Summary

003.3 owns direct-Mland settlement for one final bill per booking/package,
accepted additional charges, VNPay-backed final payment, deferred-payment cases,
whole-booking refunds and Manager-mediated required-session changes. It is not an
item/session financial system, cart, OTA payment system, or Operations capacity/
ticket implementation.

## Technical context

| Topic | Direction |
| --- | --- |
| Platform | Java 21/Spring REST backend; separate React + Vite client; no SSR. |
| Module | Spring Modulith; facade-only cross-feature access. |
| Persistence | MySQL/JPA; append-only Flyway when implementation is authorized. |
| Identity | Firebase bearer token proves identity only; Mland account/status/capability/scope authorizes. |
| Payment | V1 VNPay only; signed matched IPN or QueryDR evidence. |
| Scope | Direct-Mland only; OTA payment/refund/commission/payout outside 003.3. |
| Testing | Module, authorization, lifecycle, idempotency, facade and upgrade/recovery tests. |

## Constitution check

| Gate | Result | Evidence/condition |
| --- | --- | --- |
| Module integrity | PASS | 003.3 owns cases only and consumes the fixed 003 owner facades; it never reads 003.1/003.2 internals. |
| Identity/data | PASS with external provider | Firebase is identity-only; 003.3 consumes the assumed-published Account authorization, scope, and central-policy authority. |
| REST/frontend | PASS | Approved stack retained; no public settlement transport is inferred. |
| Security/audit | PASS | Verified provider evidence; redacted immutable business audit; no raw secret/token/payload. |
| Data evolution | PASS with rollout condition | Only empty dev/test may reset after preflight; data-bearing environments require append-only preservation migration, upgrade test and recovery evidence. |

## Boundary design

```text
003.1 Booking ─ frozen booking/package, deposit projection, `FULLY_PAID` update ◄► 003.3
003.2 Operations ◄ settlement readiness, atomic change and verified-refund effects ► 003.3
FE-001 Account ─ active authorization/scope ─► 003.3
VNPay ─ signed IPN or QueryDR evidence ─► 003.3
003.3 ─ post-commit delivery intent only ─► Notification
```

| Concern | Owner | 003.3 rule |
| --- | --- | --- |
| Booking/frozen price/deposit | 003.1 | Consume projection only. |
| Required sessions/capacity/ticket/attendance | 003.2 | Semantic facade only; never direct entity/repository access. |
| Final bill/charges/payment/refund/change cases | 003.3 | Authoritative owner. |
| Account/capability/branch scope | FE-001 | Recheck at command time; never infer role labels. |
| Customer delivery | Notification | Post-commit intent; delivery is not financial truth. |

Required semantic boundaries:

- 003.1 supplies confirmed direct booking/package, frozen price and deposit
  evidence; on 003.3's idempotent final-balance-resolved conclusion it alone
  changes Booking financial status to `FULLY_PAID`.
- 003.2 supplies its authoritative settlement-readiness conclusion, true only
  when every required session is `ATTENDANCE_FINALIZED`, without exposing
  attendance/ticket detail; it applies a complete
  required-session replacement all-or-none for capacity and entitlement; applies
  unused-ticket/future-capacity effects after verified refund. Booking's later
  customer read obtains the current delivery-safe projection from 003.2, rather
  than a copied replacement/refund schedule.
- FE-001 supplies current active actor, capability/scope and central policy-editor
  authority.
- VNPay supplies safely verified final-payment/refund outcome. Browser return is
  never settlement truth.
- Notification receives post-commit payment-link/reminder/outcome intent.

The owner facade operations, boundary types, typed outcomes, and idempotency
rules are defined in the shared [003 internal facade contract](../contracts/workshop-interfeature-facades.md).
It does not choose public endpoints, persistence schema, queues, locks, or
provider configuration.

## Lifecycle decisions

1. One direct booking/package has one final bill only after its final required
   session. Balance = frozen package price + accepted charges − deposit.
2. Customer charge selection is a permitted fixed-price catalogue add-on; variable
   material/service charge needs Staff/Manager confirmation. Neither adds capacity,
   tickets, participants or bookings.
3. Valid signed, matched VNPay evidence settles once; duplicate/out-of-order is
   idempotent. Browser return/manual counter record cannot settle.
4. Deferred payment is audited, lasts 24 hours, permits at most one reminder and
   creates no default credit/release. Reissue after expiry is a new authorized act.
5. Customer cancellation is whole booking only; ticket/capacity effects await
   verified refund success.
6. Manager session change is complete booking-level and atomic in Operations;
   persistent QR remains valid. Silverclay keeps its branch/order/gap rules.
7. Mland-caused cancellation offers replacement first; otherwise whole-package
   refund uses booking policy snapshot. Default is 100% of collected payment;
   central policy changes apply only to future confirmed bookings.

## Artifacts

- [research.md](./research.md): source decisions and unresolved dependencies.
- [data-model.md](./data-model.md): conceptual model/invariants, not schema.
- [quickstart.md](./quickstart.md): non-production validation.
- [workshop-settlement.openapi.yaml](./contracts/workshop-settlement.openapi.yaml):
  no paths until a public REST transport is approved; legacy item paths retired.

## Readiness and blockers

| Dependency | Status | Task implication |
| --- | --- | --- |
| 003.1 booking/deposit projection and `FULLY_PAID` conclusion | Concrete facade contract approved | 003.3 consumes typed projection/outcome only; it cannot mutate Booking directly. |
| 003.2 settlement-readiness conclusion | Concrete facade contract approved | 003.3 uses only that conclusion; it cannot infer completion from schedule or attendance detail. |
| 003.2 settlement-readiness, complete replacement and post-refund consequence | Concrete facade contract approved | 003.3 consumes typed outcomes only; it cannot access Operations data directly. |
| Account authorization/capability | Shared baseline fixed; provider owned externally | Consume current Account decisions; do not infer role/capability or edit FE-001. |
| VNPay merchant/IPN/QueryDR/refund config | Pending | Provider adapter/config task blocked. |
| Notification handoff | Pending | Delivery task blocked; must not block settlement. |
| Public REST transport | Not approved | HTTP/OpenAPI implementation tasks blocked. |

Before `$speckit-tasks`, replace legacy item-bill/session-price/old-QR tasks with
booking-package tasks, preserving each unresolved dependency as explicit blocked
work. Implementation later uses one local MySQL transaction per booking-critical
command and append-only migration/upgrade/recovery practice for data-bearing
environments.
