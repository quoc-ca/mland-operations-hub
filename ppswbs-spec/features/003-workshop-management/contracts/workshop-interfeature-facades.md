# 003 Workshop Management — Internal Facade Contract

**Date created**: 2026-10-25
**Date updated**: 2026-11-26
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Status**: Approved design contract for 003.1, 003.2, and 003.3. This is an
in-process Spring Modulith named-interface contract, not a public REST API or
OpenAPI document.

**2026-11-25: OTA removed** ([record](../ota-removal-2026-11-25.md)). Removed
operations: `acceptOtaBooking`, `createOrReturnOtaBooking`, `revokeOtaTickets`,
`applyVerifiedRefundConsequence`, `createDirectShopAddonBill`, `findOtaAddonBill`
and the session OTA snapshot publication/resync. Mentions of OTA further down in
dated notes are history.
On 2026-10-30 the owner approved the VNPay outbound-operation safety contract:
`paymentintegration` owns the Merchant API adapter and journal, while business
callers use typed technical ports in `common.payment`. This keeps inbound
payment dispatch and outbound gateway calls from creating a Modulith cycle.
On 2026-10-31 the owner approved the 003.3 final-payment checkout contract:
separate `FINAL_PAYMENT` owner registration, exact outstanding VND amount,
10-minute URL expiry, and QueryDR reconciliation before another attempt after
expiry; there is no credit, reminder, or payment-link reissue flow.

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
target: `DEPOSIT` for 003.1, or `FINAL_PAYMENT`/`REFUND` for
003.3. `REFUND` is legacy for Mland money: since 003.3 T104 no
Mland money is refunded through the gateway (bank refunds instead). An unknown,
ambiguous, mismatched, or already-terminal reference is not broadcast or
guessed; it receives the safe provider acknowledgement outcome and no business
side effect. The routing registry/reference format, Java package names and
persistence mechanism remain implementation decisions, but its target must be
durable and immutable for the payment-attempt lifetime.

The technical module retains only redacted outcome fields after signature,
owner, amount and currency verification. `vnp_TransactionStatus=01` is
nonterminal; a verified two-digit status other than `01` is terminal. *(Decided 2026-11-19, Hồ Hoàng Quốc, 003.1 FR-032)* A verified IPN with a definite-failure code (003.1 FR-032 list) is also reported to Booking through its facade so the matching group attempt becomes `FAILED`; the command is `RecordDefinitePaymentFailureCommand` (see the facade table). The
read-only `PaymentIngressOutcomeQueryPort` in `common.payment` lets Booking
check that state without depending on the `paymentintegration` module. For a
verified successful deposit that arrives after its hold is released, Booking
stores a deduplicated `BookingLatePaymentEvidence` record through its facade;
that record is reconciliation evidence for Manager `RECORD`/`ESCALATE` only
and never restores the hold or confirms the booking. This applies to the
single-booking model only. For a booking group, FR-040 (`CONFIRM`/`REFUND`)
supersedes it: see "Late-payment review and bank refund (decision 2026-11-13)".

## Final-payment checkout boundary

003.3 starts final-payment checkout only after the final bill exists and all
required workshop sessions are attendance-finalized. Active Staff with booking
location scope or any active Manager may initiate it. The caller provides the
booking reference and an opaque idempotency/operation reference; the server
derives the exact remaining balance and fixes currency to `VND`. The typed
`common.payment` checkout port returns an opaque attempt reference, unique
merchant reference, amount/currency, short-lived payment URL, and expiry. The
URL expires 10 minutes after creation and is returned only to the authorized
Staff/Manager initiating checkout so it can be shown to the customer as a QR/link
during settlement; it is never written to an event, audit, log, or notification
snapshot.

Each attempt is registered immutably as `FINAL_PAYMENT` for the exact booking,
merchant reference, amount, currency, provider create date and expiry. Reusing
the same idempotency key/content returns the same attempt. A changed reuse
conflicts. While an attempt is pending or unknown, no second attempt is allowed.
After URL expiry, another attempt is allowed only after signed QueryDR confirms
the previous attempt did not succeed. Expiry alone is not evidence of
non-payment; any late signed success is processed against its original owner
reference. Only redacted signed IPN/QueryDR evidence reaches the Settlement
facade and reduces the outstanding balance. The checkout creates no credit
period, reminder, or reissue operation.

## VNPay outbound Merchant API technical port

`paymentintegration` owns outbound QueryDR/refund HTTP, Merchant credentials,
HMAC construction/verification, and durable operation state. It exposes a
typed implementation through `common.payment`; 003.3 owns caller authorization
and invokes the port only after its business transaction has committed. The
port is technical and transfers no Settlement persistence ownership.

Each command supplies an opaque `operationReference`, original merchant
transaction reference, original payment creation timestamp, and the minimum
command fields required by VNPay. The operation reference is the idempotency
identity. Before network I/O, the adapter stores the request fingerprint,
provider-generated `vnp_RequestId`, operation kind and `IN_FLIGHT` state.
Identical replay returns the stored state without another provider call;
changed content under that reference is rejected. It does not persist the
HMAC secret or raw signed response.

The adapter makes at most one provider call for a given operation reference.
Transport failure/timeout and response code `99` yield `UNKNOWN`; no refund is
retried automatically. Refund API response code `00` is only `ACCEPTED`, never
proof of returned funds. A distinct QueryDR operation can reconcile the
original merchant transaction using the exact original `vnp_CreateDate`.
QueryDR output is returned to the caller as verified evidence only after the
response checksum, terminal, transaction reference, amount and currency are
validated. 003.3 still owns applying a refund conclusion and related business
effects. The production endpoint/credential remain disabled until the
configuration preflight is approved.

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
   *(Proposed 2026-11-17, 003.2 spec FR-037–FR-045, not yet approved:)* one more
   reusable reason, `PACKAGE_NOT_AVAILABLE_AT_LOCATION`, means the Package is
   `INACTIVE` at the location of at least one selected or target session. It is
   returned by the atomic reserve and by `replaceTicketSessions` (all or none) and
   wins nothing over `PACKAGE_NOT_PUBLISHED`, which is checked first. A new
   read, `WorkshopOperationsFacade.activeLocationsForPackage(packageReference) →
   locationIds`, lists the locations where the Package is sellable, for 003.3
   change options. `replaceTicketSessions` accepts targets at another location
   when the Package is `ACTIVE` there and, under `sameLocationRequired`, all of
   the ticket's sessions end at one location; it has no actor, so 003.3 applies the Staff scope and assignment rule and needs no Manager for a location change (amended 2026-11-17, owner Hồ Hoàng Quốc). `findEquivalentReplacementSessions`
   drops the "same branch" filter and returns candidates at every location where
   the Package is `ACTIVE`, each with `locationId` and `locationName` (spec
   FR-046, owner decision 2026-11-17). Ticket-level operations (`cancelTicket`, `reissueTicketQr`,
   `replaceTicketSessions`) also use
   `TICKET_NOT_FOUND`, `TICKET_ALREADY_CHECKED_IN`, `RESCHEDULE_QUOTA_EXHAUSTED`
   and `LATE_ARRIVAL_WINDOW_EXPIRED` (003.2
   FR-034–FR-035). This is the complete reusable list; each facade may document
   a narrower set but MUST NOT return a reason outside it. *(Documentation sync
   2026-11-15, 003.2 checklist CHK012/CHK013; matches `RejectionReason`.
   Superseded: "Each facade may document a narrower set.")*
4. Exceptions represent unexpected technical failure only. They do not encode
   normal business refusal.
5. Commands are synchronous because the caller needs an immediate all-or-none
   answer. Events are published only after the owning local transaction commits
   for notification or downstream work; an event never decides booking
   confirmation, capacity, or a financial conclusion.

## `WorkshopOperationsFacade` — owned by 003.2

