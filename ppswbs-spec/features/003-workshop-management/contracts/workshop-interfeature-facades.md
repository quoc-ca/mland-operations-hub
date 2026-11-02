# 003 Workshop Management — Internal Facade Contract

**Date created**: 2026-10-25
**Date updated**: 2026-10-31
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Status**: Approved design contract for 003.1, 003.2, and 003.3. This is an
in-process Spring Modulith named-interface contract, not a public REST API or
OpenAPI document.

The Feature 003 owner approved OTA booking and add-on facade prerequisites on
2026-10-29. These operations extend the existing boundaries without transferring
ownership of capacity/tickets, direct-Mland payment, or OTA channel payment.
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
target: `DEPOSIT` for 003.1, or `FINAL_PAYMENT`/`REFUND` for 003.3. An unknown,
ambiguous, mismatched, or already-terminal reference is not broadcast or
guessed; it receives the safe provider acknowledgement outcome and no business
side effect. The routing registry/reference format, Java package names and
persistence mechanism remain implementation decisions, but its target must be
durable and immutable for the payment-attempt lifetime.

The technical module retains only redacted outcome fields after signature,
owner, amount and currency verification. `vnp_TransactionStatus=01` is
nonterminal; a verified two-digit status other than `01` is terminal. The
read-only `PaymentIngressOutcomeQueryPort` in `common.payment` lets Booking
check that state without depending on the `paymentintegration` module. For a
verified successful deposit that arrives after its hold is released, Booking
stores a deduplicated `BookingLatePaymentEvidence` record through its facade;
that record is reconciliation evidence for Manager `RECORD`/`ESCALATE` only
and never restores the hold or confirms the booking.

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
| Read a published Package for Booking selection | `PublishedPackageSelectionQuery(packageReference)` | `PublishedPackageSelectionSnapshot(packageReference, packageName, publicPerParticipantPrice, minParticipants, maxParticipants, requiredSessionCount, sameLocationRequired, minimumSameDaySlotGap)` | Read-only, published Package only; no entity/repository, Account, Booking/payment, ticket, attendance, Staff/audit or PII data. Booking may freeze these facts but never mutate master data. |
| Read sale eligibility | `SaleEligibilityQuery(sessionReference)` | `SaleEligibilitySnapshot(sessionReference, customerSelectable, saleState, locationReference, scheduledStart, scheduledEnd, remainingCapacity, workshopDate)` | Read-only, per UUID session reference; supplies authoritative selection/date facts for the Booking snapshot and Loyalty `WorkshopSessionFact`. Numeric Operations session IDs remain internal and are not returned; no booking/contact/Staff data. |
| Reserve all required-session capacity | `ReserveBookingCapacityCommand(bookingReference, packageReference, orderedRequiredSessionReferences, participantCount, operationReference, correlationReference)` | `CapacityHoldOutcome(ACCEPTED + holdReferences + expiresAt, or REJECTED reason)` | Operations atomically rechecks that the Package is published, participant bounds, required-session count, same-location and same-day slot-gap rules, sale eligibility and capacity; it holds `participantCount` seats in every requested session or none. |
| Accept OTA booking capacity and issue per-person tickets | `AcceptOtaBookingCommand(bookingReference, packageReference, sessionReference, participantCount, operationReference, correlationReference)` | `OtaBookingCapacityOutcome(ACCEPTED + exactly N opaque ticket references, prior equivalent outcome, or REJECTED reason)` | Basic Ring/`PER_PERSON` only. One Operations-owned local transaction validates the published Package/session and capacity, commits the required seats, and issues one independently scannable ticket per participant; rejection has no capacity or ticket effect. Same operation reference and semantic input returns the original ticket set; changed reuse conflicts. It exposes no entity, contact, payment, attendance, or raw QR data. |
| Commit holds after valid deposit evidence | `CommitBookingHoldsCommand(bookingReference, expectedHoldReferences, operationReference, correlationReference)` | `CapacityCommitOutcome(ACCEPTED, or REJECTED reason)` | Commits all active expected holds or none. |
| Release pending holds | `ReleaseBookingHoldsCommand(bookingReference, expectedHoldReferences, operationReference, correlationReference)` | `CapacityReleaseOutcome(ACCEPTED or prior equivalent outcome)` | Releases active holds once only; late/repeated release is idempotent. |
| Read current delivery-safe ticket set | `BookingTicketProjectionQuery(bookingReference)` | `DeliverySafeTicketSet(ticketReference, qrReference, ticketCode, sessionEntitlements(sessionReference, locationReference, scheduledStart, scheduledEnd))` | Each entitlement carries the opaque UUID session reference and current schedule for unambiguous customer projection after replacement/refund. `qrReference` is the Operations-issued opaque bearer scan value, returned only through this authorized projection; no attendance, Staff/audit or PII. |
| Read settlement readiness | `BookingSettlementReadinessQuery(bookingReference)` | `SettlementReadiness(ready)` | `ready` is true only if every required session is `ATTENDANCE_FINALIZED`; no underlying attendance detail. |
| Read customer-cancellation eligibility | `BookingCancellationEligibilityQuery(bookingReference)` | `BookingCancellationEligibilitySnapshot(eligible, reason, earliestRequiredSessionReference, earliestRequiredSessionStart, hasCheckedInTicket, hasNoShow)` | Operations derives the complete current required-session set from its active booking entitlements, including Manager replacements; it is authoritative for the earliest scheduled session reference/start, check-in and no-show facts. Settlement must not supply a potentially stale Booking-time session set and receives no ticket/participant payload. |
| Verify customer access to a booking | `CustomerBookingAccessQuery(bookingReference, memberAccountId, contactEmail, contactPhone)` | `CustomerBookingAccessDecision(authorized)` | Exactly one proof mode is supplied: a Member account ID or one Guest contact value. Booking compares it to its own stored owner/contact and returns only a boolean decision; it does not return contact PII or log the submitted proof. |
| Read a customer-selectable fixed workshop add-on | `WorkshopAddOnPriceQuery(packageReference, locationReference, addOnReference)` | `WorkshopAddOnPriceSnapshot(addOnReference, description, amountVnd, currencyCode)` or `null` | Operations returns a snapshot only when the package is published, location active, and add-on active for the exact package/location. Amount is whole VND; no numeric internal add-on row ID is exposed. |
| Replace the complete future required-session set | `ReplaceRequiredSessionsCommand(bookingReference, completeTargetSessionReferences, operationReference, correlationReference)` | `RequiredSessionReplacementOutcome(ACCEPTED, or REJECTED reason)` | Operations requires the immutable package-policy snapshot created with the accepted booking hold; it validates the complete target set against the booking-time required count, participant bounds, same-location and same-day slot-gap rules, plus current target eligibility/capacity, then transfers all entitlements or none and preserves ticket QR. A booking without a policy snapshot is rejected; Operations never infers or backfills terms from the mutable Package master. |
| Apply verified whole-booking refund consequence | `ApplyVerifiedRefundConsequenceCommand(bookingReference, verifiedRefundReference, operationReference, correlationReference)` | `RefundConsequenceOutcome(ACCEPTED, or REJECTED reason)` | Only after verified refund; revoke unused future ticket entitlement and release future capacity; never rewrite attendance history. |
| Complete a resolved cancellation handoff | `CompleteCancellationHandoffCommand(sessionReference, cancellationCaseReference, operationReference, correlationReference)` | `CancellationCompletionOutcome(ACCEPTED, or REJECTED reason)` | `CANCELLED` only when Settlement reports the handoff resolved/audited; no refund/reschedule logic lives in Operations. |

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

