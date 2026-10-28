# 003 Workshop Management — Internal Facade Contract

**Date created**: 2026-10-25
**Date updated**: 2026-10-28
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
| 003.3 Workshop Settlement and Changes | `WorkshopSettlementFacade` | 003.2; Feature 007 for the notification-delivery query only |

003.1 additionally publishes its named post-commit booking-notification handoff
for Feature 007. That event is not a facade and does not let Feature 007 read
Booking persistence directly.

No caller may access another feature's controller, HTTP DTO, entity, repository,
or internal service. A facade's command/query types are immutable Java boundary
types in its owner's named `facade` interface. They contain opaque identifiers
and only the minimum data listed below; they never expose JPA entities, contact
PII, attendance detail, Staff/audit data, raw Magic-Link/QR material, or raw
VNPay payloads.

### Canonical workshop location identity

Every location reference in this contract denotes the same canonical physical
resource: `workshop_location.id`. In particular, an Operations session's
location, a Booking's package/session location and Account Management
`activeLocationIds` use that one identity directly. This contract has no
independent branch identifier, branch-to-location mapping or duplicate location
resource; `branch` is a retired synonym, not a boundary field.

Every Operations session reference in this contract is the opaque UUID identity
owned by 003.2. Consumers carry it as a UUID/string `sessionReference`; no
facade accepts or exposes a legacy numeric session identity.

## Shared VNPay ingress boundary

`paymentintegration` is a technical Spring Modulith module, not a business
feature and not `common`. It owns the one public VNPay IPN ingress documented
in [`vnpay-payment-ingress.openapi.yaml`](./vnpay-payment-ingress.openapi.yaml).
Its only responsibilities are to receive the HTTPS `GET`, verify the approved
VNPay checksum once, deduplicate provider delivery, resolve the immutable
owner-qualified merchant reference, and dispatch the resulting **redacted,
verified payment evidence** to exactly one owner facade. It does not create a
booking, final bill, refund, ticket, hold, or business audit record.

The payment attempt that creates a merchant reference registers exactly one
target: `DEPOSIT` for 003.1, or `FINAL_PAYMENT`/`REFUND` for 003.3. An unknown,
ambiguous, mismatched, or already-terminal reference is not broadcast or
guessed; it receives the safe provider acknowledgement outcome and no business
side effect. The routing registry/reference format, Java package names and
persistence mechanism remain implementation decisions, but its target must be
durable and immutable for the payment-attempt lifetime.

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
   `CANCELLATION_HANDOFF_UNRESOLVED`, `PACKAGE_NOT_PUBLISHED`, and
   `PACKAGE_SELECTION_INVALID`. `PACKAGE_NOT_PUBLISHED` means the selected
   Package is not currently bookable; `PACKAGE_SELECTION_INVALID` means the
   participant bounds, required-session count or same-location rule fails.
   Each facade may document a narrower set.
4. Exceptions represent unexpected technical failure only. They do not encode
   normal business refusal.
5. Commands are synchronous because the caller needs an immediate all-or-none
   answer. Events are published only after the owning local transaction commits
   for notification, OTA, or downstream work; an event never decides booking
   confirmation, capacity, or a financial conclusion.

## `WorkshopOperationsFacade` — owned by 003.2

