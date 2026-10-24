# Feature Specification: Workshop Operations

**Feature**: `003.2-workshop-operations`  
**Status**: Draft — not implemented  
**Source**: FE-03 in `../../../research.md`

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
### Session 2026-10-23

- Q: Which operational states does a session use? → A: The lifecycle is `DRAFT → PUBLISHED → SALES_PAUSED → IN_PROGRESS → ATTENDANCE_FINALIZED`; sales may resume from `SALES_PAUSED`, and `CANCELLATION_REQUESTED` is a pre-finalization cancellation branch.
- Q: When may a customer check in? → A: A valid ticket may be checked in only at or after the Manager-configured `check_in_opens_at`, whose default is 60 minutes before the session start, while the session is `PUBLISHED` or `IN_PROGRESS`. There is no automatic check-in closing time; check-in ends when attendance is finalized.
- Q: May Manager directly change the time or branch of a session with confirmed tickets or unexpired payment holds? → A: No. Direct time/branch changes are allowed only when neither exists. Otherwise Manager uses `CANCELLATION_REQUESTED`, and feature 004 resolves customer notification, reschedule/refund and ticket consequences with audit.
- Q: How must exceptional operational actions record their audit reason? → A: Check-in override, finalized-attendance correction and session cancellation request require a standardized reason code. A short note or evidence reference is optional or required by the chosen reason code.
- Q: What customer information may Staff see during ticket or booking lookup? → A: Check-in displays only ticket code, session/branch, ticket status and check-in result. Booking lookup displays its tickets and their used/unused status; it does not expose the booking contact's full email or phone number.

### Session 2026-10-23

- Q: Which state follows `CANCELLATION_REQUESTED` after all affected customer cases are resolved? → A: Feature 004 reports completion only after all affected customers are resolved or have an audited Manager decision; the session then becomes terminal `CANCELLED` and cannot operate or check in again.
- Q: Does V1 support check-in while Mland is offline? → A: No. V1 check-in is online only; it has no offline scan queue. Staff uses a backup connection or waits for recovery to preserve ticket reuse protection and audit consistency.
- Q: Must a session have an assigned Staff member before publication? → A: No. Manager may publish without Staff assignment, but the system must not start onsite operation or allow check-in until it has an active assigned Staff member or a Manager recorded as operational lead.

### Session 2026-10-24

- Q: What happens to unexpired payment capacity holds when Manager pauses sales? → A: `SALES_PAUSED` rejects new bookings and holds but preserves unexpired holds created before the pause. Those holds may complete payment until their existing deadline, then expire and release capacity normally.
- Q: Who may reverse an erroneous check-in? → A: Only Manager may reverse a recorded check-in before or after attendance finalization. Before finalization, the ticket returns to not checked-in and may be scanned again. After finalization, Manager must select the correct final attendance (`CHECKED_IN` or `NO_SHOW`). The reversal requires a standardized reason code and preserves before/after audit evidence rather than deleting the original scan.
- Q: What is shown when a ticket is scanned again after check-in? → A: The result states that the ticket is already checked in and shows its most recent check-in time only; it does not expose booking-contact data or the identity of the Staff member who scanned it.
- Q: Does a ticket need a separate `COMPLETED` or `ATTENDED` status? → A: No. A ticket remains `CHECKED_IN` after attendance finalization; an unchecked ticket becomes `NO_SHOW`. Workshop closure is inferred from `CHECKED_IN` plus the corresponding final bill being `PAID`, while the session itself records `ATTENDANCE_FINALIZED`.
- Q: Is attendance finalized automatically at the scheduled end time? → A: No. An assigned Staff member or Manager manually finalizes the entire session after it actually ends. Scheduled end time is informational only.

### Session 2026-10-24

- Q: May a session return to `PUBLISHED` or `SALES_PAUSED` after it enters `IN_PROGRESS`? → A: No. `IN_PROGRESS` is one-way: it may transition only to `ATTENDANCE_FINALIZED` or, for an interruption before finalization, `CANCELLATION_REQUESTED`. It must never reopen sales.
- Q: When may a session with no checked-in ticket be finalized? → A: The same as any session: only while it is `IN_PROGRESS`, after the assigned Staff member or Manager confirms it has actually ended. It may finish earlier or later than scheduled; finalization is not gated by `scheduled_end_at`, requires confirmation and audit, and marks every unchecked ticket `NO_SHOW`.
- Q: Who may start onsite operation by moving a session to `IN_PROGRESS`? → A: An active Staff member assigned to that session or its recorded Manager operational lead may do so, with actor/time/previous-state audit. Configuration, sale-state management and cancellation remain Manager-only.
- Q: May Manager change Staff assignment while a session is `IN_PROGRESS`? → A: Yes. Manager may add or remove assigned Staff for a handover, with audit and immediate update of normal check-in authority, but may not remove the final responsible operator unless a Manager operational lead remains active.

## Business model

- A booking is the commercial order. A workshop item is the product being made;
  an attendance ticket is one person's QR right to join its session.
- Capacity is counted from active attendance tickets in each session.
- A Staff member normally checks in one ticket by QR. Individual `ticket_code`
  is a manual fallback; `booking_code` only finds a booking and never checks in
  an unspecified ticket.