| Operation | Input boundary type | Result boundary type | Invariant / disclosure limit |
| --- | --- | --- | --- |
| Read confirmed booking financial projection | `ConfirmedBookingFinancialProjectionQuery(bookingReference)` | `ConfirmedBookingFinancialProjection(packageReference, frozenPackagePrice, frozenPostPromotionAmount, acceptedDepositAmount, frozenRemainingAmount, participantCount, requiredSessionReferences, bookingLocationReference)` | Available only for the confirmed direct-Mland booking/package context required by 003.3. Deposit is `ceil(frozenPostPromotionAmount / 2)` and remaining amount is the exact difference; no contact, raw provider evidence, or ticket payload. |
| Read verified deposit payments for refund calculation | `VerifiedBookingDepositPaymentsQuery(bookingReference)` | `VerifiedBookingDepositPaymentsProjection(payments: [VerifiedBookingPayment(reference, providerTransactionNumber, originalProviderCreateDate, amountVnd, currencyCode, verified)])` | Booking returns only its own successful verified deposit receipts, ordered by original provider create date then stable reference. No contact, browser URL, raw callback, signature, or final-payment facts cross this boundary. |
| Record final-balance resolution | `RecordFinalBalanceResolvedCommand(bookingReference, settlementReference, operationReference, correlationReference)` | `FinalBalanceResolutionOutcome(ACCEPTED or prior equivalent outcome, or REJECTED reason)` | Only Booking changes its financial state to `FULLY_PAID`, exactly once; it receives no raw final-payment detail. |
| Apply verified deposit evidence | `ApplyVerifiedDepositPaymentEvidenceCommand(bookingReference, verifiedEvidenceReference, operationReference, correlationReference)` | `DepositPaymentEvidenceOutcome(ACCEPTED or prior equivalent outcome, or REJECTED reason)` | Called only by `paymentintegration` after one successful verified dispatch. Booking checks its frozen reference/amount/currency, commits capacity atomically, and never receives raw VNPay payload or checksum. |
| Request delivery of existing ticket set | `RequestBookingTicketRedeliveryCommand(bookingReference, activeUnusedTicketReferences, operationReference, correlationReference)` | `TicketRedeliveryOutcome(ACCEPTED or REJECTED reason)` | Existing ticket/QR only; Booking delivers to the stored contact channel and does not change contact, tickets, payment, or Magic Link. This supports the already-approved Operations resend flow. |
| Accept or return an OTA-origin booking | `AcceptOtaBookingCommand(providerCode, externalBookingReference, packageReference, sessionReference, participantCount, operationReference, correlationReference)` | `OtaBookingAcceptanceOutcome(ACCEPTED + bookingReference + exactly N ticket references, prior equivalent outcome, or REJECTED reason)` | Provider plus external booking reference is the idempotent business identity. Booking coordinates the create-or-return outcome with Operations' atomic OTA capacity/ticket operation; it returns `ACCEPTED` only when both booking and ticket outcome are accepted. Rejection creates no confirmed Mland booking, committed capacity, or ticket. OTA guest payment/payout remains separate; this operation never records direct-Mland deposit/final payment. Contact is absent unless separately approved by the provider allowlist. |
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