| Operation | Input boundary type | Result boundary type | Invariant / disclosure limit |
| --- | --- | --- | --- |
| Read a published Package for Booking selection | `PublishedPackageSelectionQuery(packageReference)` | `PublishedPackageSelectionSnapshot(packageReference, publicPerParticipantPrice, minParticipants, maxParticipants, requiredSessionCount, sameLocationRequired, minimumSameDaySlotGap)` | Read-only, published Package only; no entity/repository, Account, Booking/payment, ticket, attendance, Staff/audit or PII data. Booking may freeze these facts but never mutate master data. |
| Read sale eligibility | `SaleEligibilityQuery(sessionReference)` | `SaleEligibilitySnapshot(customerSelectable, saleState, locationReference, scheduledStart, scheduledEnd, remainingCapacity)` | Read-only, per session; no booking/contact/Staff data. |
| Reserve all required-session capacity | `ReserveBookingCapacityCommand(bookingReference, packageReference, orderedRequiredSessionReferences, participantCount, operationReference, correlationReference)` | `CapacityHoldOutcome(ACCEPTED + holdReferences + expiresAt, or REJECTED reason)` | Operations atomically rechecks that the Package is published, participant bounds, required-session count, same-location and same-day slot-gap rules, sale eligibility and capacity; it holds `participantCount` seats in every requested session or none. |
| Commit holds after valid deposit evidence | `CommitBookingHoldsCommand(bookingReference, expectedHoldReferences, operationReference, correlationReference)` | `CapacityCommitOutcome(ACCEPTED, or REJECTED reason)` | Commits all active expected holds or none. |
| Release pending holds | `ReleaseBookingHoldsCommand(bookingReference, expectedHoldReferences, operationReference, correlationReference)` | `CapacityReleaseOutcome(ACCEPTED or prior equivalent outcome)` | Releases active holds once only; late/repeated release is idempotent. |
| Read current delivery-safe ticket set | `BookingTicketProjectionQuery(bookingReference)` | `DeliverySafeTicketSet(ticketReferenceOrQrReference, ticketCode, sessionEntitlements(locationReference, scheduledStart, scheduledEnd))` | Read-only; no attendance, Staff/audit, PII, or raw QR payload. |
| Read settlement readiness | `BookingSettlementReadinessQuery(bookingReference)` | `SettlementReadiness(ready)` | `ready` is true only if every required session is `ATTENDANCE_FINALIZED`; no underlying attendance detail. |
| Replace the complete future required-session set | `ReplaceRequiredSessionsCommand(bookingReference, completeTargetSessionReferences, operationReference, correlationReference)` | `RequiredSessionReplacementOutcome(ACCEPTED, or REJECTED reason)` | Operations validates all current/target rules, transfers all capacity and entitlement or none, and preserves ticket QR. |
| Apply verified whole-booking refund consequence | `ApplyVerifiedRefundConsequenceCommand(bookingReference, verifiedRefundReference, operationReference, correlationReference)` | `RefundConsequenceOutcome(ACCEPTED, or REJECTED reason)` | Only after verified refund; revoke unused future ticket entitlement and release future capacity; never rewrite attendance history. |
| Complete a resolved cancellation handoff | `CompleteCancellationHandoffCommand(sessionReference, cancellationCaseReference, operationReference, correlationReference)` | `CancellationCompletionOutcome(ACCEPTED, or REJECTED reason)` | `CANCELLED` only when Settlement reports the handoff resolved/audited; no refund/reschedule logic lives in Operations. |

## `WorkshopBookingFacade` — owned by 003.1

| Operation | Input boundary type | Result boundary type | Invariant / disclosure limit |
| --- | --- | --- | --- |
| Read confirmed booking financial projection | `ConfirmedBookingFinancialProjectionQuery(bookingReference)` | `ConfirmedBookingFinancialProjection(packageReference, frozenPackagePrice, frozenPostPromotionAmount, acceptedDepositAmount, frozenRemainingAmount, participantCount, requiredSessionReferences, bookingLocationReference)` | Available only for the confirmed direct-Mland booking/package context required by 003.3. Deposit is `ceil(frozenPostPromotionAmount / 2)` and remaining amount is the exact difference; no contact, raw provider evidence, or ticket payload. |
| Record final-balance resolution | `RecordFinalBalanceResolvedCommand(bookingReference, settlementReference, operationReference, correlationReference)` | `FinalBalanceResolutionOutcome(ACCEPTED or prior equivalent outcome, or REJECTED reason)` | Only Booking changes its financial state to `FULLY_PAID`, exactly once; it receives no raw final-payment detail. |
| Apply verified deposit evidence | `ApplyVerifiedDepositPaymentEvidenceCommand(bookingReference, verifiedEvidenceReference, operationReference, correlationReference)` | `DepositPaymentEvidenceOutcome(ACCEPTED or prior equivalent outcome, or REJECTED reason)` | Called only by `paymentintegration` after one successful verified dispatch. Booking checks its frozen reference/amount/currency, commits capacity atomically, and never receives raw VNPay payload or checksum. |
| Request delivery of existing ticket set | `RequestBookingTicketRedeliveryCommand(bookingReference, activeUnusedTicketReferences, operationReference, correlationReference)` | `TicketRedeliveryOutcome(ACCEPTED or REJECTED reason)` | Existing ticket/QR only; Booking delivers to the stored contact channel and does not change contact, tickets, payment, or Magic Link. This supports the already-approved Operations resend flow. |
| Read immutable notification delivery payload | `BookingNotificationDeliveryPayloadQuery(notificationReference)` | `BookingNotificationDeliveryPayload(notificationKind, storedRecipient, renderedBookingContent, renderedTicketArtifacts, bookingAccessLink, correlationReference)` | Callable only by the Feature 007 notification boundary after a matching handoff. It returns a single immutable render-ready snapshot for that delivery reference; recipient/contact data, rendered ticket artifacts, and booking-access link MUST NOT be included in the event or provider/audit logs. |

