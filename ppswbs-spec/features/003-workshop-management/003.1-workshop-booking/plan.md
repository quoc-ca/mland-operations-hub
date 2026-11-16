# Implementation Plan: Customer Workshop Booking Core

**Date created**: 2026-10-07
**Date updated**: 2026-11-17
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature Branch**: `003.1-workshop-booking`
**Status**: Approved — updated 2026-11-14 to the implemented group/ticket model and re-approved the same day by Hồ Hoàng Quốc (owner Feature 003) in session.
*(Spec approval of 2026-11-15 by Hồ Hoàng Quốc covers this plan as updated.)* (Previous status 2026-11-05: "Draft design — not approved for implementation"; the
2026-11-05 single-booking design is kept for history at the end of this file.)
**Spec**: [spec.md](spec.md) (FR-030–FR-052a are the current direct-booking requirements)
**Tasks**: [tasks.md](tasks.md) Phases 8–16 (T089–T149; T107 is Phase 11, T108–T114 Phase 12, T115 Phase 13, T116–T122 Phase 14, T123 Phase 15, T124–T149 Phase 16; range fixed 2026-11-16, analyze) *(was "Phases 8–14 (T089–T120 …)", updated 2026-11-15)*
**Research baseline**: [research.md](research.md) and [REBASE-NOTES.md](REBASE-NOTES.md)

## Summary

003.1 is the direct-Mland customer booking core. Since the 2026-11-12 and
2026-11-13 decisions (spec FR-030–FR-051) a direct Guest or Member booking is
**only a booking group**: a cart of one or more package lines that, when
confirmed, creates one booking group and one ticket booking per person. One
person is a group with one ticket (FR-043, FR-044). The single
`WorkshopBooking` flow (one Package per booking, `ceil(post/2)` deposit,
rejection of every late payment) is removed for direct bookings (FR-046,
V132/V133) and survives only for OTA bookings received through 003.4.

The implemented flow is:

1. **Cart quote** (`POST /api/v1/workshop-cart-quotes`, Guest or Member):
   re-reads each line's published Package and sessions through
   `WorkshopOperationsFacade`, previews benefits with Feature 006, allocates the
   programme discount and points per ticket (FR-033, FR-034), computes each
   ticket's booking-time collection (FR-035) and the group payment mode
   (FR-036). Every cart, Guest included, gets the largest applicable automatic
   workshop discount unless a Member picks a voucher, and the response states
   which programme applied in `appliedDiscount` (`NONE`/`AUTO`/`VOUCHER`; T105).
   A quote holds no capacity or benefit.
2. **Group hold** (`POST /api/v1/workshop-booking-groups`): accepts the quote
   once, holds every line in one transaction all or none (refusal lists
   `failingLines`, FR-031), reserves the group's one Loyalty transaction, and
   snapshots each ticket's price. `holdExpiresAt` is 12 minutes after the hold
   (`workshop.booking.group-hold-minutes`, default 12) and `paymentDeadlineAt`
   is 2 minutes earlier, i.e. a 10-minute payment window (FR-032).
3. **Group payment**: a `VNPAY` group pays once for the whole group through
   `POST …/{groupCode}/payment-attempts` (link expiry = `paymentDeadlineAt`,
   QueryDR at minutes 10 and 11, never extending the hold); an
   `INTERNAL_FREE` (0 VND) or `INTERNAL_DEFERRED` (group collection below the
   5,000 VND VNPay floor) group is confirmed inside group creation (FR-036).
4. **Per-ticket confirmation** (FR-038): verified evidence commits every hold
   through Operations, sets each ticket's financial status from its snapshot,
   settles the Loyalty benefits once, opens one group Magic Link and publishes
   one `MlandTicketConfirmedEvent` per ticket so that 003.3 opens one OPEN folio
   per ticket. Expiry releases the whole group (FR-039).
5. **Late-payment review** (FR-040/FR-040a): signed evidence for an expired
   group is evaluated against the five conditions and either re-confirmed
   automatically (`LATE_PAYMENT_RECONFIRMED`) or queued for a Manager
   (CONFIRM only when conditions 4/5 alone failed; REFUND hands a 100% bank
   refund to 003.3).
6. **Recipients and QR** (FR-041): the buyer (or Staff after verifying the
   buyer) may send a ticket to a recipient; a change rotates that ticket's QR
   through 003.2.
7. **Recovery routes** (FR-047–FR-049): owning-Member group read and list,
   Guest group lookup, group Magic-Link exchange, pending-hold release,
   confirmation resend and Manager Magic-Link incidents.
8. **Public discounted price** (FR-052/FR-052a): backend part done in T105;
   the five public frontend surfaces are T106 (done 2026-11-14).

003.1 still stops at ticket issuance, delivery, recovery and the per-ticket
facts other features read; check-in and attendance (003.2), folios,
cancellation, refund and changes (003.3) and OTA (003.4) are outside it.

## Technical Context