## `WorkshopSettlementFacade` — owned by 003.3

| Operation | Input boundary type | Result boundary type | Invariant / disclosure limit |
| --- | --- | --- | --- |
| Open a customer-commitment cancellation handoff | `OpenCancellationHandoffCommand(sessionReference, affectedBookingReferences, cancellationReasonReference, operationReference, correlationReference)` | `CancellationHandoffOutcome(ACCEPTED + cancellationCaseReference, or REJECTED reason)` | Consumed asynchronously from the Operations-owned durable outbox after Operations has entered `CANCELLATION_REQUESTED`; delivery is idempotent by `operationReference`, and opening a case does not alter capacity, tickets, or payment. |
| Create direct-Mland add-on bill from accepted OTA quote | `CreateAcceptedOtaAddonBillCommand(bookingReference, acceptedQuoteReference, consentReference, operationReference, correlationReference)` | `AcceptedOtaAddonBillOutcome(ACCEPTED + billReference + amount/currency, prior equivalent outcome, or REJECTED reason)` | Settlement validates the quote and consent, derives price from its approved server-side quote, and creates or returns one direct-Mland bill idempotently. It does not alter OTA customer-payment/refund evidence, commission, or payout; it returns no card/payment secret or raw provider data. |
| Read cancellation-handoff resolution | `CancellationHandoffResolutionQuery(cancellationCaseReference)` | `CancellationHandoffResolution(resolved)` | `resolved` is true only after every affected booking case has a recorded remedy or audited Manager decision. It exposes no refund amount/payment/PII detail. |
| Apply verified settlement evidence | `ApplyVerifiedSettlementPaymentEvidenceCommand(bookingReference, verifiedEvidenceReference, merchantReference, paymentKind, amountVnd, currencyCode, providerTransactionNumber, originalProviderCreateDate, operationReference, correlationReference)` | `SettlementPaymentEvidenceOutcome(ACCEPTED or prior equivalent outcome, or REJECTED reason)` | Called only by `paymentintegration` after one successful verified dispatch. `paymentKind` is `FINAL_PAYMENT` or `REFUND`; `amountVnd` is a positive whole-VND amount and `currencyCode` is `VND`. For FINAL_PAYMENT, the transaction number and original payment URL create date are retained as redacted verified facts owned by Settlement. Settlement checks its frozen bill/refund reference, amount and currency, and may send only the existing safe final-balance conclusion to Booking. It receives no raw VNPay payload or checksum. |
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

## OTA snapshot publication and resync - 003.2 to 003.4

003.2 owns the internal `WorkshopSessionOtaSnapshot` boundary. The snapshot
contains only `sessionReference`, `locationReference`, `scheduledStart`,
`scheduledEnd`, `sellableAvailability`, `saleState`, `sourceVersion`,
`changeReference` and `effectiveAt`. 003.2 publishes the complete snapshot as
the persistent internal Spring Modulith event
`WorkshopSessionOtaSnapshotChanged` in the same local transaction as the
observable session change. `IN_PROGRESS`, `ATTENDANCE_FINALIZED`,
`CANCELLATION_REQUESTED` and `CANCELLED` have closed sale state. In particular,
scheduled `PUBLISHED` to `IN_PROGRESS` persists and publishes the complete
closed post-state in the same Operations transaction. Entering
`ATTENDANCE_FINALIZED` does not advance the version if sale state was already
closed and no other observable value changed. The event contains no
booking/payment, ticket, attendance, Staff, PII or OTA mapping data.

003.2 also owns the read-only authoritative resync query for one session. It
returns the complete current snapshot at the existing `sourceVersion`; resync
does not allocate a version or change business state. The source version is
monotonic per session and `(sessionReference, sourceVersion)` is unique.

003.4 is a consumer only. It applies a publication only when its version is
newer than the last applied version, ignores duplicate/older versions, and
requests the one-session resync when it detects a gap or cannot establish
order. 003.4 owns OTA mapping/provider delivery; a consumer/provider failure
may suspend only that mapping and must not mutate Operations capacity, sale
state or direct-Mland booking. V1 uses no broker and no external OTA call in
the Operations transaction.

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