- V1 check-in requires an online connection to Mland. It does not persist
  offline scans for later synchronization.
- Ring workshops have no `check_in_closes_at`. A valid ticket may be checked in
  only at or after the Manager-configured `check_in_opens_at`, which defaults
  to 60 minutes before session start. Guests may still be checked in when late,
  until attendance is finalized. Check-in records arrival only; Staff controls
  waiting seats and workshop start onsite.
- A session uses `DRAFT`, `PUBLISHED`, `SALES_PAUSED`, `IN_PROGRESS`,
  `ATTENDANCE_FINALIZED`, `CANCELLATION_REQUESTED`, and `CANCELLED`. Only `PUBLISHED`
  accepts new bookings. `SALES_PAUSED` stops new bookings and capacity holds but
  preserves existing ticket commitments and unexpired holds until their existing
  deadlines; it may return to `PUBLISHED`. A session becomes
  `IN_PROGRESS` when onsite operation starts and may not return to a sales state.
  From `IN_PROGRESS`, it may proceed only to `ATTENDANCE_FINALIZED` through a
  manual whole-session action by assigned Staff or Manager after they confirm
  that the session has actually ended, or to `CANCELLATION_REQUESTED` for an
  interruption before finalization. This action is available only while the
  session is `IN_PROGRESS`, may occur earlier or later than scheduled, and
  records audit evidence. Scheduled end time does not finalize attendance
  automatically. `CANCELLATION_REQUESTED` is available before
  finalization and follows the feature-004 handoff rule; after that handling is
  complete, it becomes terminal `CANCELLED`.
- A session may be `PUBLISHED` without an operational lead. Before it becomes
  `IN_PROGRESS` or allows check-in, it needs an active Staff assignment or a
  Manager recorded as its operational lead.

## User scenarios

### Manager configures an operable session

Manager creates or updates a session's branch, time, capacity, sale state and
eligible Staff. Capacity cannot be reduced below confirmed attendance tickets
plus unexpired payment capacity holds. An expired hold no longer constrains
capacity. Every operational configuration change is audited. The Manager may
publish a draft, pause/resume sales and request cancellation before attendance
finalization. Publication does not require a Staff assignment. An active
assigned Staff member or the recorded Manager operational lead may start onsite
operation; this records the actor, time and previous state. The Manager may
directly change a session's time or branch only when it has neither confirmed
tickets nor unexpired payment capacity holds; otherwise the Manager must use
the `CANCELLATION_REQUESTED` handoff. During `IN_PROGRESS`, Manager may change
Staff assignment for a handover, but cannot leave the session without an active
assigned Staff member or Manager operational lead; the assignment change and its
immediate effect on check-in authority are audited.

### Manager cancels an operable session

Manager may move a session with valid tickets to `CANCELLATION_REQUESTED`. This
immediately stops normal operation and check-in, but does not revoke tickets,
move customers, release capacity or refund money in this feature. The system
creates an audited handoff case for feature 004 to resolve those consequences.
After feature 004 reports that every affected customer case is resolved or has
an audited Manager decision, the session becomes terminal `CANCELLED`.

### Staff checks in one attendee

An assigned Staff member or the recorded Manager operational lead scans a valid QR for the correct
session/branch at or after `check_in_opens_at`. The system records the ticket,
Staff and time and prevents reuse. A manual ticket-code lookup produces the
same ticket-specific outcome. The result shows only ticket code,
session/branch, ticket status and check-in outcome.

### Staff scans an already checked-in ticket

The result states that the ticket is already checked in and shows its most
recent check-in time. It does not expose booking-contact data or the identity
of the Staff member who scanned it.

### Manager reverses an erroneous check-in

Only Manager may reverse a recorded check-in. The original scan remains in the
audit trail and the reversal requires a standardized reason code and before/after
evidence. Before attendance finalization the ticket returns to not checked-in
and may be scanned again. After finalization, Manager must select the corrected
final attendance: `CHECKED_IN` or `NO_SHOW`.

### Staff handles loss of connectivity

If the check-in device cannot reach Mland, it does not record a local scan or
queue one for later. Staff uses a backup connection or waits for recovery.

### Authorized Staff handles an exception

Manager or Staff with `workshop.checkin.override` selects a particular unused
ticket for an exceptional case, selects a standardized reason code and leaves
the required audit evidence. The reason code may require a short note or an
evidence reference. The ticket must still be valid for that session and branch;
override cannot be used as a session or branch change. A booking code alone is
never enough to check in every ticket. Booking lookup may show its ticket set
and each ticket's used/unused status, but not the booking contact's full email
or phone number.

### Staff finalizes attendance

After the session actually ends, an assigned Staff member or the recorded
Manager operational lead reviews attendance and finalizes the whole session in
one action. Checked-in tickets remain
`CHECKED_IN`; unchecked tickets become `NO_SHOW`. Only Manager may make a later
correction, with before/after audit evidence.

## Functional requirements

- **FR-001**: The system MUST keep capacity, Staff assignment and attendance
  isolated by session and branch.