| Area | Design baseline |
| --- | --- |
| Runtime | Java 21, Spring Boot REST, Spring Modulith, JPA/Spring Data, MySQL and append-only Flyway migrations (group model V113–V133). |
| Client | Separate React + Vite client using versioned REST APIs; no backend-rendered booking UI. |
| Identity and authorization | Firebase bearer tokens prove identity only. Account Management supplies canonical `MEMBER`, `STAFF`, `MANAGER`, or `ADMIN` role, active status and `activeLocationIds`; Booking applies its own support-action rules: for a group-level Staff operation the Staff member's active location scope MUST include the group's branch AND the Staff member MUST be actively assigned to a session of the group (003.2 "Staff session-assignment access", 003.3 FR-032; decided 2026-11-15, CHK025); a Manager is global. Guest access uses one stored contact proof of the group, a protected group Magic Link, or (for a recipient change) the single-use `buyerVerificationReference` of an OTP to the original contact email. |
| State | Persisted group/line/ticket/snapshot/hold/payment/review state; one group `holdExpiresAt` (12 min) and `paymentDeadlineAt` (10 min); booking-critical local work uses one MySQL transaction; the unquoted cart lives only in the browser (FR-045); no new global session/token store. Rate-limit counters (Guest lookup, hold release, resend, T110) are per-instance in-memory sliding windows (decided 2026-11-15, spec Session 2026-11-15): lifecycle = process lifetime, reset on restart, effective cap up to N × configured with N instances, no cross-instance consistency; revisit (shared MySQL counter) before running more than one backend instance. |
| Payment | Shared `paymentintegration` ingress verifies VNPay once and routes redacted evidence by immutable merchant reference; a group attempt's evidence goes to `GroupPaymentEvidenceService`, anything else is refused by the single-booking path. |
| Benefits | Feature 006 cart contract (006 T098, D-010) plus automatic workshop discount (006 FR-044, D-013, D-014); 003.1 allocates per ticket with `TicketBenefitAllocator`. |
| Verification | Unit/integration (MySQL for schema and late payment), contract (`WorkshopBookingContractIT`), concurrency, callback/idempotency, migration and Spring Modulith boundary verification. |
| Unknowns | VNPay integration values/environments (T005/T046) and production rollout approval. *(Manager alert recipient source and the automatic-discount behaviour of late-payment condition 3 were unknowns here; both decided 2026-11-14, see design risks 1-2 and T094.)* |

### Approved React route boundary

The React + Vite client owns `/workshops` (browse), `/workshops/:packageId`
(detail/session selection), `/workshops/:packageId/book` ("Đặt ngay" one-line
quote and confirmation), `/workshop-booking/cart` (multi-line cart, FR-045),
`/workshop-booking/lookup` (Guest/Member recovery), and
`/workshop-booking/access#token` (group Magic-Link exchange). Browser return
renders at `/workshop-booking/:groupCode/payment-return`; it reads only current
server-confirmed group state and never promotes redirect parameters to payment
evidence. These routes are client routes, not Java REST endpoints or SSR pages.
*(Updated 2026-11-14 from the T102/T103 implementation; the 2026-11-05 list used
`:bookingCode` and had no cart route.)*

## Constitution Check

| Gate | Result | Plan response |
| --- | --- | --- |
| Module and data ownership | PASS | `workshopbooking` owns groups, lines, ticket bookings, price snapshots, holds references, group payment attempts, late-payment reviews, recipients, buyer verifications, Magic Links and notification intents; it reaches 003.2, 003.3, 006 and FE-001 only through their facades/events. |
| Separate frontend/REST | PASS | Contract is JSON `/api/v1` ([workshop-booking.openapi.yaml](contracts/workshop-booking.openapi.yaml), 15 paths; 10 of them Guest-capable with `security: [{}, …]`); the group Magic Link resolves to client-readable data, never server-rendered HTML. |
| Identity and sensitive data | PASS | No participant identity, OTP (only salted hash), raw gateway payload, raw Magic-Link secret or unmasked recipient email in audit; contact proof never travels in a URL. |
| Data evolution | PASS (dev/test) | Only dev/test data exists (FR-043). V113–V131 add the group model; V132 deletes direct single bookings and V133 drops tables only they used. No applied migration was edited. Any data-bearing environment still needs its own preservation plan. |
| Evidence and concurrency | PASS | All-or-none group hold, one group payment, IPN/QueryDR idempotency, late-payment review with one winner in a capacity race, and confirmation replay are covered by `WorkshopCartBookingTest`, `GroupConfirmationTest`, `GroupPaymentQueryDrRecoveryTest`, `LatePaymentReviewIT`; the full MySQL group flow is T097 (done 2026-11-14). |
| Explicit decisions | PASS | Decisions of 2026-11-12/13/14 are recorded in spec.md. Decided 2026-11-14: Manager alert source waits for FE-001 (no interim list) *(then closed 2026-11-14: `listActiveManagerEmails()` landed and T094 is done)*; late-payment condition 3 recomputes an automatic discount. Decided 2026-11-14: group-creation `Idempotency-Key` aligns to 16–128 (T107, done). *(Was CONDITIONAL until these decisions were recorded and implemented.)* |

