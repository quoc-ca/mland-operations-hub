# Implementation Plan: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-11-01
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Branch**: `003.3-workshop-settlement-and-changes` | **Spec**: [spec.md](./spec.md)

## Summary

Every active canonical Manager has global authority for Manager-authorized
003.3 actions, including policy reads/revisions and cross-location booking
changes. Staff-authorized actions remain location-scoped; Member ownership and
explicitly Admin-only actions remain unchanged. There is no central Manager tier.

003.3 owns direct-Mland post-workshop settlement: exactly one final bill for one
booking/package, accepted additional charges, a short-lived final-payment
checkout attempt, verified final payment before package-output handover,
non-refundable customer cancellation decision, Manager-mediated required-session change, and
Mland-caused cancellation remedy. Direct customer cancellation records a
non-refundable deposit decision and does not invoke VNPay refund/QueryDR. It is
not an item/session finance system, cart,
OTA payment system, capacity owner, or normal check-in system.

## Technical Context

| Topic | Direction |
| --- | --- |
| Platform | Java 21/Spring Boot REST backend and a separate React + Vite client; no SSR. |
| Module | Spring Modulith; only explicit owner facades cross business modules. |
| Persistence | MySQL/JPA and append-only Flyway migrations when implementation is authorized. |
| Identity | Firebase bearer token proves identity; Account Management supplies canonical role/status, ownership and location facts. Active Manager authority is global for Manager actions; Staff actions use canonical `workshop_location.id` and `activeLocationIds`. |
| Payment | VNPay only. Shared `paymentintegration` owns provider credentials, checkout URL generation, provider attempt expiry and payment/IPN evidence. Direct customer-deposit cancellation never calls the refund/QueryDR protocol; OTA refunds remain external to this flow. Final-payment checkout uses the server-derived outstanding VND amount and expires after 10 minutes. Browser Return URL is display-only. |
| Contract | Versioned `/api/v1` REST contract in `contracts/workshop-settlement.openapi.yaml`. |
| Validation | Module-boundary, authorization, lifecycle, idempotency, facade, contract, migration-upgrade and recovery evidence. |

## Constitution Check

| Gate | Result | Evidence / condition |
| --- | --- | --- |
| Module integrity | PASS | 003.3 consumes only the named 003.1/003.2 facades and owns only settlement records/cases. |
| Identity and sensitive data | PASS with FE-001 dependency | Firebase is identity-only; server rechecks canonical account facts plus Settlement's location/resource action rules. No contact proof, raw token, QR payload, provider secret or raw callback is retained in audit. |
| REST and frontend | PASS | The OpenAPI contract defines resource routes, authorization alternatives, idempotency, safe projections and typed errors. React consumes REST only. |
| State, failure and idempotency | PASS | Client commands retain idempotent outcomes; verified provider transaction/reference deduplicates IPN/QueryDR. Cases have explicit lifecycle and correlation. |
| Data evolution | PASS with rollout condition | Only dev/test without retained booking/customer data may reset after preflight. Data-bearing environments require append-only preservation migration, upgrade test, recovery/rollback evidence. |

## Ownership and Internal Boundaries

```text
paymentintegration ─ verified final-payment/refund evidence ─► 003.3
003.1 Booking ─ confirmed financial projection / FULLY_PAID conclusion ─► 003.3
003.2 Operations ─ readiness / atomic replacement / verified-refund effect ─► 003.3
FE-001 Account ─ current authorization, ownership and location scope ─► 003.3
VNPay ─ signed IPN / QueryDR evidence ─► paymentintegration
003.3 ─ reference-only post-commit settlement notification handoff ─► Feature 007
```

| Concern | Owner | 003.3 rule |
| --- | --- | --- |
| Confirmed booking, frozen price/deposit, `FULLY_PAID` | 003.1 | Read/update only through `WorkshopBookingFacade`. |
| Sessions, capacity, entitlements, attendance, cancellation state | 003.2 | Read/change only through `WorkshopOperationsFacade`. |
| Final bill, charge, payment/refund/change case and settlement audit | 003.3 | Authoritative owner. |
| Canonical account role/status, ownership and location scope | FE-001 | Recheck facts at every protected command; Settlement applies its own action rules and never infers authority from display roles or claims. |
| Delivery | Feature 007 | Verified settlement/refund notification handoff is persisted with accepted state; its post-commit reference event lets Feature 007 read the facade payload, deduplicate and retry. Recipient/content expire after 30 days. Payload contains no payment-action link and delivery cannot settle a bill or refund. |

Workshop Branch is not a second resource in this feature: every booking,
session, catalogue and audit scope is the canonical `workshop_location.id`.
For Staff actions Settlement compares it directly with `activeLocationIds`;
Manager-authorized actions are global. It stores neither a separate branch
identifier nor a branch-to-location mapping.

