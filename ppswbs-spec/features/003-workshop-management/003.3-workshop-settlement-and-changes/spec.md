# Feature Specification: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-11-14
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature Branch**: `003.3-workshop-settlement-and-changes`

**Status**: Draft — not implemented

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
  route.
- **Catalogue add-on** (`catalogueAddOnReference`): the price is resolved by the
  backend from the 003.2 catalogue; `GET .../final-bill/add-ons` lists the
  add-ons sellable for the booking's package and location.
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
  it returns the existing folio.
- **D2**: a booking is a no-show only when every ticket on every required
  session is a final `NO_SHOW`; partial attendance proceeds to normal checkout.
- **D3** *(superseded 2026-11-13 by FR-046/FR-047: per ticket only)*:
  Operations publishes `BookingAttendanceFinalizedEvent` and exposes
  `BookingAttendanceOutcomeQuery`; Settlement decides closure from the query.
- **D4**: folio states are `OPEN`, `FINALIZED` (checkout, `balanceDue = 0`) and
  `CLOSED` (ordinary cancellation/no-show at the verified collected amount, or
  a verified Mland-caused remedy refund per D11).
  Both terminal states are immutable; `CLOSED` never triggers Loyalty earn.
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
  See FR-004c.

## Per-ticket settlement, bank refunds, counter collection and changes (decision 2026-11-12)

**Source:** owner decisions recorded 2026-11-11/12 in
`artifacts/workshop-changes-to-implement.md`. **Status:** specified, not yet
implemented (tasks Phase 8). It follows 003.1 FR-030–FR-043 (booking group,
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
  Staff); cash by Staff with collection authority, recording amount received
  and change returned, with only the applied amount on the folio; direct
  transfer only by an actor holding `workshop.payment.transfer.confirm` after
  seeing the money in the shop account (remote confirmation allowed). A
  customer screenshot is never enough. A `PENDING_VERIFICATION` collection does
  not reduce the balance. *(V1, decision 2026-11-12: the codebase has roles,
  not named permissions, so the holder of `workshop.payment.transfer.confirm`
  is a Manager and transfer confirmation is Manager-only; per-person grants
  wait for FE-001.)*
- **FR-033**: The VNPay minimum of 5,000 VND applies to each VNPay collection;
  cash has no minimum and direct transfer follows the shop bank's limits. A
  balance of 1–4,999 VND is collected by cash or transfer. When a split would
  leave a VNPay part under 5,000 the screen MUST say so before collecting. The
  system MUST NOT raise an amount, waive a remainder or hide a remainder.
- **FR-034**: Creating a collection MUST lock the folio and consider confirmed
  and pending collections so one balance is never assigned twice. Switching
  method for a part with a pending VNPay attempt MUST first reconcile that
  attempt; closing the QR or screen is not proof of failure. Money that arrives
  twice or exceeds the balance MUST be recorded in full and routed to a Manager
  refund (FR-039); it never finalizes or earns twice. The same bank transaction
  or cash confirmation cannot be recorded twice.
- **FR-035**: The folio MUST be `FINALIZED` only when `balanceDue = 0`, no
  collection is pending and checkout conditions hold; the workshop product is
  handed over only after that. When the customer must leave while a transfer or
  VNPay part is unverified, Staff records `Chờ xác minh thanh toán` with the
  customer's transaction reference and the product is not handed over; the
  folio finalizes automatically when the part is confirmed. Handing over before
  verification is an exception only a Manager may approve, with a reason and
  audit. Recording a real debt (`Còn nợ`) and closing it are Manager-only, with
  a reason; there is no automatic reminder, deadline or booking block.
- **FR-036**: Each branch MUST close its cash book once per working day (Vietnam
  time) when closing: the system totals confirmed cash for the branch and day
  (net of change returned), the closer enters the counted amount, a difference
  requires a reason and Manager confirmation, and cash collected after the close
  belongs to the next day. Confirmed cash of a closed day changes only through
  the Manager correction flow. Transfers and VNPay are reconciled in separate
  reports, not in the cash close.
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
  Voucher value is never refunded as money.
- **FR-038a** *(decided 2026-11-12)*: The overcharge refund of an OTA booking's
  separate Mland shop add-on bill (D12, T092) is Mland money and also moves to
  the bank refund flow of FR-039–FR-040 (Manager-only, no VNPay Refund). The
  payer is usually at the counter, so Staff may enter the account there instead
  of sending the link. OTA base-ticket refunds remain the OTA's process.