## Module boundaries and flows

`workshopbooking` synchronously reads each line's published-Package snapshot
and sale eligibility from `WorkshopOperationsFacade`, asks it to reserve,
commit or release a whole group's capacity (`ReserveGroupCapacityCommand`,
all or none), and to rotate a ticket's QR on a recipient change. It reads
Feature 006 benefits through `LoyaltyFacade` (cart preview, one
`reserveAcceptedTransaction` per group, settle/release outcomes). It reads
FE-001 account facts for authorization. It publishes
`MlandTicketConfirmedEvent` (per ticket, consumed by 003.3
`TicketConfirmedFolioConsumer`), `LatePaymentReviewQueued` and reference-only
notification events that Feature 007 resolves through
`WorkshopBookingNotificationFacade`. For 003.3 it exposes
`findTicketBookingByOperationsReference` (T099) and, decided 2026-11-14,
`getTicketFolioContext` and `consumeBuyerProof` (T107, see
[../contracts/workshop-interfeature-facades.md](../contracts/workshop-interfeature-facades.md)) and, decided 2026-11-16 for 003.3 FR-056, the read-only
`listBookingTimePayments(locationId, workingDate)` (T150; stored group payment attempts and evidence only, no VNPay call; statuses `CONFIRMED`, `LATE_PENDING_REVIEW`, `UNVERIFIED`; one row per payment with `locationIds[]`, filter by any session location, decided 2026-11-16 option B).

**Cart quote flow.** Validate lines; re-read published Package price and
sessions; preview with 006 (`VOUCHER` stays a voucher; otherwise `AUTO` with the
requested points; a Guest is previewed as `TrustedSourceContext.GUEST`, and a
Guest voucher/points request is refused with `CART_BENEFITS_NEED_A_MEMBER`
before 006 is called); allocate the programme discount and points per ticket by
largest remainder; compute `collectionKind` per ticket and `paymentMode` for the
group; persist the quote with its lines, selection, eligible ticket indexes,
point value, terms digest and policy revision (V114/V118). The response reports
`appliedDiscount`; `voucherDiscountVnd` carries whichever programme discount
applied (T105).

**Group creation flow.** Under a row lock accept the quote once
(`QUOTE_EXPIRED`, `QUOTE_ALREADY_ACCEPTED`, `QUOTE_CHANGED`), hold every line
through Operations all or none, make one Loyalty reservation for the group
(refuse `QUOTE_CHANGED` if the accepted discount or points differ), create the
group, lines, line sessions with hold references, one ticket booking and one
immutable price snapshot per person, all in one MySQL transaction, protected by
`Idempotency-Key`. An internal group (`INTERNAL_FREE`/`INTERNAL_DEFERRED`) is
confirmed in the same call.

**Group payment flow.** One attempt for the group collection (≥ 5,000 VND),
expiring at `paymentDeadlineAt`; a retry is refused after `paymentDeadlineAt` (decided 2026-11-15, FR-012a), returns the single live `PENDING` attempt and never
extends the hold. Verified evidence is routed by the group attempt's merchant
reference and is idempotent. `GroupPaymentQueryDrRecoveryScheduler` queries at
minutes 10 and 11 only when no verified terminal IPN exists and accepts only a
signed, successful, exactly matching answer.

**Confirmation flow.** Commit every line's holds (Operations issues the
tickets), store each ticket's Operations reference and QR, set ticket financial
status from its snapshot (`FULLY_PAID`/`DEPOSIT_PAID`/`UNPAID`), settle the
group's Loyalty transaction once (`WORKSHOP_DEPOSIT_ACCEPTED`), open one group
Magic Link expiring with the last session, queue the buyer confirmation email,
and publish one `MlandTicketConfirmedEvent` per ticket after commit. Replays
return the stored outcome. `GroupHoldExpiryScheduler` releases an unpaid group
once and releases its benefits (`UNPAID_EXPIRY_CONFIRMED`). Expiry releases the group's seats, voucher and points immediately even when an unverified VNPay attempt exists (Session 2026-11-15, FR-039).