| Operation | Input boundary type | Result boundary type | Invariant / disclosure limit |
| --- | --- | --- | --- |
| Read a published Package for Booking selection  | `PublishedPackageSelectionQuery(packageReference)` | `PublishedPackageSelectionSnapshot(packageReference, packageName, publicPerParticipantPrice, minParticipants, maxParticipants, requiredSessionCount, sameLocationRequired, minimumSameDaySlotGap, requiredFoundryWorkingDaysBetweenSessions)` | Read-only, published Package only; no entity/repository, Account, Booking/payment, ticket, attendance, Staff/audit or PII data. Booking may freeze these facts but never mutate master data. |
| Read sale eligibility  | `SaleEligibilityQuery(sessionReference)` | `SaleEligibilitySnapshot(sessionReference, customerSelectable, saleState, locationReference, scheduledStart, scheduledEnd, remainingCapacity, workshopDate)` | Read-only, per UUID session reference; supplies authoritative selection/date facts for the Booking snapshot and Loyalty `WorkshopSessionFact`. Numeric Operations session IDs remain internal and are not returned; no booking/contact/Staff data. |
| Reserve all required-session capacity | `ReserveBookingCapacityCommand(bookingReference, packageReference, orderedRequiredSessionReferences, participantCount, operationReference, correlationReference)` | `CapacityHoldOutcome(ACCEPTED + holdReferences + expiresAt, or REJECTED reason)` | Operations atomically rechecks that the Package is published, participant bounds, required-session count, same-location, same-day slot-gap and foundry-working-date rules, sale eligibility and capacity; it holds `participantCount` seats in every requested session or none. |
| Commit holds after valid deposit evidence | `CommitBookingHoldsCommand(bookingReference, expectedHoldReferences, operationReference, correlationReference)` | `CapacityCommitOutcome(ACCEPTED, or REJECTED reason)` | Commits all active expected holds or none. *(Added 2026-11-15, owner decision Hồ Hoàng Quốc, 003.2 FR-010c/FR-010j, CHK004, task 003.2 T132:)* accepted while each held session is `PUBLISHED`, `SALES_PAUSED` or `IN_PROGRESS`; `REJECTED INVALID_STATE` with nothing committed once any held session is `CANCELLATION_REQUESTED`, `CANCELLED` or `ATTENDANCE_FINALIZED`. The holds then expire and release normally and 003.1 follows its late-payment path (003.1 FR-040). |
| Release pending holds | `ReleaseBookingHoldsCommand(bookingReference, expectedHoldReferences, operationReference, correlationReference)` | `CapacityReleaseOutcome(ACCEPTED or prior equivalent outcome)` | Releases active holds once only; late/repeated release is idempotent. |
| Read current delivery-safe ticket set | `BookingTicketProjectionQuery(bookingReference)` | `DeliverySafeTicketSet(ticketReference, qrReference, ticketCode, sessionEntitlements(sessionReference, locationReference, scheduledStart, scheduledEnd))` | Each entitlement carries the opaque UUID session reference and current schedule for unambiguous customer projection after replacement/refund. `qrReference` is the Operations-issued opaque bearer scan value, returned only through this authorized projection; no attendance, Staff/audit or PII. |
| Read booking attendance outcome (approved 2026-11-05; removed by decision 2026-11-13, see "Direct booking is only a booking group") | `BookingAttendanceOutcomeQuery(bookingReference)` | `BookingAttendanceOutcome(terminal, outcome: ATTENDED, NO_SHOW or PARTIAL)` | `NO_SHOW` only when every ticket on every required session is a final `NO_SHOW`. Paired with the post-commit `BookingAttendanceFinalizedEvent(bookingReference, finalizedAt)` trigger; see "Direct-booking open-folio lifecycle". No ticket/participant payload. |
| Read settlement readiness | `BookingSettlementReadinessQuery(bookingReference)` | `SettlementReadiness(ready)` | `ready` is true only if every required session is `ATTENDANCE_FINALIZED`; no underlying attendance detail. It gates finalization only; ~~terminal attendance outcome (correction window closed, `ATTENDED`/`NO_SHOW`/`PARTIAL`) comes from `BookingAttendanceOutcomeQuery`~~ *(superseded 2026-11-14: `BookingAttendanceOutcomeQuery` is removed with 003.3 T105; the terminal outcome is per ticket through `getTicketAttendanceOutcome`, whose result gains `terminal` and `windowClosesAt`, and there is no separate per-ticket readiness query; see the `TicketAttendanceFinalizedEvent` row of "Direct booking is only a booking group"). This booking-wide query itself is removed after 003.3 T106.* *(Superseded for booking-wide settlement readiness: the `getSettlementReadiness` method was removed 2026-11-14 by 003.2 T097 (owner approved); `BookingSettlementReadinessQuery` is kept only for `hasBookingServiceStarted` and `isStaffAssignedToBooking`.)* |
| Read customer-cancellation eligibility | `BookingCancellationEligibilityQuery(bookingReference)` | `BookingCancellationEligibilitySnapshot(eligible, reason, earliestRequiredSessionReference, earliestRequiredSessionStart, hasCheckedInTicket, hasNoShow)` | Operations derives the complete current required-session set from its active booking entitlements, including Manager replacements; it is authoritative for the earliest scheduled session reference/start, check-in and no-show facts. Settlement must not supply a potentially stale Booking-time session set and receives no ticket/participant payload. *(Removed 2026-11-14 by 003.2 T097 after 003.3 T106 left no caller; owner approved. Kept: only `BookingSettlementReadinessQuery`, for `hasBookingServiceStarted` and `isStaffAssignedToBooking`.)* |
| Verify customer access to a booking | `CustomerBookingAccessQuery(bookingReference, memberAccountId, contactEmail, contactPhone)` | `CustomerBookingAccessDecision(authorized)` | Exactly one proof mode is supplied: a Member account ID or one Guest contact value. Booking compares it to its own stored owner/contact and returns only a boolean decision; it does not return contact PII or log the submitted proof. |
| Read an eligible fixed workshop add-on for Staff confirmation  | `WorkshopAddOnPriceQuery(packageReference, locationReference, addOnReference)` | `WorkshopAddOnPriceSnapshot(addOnReference, description, amountVnd, currencyCode)` or `null` | Operations returns a read-only snapshot only when the package is published, location active, and add-on active for the exact package/location. Customer routes cannot create a charge; Staff/Manager confirmation is required. Amount is whole VND; no numeric internal add-on row ID is exposed. |
| Replace the complete future required-session set | `ReplaceRequiredSessionsCommand(bookingReference, completeTargetSessionReferences, operationReference, correlationReference)` | `RequiredSessionReplacementOutcome(ACCEPTED, or REJECTED reason)` | Operations requires the immutable package-policy snapshot created with the accepted booking hold; it validates the complete target set against the booking-time required count, participant bounds, same-location, same-day slot-gap and foundry-working-date rules, plus current target eligibility/capacity, then transfers all entitlements or none and preserves ticket QR. A booking without a policy snapshot is rejected; Operations never infers or backfills terms from the mutable Package master. *(Removed 2026-11-14 by 003.2 T097 after 003.3 T106 left no caller; owner approved. Kept: only `BookingSettlementReadinessQuery`, for `hasBookingServiceStarted` and `isStaffAssignedToBooking`.)* |
| Complete a resolved cancellation handoff (`completeCancellation(CompleteCancellationHandoffCommand)`; Operations facade method in code: `completeCancellationHandoff`) | `CompleteCancellationHandoffCommand(sessionReference, cancellationCaseReference, operationReference, correlationReference)` | `CancellationCompletionOutcome(ACCEPTED, or REJECTED reason)` | `CANCELLED` only when Settlement reports the handoff every affected ticket resolved (FR-010a); no refund/reschedule logic lives in Operations. *(Added 2026-11-15, owner decision Hồ Hoàng Quốc, 003.2 FR-010a/SC-008, CHK009:)* an affected ticket counts as resolved once it is moved to a replacement session, or a bank refund request is opened for it (no wait for `REFUNDED`), or the ticket has no collected money (FREE/deferred) and the buyer's answer or default is recorded and the folio is CLOSED (003.3 A03, 2026-11-16). 003.3 MUST call this operation (through `WorkshopOperationsSettlementAdapter.completeCancellation`) as soon as the last affected ticket of the case is resolved, idempotently per `operationReference` (task 003.3 T167; on 2026-11-15 no 003.3 service calls it). *(Decided 2026-11-16:)* while any ticket of the session is unresolved, Operations REJECTS with `CANCELLATION_HANDOFF_UNRESOLVED`, changes no state, and the call is safely repeatable; when all are resolved it moves the session to `CANCELLED`, and a replay returns the same result. A session still `CANCELLATION_REQUESTED` 24 hours after `scheduledEndAt` triggers a 003.2 Manager alert (003.2 T133) by email through `NotificationFacade`, recipients from `AccountFacade.listActiveManagerEmails()`; the alert changes nothing. |
| Cancel a whole confirmed booking's entitlements (backfilled 2026-11-12, ed92c6b) | `CancelBookingCommand(bookingReference, cancellationCaseReference, operationReference, correlationReference)` | Accepted or rejected outcome | Called by 003.3 for an accepted direct customer cancellation; revokes the booking's unused future entitlements and releases its future capacity, idempotent by operation reference. No refund logic. *(Removed 2026-11-14 by 003.2 T097 after 003.3 T106 left no caller; owner approved. Kept: only `BookingSettlementReadinessQuery`, for `hasBookingServiceStarted` and `isStaffAssignedToBooking`.)* |
| Check Staff assignment for authorization (backfilled 2026-11-14 from code) | `isStaffAssignedToBooking(StaffBookingAssignmentQuery(staffAccountId, bookingReference))` | `boolean` | True when the Staff member is actively assigned to at least one session the booking holds a valid ticket for. 003.3 uses it for Staff authorization (`WorkshopOperationsSettlementAdapter.staffAssignedToBooking` → `WorkshopSettlementAuthorizationService`); per 003.3 T106 the ticket folio routes reuse it with the ticket's line reference as `bookingReference`.  Read-only; returns no session, Staff or ticket detail. |