- **FR-039**: Refund request lifecycle: `PENDING_ACCOUNT` → `PENDING_APPROVAL` →
  `PENDING_TRANSFER` → `PENDING_RECONCILIATION` → `REFUNDED`, plus
  `CLOSED_UNREACHABLE` (reopenable). A Manager approves reason, amount and
  recipient. An actor holding `workshop.refund.execute` transfers outside the
  system and records the bank reference and proof; only a Manager confirms
  `REFUNDED` after matching the bank statement (one Manager may both approve and
  confirm; every step records actor, time and proof). The default recipient is
  the payer of the refunded money (the group payer for the shared booking-time
  payment even when the ticket was sent to someone else; the counter payer for
  a counter collection); another recipient needs the payer's recorded
  confirmation. Requests and confirmations are deduplicated. A legacy VNPay
  refund with code `99` MUST be reconciled before any manual refund of the same
  money. Business consequences (folio `CLOSED`, booking refund status) apply only
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
  the approving Manager approves the amount, reason and order, and the
  transferring actor compares the holder name the bank shows for the account
  number with the name entered and stops on a mismatch (a mistyped account
  number), then contacts the customer. Account numbers are encrypted, shown in full
  only to the approving Manager and the transferring actor, masked
  (`****1234`) elsewhere, never written to email, logs or audit, and only the
  masked form is kept after `REFUNDED`. Without a reply the request stays
  `PENDING_ACCOUNT` with reminders on days 3 and 7; after 30 days a Manager may
  close it as `CLOSED_UNREACHABLE` with a reason and reopen it when the customer
  returns. The customer never loses the right to the refund.

### Customer cancellation per ticket

- **FR-041**: The buyer MAY cancel chosen tickets (a recipient cannot; a
  recipient goes through the buyer or the shop). A ticket can be cancelled while
  unused and before its first session starts; there is no earlier deadline.
  Cancellation releases the capacity of every future session of the ticket and
  changes no other ticket. The booking-time collection is not refunded; a
  `FREE` or deferred ticket's folio closes without debt. *(Decision 2026-11-14,
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
  prevents it. Each cancellation records request time, first-session start, the
  24-hour outcome, benefits returned, actor and channel, and replays are
  idempotent. Cart-level voucher conditions are not re-evaluated.

### Mland cancellation per ticket

- **FR-043**: When Mland cancels or moves a session, only tickets with that
  session are affected; a two-session ticket losing one session is handled as a
  whole ticket. Mland first offers every valid equivalent replacement (same package and
  branch, starting after the cancellation, with capacity and quota left, package
  session order kept; not counted as the customer's change, no extra charge) and
  the buyer chooses one; with none, the 100% bank refund opens at once
  (decided 2026-11-14).
  The buyer answers at least 24 hours before the replacement starts; no answer
  means refund, never an automatic move. If declined or unavailable, 100% of the
  money collected for the ticket (booking-time and counter) is refunded by bank
  under FR-038–FR-040, even if an earlier session of the ticket was attended.
  Used points of the ticket are returned. The group's voucher use is restored
  once even when only part of the group is cancelled by Mland, and never a
  second time. The buyer is emailed the replacement and refund choice; a
  recipient is told the session was cancelled but only the buyer chooses. The
  refund policy snapshot taken at confirmation still applies.

### Changes (rescheduling)

- **FR-044**: Each ticket allows one customer-requested change, shared by an
  advance change (at least 24 hours before the session) and late-arrival
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
     ticket's first session, or late-arrival support inside the window of
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
  counted change) or rejects it with a reason; it expires when it can no longer
  be carried out. Several tickets of one group change together, all or none,
  through `POST /workshop-booking-groups/{groupCode}/ticket-changes`.

*(Superseded 2026-11-14 by FR-045a.)* **Open before implementation:** the exact
auto-approval conditions for online
changes. Customer email content was approved 2026-11-12 in
`artifacts/workshop-email-templates-draft.md` (filename retained; not yet
implemented). Reschedule quota values were decided 2026-11-12 (003.2 FR-035)
and the OTA add-on refund in FR-038a.

**Open (owner decision needed, re-check 2026-11-14): operations required by
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
  close unchanged. The code's existing `POST /api/v1/workshop-cash-days/confirm-difference`
  (query `branchId`, optional `businessDay`) moves to it in T106; the cash-day
  paths stop being public then.
