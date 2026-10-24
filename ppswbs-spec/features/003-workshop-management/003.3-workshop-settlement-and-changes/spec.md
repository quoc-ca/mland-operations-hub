# Feature Specification: Workshop Settlement and Changes

**Feature**: `003.3-workshop-settlement-and-changes`  
**Status**: Draft — not implemented  
**Source**: FE-03 in `../../../research.md`

## Scope

This feature owns direct-Mland financial actions after a workshop item is
booked: post-session add-ons and final bills, in-store payment, exceptional
payment links, manual reschedule, cancellation and refund. It consumes the
booking core and operations results; it does not create sessions or integrate
OTAs.

## Business rules

- A direct booking pays a 50% deposit of its frozen base workshop price. Deposit
  is allocated to workshop items.
- After an item's session, Staff/Manager assesses its additional material or
  service charges. The item's final bill is its remaining base amount plus its
  assessed charges.
- The normal policy is payment at the counter immediately after workshop and
  before the finished item is released. Mland does not grant default credit.
- A Manager-approved payment-link exception lasts 24 hours and receives one
  reminder before expiry. After expiry no link is automatically renewed; the
  shop retains the product and Manager action is audited.
- Customer self-service has no reschedule path. A customer may request
  cancellation/refund of an entire booking, then make a new booking.
- Customer-requested refund of a whole eligible booking is 100% of paid deposit
  at least 72 hours before its earliest session, 50% from 24 to under 72 hours,
  and 0% below 24 hours, for no-show or once any ticket has checked in.
- Manager may manually move one un-used workshop item to a session with capacity
  in the same package/configuration and participant count. A higher current
  base price is added to the final bill; a lower price does not create refund or
  credit in V1.
- If Mland cancels one item and no suitable replacement is accepted, Manager may
  refund payments allocated to that item as an audited Mland-caused exception.

## User scenarios

### Staff settles an item at the counter

Staff finalizes charge lines, presents a bill and records a permitted payment.
The item is released only after successful payment, unless a Manager exception
is recorded.

### Customer cancels a whole booking

The system verifies that every item/ticket is unused and evaluates the policy
snapshot using the earliest session start. It creates the applicable gateway
refund and revokes unused tickets/releases future capacity only after the refund
outcome is confirmed.

### Manager manually reschedules an item

Manager approves a customer request, validates capacity and price conditions,
moves every attendance ticket of the item atomically, revokes old QR codes and
issues replacement QR codes with audit.

## Functional requirements

- **FR-001**: The system MUST keep immutable charge, payment, void and adjustment
  records for every final bill.
- **FR-002**: The system MUST calculate each item's final balance from its
  allocated deposit, remaining base amount and approved charge lines.
- **FR-003**: The system MUST prevent product release before final payment except
  through an authorized, audited exception.
- **FR-004**: The system MUST apply the direct-booking refund policy snapshot and
  never offer customer self-service item/ticket refund.
- **FR-005**: The system MUST distinguish a whole-booking refund from a
  Manager-only Mland-caused item refund.
- **FR-006**: The system MUST make reschedule, price difference, QR replacement
  and session-capacity changes atomic for an approved workshop item move.

## Out of scope

OTA customer payments/refunds, OTA commission/payout reconciliation, session
configuration and normal check-in implementation.
