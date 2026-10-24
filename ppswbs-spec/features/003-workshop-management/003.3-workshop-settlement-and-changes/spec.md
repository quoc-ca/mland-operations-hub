# Feature Specification: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-10-24
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature Branch**: `003.3-workshop-settlement-and-changes`

**Status**: Draft — not implemented

**Input**: Confirmed workshop-settlement, change, and direct-booking cancellation
decisions. This spec retains the feature baseline locally so a later retirement
of the shared `research.md` does not remove its behavioural decisions.

## Scope

This feature owns direct-Mland actions after a workshop booking is confirmed:
post-session charges and final bills, final payment and release control,
Manager-approved deferred payment, manual item rescheduling, customer-requested
whole-booking cancellation/refund, and Mland-caused item cancellation remedies.

It consumes booking/deposit evidence from 003.1 and authoritative session,
capacity, ticket, attendance, and cancellation-handoff outcomes from 003.2. It
does not create sessions, perform normal check-in, provide customer self-service
rescheduling, or process OTA payments/refunds.

## Clarifications

### Session 2026-10-24

- Q: What is the normal settlement point? → A: After an item's session,
  Staff/Manager finalizes assessed charges, collects payment at the counter, and
  releases the completed item only after full payment. Mland grants no default
  credit.
- Q: Can a customer reschedule online? → A: No. The customer contacts the shop;
  an authorized Manager may move one unused item only to a session with capacity
  for the same package, configuration, and participant count.
- Q: What is the customer refund unit? → A: Only the whole direct booking;
  customers cannot self-service cancel/refund an item, ticket, or participant.
- Q: What is the direct-booking refund policy? → A: Against the earliest session
  start: 100% of paid deposit at least 72 hours before; 50% from 24 to under 72
  hours; 0% below 24 hours, for no-show, or after any ticket check-in.
- Q: What if Mland cancels one item? → A: Offer an equivalent replacement. If it
  is unavailable or declined, Manager may make an audited item-scoped refund of
  payments allocated to that item; this creates no customer partial-refund right.

## Business Rules

- A confirmed direct booking freezes its base price and collects a 50% deposit.
  The deposit is allocated across workshop items without rewriting original
  payment evidence.
- A final item balance equals remaining frozen base amount, approved additional
  material/service charges, and permitted reschedule price increase, less its
  allocated paid deposit.
- Charge, payment, void, and adjustment records are immutable evidence. A
  correction is an authorized void or adjustment with audit, not an edit to
  historical evidence.
- An item is releasable only when its final balance is fully paid, except for an
  authorized deferred-payment exception. A booking is `FULLY_PAID` only when
  every item is fully settled or resolved by an audited exception.
- A successful item reschedule transfers every active item ticket and capacity
  commitment atomically, retains allocated deposit, revokes old QR codes, issues
  replacements, and leaves other items unchanged.
- A higher current base price at the target session is added to final balance. A
  lower/equal price keeps the frozen original price; V1 creates no credit/refund.
- A whole-booking refund revokes unused tickets and releases future capacity only
  after the gateway confirms the refund. A failed refund does not restore the
  booking for use.
- OTA customer payments, refunds, commission, payout, and reconciliation are
  outside this feature.

## Decisions Requiring Resolution Before Planning

1. **Deferred-payment link duration**: the inherited feature decision specifies
   a 24-hour Manager-approved final-balance link with one reminder. The shared
   `spec-general.md` §4 states that a payment link expires after 10 minutes.
   The responsible authority must explicitly decide whether final settlement is
   a narrow 24-hour exception or revise one rule. No plan/contract may silently
   choose either value.
2. **Manager role-code mapping**: this spec uses the business role “Manager”;
   `spec-general.md` §2 leaves its mapping to implementation role codes TBD.
   Server authorization must use the approved account/personnel capability
   contract, never infer it from a label.

## User Scenarios and Testing

### User Story 1 — Staff settles an item at the counter (P1)

After a completed item session, Staff needs to finalize charges, collect final
payment, and determine whether the item can be released.

**Independent Test**: With a confirmed direct booking and finished item, Staff
can finalize charges and record one valid payment. The item becomes releasable
only when its balance is zero; failed/duplicate evidence changes nothing.

1. **Given** an eligible completed item, **When** valid final payment is
   confirmed, **Then** immutable bill/payment evidence is recorded and release is
   permitted.
2. **Given** an outstanding balance, **When** Staff attempts release without a
   Manager exception, **Then** release is rejected.
3. **Given** a finalized bill, **When** Staff finds an error, **Then** only an
   audited void/adjustment corrects it; original evidence remains.

### User Story 2 — Customer cancels a whole booking (P1)

Guest or Member needs a clear direct-booking cancellation/refund result without
being offered an item/ticket-level refund.

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

### User Story 3 — Manager reschedules one item (P2)

Manager needs to move an unused item without affecting other booking items or
overbooking the target session.

**Independent Test**: A compatible target with capacity moves all and only the
item’s tickets atomically, invalidates old QR codes, and applies price policy.

1. **Given** an unused compatible item and capacity, **When** Manager approves
   the move, **Then** ticket, capacity, deposit, QR, price, and audit outcomes
   succeed as one result.
2. **Given** insufficient/incompatible target capacity, **Then** no session,
   ticket, or bill change persists.
