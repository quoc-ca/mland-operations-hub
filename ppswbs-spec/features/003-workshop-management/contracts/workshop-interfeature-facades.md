# 003 Workshop Management — Internal Facade Contract

**Date created**: 2026-10-25
**Date updated**: 2026-10-25
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Status**: Approved design contract for 003.1, 003.2, and 003.3. This is an
in-process Spring Modulith named-interface contract, not a public REST API or
OpenAPI document.

## Purpose and boundary

Each feature exposes exactly one owner-specific facade:

| Owner | Public facade | Consumers |
| --- | --- | --- |
| 003.1 Workshop Booking | `WorkshopBookingFacade` | 003.2, 003.3 |
| 003.2 Workshop Operations | `WorkshopOperationsFacade` | 003.1, 003.3 |
| 003.3 Workshop Settlement and Changes | `WorkshopSettlementFacade` | 003.2 |

No caller may access another feature's controller, HTTP DTO, entity, repository,
or internal service. A facade's command/query types are immutable Java boundary
types in its owner's named `facade` interface. They contain opaque identifiers
and only the minimum data listed below; they never expose JPA entities, contact
PII, attendance detail, Staff/audit data, raw Magic-Link/QR material, or raw
VNPay payloads.

## Shared command/outcome rules

1. Every command that changes business state includes an opaque caller-generated
   `operationReference` and a safe `correlationReference`.
2. Retrying the same command with the same `operationReference` and semantically
   identical content returns its original outcome. Reusing that reference with
   different content is rejected safely.
3. Expected business outcomes are immutable typed results: `ACCEPTED`, or
   `REJECTED` with a safe reason. The reusable reasons are
   `INSUFFICIENT_CAPACITY`, `SALE_NOT_ELIGIBLE`, `HOLD_MISSING_OR_EXPIRED`,
   `NOT_READY`, `INVALID_REPLACEMENT`, `INVALID_STATE`, and
   `CANCELLATION_HANDOFF_UNRESOLVED`. Each facade may document a narrower set.
4. Exceptions represent unexpected technical failure only. They do not encode
   normal business refusal.
5. Commands are synchronous because the caller needs an immediate all-or-none
   answer. Events are published only after the owning local transaction commits
   for notification, OTA, or downstream work; an event never decides booking
   confirmation, capacity, or a financial conclusion.

## `WorkshopOperationsFacade` — owned by 003.2

| Operation | Input boundary type | Result boundary type | Invariant / disclosure limit |
| --- | --- | --- | --- |
| Read sale eligibility | `SaleEligibilityQuery(sessionReference)` | `SaleEligibilitySnapshot(customerSelectable, saleState, branchReference, scheduledStart, scheduledEnd, remainingCapacity)` | Read-only, per session; no booking/contact/Staff data. |
| Reserve all required-session capacity | `ReserveBookingCapacityCommand(bookingReference, orderedRequiredSessionReferences, participantCount, operationReference, correlationReference)` | `CapacityHoldOutcome(ACCEPTED + holdReferences + expiresAt, or REJECTED reason)` | Holds `participantCount` seats in every requested session or none. |
| Commit holds after valid deposit evidence | `CommitBookingHoldsCommand(bookingReference, expectedHoldReferences, operationReference, correlationReference)` | `CapacityCommitOutcome(ACCEPTED, or REJECTED reason)` | Commits all active expected holds or none. |
| Release pending holds | `ReleaseBookingHoldsCommand(bookingReference, expectedHoldReferences, operationReference, correlationReference)` | `CapacityReleaseOutcome(ACCEPTED or prior equivalent outcome)` | Releases active holds once only; late/repeated release is idempotent. |
| Read current delivery-safe ticket set | `BookingTicketProjectionQuery(bookingReference)` | `DeliverySafeTicketSet(ticketReferenceOrQrReference, ticketCode, sessionEntitlements(branchReference, scheduledStart, scheduledEnd))` | Read-only; no attendance, Staff/audit, PII, or raw QR payload. |
| Read settlement readiness | `BookingSettlementReadinessQuery(bookingReference)` | `SettlementReadiness(ready)` | `ready` is true only if every required session is `ATTENDANCE_FINALIZED`; no underlying attendance detail. |
| Replace the complete future required-session set | `ReplaceRequiredSessionsCommand(bookingReference, completeTargetSessionReferences, operationReference, correlationReference)` | `RequiredSessionReplacementOutcome(ACCEPTED, or REJECTED reason)` | Operations validates all current/target rules, transfers all capacity and entitlement or none, and preserves ticket QR. |
| Apply verified whole-booking refund consequence | `ApplyVerifiedRefundConsequenceCommand(bookingReference, verifiedRefundReference, operationReference, correlationReference)` | `RefundConsequenceOutcome(ACCEPTED, or REJECTED reason)` | Only after verified refund; revoke unused future ticket entitlement and release future capacity; never rewrite attendance history. |
| Complete a resolved cancellation handoff | `CompleteCancellationHandoffCommand(sessionReference, cancellationCaseReference, operationReference, correlationReference)` | `CancellationCompletionOutcome(ACCEPTED, or REJECTED reason)` | `CANCELLED` only when Settlement reports the handoff resolved/audited; no refund/reschedule logic lives in Operations. |

