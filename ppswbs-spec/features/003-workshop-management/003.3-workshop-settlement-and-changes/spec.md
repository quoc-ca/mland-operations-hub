# Feature Specification: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-10-25
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature Branch**: `003.3-workshop-settlement-and-changes`

**Status**: Draft — not implemented

**Input**: Confirmed workshop-settlement, change, and direct-booking cancellation
decisions. This spec retains the feature baseline locally so a later retirement
of the shared `research.md` does not remove its behavioural decisions.

## Scope

This feature owns direct-Mland actions after a workshop booking is confirmed:
post-package charges and final bills, final payment and release control,
Manager-approved deferred payment, Manager-mediated change of one or more
future required sessions,
customer-requested whole-booking cancellation/refund, and Mland-caused
package cancellation remedies.

It consumes booking/deposit evidence from 003.1 and authoritative session,
capacity, ticket, attendance, and cancellation-handoff outcomes from 003.2. It
does not create sessions, perform normal check-in, provide customer self-service
rescheduling, or process OTA payments/refunds.

## Clarifications

### Session 2026-10-24

- Q: What is the customer refund unit? → A: Only the whole direct booking;
  customers cannot self-service cancel/refund a session, ticket, or participant.
- Q: What is the direct-booking refund policy? → A: Against the earliest session
  start: 100% of paid deposit at least 72 hours before; 50% from 24 to under 72
  hours; 0% below 24 hours, for no-show, or after any ticket check-in.

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
- Q: How are additional material or service charges added? → A: A customer may
  add a fixed-price catalogue add-on allowed for the package/branch; it enters
  the final bill at its displayed price. A variable material or service charge
  becomes part of the bill only after Staff/Manager confirms it. Customers never
  enter arbitrary prices.
- Q: Which V1 channel provides accepted final-payment evidence? → A: VNPay only.
  A customer may pay at the counter through the VNPay link/QR, but settlement
  requires valid signed VNPay IPN or QueryDR evidence; browser return and a
  manually recorded counter payment do not settle the bill.
- Q: Which feature executes a required-session change? → A: 003.3 requests one
  booking-level change containing the complete replacement session set. 003.2,
  as capacity and ticket-entitlement owner, validates the complete result and
  applies all changes or none; it preserves the persistent ticket QR.
- Q: Who may revise the Mland-caused-cancellation refund policy? → A: One
  designated central Manager may manage the policy for all Mland branches. Other
  Managers handle individual cases only under the booking's existing policy
  snapshot; they cannot revise that policy.

## Business Rules

- A confirmed direct booking freezes its package price and collects a 50%
  deposit. The one deposit applies to the one booking/package without
  allocating it across sessions or rewriting original payment evidence.
- A final booking balance equals the remaining frozen package amount, approved
  additional material/service charges, less the paid deposit. Replacing required
  sessions within the same package never changes the frozen package price.
- A customer may add a fixed-price catalogue add-on permitted for the package and
  branch; its displayed price becomes a final-bill charge. A variable material
  or service charge becomes a final-bill charge only after Staff/Manager confirms
  it. Customers cannot enter an arbitrary charge or price.
- Charge, payment, void, and adjustment records are immutable evidence. A
  correction is an authorized void or adjustment with audit, not an edit to
  historical evidence.
- The completed booking/package output is releasable only when its one final
  balance is fully paid through valid signed VNPay IPN or QueryDR evidence,
  except for an authorized deferred-payment exception. A booking is `FULLY_PAID`
  only when that final balance is settled or resolved by an audited exception;
  browser return and a manually recorded counter payment do not settle it.
- A successful required-session change transfers the affected session
  entitlements and capacity commitments through 003.2 as one booking-level
  all-or-none result. It retains the booking deposit and preserves each
  participant's persistent ticket/QR across the booking's resulting required
  sessions. For Silverclay, 003.2 MUST validate the complete resulting pair as
  same-branch, distinct and chronologically ordered; if on the same day, the two
  slots MUST have at least one slot between them.
- A whole-booking refund revokes unused tickets and releases future capacity only
  after the gateway confirms the refund. A failed refund does not restore the
  booking for use.
- When Mland cancels a required session, it MUST offer an equivalent replacement
  first. If the replacement is unavailable or declined, its remedy is an audited
  refund governed by the booking's Mland-caused-cancellation policy snapshot.
  The V1 default is 100% of every successfully collected booking/package
  payment; V1 does not allocate a partial refund or credit to one session.
- A designated central Manager owns prospective Mland-caused-cancellation refund
  policy changes for all branches. Other Managers may resolve individual cases
  only under the policy snapshot already retained by the booking.