The concrete owner operations and boundary types are fixed by the shared
[003 internal facade contract](../contracts/workshop-interfeature-facades.md).
The facade is synchronous for booking-critical conclusions. Events are emitted
only after a local commit for delivery/downstream handoff; events never decide
capacity, final payment, refund, or Booking `FULLY_PAID`.

## Settlement Design

### Financial invariant

One confirmed direct booking/package has at most one final bill. It receives the
frozen post-promotion amount, accepted deposit and frozen remaining amount from
003.1. It never recalculates voucher, points, deposit or remaining amount. Its
amount due is the frozen remaining amount plus accepted charges, less verified
final-payment/refund consequences as applicable.

The final bill is available only after 003.2 reports every required session as
`ATTENDANCE_FINALIZED`. On verified final-balance resolution, 003.3 invokes
`WorkshopBookingFacade`; 003.1 alone changes booking state to `FULLY_PAID`.

### Charges and bill access

Customer selection is limited to an eligible fixed-price catalogue add-on for
the booking package/location. The server derives its price from the catalogue
snapshot. An owning Member uses authenticated ownership; a Guest supplies the
booking code plus exactly one stored phone or email per request. The command is
rejected once the bill is finalized. Magic Link is read-only.

Staff in the booking location or any active Manager may create/confirm variable material or service
charges. The command has description, VND amount and reason and produces audit
evidence. A customer cannot submit a variable price/name. Final-bill reads return
a customer-safe projection only after Member ownership or Guest contact proof.

### Cases and lifecycle

Customer cancellation, required-session change and Mland-caused remedy are
booking-scoped, auditable cases. A case moves from
`OPEN` to `RESOLVED`, `REJECTED`, or `FAILED`; only a gateway-waiting refund may
be `PENDING_PROVIDER`. Each case is read through its booking and `caseReference`, never by
ticket/session.

For a required-session replacement, Manager supplies the whole target session
set and reason. 003.2 validates/applies every capacity and entitlement change or
none, preserving ticket QR. For Mland-caused replacement, the change carries the
authoritative `cancellationHandoffReference`; an `UNAVAILABLE` or `DECLINED`
remedy references the same handoff. A remedy refund uses the booking's retained
policy snapshot, not a caller amount.

Any active Manager may read or revise global refund-policy history. A
revision has a future effective time, integral percentage `0..100`, applies only
to later-confirmed bookings, and computes its snapshot refund as
`floor(collectedAmount × wholeBookingRefundPercent / 100)` whole VND for a
Mland-caused remedy. Direct customer cancellation has no refund percentage: the
deposit is forfeited and the booking is cancelled by Operations.

### Payment and retry boundaries

Every client-originated state-changing REST command requires `Idempotency-Key`.
Same key plus semantically identical input returns the original safe result;
changed reuse returns `409 IDEMPOTENCY_CONFLICT`. Each booking-critical command
has one local MySQL transaction for its owned state and audit outcome. Gateway
calls are outside that transaction.

The shared `paymentintegration` boundary owns VNPay IPN `GET
/api/v1/integrations/payments/vnpay/events`, validates signature, resolves the
immutable owner-qualified merchant reference, deduplicates provider delivery,
and dispatches redacted evidence to exactly one owner facade. 003.3 receives
only final-payment/refund evidence and checks bill/refund reference, amount and
currency before its local idempotent outcome. QueryDR is outbound Mland-to-VNPay
reconciliation only. Browser Return URL has no settlement route and never
confirms a bill.

Outbound Merchant API requests are owned by `paymentintegration` and exposed to
business code only through typed technical ports in `common.payment`, avoiding
a Modulith cycle between the inbound dispatcher and 003.3. Each operation uses
an opaque caller operation reference as its idempotency key; the operation row
and a generated VNPay RequestId are committed before network I/O. Reusing the
same operation reference with the same request returns its retained outcome;
changed content conflicts. No automatic network retry is performed. A timeout,
transport failure, or provider response `99` is retained as `UNKNOWN`; the
caller must start a distinct QueryDR operation to reconcile. A refund response
`00` means the refund request was accepted, not that funds were returned. Only
a signature-verified QueryDR result can be presented as reconciliation evidence
to 003.3. Its original `vnp_CreateDate` is supplied as the transaction date;
unknown outcomes never cause an automatic second refund.

## Public REST Design

The contract is the normative transport detail in
[`contracts/workshop-settlement.openapi.yaml`](./contracts/workshop-settlement.openapi.yaml).