## `WorkshopBookingFacade` — owned by 003.1

| Operation | Input boundary type | Result boundary type | Invariant / disclosure limit |
| --- | --- | --- | --- |
| Read confirmed booking financial projection | `ConfirmedBookingFinancialProjectionQuery(bookingReference)` | `ConfirmedBookingFinancialProjection(packageReference, frozenPackagePrice, acceptedDepositAmount, participantCount, requiredSessionReferences, bookingBranchReference)` | Available only for the confirmed direct-Mland booking/package context required by 003.3; no contact, raw provider evidence, or ticket payload. |
| Record final-balance resolution | `RecordFinalBalanceResolvedCommand(bookingReference, settlementReference, operationReference, correlationReference)` | `FinalBalanceResolutionOutcome(ACCEPTED or prior equivalent outcome, or REJECTED reason)` | Only Booking changes its financial state to `FULLY_PAID`, exactly once; it receives no raw final-payment detail. |
| Request delivery of existing ticket set | `RequestBookingTicketRedeliveryCommand(bookingReference, activeUnusedTicketReferences, operationReference, correlationReference)` | `TicketRedeliveryOutcome(ACCEPTED or REJECTED reason)` | Existing ticket/QR only; Booking delivers to the stored contact channel and does not change contact, tickets, payment, or Magic Link. This supports the already-approved Operations resend flow. |

## `WorkshopSettlementFacade` — owned by 003.3

| Operation | Input boundary type | Result boundary type | Invariant / disclosure limit |
| --- | --- | --- | --- |
| Open a customer-commitment cancellation handoff | `OpenCancellationHandoffCommand(sessionReference, affectedBookingReferences, cancellationReasonReference, operationReference, correlationReference)` | `CancellationHandoffOutcome(ACCEPTED + cancellationCaseReference, or REJECTED reason)` | Called only after Operations has entered `CANCELLATION_REQUESTED`; opening a case does not alter capacity, tickets, or payment. |
| Read cancellation-handoff resolution | `CancellationHandoffResolutionQuery(cancellationCaseReference)` | `CancellationHandoffResolution(resolved)` | `resolved` is true only after every affected booking case has a recorded remedy or audited Manager decision. It exposes no refund amount/payment/PII detail. |

## Verification obligations

- `ApplicationModules.verify()` must permit only the named facade dependencies.
- Contract tests must prove all-or-none reserve/commit/replacement/refund
  consequences, same-reference retry equivalence, mismatch rejection, and no
  entity/repository/HTTP DTO leakage.
- Integration tests must prove failed post-commit event delivery cannot reverse
  a successful command or cause a second capacity/payment effect.

## Explicitly not decided here

- Java package names beyond the existing feature `facade` package, concrete
  record field types, database schema, locking implementation, public HTTP
  routes, VNPay credentials/configuration, and notification-provider delivery
  mechanics remain owned by their respective implementation/design work.
