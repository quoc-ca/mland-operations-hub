# Feature Specification: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-11-16
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature Branch**: `003.3-workshop-settlement-and-changes`

**Status**: Implemented except FR-056 and FR-057 (added 2026-11-15; planned as
tasks T133–T136, not built), the FR-045a change-request expiry fix and the FR-044 24-hour window fix (T130), the
cash-close `locationId`/`workingDate` naming fix (T131/T132), the FR-040 key
replacement operation (T137) and the SC-015 accessibility/browser verification
(T138), and the refund-policy removal (T139/T140), the follow-ups T141–T143 and
the checklist follow-up of 2026-11-15 (Phase 11, T144–T172, all open except T153 (done) and T148 (merged into T137): cash
reversal, refund failure path, account-entry confirmation, optional receipt
image after plan decision O-1, voucher restoration outcome, folio close rule);
T005 and T035 blocked on VNPay merchant configuration

**Approval**: Session 2026-11-15 (Clarifications and "Checklist follow-up") approved
2026-11-15 by Hồ Hoàng Quốc (owner Feature 003), on request in the working
session. Scope: the decisions recorded in that session in this `spec.md` and
their sync in `plan.md`, `data-model.md`, `tasks.md` and the contract. Also
confirmed by the owner on 2026-11-15: the FR-043 timing of closing a folio from
`FINALIZED` to `CLOSED` after a Mland cancellation (only after the refund is
`REFUNDED`), and the plan decision O-1 names (receipt image folder and table
names, as proposed there).

**Input**: Confirmed workshop-settlement, change, and direct-booking cancellation
decisions. This spec retains the feature baseline locally so a later retirement
of the shared `research.md` does not remove its behavioural decisions.

## Feature 003 authorization decision (2026-10-29)

Every active canonical `MANAGER` has global location scope for Manager-authorized
settlement and change actions, consistent with 003.1, 003.2 and 003.4. Manager
authorization does not depend on `activeLocationIds`; Staff actions remain
location-scoped, Member ownership and booking-specific safeguards remain in
force, and explicitly Admin-only technical actions remain Admin-only. There is
no Central Manager tier.

## Staff booking-assignment access and folio add-on rules (owner decision, 2026-11-06)

This amendment supersedes every statement in this document that lets an active
Staff member act on a final bill, charge, add-on or final payment by booking
location scope alone ("Staff in the booking location scope", "in-scope Staff",
FR-002a and the add-on acceptance rule). Read each of them as "an active Staff
member whose location scope contains the booking location **and who is actively
assigned to a session the booking holds a valid ticket for** (003.2 "Staff
session-assignment access")". A Manager remains global.

- **Access**: reading the operator view of the bill, creating or loading the
  folio, listing catalogue add-ons, adding a charge or add-on, finalizing, and
  starting the final payment follow the rule above. An unassigned Staff member
  is refused (`SESSION_ASSIGNMENT_REQUIRED`) and the refusal is audited.
  Customer routes (Member ownership, Guest proof) are unchanged, and an operator
  can never use them.
- **Operator read**: `GET /workshop-bookings/{bookingCode}/final-bill/operator`
  returns the bill for the operator; `GET .../final-bill` stays the customer
  route. *(Superseded 2026-11-14/15: the booking-wide `final-bill*` routes were
  removed (T106, T123); the per-ticket reads under
  `/workshop-tickets/{ticketCode}/folio` replace them, FR-048.)*
- **Catalogue add-on** (`catalogueAddOnReference`): the price is resolved by the
  backend from the 003.2 catalogue; `GET .../final-bill/add-ons` lists the
  add-ons sellable for the booking's package and location. *(Superseded
  2026-11-14/15: route replaced by the per-ticket folio add-on list, FR-048,
  T106; the server-priced rule stands.)*
- **Session started**: an add-on or a variable charge may be recorded only once
  at least one session of the booking is `IN_PROGRESS` or `ATTENDANCE_FINALIZED`
  (`SESSION_NOT_STARTED` otherwise) and only while the folio is OPEN.
- **Customer-agreed confirmation**: both need the operator to state that the
  customer agreed (`customerConsentConfirmed = true`, else
  `CUSTOMER_CONSENT_REQUIRED`). The backend generates `CONSENT-<id>` and stores
  it with the acting account and time on the charge. It evidences the
  operator's confirmation only, not the customer's own consent.
- **OTA shop add-on bill** (003.4): the settlement bill also records the Mland
  booking reference, and payment and refund follow the same assignment rule.
  Legacy bills without that reference stay Manager-only for Staff.
- Cancellation cases, remedies, required-session changes and folio corrections
  keep their previous authority (Manager, or Staff by location where already
  allowed); this amendment does not change them.

## Direct-booking open-folio policy (2026-11-05)

003.3 is the settlement authority for the direct-booking OPEN folio.

- Add-ons may be appended only while the folio is OPEN.
- `balance_due` is derived server-side from immutable base/payment evidence and accepted charges.
- Finalization requires verified payment evidence.
- No add-on mutation is allowed after finalization.
- Ordinary cancellation/no-show closes the folio at the verified collected amount.
- Direct deposits are non-refundable and do not call VNPay QueryDR/refund.
- OTA base-ticket payment remains provider-owned; Mland shop add-ons use separate bills.

T040 is an exceptional post-finalized correction/remedy path, not the normal add-on flow.

- *(Superseded 2026-11-12 by FR-030: per-ticket base and booking-time collection.)* The folio's base line is the 003.1 frozen `postPromotionAmount`; its first
  verified collection is the 003.1 deposit `ceil(postPromotionAmount / 2)`,
  immutable after acceptance. A full-amount payment at booking time is not
  offered; the remaining amount and add-ons are collected at checkout.
- `totalAmount = base line + accepted add-ons`; `paidAmount = Σ verified
  collections` (append-only); `balanceDue = totalAmount − paidAmount`.
- Operations (003.2) owns cancellation eligibility and no-show facts; Settlement
  owns folio closure and its financial state.
- Free laser engraving is an OTA-only entitlement and creates no bill.

Where later sections say "final bill", read the direct-booking OPEN folio. Where
they allow a separate direct-Mland add-on bill after finalization, that is
superseded: after `FINALIZED` no add-on may be added, changed, or removed, and
only the exceptional correction flow (T040) applies.

**Folio lifecycle decisions (owner, 2026-11-05)** — boundaries are in
[workshop-interfeature-facades.md](../contracts/workshop-interfeature-facades.md#direct-booking-open-folio-lifecycle-approved-2026-11-05):

- **D1**: Settlement opens the OPEN folio when it consumes
  `MlandBookingConfirmedEvent` at deposit confirmation, with the verified
  deposit as the first collection. `POST /final-bill` no longer creates a bill;
  it returns the existing folio. *(Superseded 2026-11-12/15: the folio is per
  ticket (FR-030) and the booking-wide `POST /final-bill` route was removed
  (T106, T123); see FR-048.)*
- **D2**: a booking is a no-show only when every ticket on every required
  session is a final `NO_SHOW`; partial attendance proceeds to normal checkout.
- **D3** *(superseded 2026-11-13 by FR-046/FR-047: per ticket only)*:
  Operations publishes `BookingAttendanceFinalizedEvent` and exposes
  `BookingAttendanceOutcomeQuery`; Settlement decides closure from the query.
- **D4**: folio states are `OPEN`, `FINALIZED` (checkout, `balanceDue = 0`) and
  `CLOSED` (ordinary cancellation/no-show at the verified collected amount, or
  a verified Mland-caused remedy refund per D11).
  Both terminal states are immutable (except FR-043); `CLOSED` never triggers Loyalty earn.
- **D5**: an append-only Flyway backfill creates an OPEN folio for existing
  confirmed direct bookings from their frozen amounts and verified deposits.
- **D6**: a `PARTIAL` booking earns Loyalty points like a fully attended one:
  Settlement emits `WORKSHOP_COMPLETED_AND_FULLY_PAID` when its folio is
  `FINALIZED` with zero balance. `WORKSHOP_NO_SHOW_FINAL` is emitted only for a
  full no-show (D2), whose folio becomes `CLOSED`.
- **D11** *(superseded 2026-11-12: a refund is complete when a Manager confirms `REFUNDED` after bank reconciliation, FR-039)*: after a Mland-caused remedy refund (FR-020/FR-021) is verified by
  signed QueryDR for every allocation, the folio becomes `CLOSED`, recording
  collected and refunded amounts; a pending/unknown refund keeps it `OPEN`.
- **D12–D16**: 003.3 owns the OTA shop add-on bill (D12); 003.1 sets booking
  `COMPLETED`/`CANCELLED`/`NO_SHOW` on a Settlement conclusion (D13) and
  `REFUND_PENDING`/`REFUNDED` during a Mland remedy (D14); a Mland remedy emits
  Loyalty `VALID_WORKSHOP_CANCELLATION` (D15); OTA laser engraving is always
  free (D16). See FR-001, FR-001a and FR-021.
- **Loyalty outcome processing (backfilled 2026-11-12, ade3df0, 4fa224e)**: the
  outcome is decided only from the authoritative attendance outcome and folio
  state: terminal `NO_SHOW` → `WORKSHOP_NO_SHOW_FINAL` whether the folio is
  `OPEN` or `CLOSED`; terminal `ATTENDED`/`PARTIAL` →
  `WORKSHOP_COMPLETED_AND_FULLY_PAID` only when the folio is `FINALIZED` with
  zero balance (a `CLOSED` folio never grants); a `CLOSED` folio from an
  accepted customer cancellation or a Mland remedy of a Member booking →
  `VALID_WORKSHOP_CANCELLATION`. A scheduler is the single trigger and retry,
  outside the cancellation, remedy, finalize and attendance transactions; once
  decided the folio's `loyaltyProcessedAt` is set and it is not rescanned.
  Booking keeps the Member Loyalty source while the booking is `CONFIRMED`,
  `COMPLETED`, `NO_SHOW` or `CANCELLED`, so a completed Member booking is not
  treated as a Guest booking.
- **D17–D18**: an OTA shop add-on bill uses the server-derived price of the
  active 003.2 catalogue add-on (no actor-entered amount), and in-scope Staff
  or any active Manager may quote and bill it, the same rules as direct-booking
  add-ons (FR-004a/FR-004b).
- **D7–D10 (T040 post-FINALIZED correction)**: the correction flow handles
  only an overcharge; an undercharge found after `FINALIZED` is absorbed by
  Mland and never collected. Only an active Manager opens and approves a
  correction with a reason; the overcharge is refunded by VNPay partial refund,
  allocated oldest verified payment first (QueryDR-verified); the deprecated
  `direct-addon-bills` create/read operations are repurposed as the
  folio-correction resource and its `payment-attempts` operation is removed.
  See FR-004c. *(Superseded 2026-11-12 by FR-038: bank refund. The overcharge
  is refunded by bank transfer through the Manager-approved refund lifecycle,
  not by VNPay partial refund or QueryDR; the rest of D7–D10 stands.)*

## Per-ticket settlement, bank refunds, counter collection and changes (decision 2026-11-12)

**Source:** owner decisions recorded 2026-11-11/12 in
`artifacts/workshop-changes-to-implement.md`. **Status:** implemented (tasks
T093–T106 of Phase 8 and T107–T127 of Phase 9, done 2026-11-12 to
2026-11-15); FR-056/FR-057 and the 2026-11-15 fixes are open as T130–T172.
*(Earlier status "specified, not yet implemented (tasks Phase 8)" superseded
2026-11-15.)* It follows 003.1 FR-030–FR-043 (booking group,
one ticket booking per person, per-ticket booking-time collection).

**Supersedes** (superseded lines are kept for history and read through this
section): the folio base/deposit rule `ceil(post/2)` and "deposits are
non-refundable and do not call VNPay" wording of the 2026-11-05 policy (the
non-refund rule stays, now per ticket); every VNPay refund and refund-QueryDR
path for direct Mland money — FR-004c, FR-021, D11 and Session 2026-10-31
"refund execution"; VNPay-only final-payment evidence and the ban on manually
recorded counter payments (FR-009, FR-010, FR-011, FR-007 "no Manager
exception"); whole-booking customer cancellation (FR-013–FR-015a, FR-016,
FR-017); Manager-only required-session change with no customer
self-service (FR-018 and Scope); whole-booking Mland remedy (FR-020, FR-021).

### Per-ticket folio

- **FR-030**: Each ticket booking MUST have its own OPEN folio. Base line = the
  ticket's final price (003.1 FR-035); first collection = the ticket's allocated
  booking-time collection; a `FREE` or `DEFERRED_BELOW_VNPAY_MINIMUM` ticket
  opens its folio with no payment evidence (collected 0, balance = ticket price,
  which is 0 for a free ticket). D2–D6 apply per ticket: a ticket is a no-show
  only when every session of that ticket is a final `NO_SHOW`; Loyalty earn is
  decided per checked-out ticket after the 24-hour attendance-correction window,
  for a Member buyer only, never twice for a folio with several collections.

### Counter collection

- **FR-031**: The remaining balance and add-ons MAY be paid by VNPay, cash or
  direct bank transfer to the shop account, in several collections and mixed
  methods on one folio. The checkout screen MUST offer them in this order:
  VNPay, cash, direct transfer. Each collection records method, amount, status
  (`PENDING_VERIFICATION`, `CONFIRMED`, `FAILED`, `VOIDED`), reference, payer,
  confirming actor and time, and belongs to one ticket folio.
- **FR-032**: Verification: VNPay only by signed IPN/QueryDR (never marked by
  Staff); cash by Staff with collection authority (an active Staff member whose location
  scope contains the ticket's location and who is actively assigned to a
  session the ticket holds, per the 2026-11-06 session-assignment rule; a
  Manager is global), recording amount received
  and change returned, with only the applied amount on the folio; direct
  transfer only by an actor holding `workshop.payment.transfer.confirm` after
  seeing the money in the shop account (remote confirmation allowed). A
  customer screenshot is never enough. A `PENDING_VERIFICATION` collection does
  not reduce the balance. *(V1, decision 2026-11-12: the codebase has roles,
  not named permissions, so the holder of `workshop.payment.transfer.confirm`
  is a Manager and transfer confirmation is Manager-only; per-person grants
  wait for FE-001.)*
- **FR-033**: The VNPay minimum of 5,000 VND applies to each VNPay collection;
  cash and direct transfer have no system minimum or maximum: a transfer
  collection is any amount above 0 up to the ticket's remaining balance, and
  money that arrives beyond it follows FR-034 (bank transfer limits are
  outside the system; clarified 2026-11-15). A
  balance of 1–4,999 VND is collected by cash or transfer. When a split would
  leave a VNPay part under 5,000 the screen MUST say so before collecting. The
  system MUST NOT raise an amount, waive a remainder or hide a remainder.
- **FR-034**: Creating a collection MUST lock the folio and consider confirmed
  and pending collections so one balance is never assigned twice. Switching
  method for a part with a pending VNPay attempt MUST first reconcile that
  attempt; closing the QR or screen is not proof of failure. Money that arrives
  twice or exceeds the balance MUST be recorded in full and routed to a Manager
  refund (FR-039); it never finalizes or earns twice. The same bank transaction
  or cash confirmation cannot be recorded twice. *(decided 2026-11-15,
  checklist CHK003)* A collection still `PENDING_VERIFICATION` when Mland
  cancels the ticket (FR-043) does not block the cancellation: a Manager
  confirms it or marks it `FAILED`; a part confirmed after the cancellation is
  recorded in full as money collected and routed to the Manager refund as
  duplicate or excess money (FR-039), and the folio closes (FR-043) only when no
  collection of the ticket is still pending. *(decided 2026-11-15, checklist
  CHK016)* A VNPay collection that is still unreconciled 24 hours after the
  attempt was created, and for which QueryDR reports no successful payment, is
  marked `FAILED` automatically (audited as system action) so the balance can be
  paid another way; closing the QR or screen remains no proof of failure before
  that. A payment that arrives after the automatic `FAILED` is recorded in full
  as money collected and routed to the Manager refund as duplicate or excess
  money (FR-039), never lost. The 24-hour rule cannot be exercised end to end
  until the VNPay QueryDR sandbox is configured (T005, T035).
- **FR-035**: A ticket's folio MUST be `FINALIZED` only when its
  `balanceDue = 0` and none of its collections is pending; no other condition
  applies (attendance, the 24-hour correction window and the group's other
  tickets do not block it; clarified 2026-11-15), and each ticket of a group
  finalizes on its own; the `FINALIZED` state is immutable (except FR-043). The workshop product is handed over only after that. When the customer must leave while a transfer or
  VNPay part is unverified, Staff records `Chờ xác minh thanh toán` with the
  customer's transaction reference and the product is not handed over; the
  folio finalizes automatically when the part is confirmed. Handing over before
  verification is an exception only a Manager may approve, with a reason and
  audit. Recording a real debt (`Còn nợ`) and closing it are Manager-only, with
  a reason; there is no automatic reminder, deadline or booking block.
  *(clarified 2026-11-15, checklist CHK012)* A recorded debt (`Còn nợ`) never
  finalizes a folio: `balanceDue` stays above 0, so the folio stays `OPEN` until
  a later collection clears it and finalizes it; the handover exception
  releases the product only and changes neither `balanceDue` nor the
  `FINALIZED` condition.
- **FR-036**: Each branch MUST close its cash book once per working day (Vietnam
  time) when closing: the system totals confirmed cash for the branch and day
  (net of change returned), the closer enters the counted amount, a difference
  requires a reason and Manager confirmation, and cash collected after the close
  belongs to the next day. Confirmed cash of a closed day never changes; a
  Manager folio correction refunds by bank and does not touch it (FR-050,
  clarified 2026-11-15). Transfers and VNPay are not part of the cash close;
  a Manager checks them with the read-only reconciliation list of FR-056.
  *(decided 2026-11-15, checklist CHK013)* "The close" is the moment the closer
  submits the counted amount: cash collected after that moment belongs to the
  next working day even while a difference still awaits Manager confirmation,
  and the Manager's confirmation does not move the cut-off or change the
  submitted count. *(Decided 2026-11-16, R04)* The read-only `cashDayClosed` of a collection is true from that same moment, so both for a close in `PENDING_MANAGER_CONFIRMATION` and in `CLOSED`. *(decided 2026-11-15, checklist CHK014: exception to the
  rule that a closed day never changes)* A cash collection recorded in error on
  a closed day is corrected by a Manager-only reversal entry (reason required,
  `Idempotency-Key` required, audited with actor and time) dated the current
  working day as a negative cash amount; the closed day is never edited. The
  reversal reduces the folio's collected amount and `balanceDue` is recomputed;
  it is refused when that folio is already `FINALIZED` or `CLOSED` or a refund
  request exists for that money (those cases go through the folio correction of
  FR-050 and the bank refund of FR-038). *(decided 2026-11-15, checklist
  CHK024)* The closer is an active Staff member or Manager whose location scope
  contains the branch; the session-assignment rule does not apply because the
  close is per branch, not per session. The closer is recorded on the close
  record, and only a Manager confirms a difference.
- **FR-037**: Add-on and variable-charge prices remain at least 5,000 VND. A
  wrongly recorded line is voided whole with history by a Manager while the
  folio is OPEN; this is not a customer's right to cancel a used service.