- **FR-053 — Manager void of a wrongly recorded line or collection.** While the
  folio is OPEN: `POST /workshop-tickets/{ticketCode}/folio/lines/{lineId}/voids`
  and `POST /workshop-tickets/{ticketCode}/folio/collections/{collectionId}/voids`.
  Manager only, reason required, `Idempotency-Key` required. A void is an
  audited reversal record and never deletes the original row; a collection
  already counted into a closed cash day is refused (FR-050), and the balance is
  recomputed by `FolioBalanceService` from what remains.
- **FR-054 — Manager-granted extra change.** `POST /workshop-tickets/{ticketCode}/change-allowances`,
  Manager only, reason required, `Idempotency-Key` required. It grants one extra
  counted customer change for that ticket (FR-044) and never performs the change
  itself.
- **FR-055 — Staff-on-behalf change.** It reuses
  `POST /workshop-tickets/{ticketCode}/changes` with the buyer proof of FR-041
  and a required reason, exactly as a Staff-on-behalf cancellation does; it gets
  no route of its own, so one code path keeps the FR-044/FR-045 rules.

## No-show per ticket only; direct booking is only a group (decision 2026-11-13)

**Source:** in-session instruction of Hồ Hoàng Quốc (owner Feature 003),
2026-11-13; follows 003.1's FR-044–FR-051 (003.1 numbering; every direct booking is a booking
group with ticket folios). **Status:** approved 2026-11-13 by the owner in session (the "Open" item stays open);
not implemented (task T105).

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
- **Open** *(decided 2026-11-14 by Hồ Hoàng Quốc, owner Feature 003; task
  T106)*: after 003.1's own FR-044–FR-051 (003.1 numbering) no direct `WorkshopBooking` remains and
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
    day stays locked, and any cash effect is recorded by the Manager as an
    adjustment on the current working day.
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
- A successful required-session change transfers the affected session
  entitlements and capacity commitments through 003.2 as one booking-level
  all-or-none result. It retains the booking deposit and preserves each
  participant's persistent ticket/QR across the booking's resulting required
  sessions. For Wax Casting, 003.2 MUST validate the complete resulting pair as
  same-location, distinct and chronologically ordered, with at least two
  Monday–Friday local dates strictly between them. Saturday/Sunday do not count;
  slot/time does not alter this calculation. The retired Silverclay same-day
  rule does not apply.
- A whole-booking refund revokes unused tickets and releases future capacity only
  after the gateway confirms the refund. A failed refund does not restore the
  booking for use.
- When Mland cancels a required session, it MUST offer an equivalent replacement
  first. If the replacement is unavailable or declined, its remedy is an audited
  refund governed by the booking's Mland-caused-cancellation policy snapshot.
  The V1 default is 100% of every successfully collected booking/package
  payment; V1 does not allocate a partial refund or credit to one session.
- Any active Manager may administer prospective Mland-caused-cancellation refund
  policy changes globally and resolve individual cases. Booking policy snapshots
  remain immutable.
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
  reminder, or link-reissue workflow.
- The system MUST NOT infer `Manager = OWNER`. Account Management supplies the
  canonical fixed role, current status and active location-scope facts; 003.3
  applies its own server-side Settlement action rules. Active Staff may perform
  routine charge/final-bill actions only within active location scope; any active
  Manager globally may perform required-session changes and refund exceptions/
  Mland-caused remedy. No role may waive or defer a final-balance requirement.
  An inactive actor loses these authorities immediately. Display role labels,
  Firebase claims and retired role codes never grant authority.
- Any active Manager may use the global Mland-caused-cancellation refund-policy
  editor. This does not introduce a role code or alter the Account Management
  role mapping; the feature checks active authorization and records the policy's
  effective time for future bookings.

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
   belongs to the next day; confirmed cash of the closed day changes only
   through the Manager correction flow (FR-036, FR-050).

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
3. **Given** a legacy VNPay refund with code `99` for the same money, **Then**
   no manual refund starts before it is reconciled (FR-039).
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
   no-show prevents it (FR-042).

### User Story 9 — Mland cancels a session of some tickets (P2)

FR-043. **Independent Test**: only tickets with the cancelled session are
affected.

1. **Given** a Mland-cancelled session, **When** the buyer declines the
   replacement or does not answer at least 24 hours before it starts,
   **Then** 100% of the money collected for each affected ticket is refunded by
   bank and its used points return.
2. **Given** only part of a group is affected, **Then** the voucher use is
   restored once and never a second time.

### User Story 10 — The customer changes a ticket's sessions (P2)

FR-044, FR-045. **Independent Test**: one counted change per ticket, all or
none, with a buyer-confirmed price effect.

1. **Given** a ticket that has used its change, **When** another change is
   requested without a Manager-granted exception, **Then** it is refused;
   moving session 1 alone uses the allowance (FR-044).
