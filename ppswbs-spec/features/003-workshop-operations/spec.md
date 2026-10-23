# Feature Specification: Workshop Operations

**Feature**: `003-workshop-operations`  
**Status**: Draft — not implemented  
**Source**: FE-03 in `../../research.md`

## Scope

This feature owns Manager and Staff operation of published workshop sessions:
session/branch capacity, Staff assignment, individual attendance-ticket
check-in, audited exception handling and attendance finalization. It consumes
confirmed direct bookings from feature 002; it does not create customer bookings,
collect money, perform refunds or connect to OTAs.

## Clarifications

### Session 2026-10-23

- Q: Who may finalize attendance for a session and correct it later? → A: A Staff member assigned to that session or a Manager may finalize attendance. Only a Manager may correct attendance after finalization, and the correction requires audit evidence.
- Q: What happens when Manager cancels a session that already has valid tickets? → A: Manager marks the session `CANCELLATION_REQUESTED`, stops its operations and creates an audited handoff case. Feature 004 owns customer notification, manual reschedule, refund, QR replacement and capacity consequences.
- Q: Which Staff may perform normal ticket check-in? → A: Only a Staff member assigned to that session or a Manager may perform normal check-in. Check-in override remains separately authorized and audited.
- Q: Which commitments constrain a capacity reduction? → A: Manager may not reduce capacity below the total of confirmed attendance tickets and unexpired payment capacity holds. An expired hold no longer constrains capacity.
- Q: May check-in override move a ticket to a different session or branch? → A: No. Override is limited to verification exceptions for an unused valid ticket at its assigned session and branch. A change of session or branch is handled by feature 004.

## Business model

- A booking is the commercial order. A workshop item is the product being made;
  an attendance ticket is one person's QR right to join its session.
- Capacity is counted from active attendance tickets in each session.
- A Staff member normally checks in one ticket by QR. Individual `ticket_code`
  is a manual fallback; `booking_code` only finds a booking and never checks in
  an unspecified ticket.
- Ring workshops have no `check_in_closes_at`. Guests may be checked in when
  they arrive, including late arrivals, until attendance is finalized. Check-in
  records arrival only; Staff controls waiting seats and workshop start onsite.
- `check_in_opens_at`, if configured, is informational only and never blocks
  check-in.

## User scenarios

### Manager configures an operable session

Manager creates or updates a session's branch, time, capacity, sale state and
eligible Staff. Capacity cannot be reduced below confirmed attendance tickets
plus unexpired payment capacity holds. An expired hold no longer constrains
capacity. Every operational configuration change is audited.

### Manager cancels an operable session

Manager may move a session with valid tickets to `CANCELLATION_REQUESTED`. This
immediately stops normal operation and check-in, but does not revoke tickets,
move customers, release capacity or refund money in this feature. The system
creates an audited handoff case for feature 004 to resolve those consequences.

### Staff checks in one attendee

An assigned Staff member or Manager scans a valid QR for the correct
session/branch. The system records the ticket, Staff and time and prevents
reuse. A manual ticket-code lookup produces the same ticket-specific outcome.

### Authorized Staff handles an exception

Manager or Staff with `workshop.checkin.override` selects a particular unused
ticket for an exceptional case, states a standardized reason and leaves audit
evidence. The ticket must still be valid for that session and branch; override
cannot be used as a session or branch change. A booking code alone is never
enough to check in every ticket.

### Staff finalizes attendance

After the session, an assigned Staff member or Manager reviews attendance and
finalizes it. Checked-in tickets become `COMPLETED`; unchecked tickets become
`NO_SHOW`. Only Manager may make a later correction, with before/after audit
evidence.

## Functional requirements

- **FR-001**: The system MUST keep capacity, Staff assignment and attendance
  isolated by session and branch.
- **FR-001a**: The system MUST reject a capacity reduction below confirmed
  attendance tickets plus unexpired payment capacity holds for that session.
- **FR-002**: The system MUST create exactly one check-in record per ticket per
  session and reject duplicate normal scans.
- **FR-003**: The system MUST validate ticket state, session and branch before
  normal check-in and MUST permit it only to a Staff member assigned to that
  session or Manager.
- **FR-004**: The system MUST require `workshop.checkin.override`, a reason and
  audit record for exceptional/manual check-in; override MUST not bypass the
  ticket's assigned session or branch.
- **FR-005**: The system MUST not block check-in merely because a workshop has
  started; it MUST block normal changes after attendance is finalized.
- **FR-006**: The system MUST allow authorized finalization to assign `NO_SHOW`
  only to unchecked tickets of that session; finalization is allowed to the
  assigned Staff member or Manager only.
- **FR-007**: The system MUST record actor, time, source and correlation data
  for check-ins, finalization and corrections.
- **FR-008**: The system MUST allow only Manager to correct finalized attendance
  and MUST preserve the before/after state and correction reason in the audit
  record.
- **FR-009**: The system MUST allow only Manager to request cancellation of an
  operable session; it MUST stop normal operation/check-in and create an audited
  handoff without performing reschedule, refund, ticket revocation or capacity
  release in this feature.

## Out of scope

Workshop checkout/bills, payment collection, refund, customer self-service
changes, OTA callbacks and ticket delivery to individual participants.