**Permitted callers (2026-11-15, documentation sync of 003.2 checklist
CHK034; owner decision Hồ Hoàng Quốc, 2026-11-15).** `cancelTicket` and
`replaceTicketSessions`: 003.3 only (003.3 FR-041/FR-043/FR-044; 003.2
FR-031/FR-034). `reissueTicketQr`: 003.1 only (recipient change, 003.1 FR-041;
003.2 FR-033). `completeCancellation` (`CompleteCancellationHandoffCommand`): 003.3 only (003.2 FR-010a; the 003.3 adapter method is `completeCancellation`, the Operations facade method in code is `completeCancellationHandoff`).
`CancelBookingCommand`: removed 2026-11-14 by 003.2 T097 (no caller left).  Staff and
Manager screens reach these operations only through the owning feature's REST
routes, which apply their own authorization; no other module may call them.

**Operations outbound dependency for the Manager alert (approved by Hồ Hoàng Quốc, 2026-11-15).** `workshopoperations` MAY depend on `notificationmanagement :: facade` (added to `allowedDependencies` in its `package-info.java` by 003.2 T133) to send the unresolved-cancellation alert (003.2 FR-010a, SC-008). Recipients come from `AccountFacade.listActiveManagerEmails()`; with no active Manager no email is sent and Operations writes a WARN log only (no audit entry, no marker; F06, 2026-11-16). *(Updated 2026-11-16, F06-email, Hồ Hoàng Quốc: the dependency and the template `WORKSHOP_CANCELLATION_UNRESOLVED_ALERT` are approved; the template is added by a Flyway migration in `notification_template_definition` + `notification_template_version`; the 007 module owner is informed by the 003 owner after the change, a convention and not a technical gate; release is not blocked by 007.)* One `NotificationFacade` intent per active Manager with `businessEventReference` = `<sessionReference>:CANCELLATION_UNRESOLVED:<managerAccountId>` (idempotent); the `SENT` audit is written only when all intents were accepted.

**`SellingLocationPort` implementation (backfilled 2026-11-12, ed92c6b).** 003.2
implements the Feature 002 SPI `SellingLocationPort.activeSellingLocations()`
(defined in `002-product-management/contracts/interfaces.md`, decision A of
2026-11-01) through `WorkshopSellingLocationAdapter`. It returns each active
canonical `workshop_locations` row once as `SellingLocation(id, displayName,
addressSnapshot)`; unverifiable data throws instead of returning an empty list.
Feature 002 never imports 003.2.

## `WorkshopBookingFacade` — owned by 003.1

Booking also publishes `MlandBookingConfirmedEvent(bookingReference, confirmedAt)`
inside the transaction that changes a booking to `CONFIRMED`. The event is a
durable Spring Modulith publication; Settlement consumes it after commit and
stores the immutable Mland-caused-cancellation policy revision and percentage
effective at `confirmedAt`. The event contains no contact, payment, provider,
ticket, or participant data. Replaying the same booking/time is idempotent; a
conflicting confirmation time for an existing booking snapshot fails closed.
Until the initial 100% policy is revised, the snapshot uses the explicit
`MLAND-CANCELLATION-DEFAULT-100` revision.