3. **Given** target price is higher, **Then** only the difference is added to
   final balance; lower/equal price creates no credit/refund.

### User Story 4 — Manager remedies Mland-caused cancellation (P2)

When Mland cannot provide one item, Manager needs an audited item remedy without
changing other active items.

**Independent Test**: After replacement is unavailable/declined, Manager creates
an item-scoped refund; only that item’s tickets/capacity change after confirmation.

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
- **FR-005**: The system MUST calculate final balance from frozen base price,
  allocated paid deposit, approved charges, and permitted reschedule increase.
- **FR-006**: The system MUST allow charge finalization only after corresponding
  item attendance/session completion, recording authorizing actor and time.
- **FR-007**: The system MUST prevent item release before final payment except
  through an authorized audited deferred-payment exception.
- **FR-008**: The system MUST set booking `FULLY_PAID` only when every item is
  fully settled or resolved by audited exception.
- **FR-009**: The system MUST process final-payment evidence idempotently and
  reject invalid, mismatched, unsigned, duplicate, late, or browser-return-only
  evidence without settling twice.
- **FR-010**: Deferred final payment MUST require Manager approval with reason,
  amount, expiry, and audit; it MUST retain the item and create no default credit.
- **FR-011**: The system MUST send at most one pre-expiry reminder and MUST NOT
  automatically renew an expired request, release the item, or erase its balance.
- **FR-012**: Deferred-payment duration/reminder timing MUST remain unresolved
  until the documented 10-minute/24-hour conflict is approved.
- **FR-013**: Guest/authorized Member cancellation MUST be whole-booking only;
  item/ticket/participant self-service cancellation/refund is prohibited.
- **FR-014**: Normal customer cancellation MUST require all items/tickets unused,
  no item started/released, and policy eligibility.
- **FR-015**: The system MUST calculate paid-deposit refund against earliest
  session start in session/branch timezone: 100% ≥72h, 50% ≥24h and <72h, else 0%.
- **FR-016**: It MUST snapshot policy, earliest-session reference, paid amount,
  computed amount, request time, and eligibility outcome for each request.
- **FR-017**: It MUST use `REFUND_PENDING` until gateway outcome is confirmed,
  then revoke unused tickets/release future capacity only on confirmed refund.
- **FR-018**: Manager may move one unused item only to a compatible same-package,
  same-configuration, same-participant-count session with capacity.
- **FR-019**: A successful move MUST atomically transfer affected tickets and
  capacity, retain deposit, revoke/replace QR, apply price rule, and audit; on
  failure no part may persist.
- **FR-020**: When Mland cancels one item, the system MUST offer equivalent
  replacement before Manager-only item-scoped refund of its allocated payments.
- **FR-021**: An item-scoped refund MUST affect only its tickets/capacity and
  MUST NOT set the whole booking `REFUNDED` when other items remain active.
- **FR-022**: The system MUST distinguish service, final-bill, and gateway
  payment/refund states, return safe typed correlation-linked errors, and never
  expose raw tokens, payment payloads, secrets, or unnecessary PII.

## Key Entities

- **Workshop Item**: Separately settleable booking service with frozen price,
  deposit allocation, session, tickets, and final settlement state.
- **Final Item Bill**: Remaining base amount, charge/adjustment lines, allocated
  deposit, payments, and outstanding balance.
- **Deferred-Payment Exception**: Manager-approved time-bounded final-balance
  request while the shop retains the item.
- **Refund Case**: Whole-booking or Mland-caused item direct-payment refund with
  policy snapshot, scope, amount, gateway outcome, and consequences.
- **Reschedule Record**: Audited move of item tickets between compatible sessions.

## Success Criteria

- **SC-001**: 100% of acceptance-test bill corrections preserve original evidence
  and use a separate authorized void/adjustment.
- **SC-002**: 100% of release attempts with an outstanding balance are rejected
  unless a valid Manager exception exists.
- **SC-003**: 100% of invalid/duplicate/mismatched/unsigned/late payment or refund
  events do not settle the related bill/refund twice.
- **SC-004**: 100% of policy tests calculate the correct earliest-session refund
  percentage and no customer path refunds only an item/ticket/participant.
- **SC-005**: 100% of successful reschedule tests move all and only affected
  tickets/capacity, replace QR codes, and leave other items unchanged.

## Assumptions and Dependencies

- Feature 003.1 supplies confirmed direct bookings, frozen price, verified deposit
  evidence, and protected customer access.
- Feature 003.2 owns session/capacity/ticket/attendance states and cancellation
  handoffs; this feature must not duplicate normal check-in/configuration.
- The payment gateway is the source of verified final-payment/refund outcomes.
  Provider details and credentials belong to later approved plan/contracts.
- Payment/audit evidence follows the five-year baseline in `spec-general.md`;
  no card data is stored.
- Performance, availability, accessibility, deployment origins, role-code mapping,
  and deferred-payment duration remain TBD as documented above.

## Out of Scope

- OTA payment/refund, commission/payout/reconciliation, and OTA cancellation events.
- Session configuration, normal check-in/finalization, booking creation/deposit flow.
- Default credit, automatic link renewal, lower-price credit/refund, customer
  self-service reschedule or partial cancellation/refund.
- Accounting, tax, general ledger, debt collection, or general reconciliation.