**Late-payment flow.** One review per verified evidence *(wording "per payment
attempt" superseded 2026-11-15)* (IPN and QueryDR share it). Conditions 1 (session capacity only; no channel quota in V1) to 5 are
evaluated; automatic re-confirmation holds fresh capacity all or none and is a
new acceptance. Otherwise the review enters the Manager queue
(`GET /late-payment-reviews`, ordered by earliest session), the payer gets the
"payment arrived late" email with the FR-040a deadline, and a Manager decides
CONFIRM (conditions 4/5 only, 1–3 rechecked) or REFUND through
`LatePaymentRefundPort` (003.3 opens a `LATE_PAYMENT_NOT_RECONFIRMED` bank
refund in `PENDING_ACCOUNT`; never VNPay Refund). Condition 3 fails for any
group with a voucher or points (decision 2026-11-13); an automatic workshop
discount is recomputed at review time and passes *(decided 2026-11-14, see
design risk 2 and spec FR-040; the 2026-11-13 wording "programme discount"
also failed automatic discounts and is superseded)*.
The Manager alert email is wired to `ManagerAlertRecipientSource`, whose
FE-001 source (`AccountManagerAlertRecipientSource`) is connected and T094 is
done *(closed 2026-11-14, see design risk 1)*.
**Decisions 2026-11-15 (checklist payment-error-access.md):** condition 4 counts 60 minutes from the time Mland receives the verified evidence (CHK006); evidence arriving after the earliest session started fails condition 2 and the Manager result limit is 24 hours after the evidence, refund only (CHK007); a failed result email is not "reached" and is resent with a bounded retry ~~**limit still to be decided in this plan (open, no number set)**~~ limit *(set 2026-11-15, fourth batch below)*, then the Manager contacts the customer manually (CHK016); ~~with no active Manager the alert goes to every active Admin, who only receive it and cannot decide (CHK017, recipient source depends on FE-001);~~ *(superseded 2026-11-15: business alerts go to active Managers only, fourth batch below)* a second verified payment with a different transaction on a `CONFIRMED` group is recorded as excess and refunded 100% through 003.3 with a Manager alert, the port being ~~proposed~~ decided 2026-11-15 (CHK001).

**Decisions 2026-11-15 (checklist payment-error-access.md, second batch):** provider failure while creating the payment link answers ~~502/503~~ HTTP 503 *(decided 2026-11-15, CHK012)* `PAYMENT_LINK_UNAVAILABLE` with no attempt and an unchanged hold (CHK012); `DEPENDENCY_UNAVAILABLE` refuses a whole group at creation and, at expiry, keeps the group waiting for an idempotent scheduler retry (CHK013); a release that fails at expiry is retried with backoff and alerts a Manager after N failures, where ~~**N, the backoff and the alert recipient are OPEN and must be decided in this plan before coding (no number set;~~ *(superseded 2026-11-15: set in the fourth batch below)* touches 003.2 idempotent release and 006 (CHK014); a Guest payment attempt needs `groupCode` plus exactly one matching contact value, a Member only an owned `PENDING_PAYMENT` group with an active payment window (CHK023); `IDEMPOTENCY_CONFLICT` replaces `IDEMPOTENCY_KEY_REUSED` (CHK018); wrong proof or non-owner answers 404, 403 only for a known Staff/Manager without authority, and every contract route is audited for this (CHK026); SC-004 release within 5 minutes after `holdExpiresAt`, measured from audit (CHK037).

**Decisions 2026-11-15 (checklist payment-error-access.md, third batch, CHK013):** *quote time* — `POST /workshop-cart-quotes` answers 503 `DEPENDENCY_UNAVAILABLE` and creates no quote when 006 or 003.2 is unavailable (spec FR-031; 503 decided 2026-11-15 as for group creation; T146). *Confirmation time* — today `PaymentIngressService` saves the `payment_ingress_receipts` row and dispatches to `GroupPaymentEvidenceService.apply` in one transaction, and Booking records an in-time payment only as `group_payment_attempts.attempt_status = SUCCESS`; a runtime failure in `GroupConfirmationService.confirmPaidGroup` (003.2 commit or 006 settlement) therefore rolls back the receipt and the attempt status. Decided (spec FR-038): on such a dependency failure the receipt, the attempt `SUCCESS` and a durable confirmation-retry record of the verified evidence commit together (data-model "Confirmation retry after a dependency failure", ~~proposed~~ `group_confirmation_retries` decided 2026-11-15), the group stays `PENDING_PAYMENT`, and a scheduler retries `confirmPaidGroup` idempotently while the hold is active. Design constraint (proposed, verify in T145): the dependency failure must be caught or classified so that it does not mark the evidence transaction rollback-only (the same concern the late-payment branch of `apply` already avoids by deciding lateness before confirming); a confirmation that fails must leave no partial commit of holds/tickets that a retry would duplicate. `GroupHoldExpiryScheduler` MUST NOT release a group with a waiting retry record before `holdExpiresAt` (it never runs before then anyway; pending-hold release is refused once an attempt exists). At `holdExpiresAt` the scheduler stops the retry, opens one late-payment review for the recorded evidence (arrival = time Mland received it, FR-040 condition 4 passes) and then releases the group under FR-039 (with the CHK014 release retry if a dependency is still down). ~~**OPEN decision (owner Hồ Hoàng Quốc), must be settled in this plan before T145:** the confirmation retry interval and the maximum number of retries inside the hold (no number set here); also whether a Manager is alerted when a retry fails (not decided).~~ *(superseded 2026-11-15: closed in the fourth batch below)*

**Decisions 2026-11-15 (checklist payment-error-access.md, fourth batch, final; owner Hồ Hoàng Quốc; spec "Checklist follow-up, final decisions"):**

- *Alert recipients (all business alerts, CHK017, T130).* Every business alert of 003.1 (late-payment review, excess payment, failed expiry release, failed result email) goes to the active Managers only through `ManagerAlertRecipientSource` (FE-001 `AccountFacade.listActiveManagerEmails()`). An Admin never receives a business alert; there is no Admin recipient source. With no active Manager: no email, an audit entry plus a WARN log, and ~~the item stays in the Manager work queue~~ *(decided 2026-11-16, F18: only a late-payment review stays in its queue, `GET /api/v1/late-payment-reviews?status=PENDING_MANAGER`; excess payment, failed expiry release and failed confirmation notification keep audit + WARN only)* for the first Manager who becomes active; nothing is decided automatically. No FE-001 change.
- *Excess payment (CHK001, T124).* Port `ExcessPaymentRefundPort.openExcessPaymentRefund(OpenExcessPaymentRefundCommand(groupCode, evidenceReference, amountVnd, operationReference, correlationReference)) → LatePaymentRefundOutcome` and table `group_excess_payments` decided; exact column types are fixed by the migration. Alert per the rule above.
- *Late retry (CHK002, T125).* A payment retry after `paymentDeadlineAt` answers `409 PAYMENT_WINDOW_CLOSED`.
- *Confirmation retry (CHK013, T145, design risk 6).* The scheduler retries `confirmPaidGroup` every 1 minute while the hold is active, about 10 attempts inside the 12-minute hold; no separate alert when a retry fails (the late-payment path already alerts the Managers). At `holdExpiresAt` the late-payment review is created first, then the group is released under FR-039. Store: `group_confirmation_retries`.
- *Failed expiry release (CHK014, T133).* Retry 3 times, 1 minute apart (inside SC-004's 5 minutes); still failing → alert the Managers per the rule above, then retry every 5 minutes until it succeeds; every retry idempotent.
- *Late-payment result email (CHK016, T129).* Resend up to 3 times, 5 minutes, 30 minutes and 2 hours after the failure; still failing → alert the Managers per the rule above to contact the buyer by phone; a failed email never counts as "reached".
- *Contact proof shape (CHK022, T137).* Both `contactEmail` and `contactPhone`, or neither, → `400 VALIDATION_ERROR` through Bean Validation, with the same safe answer.
- ~~*Not in this batch.* The HTTP 503 status of `DEPENDENCY_UNAVAILABLE` (quote and group creation, FR-031, T146) stays proposed.~~ *(Decided 2026-11-15 by Hồ Hoàng Quốc: HTTP 503 for `DEPENDENCY_UNAVAILABLE` at quote and group creation.)*

**Decisions 2026-11-15 (checklist payment-error-access.md, fifth batch, owner Hồ Hoàng Quốc; checklist fix log):**

- *Payment-link failure status (CHK012, T131).* `PAYMENT_LINK_UNAVAILABLE` is HTTP 503 only (the earlier "502/503" is superseded); no attempt, hold unchanged.
- *Verified evidence with a wrong amount, reference or currency (CHK003, FR-010f, T144).* Never confirms a group. It is recorded (decided 2026-11-16, F14: the `payment_ingress_receipts` row, one audit entry with the reason and one WARN log; no new table) with the reason, the group stays `PENDING_PAYMENT`, and at `holdExpiresAt` it is handed to the late-payment path (condition 3 fails, Managers alerted, 100% bank refund only).
- *Confirmation notification failure (CHK015, FR-015b, T147).* Resend up to 3 times, 5 minutes, 30 minutes and 2 hours after the failure (the CHK016 schedule); still failing → alert the active Managers only (alert rule above) to phone the buyer. Never reverses payment or confirmation.
- *Buyer-verification routes (CHK024, T148).* Guest, Staff within the Staff scope, and any active Manager (global) on the buyer's behalf; Member needs no OTP; Admin has no authority.
- *Staff scope of a multi-location group (CHK025, T135, T148).* The location scope is checked against the location of the session the Staff member is assigned to.

**Recipient flow.** `PUT /workshop-tickets/{ticketCode}/recipient` with buyer
proof (Member bearer + ownership; Guest OTP → single-use
`buyerVerificationReference`; Staff with location scope, session assignment and reason; a Manager globally with reason *(updated 2026-11-15, CHK028: "Staff/Manager with location scope" superseded, Manager is global per FR-048)*).
A first send keeps the QR; a change rotates it through 003.2 and counts once
online (max 3); masked audit (V127).

**Recovery flow.** Group read for the owning Member, Member list (30 newest,
expired only within 24 h), Guest lookup with one contact proof, group Magic-Link
exchange, pending-hold release (refused once a payment attempt exists), resend
reusing the active link, and Manager revoke/rotate (FR-047–FR-049).

**Removal of the single flow.** T100 removed the single-booking controllers,
services, entities and their tests; V132 deleted direct `workshop_bookings` rows
and V133 dropped tables only they used. The facade operations the 003.3
booking-wide routes called were removed by T100 on 2026-11-14 (spec FR-046 note); `verifyCustomerBookingAccess`, `recordBookingServiceOutcome` and `recordBookingRefundStatus` are kept on purpose.

Events occur only after Booking's local state transition; an event never
determines capacity or whether a group confirms.

## Contract and state design

The public contract is [workshop-booking.openapi.yaml](contracts/workshop-booking.openapi.yaml)
(15 paths after T104/T105 and T095/T094). `workshop-booking-cart.openapi.yaml` is an empty
pointer. The shared VNPay ingress is documented separately.

- Group status: `PENDING_PAYMENT`, `CONFIRMED`, `EXPIRED`, `CANCELLED`.
- Ticket status: `PENDING_PAYMENT`, `CONFIRMED`, `EXPIRED`, `CANCELLED`,
  `COMPLETED`, `NO_SHOW`; ticket financial status `UNPAID`, `DEPOSIT_PAID`,
  `FULLY_PAID`, `REFUND_PENDING`, `REFUNDED` (V113).
- Collection kind per ticket: `FREE`, `FULL`, `DEPOSIT`,
  `DEFERRED_BELOW_VNPAY_MINIMUM`; group payment mode `VNPAY`, `INTERNAL_FREE`,
  `INTERNAL_DEFERRED`.
- Late-payment review outcome: `AUTO_RECONFIRMED`, `PENDING_MANAGER`,
  `MANAGER_CONFIRMED`, `REFUND_REQUESTED`; decision `CONFIRM`/`REFUND` (V128).
- `Idempotency-Key` protects every command (16–128 characters; group creation
  was aligned to 16–128 by the decision of 2026-11-14 and the controller now
  refuses anything outside it, T107).

The group Magic Link (one per group) follows the single-booking rules carried
over by FR-048: hash persisted, raw URL only in the protected notification
snapshot for Feature 007, scrubbed on revoke/rotate and by a one-minute expiry
sweep, fragment-only in the browser.

## Data, migration, and operational constraints

The model is in [data-model.md](data-model.md) ("Booking group and ticket
bookings", implemented tables V113–V133). Monetary snapshots are integer VND.
No applied migration may be edited. Only dev/test data exists (FR-043);
environments were reseeded after V132/V133. Deployment topology, origins,
provider credentials, monitoring ownership and SLOs remain outside this plan's
decision authority.

## Package availability per location (Phase 18, plan 2026-11-17)

**Spec:** "Package availability per location — effect on 003.1", FR-053–FR-057,
SC-009, questions Q1–Q7. **Source:** 003.2 plan Phase 18 "Cross-feature impact"
and `design/architecture.md` Open decisions 1–2. **Owner decisions recorded 2026-11-17 (Q1–Q7)**,
not yet formally approved. No new table, no
migration, no new module dependency: `workshopbooking` already reads
`WorkshopOperationsFacade`; `ApplicationModules.verify()` must stay green.

**Design (proposed):**

1. **Current location of a ticket (FR-053).** In `getTicketFolioContext`, after
   the ticket and its line are found, ask Operations for the line's ticket
   projection (`WorkshopOperationsAdapter.getBookingTicketProjection(lineReference)`,
   already used by `BookingGroupProjectionService.currentTickets`), pick the
   `DeliverySafeTicket` whose `ticketReference` equals
   `ticket.operationsTicketReference`, order its `sessionEntitlements` by start
   and fill `sessionReferences` and `locationReference` (the first session's
   location; all are equal under `sameLocationRequired`). The context record
   `TicketFolioContext` keeps its shape, so 003.3 callers are unchanged.
   `firstSessionStart` is read from the same projection for the same reason
   (the first session also changes on a reschedule; to confirm in T155 whether
   003.3 relies on the old value). No Operations facade method is added.
2. **Cost and failure.** One read per call. The method is used on settlement and
   audit paths (about nine call sites in `workshopsettlement` and three in
   `WorkshopBookingFacadeImpl`), not per page view, so no cache is proposed. A
   projection failure follows Q3.
3. **Readers of the location copy** (verified in code 2026-11-17; the copy is
   `BookingGroupLineSession.locationReference`, written once by
   `BookingGroupCreationService` and read as follows):

   | Reader | Use | Effect of a cross-location move | Proposed treatment |
   | --- | --- | --- | --- |
   | `WorkshopBookingFacadeImpl.getTicketFolioContext` | 003.3 Staff authority, folio, `branch_id`; also its own audit | Wrong location after a move | Change (FR-053) |
   | `WorkshopBookingFacadeImpl` ticket-conclusion audits (final balance, refund status) | `BookingAuditEvidence.locationId` via the context | Follows the new location automatically; past rows unchanged | No separate change; tested in T156 |
   | `GroupStaffScopeService.staffCovers` | Staff scope of group-level operations | Scope read from the old location while assignment follows the new one | Open: Q4 |
   | `TicketRecipientService.locationOf`, `BookingGroupRecoveryService.locationOf` | Audit and denied-attempt `locationId` of group-level commands | Old location in audit text | Open with Q4 (audit only, no access effect) |
   | `GroupPaymentAttemptRepository` query, `BookingTimePaymentQueryService` (T150) | Booking-time payment list by location | Payments would move with the ticket | Keep the copy: Q5 |
   | `LatePaymentReviewService` (workshop date, location, audit, re-hold) | Group not yet confirmed, or its first confirmation | No ticket has moved | No change |
   | `BookingGroupProjectionService.sessions` | Group view | Already prefers the current Operations entitlement and falls back to the copy | No change |
   | `BookingSessionCommitment.locationId` | Written only by `acceptOtaBooking`; no reader found in `workshopbooking` | OTA tickets are not changeable (003.2 FR-036) | No change |
   | `WorkshopCartPricingService`, `WorkshopCartBenefitService`, `WorkshopCartQuoteService` | Cart, benefits at quote time | None (before booking) | No change |
4. **Cart refusal (FR-055).** The Operations rejection reaches Booking
   unchanged through `WorkshopOperationsAdapter.reserveGroupCapacity`; the
   mapping is not in the adapter but in
   `BookingGroupCreationService.requireHeld`, which sends every reason other
   than capacity, not-published and not-eligible to `PACKAGE_SELECTION_INVALID`.
   Add the new reason there (and in `RejectionReason` use once 003.2 lands it),
   answered by `CartHoldRejectedException` → 409 `CartHoldRejectionResponse`
   with `failingLines`, unchanged shape. `LatePaymentReviewService` already
   treats any non-`ACCEPTED` reserve as a failed re-hold; only a test is added.
   Quote and the OpenAPI example list the new code
   (`workshop-booking.openapi.yaml` describes the 409 of
   `POST /workshop-booking-groups` only as "capacity failure"; the new code is added there).
5. **Frontend (FR-056, FR-057), `ppswbs_frontend/src/workshops`.**
   `WorkshopDetailPage` and `WorkshopBookingPage` already read branches from
   `listWorkshopLocations` (`api/workshops.js` →
   `GET /workshop-packages/{packageId}/locations`); the work is: a 409 branch in
   the sessions load that clears the branch and reloads the list, a message for
   the new code in `BOOKING_ERROR_MESSAGE` (booking page) and
   `CART_ERROR_MESSAGE` (cart page), `locationName` on session rows, and an
   empty-list state. `cartStorage.js` keeps `locationId` only; the name is
   re-read. No new route and no new client library.
6. **Not in scope here:** the Manager matrix screen and the location name in
   003.3 ticket-change options (003.2 and 003.3); the OTA listing (003.4).

**Verification:** MySQL integration for FR-053 and FR-055 (a ticket moved by
`replaceTicketSessions` reads the new location; the old copy is untouched; a
cart over an `INACTIVE` pair holds nothing); component tests for FR-056 and
FR-057; `ApplicationModules.verify()` and `WorkshopBookingContractIT`.

## Verification plan

Evidence already recorded per task in [tasks.md](tasks.md) (T089–T120). T097,
T106 and T107 are all done (2026-11-14); what remains is the real-provider
evidence (T005/T046), the open verification tasks T116–T123 and Phase 16 T124–T149 (T117 proves SC-005, T118 proves SC-007) and the per-change checks below. The recorded
verification:

- **T097 (done 2026-11-14):** MySQL integration of the full group flow — paid group, mixed
  free/paid group, 0 VND group, deferred group, capacity refusal of one line,
  expiry, late payment auto/manual paths, recipient change and QR revocation,
  plus the T105 automatic-discount cases (Guest cart with an automatic
  discount through confirmation, Member voucher replacing it, cart below the
  discount minimum, per-ticket remainder allocation, and the late-payment
  condition 3 effect).
- **T106 (done 2026-11-14):** component tests of the five FR-052 surfaces.
- **T107 (done 2026-11-14):** narrow tests of `getTicketFolioContext`/`consumeBuyerProof`
  and `ApplicationModules.verify()`.
- **T005/T046 (open):** real VNPay sandbox evidence; until then fake-provider
  and signed-fixture tests are the interim evidence (constitution
  manual-verification rule).
- Every change keeps `WorkshopBookingContractIT` (contract paths = controller
  mappings, no FR-046 path) and `ApplicationModules.verify()` green.
- The approved frontend E2E suite must still prove that a visitor reaches
  booking entry from the workshop listing in no more than three primary
  interactions (SC-005).

## Design risks and handoff conditions

1. **Manager alert recipients (T094) — closed 2026-11-14:** FE-001 provides
   `AccountFacade.listActiveManagerEmails()` and `AccountManagerAlertRecipientSource`
   reads it, so the Manager alert email is sent as well as queued in-app.
2. **Late-payment condition 3 and automatic discounts — decided 2026-11-14:**
   condition 3 recomputes the automatic workshop discount at review time and
   passes when it still applies; a group that used a voucher or points still
   fails it (spec FR-040, `LatePaymentReviewService`).
3. **Legacy facade operations (T100 remainder) — closed 2026-11-14:** removal was gated on 003.3 T106,
   which itself follows 003.3 T099–T101 and T105; T107 provides the facade
   operations 003.3 T099/T106 need.
4. **Real VNPay** needs the provider/environment inputs of
   [vnpay-configuration-preflight.md](../contracts/vnpay-configuration-preflight.md).
5. **Owner re-approval**: given by Hồ Hoàng Quốc (owner Feature 003) in session
   on 2026-11-14 for this updated plan. The approval covers the design recorded
   here; it does not by itself approve code, schema, deployment or contract
   changes, each of which still needs its own task and evidence.
6. **Confirmation retry after a dependency failure (CHK013) — closed
   2026-11-15, owner Hồ Hoàng Quốc:** ~~OPEN 2026-11-15: the rule is decided
   (spec FR-038/FR-039/FR-040), but the retry interval and the maximum retry
   count inside the 12-minute hold are not set, and the retry record
   (data-model, proposed) is not approved. T145 does not start until both are
   recorded here.~~ *(superseded 2026-11-15)* Decided: retry every 1 minute
   while the hold is active (about 10 attempts), no separate alert; at
   `holdExpiresAt` the late-payment review is created first, then the group is
   released (FR-039); store `group_confirmation_retries` (data-model). T145 is
   no longer blocked by a decision.
7. **Business-alert recipients (CHK017) — decided 2026-11-15, owner Hồ Hoàng
   Quốc:** active Managers only, via FE-001 `listActiveManagerEmails()`; an
   Admin never receives a business alert; with no active Manager an audit
   entry plus a WARN log (the item stays in the Manager queue only for a late-payment review, F18 2026-11-16). CHK014
   (T133: 3 × 1 minute, then Manager alert and every 5 minutes) and CHK016
   (T129: resend at 5 minutes, 30 minutes, 2 hours, then Manager alert) use
   this rule; see the fourth batch of decisions above.

---

## Superseded design of 2026-11-05 (history)

*(superseded by FR-030–FR-052a, 2026-11-12/13/14; kept for history. Read every
statement below through the sections above.)*

**Former summary.** 003.1 was the direct-Mland customer booking core. One
booking purchased exactly one Package, with no cart of unrelated Packages.
Wax Casting selected two sessions at one location; both reservations were
committed or neither existed. The booking froze
`participant_count × price_per_participant`, the post-promotion amount, a
`ceil(post_promotion_amount / 2)` deposit and the remaining amount, created one
deposit invoice, and on valid gateway evidence confirmed the booking and issued
one QR ticket per participant. *(superseded by FR-030, FR-035, FR-044)*

**Former Constitution Check.** Data evolution was CONDITIONAL (reset only for
dev/test after preflight); evidence and concurrency covered "atomic
multi-session hold, payment callback idempotency, late-payment rejection, and
capacity races". *(late-payment rejection superseded by FR-040/FR-040a)*

**Former flows.** Create: validate contact, reserve `participant_count` places
in every session through Operations, persist pending booking, invoice and
expiry in one transaction. Payment: start/retry only while the 10-minute hold *(for groups: until `paymentDeadlineAt`, FR-012a 2026-11-15)*
is active; commit the hold set and confirm on matching evidence; obtain the
ticket projection; publish a reference-only notification event. A signed
success after hold expiry never confirmed; only a Manager could record or
escalate a follow-up without restoring capacity. *(superseded by FR-031,
FR-032, FR-038, FR-040, FR-046)*

**Former contract and states.** Booking create, payment start, contact-proof
and Member lookup, Magic-Link exchange, pending-hold release, confirmation
resend, Manager Magic-Link incidents and Manager late-payment follow-up on
`/workshop-bookings/{bookingCode}`; booking states `PENDING_PAYMENT`,
`CONFIRMED`, `EXPIRED`, `CANCELLED`, plus `COMPLETED`/`NO_SHOW` from 003.3 (D13)
and financial `UNPAID`/`DEPOSIT_PAID`/`FULLY_PAID`/`REFUND_PENDING`/`REFUNDED`
(D14). *(routes removed by FR-046; the D13/D14 statuses remain on OTA
`WorkshopBooking` and on ticket bookings)*

**Former verification plan.** Quickstart checks of each one-session offer,
two-session Wax Casting, odd/even post-promotion amount with rounded-up deposit,
valid/invalid/duplicate/late gateway events, ticket issuance and lookup,
Magic-Link revocation, hold expiry and authorization-safe lookup, plus MySQL
migration coverage, API contract validation and `ApplicationModules.verify()`.
*(superseded by the verification plan above)*