| Resource group | Public route family | Authority / proof |
| --- | --- | --- |
| Final bill | `/api/v1/workshop-bookings/{bookingCode}/final-bill` | Member ownership, Guest contact proof for read/customer fixed add-on; in-scope Staff for create/finalization. |
| Charges | `/final-bill/charges` | Customer fixed catalogue selection, Staff in booking-location scope, or any active Manager globally for variable charges. |
| Settlement cases | `/.../{case-collection}` and `/{caseReference}` | Customer cancellation/read by ownership/proof; Manager for required-session change and Mland remedy. |
| Refund policy | `/api/v1/workshop-refund-policies` | Any active Manager, global scope. |
| VNPay ingress | Shared integration contract | `paymentintegration` owns the signed GET; this feature never owns the provider route. |

All client write routes declare `Idempotency-Key`, and all responses use a safe
projection or typed, correlation-linked `Problem`. The contract maps malformed
input to `400`, missing/invalid authentication to `401`, authority denial to
`403`, absent/not-visible resource to `404`, conflict/idempotency misuse to
`409`, and business ineligibility to `422`.

## State and Data Design

`data-model.md` describes conceptual records except for the approved
customer-cancellation/refund persistence contract: append a Flyway migration
with dedicated cancellation-snapshot and refund-allocation tables; allow one
customer-cancellation case per booking using a unique booking reference; link
each snapshot to its settlement case; enforce one allocation per case plus
original merchant payment reference and a unique provider operation reference.
Persist all allocations before dispatch, call paymentintegration only after the
Settlement transaction commits, and use its operation journal as the provider
idempotency boundary. QueryDR runs immediately after a refund is accepted and
every five minutes for up to 24 hours when the refund transaction number is
known. Unknown outcomes without a refund transaction number remain pending for
manual Merchant Admin investigation; the application never marks them complete
without signed matching QueryDR evidence and never resends a refund. No test/live
database reset is permitted. Other
records retain only the schema decisions explicitly represented by existing
migrations. A React client holds only response state and must recover by
re-reading the safe resource/case projection.

## Validation and Rollout

- Contract-test every documented route, proof/authorization alternative,
  `Idempotency-Key` replay, typed error and safe projection.
- Test valid, duplicate, mismatched, unsigned, late and out-of-order VNPay
  evidence; a browser Return URL must never settle state.
- Test facade-only module boundaries with `ApplicationModules.verify()` and
  contract tests for all-or-none replacement/refund consequence.
- Test Guest/Member proof, location authority, inactive/out-of-scope denial and
  no-side-effect failure paths.
- Test preservation migration on an upgrade copy plus recovery/rollback evidence
  before data-bearing deployment. Do not reset staging/production data.
- Test concurrent cancellation requests with different idempotency keys for the
  same booking; the unique booking claim must prevent a second case/allocation.

## Readiness and Dependencies

| Dependency | Status | Planning consequence |
| --- | --- | --- |
| 003.1 confirmed booking projection and `FULLY_PAID` conclusion | Concrete facade contract | 003.3 consumes only the typed owner boundary. |
| 003.2 readiness, replacement, refund consequence and cancellation handoff | Concrete facade contract | 003.3 consumes only typed owner outcomes. |
| FE-001 account facts/scope | Approved dependency assumption | Implementation consumes canonical role/status, ownership and location scope; Settlement owns its action rules and does not edit FE-001 or use display roles/claims as authority. |
| VNPay merchant credential, configured callback URL, QueryDR/refund access | External configuration/evidence blocker | Protocol and fake-backed adapter can be implemented locally. Live requests remain disabled until the shared [VNPay configuration preflight](../contracts/vnpay-configuration-preflight.md) is satisfied. |
| Settlement-to-007 notification handoff | Defined cross-feature boundary | Durable handoff, reference-only event and payload facade are fixed in the shared facade contract. Recipient/content are retained up to 30 days; payment-action links are not part of settlement notifications. Feature 007 provider configuration remains external; Settlement commits independently. |
| Shared VNPay ingress/dispatch | Defined cross-feature boundary | Canonical route and dispatch rules live in `../contracts/vnpay-payment-ingress.openapi.yaml` and the facade contract; merchant credentials/configuration remain blocked. |

## Design Challenge Review

The main risk is pretending a complete REST contract makes payment integration
ready. It does not: without an approved VNPay merchant configuration and actual
callback/QueryDR/refund access, provider adapter work cannot be verified. The
design therefore keeps that work explicitly blocked while retaining a testable
semantic contract. A second risk is letting delivery failure alter money/capacity;
post-commit delivery isolation avoids that. No new framework, queue, state store,
locking mechanism, role mapping, provider secret, migration version or database
schema is assumed.

## Next Gate

This design is ready for `$speckit-tasks`. The resulting task set must replace
legacy item/session-bill and browser-confirmation work, mark the provider/
notification configuration dependencies blocked, and implement the shared VNPay
ingress before either business facade consumes payment evidence.