### Bank refunds (no VNPay Refund)

- **FR-038**: Direct Mland money is refunded only by bank transfer outside the
  system; the system calculates, records, tracks and reconciles but MUST NOT
  call VNPay Refund or refund-QueryDR. Refunds apply to Mland-caused
  cancellation (FR-043), late payment that is not re-confirmed (003.1 FR-040),
  overcharge after `FINALIZED` (FR-004c, now by bank) and duplicate or excess
  money (FR-034). Customer self-cancellation and no-show never refund the
  booking-time collection. Refunds are per ticket; the total refunded or
  pending for a ticket never exceeds the money collected for that ticket, and a
  refund of one ticket changes no other ticket. There is no 5,000 VND minimum.
  Voucher value is never refunded as money. *(clarified 2026-11-16, fix A31, task T168)* A `REFUNDED` refund request does not trigger ticket entitlement revocation; revocation happens when `cancelTicket` is called (003.2 FR-031). *(clarified 2026-11-15, checklist
  CHK032)* Exception: a late payment that is not re-confirmed (003.1 FR-040)
  has no ticket and no folio; it opens one group-level request (source
  `LATE_PAYMENT_NOT_RECONFIRMED`, source reference = the late-payment review
  reference, amount = the verified late evidence), and the per-ticket cap
  applies only to ticket-based requests. *(clarified 2026-11-15, checklist
  CHK032)* A second group-level source exists: verified money for a `CONFIRMED`
  group that settles no ticket (a second verified success with a different
  provider transaction, 003.1 FR-013a; `workshop-interfeature-facades.md`,
  decided port `ExcessPaymentRefundPort`) opens one group-level request with
  reason `EXCESS_OR_DUPLICATE_MONEY`, source reference = the Booking
  `evidenceReference`, amount = the verified evidence amount (Settlement
  rechecks it through the Booking facade), idempotent on
  `(reason, source reference)`. Like the late-payment request it has no ticket
  and no folio, the per-ticket cap does not apply, and its account is collected
  only through the account link (FR-040), never at the counter. *(clarified
  2026-11-15, checklist CHK002)* The ticket cap binds every ticket-based
  request whatever its reason: the amount of a new request is at most the
  money collected for the ticket (including excess or duplicate money kept on
  that folio, T110) minus the sum of the amounts of all its other requests
  that are `REFUNDED` or still open (`CLOSED_UNREACHABLE` counts as open,
  because the customer keeps the right). Example: collected 500,000 VND, of
  which an excess refund of 80,000 VND is pending; a Mland cancellation then
  opens a request of 420,000 VND, so the customer receives 500,000 VND in
  total and never more. The remainder is computed and the request created
  under the ticket folio lock (the lock of FR-034), so two requests opened at
  the same moment cannot together pass the cap; a request whose remainder
  would be 0 is not created and the existing request is returned.
- **FR-038a** *(decided 2026-11-12)*: The overcharge refund of an OTA booking's
  separate Mland shop add-on bill (D12, T092) is Mland money and also moves to
  the bank refund flow of FR-039–FR-040 (Manager-only, no VNPay Refund). The
  payer is usually at the counter, so Staff may enter the account there instead
  of sending the link. OTA base-ticket refunds remain the OTA's process.
- **FR-039**: Refund request lifecycle: `PENDING_ACCOUNT` → `PENDING_APPROVAL` →
  `PENDING_TRANSFER` → `PENDING_RECONCILIATION` → `REFUNDED`, plus
  `CLOSED_UNREACHABLE` (reopenable). A Manager approves reason, amount and
  recipient. An actor holding `workshop.refund.execute` transfers outside the
  system and records the bank reference (required, text) and may attach proof
  (an optional image of the transfer receipt; *decided 2026-11-15, checklist
  CHK007; ~~file type, size limit, storage location and retention of the image
  are not yet decided and are settled in the plan before this is built~~
  (superseded 2026-11-15, plan O-1 decided by Hồ Hoàng Quốc): at most one
  JPEG, PNG or WebP image of at most 5,000,000 bytes per transfer evidence,
  checked by declared Content-Type, file signature and a readable header (the
  file name extension is never trusted; PDF is refused, staff send a
  screenshot); stored as an authenticated object in the Cloudinary account
  already used by feature 002, in a separate 003.3 folder, with only metadata
  in the database; never served by a provider URL but streamed by the backend
  after the same access check as the request; if image storage is unavailable
  the bank reference is still recorded without the image; an ATTACHED image is
  not deleted before 5 years after the request reaches `REFUNDED` or
  `CLOSED_UNREACHABLE` (delete job T166, after V1; fix A16, 2026-11-16),
  an uploaded image never attached is removed after 24 hours; ~~the final
  retention period waits for an accounting decision (open, owner Hồ Hoàng
  Quốc)~~ *(decided 2026-11-15 by Hồ Hoàng Quốc)* the attached image file is
  deleted 5 years after the refund request reaches `REFUNDED` or
  `CLOSED_UNREACHABLE`; the bank reference, checksum and upload metadata stay
  with the refund record; the in-app image is supporting evidence, not the
  accounting voucher (the bank statement is kept by accounting)*; the image is visible only to a Manager and the transferring actor,
  which in V1 means a Manager only (see the 2026-11-13 role decision below), and
  follows the request's lifecycle); only a Manager confirms
  `REFUNDED` after matching the bank statement (one Manager may both approve and
  confirm; every step records actor, time and proof). The default recipient is
  the payer of the refunded money (the group payer for the shared booking-time
  payment even when the ticket was sent to someone else; the counter payer for
  a counter collection); another recipient needs the payer's recorded
  confirmation *(withdrawn 2026-11-14 by Hồ Hoàng Quốc: there is no
  different-recipient step, see the decision below)*. Requests and
  confirmations are deduplicated. *(Decided 2026-11-15
  by Hồ Hoàng Quốc: VNPay Refund and refund-QueryDR are no longer used and their code is removed, so the
  former rule that a legacy VNPay refund with code `99` is reconciled before a manual refund no longer
  applies; no such refund exists in any local database.)* Business consequences (folio `CLOSED`, booking refund status) apply only
  after `REFUNDED`. *(V1, owner decision 2026-11-13: the codebase has roles,
  not named permissions, so a Manager is the holder of `workshop.refund.execute`
  and every refund transition is Manager-only; per-person grants wait for
  FE-001.)*
  **Decided 2026-11-14 (Hồ Hoàng Quốc, owner Feature 003): there is no
  different-recipient step at all.** The refund goes to the account the payer
  enters, and the system neither compares the holder with the payer (FR-040,
  2026-11-13) nor asks anyone to declare that the recipient is someone else.
  The earlier rule "another recipient needs the payer's recorded confirmation"
  is **withdrawn**: the holder name cannot be verified against any trusted
  source, so the declaration would add work for the customer and Staff without
  adding assurance. What protects the money instead is unchanged: only the
  payer reaches the single-use account link, Staff counter entry records actor,
  channel and time, a Manager approves every refund transition, and the full
  account number is revealed only to that Manager and the transferring actor,
  audited and `no-store`.
- **FR-040**: The system MUST collect the recipient account through a single-use
  link sent to the payer (valid 7 days, resendable, a resend revokes the old
  link) or Staff entry at the counter (recording actor, channel and time): bank,
  account number and holder name. The system does not compare the holder with the payer
  *(decision 2026-11-13: no trustworthy payer name or source account exists —
  VNPay returns neither — and name matching misjudges namesakes and spelling)*;
  the approving Manager approves the amount, reason and order. *(decided
  2026-11-15, checklist CHK025: Staff counter entry is allowed only for an
  active Staff member actively assigned to a session the ticket holds, by the
  same rule as FR-032, or for a Manager; a group-level request (a late
  payment that is not re-confirmed, or excess or duplicate group money,
  FR-038) has no ticket, so it is collected only by the
  account link and never at the counter; a refund of an OTA booking's overcharge
  follows the 2026-11-06 session-assignment rule)* *(decided
  2026-11-15, checklist CHK005/006: the earlier rule that the transferring
  actor compares the holder name the bank shows with the name entered and stops
  on a mismatch is replaced by a confirmation at entry)* Before the customer
  (on the account link) or Staff (at the counter) submits, the form shows the
  bank, account number and holder name back and requires an explicit
  confirmation that they are correct; no bank name lookup is performed in V1.
  When a transfer later fails (the bank returns it, the statement does not match,
  or the approving Manager refuses), the request returns to `PENDING_ACCOUNT`
  with the reason recorded, the encrypted number is deleted (the mask is kept),
  the earlier approval no longer applies and a Manager approves again after a new
  account is entered; there is no terminal rejection state and the customer
  never loses the right to the refund. Account numbers are encrypted, shown in full
  only to the approving Manager and the transferring actor, masked
  (`****1234`) elsewhere, never written to email, logs or audit, and only the
  masked form is kept after `REFUNDED`. The encryption key is not rotated
  in-system in V1 (clarified 2026-11-15): when the key is replaced, every
  request still holding an encrypted number has that ciphertext deleted
  (keeping the mask), returns to `PENDING_ACCOUNT`, and a new account link is
  sent to the payer; this is done by an Admin-only route (`POST /workshop-refund-account-key-replacements`, reason and Idempotency-Key required, one audit record per request, never returning or logging a number; clarified 2026-11-15) called after the key variable is replaced and the service restarted. *(decided 2026-11-15, checklist CHK021)* The replacement runs in one transaction per request and a rerun is idempotent: it touches only requests that still hold ciphertext; the earlier Manager approval of such a request no longer applies, so a Manager approves again after the new account is entered. Without a reply the request stays
  `PENDING_ACCOUNT` with reminders on days 3 and 7; after 30 days a Manager may
  close it as `CLOSED_UNREACHABLE` with a reason and reopen it when the customer
  returns. The customer never loses the right to the refund. *(Decided 2026-11-16, R07)* A transfer returned late after `REFUNDED` is handled outside the system; no field is added and a transition from `REFUNDED` is refused with `409`. *(Decided 2026-11-16, A10)* The Admin-only key-replacement route runs a technical operation only and decides nothing about the business; every new approval is made by a Manager. *(Decided 2026-11-16, A11)* In V1 `REFUNDED` is the final state: money the bank returns late after `REFUNDED` is handled outside the system, a Manager writes a note, and the system changes no state.
  *(clarified 2026-11-15, checklist CHK017)* The refund-account link route
  answers an unknown token with `404 RESOURCE_NOT_FOUND` and a link that is
  used, revoked by a resend or expired with `410 REFUND_ACCOUNT_LINK_NOT_USABLE`;
  neither answer echoes an account number, and the customer asks the shop for a
  new link.

### Customer cancellation per ticket

- **FR-041**: The buyer MAY cancel chosen tickets (a recipient cannot; a
  recipient goes through the buyer or the shop). A ticket can be cancelled while
  unused and before its first session starts; there is no earlier deadline.
  Cancellation releases the capacity of every future session of the ticket and
  changes no other ticket. The booking-time collection is not refunded; a
  `FREE` or deferred ticket's folio closes without debt. *(Decided 2026-11-16, A05: a customer cancellation closes an `OPEN` folio at the amount collected, without debt; a `FINALIZED` folio stays `FINALIZED`, T157.)* *(Decision 2026-11-14,
  owner Hồ Hoàng Quốc.)* The buyer MAY cancel one ticket or several tickets of
  the same group in one request; a request for several tickets is all or none:
  every ticket is checked first, and if any cannot be cancelled none is and the
  failing tickets are reported. Each ticket keeps its own FR-042 outcome.
  *(Decision 2026-11-14.)* Proof of the buyer: a Member by the bearer token and
  ownership of the group; a Guest, and Staff at the counter on the buyer's
  behalf (reason required, channel STAFF), by the single-use, group-scoped
  `buyerVerificationReference` of 003.1 FR-041 (OTP to the group's original
  contact email). Settlement asks 003.1 to consume the proof inside the
  cancellation transaction, so a failed cancellation does not use it up. A
  recipient cannot cancel.
- **FR-042**: The 24-hour mark before the ticket's first session decides
  benefits: cancelling at least 24 hours before is a **valid cancellation** and
  returns the ticket's used points; cancelling later still releases capacity
  but benefits are treated as a no-show (no points returned). The group's
  voucher use is restored once only when every ticket of the group ended by
  valid cancellation or Mland cancellation; any late cancellation or no-show
  prevents it, unless a Mland cancellation has already restored it under FR-043
  (clarified 2026-11-15: a Mland cancellation restores it regardless of the
  other tickets, and it is never restored a second time). Each cancellation records request time, first-session start, the
  24-hour outcome, benefits returned, actor and channel, and replays are
  idempotent. Cart-level voucher conditions are not re-evaluated. *(decided
  2026-11-15, checklist CHK011/CHK034)* A voucher is a shared system-wide
  campaign, so it is never extended for one customer: restoration happens only
  while the voucher is still valid at the moment of restoration, for every kind
  of cancellation including Mland's. If it has expired, nothing is restored, the
  audit records reason `EXPIRED`, and the buyer is told; the money already paid
  is still refunded in full by bank (FR-038) and voucher value is never refunded
  as money. Settlement (003.3) only triggers the restoration through the shared
  facade; the module that owns vouchers and points performs it, exactly once per
  `groupCode` (idempotent key), and 003.3 never writes voucher or points data
  itself.

### Mland cancellation per ticket