- OTA customer payments, refunds, commission, payout, and reconciliation are
  outside this feature.

## Confirmed Decisions

### Session 2026-10-24

- A final-payment link is a 24-hour exception only for a Manager-approved
  Workshop Final Bill. Deposit/payment-hold links remain valid for 10 minutes
  and their associated holds last at most 15 minutes. The final-payment link
  may send at most one reminder before expiry; expiry never automatically renews
  the link, releases the package output, or erases the balance. A new link requires an
  audited authorized action.
- The system MUST NOT infer `Manager = OWNER`. Account/personnel owns capability
  grants and role-code mapping. Feature 003.3 authorizes actions using active
  actor status plus the relevant branch-scoped capability:
  `workshop.settlement.defer` for deferred payment/link issue or reissue,
  `workshop.settlement.reschedule` for required-session changes, and
  `workshop.settlement.refund` for refund exceptions and Mland-caused package
  refund. An inactive actor loses these authorities immediately.
- The Mland-caused-cancellation refund-policy editor is one designated central
  Manager. This business authority does not introduce a role code or alter the
  Account Management role mapping; the feature requires its active authorization
  and records the policy's effective time for future bookings.

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
2. **Given** an outstanding balance, **When** Staff attempts release without a
   Manager exception, **Then** release is rejected.
3. **Given** a finalized bill, **When** Staff finds an error, **Then** only an
   audited void/adjustment corrects it; original evidence remains.
4. **Given** a permitted fixed-price catalogue add-on, **When** the customer
   selects it, **Then** it is visible in the final bill at its displayed price;
   a variable charge appears only after Staff/Manager confirmation.

### User Story 2 — Customer cancels a whole booking (P1)

Guest or Member needs a clear direct-booking cancellation/refund result without
being offered a session/ticket-level refund.

**Independent Test**: An eligible unstarted booking receives one whole-booking
request, snapshots its policy result, and changes tickets/capacity only after
confirmed refund.

1. **Given** all tickets are unused and earliest session is at least 72 hours
   away, **When** the customer requests cancellation, **Then** 100% of paid
   deposit is requested as refund.
2. **Given** earliest session is 24 to under 72 hours away, **Then** 50% is
   requested; below 24 hours, no-show, or checked-in ticket rejects the normal
   customer refund path.
3. **Given** refund is pending or fails, **Then** tickets/capacity do not change
   until confirmed and the booking is not restored for use after failure.

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

## Functional Requirements

- **FR-001**: The system MUST apply this feature only to direct Mland workshop
  bookings and MUST exclude OTA payment/refund/payout/reconciliation.
- **FR-002**: The system MUST authorize protected actions on the server using
  active Mland account status, approved capability, branch and resource scope;
  client/provider claims and display labels MUST NOT grant authority.
- **FR-003**: The system MUST retain immutable, redacted audit evidence for
  settlement, release exception, deferred payment, reschedule, refund, void,
  adjustment, and Mland-caused remedy.
- **FR-004**: The system MUST preserve immutable charge, payment, void, and
  adjustment records for each final bill.
- **FR-004a**: The system MUST accept a customer-selected charge only for a
  fixed-price catalogue add-on permitted for the booking's package and branch,
  at its displayed price. A variable material or service charge MUST require
  Staff/Manager confirmation before it enters the final bill; customers MUST NOT
  create arbitrary charge names or prices.
- **FR-005**: The system MUST calculate one booking/package final balance from
  frozen package price, paid deposit, and approved charges. A required-session
  change within that package MUST NOT alter its frozen package price.
- **FR-006**: The system MUST allow charge finalization only after the booking's
  final required session is completed, recording authorizing actor and time.
- **FR-007**: The system MUST prevent package-output release before final payment except
  through an authorized audited deferred-payment exception.
- **FR-008**: The system MUST set booking `FULLY_PAID` only when its one final
  balance is settled or resolved by an audited exception.
- **FR-009**: The system MUST accept final-payment evidence only from valid
  signed VNPay IPN or QueryDR, matched to the final bill's reference, amount and
  currency. It MUST process that evidence idempotently and reject invalid,
  mismatched, unsigned, duplicate, late, browser-return-only, or manually
  recorded counter-payment evidence without settling twice.
- **FR-010**: Deferred final payment MUST require Manager approval with reason,
  amount, expiry, and audit; it MUST retain the package output and create no
  default credit.
- **FR-011**: The system MUST send at most one pre-expiry reminder and MUST NOT
  automatically renew an expired request, release the package output, or erase
  its balance.