2. **Given** a change that fails, or a retry of a successful change, **Then**
   no additional allowance is consumed (FR-044).
3. **Given** a change that raises the price, **When** the buyer has not
   confirmed the increase, **Then** nothing changes; once confirmed, the
   increase is added to the ticket's checkout balance and no price ever
   falls (FR-045).

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
  sends neither command.
- **FR-002**: The system MUST authorize protected actions on the server using
  active Mland account status, canonical role, and feature-owned location and
  resource action rule;
  client/provider claims and display labels MUST NOT grant authority.
- **FR-002a**: Active Staff with booking-location scope MAY create or confirm an
  eligible charge and finalize a fully paid final bill. Required-session change,
  Mland-caused remedy/refund exception, and prospective refund-policy revision
  remain Manager-only actions under their applicable authority. No role may
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
- **FR-004b**: `POST /workshop-bookings/{bookingCode}/final-bill/charges` MUST
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
- **FR-005**: The system MUST calculate one booking/package balance due from the
  OPEN folio as `totalAmount − paidAmount`, where `totalAmount` is the frozen
  post-promotion amount plus approved charges and `paidAmount` is the sum of
  append-only verified collections (including the 003.1 deposit). It MUST NOT
  recalculate a frozen voucher, point, post-promotion amount, deposit, or
  remaining amount. A required-session change within that package MUST NOT alter
  its frozen package price.
- **FR-006**: The system MUST allow charge finalization only after the booking's
  final required session is completed, recording authorizing actor and time.
  The folio itself already exists from deposit confirmation (D1); finalization
  moves it from `OPEN` to `FINALIZED` only when `balanceDue = 0`.
- **FR-006a**: The public REST contract MUST expose the one final bill in its
  booking context. Charge creation/confirmation and finalization commands MUST
  operate in that final-bill context; no final bill is ticket-scoped or shared
  by multiple bookings.
- **FR-006b**: `GET /workshop-bookings/{bookingCode}/final-bill` MUST disclose a
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
  of deposit evidence.
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
  Delivery failure MUST NOT reverse the accepted Settlement outcome.
- **FR-013** *(superseded 2026-11-12 by FR-041)*: Guest/authorized Member cancellation MUST be whole-booking only;
  session/ticket/participant self-service cancellation/refund is prohibited. An
  authenticated Member MUST pass ownership verification; a Guest MUST provide
  the booking code and stored contact phone or email at cancellation-request
  time. Magic Link is read-only and MUST NOT alone authorize cancellation.
- **FR-013a**: `POST /workshop-bookings/{bookingCode}/cancellation-cases` MUST
  accept no customer-selected financial or ticket/session input. An authenticated
  Member requires no additional ownership field; a Guest MUST provide exactly one
  stored contact phone or email as request-time proof.
- **FR-013b**: A booking MUST have at most one customer-cancellation case. A
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
- **FR-019b**: `POST /workshop-bookings/{bookingCode}/required-session-change-cases`
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
- **FR-021c**: `POST /workshop-bookings/{bookingCode}/mland-cancellation-remedy-cases`
  MUST accept an authorized Manager reason and `replacementOutcome` of
  `UNAVAILABLE` or `DECLINED` only, plus the authoritative 003.2
  `cancellationHandoffReference` used for the offered replacement. The server
  MUST derive the retained policy snapshot and refund amount; it MUST NOT accept
  caller-selected amount, ticket/session, or QR input.
- **FR-021a**: Any active Manager may create or revise the
  Mland-caused-cancellation refund policy for future bookings across all
  locations. The change MUST record an effective time and MUST NOT alter a policy
  snapshot retained by an already confirmed booking.
- **FR-021d**: `POST /workshop-refund-policies` MUST accept only `effectiveAt`
  and `wholeBookingRefundPercent` from an active Manager. The
  effective time MUST be future and the percentage MUST be a whole number from
  `0` through `100`; the revision MUST apply only to later-confirmed bookings
  and MUST NOT rewrite retained snapshots. The policy snapshot refund amount MUST
  be `floor(collectedAmount × wholeBookingRefundPercent / 100)` whole VND.
- **FR-021e**: `GET /workshop-refund-policies` MUST be available to any active
  Manager and MUST return the global policy revision history.
  A Guest or Member MUST instead receive only the retained policy snapshot and
  refund outcome permitted for their own booking through its final-bill/case
  projection.
- **FR-021b**: The public REST contract MUST expose required-session change and
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
  projection.