## Post-commit Booking notification handoff — 003.1 to Feature 007

003.1 persists one immutable notification handoff in the same local transaction
as an accepted confirmed-booking delivery, confirmation resend, or Magic-Link
rotation. Only after that transaction commits, it publishes at-least-once
`BookingNotificationReady(notificationReference, bookingReference,
notificationKind, correlationReference)`. The event contains no recipient
address, ticket credential/QR payload, Magic-Link value, payment data, or other
unnecessary PII.

Feature 007 deduplicates by `notificationReference`, reads the immutable
render-ready payload through `WorkshopBookingFacade`, and owns provider choice,
send state, retry policy, and delivery logs. It may not query Booking entities
or reconstruct the message from current Booking state. A duplicate event must
not create a second delivery request for the same handoff reference. A delivery
failure or an uncertain provider outcome is operational only: it does not
reverse a confirmed booking, payment, hold, ticket, resend acceptance, or
Magic-Link revocation/rotation.

## `WorkshopSettlementFacade` — owned by 003.3

| Operation | Input boundary type | Result boundary type | Invariant / disclosure limit |
| --- | --- | --- | --- |
| Open a customer-commitment cancellation handoff | `OpenCancellationHandoffCommand(sessionReference, affectedBookingReferences, cancellationReasonReference, operationReference, correlationReference)` | `CancellationHandoffOutcome(ACCEPTED + cancellationCaseReference, or REJECTED reason)` | Consumed asynchronously from the Operations-owned durable outbox after Operations has entered `CANCELLATION_REQUESTED`; delivery is idempotent by `operationReference`, and opening a case does not alter capacity, tickets, or payment. |
| Read cancellation-handoff resolution | `CancellationHandoffResolutionQuery(cancellationCaseReference)` | `CancellationHandoffResolution(resolved)` | `resolved` is true only after every affected booking case has a recorded remedy or audited Manager decision. It exposes no refund amount/payment/PII detail. |
| Apply verified settlement evidence | `ApplyVerifiedSettlementPaymentEvidenceCommand(bookingReference, verifiedEvidenceReference, operationReference, correlationReference)` | `SettlementPaymentEvidenceOutcome(ACCEPTED or prior equivalent outcome, or REJECTED reason)` | Called only by `paymentintegration` after one successful verified dispatch. Settlement checks its frozen bill/refund reference, amount and currency, and may send only the existing safe final-balance conclusion to Booking. It receives no raw VNPay payload or checksum. |
| Read immutable notification delivery payload | `SettlementNotificationDeliveryPayloadQuery(notificationReference)` | `SettlementNotificationDeliveryPayload(notificationKind, storedRecipient, renderedSettlementContent, paymentActionLinkWhenApplicable, correlationReference)` | Callable only by the Feature 007 notification boundary after a matching handoff. It returns one immutable render-ready snapshot for that delivery reference; recipient/contact data and a payment action link MUST NOT be included in the event or provider/audit logs. |

## Post-commit Settlement notification handoff — 003.3 to Feature 007

003.3 persists one immutable notification handoff in the same local transaction
as an authorized deferred-payment-link issue/reissue, its permitted reminder, or
a customer-facing verified settlement/refund outcome. Only after that transaction
commits, it publishes at-least-once
`SettlementNotificationReady(notificationReference, settlementReference,
notificationKind, correlationReference)`. The event contains no recipient
address, payment-action-link value, raw gateway evidence, ticket/QR data, or
other unnecessary PII.

Feature 007 deduplicates by `notificationReference`, reads the immutable
render-ready payload through `WorkshopSettlementFacade`, and owns provider
selection, send state, retry and operational delivery logs. It cannot read
Settlement persistence or reconstruct content from mutable current data. A
duplicate event MUST NOT create a second delivery request. A delivery failure or
uncertain provider outcome is operational only; it MUST NOT reverse a final
bill, verified payment/refund, deferred-payment case, remedy case, or Booking
`FULLY_PAID` state.

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