- **FR-001a**: The system MUST reject a capacity reduction below confirmed
  attendance tickets plus unexpired payment capacity holds for that session.
- **FR-002**: The system MUST create exactly one check-in record per ticket per
  session and reject duplicate normal scans.
- **FR-002a**: The system MUST require an online Mland connection for V1
  check-in and MUST NOT create an offline scan queue.
- **FR-002b**: The system MUST allow only Manager to reverse a recorded
  check-in, before or after attendance finalization, with a standardized reason
  code and immutable before/after audit evidence; it MUST NOT delete the
  original scan. Before finalization it MUST return the ticket to not checked-in;
  after finalization it MUST require Manager to select `CHECKED_IN` or `NO_SHOW`.
- **FR-002c**: When a ticket is scanned after it has been checked in, the system
  MUST show only its already-checked-in status and most recent check-in time;
  it MUST NOT expose booking-contact data or the prior Staff actor.
- **FR-003**: The system MUST validate ticket state, session and branch before
  normal check-in and MUST permit it only to a Staff member assigned to that
  session or the Manager recorded as that session's operational lead.
- **FR-003a**: The system MUST limit ticket lookup/check-in results to ticket
  code, session/branch, ticket status and check-in outcome; booking lookup may
  show ticket used/unused status but MUST NOT expose the booking contact's full
  email or phone number.
- **FR-004**: The system MUST require `workshop.checkin.override`, a reason and
  audit record for exceptional/manual check-in; override MUST not bypass the
  ticket's assigned session or branch.
- **FR-005**: The system MUST not block check-in merely because a workshop has
  started; it MUST reject normal check-in before `check_in_opens_at` and after
  attendance is finalized.
- **FR-005a**: The system MUST default `check_in_opens_at` to 60 minutes before
  session start and allow Manager to configure it per session.
- **FR-006**: The system MUST allow authorized finalization to assign `NO_SHOW`
  only to unchecked tickets of that session; finalization is allowed to the
  assigned Staff member or the recorded Manager operational lead only, applies
  to the whole session in one action, and MUST NOT occur automatically at the
  scheduled end time. It MUST be allowed only while the session is
  `IN_PROGRESS` after the actor confirms that the session has actually ended;
  it may occur before or after `scheduled_end_at` and MUST record that
  confirmation in the audit trail.
- **FR-007**: The system MUST record actor, time, source and correlation data
  for check-ins, finalization and corrections.
- **FR-007a**: The system MUST require a standardized reason code for a
  check-in override, finalized-attendance correction or session cancellation
  request, and MUST require a short note or evidence reference whenever that
  reason code defines it.
- **FR-008**: The system MUST allow only Manager to correct finalized attendance
  and MUST preserve the before/after state and correction reason in the audit
  record.
- **FR-009**: The system MUST allow only Manager to request cancellation of an
  operable session; it MUST stop normal operation/check-in and create an audited
  handoff without performing reschedule, refund, ticket revocation or capacity
  release in this feature.
- **FR-010**: The system MUST enforce the session lifecycle `DRAFT`,
  `PUBLISHED`, `SALES_PAUSED`, `IN_PROGRESS`, `ATTENDANCE_FINALIZED` and
  `CANCELLATION_REQUESTED`, and `CANCELLED`; only `PUBLISHED` may accept new
  bookings, and only Manager may publish, pause/resume sales or request
  cancellation. Once a session enters `IN_PROGRESS`, it MUST NOT return to
  `PUBLISHED` or `SALES_PAUSED`; before finalization it may move only to
  `ATTENDANCE_FINALIZED` or `CANCELLATION_REQUESTED`.
- **FR-010b**: The system MAY publish a session without Staff assignment, but
  MUST reject transition to `IN_PROGRESS` and normal check-in until the session
  has an active assigned Staff member or a Manager recorded as operational lead.
- **FR-010d**: The system MUST allow only an active Staff member assigned to
  the session or its recorded Manager operational lead to transition that
  session to `IN_PROGRESS`, and MUST audit the actor, timestamp and previous
  state.
- **FR-010e**: While a session is `IN_PROGRESS`, the system MUST allow only
  Manager to add or remove its assigned Staff, immediately update normal
  check-in authority and audit the change. It MUST reject removing the final
  responsible operator unless an active Manager operational lead remains.
- **FR-010c**: The system MUST reject new bookings and capacity holds while a
  session is `SALES_PAUSED`, but MUST allow an unexpired hold created before
  the pause to complete payment until its existing deadline.
- **FR-010a**: The system MUST transition `CANCELLATION_REQUESTED` to terminal
  `CANCELLED` only after feature 004 reports all affected customer cases
  resolved or an audited Manager decision for each; `CANCELLED` MUST reject
  normal operation and check-in.
- **FR-011**: The system MUST reject a direct session time or branch change
  whenever confirmed tickets or unexpired payment capacity holds exist; Manager
  MUST use the audited `CANCELLATION_REQUESTED` handoff instead.

## Out of scope

Workshop checkout/bills, payment collection, refund, customer self-service
changes, OTA callbacks and ticket delivery to individual participants.