- **FR-022d**: Client-originated commands MUST use `Idempotency-Key`; identical
  same-key retry MUST return the original response and changed reuse MUST return
  `409 IDEMPOTENCY_CONFLICT`. VNPay IPN/QueryDR MUST instead deduplicate by the
  verified provider transaction/reference and safely acknowledge a duplicate.
  The public contract MUST return typed safe errors as `400` for malformed input,
  `401` for missing/invalid authentication, `403` for authority denial, `404` for
  missing/not-visible booking, `409` for conflict, and `422` for valid input that
  is business-ineligible.
- **FR-022b**: The public REST contract MUST represent final bill and charge as
  business resources. Customer cancellation, required-session change, and
  Mland-caused remedy MUST each create or update an auditable case resource with
  its own reference and lifecycle; they MUST NOT be untracked action-only
  operations.
- **FR-022b1**: Settlement cases MUST use `OPEN`, `RESOLVED`, `REJECTED`, or
  `FAILED`. Only cases awaiting gateway evidence MAY use `PENDING_PROVIDER`.
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
  `SettlementCaseLifecycleStatus` is updated to this meaning in the next task
  that touches settlement refunds (T106).
- **FR-022c**: The public REST contract MUST use the booking-scoped routes
  `GET/POST /workshop-bookings/{bookingCode}/final-bill`,
  `POST /workshop-bookings/{bookingCode}/final-bill/charges`, and
  `POST /workshop-bookings/{bookingCode}/final-bill/finalizations`. It MUST use
  booking-scoped `POST` case collections named `cancellation-cases`,
  `required-session-change-cases`, and `mland-cancellation-remedy-cases`, plus central
  `GET/POST /workshop-refund-policies`.
  *(Superseded by FR-048 after T106 (decision 2026-11-14): the booking-scoped
  final-bill routes and case collections give way to the per-ticket routes
  under `/workshop-tickets/{ticketCode}/…` (FR-041–FR-045, FR-048) and are
  removed only once T106 lands. `/workshop-refund-policies` is unaffected.)*
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
  case reads are removed with the booking-wide routes; the no-disclosure rule
  carries over to the per-ticket reads.)*

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
- **Package Output Handover**: Staff gives the workshop package deliverable to
  the customer only after the final balance has been verified as paid. This
  describes the product/output after the workshop, not the booking ticket used
  to attend a session.
- **Refund Case** *(superseded for direct Mland money 2026-11-12 by the bank
  refund request of FR-039, per ticket; no gateway outcome)*: Mland-caused whole-booking/package direct-payment remedy
  refund with policy snapshot, amount, gateway outcome, and consequences.
  Customer-requested direct cancellation is a non-refundable Operations
  cancellation case and is not a Refund Case.
- **Reschedule Record**: Audited booking-level replacement of one or more future
  required sessions, including the prior/resulting session sets and the
  all-or-none 003.2 outcome.

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
  consequence applies before `REFUNDED`; the full account number appears only
  in the audited Manager reveal (served `no-store`) and never in email, logs,
  audit or another response; an account link works once and within 7 days; a
  legacy code-`99` refund blocks a manual refund of the same money.
- **SC-010** *(FR-041, FR-042)*: a cancellation at 24 h 00 m before the first
  session is a valid cancellation that returns used points, and one at
  23 h 59 m returns none, in 100% of tests; a multi-ticket request with one
  ineligible ticket cancels none; the group voucher is restored at most once.
- **SC-011** *(FR-043)*: in 100% of Mland partial-cancellation tests only
  tickets with the cancelled session are affected, each refunded 100% of its
  collected money by bank, and the group voucher is restored exactly once.
- **SC-012** *(FR-044, FR-045)*: in 100% of change tests a ticket accepts one
  counted customer change (moving session 1 exhausts it for a later session 2
  change), a failed attempt or retry consumes none, a price increase is
  applied only with the buyer's confirmation, and no change lowers the price
  or creates a refund.

## Assumptions and Dependencies

- Feature 003.1 supplies confirmed direct bookings, frozen price, verified deposit
  evidence, and protected customer access.
- Feature 003.2 owns session/capacity/ticket/attendance states and cancellation
  handoffs; this feature must not duplicate normal check-in/configuration.
- The payment gateway is the source of verified final-payment/refund outcomes.
  Provider details and credentials belong to later approved plan/contracts.
- Payment/audit evidence follows the five-year baseline in `spec-general.md`;
  no card data is stored.
- Performance, availability, accessibility, deployment origins, and role-code
  mapping remain TBD. Account Management owns canonical role codes; this feature
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