- **FR-043**: When Mland cancels or moves a session, only tickets with that
  session are affected; a two-session ticket losing one session is handled as a
  whole ticket. Mland first offers every valid equivalent replacement (same package and
  branch, starting after the cancellation, with capacity and quota left, package
  session order kept; not counted as the customer's change, no extra charge) and
  the buyer chooses one; with none, the 100% bank refund opens at once
  (decided 2026-11-14).
  The buyer chooses ONE of all the offered equivalent sessions; a session is
  choosable only while it starts at least 24 hours after the answer; no answer
  means refund, never an automatic move, and the default refund fires at
  `answerDeadlineAt`, 24 hours before the LATEST offered session (aligned
  2026-11-15 with the implemented rule, `data-model.md` and `plan.md`; the
  earlier wording "answers at least 24 hours before the replacement starts" is
  superseded). If declined or unavailable, 100% of the
  money collected for the ticket (booking-time and counter) is refunded by bank
  under FR-038–FR-040, even if an earlier session of the ticket was attended.
  Used points of the ticket are returned. The group's voucher use is restored
  once, when the first Mland-cancelled ticket has its refund request opened, even when only part
  of the group is cancelled by Mland and even if another ticket of the group was
  cancelled late or was a no-show; it is never restored a second time
  (clarified 2026-11-15). *(Decided 2026-11-16, R06)* The restoration is also triggered when the buyer's answer, or the default at `answerDeadlineAt`, is recorded for a Mland-cancelled `FREE`/deferred ticket (no refund request is needed), still once per `groupCode`. *(decided 2026-11-15 by Hồ Hoàng Quốc, checklist
  CHK010)* "Has its refund request opened" means the moment the refund request of that
  ticket is opened (the buyer's refund answer, or the default refund at
  `answerDeadlineAt`), not `REFUNDED`: the voucher is not money and must not
  expire while the customer waits for the bank. This is the one consequence
  that does not wait for `REFUNDED` (FR-039 keeps the folio and booking
  consequences after `REFUNDED`). "Exactly once" means once per `groupCode`,
  enforced by the voucher-owning module (facade "Voucher and points
  restoration"); a repeat trigger returns `ALREADY_RESTORED` and changes
  nothing. A Mland-cancelled ticket that the buyer MOVES to a replacement
  session instead of a refund is not refunded, so it does not trigger the
  restoration and the voucher stays used; if another ticket of the same group
  is later refunded after a Mland cancellation, that refund triggers it then.
  A refund request of a ticket not cancelled by Mland never triggers it
  (FR-042). If the voucher has already expired when the request is opened
  it is not restored, reason `EXPIRED` (never extended), the refund is
  unaffected and the buyer is told. The refund amount follows FR-038 (100% of
  the money collected for the ticket, less other requests of the same ticket
  that are refunded or still open). The buyer is emailed the replacement and refund choice; a
  recipient is told the session was cancelled but only the buyer chooses. The
  refund is always 100% of the money collected for the ticket; the
  Mland-cancellation refund policy and its booking snapshot (FR-021a–FR-021e)
  do not apply to tickets (clarified 2026-11-15). *(decided 2026-11-15,
  checklist CHK004)* This is the only transition out of `FINALIZED`: when Mland
  cancels a ticket whose folio is already `FINALIZED`, the refund request
  (FR-038) is opened and the folio moves to `CLOSED` only once that refund is
  `REFUNDED` (FR-039: business consequences apply after `REFUNDED`); no other event reopens or
  closes a `FINALIZED` folio, and FR-035's immutability applies to every other
  case. *(decided 2026-11-15, follow-up to CHK004)* This FR applies whenever a
  session reaches `CANCELLATION_REQUESTED` (003.2), including an `IN_PROGRESS`
  session interrupted before attendance is finalized and tickets already
  checked in to it: the same 100% refund path applies. For a multi-session
  ticket that loses a session the refund stays 100% of the money collected for
  the whole ticket, including any session already attended.
  *(Added 2026-11-15, owner decision Hồ Hoàng Quốc, 003.2 checklist CHK009,
  003.2 FR-010a/SC-008; task T167)* An affected ticket of a 003.2 cancellation
  handoff is resolved once it is moved to a replacement session, or its bank
  refund request is opened (003.3 does not wait for `REFUNDED`). *(Decided 2026-11-16, A03: the earlier "or an audited Manager decision" is removed; a ticket with no money collected, `FREE` or deferred, is resolved when the buyer's answer or the default is recorded and its folio closes, with no refund request.)* As soon as the last affected ticket of the
  handoff is resolved, 003.3 MUST report it to 003.2 through
  `completeCancellation` (`CompleteCancellationHandoffCommand`), idempotently,
  so the session becomes `CANCELLED`; a handoff with no affected ticket is
  reported when it is received. *(Decided 2026-11-16, owner Hồ Hoàng Quốc)* If
  003.2 answers `CANCELLATION_HANDOFF_UNRESOLVED`, 003.3 treats it as "not yet":
  the folio and refund flow continue unchanged, and 003.3 retries when the last
  ticket resolves. 003.2 alerts the Managers if a session is still
  `CANCELLATION_REQUESTED` 24 hours after its scheduled end.

### Changes (rescheduling)

- **FR-044**: Each ticket allows one customer-requested change, shared by an
  advance change (at least 24 hours before the start of the session being
  changed; when one change moves both sessions, before the earlier of them;
  the ticket's other sessions do not count; clarified 2026-11-15) and late-arrival
  support (requested before the old session ends). One change may move session
  1, session 2 or both in one action. The allowance belongs to the ticket, not
  to each session: changing only session 1 consumes the ticket's allowance,
  so a later change of session 2 needs the Manager-granted exception below.
  Consume the allowance only after the whole selected replacement succeeds;
  failed attempts and retries of the same successful operation consume no
  additional allowance (clarified and approved 2026-11-12).
  For late-arrival support the replacement
  for the missed session starts no later than the old session's end + 7 days; a
  following session moved with it may fall outside that window but must keep
  the package order and gap. Moving several tickets together is all-or-none for
  the chosen tickets. Mland-caused changes do not count; a Manager may grant
  extra changes with a reason and audit. The customer may act online within
  these rights or ask Staff at the shop. Only valid options are shown and they
  are rechecked on confirmation. Capacity and the reschedule quota are checked
  per ticket in 003.2; quota never replaces the capacity check. OTA tickets
  follow the OTA's policy.
- **FR-045**: The package price stays frozen, so a change never lowers the price
  and never creates a refund. A ticket moved outside its voucher scope loses
  its allocated voucher discount; used points stay. A ticket moved into scope
  gains no new voucher. Any increase is shown with the new schedule and amount
  and confirmed by the buyer (owning a QR is not consent to a financial
  change), then added to the ticket's checkout balance; it need not be paid at
  the time of the change.

- **FR-045a — when an online change is auto-approved** *(decided 2026-11-14 by
  Hồ Hoàng Quốc, owner Feature 003; closes the "Open before implementation"
  item below)*: an online change requested by the buyer MUST be approved by the
  system itself, with no Staff or Manager step, when **all** of these hold at
  confirmation time:
  1. every target session has a free seat and reschedule quota left (003.2
     FR-035, checked per ticket; quota never replaces the capacity check);
  2. the request is either an advance change at least 24 hours before the
     start of the session being changed (the earlier one when both move;
     clarified 2026-11-15, formerly "the ticket's first session"), or late-arrival support inside the window of
     FR-044 (before the old session ends, replacement no later than the old
     session's end + 7 days);
  3. the ticket still has its one counted customer change, or a
     Manager-granted extra one (FR-054);
  4. the change does not raise the price, or it does and the buyer has
     confirmed the increase with the new schedule and amount (FR-045).
  If any of them fails, the system MUST NOT auto-approve: it refuses with the
  reason, and a request that only a Manager could allow (quota exhausted, the
  allowance used, outside the window) goes to the Staff/Manager queue instead.
  Mland-caused changes are not customer changes and never take this path.
  *(Queue and group change decided 2026-11-14, T117/T118)*: a request that only a
  Manager could allow is stored as `settlement_ticket_change_requests` and
  answered 202; a Manager approves (equal to an FR-054 grant followed by one
  counted change) or rejects it with a reason; it expires at the earliest of
  the start of the session being changed and the start of any requested target
  session (clarified 2026-11-15); the ticket's other sessions, such as an
  earlier first session, never make it expire. **LATE_ARRIVAL (decided 2026-11-15, owner Ho Hoang Quoc):** the session being changed has already started when the request is made, so it expires at the earliest of the end of the FR-044 7-day window (the changed session's end + 7 days) and the start of any requested target session. Each request carries `fromSessionReferences`, and each reference must belong to the ticket, otherwise it is refused (422). Several tickets of one group change together, all or none,
  through `POST /workshop-booking-groups/{groupCode}/ticket-changes`.


- **FR-044a — listing the sessions a ticket may move to** *(decided 2026-11-16 by Hồ Hoàng Quốc, owner Feature 003; tasks T176–T181)*:
  the system MUST offer read-only `GET /workshop-tickets/{ticketCode}/change-options` (Member who owns the group, Staff in
  the ticket's location, Manager) and `POST /workshop-tickets/{ticketCode}/change-option-lookups` (Guest, one contact proof
  of the group; same proof, uniform 404 and rate limit as `folio-lookups`; never an OTP), so a customer or Staff picks from
  valid sessions instead of typing identifiers. (1) A ticket with two sessions is answered one from-session at a time (the
  other stays); `pairs` appears only when both sessions are requested together. (2) A candidate must pass the same checks
  as 003.2 FR-034 (published, future, package order/gap/branch rule, type-specific source rule, LATE_ARRIVAL target no
  later than the old session's end + 7 days); a candidate that breaks the package rules is not listed. A target at another
  branch is listed when the package does not require one branch, with its own price increase (006 voucher scope, read
  only). (3) A candidate that only a Manager could allow (reschedule quota full, 24 h window closed, allowance used) is
  listed with `requiresManager=true` and the rule names; a full session, an OTA ticket or a folio that is not `OPEN` is
  never selectable. (4) The exact remaining seats are shown to every caller. (5) The list is advisory: `POST /changes` still
  re-checks everything. `fromSessionReferences` of `POST /changes` names only the sessions that really move (one per
  target, same order); the server builds the ticket's new session set by keeping the other session, and ignores a pair
  whose from equals its target.

*(Superseded 2026-11-14 by FR-045a.)* **Decided 2026-11-14 (formerly "Open before implementation"):** the exact
auto-approval conditions for online
changes. Customer email content was approved 2026-11-12 in
`artifacts/workshop-email-templates-draft.md` (filename retained; not yet
implemented). Reschedule quota values were decided 2026-11-12 (003.2 FR-035)
and the OTA add-on refund in FR-038a.

**Decided 2026-11-14 (formerly "Open (owner decision needed, re-check
2026-11-14)"; routes published by T123, screens by T125): operations required by
FR-030–FR-051 with no route in `contracts/workshop-settlement-ticket.openapi.yaml`:**

**Decided 2026-11-14 (Hồ Hoàng Quốc, owner Feature 003): each of these is a
public route, Manager-only, with a required reason and an audit record.** They
are not internal-only, because the Staff/Manager screens of T102 must call
them. T106 adds them to
`contracts/workshop-settlement-ticket.openapi.yaml`:

- **FR-052 — Manager confirmation of a cash-close difference.** The canonical
  path family is the cash-close resource:
  `POST /api/v1/workshop-cash-closes/{cashCloseId}/difference-confirmations`,
  Manager only, `Idempotency-Key` required (FR-022d), replay returns the stored
  close unchanged. The code's former `POST /api/v1/workshop-cash-days/confirm-difference`
  (query `branchId`, optional `businessDay`) was moved to it and removed (T106,
  T123); the cash-day paths are no longer served. The branch and day are now
  named `locationId`/`workingDate` (C2, decided 2026-11-15; code fix T131/T132).
  *(Earlier wording "moves to it in T106; the cash-day paths stop being public
  then" superseded 2026-11-15.)*
- **FR-053 — Manager void of a wrongly recorded line or collection.** While the
  folio is OPEN: `POST /workshop-tickets/{ticketCode}/folio/lines/{lineId}/voids`
  and `POST /workshop-tickets/{ticketCode}/folio/collections/{collectionId}/voids`.
  Manager only, reason required, `Idempotency-Key` required. A void is an
  audited reversal record and never deletes the original row; a collection
  already counted into a closed cash day is refused (FR-050), and the balance is
  recomputed by `FolioBalanceService` from what remains. *(R04, 2026-11-16: "closed" means the counted amount was submitted, including a close awaiting Manager confirmation.)* *(clarified
  2026-11-15, checklist CHK014)* The refusal for a closed cash day is not a dead
  end: the Manager records the cash reversal entry of FR-036 on the current
  working day instead, which is the only correction path for a wrongly recorded
  cash collection of a closed day.
- **FR-054 — Manager-granted extra change.** `POST /workshop-tickets/{ticketCode}/change-allowances`,
  Manager only, reason required, `Idempotency-Key` required. It grants one extra
  counted customer change for that ticket (FR-044) and never performs the change
  itself.
- **FR-055 — Staff-on-behalf change.** It reuses
  `POST /workshop-tickets/{ticketCode}/changes` with the buyer proof of FR-041
  and a required reason, exactly as a Staff-on-behalf cancellation does; it gets
  no route of its own, so one code path keeps the FR-044/FR-045 rules.

### Transfer/VNPay reconciliation list and absorbed undercharge (clarified 2026-11-15)

- **FR-056 — Transfer/VNPay reconciliation list.** A Manager MUST be able to
  list a branch's folio collections paid by transfer or VNPay, filtered by
  branch (`locationId`), Vietnam working day, method (`BANK_TRANSFER`, `VNPAY`, `BOOKING_TIME`; `BOOKING_TIME` added 2026-11-16, A04)
  and state (`CONFIRMED`, `PENDING_VERIFICATION`, `VOIDED`, `EXCESS`; clarified 2026-11-15, so every real transfer or VNPay money that can appear on the bank statement or VNPay Merchant Admin is visible, a voided collection flagged as such and VNPay money that settled no part flagged `EXCESS`; a `FAILED` collection carries no money and is not listed), without paging (one branch
  and one day). A confirmed row belongs to the
  Vietnam working day of its confirmation time, a pending row to the Vietnam
  working day of its creation time (clarified 2026-11-15). Each row shows the ticket code,
  group code, amount, method, state (the same `state` values as the filter),
  bank or VNPay transaction reference, and the
  confirming actor and time. The list is read-only: it records no "reconciled"
  mark, changes no collection, folio or cash close, and the Manager compares it
  with the bank statement and VNPay Merchant Admin outside the system. Staff
  cannot use it. A transfer that exceeds the balance or arrives twice (FR-034) is one `CONFIRMED` row with its full amount, `excessAmount` and `refundRequestReference`; an `EXCESS` row has no collection, belongs to the working day of its evidence time and gets its branch through the ticket folio at query time (decided 2026-11-15). *(Decided 2026-11-16, A04)* A `BOOKING_TIME` row is VNPay money paid when booking online, read through a new read-only 003.1 facade operation that lists booking-time payments by day and location from stored payment data and never calls VNPay. A row with a successful IPN shows the amount actually received and counts in the total; a row not yet verified (QueryDR not run or failed) shows the requested amount with the state "unverified" (`PENDING_VERIFICATION`) and does not count in the confirmed total. Example: online 6,000,000 VND plus counter 1,200,000 VND = 7,200,000 VND, matching VNPay Merchant Admin. *(Decided 2026-11-16, R02/R03)* When the filter includes `BOOKING_TIME` and the 003.1 operation fails or times out, the whole list answers `503 RECONCILIATION_SOURCE_UNAVAILABLE` and no partial list is returned; there is one row per payment, with `ticketCode` nullable and `ticketCodes[]` listing the tickets it covers (the 6,000,000 VND example is one row). *(Decided 2026-11-16, R18/R19)* A `BOOKING_TIME` row belongs to the Vietnam working day of its confirmation time (`CONFIRMED`) or of its attempt creation time (unverified or late-pending); its location by `locationIds[]` (decided 2026-11-16, option B, owner Hồ Hoàng Quốc: one row per payment, the sessions' locations of the group's tickets; the `locationId` filter returns every payment with at least one session at that location; the whole-system day total counts a payment once; per-location totals must not be summed, warn about double counting); it has no confirming actor (confirmed by the system through the IPN, `confirmedBy` null). Mapping from the 003.1 states: 003.1 `CONFIRMED` maps to `CONFIRMED`, `UNVERIFIED` to `PENDING_VERIFICATION` and `LATE_PENDING_REVIEW` (money received, group not confirmed, awaiting review or refund) to `PENDING_VERIFICATION` with the reason note 'late payment under review'; neither of the last two counts in the confirmed total. The amount of a `BOOKING_TIME` row is the stored requested amount (no separate received amount exists; a mismatched IPN is rejected by 003.1 and never listed in V1).
- **FR-057 — Absorbed undercharge record.** When an undercharge is found on a
  `FINALIZED` ticket folio (D7–D10: never collected, Mland absorbs it), a
  Manager MUST be able to record it: ticket, amount in VND (> 0), required
  reason, actor, time, `Idempotency-Key` and audit. The record only documents
  the loss: it collects nothing, does not reopen or change the folio, its lines
  or Loyalty earn, does not touch confirmed or closed cash, and names no
  employee as liable. Its content is immutable once saved; a mistaken record
  is voided by a Manager (reason required, audited, once only), keeps its
  original content, and no longer counts in totals; the correct amount is
  then recorded as a new record. A Manager can list these records by branch
  and period, with the total of non-voided records. *(decided 2026-11-15,
  checklist CHK009)* The amount of a record may not exceed the ticket's folio
  total (price plus add-ons and variable charges, voided lines excluded); a
  larger amount is refused with `422`, and the non-voided records of one ticket
  together may not exceed that total either.

## No-show per ticket only; direct booking is only a group (decision 2026-11-13)

**Source:** in-session instruction of Hồ Hoàng Quốc (owner Feature 003),
2026-11-13; follows 003.1's FR-044–FR-051 (003.1 numbering; every direct booking is a booking
group with ticket folios). **Status:** approved 2026-11-13 by the owner in session;
the former "Open" item below was decided 2026-11-14 (T106); implemented (T105
done 2026-11-14; booking-wide routes removed by T106, T121, T123, T124).
*(Earlier status "the "Open" item stays open; not implemented (task T105)"
superseded 2026-11-15.)*

**Supersedes:** D3 and the "Close on final no-show (P2)" row of the open-folio
lifecycle as applied to a whole booking, FR-015a, and the D5 legacy backfill
(no direct `WorkshopBooking` with a folio remains). Superseded lines are kept
for history and read through this section.

- **FR-046**: Settlement MUST close a ticket folio for no-show only from the
  per-ticket signal: 003.2 `TicketAttendanceFinalizedEvent`, keyed by the 003.2
  ticket reference (`attendance_tickets.reference`) and published per ticket
  after its 24-hour correction window (003.2 FR-032, T092). Settlement reads the
  per-ticket outcome (`getTicketAttendanceOutcome`) and, when terminal
  `NO_SHOW`, closes that ticket's OPEN folio as `CLOSED` at the verified
  collected amount, idempotently, touching no other ticket of the group.
  Because ticket folios are keyed by the 003.1 `ticketCode`, Settlement
  resolves the 003.2 ticket reference to the ticket booking through
  `WorkshopBookingFacade` (003.1 stores it as
  `ticket_bookings.operations_ticket_reference`); an unknown reference is
  recorded and retried, never guessed.
- **FR-047**: The booking-wide `BookingAttendanceFinalizedEvent`, its outbox
  `workshop_booking_attendance_finalization_outbox`, its consumer
  `BookingAttendanceFinalizedEventConsumer` and the booking-wide
  `BookingAttendanceOutcomeQuery` are removed once T092 publishes the ticket
  event. The Loyalty no-show outcome (D6, T089) is taken per ticket from the
  same per-ticket outcome, consistent with T093.
- **Decided (formerly Open)** *(decided 2026-11-14 by Hồ Hoàng Quốc, owner Feature 003; task
  T106; done)*: after 003.1's own FR-044–FR-051 (003.1 numbering) no direct `WorkshopBooking` remains and
  OTA bookings have no Mland folio, so the booking-wide routes of
  `workshop-settlement.openapi.yaml` (`/workshop-bookings/{bookingCode}/final-bill*`,
  `…/folio-corrections*`, `…/cancellation-cases*`,
  `…/required-session-change-cases*`, `…/mland-cancellation-remedy-cases*`)
  and the code routes `/workshop-folios/{bookingReference}/*` have no direct
  subject. They are replaced by per-ticket routes under
  `/workshop-tickets/{ticketCode}/folio` (`workshop-settlement-ticket.openapi.yaml`):
  - **FR-048**: Read (customer and operator view), catalogue add-ons, charges,
    collections, handover exception, debt record/close, explicit finalization
    and the post-`FINALIZED` correction MUST each have a per-ticket route with
    the rules of the booking-wide one: add-ons and charges only while the folio
    is OPEN and a session of the ticket has started, server-priced catalogue
    add-ons (D17–D18), at least 5,000 VND (FR-037); explicit finalization runs
    the FR-035 rule and is idempotent. Reading proof: the owning Member by
    bearer; Manager or assigned Staff for the operator view; a Guest by one
    contact proof of the group (email or phone, exactly as 003.1's group lookup)
    sent in a request body, never in a URL, answering the same 404 for a wrong
    code or proof. Reading never consumes the single-use
    `buyerVerificationReference`, which is kept for changes (FR-041).
  - **FR-049**: The checkout VNPay payment of a ticket folio is a folio
    collection with method `VNPAY`, confirmed only through signed evidence
    (`FolioCollectionService.confirmGatewayCollection`); the booking-wide
    `final-bill/payment-attempts` is removed.
  - **FR-050**: The post-`FINALIZED` correction keeps D7–D10 (overcharge only,
    Manager only, reason required, undercharge absorbed) but refunds through
    the bank refund flow (FR-038–FR-040), never VNPay. After `FINALIZED` no
    add-on is added, changed or removed and the folio is never reopened, even
    by a Manager (confirmed 2026-11-14). A purchase after checkout is a separate
    retail sale (FE-08), not a folio line. A correction never edits confirmed
    cash of a branch working day that is already closed (FR-036): the closed
    day stays locked. Because the correction refunds only by bank, it has no
    cash effect and no cash adjustment is recorded (clarified 2026-11-15).
    A confirmed cash collection of a closed day is never edited, reopened or
    deleted (except the Manager cash reversal of FR-036 for a folio not yet
    FINALIZED or CLOSED).
  - **FR-051**: The ticket's package, branch, sessions, group and buyer come
    from 003.1 (`getTicketFolioContext`), and Staff authorization reuses the
    session assignment of the ticket's group line (`staffAssignedToBooking`
    with the line reference); Manager is global.
  - Cancellation, ticket changes and Mland remedy are already per ticket
    (FR-041–FR-045, T099–T101). The booking-wide routes, 003.1 T100's legacy
    facade operations and the booking-wide 003.2 queries are removed only after
    T106 lands.

## Scope

*(Partly superseded 2026-11-12/14: the subject is now the ticket booking and
its ticket folio (FR-030, FR-048); "customer-requested whole-booking
cancellation" is superseded by FR-041 (the buyer cancels chosen tickets, one
or several of a group, all or none); "Manager-mediated change" and "does not
… provide customer self-service rescheduling" are superseded by FR-044 (one
customer change per ticket, online or at the shop, plus Manager-granted
extra changes); the Mland remedy is per ticket (FR-043). The original text
below is kept for history.)*

This feature owns direct-Mland actions after a workshop booking is confirmed:
post-package charges and final bills, final payment and release control,
immediate final-balance collection before package-output handover,
Manager-mediated change of one or more
future required sessions,
customer-requested whole-booking cancellation decision, and Mland-caused
package cancellation remedies.

It consumes booking/deposit evidence from 003.1 and authoritative session,
capacity, ticket, attendance, and cancellation-handoff outcomes from 003.2. It
does not create sessions, perform normal check-in, provide customer self-service
rescheduling, or process OTA payments/refunds. It does create and collect the
separate Mland shop add-on bill of an OTA booking (D12).

## Clarifications

### Session 2026-11-15

- Q: In a group where at least one ticket is cancelled by Mland and another ticket is cancelled late or is a no-show, is the group's voucher use restored? → A: Yes. The first Mland-cancelled ticket that has its refund request opened (or, for a ticket with no collected money, whose answer or default is recorded; FR-043, 2026-11-16) restores the group's voucher use at once, exactly once, whatever happens to the other tickets (FR-043 prevails; FR-042's all-tickets rule applies only when no ticket was cancelled by Mland).
- Q: Besides a zero balance and no pending collection, must any other condition hold before one ticket's folio can be `FINALIZED`? → A: No. A ticket's folio finalizes when its `balanceDue = 0` and no collection of it is pending; attendance, the 24-hour correction window and the other tickets of the group are not conditions (each ticket finalizes on its own).
- Q: When a Manager corrects a finalized folio whose cash day is already closed, is a cash adjustment on the current working day needed in V1? → A: No. A post-`FINALIZED` correction refunds only by bank (FR-050) and never touches confirmed cash; a closed cash day stays locked and no cash adjustment record or route exists. *(Partly superseded later on 2026-11-15, checklist CHK014: this still holds for a post-`FINALIZED` folio correction; a cash collection recorded in error on a closed day while the folio is not yet `FINALIZED`/`CLOSED` is corrected by the Manager-only cash reversal entry of FR-036.)*
- Q: Are the transfer/VNPay reconciliation reports of FR-036 in V1, and at what depth? → A: Yes, in 003.3, as a read-only Manager list only (FR-056); no "reconciled" mark and no in-system mismatch handling (customer collection after `FINALIZED`, employee liability, accounting entries) in V1.
- Q: Should an undercharge absorbed by Mland after `FINALIZED` be recorded in V1? → A: Yes. "Mland absorbs" means the business bears the loss, not a Staff member or Manager personally; a Manager records it as an immutable, audited, documentation-only record (FR-057) that collects nothing and changes no folio or cash.
- Q: When does a pending (Manager-approval) ticket change request expire? → A: At the earliest of the start of the session being changed and the start of any requested target session; the ticket's other sessions (e.g. an earlier session 1 when session 2 is being changed) do not affect it. The current code uses the ticket's first session instead and must be corrected (`TicketChangeService.requestExpiry`).
- Q: When the refund-account encryption key is replaced, what happens to account numbers encrypted with the old key? → A: No in-system key rotation in V1. Every request still holding an old-key ciphertext has it deleted (mask kept), returns to `PENDING_ACCOUNT`, and the payer gets a new account link; an audited operational step (FR-040).
- Q: Does the system cap the amount of one counter transfer collection? → A: No. A transfer collection is any amount above 0 up to the ticket's remaining balance; bank transfer limits are outside the system; money arriving beyond the balance follows FR-034 (FR-033).
- Q: Does 003.3 set feature-level non-functional targets or keep them TBD? → A: Accessibility and browsers follow 001: WCAG 2.2 AA; latest two major desktop Chrome, Edge, Firefox (SC-015). Performance and availability stay TBD at project level; no feature-specific number.
- Q: How is a mistaken absorbed-undercharge record corrected? → A: A Manager voids it (reason required, audited, once only); the original content is kept and the voided record no longer counts in totals; the correct amount is recorded as a new record (FR-057, SC-014).
- Q: In the reconciliation list, which working day does a transfer/VNPay collection belong to? → A: A confirmed collection belongs to the Vietnam working day of `confirmed_at`; a pending one to the Vietnam working day of its creation time (FR-056).
- Q: Must the cash-close fix align the whole request body with the contract, or only rename `branchId`/`businessDay`? → A: The whole body (`locationId`, `workingDate`, `countedAmount`, `differenceReason`), with an end-to-end test of the exact frontend body (T131/T132).
- Q: Is the 24-hour advance-change window measured from the session being changed or from the ticket's first session? → A: From the session being changed (the earlier one when one change moves both); the ticket's other sessions do not count. The code currently uses the first session and must be corrected (`TicketChangeService`, T130) (FR-044, FR-045a).
- Q: How is the refund-account key replacement operation run? → A: By an Admin-only route (`POST /workshop-refund-account-key-replacements`, reason + Idempotency-Key, audited per request), called after the key variable is replaced and the service restarted; not a startup profile and not manual SQL; the procedure is documented in a committed document of 003.3 (FR-040, T137).
- Q: Must the refund-policy tables be archived before T139 drops them? → A: No. They hold only refund-percentage parameters and snapshots, no payment evidence; drop them with a new migration (never editing an applied one); the history of policy changes stays in the audit table (T139).
- Q: Does the reconciliation list show VNPay excess money and voided collections? → A: Yes: states `VOIDED` and `EXCESS` are added (a `FAILED` collection carries no money and is not listed); still read-only (FR-056, T133).
- Q: Where does the reconciliation list get a transfer/VNPAY collection's branch? → A: Every collection stores its branch (`branch_id` = canonical `workshop_location.id`), set at creation from the ticket folio context; a new migration back-fills existing rows. No query-time lookup per ticket (FR-056, T133).
- Q: How does the system know which session a change replaces? → A: The change request carries `fromSessionReferences` (the ticket's current session or sessions being replaced, one per target, same order); the system checks each belongs to the ticket and takes its start time from 003.2 (`saleEligibility(sessionReference).scheduledStart()`); the 24-hour window and the request expiry use the earlier of them. Applies to `POST /workshop-tickets/{ticketCode}/changes` and the group `ticket-changes` route (FR-044, FR-045a, T130).
- Q: Until when does a queued LATE_ARRIVAL change request live, given that the session being changed has already started? -> A: Until the earliest of the end of the 7-day window (the changed session's end + 7 days, FR-044) and the start of any requested target session (FR-045a, T130).
- Q: How do an excess or duplicate bank transfer and VNPay excess money appear in the reconciliation list? -> A: A transfer that exceeds the balance or arrives twice (FR-034) is one `CONFIRMED` `BANK_TRANSFER` row with its full amount (matching the bank statement), plus `excessAmount` and `refundRequestReference` (FR-039); two different bank transactions are two rows; a second recording of the same transaction is refused. An `EXCESS` row (VNPay money that settled no part) has no collection (`collectionId` null), belongs to the Vietnam working day of its evidence time and gets its branch through the ticket folio at query time, a deliberate exception to the stored `branch_id` rule because such rows are rare (FR-056, T133, T143).
- Q: When Mland cancels a ticket's session, is the refund always 100% or the percentage of the booking's policy snapshot? → A: Always 100% of the money collected for the ticket; the Mland-cancellation refund policy and snapshots (FR-021a–FR-021e) do not apply to tickets (FR-043).
- Q: Should the now-unused Mland-cancellation refund policy (routes, snapshot capture, UI) be removed? → A: Yes: remove the `/workshop-refund-policies` routes, the snapshot capture on booking confirmation and the frontend use; drop the policy tables with a new migration (never by editing an applied one) (T139, T140).

#### Checklist follow-up (payment-error-access.md), answered by Hồ Hoàng Quốc on 2026-11-15

- Q: Can Mland's refund close a `FINALIZED` folio? → A: Yes, as the only exception: Mland cancellation opens the refund request and moves the folio to `CLOSED` only after the refund is `REFUNDED` and nothing is pending (FR-043, FR-039) (owner confirmed option A, 2026-11-15).
- Q: Does a pending collection block a Mland cancellation? → A: No; a Manager confirms or fails it, a later confirmation is routed to refund as excess, and the folio closes only when nothing is pending (FR-034).
- Q: After a bank refund fails (returned, statement mismatch, Manager refusal) what state? → A: `PENDING_ACCOUNT` with a reason, ciphertext deleted, approval void, Manager re-approves; no terminal rejection (FR-040). The transfer-time holder-name comparison is replaced by an explicit confirmation of bank, number and holder name at entry; no bank name lookup in V1.
- Q: Key replacement and old approval? → A: Approval no longer applies; one transaction per request, idempotent rerun (FR-040).
- Q: Transfer proof? → A: Bank reference text required, optional receipt image; storage, size and retention of the image are decided in the plan (FR-039). ~~**Open.**~~ *(Settled 2026-11-15, plan O-1; see the next answer.)*
- Q: Which receipt image files are accepted, how large, where stored, who sees them and how long are they kept (plan O-1, CHK007)? → A: JPEG, PNG or WebP only, validated like feature 002 product images (FR-008 of 002): declared Content-Type plus file signature plus a readable header, no trust in the extension; PDF is refused (staff send a screenshot). At most 5,000,000 bytes and one image per refund transfer evidence. Stored in Cloudinary as `type=authenticated` objects through a 003.3-owned adapter (the 002 module exposes no upload facade), same Cloudinary account and `CLOUDINARY_*` variables, separate folder (proposed `ppswbs/settlement-receipts`); the database stores metadata only (storage key, checksum, content type, byte count, uploader, times), never the bytes. The provider URL is never returned: the backend reads with a signed URL and streams it same-origin with `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`, after the same access check as the refund request (FR-039: a Manager and the transferring actor; V1: Manager only, since every refund transition is Manager-only; Staff, who may only enter the account at the counter and resend the link under FR-040, get `403`). Missing Cloudinary credentials give a typed dependency-unavailable error and the bank reference can still be recorded without the image. An ATTACHED image is not deleted before 5 years after the request reaches `REFUNDED` or `CLOSED_UNREACHABLE` (job T166, after V1; fix A16, 2026-11-16, replaces "V1 never deletes an attached image automatically"); an uploaded image never attached is removed after 24 hours (same rule as 002). ~~The final retention/deletion period is deferred to an accounting decision (owner Hồ Hoàng Quốc), still open~~ *(decided 2026-11-15)* The attached image file is deleted 5 years after the refund request reaches `REFUNDED` or `CLOSED_UNREACHABLE`; the bank reference, checksum and upload metadata are kept with the refund record. Basis: the image is supporting evidence, not the accounting voucher (accounting keeps the bank statement); 5 years follows the minimum for records not used directly for bookkeeping (Luật Kế toán 2015 / Nghị định 174/2016, to be confirmed by accounting; if accounting uses the in-app image as its voucher, the period becomes 10 years) (FR-039; decided by Hồ Hoàng Quốc, 2026-11-15).
- Q: Which day does cash taken while a difference awaits confirmation belong to? → A: The next working day; the cut-off is the submission of the counted amount (FR-036).
- Q: Correcting a wrong cash entry on a closed day? → A: Manager-only reversal entry on the current day, refused for `FINALIZED`/`CLOSED` folios or money under a refund request (FR-036, FR-053).
- Q: Voucher expired at restoration? → A: Never extended; not restored, reason `EXPIRED`, buyer told; the voucher-owning module performs restoration once per `groupCode` (FR-042).
- Q: Who closes the cash book and who enters a refund account at the counter? → A: Staff or Manager of the branch for the close; Staff assigned to a session of the ticket (or a Manager) for the counter entry (FR-036, FR-040).
- Q: Unreconciled VNPay attempt? → A: Automatically `FAILED` after 24 hours when QueryDR reports no payment; late money goes to excess refund (FR-034).
- Q: Cap on an absorbed undercharge? → A: Not above the ticket's folio total (FR-057).
- Q (CHK010, fix of 2026-11-15, decided by Hồ Hoàng Quốc): When is the group's voucher restored after a Mland cancellation? → A: When the refund request of the first Mland-cancelled ticket is opened (the buyer chose refund, or the default refund fired), not at `REFUNDED`, so the voucher cannot expire during the bank wait; once per `groupCode` (a repeat returns `ALREADY_RESTORED`); a ticket the buyer moves to a replacement session does not trigger it; an already expired voucher is not restored (`EXPIRED`) and the refund is unaffected (FR-043, T142, T155).
- Q (CHK019, fix of 2026-11-15, decided by Hồ Hoàng Quốc): Does `POST /refund-account-links/{token}` need `Idempotency-Key`? → A: Yes. A same-key same-content retry replays `204`; changed content is `409 IDEMPOTENCY_CONFLICT`; the record keeps only a keyed fingerprint, never the account (FR-022a, T169).
- Document-sync additions of the fix of 2026-11-15 (no new decision): ticket refund cap across reasons and the remainder amount under the folio lock (CHK002, FR-038, T170); group-level `EXCESS_OR_DUPLICATE_MONEY` request (CHK032, FR-038, FR-040); `401` rule and receipt-image/`429` status codes (CHK018, FR-022d, T171).
- Document-sync additions (no new decision): FR-035 debt, FR-040 link `404/410`, FR-022d `410`, FR-032 collection authority, FR-038 group-level late-payment request, FR-012a token scrub, SC-016, SC-017 (CHK012, 017, 018, 023, 032, 030, 037, 038).

### Analyze follow-up, answered 2026-11-16

*(Decided by Hồ Hoàng Quốc, owner of Feature 003, on 2026-11-16; answers to the analyze findings A02, A03, A04, A05, A10, A11, A12 and A15. No FR is renumbered.)*

- Q (A02): How do T130 and T141 ship, given that `fromSessionReferences` becomes required? → A: In the SAME release. The contract marks `fromSessionReferences` required in the same change, the stale "extend T132" wording of T130 is removed, and `ChangeRequest` carries `fromSessionReferences` (FR-044, FR-045a).
- Q (A03): Does an audited Manager decision resolve a ticket of a Mland-cancelled session, and what about a ticket with no money? → A: The "audited Manager decision" resolution is removed from FR-043 and T167. A ticket with no money collected (`FREE` or deferred) is resolved when the buyer's answer or the default is recorded and its folio closes; no refund request is needed. T167 tests a three-ticket group with one `FREE` ticket: when Mland cancels the session, 003.2 `completeCancellation` is not stuck.
- Q (A04): Does the FR-056 list include the online booking-time VNPay money? → A: Yes (option B, extended). The list adds method `BOOKING_TIME` (VNPay money paid when booking online), read through a new read-only 003.1 facade operation "list booking-time payments by day and location" that returns stored payment data and never calls VNPay. A row with a successful IPN shows the amount actually received and counts in the total. A row not yet verified (QueryDR not run or failed) shows the requested amount with the state "unverified" (`PENDING_VERIFICATION`) and does NOT count in the confirmed total. Example: online 6,000,000 VND plus counter 1,200,000 VND = 7,200,000 VND, which matches VNPay Merchant Admin (FR-056).
- Q (A05, A36): What happens to a folio when the buyer cancels? → A: Option A. An `OPEN` folio closes at the amount collected, without debt; a `FINALIZED` folio stays `FINALIZED` (T157). D4 and FR-035 read "(except FR-043)" (FR-041).
- Q (A10): Is the key replacement route Admin-only? → A: Yes, it stays Admin-only. FR-040 states that the Admin only runs a technical operation and decides nothing about the business; every new approval is made by a Manager.
- Q (A11): What if the bank returns money after `REFUNDED`? → A: In V1 `REFUNDED` is the final state. A late return after `REFUNDED` is handled outside the system, a Manager writes a note, and the system changes no state (FR-040).
- Q (A12): How is `branch_id` back-filled for existing transfer/VNPAY collections? → A: A one-off Java job calls the 003.1 facade `getTicketFolioContext` for each ticket; rows it cannot resolve stay NULL and are listed in the evidence; no SQL across modules (T133).
- Q (A15): Does 003.3 need a separate approval file or design pack? → A: No (option B). The owner approved the work already done, and there is no `approval.md` or separate design pack; this exception is recorded in `plan.md`, which also gets short step-by-step workflows for T137, T146, T167 and T170.
- Q (R02): The FR-056 list includes `BOOKING_TIME` and the 003.1 facade fails or times out, what does the Manager get? → A: Option A. When the filter includes `BOOKING_TIME` (explicitly or because no method is given) and the 003.1 operation fails or times out, the whole list is refused with a typed `503 RECONCILIATION_SOURCE_UNAVAILABLE`; no partial list is returned (FR-056, T133).
- Q (R03): One online payment covers several tickets, how many rows? → A: Option A. One row per payment: `ticketCode` is nullable and `ticketCodes[]` lists the tickets the payment covers; the example of 6,000,000 VND is one row (FR-056, T133, 003.1 T150).
- Q (R04): From when is `cashDayClosed` true? → A: Option A. From the moment the counted amount is submitted, so in both `PENDING_MANAGER_CONFIRMATION` and `CLOSED`, matching the FR-036 cut-off (FR-036, FR-053, T144).
- Q (R05): Who builds the cut-off note on `/settlement/cash-close`? → A: Option A. A new small FE task T172 (after T147) builds it; T164 includes it in the QA list.
- Q (R06): Does a `FREE`/deferred ticket cancelled by Mland trigger the group voucher restoration? → A: Option A. Yes: the restoration is triggered also when the buyer's answer, or the default, of such a ticket is recorded (no refund request needed); once per `groupCode` as before (FR-043, T142, T155).
- Q (R07): What if the bank returns money after `REFUNDED`? → A: Option A. It is handled outside the system; no field is added; a transition from `REFUNDED` is refused with `409` and changes no data (FR-040, T146).
- Q (R08): Do FR-040 key replacement, FR-036/FR-053 cash reversal and FR-034 auto-`FAILED` get success criteria? → A: Option A. SC-018, SC-019 and SC-020 are added and linked from T137, T144, T146, T147 and T149.

### Session 2026-10-31 — owner-approved refund execution (superseded 2026-11-12 by FR-038–FR-040: bank refunds, no VNPay Refund)

- Once an Mland-caused remedy is eligible, Settlement automatically initiates
  the policy-calculated refund; no second manual send
  step is required. Every original collected payment has at most one distinct
  refund operation. Calculate the aggregate refund once using the policy's
  whole-VND rounding, then allocate it oldest original payment first, capped at
  each original payment amount. VNPay full/partial request type follows whether
  that allocation equals the original payment amount.
- A provider acceptance response is not verified refund completion. A case and
  booking remain pending until signed, matching provider evidence confirms each
  nonzero refund operation. Unknown/time-out outcomes are reconciled and are
  never blindly resent.
- Operations owns authoritative cancellation facts and exposes a typed,
  PII-free eligibility projection containing earliest required-session start,
  check-in and no-show facts. Booking exposes only verified deposit payment
  facts. Settlement owns verified final-payment facts; paymentintegration
  supplies the verified provider transaction number and immutable original
  payment URL creation date.

### Session 2026-11-01 - direct deposit cancellation policy

- Direct Mland booking deposits are non-refundable. When the booking is eligible,
  a customer cancellation is handed to Operations, all future sessions/tickets
  are cancelled atomically, and the Settlement case becomes `RESOLVED` with
  `DIRECT_DEPOSIT_FORFEITED`; no refund allocation, VNPay refund, or VNPay
  QueryDR is created. Ineligible requests remain `REJECTED`.
- VNPay payment creation and signed payment/IPN evidence remain in scope for
  collecting the deposit. OTA cancellation/refund evidence is owned and
  reconciled by 003.4 outside this direct-booking policy.

### Session 2026-10-27

- Q: Is a Workshop Branch a separate authorization/resource identity from a
  Workshop Location? → A: No. Both terms refer to the same canonical physical
  resource identified by `workshop_location.id`. Every booking/session/catalogue
  scope and applicable audit location uses that identity directly, as do Account
  Management `activeLocationIds`. Public terminology is `location`/`locationId`;
  no separate branch ID, branch-to-location mapping, or local location copy is
  introduced.

### Session 2026-10-24

> The direct-deposit cancellation decision above supersedes the earlier refund-tier
> clarification in this section. The historical tier text is no longer an active
> direct-booking rule; refund tiers remain applicable only to approved Mland-caused
> remedy/provider flows.

### Session 2026-10-25

> The per-ticket model of 2026-11-12 and later (FR-030–FR-057) supersedes the
> answers in this section where they conflict: one folio per ticket, not per
> booking (FR-030); VNPay, cash or direct transfer collection (FR-031–FR-035);
> one customer change per ticket online or at the shop (FR-044, FR-045a);
> per-ticket Mland cancellation with 100% bank refund (FR-038–FR-043); no add-on
> after `FINALIZED` (FR-050). The historical answers are kept for history only.

- Q: What is the normal settlement point for a multi-session package? → A: One
  final bill applies to the whole booking/package. Staff/Manager finalizes
  assessed charges and collects final payment only after its final required
  session; release is permitted only after that bill is fully paid. Mland grants
  no default credit.
- Q: Can a customer reschedule a multi-session package online? → A: No. The
  customer contacts the shop. An authorized Manager may replace one or both
  future required sessions, only if the resulting complete package/session set
  remains valid and capacity is available for every replacement; the outcome is
  atomic.
- Q: What happens if Mland cancels a required session and a replacement is not
  possible or is declined? → A: Mland first offers an equivalent replacement.
  If that cannot be completed, it processes an audited refund of the whole
  booking/package; V1 has no session-scoped partial refund or credit/voucher.
- Q: What amount is refunded for an unresolved Mland-caused package
  cancellation? → A: Refund 100% of every successfully collected payment for
  the booking/package, including the deposit and any final payment, even if an
  earlier required session was attended.
- Q: When may a revised Mland-caused-cancellation refund policy apply? → A: The
  V1 default is 100% of every successfully collected booking/package payment.
  Mland may revise the policy for bookings confirmed after its effective time;
  each confirmed booking retains the policy snapshot accepted at confirmation.
- Q: How are additional material or service charges added? → A: Customers may
  view active catalogue add-ons, but only Staff/Manager records customer consent
  and confirms one at the workshop. A direct booking appends the immutable line to
  an OPEN final bill, or uses a separate direct-Mland add-on bill after the bill
  is FINALIZED (superseded 2026-11-05: no add-on after `FINALIZED`; correction
  uses the exceptional flow only). An OTA booking always uses a separate direct-Mland add-on bill
  and never changes OTA payment or payout facts. Customers never enter prices.
- Q: Which V1 channel provides accepted final-payment evidence? → A: VNPay only.
  A customer may pay at the counter through the VNPay link/QR, but settlement
  requires valid signed VNPay IPN or QueryDR evidence; browser return and a
  manually recorded counter payment do not settle the bill.
- Q: Which feature executes a required-session change? → A: 003.3 requests one
  booking-level change containing the complete replacement session set. 003.2,
  as capacity and ticket-entitlement owner, validates the complete result and
  applies all changes or none; it preserves the persistent ticket QR.
- Q: Who may revise the Mland-caused-cancellation refund policy? → A: Any
  active Manager may manage the policy globally. All Manager-authorized actions
  in Feature 003 use global location scope; booking snapshots remain immutable.

### Session 2026-10-26

> The per-ticket model of 2026-11-12 and later (FR-030–FR-057) supersedes the
> answers in this section where they conflict: booking-scoped final-bill, case
> and remedy routes are replaced by per-ticket routes (FR-048, T106, T123);
> whole-booking cancellation by per-ticket buyer cancellation (FR-041, FR-042);
> VNPay-only `FINAL_PAYMENT` attempts by folio collections (FR-031–FR-034,
> FR-049); "no Manager exception" by the handover exception and debt of FR-035;
> gateway refunds by bank refunds (FR-038–FR-040). The idempotency, error-status
> and IPN-ingress answers still apply. The historical answers are kept for
> history only.

- Q: How may a customer submit a whole-booking cancellation request? → A: An
  authenticated Member uses verified ownership. A Guest submits the booking code
  with the stored contact phone or email again at request time. Magic Link may
  lead to the cancellation screen but remains read-only and is not sufficient
  cancellation authority.
- Q: Which internal actor performs routine settlement? → A: Active Staff with
  applicable booking-location scope may create/confirm eligible charges and
  finalize a fully paid final bill. Manager-only authority remains required for
  required-session change, Mland-caused remedy/refund exception, and prospective
  refund-policy revision. No role can approve unpaid release or deferred payment.
- Q: At what REST scope are final bill, charge, and finalization exposed? → A:
  The one final bill is a booking-scoped resource. Its charges and finalization
  commands are exposed in the context of that final bill, not as a shared or
  ticket-scoped bill.
- Q: At what REST scope are Manager change, remedy, and policy-revision actions
  exposed? → A: Required-session change and Mland-caused remedy are
  booking-scoped commands. Prospective refund-policy revision is a separate
  central resource because it applies only to bookings confirmed after its
  effective time across Mland locations.
- Q: How do public state-changing REST commands handle retries and responses?
  → A: Each requires an `Idempotency-Key`; a same-key, same-content retry returns
  the original outcome. A response contains the current safe settlement
  projection, or a typed safe correlation-linked error, never unnecessary PII,
  token, QR payload, secret, or raw gateway payload.
- Q: How are settlement actions represented in public REST? → A: Settlement
  actions create or update an auditable business resource. Final bill and charge
  are resources; customer cancellation, required-session change, and
  Mland-caused remedy create a case with its own reference and lifecycle.
- Q: How does Settlement hand a verified settlement/refund outcome to Feature 007 without coupling money truth to
  email delivery? → A: In the same local transaction that accepts the Settlement
  business outcome, 003.3 creates an immutable notification handoff reference.
  After commit it publishes an at-least-once reference-only event. Feature 007
  deduplicates that reference, obtains the immutable delivery payload through
  the named Settlement facade, and owns provider send/retry logs. A handoff or
  provider failure never reverses final-bill, payment, refund, case, or Booking
  `FULLY_PAID` state.
- Q: Does an outstanding final balance create a credit period or permit a Manager
  exception? → A: No. The balance must be collected and verified during the
  settlement flow before package-output handover. There is no deferred-payment
  case, reminder, or reissue. Payment checkout transport and attempt expiry are
  owned by the approved `paymentintegration` contract and remain a separate
  dependency.
- Q: Which public REST routes and methods expose settlement resources? → A:
  Final bill uses `GET/POST /workshop-bookings/{bookingCode}/final-bill`; charge
  and finalization use `POST` under its final-bill context; customer cancellation,
  required-session change, and Mland remedy each use a booking-scoped `POST` case
  collection. Prospective policy uses
  `GET/POST /workshop-refund-policies`.
- Q: What minimum input opens a customer cancellation case? → A: Booking code is
  in the route. An authenticated Member sends no additional ownership proof; a
  Guest supplies exactly one stored contact phone or email. Neither may submit a
  refund amount, ticket/session selection, or Magic Link as cancellation authority.
- Q: What minimum input opens a required-session-change case? → A: Booking code
  is in the route; an authorized Manager supplies the complete target required-
  session reference set and a reason. It supplies no capacity seat count,
  ticket/QR, price, or partial replacement mapping; 003.2 validates/applies the
  complete result atomically.
- Q: What happens when a final bill has an outstanding balance? → A: The balance
  must be paid and verified before the workshop package output is handed over.
  No role may approve credit, defer collection, or bypass the zero-balance gate.
  Staff/Manager starts a separate `FINAL_PAYMENT` checkout for the exact
  outstanding VND amount; the customer pays through the resulting VNPay URL/QR.
  The payment attempt expires 10 minutes after creation. An expired attempt
  permits a new attempt only after QueryDR confirms that the previous attempt
  did not succeed. An unknown attempt blocks another attempt until reconciled.
  There is no credit period, reminder, or link-reissue workflow.
- Q: What minimum input opens a Mland-caused remedy case that proceeds to refund?
  → A: An authorized Manager supplies a reason and `replacementOutcome` of
  `UNAVAILABLE` or `DECLINED`. The server derives the retained policy snapshot
  and refund amount; it accepts no amount, ticket/session selection, or QR input.
- Q: What minimum input creates a prospective refund-policy revision? → A: The
  active Manager supplies only `effectiveAt` and
  `wholeBookingRefundPercent`. The server requires a future effective time and a
  valid percentage; the policy applies only prospectively and never rewrites an
  existing booking snapshot.
- Q: What lifecycle and safe projection do settlement cases use? → A: Every case
  uses `OPEN` then `RESOLVED`, `REJECTED`, or `FAILED`; cases awaiting verified
  refund evidence may additionally use `PENDING_PROVIDER`. No payment-deferral
  case or `EXPIRED` settlement-case state exists. The safe projection includes
  case reference, type, status, booking
  code and correlation reference; policy/amount outcome is included only for an
  authorized caller.
- Q: How do fixed add-on and variable charge flow? → A: Staff/Manager supplies
  only the add-on reference after customer consent at the workshop (the
  customer never submits it; see FR-004a/FR-004b); the server validates
  package/location and derives the immutable catalogue snapshot price. Only
  Staff/Manager supplies a variable description, amount, and reason. Customer
  booking and lookup routes are read-only for catalogue information.
- Q: How does VNPay evidence enter settlement? → A: HTTPS signed IPN is provider
  ingress and receives a VNPay acknowledgement; server-to-server QueryDR
  reconciles missing, late, or ambiguous IPN. Both match reference, amount, and
  currency and deduplicate by provider transaction/reference. Browser return is
  display-only and has no settlement route.
- Q: How are REST errors and client/provider retries distinguished? → A: Client
  commands use `Idempotency-Key`: same key/same content returns original result;
  changed reuse returns `409 IDEMPOTENCY_CONFLICT`. VNPay IPN/QueryDR deduplicates
  by verified provider transaction/reference and safely acknowledges duplicates.
  Errors use `400` malformed input, `401` invalid/missing authentication, `403`
  authority denial, `404` missing/not-visible booking, `409` conflict, and `422`
  valid input but business ineligibility.
- Q: Can a created settlement case be read again after a reload or retry? → A:
  Yes. Each booking-scoped case has a `caseReference` and a corresponding `GET`
  route beneath the same booking. The server returns only the safe case
  projection permitted to the requesting actor; it does not expose raw payment,
  token, QR, or unnecessary contact data.
- Q: May a customer read the final bill and case status for their own booking? →
  A: Yes. A Member proves authenticated ownership; a Guest proves possession of
  the booking code plus one stored contact phone or email on each read. The
  response is read-only and limited to the customer-safe final-bill/case
  projection, without raw gateway evidence or internal data.
- Q: How is an offered replacement linked to an Mland-caused cancellation and a
  possible remedy? → A: The Manager uses the existing required-session-change
  case with the complete replacement set and the 003.2
  `cancellationHandoffReference`. A remedy case for replacement `UNAVAILABLE` or
  `DECLINED` must reference that same handoff; a free-text reason alone is not
  sufficient.
- Q: Which public ingress accepts VNPay IPN in V1? → A: VNPay calls shared
  technical `paymentintegration` at `GET
  /api/v1/integrations/payments/vnpay/events` with its signed query parameters.
  After verification and idempotent processing, Mland responds with the VNPay
  JSON acknowledgement fields `RspCode` and `Message`. QueryDR remains an
  outbound Mland-to-VNPay reconciliation call; browser Return URL is display-only.
- Q: Which component owns an IPN shared by deposit and settlement flows? → A:
  Technical `paymentintegration` owns the one HTTPS `GET` ingress. It verifies
  the checksum once, deduplicates delivery, resolves the immutable
  owner-qualified merchant reference, and dispatches redacted verified evidence
  to 003.1 for `DEPOSIT` or 003.3 for `FINAL_PAYMENT`/`REFUND`. It never
  broadcasts, guesses an owner, or changes booking/settlement state itself.
- Q: Which refund percentage and VND rounding rule does a prospective policy
  revision use? → A: An active Manager supplies a whole-number percentage from
  `0` through `100`. The system computes the refund as
  `floor(collectedAmount × wholeBookingRefundPercent / 100)` whole VND.
- Q: Who may select a fixed-price catalogue add-on, and until when? → A: An
  Staff in the booking location scope or a global Manager may record the
  selection after customer consent at the workshop. Member and Guest routes may
  display the active catalogue but cannot create a financial charge; Magic Link
  remains read-only.
- Q: Who may read the central refund-policy revision resource? → A: Any active
  Manager may read all policy revisions. Guest/Member may
  read only the retained policy snapshot and refund outcome permitted for their
  own booking; they cannot read central revision history.

## Business Rules

- A confirmed direct booking freezes its post-promotion amount, deposit, and
  remaining amount. Deposit is `ceil(post_promotion_amount / 2)` whole VND and
  remaining amount is the exact difference. The one deposit applies to the one
  booking/package without allocating it across sessions or rewriting original
  payment evidence.
- The direct booking's balance due equals the folio total (frozen
  post-promotion amount plus accepted add-on/material/service charges) less the
  sum of verified collections. Replacing required sessions within the same
  package never changes the frozen package price.
- Customers may view active add-ons, but only Staff/Manager may confirm them at
  the workshop with customer-consent evidence. A direct booking appends the
  immutable line to its OPEN folio only; after `FINALIZED` no add-on may be
  added, changed, or removed, and a correction uses the separate exceptional
  flow. An OTA booking always uses a separate direct-Mland
  add-on bill isolated from OTA financial facts. Laser engraving is complimentary
  only for OTA-origin bookings under the OTA shop add-on policy. For a direct
  Mland booking, laser engraving is an ordinary catalogue add-on: its active
  Operations price is snapshotted and charged like any other direct add-on.
- Charge, payment, void, and adjustment records are immutable evidence. A
  correction is an authorized void or adjustment with audit, not an edit to
  historical evidence. A void/adjustment applies only while the folio is
  `OPEN`; after `FINALIZED`, only a Manager folio correction (FR-004c,
  overcharge refund) is allowed.
- The completed booking/package output is releasable only when its one final
  balance is fully paid through valid signed VNPay IPN or QueryDR evidence. No
  Manager or Staff exception may waive or defer collection. A booking is
  `FULLY_PAID` only when that final balance is settled;
  browser return and a manually recorded counter payment do not settle it.
  *(Superseded 2026-11-12 for direct bookings by FR-031–FR-035: a ticket folio
  is also paid by cash or direct transfer recorded by Staff/Manager, VNPay only
  by signed evidence, and a Manager may approve an audited handover exception
  before verification; the "VNPay-only settlement" and "no Manager exception"
  statements above are kept for history.)*
- A successful required-session change transfers the affected session
  entitlements and capacity commitments through 003.2 as one booking-level
  all-or-none result. It retains the booking deposit and preserves each
  participant's persistent ticket/QR across the booking's resulting required
  sessions. For Wax Casting, 003.2 MUST validate the complete resulting pair as
  same-location, distinct and chronologically ordered, with at least two
  Monday–Friday local dates strictly between them. Saturday/Sunday do not count;
  slot/time does not alter this calculation. The retired Silverclay same-day
  rule does not apply. *(Superseded 2026-11-12 by FR-044/FR-045: a change is
  per ticket, all or none for the chosen tickets, one counted customer change
  per ticket; the 003.2 pair-validation and QR-preservation rules still apply
  per ticket.)*
- A whole-booking refund revokes unused tickets and releases future capacity only
  after the gateway confirms the refund. A failed refund does not restore the
  booking for use. *(Superseded 2026-11-12 by FR-038–FR-040 and FR-043: refunds
  are per ticket by bank transfer, never through the gateway; consequences
  apply only after a Manager confirms `REFUNDED`.)*
- When Mland cancels a required session, it MUST offer an equivalent replacement
  first. If the replacement is unavailable or declined, its remedy is an audited
  refund governed by the booking's Mland-caused-cancellation policy snapshot.
  The V1 default is 100% of every successfully collected booking/package
  payment; V1 does not allocate a partial refund or credit to one session.
  *(Superseded 2026-11-12 by FR-043: only tickets holding the cancelled session
  are affected, each refunded 100% of the money collected for that ticket by
  bank (FR-038–FR-040); the replacement-first rule stands.)*
- Any active Manager may administer prospective Mland-caused-cancellation refund
  policy changes globally and resolve individual cases. Booking policy snapshots
  remain immutable. *(Refund-policy administration superseded 2026-11-15 by
  FR-043: a Mland cancellation of a ticket always refunds 100%; the policy, its
  snapshots and the `/workshop-refund-policies` routes are removed by T139/T140.
  Resolving individual remedy cases stands per FR-043.)*
- OTA customer payments, refunds, commission, payout, and reconciliation are
  outside this feature. The separate Mland shop add-on bill of an OTA booking
  is the one OTA-related item 003.3 owns (D12); it never changes OTA facts.

## Confirmed Decisions

### Session 2026-10-24

- Final balance collection occurs during settlement and must be verified before
  package-output handover. This feature defines no credit/deferred-payment
  exception, payment reminder, or payment reissue. Deposit/payment-hold links
  and their associated holds continue to share one absolute deadline of at most
  10 minutes from hold creation. Final-payment checkout uses a separate
  `FINAL_PAYMENT` attempt for the server-derived outstanding VND balance and
  expires 10 minutes after attempt creation. A new attempt is allowed only after
  the prior attempt is confirmed unsuccessful by QueryDR; an unknown outcome
  blocks another attempt. A successful verified payment is the only evidence
  that reduces the outstanding balance. This does not create a credit period,
  reminder, or link-reissue workflow. *(Superseded 2026-11-12 for direct
  bookings by FR-031–FR-035 and FR-049: the balance is collected per ticket by
  VNPay, cash or direct transfer, the booking-wide `FINAL_PAYMENT` attempt is
  replaced by a `VNPAY` folio collection, and a Manager may approve an audited
  handover exception or record a debt.)*
- The system MUST NOT infer `Manager = OWNER`. Account Management supplies the
  canonical fixed role, current status and active location-scope facts; 003.3
  applies its own server-side Settlement action rules. Active Staff may perform
  routine charge/final-bill actions only within active location scope; any active
  Manager globally may perform required-session changes and refund exceptions/
  Mland-caused remedy. No role may waive or defer a final-balance requirement.
  *(Superseded 2026-11-12 by FR-035: a Manager may approve an audited handover
  exception before verification and record or close a debt, both with a
  reason; the balance itself is still never waived.)*
  An inactive actor loses these authorities immediately. Display role labels,
  Firebase claims and retired role codes never grant authority.
- Any active Manager may use the global Mland-caused-cancellation refund-policy
  editor. This does not introduce a role code or alter the Account Management
  role mapping; the feature checks active authorization and records the policy's
  effective time for future bookings. *(Superseded 2026-11-15 by FR-043: the
  refund-policy editor is removed by T139/T140; a Mland cancellation always
  refunds 100%.)*

## User Scenarios and Testing

### User Story 1 — Staff settles a completed booking/package (P1)

After a booking's final required session is completed, Staff needs to finalize
charges, present the final bill for VNPay payment, and determine whether the
package output can be released.

**Independent Test**: With a confirmed direct booking whose final required
session has finished, Staff can finalize charges and record one valid payment.
The package output becomes releasable only when its balance is zero;
failed/duplicate evidence changes nothing.

1. **Given** an eligible completed booking/package, **When** valid final payment is
   confirmed, **Then** immutable bill/payment evidence is recorded and release is
   permitted.
2. **Given** an outstanding balance, **When** any actor attempts finalization
   or package-output release, **Then** the action is rejected until matching
   verified payment evidence reduces the balance to zero.
3. **Given** an OPEN folio, **When** Staff finds an error, **Then** only an
   audited void/adjustment corrects it; original evidence remains. **Given** a
   `FINALIZED` folio, **When** an overcharge is found, **Then** only an active
   Manager may open a folio correction that refunds it (FR-004c); an
   undercharge is absorbed by Mland.
4. **Given** a permitted fixed-price catalogue add-on, **When** Staff records
   customer consent at the workshop, **Then** an eligible direct booking gets an
   immutable line in its OPEN folio (a `FINALIZED` folio rejects the add-on;
   only the exceptional correction flow applies); an OTA booking gets only a separate direct-Mland bill and OTA facts remain
   unchanged. A variable charge appears only after Staff/Manager confirmation.

### User Story 2 — Customer cancels a whole booking (P1)

Guest or Member needs a clear direct-booking cancellation/refund result without
being offered a session/ticket-level refund.

**Independent Test**: An eligible unstarted booking receives one whole-booking
request, snapshots the non-refundable policy result, and Operations cancels all
future sessions/tickets atomically.

1. **Given** all tickets are unused and every required session is in the future,
   **When** the customer requests cancellation, **Then** Operations cancels the
   whole booking, the case becomes `RESOLVED`, and the paid deposit is retained.
2. **Given** a session has started, a ticket was checked in/no-show, or Operations
   rejects the command, **Then** the case remains/reverts to `REJECTED` and no
   refund or QueryDR call is made.

### User Story 3 — Manager changes future required sessions (P2)

Manager needs to replace one or both future required sessions without leaving a
multi-session package invalid or overbooking a target session.

**Independent Test**: Compatible replacement sessions with capacity update all
affected session entitlements and capacity atomically; the persistent ticket QR
continues to work for every resulting entitled session.

1. **Given** a booking with eligible future required sessions and compatible
   replacement capacity, **When** Manager approves a one- or two-session change,
   **Then** ticket entitlement, capacity, deposit, frozen package price, and audit outcomes
   succeed as one result.
2. **Given** insufficient/incompatible target capacity, **Then** no session,
   ticket, or bill change persists.

### User Story 4 — Manager remedies Mland-caused package disruption (P2)

When Mland cannot provide a required session, Manager needs an audited package
remedy that does not invent a separate per-session financial unit.

**Independent Test**: After replacement is unavailable or declined, Manager
creates a whole-booking refund; tickets/capacity change only after its verified
outcome.

*(User Stories 1–4 describe the whole-booking model and are superseded for
direct bookings by the per-ticket model of FR-030–FR-051 (decisions
2026-11-12 to 2026-11-14). They are kept for history. The per-ticket stories
below restate FR-030–FR-051 only and add no rule; acceptance is verified by
T103.)*

### User Story 5 — Staff collects a ticket's balance at the counter (P1, per ticket)

FR-030–FR-035, FR-037. **Independent Test**: a ticket folio with a balance is
settled by mixed collections and finalizes only at zero.

1. **Given** an OPEN ticket folio with a balance, **When** Staff records
   collections by VNPay, cash and direct transfer, **Then** each collection
   belongs to that ticket folio only, and a `PENDING_VERIFICATION` collection
   does not reduce the balance (FR-031, FR-032).
2. **Given** a split that would leave a VNPay part under 5,000 VND, **When**
   Staff tries to collect it by VNPay, **Then** it is refused and the screen
   says so before collecting; the amount is never raised, waived or hidden
   (FR-033).
3. **Given** a pending direct transfer, **When** an actor without
   `workshop.payment.transfer.confirm` (V1: anyone but a Manager) confirms it,
   **Then** it is refused; **When** a Manager confirms it after seeing the
   money, **Then** the balance falls (FR-032).
4. **Given** a bank transaction or cash confirmation already recorded,
   **When** it is recorded again, **Then** it is refused; **Given** money above
   the balance, **Then** it is recorded in full and routed to a Manager refund,
   never finalizing or earning twice (FR-034).
5. **Given** a balance of 0 and no pending collection, **Then** the folio is
   `FINALIZED` and the product may be handed over; **Given** an unverified
   part, **Then** the product is handed over only by a Manager's audited
   exception with a reason (FR-035).

### User Story 6 — A branch closes its cash book for the day (P2)

FR-036. **Independent Test**: a branch with confirmed cash closes once per
Vietnam working day.

1. **Given** confirmed cash for the branch and day, **When** the closer enters
   a counted amount that differs, **Then** a reason is required and the close
   waits for Manager confirmation.
2. **Given** a closed day, **When** cash is collected afterwards, **Then** it
   belongs to the next day; confirmed cash of the closed day never changes,
   and a Manager folio correction refunds by bank without touching it
   (FR-036, FR-050).

### User Story 7 — Manager refunds Mland money by bank (P1)

FR-038–FR-040. **Independent Test**: a refund request moves through its
lifecycle and has business effects only after `REFUNDED`.

1. **Given** a refund request in `PENDING_ACCOUNT`, **When** the payer submits
   the account through the single-use link (valid 7 days) or Staff enters it
   at the counter, **Then** the request moves on and the account number is
   stored encrypted and shown masked (`****1234`) except to the approving
   Manager and the transferring actor (FR-040).
2. **Given** a request, **When** it is approved, transferred and confirmed
   `REFUNDED` by a Manager, **Then** every step records actor, time and proof,
   and the folio and booking consequences apply only after `REFUNDED`
   (FR-039).
3. *(Withdrawn 2026-11-15 by Hồ Hoàng Quốc: VNPay Refund and refund-QueryDR
   are no longer used and their code is removed, so no legacy code-`99`
   refund can block a manual refund; FR-039.)* ~~**Given** a legacy VNPay
   refund with code `99` for the same money, **Then** no manual refund starts
   before it is reconciled (FR-039).~~
4. **Given** no reply, **Then** the request stays `PENDING_ACCOUNT` with
   reminders on days 3 and 7, and after 30 days a Manager may close it as
   `CLOSED_UNREACHABLE` and reopen it later (FR-040).

### User Story 8 — The buyer cancels chosen tickets (P1)

FR-041, FR-042. **Independent Test**: tickets are cancelled per ticket with the
24-hour benefit rule.

1. **Given** unused tickets whose first session has not started, **When** the
   proven buyer cancels one or several tickets of a group, **Then** each is
   cancelled with its own 24-hour outcome, capacity is released and no other
   ticket changes; a request for several is all or none (FR-041).
2. **Given** a request made at least 24 hours before the first session,
   **Then** it is a valid cancellation and used points return; **Given** less
   than 24 hours, **Then** capacity is released but no points return
   (FR-042).
3. **Given** a recipient, or a Guest without a valid buyer proof, **When**
   they cancel, **Then** it is refused (FR-041).
4. **Given** every ticket of a group ended by valid or Mland cancellation,
   **Then** the group's voucher use is restored once; any late cancellation or
   no-show prevents it (FR-042), unless a Mland cancellation already restored
   it (FR-043, clarified 2026-11-15).

### User Story 9 — Mland cancels a session of some tickets (P2)

FR-043. **Independent Test**: only tickets with the cancelled session are
affected.

1. **Given** a Mland-cancelled session, **When** the buyer declines the
   replacement or does not answer by `answerDeadlineAt` (24 hours before the
   latest offered session; a session is choosable only while it starts at
   least 24 hours after the answer, aligned 2026-11-15),
   **Then** 100% of the money collected for each affected ticket is refunded by
   bank and its used points return.
2. **Given** only part of a group is affected, **Then** the voucher use is
   restored once and never a second time.

### User Story 10 — The customer changes a ticket's sessions (P2)

FR-044, FR-045. **Independent Test**: one counted change per ticket, all or
none, with a buyer-confirmed price effect.

1. **Given** a ticket that has used its change, **When** another change is
   requested without a Manager-granted exception, **Then** it is refused, or
   queued for a Manager per FR-045a (202);
   moving session 1 alone uses the allowance (FR-044).
2. **Given** a change that fails, or a retry of a successful change, **Then**
   no additional allowance is consumed (FR-044).
3. **Given** a change that raises the price, **When** the buyer has not
   confirmed the increase, **Then** nothing changes; once confirmed, the
   increase is added to the ticket's checkout balance and no price ever
   falls (FR-045).
4. **Given** a two-session ticket whose session 1 starts today and whose
   session 2 starts more than 24 hours from now, **When** the buyer changes
   session 2 only, **Then** the 24-hour advance window is measured from session
   2, the session being changed, and the change is not blocked by session 1
   (when one change moves both sessions, from the earlier of them) (FR-044,
   clarified 2026-11-15; the code follows in T130).

### User Story 11 — A refund account is replaced or returned for re-entry (P2)

FR-040, FR-039. **Independent Test**: a failed transfer or a replaced
encryption key sends a refund request back to `PENDING_ACCOUNT` without losing
the customer's refund right. *(Scenarios written 2026-11-16, task T143, fix A19,
from the decisions of Session 2026-11-15.)*

1. **Given** a request in `PENDING_APPROVAL`, `PENDING_TRANSFER` or
   `PENDING_RECONCILIATION`, **When** the bank returns the transfer, the
   statement does not match or the Manager refuses, **Then** the request returns
   to `PENDING_ACCOUNT` with the reason recorded, the encrypted number is deleted
   (the mask is kept), the earlier approval is void, the old link is revoked, a
   new account link is sent and a Manager must approve again; there is no
   terminal rejection state (FR-040).
2. **Given** the key `WORKSHOP_REFUND_ACCOUNT_KEY` was replaced and the service
   restarted, **When** an Admin calls `POST /workshop-refund-account-key-replacements`
   with a reason and an Idempotency-Key, **Then** every request still holding a
   ciphertext loses it (the mask stays), returns to `PENDING_ACCOUNT`, loses its
   Manager approval, gets a new link and one audit record, and no number is
   returned or logged; a Manager or Staff caller gets `403` (FR-040, T137).
3. **Given** the same call is repeated, **Then** it touches only requests that
   still hold ciphertext and changes nothing else (idempotent rerun, one
   transaction per request).

### User Story 12 — Manager reconciles transfers and VNPay and records an absorbed undercharge (P2)

FR-056, FR-057. **Independent Test**: the read-only reconciliation list matches
the bank statement and VNPay Merchant Admin, and an undercharge is documented
without changing money. *(Scenarios written 2026-11-16, task T143, fix A19.)*

1. **Given** a branch and a Vietnam working day, **When** a Manager lists
   `BANK_TRANSFER`, `VNPAY` and `BOOKING_TIME` collections, **Then** `CONFIRMED`,
   `PENDING_VERIFICATION`, `VOIDED` and `EXCESS` rows appear with ticket code,
   group code, amount, method, state, reference and confirming actor and time; a
   `FAILED` collection is not listed; a Staff caller gets `403` and nothing is
   written (FR-056, SC-013).
2. **Given** a transfer that exceeds the balance or arrives twice, **Then** it
   is one `CONFIRMED` row with the full amount plus `excessAmount` and
   `refundRequestReference`; VNPay money that settled no part is an `EXCESS` row
   of the working day of its evidence time (FR-034, FR-056).
3. **Given** a `FINALIZED` ticket folio, **When** a Manager records an absorbed
   undercharge (amount above 0 and within the folio total, reason,
   Idempotency-Key), **Then** the record is saved and the folio, lines, balance,
   Loyalty earn and cash are unchanged; on a folio that is not `FINALIZED` the
   answer is `409 FOLIO_NOT_FINALIZED` and above the total `422` (FR-057).
4. **Given** a saved record, **When** a Manager voids it once with a reason,
   **Then** it keeps its content, leaves the total, and a second void is refused
   (FR-057, SC-014).
5. **Given** online booking-time VNPay money of 6,000,000 VND with a successful
   IPN and a counter collection of 1,200,000 VND on the same day, **When** a
   Manager lists the day, **Then** the confirmed total is 7,200,000 VND; a
   booking-time row not yet verified shows the requested amount with state
   `PENDING_VERIFICATION` and is not in the confirmed total (FR-056, A04,
   SC-013).

### User Story 13 — Cash is corrected on a closed day and an unreconciled VNPay part fails by itself (P2)

FR-036, FR-053, FR-034. **Independent Test**: a closed cash day is never edited
and a VNPay part never stays pending for ever. *(Scenarios written 2026-11-16,
task T143, fix A19.)*

1. **Given** a `CONFIRMED` cash collection of a closed day on an `OPEN` folio
   with no refund request for that money, **When** a Manager records the cash
   reversal with a reason and an Idempotency-Key, **Then** a negative entry is
   dated the current working day, the closed day is unchanged and the ticket
   balance is recomputed (FR-036, FR-053).
2. **Given** a `FINALIZED` or `CLOSED` folio, money under a refund request, a
   non-cash or already voided collection or a day not yet closed, **Then** the
   reversal is refused with the typed code and a Staff caller gets `403`
   (FR-036).
3. **Given** cash recorded after the counted amount was submitted, **Then** it
   belongs to the next working day even while a difference awaits Manager
   confirmation (FR-036, T147).
4. **Given** a `PENDING_VERIFICATION` VNPay part 24 hours after its attempt was
   created and QueryDR reports no successful payment, **Then** the system marks
   it `FAILED` as a system action with an audit record so the balance can be paid
   another way; a payment that arrives later is recorded in full and routed to
   the Manager refund as excess or duplicate money (FR-034, T149).

## Functional Requirements

- **FR-001**: The system MUST apply this feature only to direct Mland workshop
  bookings and MUST exclude OTA payment/refund/payout/reconciliation. The single
  exception (D12) is the separate Mland shop add-on bill of an OTA booking:
  003.3 creates it from 003.4's accepted quote and consent and collects it
  through `SHOP_ADDON` evidence, without touching any OTA channel fact.
- **FR-001a**: When a direct folio becomes `FINALIZED` or `CLOSED`, Settlement
  MUST send 003.1 one idempotent `RecordBookingServiceOutcomeCommand` (D13):
  `COMPLETED` for `FINALIZED`; `CANCELLED` for closure by customer cancellation
  or Mland remedy; `NO_SHOW` for closure by full no-show. For a Mland-caused
  remedy it MUST also send `RecordBookingRefundStatusCommand` with
  `REFUND_PENDING` when the first refund allocation is dispatched and
  `REFUNDED` when every allocation is verified (D14). Settlement MUST NOT
  change Booking state directly, and a post-`FINALIZED` overcharge correction
  sends neither command. *(Superseded 2026-11-12 for direct Mland money by the
  per-ticket bank refund lifecycle of FR-038–FR-040: "dispatched" now means
  handed to a bank refund request and "verified" means a Manager confirmed
  that request `REFUNDED`; business consequences apply only at `REFUNDED`. No
  gateway refund allocation exists any more.)*
- **FR-002**: The system MUST authorize protected actions on the server using
  active Mland account status, canonical role, and feature-owned location and
  resource action rule;
  client/provider claims and display labels MUST NOT grant authority.
- **FR-002a** *(partly superseded 2026-11-12 by FR-035, like FR-007: "No role
  may authorize unpaid release or deferred collection" no longer holds, because
  a Manager may approve an audited handover exception before verification and
  record a real debt (`Còn nợ`) with a reason; Staff access also follows the
  2026-11-06 assignment rule)*: Active Staff with booking-location scope MAY create or confirm an
  eligible charge and finalize a fully paid final bill. Required-session change,
  Mland-caused remedy/refund exception, and prospective refund-policy revision
  *(the policy-revision clause superseded 2026-11-15: the Mland-cancellation
  refund policy and its routes are removed by T139/T140; FR-043 always refunds
  100%)* remain Manager-only actions under their applicable authority. No role may
  authorize unpaid release or deferred collection.
- **FR-003**: The system MUST retain immutable, redacted audit evidence for
  settlement, reschedule, refund, void,
  adjustment, and Mland-caused remedy.
- **FR-004**: The system MUST preserve immutable charge, payment, void, and
  adjustment records for each final bill. Void/adjustment records are created
  only while the folio is `OPEN`; post-`FINALIZED` corrections follow FR-004c.
- **FR-004a**: The system MUST accept a Staff/Manager-confirmed charge only for a
  fixed-price catalogue add-on after customer consent at the workshop. Customer
  routes MAY display eligible add-ons but MUST NOT create or select bill lines.
  The
  server MUST derive the price from the package/location catalogue snapshot and
  MUST NOT accept customer-supplied financial mutation. A direct booking appends
  only to its OPEN folio and MUST reject an add-on after `FINALIZED`; an OTA
  booking always uses a separate direct-Mland bill isolated from OTA facts.
- **FR-004b** *(superseded 2026-11-15: the booking-scoped route this requires was removed (T106, T121, T123, T124); replaced by per-ticket `POST /workshop-tickets/{ticketCode}/folio/charges` (FR-048))*: `POST /workshop-bookings/{bookingCode}/final-bill/charges` MUST
  accept `catalogueAddOnReference` only from an active Staff scoped to the
  booking location or a global Manager, after workshop consent, and MUST derive
  the accepted price from the permitted package/location catalogue snapshot.
  Member/Guest access is read-only and cannot mutate charges. A direct booking
  appends to its OPEN folio; a `FINALIZED` direct folio MUST reject the add-on
  without mutation, and any later correction uses only the exceptional flow
  (T040). An OTA booking always requires a separate
  direct-Mland add-on bill isolated from OTA payment/payout facts; only that OTA
  flow treats laser engraving as zero-charge. A direct Mland booking charges
  laser engraving from the active catalogue like any other add-on. A variable charge MUST be
  submitted only by Staff/Manager with description, amount, and reason; that
  audited command confirms it. No customer variable-charge command exists.
- **FR-004c** *(superseded 2026-11-12 by FR-038 (bank refund))*: A correction to a `FINALIZED` direct folio (owner decisions
  D7–D10, 2026-11-05) MUST be a separate, immutable, audited folio-correction
  record linked to the folio; it MUST NOT add, edit or remove folio lines or
  totals. Only an active Manager MAY open and approve it, with a required
  reason; Staff, Member and Guest MUST NOT. A correction covers only an
  overcharge, with a whole-VND amount not exceeding the folio's total verified
  unrefunded collections. It MUST be returned by VNPay partial refund through
  the `paymentintegration` refund adapter, allocated oldest verified payment
  first and capped by each payment's unrefunded amount (the same allocation
  rule as the Mland-caused remedy), and is complete only after signed matching
  QueryDR evidence; refund evidence uses the existing `REFUND` kind. An
  undercharge found after `FINALIZED` MUST NOT be collected: Mland absorbs it,
  and no deferred-payment, credit or reminder is created. Commands are
  idempotent. A correction MUST NOT change the folio state, booking
  financial status, Loyalty earn, ticket or capacity. A `CLOSED` folio and OTA
  bookings are out of scope. A new purchase after checkout is not a
  correction.
- **FR-005** *(superseded 2026-11-12 by FR-030: the balance is calculated per
  ticket folio, base = the ticket's final price, first collection = the
  ticket's booking-time collection; the frozen-price rule stands per ticket,
  FR-045)*: The system MUST calculate one booking/package balance due from the
  OPEN folio as `totalAmount − paidAmount`, where `totalAmount` is the frozen
  post-promotion amount plus approved charges and `paidAmount` is the sum of
  append-only verified collections (including the 003.1 deposit). It MUST NOT
  recalculate a frozen voucher, point, post-promotion amount, deposit, or
  remaining amount. A required-session change within that package MUST NOT alter
  its frozen package price.
- **FR-006** *(superseded 2026-11-15 by FR-035: a ticket folio finalizes when
  `balanceDue = 0` and no collection is pending; completion of the final
  session or attendance is not a finalize condition, clarified 2026-11-15)*: The system MUST allow charge finalization only after the booking's
  final required session is completed, recording authorizing actor and time.
  The folio itself already exists from deposit confirmation (D1); finalization
  moves it from `OPEN` to `FINALIZED` only when `balanceDue = 0`.
- **FR-006a** *(superseded 2026-11-15: the booking-scoped route this requires was removed (T106, T121, T123, T124); replaced by the per-ticket folio under `/workshop-tickets/{ticketCode}/folio` (FR-030, FR-048); a folio is now ticket-scoped)*: The public REST contract MUST expose the one final bill in its
  booking context. Charge creation/confirmation and finalization commands MUST
  operate in that final-bill context; no final bill is ticket-scoped or shared
  by multiple bookings.
- **FR-006b** *(superseded 2026-11-15: the booking-scoped route this requires was removed (T106, T121, T123, T124); replaced by `GET /workshop-tickets/{ticketCode}/folio` and the Guest `POST …/folio-lookups` (FR-048))*: `GET /workshop-bookings/{bookingCode}/final-bill` MUST disclose a
  customer-safe, read-only final-bill projection to an authenticated Member
  with booking ownership or a Guest who supplies the booking code and exactly one
  stored contact phone or email for that request. The projection MAY include the
  customer-visible charge lines, due/paid/refunded amounts, and bill status, but
  MUST exclude raw gateway evidence and internal-only data.
- **FR-007** *(superseded 2026-11-12 by FR-035)*: The system MUST prevent final-bill finalization and package-output
  release until the complete final balance has matching verified payment
  evidence. No Manager exception, credit, or deferred collection is permitted.
- **FR-008**: Settlement MUST send 003.1 the `RecordFinalBalanceResolvedCommand`
  conclusion only when the folio's final balance is settled through matching
  verified payment evidence; 003.1 alone then changes the booking to
  `FULLY_PAID`. Settlement MUST NOT change Booking state directly.
  *(Per ticket, decided 2026-11-14 by Hồ Hoàng Quốc, BE-DESIGN-0006: for a ticket folio the conclusion is `recordTicketFinalBalanceResolved(ticketCode, …)`, sent when the ticket folio becomes `FINALIZED`; 003.1 then sets that ticket's `financialStatus` to `FULLY_PAID`. See `../contracts/workshop-interfeature-facades.md`, "Per-ticket boundary changes".)*
- **FR-009** *(superseded 2026-11-12 by FR-031–FR-034 (counter collection))*: The system MUST accept final-payment evidence only from valid
  signed VNPay IPN or QueryDR, matched to the final bill's reference, amount and
  currency. It MUST process that evidence idempotently and reject invalid,
  mismatched, unsigned, duplicate, late, browser-return-only, or manually
  recorded counter-payment evidence without settling twice.
- **FR-009a**: Signed VNPay IPN MUST be HTTPS provider ingress with a
  VNPay-compatible acknowledgement. Server-to-server QueryDR MAY reconcile a
  missing, late, or ambiguous IPN. Both sources MUST match reference, amount,
  and currency and deduplicate by provider transaction/reference; browser return
  MUST remain display-only and MUST NOT have a settlement-confirmation route.
- **FR-009b**: V1 VNPay IPN ingress MUST be `GET`
  `/api/v1/integrations/payments/vnpay/events` and accept only signed provider
  query parameters. The technical `paymentintegration` boundary owns this route:
  it verifies and deduplicates once, then routes redacted evidence by immutable
  owner-qualified merchant reference to exactly one business owner. Following
  processing, it returns the VNPay JSON acknowledgement fields `RspCode` and
  `Message`. QueryDR is an outbound server-to-server reconciliation call and has
  no inbound public route.
- **FR-009c**: 003.3 MUST consume redacted verified `FINAL_PAYMENT`, `REFUND`,
  or (for an OTA booking's separate Mland shop add-on bill only) `SHOP_ADDON`
  evidence only through `WorkshopSettlementFacade`; it MUST NOT expose or own a
  VNPay IPN HTTP route, raw callback payload, checksum verification, or routing
  of deposit evidence. *(2026-11-15: `REFUND` evidence is legacy and no longer
  produced, because VNPay Refund and refund-QueryDR are not used and their code
  was removed (bc39429c, FR-038); `FINAL_PAYMENT` and `SHOP_ADDON` evidence
  still apply.)*
- **FR-010** *(superseded 2026-11-12 by FR-035)*: Settlement MUST require the final balance to be collected and
  verified before handing the workshop package output to the customer. It MUST
  NOT create credit, deferred-payment cases, reminders, or payment reissues.
- **FR-011** *(superseded 2026-11-12 by FR-031–FR-034)*: Staff/Manager MUST be able to start a separate `FINAL_PAYMENT`
  checkout for the server-derived outstanding VND balance through
  `paymentintegration`. The resulting attempt MUST be idempotently bound to its
  booking, merchant reference, amount, and VND currency, and MUST expire 10
  minutes after creation. A new attempt MUST NOT be created while an earlier
  attempt is pending or unknown; after expiry it is allowed only when QueryDR
  confirms the earlier attempt did not succeed. Only signed verified payment
  evidence may reduce the balance. A pending, failed, unknown, or expired
  attempt MUST leave the final bill unpaid and package output withheld. No
  reminder or payment-link reissue is provided.
- **FR-011a**: A final-payment UI command with browser/network timeout, lost
  response, or unverified provider outcome MUST be represented as `UNKNOWN`.
  It MUST retain and reuse the original `Idempotency-Key` solely for an
  explicit same-command retry or server-backed status check. It MUST NOT
  automatically retry, generate another payment attempt, create a new payment
  URL, or release package output. `EXPIRED` remains unpaid and requires
  server-side QueryDR reconciliation before any later attempt is permitted by
  the existing contract. Browser-return parameters are display-only and cannot
  transition a bill, booking, or output-handover state.
- **FR-012**: Deposit/payment-hold links and their associated capacity holds
  MUST continue to share one absolute deadline of at most 10 minutes from hold
  creation. Final-payment attempts have a separate 10-minute provider URL expiry
  from attempt creation and do not hold workshop capacity.
- **FR-012a**: When a customer-facing verified settlement/refund outcome is accepted,
  003.3 MUST persist one unique immutable settlement notification handoff in
  the same local transaction and publish its reference only after commit. The
  event MUST contain only notification reference, settlement/bill or case
  reference, notification kind, and correlation reference; it MUST NOT contain
  recipient address, payment-link value, raw gateway evidence, ticket/QR data,
  or unnecessary PII. Feature 007 obtains the immutable render-ready payload
  only through the approved 003.3 notification-delivery facade and owns
  idempotent provider delivery/retry and operational logs. The stored
  recipient/rendered content MUST be retained for at most 30 days after
  handoff. After 30 days the delivery payload MUST no longer be retrievable.
  *(clarified 2026-11-15, checklist CHK030)* The payload of a refund-account
  email carries the account link token only while that link is usable: the token
  is scrubbed from the stored payload when the link is used, revoked by a resend
  or expired (FR-040); the rest of the payload keeps the 30-day retention.
  Delivery failure MUST NOT reverse the accepted Settlement outcome.
- **FR-013** *(superseded 2026-11-12 by FR-041)*: Guest/authorized Member cancellation MUST be whole-booking only;
  session/ticket/participant self-service cancellation/refund is prohibited. An
  authenticated Member MUST pass ownership verification; a Guest MUST provide
  the booking code and stored contact phone or email at cancellation-request
  time. Magic Link is read-only and MUST NOT alone authorize cancellation.
- **FR-013a** *(superseded 2026-11-15: the booking-scoped route this requires was removed (T106, T121, T123, T124); replaced by `POST /workshop-tickets/{ticketCode}/cancellations` and `POST /workshop-booking-groups/{groupCode}/cancellations` (FR-041))*: `POST /workshop-bookings/{bookingCode}/cancellation-cases` MUST
  accept no customer-selected financial or ticket/session input. An authenticated
  Member requires no additional ownership field; a Guest MUST provide exactly one
  stored contact phone or email as request-time proof.
- **FR-013b** *(superseded 2026-11-15: the booking-scoped route this requires was removed (T106, T121, T123, T124); replaced by the per-ticket cancellation routes of FR-041 (each ticket cancelled once, idempotent per request key))*: A booking MUST have at most one customer-cancellation case. A
  same-key request with the same semantic input MUST replay the existing safe
  outcome; any new key after a case exists MUST NOT create another case or refund
  operation and MUST return a conflict.
- **FR-014** *(superseded 2026-11-12 by FR-041)*: Normal customer cancellation MUST require all tickets unused, no
  required session started, and policy eligibility. The workshop product is
  handed to the customer only after their workshop session; therefore the
  no-session-started requirement also ensures no workshop product has already
  been handed over. No separate package-output delivery check is required for
  customer cancellation.
- **FR-015** *(superseded 2026-11-12 by FR-041)*: Direct customer cancellation MUST NOT calculate or request a refund.
  If all required sessions are future and all tickets are unused, Settlement MUST
  hand the booking to Operations for an idempotent whole-booking cancellation;
  the case is `RESOLVED` only after Operations accepts, and the paid deposit is
  retained. An Operations rejection leaves the case `REJECTED`.
- **FR-015a** *(superseded 2026-11-12 by FR-041 and FR-030)*: After an accepted ordinary direct cancellation or an
  Operations-final no-show, Settlement MUST close the direct folio at the
  verified collected amount and set it to `CLOSED`. It MUST NOT refund the
  direct deposit,
  invoke VNPay QueryDR/refund, or create a receivable for the uncollected
  remainder. Only an approved Mland-caused remedy/provider flow or a Manager folio correction (FR-004c, overcharge) may refund.
  A booking is an Operations-final no-show only when every ticket on every
  required session has a final `NO_SHOW`; partial attendance is not a no-show
  and proceeds to normal checkout for the full package price plus accepted
  add-ons.
- **FR-016** *(superseded 2026-11-12 by FR-042)*: The case MUST snapshot policy, earliest-session reference, paid
  amount, computed amount, request time, and eligibility outcome. A replay MUST
  return that snapshot; a conflicting new key MUST NOT create another snapshot.
- **FR-017** *(superseded 2026-11-12 by FR-038)*: Direct customer cancellation MUST never create a `PENDING_PROVIDER`
  case and MUST never invoke VNPay refund or QueryDR. Provider refund states are
  limited to approved Mland-caused remedy/provider flows and Manager folio corrections (FR-004c).
- **FR-018** *(superseded 2026-11-12 by FR-044)*: An authorized Manager MAY replace one or more future required
  sessions only with compatible sessions that have capacity for the booking's
  unchanged participant count. For a multi-session package, the complete
  resulting session set MUST remain valid; Wax Casting's resulting pair MUST be
  same-location, distinct and chronologically ordered, with at least two
  Monday–Friday local dates strictly between them (Saturday/Sunday excluded).
  Settlement must not substitute the retired Silverclay same-day rule. Feature 003.3 MUST request the complete
  booking-level replacement set from 003.2 and MUST NOT directly read or change
  its capacity, ticket or session entities.
- **FR-019**: Feature 003.2 MUST validate and apply the complete requested
  required-session change as all-or-none. On success it MUST atomically transfer
  affected ticket entitlements and capacity, retain the booking deposit and
  frozen package price, and preserve the persistent ticket QR for each resulting
  entitled session. On failure no part may persist.
- **FR-019a**: After a successful required-session replacement or verified
  refund consequence, this feature MUST rely on 003.2 as the source of the
  current ticket/session projection for existing 003.1 customer access. It
  MUST NOT copy a replacement schedule into Booking or treat Booking's initial
  delivery projection as authoritative.
- **FR-019b** *(superseded 2026-11-15: the booking-scoped route this requires was removed (T106, T121, T123, T124); replaced by `POST /workshop-tickets/{ticketCode}/changes` and `POST /workshop-booking-groups/{groupCode}/ticket-changes` (FR-044, FR-045a))*: `POST /workshop-bookings/{bookingCode}/required-session-change-cases`
  MUST accept the complete target required-session reference set and a reason
  from an authorized Manager. When the change is an Mland-caused replacement, it
  MUST additionally accept the authoritative 003.2 `cancellationHandoffReference`.
  It MUST NOT accept caller-supplied capacity, ticket/QR, price, or partial
  replacement mapping.
- **FR-020** *(superseded 2026-11-12 by FR-043)*: When Mland cancels a required session, the system MUST offer an
  equivalent replacement before an authorized whole-booking/package refund.
- **FR-021** *(superseded 2026-11-12 by FR-038 and FR-043)*: If replacement is unavailable or declined, the authorized remedy
  MUST refund the amount required by the booking's Mland-caused-cancellation
  policy snapshot. The initial policy is 100% of every successfully collected
  booking/package payment, including deposit and final payment even if an
  earlier session was attended. A revised policy applies only to bookings
  confirmed after its effective time; V1 MUST NOT create a session-scoped
  partial refund, credit, voucher or separately settled session. When every
  remedy refund allocation is verified by signed QueryDR, the direct folio
  MUST become `CLOSED` with its collected and refunded amounts (D11); while any
  allocation is pending or unknown it stays `OPEN` and accepts no add-on. For a
  Member booking the remedy MUST emit Loyalty `VALID_WORKSHOP_CANCELLATION`
  (restore benefits, no earn; D15).
- **FR-021c** *(superseded 2026-11-15: the booking-scoped route this requires was removed (T106, T121, T123, T124); replaced by `GET /workshop-tickets/{ticketCode}/mland-remedy` and `POST …/mland-remedy/answers` (FR-043))*: `POST /workshop-bookings/{bookingCode}/mland-cancellation-remedy-cases`
  MUST accept an authorized Manager reason and `replacementOutcome` of
  `UNAVAILABLE` or `DECLINED` only, plus the authoritative 003.2
  `cancellationHandoffReference` used for the offered replacement. The server
  MUST derive the retained policy snapshot and refund amount; it MUST NOT accept
  caller-selected amount, ticket/session, or QR input.
*(FR-021a–FR-021e superseded for ticket bookings 2026-11-15: a Mland
cancellation of a ticket always refunds 100% of the money collected for it
(FR-043); the policy revisions and booking snapshots below do not change any
ticket refund.)*

- **FR-021a** *(superseded 2026-11-15 by FR-043: the policy is removed by T139/T140; a Mland cancellation always refunds 100%)*: Any active Manager may create or revise the
  Mland-caused-cancellation refund policy for future bookings across all
  locations. The change MUST record an effective time and MUST NOT alter a policy
  snapshot retained by an already confirmed booking.
- **FR-021d** *(superseded 2026-11-15 by FR-043: the route is removed by T139/T140)*: `POST /workshop-refund-policies` MUST accept only `effectiveAt`
  and `wholeBookingRefundPercent` from an active Manager. The
  effective time MUST be future and the percentage MUST be a whole number from
  `0` through `100`; the revision MUST apply only to later-confirmed bookings
  and MUST NOT rewrite retained snapshots. The policy snapshot refund amount MUST
  be `floor(collectedAmount × wholeBookingRefundPercent / 100)` whole VND.
- **FR-021e** *(superseded 2026-11-15 by FR-043: the route is removed by T139/T140)*: `GET /workshop-refund-policies` MUST be available to any active
  Manager and MUST return the global policy revision history.
  A Guest or Member MUST instead receive only the retained policy snapshot and
  refund outcome permitted for their own booking through its final-bill/case
  projection.
- **FR-021b** *(superseded 2026-11-15: the booking-scoped route this requires was removed (T106, T121, T123, T124); replaced by the per-ticket change and Mland-remedy routes (FR-043–FR-045a); the central `/workshop-refund-policies` resource is removed by T139 (2026-11-15))*: The public REST contract MUST expose required-session change and
  Mland-caused remedy as commands in the affected booking context. It MUST
  expose prospective refund-policy revision as a separate central resource, not
  as an operation scoped to one booking.
- **FR-022**: The system MUST distinguish service, final-bill, and gateway
  payment/refund states, return safe typed correlation-linked errors, and never
  expose raw tokens, payment payloads, secrets, or unnecessary PII.
- **FR-022a**: Every public REST command that changes settlement state MUST
  require an `Idempotency-Key`. A retry with the same key and semantically
  identical request MUST return the original outcome without repeating the
  command; a reused key with changed content MUST return a safe typed conflict.
  A successful command response MUST return the current safe settlement
  projection. *(clarified 2026-11-15, checklist CHK019, decided by Hồ Hoàng
  Quốc)* The rule covers the unauthenticated, single-use account-link route
  `POST /refund-account-links/{token}`: the page generates the key; the same
  key with the same content replays `204` without a second write or email, the
  same key with changed content is `409 IDEMPOTENCY_CONFLICT`, and a new key on
  a link already used, revoked or expired is `410
  REFUND_ACCOUNT_LINK_NOT_USABLE`. The stored record is scoped to the link
  token hash and keeps a keyed fingerprint of the request, never the account
  number, holder name or token.
- **FR-022d** *(note 2026-11-15: its first sentence restates the
  `Idempotency-Key` rule of FR-022a; FR-022a is the source, this requirement
  adds the error code, the provider deduplication and the status mapping)*:
  Client-originated commands MUST use `Idempotency-Key`; identical
  same-key retry MUST return the original response and changed reuse MUST return
  `409 IDEMPOTENCY_CONFLICT`. VNPay IPN/QueryDR MUST instead deduplicate by the
  verified provider transaction/reference and safely acknowledge a duplicate.
  The public contract MUST return typed safe errors as `400` for malformed input,
  `401` for missing/invalid authentication, `403` for authority denial, `404` for
  missing/not-visible booking, `409` for conflict, `410` for a refund-account link that is used, revoked or expired (`REFUND_ACCOUNT_LINK_NOT_USABLE`), and `422` for valid input that
  is business-ineligible. *(clarified 2026-11-15, checklist CHK018, same rule as
  003.2 CHK028)* `401` is returned only for a missing, malformed, invalid or
  expired bearer token and is declared on every operation that accepts
  `bearerAuth`; on a route open to a guest or a Member it applies only when a
  bearer token is present but invalid (no token means the guest path). A
  verified identity with no Mland account, an inactive account, a wrong role or
  an actor outside the scope gets `403`. The receipt-image routes (FR-039)
  add `413 RECEIPT_IMAGE_TOO_LARGE`, `415 RECEIPT_IMAGE_TYPE_UNSUPPORTED` and
  `503 RECEIPT_STORAGE_UNAVAILABLE`, and the guest folio lookup keeps `429`
  (the 003.1 group lookup limit), and the reconciliation list (FR-056) adds
  `503 RECONCILIATION_SOURCE_UNAVAILABLE` when the 003.1 source is down; these
  are the only status codes outside the list above. The public account-link route `POST /refund-account-links/{token}`
  also requires `Idempotency-Key` (see FR-022a).
- **FR-022b**: The public REST contract MUST represent final bill and charge as
  business resources. Customer cancellation, required-session change, and
  Mland-caused remedy MUST each create or update an auditable case resource with
  its own reference and lifecycle; they MUST NOT be untracked action-only
  operations. *(Superseded 2026-11-16 by FR-041–FR-045a, fix A28: customer cancellation, change and Mland remedy are now per-ticket records; the settlement case list still serves `FOLIO_CORRECTION`, Mland remedy and `ADDON_REFUND` cases.)*
- **FR-022b1**: Settlement cases MUST use `OPEN`, `RESOLVED`, `REJECTED`, or
  `FAILED`. Only cases that await verified refund evidence MAY use
  `PENDING_PROVIDER` *(wording changed 2026-11-15 from "awaiting gateway
  evidence" to match the 2026-11-14 decision below)*.
  Settlement defines no deferred-payment case or `EXPIRED` case state. A safe
  case projection MUST contain
  case reference, type, status, booking code, and correlation reference, and
  MUST disclose policy/amount outcome only to an authorized caller.
  *(Code, re-checked 2026-11-14: the vocabulary is
  `SettlementCaseLifecycleStatus` {`OPEN`, `PENDING_PROVIDER`, `RESOLVED`,
  `REJECTED`, `FAILED`} on `SettlementCase`; `SettlementCaseStatus` {`OPEN`,
  `RESOLVED`, `REJECTED`, `FAILED`} is the status of the 003.2
  session-cancellation handoff record `SettlementCancellationCase`, not this
  lifecycle.)* **Decided 2026-11-14 (Hồ Hoàng Quốc, owner Feature 003):
  `PENDING_PROVIDER` is kept, and its meaning is restated as "awaits verified
  refund evidence".** It no longer means "awaiting the gateway": since
  T098/T104 no Mland money goes to VNPay Refund (FR-038). The code creates
  `FOLIO_CORRECTION`, `MLAND_CANCELLATION_REMEDY` and `ADDON_REFUND` cases as
  `PENDING_PROVIDER` and keeps them there while the allocation is
  `PENDING_BANK_REFUND`, until the Manager confirms `REFUNDED` and the case
  becomes `RESOLVED`. Retiring it was refused because
  `FolioCorrectionService` also uses the state as the condition for dispatching
  the refund, so removing it would rewrite that flow for a naming gain only.
  Only the wording changes: the enum value, the stored strings and the
  behaviour stay as they are, and no migration is needed. The Javadoc of
  `SettlementCaseLifecycleStatus` was updated to this meaning in T106.
- **FR-022c**: The public REST contract MUST use the booking-scoped routes
  `GET/POST /workshop-bookings/{bookingCode}/final-bill`,
  `POST /workshop-bookings/{bookingCode}/final-bill/charges`, and
  `POST /workshop-bookings/{bookingCode}/final-bill/finalizations`. It MUST use
  booking-scoped `POST` case collections named `cancellation-cases`,
  `required-session-change-cases`, and `mland-cancellation-remedy-cases`, plus central
  `GET/POST /workshop-refund-policies`.
  *(Superseded by FR-048 after T106 (decision 2026-11-14): the booking-scoped
  final-bill routes and case collections give way to the per-ticket routes
  under `/workshop-tickets/{ticketCode}/…` (FR-041–FR-045, FR-048) and were
  removed on 2026-11-15 (T106, T123). `/workshop-refund-policies` is removed by T139 (2026-11-15).)*
- **FR-022e**: The public REST contract MUST provide `GET`
  `/workshop-bookings/{bookingCode}/cancellation-cases/{caseReference}`,
  `/workshop-bookings/{bookingCode}/required-session-change-cases/{caseReference}`, and
  `/workshop-bookings/{bookingCode}/mland-cancellation-remedy-cases/{caseReference}`.
  Each route MUST return only the safe case projection authorized for the
  requesting actor: an owning Member uses authenticated ownership and a Guest
  supplies the booking code plus one stored contact phone or email for that
  request. The routes MUST NOT disclose raw gateway evidence, tokens, QR payloads,
  or unnecessary contact data.
  *(Superseded by FR-048 after T106 (decision 2026-11-14): the booking-scoped
  case reads were removed with the booking-wide routes on 2026-11-15 (T123); the
  no-disclosure rule carries over to the per-ticket reads.)*

## Key Entities

- **Booking Settlement**: The settlement view of one booking/package, with frozen
  package price, paid deposit, required-session completion and final state.
- **Final Booking Bill (direct OPEN folio)**: The one folio from deposit
  confirmation to checkout or terminal closure: immutable base line, charge/adjustment lines,
  append-only verified collections (deposit and later payments), and
  derived balance due. Immutable once `FINALIZED`.
- **Charge Line**: Immutable final-bill charge with either a permitted
  Staff/Manager-confirmed fixed-price catalogue source or a Staff/Manager-
  confirmed variable material/service source.
- **Package Output Handover** *(superseded 2026-11-12 by FR-035: handover is
  per ticket after its folio is `FINALIZED`, or earlier only by a Manager's
  audited handover exception; see "Debt / Handover Exception" below)*: Staff gives the workshop package deliverable to
  the customer only after the final balance has been verified as paid. This
  describes the product/output after the workshop, not the booking ticket used
  to attend a session.
- **Refund Case** *(superseded for direct Mland money 2026-11-12 by the bank
  refund request of FR-039, per ticket; no gateway outcome)*: Mland-caused whole-booking/package direct-payment remedy
  refund with policy snapshot, amount, gateway outcome, and consequences.
  Customer-requested direct cancellation is a non-refundable Operations
  cancellation case and is not a Refund Case.
- **Reschedule Record** *(superseded 2026-11-12 by FR-044 and FR-043: see
  "Ticket Change" and "Mland Ticket Remedy" below)*: Audited booking-level replacement of one or more future
  required sessions, including the prior/resulting session sets and the
  all-or-none 003.2 outcome.

Per-ticket entities (added 2026-11-15; FR-030–FR-057; physical detail in
`data-model.md` "Per-ticket settlement records"):

- **Ticket Folio**: One folio per ticket booking (base = ticket final price,
  lines, derived balance, `OPEN`/`FINALIZED`/`CLOSED`); FR-030, FR-035.
- **Folio Collection**: One payment part of a ticket folio by `VNPAY`, cash or
  direct transfer, with status and reference; FR-031–FR-034.
- **Refund Request**: Per-ticket bank refund of Mland money through
  `PENDING_ACCOUNT` … `REFUNDED`/`CLOSED_UNREACHABLE`; FR-038, FR-039.
- **Refund Account**: Recipient bank account of a refund request, entered by
  single-use link or at the counter, number encrypted and masked; FR-040.
- **Cash Day Close**: One cash count per branch per Vietnam working day, with
  difference reason and Manager confirmation; FR-036, FR-052.
- **Debt / Handover Exception**: Manager-only audited record of a real debt or
  of handing over the product before verification; FR-035.
- **Ticket Cancellation**: Buyer cancellation of one ticket (or several of a
  group, all or none) with its 24-hour benefit outcome; FR-041, FR-042.
- **Ticket Change**: One applied session change of a ticket, counted or not,
  with its price effect; FR-044, FR-045.
- **Ticket Change Request**: A change only a Manager may allow, waiting as
  `PENDING` until approved, rejected or expired; FR-045a.
- **Mland Ticket Remedy**: Replacement offer and buyer answer (move or 100%
  bank refund) after a Mland cancellation; FR-043.
- **Absorbed Undercharge Record**: Manager's immutable, voidable-once record of
  an undercharge Mland absorbs on a `FINALIZED` folio; documentation only;
  FR-057.

## Success Criteria

- **SC-001**: 100% of acceptance-test bill corrections preserve original evidence
  and use a separate authorized void/adjustment while the folio is `OPEN`, or a
  Manager folio correction after `FINALIZED`.
- **SC-002** *(superseded for direct bookings 2026-11-12 by SC-002a: "for
  every role" no longer holds, because FR-035 lets a Manager approve handover
  before verification)*: 100% of finalization and package-output release attempts with an
  outstanding balance are rejected for every role until valid payment evidence
  confirms the full amount.
- **SC-002a** *(per ticket, FR-035)*: 100% of ticket-folio finalization
  attempts with a balance above 0 or a pending collection are rejected for
  every role, and 100% of product-handover attempts before verification are
  rejected unless a Manager recorded the audited handover exception with a
  reason.
- **SC-003**: 100% of invalid/duplicate/mismatched/unsigned/late payment or refund
  events do not settle the related bill/refund twice.
- **SC-004** *(superseded for direct bookings 2026-11-12 by SC-004a: refunds
  are now per ticket, FR-038, FR-043)*: 100% of Mland-remedy policy tests calculate the correct
  whole-booking refund percentage; direct customer cancellation never creates a
  refund and no path refunds only a session/ticket/participant.
- **SC-004a** *(per ticket, FR-038, FR-041, FR-043)*: in 100% of tests a
  customer cancellation or no-show refunds nothing of the booking-time
  collection; a Mland cancellation refunds exactly 100% of the money collected
  for each affected ticket and nothing of an unaffected ticket; and the total
  refunded or pending for a ticket never exceeds what was collected for it.
- **SC-005**: 100% of successful required-session-change tests transfer all and
  only affected capacity/ticket entitlements atomically while preserving the
  persistent QR and maintaining the resulting package/session-set invariants.

Per-ticket success criteria (FR-030–FR-051, User Stories 5–10; verified by
T103 on MySQL):

- **SC-006** *(FR-033, FR-034)*: 100% of VNPay collections under 5,000 VND are
  refused with no amount raised or waived, and in 100% of concurrent
  collection tests on one folio the sum of confirmed and pending collections
  never exceeds the balance.
- **SC-007** *(FR-032, FR-034)*: 100% of transfer confirmations by an actor
  without `workshop.payment.transfer.confirm` (V1: a non-Manager) are
  refused; 100% of repeated bank transaction references or cash
  confirmations are refused; 100% of excess money is recorded in full and
  opens one refund request, with no second finalization or earn.
- **SC-008** *(FR-036)*: 100% of cash closes with a difference and no reason
  are refused, and a close with a difference stays unconfirmed until a
  Manager confirms it; 100% of cash taken after the close counts for the next
  day.
- **SC-009** *(FR-039, FR-040)*: in 100% of refund tests no business
  consequence applies before `REFUNDED` (except capacity release and entitlement revocation at ticket cancellation, 003.2 FR-031); the full account number appears only
  in the audited Manager reveal (served `no-store`) and never in email, logs,
  audit or another response; an account link works once and within 7 days.
  *(Clause "a legacy code-`99` refund blocks a manual refund of the same
  money" withdrawn 2026-11-15 by Hồ Hoàng Quốc: VNPay Refund is not used and
  its code is removed, FR-039.)*
- **SC-010** *(FR-041, FR-042)*: a cancellation at 24 h 00 m before the first
  session is a valid cancellation that returns used points, and one at
  23 h 59 m returns none, in 100% of tests; a multi-ticket request with one
  ineligible ticket cancels none; the group voucher is restored at most once,
  and a late cancellation or no-show does not stop a Mland cancellation in the
  same group from restoring it (FR-043).
- **SC-011** *(FR-043)*: in 100% of Mland partial-cancellation tests only
  tickets with the cancelled session are affected, each refunded 100% of its
  collected money by bank, and the group voucher is restored exactly once.
- **SC-012** *(FR-044, FR-045)*: in 100% of change tests a ticket accepts one
  counted customer change (moving session 1 exhausts it for a later session 2
  change), a failed attempt or retry consumes none, a price increase is
  applied only with the buyer's confirmation, and no change lowers the price
  or creates a refund; the 24-hour advance window is measured from the session
  being changed (the earlier one when both move), so changing session 2 on the
  day of session 1 is allowed when session 2 is more than 24 hours away
  (clarified 2026-11-15).
- **SC-013** *(FR-056)*: in 100% of tests the reconciliation list returns
  exactly the transfer/VNPay/booking-time collections of the chosen branch, working day,
  method and state, refuses Staff, and leaves every collection, folio and cash
  close unchanged.
- **SC-014** *(FR-057)*: in 100% of tests an absorbed-undercharge record on a
  `FINALIZED` folio is Manager-only, needs a reason and a positive amount, is
  idempotent per key, cannot be edited, can be voided once with a reason
  (a voided record is excluded from totals), and changes no folio, line, Loyalty
  earn or cash figure; it is refused on a folio that is not `FINALIZED`.
- **SC-015**: every 003.3 screen and recovery state (folio, collection, cash
  close, refund queue and account link, ticket cancellation/change, remedy,
  change requests, customer folio, settlement case list, reconciliation list, undercharge record) meets WCAG 2.2 AA and is verified on
  the two latest major desktop versions of Chrome, Edge and Firefox.
- **SC-016** *(FR-052 to FR-054, FR-035, FR-057; checklist CHK037)*: in 100% of
  contract tests every Manager-only operation (line void, collection void,
  collection failure marking, extra change, cash-close difference confirmation,
  handover exception, debt record and close, reconciliation list, absorbed
  undercharge record and its void, and the Manager cash reversal of T144) answers
  a Staff caller with `403` and records no change; the Admin-only refund-account
  key-replacement route (T137) answers a Manager and a Staff caller with `403`.
- **SC-017** *(FR-040; checklist CHK038)*: a refund request in `PENDING_ACCOUNT`
  without a reply sends exactly one reminder on day 3 and one on day 7 and none
  once the account is entered; closing it as `CLOSED_UNREACHABLE` is refused
  before day 30 and allowed from day 30 for a Manager with a reason; reopening
  returns it to `PENDING_ACCOUNT` with a fresh link and leaves the customer's
  entitlement to the refund unchanged.
- **SC-018** *(FR-040; decided 2026-11-16, R08)*: in 100% of tests a refund-account key replacement is Admin-only (Manager and Staff get `403`), deletes the ciphertext of every request still holding one while keeping the mask, returns each to `PENDING_ACCOUNT` with a revoked old link and a new link sent, writes one audit record per request and never logs a number; a rerun touches only requests still holding ciphertext.
- **SC-019** *(FR-036, FR-053; decided 2026-11-16, R08)*: in 100% of tests a Manager cash reversal is dated the current working day and never edits the closed day; `cashDayClosed` is true from the submission of the counted amount (both `PENDING_MANAGER_CONFIRMATION` and `CLOSED`); cash recorded after that submission belongs to the next working day.
- **SC-020** *(FR-034; decided 2026-11-16, R08)*: in 100% of tests a `PENDING_VERIFICATION` VNPay part older than 24 hours with no successful QueryDR becomes `FAILED` with audit by `SYSTEM`; an inconclusive QueryDR does not fail it; money signed after `FAILED` is accepted in full and routed to an excess refund.

## Assumptions and Dependencies

- Feature 003.1 supplies confirmed direct bookings, frozen price, verified deposit
  evidence, and protected customer access.
- Feature 003.2 owns session/capacity/ticket/attendance states and cancellation
  handoffs; this feature must not duplicate normal check-in/configuration.
- The payment gateway is the source of verified final-payment/refund outcomes.
  Provider details and credentials belong to later approved plan/contracts.
  *(Superseded 2026-11-15: VNPay collections are verified only by signed
  IPN/QueryDR evidence; cash is recorded by the collecting Staff and direct
  transfers are confirmed by a Manager (FR-031, FR-032); refunds are bank
  transfers outside the system, confirmed `REFUNDED` by a Manager (FR-038,
  FR-039). The gateway is not a source of refund outcomes.)*
- Payment/audit evidence follows the five-year baseline in `spec-general.md`;
  no card data is stored.
- Accessibility and browser baseline (clarified 2026-11-15, same as 001
  SC-007): every 003.3 screen and recovery state meets WCAG 2.2 AA and is
  verified on the two latest major desktop versions of Chrome, Edge and
  Firefox. Performance, availability, deployment origins, and role-code
  mapping remain TBD at project level (spec-general); this feature sets no
  numeric target of its own. Account Management owns canonical role codes; this feature
  owns its Settlement action rules and must not infer them from display labels,
  Firebase claims or retired codes.

## Out of Scope

- OTA payment/refund, commission/payout/reconciliation, and OTA cancellation
  events (except the separate Mland shop add-on bill of an OTA booking, D12).
- Session configuration, normal check-in/finalization, booking creation/deposit flow.
- Default credit, automatic link renewal, lower-price credit/refund, customer
  self-service reschedule or partial cancellation/refund.
  *(Partly superseded 2026-11-12/14: "customer self-service reschedule" by
  FR-044 (one customer change per ticket, online or at the shop); "partial
  cancellation" by FR-041 (the buyer cancels chosen tickets); "partial
  refund" by the per-ticket bank refunds of FR-038 and FR-043. Default
  credit, automatic link renewal and lower-price credit/refund stay out of
  scope (FR-045: a change never lowers the price or creates a refund).)*
- Accounting, tax, general ledger, debt collection, or general reconciliation.
  *(Clarified 2026-11-15: V1 has only the read-only list of FR-056 and the
  absorbed-undercharge record of FR-057. A "reconciled" mark or reconciliation
  state, and any in-system handling of a mismatch (collecting from the
  customer after `FINALIZED`, charging an employee, accounting entries) are
  out of V1; a Manager handles a mismatch outside the system.)*

### Analyze follow-up: where each finding was applied (2026-11-16)

| Code | Finding | Applied where / decision |
| --- | --- | --- |
| A01 | Collection schema | data-model and contract (`Collection`, `branch_id`), T133 |
| A02 | `fromSessionReferences` release | same release as T141 (decided 2026-11-16, Q A02); T130 |
| A03 | Zero-money tickets | FR-043 and T167: `FREE`/deferred ticket resolved by answer/default and CLOSED folio; "audited Manager decision" removed |
| A04 | `BOOKING_TIME` | FR-056, US12, SC-013, contract, T133/T134, 003.1 T150 |
| A05 | Folio on customer cancel | decided option A: OPEN folio closes at collected amount, FINALIZED stays; T157 |
| A06 | VoucherRestoration | T155, contract mapping table (R20) |
| A07 | Voucher path replacement | T155 |
| A08 | `proofReference` optional | contract and T-tasks for transfer proof |
| A09 | 401/403 corrections | contracts (response codes) |
| A10 | Admin key replacement | decided: Admin-only (FR-040), T137 |
| A11 | `REFUNDED` final | decided: final in V1 (FR-040) |
| A12 | Backfill | decided: one-off Java job via `getTicketFolioContext`, T133 |
| A13 | Link idempotency table | data-model / contract |
| A14 | Quickstart runbook | quickstart.md (section "Per-ticket validation (current)") |
| A15 | Plan exception | decided option B: recorded in plan.md, no approval.md |
| A16 | Receipt retention | T154 and data-model |
| A17 | `cashDayClosed` | server value, true from the submission of the counted amount (R04), T145 and ui-flow |
| A18 | Cash-close release | T131/T132 and T145 |
| A19 | US 11-13 | spec User Stories 11, 12, 13; T143 |
| A20-A35 | Housekeeping | dates, task lists, cross-references (this pass) |
| A36 | D4/FR-035 exception | decided: "(except FR-043)" (FR-041) |

*(Mapping recorded 2026-11-16 as a pointer only; where a row says "decided", the decision text is the Q&A above. Rows A01, A06-A09, A13, A14, A16-A18 are mapped by code from the analyze list, not new decisions.)*