- **FR-012**: A Manager-approved final-payment link MUST be valid for 24 hours,
  send at most one reminder before expiry, and apply only to a Workshop Final
  Bill. Deposit/payment-hold links remain subject to the 10-minute/15-minute
  shared rule. A new final-payment link after expiry requires an authorized,
  audited action.
- **FR-013**: Guest/authorized Member cancellation MUST be whole-booking only;
  session/ticket/participant self-service cancellation/refund is prohibited.
- **FR-014**: Normal customer cancellation MUST require all tickets unused, no
  required session started and no package output released, plus policy
  eligibility.
- **FR-015**: The system MUST calculate paid-deposit refund against earliest
  session start in session/branch timezone: 100% ≥72h, 50% ≥24h and <72h, else 0%.
- **FR-016**: It MUST snapshot policy, earliest-session reference, paid amount,
  computed amount, request time, and eligibility outcome for each request.
- **FR-017**: It MUST use `REFUND_PENDING` until gateway outcome is confirmed,
  then revoke unused tickets/release future capacity only on confirmed refund.
- **FR-018**: An authorized Manager MAY replace one or more future required
  sessions only with compatible sessions that have capacity for the booking's
  unchanged participant count. For a multi-session package, the complete
  resulting session set MUST remain valid; Silverclay's resulting pair MUST be
  same-branch, distinct, chronologically ordered and, when on the same day,
  separated by at least one slot. Feature 003.3 MUST request the complete
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
- **FR-020**: When Mland cancels a required session, the system MUST offer an
  equivalent replacement before an authorized whole-booking/package refund.
- **FR-021**: If replacement is unavailable or declined, the authorized remedy
  MUST refund the amount required by the booking's Mland-caused-cancellation
  policy snapshot. The initial policy is 100% of every successfully collected
  booking/package payment, including deposit and final payment even if an
  earlier session was attended. A revised policy applies only to bookings
  confirmed after its effective time; V1 MUST NOT create a session-scoped
  partial refund, credit, voucher or separately settled session.
- **FR-021a**: Only the designated central Manager may create or revise the
  Mland-caused-cancellation refund policy for future bookings across all
  branches. The change MUST record an effective time and MUST NOT alter a policy
  snapshot retained by an already confirmed booking; other Managers MUST NOT
  revise the policy.
- **FR-022**: The system MUST distinguish service, final-bill, and gateway
  payment/refund states, return safe typed correlation-linked errors, and never
  expose raw tokens, payment payloads, secrets, or unnecessary PII.

## Key Entities

- **Booking Settlement**: The settlement view of one booking/package, with frozen
  package price, paid deposit, required-session completion and final state.
- **Final Booking Bill**: Remaining package amount, charge/adjustment lines,
  paid deposit, payments, and outstanding balance.
- **Charge Line**: Immutable final-bill charge with either a permitted
  customer-selected fixed-price catalogue source or a Staff/Manager-confirmed
  variable material/service source.
- **Deferred-Payment Exception**: Manager-approved time-bounded final-balance
  request while the shop retains the package output.
- **Refund Case**: Customer-requested or Mland-caused whole-booking/package
  direct-payment refund with policy snapshot, amount, gateway outcome, and
  consequences.
- **Reschedule Record**: Audited booking-level replacement of one or more future
  required sessions, including the prior/resulting session sets and the
  all-or-none 003.2 outcome.

## Success Criteria

- **SC-001**: 100% of acceptance-test bill corrections preserve original evidence
  and use a separate authorized void/adjustment.
- **SC-002**: 100% of release attempts with an outstanding balance are rejected
  unless a valid Manager exception exists.
- **SC-003**: 100% of invalid/duplicate/mismatched/unsigned/late payment or refund
  events do not settle the related bill/refund twice.
- **SC-004**: 100% of policy tests calculate the correct earliest-session refund
  percentage and no customer path refunds only a session/ticket/participant.
- **SC-005**: 100% of successful required-session-change tests transfer all and
  only affected capacity/ticket entitlements atomically while preserving the
  persistent QR and maintaining the resulting package/session-set invariants.

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
  mapping remain TBD. Capability-to-role-code mapping is explicitly owned by
  account/personnel and must not be inferred by this feature.

## Out of Scope

- OTA payment/refund, commission/payout/reconciliation, and OTA cancellation events.
- Session configuration, normal check-in/finalization, booking creation/deposit flow.
- Default credit, automatic link renewal, lower-price credit/refund, customer
  self-service reschedule or partial cancellation/refund.
- Accounting, tax, general ledger, debt collection, or general reconciliation.