*(Superseded 2026-11-14: `MlandBookingConfirmedEvent` belongs to the removed
single direct-booking flow and is no longer published in code; a booking group
publishes `MlandTicketConfirmedEvent` per ticket instead, see "Per-ticket boundary
changes". The table below is kept for history; the current status of every
003.1-owned operation is in "003.1-owned operations — current status
(2026-11-14)".)*

| Operation | Input boundary type | Result boundary type | Invariant / disclosure limit |
| --- | --- | --- | --- |
| Read confirmed booking financial projection | `ConfirmedBookingFinancialProjectionQuery(bookingReference)` | `ConfirmedBookingFinancialProjection(packageReference, frozenPackagePrice, frozenPostPromotionAmount, acceptedDepositAmount, frozenRemainingAmount, participantCount, requiredSessionReferences, bookingLocationReference)` | Available only for the confirmed direct-Mland booking/package context required by 003.3. Deposit is `ceil(frozenPostPromotionAmount / 2)` and remaining amount is the exact difference; no contact, raw provider evidence, or ticket payload. |
| Read verified deposit payments for refund calculation | `VerifiedBookingDepositPaymentsQuery(bookingReference)` | `VerifiedBookingDepositPaymentsProjection(payments: [VerifiedBookingPayment(reference, providerTransactionNumber, originalProviderCreateDate, amountVnd, currencyCode, verified)])` | Booking returns only its own successful verified deposit receipts, ordered by original provider create date then stable reference. No contact, browser URL, raw callback, signature, or final-payment facts cross this boundary. |
| Record final-balance resolution | `RecordFinalBalanceResolvedCommand(bookingReference, settlementReference, operationReference, correlationReference)` | `FinalBalanceResolutionOutcome(ACCEPTED or prior equivalent outcome, or REJECTED reason)` | Only Booking changes its financial state to `FULLY_PAID`, exactly once; it receives no raw final-payment detail. |
| Record booking service outcome (approved 2026-11-05, D13) | `RecordBookingServiceOutcomeCommand(bookingReference, outcome: COMPLETED, CANCELLED or NO_SHOW, settlementReference, operationReference, correlationReference)` | `BookingServiceOutcomeResult(ACCEPTED or prior equivalent outcome, or REJECTED reason)` | Called only by 003.3 after the folio becomes `FINALIZED` (→ `COMPLETED`) or `CLOSED` (customer cancellation or Mland remedy → `CANCELLED`; full no-show → `NO_SHOW`). Only Booking changes its service status, exactly once per outcome; no attendance, payment or PII payload. |
| Record booking refund status (approved 2026-11-05, D14) | `RecordBookingRefundStatusCommand(bookingReference, status: REFUND_PENDING or REFUNDED, settlementReference, operationReference, correlationReference)` | `BookingRefundStatusResult(ACCEPTED or prior equivalent outcome, or REJECTED reason)` | Called only by 003.3 for a Mland-caused remedy: `REFUND_PENDING` when the first allocation is dispatched, `REFUNDED` when every allocation is verified. Never used for a post-`FINALIZED` overcharge correction. Monotonic; no provider payload. |
| Record definite payment failure (decided 2026-11-19, Hồ Hoàng Quốc, 003.1 FR-032) | `RecordDefinitePaymentFailureCommand(merchantReference, providerResponseCode, providerTransactionStatus, eventReference)` | `PaymentFailureOutcome(ACCEPTED or prior equivalent outcome, or REJECTED reason)` | Called only by `paymentintegration` after the IPN passed the signature, owner, amount and currency checks and its code is in the 003.1 FR-032 failure list; never for `01`, `07`, `75`, `99`, unknown codes or the return URL. Booking resolves the group attempt by `merchantReference`, sets an `INITIATED`/`PENDING` attempt to `FAILED` under the group row lock and leaves the group `PENDING_PAYMENT`, `UNPAID` and the hold unchanged. Idempotent: a repeated `eventReference` or an attempt already `FAILED` answers the prior outcome; a `SUCCESS` or `EXPIRED` attempt is unchanged. Never receives the raw VNPay payload or checksum. |
| Apply verified deposit evidence | `ApplyVerifiedDepositPaymentEvidenceCommand(bookingReference, verifiedEvidenceReference, operationReference, correlationReference)` | `DepositPaymentEvidenceOutcome(ACCEPTED or prior equivalent outcome, or REJECTED reason)` | Called only by `paymentintegration` after one successful verified dispatch. Booking checks its frozen reference/amount/currency, commits capacity atomically, and never receives raw VNPay payload or checksum. |
| Cancel a confirmed booking (backfilled 2026-11-12, ed92c6b) | `CancelConfirmedBookingCommand(bookingReference, cancellationCaseReference, operationReference, correlationReference)` | `BookingCancellationOutcome(ACCEPTED, or REJECTED reason)` | Called only by 003.3 for an accepted direct customer cancellation; idempotent by operation reference. |
| Read Loyalty outcome source (backfilled 2026-11-12, 4fa224e) | Booking's Loyalty outcome source query used by 003.3 | Member Loyalty source or none | Answers for a Member booking in `CONFIRMED`, `COMPLETED`, `NO_SHOW` or `CANCELLED`, so an outcome decided after attendance or cancellation still reaches Loyalty; a Guest booking has none. |
| Request delivery of existing ticket set | `RequestBookingTicketRedeliveryCommand(bookingReference, activeUnusedTicketReferences, operationReference, correlationReference)` | `TicketRedeliveryOutcome(ACCEPTED or REJECTED reason)` | Existing ticket/QR only; Booking delivers to the stored contact channel and does not change contact, tickets, payment, or Magic Link. This supports the already-approved Operations resend flow. |
| Read immutable notification delivery payload | Named interface `workshopbooking :: notification`; `BookingNotificationDeliveryPayloadQuery(notificationReference)` | `BookingNotificationDeliveryPayload(notificationKind, storedRecipient, renderedBookingContent, renderedTicketArtifacts, bookingAccessLink, correlationReference)` | Only Feature 007 may declare a dependency on `workshopbooking :: notification`, and it calls this boundary only after receiving the matching handoff. The raw bearer URL may be persisted only in the protected snapshot for this purpose; it is excluded from events, business audit, and application/provider logs. The URL is scrubbed from all snapshots by the next one-minute cleanup sweep after its Magic Link expires or is revoked; the rest of each delivery snapshot remains immutable. |

## Post-commit Booking notification handoff — 003.1 to Feature 007

003.1 persists one immutable notification handoff in the same local transaction
as an accepted confirmed-booking delivery, confirmation resend, or Magic-Link
rotation. Only after that transaction commits, it publishes at-least-once
`BookingNotificationReady(notificationReference, bookingReference,
notificationKind, correlationReference)`. The event contains no recipient
address, ticket credential/QR payload, Magic-Link value, payment data, or other
unnecessary PII.

Feature 007 deduplicates by `notificationReference`, reads the immutable
render-ready payload through `WorkshopBookingNotificationFacade` in the
`workshopbooking :: notification` named interface, and owns provider choice,
send state, retry policy, and delivery logs. It may not query Booking entities
or reconstruct the message from current Booking state. A duplicate event must
not create a second delivery request for the same handoff reference. A delivery
failure or an uncertain provider outcome is operational only: it does not
reverse a confirmed booking, payment, hold, ticket, resend acceptance, or
Magic-Link revocation/rotation. Access to the bearer URL is limited to this
facade read following the corresponding event; the notification provider must
not write the URL to logs. The snapshot retains it only while the associated
Magic Link is active and the scheduled workshop has not ended; a one-minute
cleanup sweep scrubs it after expiry or revocation.

## Booking notification delivery-failed signal — Feature 007 to 003.1

When Feature 007 exhausts its technical retry attempts (at most 2 attempts within 1–2 minutes) without successful SMTP acceptance, it publishes at-least-once `BookingNotificationDeliveryFailed(notificationReference, bookingReference, notificationKind, failureCode, failedAt, correlationReference)`.

- **Package and Modulith Boundary**: Defined in `edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade` under named interface `workshopbooking :: notification`. Feature 007 imports and publishes this event; 003.1 consumes it. This maintains unidirectional module dependency (`notificationmanagement -> workshopbooking :: notification`) with zero cyclic dependencies.
- **DeliveryFailureCode enum**: `PROVIDER_UNAVAILABLE` (network/timeout), `PROVIDER_REJECTED` (SMTP 5xx response), `RECIPIENT_INVALID` (malformed address/bounced), `PAYLOAD_MISSING` (could not read payload from Booking facade).
- **Semantics**: "Failed" represents the final operational delivery outcome for that specific handoff reference. Transient failures during technical retry do not emit this signal. Exactly one failure event is published per failed `notificationReference`.
- **Decoupling and Retry Ownership**: 003.1 owns business-level retry scheduling (5 minutes, 30 minutes, 2 hours per FR-015a/b) and escalation to Manager. Each business retry generates a brand-new notification handoff with a fresh `notificationReference`, which Feature 007 handles and deduplicates independently.
- **PII and Security (D13)**: Contains no recipient address, message content, Magic-Link token, ticket/QR artifact data, or payment secrets.

## `WorkshopSettlementFacade` — owned by 003.3

| Operation | Input boundary type | Result boundary type | Invariant / disclosure limit |
| --- | --- | --- | --- |
| Open a customer-commitment cancellation handoff | `OpenCancellationHandoffCommand(sessionReference, affectedBookingReferences, cancellationReasonReference, operationReference, correlationReference)` | `CancellationHandoffOutcome(ACCEPTED + cancellationCaseReference, or REJECTED reason)` | Consumed asynchronously from the Operations-owned durable outbox after Operations has entered `CANCELLATION_REQUESTED`; delivery is idempotent by `operationReference`, and opening a case does not alter capacity, tickets, or payment. *(Added 2026-11-15, 003.2 CHK035, owner decision Hồ Hoàng Quốc via 003.2 FR-010a:)* the command still carries booking references (the unit Operations sends); the unit of resolution is the **ticket**: the case covers every ticket of those bookings that holds an entitlement in the session. 003.3 derives that ticket set itself (Operations does not send ticket references), and a case with no such ticket is completed when it is received (`completeCancellation` below). |
| Read cancellation-handoff resolution | `CancellationHandoffResolutionQuery(cancellationCaseReference)` | `CancellationHandoffResolution(resolved)` | `resolved` is true only after every affected **ticket** of the case is resolved: moved to a replacement session, or a bank refund request opened for it (no wait for `REFUNDED`), or the ticket has no collected money (FREE/deferred) and the buyer's answer or default is recorded and the folio is CLOSED (003.3 A03, 2026-11-16) (same definition as `completeCancellation` below and 003.2 FR-010a; *supersedes 2026-11-15, CHK035:* "every affected booking case has a recorded remedy"). It is true at once for a case with no affected ticket. The 003.3 service calls `completeCancellation` when this becomes true (T167); `CANCELLATION_HANDOFF_UNRESOLVED` (decided 2026-11-16, owner Hồ Hoàng Quốc) is the typed rejection of `completeCancellation` when Operations finds any ticket of the session still unresolved (no state change, safely repeatable; REST-equivalent 409 `INVALID_STATE` where it surfaces), and a repeat of an already accepted `completeCancellation` is a replay of the same outcome by `operationReference`. It exposes no refund amount/payment/PII detail. |
| Apply verified settlement evidence | `ApplyVerifiedSettlementPaymentEvidenceCommand(bookingReference, verifiedEvidenceReference, merchantReference, paymentKind, amountVnd, currencyCode, providerTransactionNumber, originalProviderCreateDate, operationReference, correlationReference)` | `SettlementPaymentEvidenceOutcome(ACCEPTED or prior equivalent outcome, or REJECTED reason)` | Called only by `paymentintegration` after one successful verified dispatch. `paymentKind` is `FINAL_PAYMENT`, `REFUND` (legacy for Mland money: direct Mland refunds are bank refunds since 003.3 T104); `amountVnd` is a positive whole-VND amount and `currencyCode` is `VND`. For FINAL_PAYMENT, the transaction number and original payment URL create date are retained as redacted verified facts owned by Settlement. Settlement checks its frozen bill/refund reference, amount and currency, and may send only the existing safe final-balance conclusion to Booking. It receives no raw VNPay payload or checksum. |
| Read immutable notification delivery payload | `SettlementNotificationDeliveryPayloadQuery(notificationReference)` | `SettlementNotificationDeliveryPayload(notificationKind, storedRecipient, renderedSettlementContent, correlationReference)` | Callable only by the Feature 007 notification boundary after a matching handoff. Recipient/content are retained for at most 30 days after handoff. No payment-action link is included in settlement notification payloads. No payload is returned after 30 days. Recipient/contact data MUST NOT be included in the event or provider/audit logs. |

## Post-commit Settlement notification handoff — 003.3 to Feature 007

003.3 persists one notification handoff in the same local transaction
as a customer-facing verified settlement/refund outcome. Only after that transaction
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
bill, verified payment/refund, remedy case, or Booking
`FULLY_PAID` state.

## Verification obligations

- `ApplicationModules.verify()` must permit only the named facade dependencies.
- Contract tests must prove all-or-none reserve/commit/replacement/refund
  consequences, same-reference retry equivalence, mismatch rejection, and no
  entity/repository/HTTP DTO leakage.
- Integration tests must prove failed post-commit event delivery cannot reverse
  a successful command or cause a second capacity/payment effect.

## Direct-booking open-folio lifecycle (approved 2026-11-05)

Approved by the Feature 003 owner per
[direct-booking-open-folio-policy.md](../direct-booking-open-folio-policy.md)
and decisions D1–D5 in
[open-folio-lifecycle-proposal-2026-11-05.md](../003.3-workshop-settlement-and-changes/design/open-folio-lifecycle-proposal-2026-11-05.md).
Folio states: `OPEN` → `FINALIZED` (checkout, `balanceDue = 0`) or `OPEN` →
`CLOSED` (ordinary cancellation/no-show at the verified collected amount, or
after a verified Mland-caused remedy refund, D11). Both terminal states are
immutable.

| Lifecycle point | Boundary | Rule |
| --- | --- | --- |
| Open at deposit confirmation (P1) | Existing `MlandBookingConfirmedEvent(bookingReference, confirmedAt)` consumed by 003.3 after commit; 003.3 then reads `ConfirmedBookingFinancialProjectionQuery` and `VerifiedBookingDepositPaymentsQuery`. | Idempotent per `bookingReference`: one OPEN folio with the immutable base line (frozen post-promotion amount) and the verified deposit appended as the first collection. Replay returns the existing folio. No new 003.1 operation. |
| Attendance-finalized signal (P2) | New 003.2 post-commit, reference-only `BookingAttendanceFinalizedEvent(bookingReference, finalizedAt)`, published once per booking when booking-wide attendance becomes terminal: every required session `ATTENDANCE_FINALIZED` and the 24-hour Manager correction window closed. | Trigger only; it carries no attendance facts and never decides a financial outcome. |
| Read booking attendance outcome (P2) | New `WorkshopOperationsFacade` query `BookingAttendanceOutcomeQuery(bookingReference)` → `BookingAttendanceOutcome(terminal, outcome)`; `outcome` ∈ `ATTENDED`, `NO_SHOW`, `PARTIAL`. | `terminal` is true only when every required session is `ATTENDANCE_FINALIZED` and the 24-hour correction window has closed, so no later correction can change the outcome. `NO_SHOW` only when every ticket on every required session is a final `NO_SHOW`; `PARTIAL` when some but not all tickets attended; no ticket, participant, or PII payload. |
| Close on final no-show (P2; superseded 2026-11-13 by per-ticket closing) | 003.3 consumer of `BookingAttendanceFinalizedEvent` reads the query. | When `terminal` and `NO_SHOW`, close the OPEN folio as `CLOSED` at the verified collected amount, idempotently: no refund, no VNPay QueryDR/refund, no receivable. `ATTENDED`/`PARTIAL` proceed to normal checkout. |
| Close on ordinary cancellation (P3) | Existing customer-cancellation flow (003.3 FR-015). | In the same Settlement transaction that resolves the case after Operations accepts, close the folio as `CLOSED` at the verified collected amount. Mland-caused remedy keeps its separate refund flow. |
| Close after Mland-caused remedy (D11) | Existing Mland-caused remedy flow (003.3 FR-020/FR-021). | When every refund allocation has signed matching QueryDR evidence, close the folio as `CLOSED` in the Settlement transaction that records the final allocation; pending/unknown refunds keep it `OPEN`. |
| Legacy confirmed bookings (D5) | Append-only Flyway backfill, not a facade operation. | Creates one OPEN folio for each existing confirmed direct booking from its frozen amounts and verified deposit receipts; must be idempotent with the P1 consumer. |

## Per-ticket boundary changes (decision 2026-11-12; partly implemented, status per row as of 2026-11-14)

Facade changes for 003.1 FR-030–FR-043, 003.2 FR-030–FR-036 and 003.3
FR-030–FR-045. ~~Planned; not implemented yet.~~ *(Superseded 2026-11-14: most
rows are implemented; each row states its status, verified against
`ppswbs_backend/src` on 2026-11-14.)* Record shapes of rows still planned are
indicative; final names are chosen at implementation.

| Owner | Operation | Change |
| --- | --- | --- |
| 003.2 | Reserve/commit/release capacity | Accept a booking-group reference with lines (package, session set, ticket count); one seat per ticket per session; all or none for the group before confirmation, per ticket afterwards. **Reserve implemented** as `reserveGroupCapacity(ReserveGroupCapacityCommand(groupReference, lines[(bookingReference, packageReference, orderedRequiredSessionReferences, ticketCount)], operationReference, correlationReference)) → GroupCapacityOutcome(status, lineHoldReferences, expiresAt, reason, failingLines)`; commit/release use the existing `commitBookingHolds`/`releaseBookingHolds` per line `bookingReference` (no separate group commit/release operation). |
| 003.2 | Cancel ticket entitlements | Implemented 2026-11-13 as `WorkshopOperationsFacade.cancelTicket(CancelTicketCommand(ticketReference, cancellationCaseReference, operationReference, correlationReference)) → TicketCancellationOutcome(status, ticketReference, releasedSessionReferences, reason)`. Releases the ticket's seats in every future session and deletes those entitlements, keeping a `ticket_entitlement_releases` row each; a session already started, finalized, cancelled or with attendance keeps its record and is not listed. The booking line's confirmed hold loses one seat (released at zero), so the seat is really free. Idempotent per `operationReference` (a replay re-reads the release rows); `REJECTED` with `TICKET_NOT_FOUND` or `INVALID_STATE`. No other ticket of the group is touched. *(Added 2026-11-15, 003.2 CHK010, owner decision Hồ Hoàng Quốc:)* the ticket becomes `CANCELLED` at the call and its QR is kept, not regenerated; a later scan or check-in answers `TICKET_CONFLICT`, and `TICKET_QR_REVOKED` is reserved for a recipient change (003.2 FR-031, FR-033). |
| 003.2 | Revoke and reissue ticket QR | Implemented 2026-11-13 as `WorkshopOperationsFacade.reissueTicketQr(ReissueTicketQrCommand(ticketReference, QrRevokeReason.RECIPIENT_CHANGED, operationReference, correlationReference)) → TicketQrReissueOutcome(status, ticketReference, qrGeneration, qrReference, reason)`. Idempotent per `operationReference` (replay re-reads the QR, the ledger never stores it); `REJECTED` with `TICKET_NOT_FOUND`, `INVALID_STATE` (not ACTIVE) or `TICKET_ALREADY_CHECKED_IN`. Joins the caller's transaction. The revoked QR is kept in `ticket_qr_generations`; check-in answers 409 `TICKET_QR_REVOKED`. |
| 003.2 | Replace ticket sessions | Implemented 2026-11-13 as `WorkshopOperationsFacade.replaceTicketSessions(ReplaceTicketSessionsCommand(lines[(ticketReference, targetSessionReferences)], type, operationReference, correlationReference)) → TicketSessionReplacementOutcome(status, failingLineIndexes, reason)`. All or none across the lines; one seat per ticket per target; the QR, the ticket and past attendance are kept. Sources and targets are paired in start order, so the missed session's own replacement carries the 7-day `CUSTOMER_LATE_ARRIVAL` window (`LATE_ARRIVAL_WINDOW_EXPIRED`) while a following session may fall outside it. `CUSTOMER_ADVANCE` needs a future, untouched source; `MLAND` accepts a cancelled or running source and neither checks nor consumes quota. Refusals: `INVALID_REPLACEMENT`, `INVALID_STATE`, `TICKET_NOT_FOUND`, `INSUFFICIENT_CAPACITY`, `RESCHEDULE_QUOTA_EXHAUSTED`, each naming the failing lines. |
| 003.2 | Find equivalent replacement sessions (T112, decided 2026-11-14) | `WorkshopOperationsFacade.findEquivalentReplacementSessions(FindEquivalentReplacementSessionsQuery(ticketReference, cancelledSessionReference)) → EquivalentReplacementSessions(candidates[(sessionReference, startAt, endAt)])` | Read-only, takes no seat. Returns ALL valid equivalent sessions ordered by start, or an empty list: same package as the ticket at any location where the Package is `ACTIVE` *(proposed 2026-11-17, spec FR-046, owner decision: replaces "same branch"; each candidate also carries `locationId` and `locationName`)*, start after now (the cancellation moment), at least one seat and one unit of reschedule quota left, package session order and gaps kept, the ticket's other session unchanged. 003.3 stores the list in the Mland offer, lets the buyer pick one, re-asks the facade to filter by current seats when the buyer answers, and then calls `replaceTicketSessions` (type MLAND) for the chosen session. An empty list means a direct 100% bank refund. |
| 003.1 | Ticket recipient email for a Mland cancellation notice (T112, decided 2026-11-14) | `WorkshopBookingFacade.getTicketRecipientEmail(ticketCode) → String` | Read-only; null when the ticket is unknown, has no recipient, or the recipient equals the buyer's contact email. Used only inside the caller's transaction to hand one "session cancelled, the buyer chooses" email to the settlement notification handoff; never an event, audit or log field. |
| 003.2 | Attendance outcome / readiness / `BookingAttendanceFinalizedEvent` | ~~Keyed by ticket booking instead of booking.~~ *(Superseded 2026-11-14: the outcome is per 003.2 ticket reference through `getTicketAttendanceOutcome(TicketAttendanceOutcomeQuery(ticketReference))` (implemented); the signal is the new `TicketAttendanceFinalizedEvent` (003.2 T092, not yet in code), and there is no separate readiness query. `BookingAttendanceFinalizedEvent` and `BookingAttendanceOutcomeQuery` are removed with 003.3 T105; see "Direct booking is only a booking group".)* |
| 003.1 | `MlandBookingConfirmedEvent` and `ConfirmedBookingFinancialProjection` | ~~One per ticket booking; the projection gives the ticket's final price, allocated booking-time collection and kind (`FREE`, `FULL`, `DEPOSIT`, `DEFERRED_BELOW_VNPAY_MINIMUM`) so 003.3 opens the ticket folio.~~ *(Superseded: implemented instead as a separate event `common.booking.MlandTicketConfirmedEvent(groupReference, ticketReference, finalPriceVnd, collectedAtBookingVnd, remainingVnd, confirmedAt)`, published once per ticket in the group-confirming transaction (`GroupConfirmationService`; `ticketReference` carries the `ticketCode`) and consumed by 003.3 `TicketConfirmedFolioConsumer` to open one OPEN folio per ticket. The event carries no kind field. `MlandBookingConfirmedEvent` (per booking) stays only for the legacy single-booking flow and is removed with it; `ConfirmedBookingFinancialProjection` is replaced for a ticket folio by `getTicketFolioContext`, see "Direct booking is only a booking group".)* |
| 003.1 | Verified booking-time payments | Group payment evidence plus each ticket's allocated share. |
| 003.3 → 003.1 | Booking status conclusions | Per ticket booking; refund status is `REFUND_PENDING` when 003.3 opens the bank-refund request (kept through `CLOSED_UNREACHABLE`) and `REFUNDED` after bank reconciliation (decided 2026-11-15, 003.1 FR-050). **Final balance per ticket (decided 2026-11-14 by Hồ Hoàng Quốc, ERROR-LOG BE-DESIGN-0006; implemented 2026-11-14):** `WorkshopBookingFacade.recordTicketFinalBalanceResolved(RecordTicketFinalBalanceResolvedCommand(ticketCode, settlementReference, operationReference, correlationReference)) → FinalBalanceResolutionOutcome`. 003.3 sends it when a ticket folio becomes `FINALIZED` (zero balance, no collection awaiting verification; after `FINALIZED` no add-on, void or reopening can change it), from every finalizing path, inside the finalization transaction (`MANDATORY`). A recorded debt does not finalize a folio, so a debt settled later is concluded when the collection that clears it finalizes the folio. 003.1 sets only that ticket's `financialStatus` to `FULLY_PAID` with audit `TICKET_FINAL_BALANCE_RESOLVED` on the group; idempotent (an already `FULLY_PAID` ticket is accepted without a second write); `BOOKING_NOT_FOUND` for an unknown ticket; `INVALID_STATE` unless the ticket is `CONFIRMED` or `COMPLETED` and not `REFUND_PENDING`/`REFUNDED`. A refusal keeps the folio `FINALIZED` (the money is verified) and 003.3 audits `TICKET_BALANCE_CONCLUSION_REJECTED`; `BOOKING_NOT_FOUND` means the folio is not a ticket's and is skipped. Reference-only: no amount, contact or payment data. Replaces the booking-wide `recordFinalBalanceResolved` for ticket folios. |
| 003.3 → paymentintegration | Refund adapter | No longer used for direct Mland money (bank refunds); VNPay collection unchanged. |

## Direct booking is only a booking group; attendance per ticket only (decision 2026-11-13)

Owner instruction of Hồ Hoàng Quốc, 2026-11-13 (003.1 FR-044–FR-051, 003.3
FR-046/FR-047). Approved by the owner in session 2026-11-13. ~~Not
implemented.~~ *(Superseded 2026-11-14: partly implemented; each row states its
status, verified against `ppswbs_backend/src` on 2026-11-14. The consolidated
003.1 view is "003.1-owned operations — current status (2026-11-14)" below.)*
Every direct booking is a booking group with one ticket booking
per person; the single direct `WorkshopBooking` flow is removed.

| Owner | Operation | Change |
| --- | --- | --- |
| 003.2 | `BookingAttendanceFinalizedEvent`, its outbox, `BookingAttendanceOutcomeQuery` | **Removed** once 003.2 T092 publishes `TicketAttendanceFinalizedEvent` per ticket (keyed by the 003.2 ticket reference, `attendance_tickets.reference`). Per-ticket outcome: existing `getTicketAttendanceOutcome(TicketAttendanceOutcomeQuery(ticketReference))`. ~~Event shape, outbox and per-ticket readiness are chosen by T092, not here.~~ *(Superseded 2026-11-14: decided in the `TicketAttendanceFinalizedEvent` row below, BE-DESIGN-0004.)* **Status 2026-11-14:** not yet removed; `BookingAttendanceFinalizedEvent`, `getBookingAttendanceOutcome` and the 003.3 consumer are still in code until 003.3 T105. |
| 003.3 | `BookingAttendanceFinalizedEventConsumer` | **Removed**; replaced by a consumer of `TicketAttendanceFinalizedEvent` that closes only that ticket's folio on terminal `NO_SHOW` (003.3 FR-046). |
| 003.1 | Resolve a 003.2 ticket reference to its ticket booking | ~~**Missing, proposed:**~~ **Implemented 2026-11-13 (003.1 T099):** `WorkshopBookingFacade.findTicketBookingByOperationsReference(ticketReference) → Optional<TicketBookingRef(ticketCode, groupCode)>`, read-only from `ticket_bookings.operations_ticket_reference`; no contact or payment data. Needed because ticket folios are keyed by `ticketCode`. |
| 003.1 | `ConfirmedBookingFinancialProjectionQuery`, `VerifiedBookingDepositPaymentsQuery`, `RecordLateDepositEvidenceCommand` (looks up a `WorkshopBooking` only), `RecordFinalBalanceResolvedCommand`, `CancelConfirmedBookingCommand`, `MlandBookingConfirmedEvent` (per booking) | Serve only the removed direct single-booking deposit/folio flow. **Removed** with it when no remaining caller exists (the backend step checks each against the per-ticket flow); the per-ticket equivalents are those of "Per-ticket boundary changes". |
| 003.1 | `ApplyVerifiedDepositPaymentEvidenceCommand` | **Kept:** `paymentintegration` delivers group VNPay evidence through it (003.1 T093 routes by the group attempt's merchant reference). Only its single-booking branch is removed; renaming is out of scope. |
| 003.2 | `BookingSettlementReadinessQuery`, `BookingCancellationEligibilityQuery`, `ReplaceRequiredSessionsCommand`, `CancelBookingCommand` (booking-wide) | Not changed by this decision. They lose their direct subject together with the booking-wide 003.3 routes; their removal waits for the open 003.3 decision on per-ticket replacements (003.3 spec, decision 2026-11-13, "Open"). **Decided 2026-11-14:** removed after 003.3 T106. |
| 003.2 | `TicketAttendanceFinalizedEvent` (003.2 T092, decided 2026-11-14, BE-DESIGN-0004; not yet in code on 2026-11-14) | Reference-only, no PII, no outcome: `(eventId, ticketReference, windowClosedAt)`, published once per ticket after its 24-hour correction window through a new outbox `workshop_ticket_attendance_finalization_outbox` (unique `ticket_reference, event_type`), at-least-once with consumer deduplication. `TicketAttendanceOutcome` gains `terminal` and `windowClosesAt`; no separate readiness query. |
| 003.1 | `getTicketFolioContext(ticketCode)` *(new, decided 2026-11-14, for 003.3 T106; implemented by 003.1 T107, not yet in code on 2026-11-14)* | Read-only → `TicketFolioContext(ticketCode, groupCode, lineReference, packageReference, locationReference, sessionReferences, buyerMemberAccountId or null)` or none. Replaces `confirmedProjection` for a ticket folio; no contact data, no payment data, no QR. |
| 003.1 | `consumeBuyerProof(ConsumeBuyerProofCommand(groupCode, memberAccountId or null, buyerVerificationReference or null))` *(new, decided 2026-11-14, ~~for 003.3 T099/T106~~ for 003.3 T099, T100, T101 (changes); T106 reads only and never consumes it (Guest reads use contact proof via `POST /workshop-tickets/{ticketCode}/folio-lookups`); implemented by 003.1 T107, not yet in code on 2026-11-14)* | MANDATORY, inside the caller's transaction. A Member is accepted when they own the group; otherwise the single-use, group-scoped, 15-minute `buyerVerificationReference` of 003.1 FR-041 is consumed (`BuyerVerificationService.consume`), so a rolled-back cancellation leaves it unused. Result `ACCEPTED` or a typed refusal reusing the 003.1 codes (`BUYER_VERIFICATION_REQUIRED`, `BUYER_VERIFICATION_FAILED`, `BUYER_VERIFICATION_EXPIRED`; aligned 2026-11-14); never reveals whether the group exists to a stranger. |

The "Direct-booking open-folio lifecycle" rows P2 (attendance signal, outcome
query, close on final no-show) and D5 (legacy backfill) are superseded by this
section for direct bookings.

### 003.1-owned operations — current status (2026-11-14)

Consolidated view of every `WorkshopBookingFacade` operation, event and port
listed in this contract, verified against `ppswbs_backend/src` on 2026-11-14.
It records status only; it takes no new decision. Status values: **kept**,
**legacy until 003.3 T106** (removed by
003.1 T100 once 003.3 T106 lands), **new 2026-11-14** (decided, not yet in
code), **removed**.

| Operation (code name) | Status | Basis |
| --- | --- | --- |
| `getConfirmedBookingFinancialProjection` | removed (003.1 T100, done 2026-11-14; confirm in code at converge) | 003.1 T100 "still legacy-only and kept"; replaced for a ticket folio by `getTicketFolioContext`. |
| `getVerifiedDepositPayments` | removed (003.1 T100, done 2026-11-14; confirm in code at converge) | 003.1 T100. |
| `getLoyaltyOutcomeSource` (single booking) | removed (003.1 T100, done 2026-11-14; confirm in code at converge) | 003.1 T100. |
| `verifyCustomerBookingAccess` | kept (spec FR-046) | 003.1 T100; 003.3 T106 replaces it with `getTicketFolioContext` and the folio-lookup contact proof. |
| `recordFinalBalanceResolved` | removed (003.1 T100, done 2026-11-14; confirm in code at converge) | 003.1 T100. Per-ticket replacement `recordTicketFinalBalanceResolved` (2026-11-14, see "Per-ticket boundary changes"). |
| `cancelConfirmedBooking` | removed (003.1 T100, done 2026-11-14; confirm in code at converge) | 003.1 T100. |
| `recordBookingServiceOutcome`, `recordBookingRefundStatus` | kept (removal not decided) | **Ticket-level since 2026-11-16 (owner Hồ Hoàng Quốc, BE-DESIGN-0010):** when `bookingReference` is a ticket code the conclusion applies to that ticket only (`recordBookingServiceOutcome` COMPLETED/NO_SHOW/CANCELLED from `CONFIRMED`; `recordBookingRefundStatus` REFUND_PENDING from `DEPOSIT_PAID`/`FULLY_PAID`, REFUNDED from `REFUND_PENDING`; replay accepted, other moves `INVALID_STATE`, audit on the group); signatures unchanged; any other reference keeps the legacy booking path. Previously called only by booking-wide 003.3 services. The per-ticket equivalent is "Booking status conclusions" in "Per-ticket boundary changes". |
| `recordLateDepositEvidence` | legacy; listed for removal (row above) | Still called by `paymentintegration` (`PaymentIngressService`) on 2026-11-14; looks up a `WorkshopBooking` only. |
| `applyVerifiedDepositPaymentEvidence` | kept (group routing only) | 003.1 T093/T100: routes group VNPay evidence by the group attempt's merchant reference and refuses everything else. |
| `requestBookingTicketRedelivery` | kept, refuses every request | 003.1 T100: a group ticket is resent per ticket (FR-048); removal not decided. |
| Notification delivery payload (`workshopbooking :: notification`) | kept | 003.1 T100: payload now comes only from the group snapshots. |
| `MlandBookingConfirmedEvent` | legacy; no longer published | Only Settlement consumers remain in code; replaced by `MlandTicketConfirmedEvent`. |
| `MlandTicketConfirmedEvent(groupReference, ticketReference, finalPriceVnd, collectedAtBookingVnd, remainingVnd, confirmedAt)` | kept | Published per ticket by `GroupConfirmationService`; consumed by 003.3 `TicketConfirmedFolioConsumer`. |
| `getGroupLoyaltyOutcomeSource(groupReference)` | kept | Implemented; called by 003.3 `WorkshopBookingSettlementAdapter`. |
| `findTicketBookingByOperationsReference(ticketReference) → Optional<TicketBookingRef(ticketCode, groupCode)>` | kept | Implemented 2026-11-13 by 003.1 T099. |
| `LatePaymentRefundPort.openLatePaymentRefund` (declared by 003.1, implemented by 003.3) | kept | Declared in `workshopbooking.facade`; implemented by 003.3 `LatePaymentRefundPortAdapter`; called by `LatePaymentReviewService`. |
| `getTicketFolioContext(ticketCode)` | new 2026-11-14 | For 003.3 T106; implemented by 003.1 T107; implemented (003.1 T107 done). |
| `consumeBuyerProof(ConsumeBuyerProofCommand)` | new 2026-11-14 | For 003.3 T099, T100, T101; implemented by 003.1 T107; implemented (003.1 T107 done). |
| `listBookingTimePayments(locationId, workingDate)` (name proposed: "list booking-time payments by day and location") | new 2026-11-16, decided by Hồ Hoàng Quốc (003.3 FR-056, A04); **task: 003.1 T150, implemented 2026-11-16** (`WorkshopBookingFacade.listBookingTimePayments(Long, LocalDate)` returns `List<BookingTimePayment>`; an attempt whose merchant reference is in `group_excess_payments` is excess money and is not listed) | Read-only: returns the stored online booking-time VNPay payments of the branch and Vietnam working day (one record per payment: group code, `ticketCodes` list, `locationIds[]`, `amountVnd` = stored requested amount `group_payment_attempts.amount_vnd`, `verificationStatus` `CONFIRMED` / `LATE_PENDING_REVIEW` (money received, group not confirmed, awaiting FR-040 review) / `UNVERIFIED`, `merchantReference` and `attemptReference` (no VNPay transaction number stored in V1), `createdAt`, `confirmedAt` = group `confirmed_at`). The `locationId` input is a filter: every payment with at least one session at that location is returned; the day total over the whole system counts a payment once, per-location totals must not be summed (decided 2026-11-16, option B). It calls no VNPay endpoint. Consumed only by the 003.3 reconciliation list (T133). |
| `recordMismatchedGroupPaymentEvidence(RecordMismatchedGroupPaymentEvidenceCommand)` | new 2026-11-16, owner Hồ Hoàng Quốc (option A, FR-010f F14); **task: 003.1 T144** | Called by `paymentintegration` for signed 00/00 evidence of a group payment whose amount or currency differs. Returns `true` when the merchant reference is a group attempt (the caller then stores the shared `payment_ingress_receipts` row), `false` otherwise. Records one audit entry and one WARN, never confirms the group, and hands the evidence to the FR-040 review at `holdExpiresAt` (or at once when it arrives later). The IPN answer stays 04. No contact data in the command. |
| Single direct-booking creation, quote, deposit, Magic-Link and lookup flow | removed | 003.1 T100 (2026-11-13). |

## Late-payment review and bank refund (decision 2026-11-13)

Approved by the Feature 003 owner on 2026-11-13 for 003.1 FR-040/FR-040a.

> **Decided 2026-11-15 by Hồ Hoàng Quốc, owner Feature 003 (003.1 FR-013a,
> CHK001, T124)** *(earlier label "Proposed 2026-11-15 … not yet approved"
> superseded 2026-11-15)*: a
> second verified success with a different provider transaction or merchant
> reference on a `CONFIRMED` group needs its own Booking-to-Settlement port,
> because `OpenLatePaymentRefundCommand` requires a `reviewReference` and fixes
> reason `LATE_PAYMENT_NOT_RECONFIRMED`. ~~Candidate:~~ Decided port:
> `ExcessPaymentRefundPort.openExcessPaymentRefund(OpenExcessPaymentRefundCommand(groupCode,
> evidenceReference, amountVnd, operationReference, correlationReference)) →
> LatePaymentRefundOutcome`, declared in `workshopbooking.facade`, implemented by
> 003.3, reason `EXCESS_OR_DUPLICATE_MONEY`, source reference =
> `evidenceReference`, idempotent on `(reason, source_reference)`, propagation
> `MANDATORY` inside the evidence transaction. Booking records the money in
> `group_excess_payments` (003.1 data-model; exact column types fixed by the
> migration). The Manager alert follows the "Manager queue" rule below (active
> Managers only). ~~Name and shape must be decided before T124 is coded.~~
> *(superseded 2026-11-15)*

- **Direction.** Booking may not depend on Settlement (Settlement already
  depends on `workshopbooking :: facade`). Booking therefore declares the port
  `LatePaymentRefundPort` in `workshopbooking.facade`, and Settlement implements
  it. Booking never references Settlement types.
- **Command.** `OpenLatePaymentRefundCommand(groupCode, reviewReference,
  evidenceReference, amountVnd, operationReference, correlationReference)`.
- **Amount source.** The verified late evidence amount recorded on the review.
  No ticket was confirmed, so there is no folio and no per-ticket allocation:
  one group-level bank-refund request per review, reason
  `LATE_PAYMENT_NOT_RECONFIRMED`, source reference = `reviewReference`.
  Settlement rechecks the amount against Booking's verified late evidence
  through the Booking facade before opening the request.
- **Result.** `LatePaymentRefundOutcome(status, refundRequestReference,
  refundStatus, reason)` with status `ACCEPTED`, `PRIOR_EQUIVALENT` (same source
  and amount already opened) or `REJECTED` with a safe reason (for example
  `AMOUNT_MISMATCH`, `EVIDENCE_NOT_FOUND`, `SOURCE_CONFLICT`).
- **Idempotency.** Unique `(reason, source_reference)` in
  `settlement_refund_requests`; a repeated command returns `PRIOR_EQUIVALENT`.
- **Transaction.** The port runs with propagation `MANDATORY` inside the
  Manager decision transaction. A failure rolls back both, so a review becomes
  `REFUND_REQUESTED` only once the durable refund request exists.
- **Missing account.** Still `ACCEPTED`; the request starts in
  `PENDING_ACCOUNT` and the refund-account email (template 7, 003.3 FR-040)
  counts as the FR-040a result. VNPay Refund is never called.
- **Conditions.** Condition 1 checks session capacity through
  `reserveGroupCapacity` only (no channel quota exists in V1). Condition 3
  holds only when the current loyalty revision equals the recorded revision,
  the group used no voucher and no points, every ticket price is unchanged and
  the recomputed total equals the collected amount. Condition 3 follows 003.1 FR-040: a voucher or points always fail it in V1 because 006 cannot re-hold them; an automatic workshop discount is recomputed and passes only if it equals the snapshot. *(Earlier wording "the voucher and points can be held again" superseded 2026-11-15, CHK032: 006 has no re-hold in V1, so a re-confirmed group never holds a voucher or points again.)*
- **Manager queue.** `GET /api/v1/late-payment-reviews` ordered by earliest
  session start. Booking publishes `LatePaymentReviewQueued(reviewReference,
  groupReference, earliestSessionAt, resultDueAt, correlationReference)` with
  references only. Internal Manager email (template 13) goes to every active Manager through
  `ManagerAlertRecipientSource`, backed by FE-001 `AccountFacade.listActiveManagerEmails()` (landed 2026-11-14,
  T094). ~~With no active Manager the alert goes to every active Admin, who only receives it (003.1 FR-040a,
  CHK017; Admin recipient source proposed, T130).~~ *(superseded 2026-11-15)* **Decided 2026-11-15
  (Hồ Hoàng Quốc, 003.1 FR-040a, CHK017, T130):** every 003.1 business alert (late-payment review, excess
  payment, failed expiry release, failed result email) goes to the active Managers only through
  `ManagerAlertRecipientSource`; an Admin never receives a business alert and there is no Admin recipient
  source. With no active Manager no email is sent, Booking writes an audit entry and a WARN log, and the item
  stays in the Manager queue for the first Manager who becomes active; nothing is decided automatically.
  No FE-001 change is needed. *(Earlier wording "to be backed … once FE-001 agrees; until
  then no alert email is sent" superseded 2026-11-15.)*
- **Customer emails.** Booking-group emails (templates 1/6a, 2, 3, 4, 6b, 12) are `booking_group_notification_intents`
  pulled through `WorkshopBookingNotificationFacade`. Settlement emails, including template 7 with the private
  `/refund-account#{token}` link, are `settlement_notification_handoffs` pulled through
  `WorkshopSettlementFacade.getSettlementNotificationDeliveryPayload`; Feature 007 now consumes both
  `BookingNotificationReady` and `SettlementNotificationReady` (decision 2026-11-13).

## Voucher and points restoration on ticket cancellation (decision 2026-11-15)

003.3 only triggers the restoration (003.3 FR-042, FR-043); the module that owns
vouchers and points performs it exactly once per `groupCode` (idempotent key)
and 003.3 never writes voucher or points data. A voucher is restored only while
it is still valid at that moment, for every kind of cancellation; an expired
voucher is not restored, the outcome carries reason `EXPIRED` and the buyer is
told. The money already paid is refunded by bank regardless (003.3 FR-038).

**Signature (2026-11-15, 003.3 T155; owner = the voucher and points module, 006).
Names approved 2026-11-15 by Hồ Hoàng Quốc, acting for Đặng Thị Khương (owner
of 006), who delegated this approval to him per his statement in the working
session; the "(proposed)" tags below are kept as history. The 006 owner builds
the operation; a rename is still possible through the owner of 006:**

```text
restoreVoucherForCancelledTicket(RestoreVoucherCommand) -> VoucherRestorationOutcome   (proposed)
RestoreVoucherCommand        { groupCode, ticketCode, partRef, cancellationKind }      (proposed; opaque references only)
VoucherRestorationOutcome    { outcome: RESTORED | EXPIRED | ALREADY_RESTORED }        (proposed)
```

- `partRef` (added 2026-11-26, BUG-E2E-0049; decided by Hồ Hoàng Quốc): the
  ticket's reference in the accepted benefit, `L{line}-T{ordinal}`, which 003.3
  takes from the 003.1 ticket context. The owner of the benefit finds the ticket
  part by `partRef`, never by `ticketCode`; `ticketCode` identifies the call in
  outcome keys and proof references.
- Idempotent per `groupCode`: the first call restores (`RESTORED`) or finds the
  voucher no longer valid (`EXPIRED`, never extended); every later call for the
  same `groupCode` returns `ALREADY_RESTORED` (or the stored `EXPIRED`) and
  restores nothing a second time.
- 003.3 stores the returned outcome with the cancellation and maps it to its
  `voucherRestoration {restored, reason}` response field; it never reads or
  writes voucher or points data, and the bank refund does not depend on the
  outcome.
- Trigger (R06, 2026-11-16): 003.3 calls it once for a group when the first Mland-cancelled ticket has its refund request opened, or when the buyer's answer, or the default, of a Mland-cancelled `FREE`/deferred ticket is recorded (no refund request needed).

## Member replacement inventory — 003.1 provider for FE-001 (decision 2026-11-25)

- **Consumer contract (owned by FE-001):** `WorkshopMemberReplacementReader.readForMemberReplacement(AccountId) → WorkshopReplacementInventory` (`status` CLEAR/BLOCKED/UNAVAILABLE, `dataOrigin`, `sourceVersion`, `bookings[]`, `blockingReferences[]`). 003.1 implements it; FE-001 does not change it.
- **Rules:** spec FR-059–FR-061 in `../003.1-workshop-booking/spec.md`. By Account ID only; no QR, Magic Link, contact proof or gateway data; UNAVAILABLE when Operations or Settlement cannot be read.
- **New-booking block (proposed, task T174):** FE-001 exposes a read on `AccountFacade` that says a MEMBER is retiring; 003.1 checks it under the group-creation lock (FR-062).
- **Status:** proposed until FE-001's owner confirms the signal (the `NO_SHOW` rule is decided 2026-11-25: never blocks); the local mock `MockMemberReplacementBookingReader` returns DEMO/UNAVAILABLE only and never authorizes retirement.

## Sessions of a cancelled ticket — 003.2 provider for 003.1 (added 2026-11-26, BUG-E2E-0045; decided by Hồ Hoàng Quốc)

- `getCancelledTicketSessions(ticketReference) -> List<SessionEntitlementProjection>`: the sessions the ticket held when
  it was cancelled (released ones plus any it still keeps because they had started). Empty when the ticket is unknown
  or not cancelled. Read only; no QR and no person data. 003.1 uses it for the buyer's view of a cancelled ticket.

## Explicitly not decided here

- Java package names beyond the existing feature `facade` package, concrete
  record field types, database schema, locking implementation, public HTTP
  routes, VNPay credentials/configuration, and notification-provider delivery
  mechanics remain owned by their respective implementation/design work.

## Feature-012 WorkshopRingDesignFacade (review baseline)

Feature-003 consumes configured workshop ring designs only through the following
logical boundary; it must not access Feature-012 repositories, entities or
tables directly.

```text
ConfiguredDesignBookingCommand
ConfiguredDesignBookingSnapshot
ConfiguredDesignBookingOutcome
```

The internal operation is `attachAndLockDesignForBooking(...)`. It runs
synchronously inside the Booking transaction, validates Guest session or Member
ownership, package/branch compatibility and `VALIDATED` state, then returns a
server-built immutable snapshot containing package, material, shape, size,
component, attachment, engraving, evaluation and `rules_version`. Feature-012
locks the design only on transaction commit; rollback leaves it retryable.

The command carries opaque references only and uses `operationReference` for
idempotency. A duplicate semantic command returns the prior outcome; changed
payload with the same operation reference is a conflict. The facade does not
create payment, capacity holds or tickets.

### Ticket-level design assignment

Booking may apply a configured design as a group default, but it must persist and
validate the final design independently for every ticket. A ticket may override
the group default before booking submission. Feature-003 calls the Feature-012
facade for each resulting ticket assignment; it never reads Feature-012
repositories or treats a group-level design as the ticket snapshot.

Only a `VALIDATED` design version is eligible for workshop attachment. This is
technical validation plus immutable snapshot creation, not Staff/Manager approval.
