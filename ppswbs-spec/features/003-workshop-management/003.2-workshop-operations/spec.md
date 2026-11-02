# Feature Specification: Workshop Operations

**Date created**: 2026-10-24
**Date updated**: 2026-10-31
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature 003 authorization decision (2026-10-29)**: Every active canonical
`MANAGER` has global location authority for Manager-authorized actions across
all 003 subfeatures. `activeLocationIds` does not restrict Manager authority;
Staff actions remain location-scoped, Member ownership remains enforced, and
Admin-only technical actions remain Admin-only. The Central Manager distinction
is removed.

**Feature Branch**: `003.2-workshop-operations`

**Status**: Draft — not implemented  

**Input**: Confirmed Workshop Operations decisions from the responsible member,
recorded in this document's Clarifications; implementation-design evidence is in
[research.md](./research.md).

## Authorization and lifecycle rebase (2026-10-28)

This amendment supersedes earlier assignment/capability/operational-lead rules
where they conflict with the following V1 decisions:

- Authorization is role-based through the public Account facade. Operations
  consumes canonical role, account status and active location IDs; it does not
  call a capability decision method and does not use
  `workshop.checkin.override`.
- An active `STAFF` may operate any session whose canonical `locationId` is in
  the actor's `activeLocationIds`. A session-level Staff assignment is not an
  authorization prerequisite.
- An active `MANAGER` has global Workshop Operations authority across all
  locations and sessions. No location-scope, session-assignment or
  operational-lead check is required for Manager authority.
- `operational lead` is retired from the target V1 design. Assignment/lead
  endpoints and lead-only authority are not part of the target contract.
- At `checkInOpensAt`, check-in becomes available while an eligible `PUBLISHED`
  session remains in that state. At the session's scheduled start time, a
  scheduler automatically transitions it to `IN_PROGRESS`. The existing
  state-transition command remains only for explicit early start or recovery;
  it is not the normal scheduled path.
- OTA sale state is closed when the session enters `IN_PROGRESS`; the automatic
  scheduled-start transition publishes the complete closed post-state snapshot
  in the same Operations transaction. `ATTENDANCE_FINALIZED` remains closed,
  but does not create a second snapshot/version when the observable snapshot is
  otherwise unchanged. Manual early start follows the same publication rule.
- Attendance correction remains Manager-only. An active Manager may correct
  attendance globally; Staff may not correct attendance.
- A finalized session records its finalization timestamp. Manager attendance
  corrections remain available until 24 hours after the latest required
  session for the booking is finalized. At that exclusive deadline, Operations
  rejects further corrections automatically; no Manager sealing action is
  required. Settlement may treat the booking-wide attendance projection as
  terminal only after every required session is finalized and this correction
  window has closed.

Any older paragraph, example or scenario that requires a Staff assignment,
Manager operational lead, capability key or manual scheduled start is legacy
wording and must be read through this amendment until the affected section is
rewritten.

## Scope

This feature owns the Workshop Package catalogue/configuration and publication,
plus Manager and Staff operation of published workshop sessions: session/location
capacity, optional Staff roster records, individual attendance-ticket check-in, audited
exception handling and attendance finalization. It consumes confirmed direct
bookings from feature 003.1; it does not create customer bookings, collect money,
perform refunds or connect directly to external OTAs.

## Clarifications

### Session 2026-10-23

- Q: Who may finalize attendance for a session and correct it later? → A: A Staff member assigned to that session or a Manager may finalize attendance. Only a Manager may correct attendance after finalization, and the correction requires audit evidence.
- Q: What happens when Manager cancels a session that already has valid tickets? → A: Manager marks the session `CANCELLATION_REQUESTED`, stops its operations and creates an audited handoff case. Feature 003.3 owns customer notification, manual reschedule, refund, QR replacement and capacity consequences.
- Q: Which Staff may perform normal ticket check-in? → A: An active Staff member whose `activeLocationIds` contains the session location, or any active Manager, may perform normal check-in. No session assignment or capability key is required.
- Q: Which commitments constrain a capacity reduction? → A: Manager may not reduce capacity below the total of confirmed attendance tickets and unexpired payment capacity holds. An expired hold no longer constrains capacity.
- Q: May check-in override move a ticket to a different session or location? → A: No. Override is limited to verification exceptions for an unused valid ticket at its assigned session and location. A change of session or location is handled by feature 003.3.
### Session 2026-10-23

- Q: Which operational states does a session use? → A: The lifecycle is `DRAFT → PUBLISHED → SALES_PAUSED → IN_PROGRESS → ATTENDANCE_FINALIZED`; sales may resume from `SALES_PAUSED`, and `CANCELLATION_REQUESTED` is a pre-finalization cancellation branch.
- Q: When may a customer check in? → A: A valid ticket may be checked in only at or after the Manager-configured `check_in_opens_at`, whose default is 60 minutes before the session start, while the session is `PUBLISHED` or `IN_PROGRESS`. There is no automatic check-in closing time; check-in ends when attendance is finalized.
- Q: May Manager directly change the slot or branch of a session with confirmed tickets or unexpired payment holds? → A: No. Direct slot/branch changes are allowed only when neither exists. Otherwise Manager uses `CANCELLATION_REQUESTED`, and feature 003.3 resolves customer notification, reschedule/refund and ticket consequences with audit.
- Q: How must exceptional operational actions record their audit reason? → A: Check-in override, finalized-attendance correction and session cancellation request require a standardized reason code. A short note or evidence reference is optional or required by the chosen reason code.
- Q: What customer information may Staff see during ticket or booking lookup? → A: Check-in displays only ticket code, session/branch, ticket status and check-in result. Booking lookup displays its tickets and their used/unused status; it does not expose the booking contact's full email or phone number.

### Session 2026-10-23

- Q: Which state follows `CANCELLATION_REQUESTED` after all affected customer cases are resolved? → A: Feature 003.3 reports completion only after all affected customers are resolved or have an audited Manager decision; the session then becomes terminal `CANCELLED` and cannot operate or check in again.
- Q: Does V1 support check-in while Mland is offline? → A: No. V1 check-in is online only; it has no offline scan queue. Staff uses a backup connection or waits for recovery to preserve ticket reuse protection and audit consistency.
- Q: Must a session have an assigned Staff member before publication or start? → A: No. Check-in opens at `checkInOpensAt` while the session remains `PUBLISHED`; a scheduled-start job moves the eligible session to `IN_PROGRESS` at its scheduled start time. Active Staff in the session location or any active Manager may operate it without a session assignment or operational-lead record.

### Session 2026-10-24

- Q: What happens to unexpired payment capacity holds when Manager pauses sales? → A: `SALES_PAUSED` rejects new bookings and holds but preserves unexpired holds created before the pause. Those holds may complete payment until their existing deadline, then expire and release capacity normally.
- Q: Who may reverse an erroneous check-in? → A: Only Manager may reverse a recorded check-in before or after attendance finalization. Before finalization, the ticket returns to not checked-in and may be scanned again. After finalization, Manager must select the correct final attendance (`CHECKED_IN` or `NO_SHOW`). The reversal requires a standardized reason code and preserves before/after audit evidence rather than deleting the original scan.
- Q: What is shown when a ticket is scanned again after check-in? → A: The result states that the ticket is already checked in and shows its most recent check-in time only; it does not expose booking-contact data or the identity of the Staff member who scanned it.
- Q: Does a ticket need a separate `COMPLETED` or `ATTENDED` status? → A: No. A ticket remains `CHECKED_IN` after attendance finalization; an unchecked ticket becomes `NO_SHOW`. Workshop closure is inferred from `CHECKED_IN` plus the corresponding final bill being `PAID`, while the session itself records `ATTENDANCE_FINALIZED`.
- Q: Is attendance finalized automatically at the scheduled end time? → A: No. An authorized Staff member in the session location or any active Manager manually finalizes the entire session after it actually ends. Scheduled end time is informational only.

### Session 2026-10-24

- Q: May a session return to `PUBLISHED` or `SALES_PAUSED` after it enters `IN_PROGRESS`? → A: No. `IN_PROGRESS` is one-way: it may transition only to `ATTENDANCE_FINALIZED` or, for an interruption before finalization, `CANCELLATION_REQUESTED`. It must never reopen sales.
- Q: When may a session with no checked-in ticket be finalized? → A: The same as any session: only while it is `IN_PROGRESS`, after an authorized Staff member in the location or any active Manager confirms it has actually ended. It may finish earlier or later than scheduled; finalization is not gated by `scheduled_end_at`, requires confirmation and audit, and marks every unchecked ticket `NO_SHOW`.
- Q: Who may start onsite operation by moving a session to `IN_PROGRESS`? → A: A scheduler moves an eligible `PUBLISHED` session to `IN_PROGRESS` at `scheduledStartAt` and records a system audit event. An explicit state-transition command remains available to an active Staff in the session location or any active Manager for early start/recovery.
- Q: May Manager change Staff roster records while a session is `IN_PROGRESS`? → A: Optional roster changes are informational/audited only and never change authorization; active Staff authority comes from role/status and location scope.
- Q: Until when may Manager change `check_in_opens_at`? → A: Manager may configure it only while the session is `DRAFT`, `PUBLISHED` or `SALES_PAUSED`. It is locked once the session enters `IN_PROGRESS`.

### Session 2026-10-24

- Q: What happens to `check_in_opens_at` when Manager changes a session's configured slot? → A: A default value becomes 60 minutes before the new slot start time. A Manager-set override remains unchanged, but the system must show its old check-in time and a warning before saving the slot change so Manager can keep it or adjust it; the acknowledgement is audited.
- Q: Is there a session operational-lead role? → A: No. V1 gives every active Manager global Workshop Operations authority; no lead assignment or handover is required.
- Q: How is a session cancelled when it has no confirmed ticket or unexpired payment hold? → A: Manager transitions it directly to terminal `CANCELLED` with audit. Only a session with either commitment enters `CANCELLATION_REQUESTED` and the feature-003.3 handoff.

### Session 2026-10-24

- Q: Which tickets appear when Staff looks up a multi-session booking by `booking_code`? → A: Staff sees only tickets for the session and location they are operating; other tickets are not shown and cannot be checked in. Manager uses a separate audited management lookup to view the full ticket set when support requires it.
- Q: Who may request ticket resend from the check-in flow? → A: Active Staff in the session location and any active Manager may request resend only to the booking contact's existing delivery channel. Ticket delivery remains owned by feature 003.1; the request is rate-limited and audited.
- Q: Who may override check-in after ticket resend fails? → A: An active Staff whose location scope contains the session location or any active Manager may override each unused ticket only for its assigned session and location, using the standardized reason `TICKET_REDELIVERY_UNAVAILABLE`, a short note and audit linked to the failed resend outcome.
- Q: When is ticket resend unavailable for override purposes? → A: When delivery reports an error, or the customer confirms they did not receive or cannot access the ticket after resend. No fixed waiting period applies; Staff records a short note and the system audits the reason and outcome.
- Q: Which tickets are included when Staff requests resend from the check-in flow? → A: The system resends the full ticket set in the booking to the booking contact's existing delivery channel, including tickets for other sessions or locations. Staff check-in authority remains limited to the current session and location.

### Session 2026-10-24

- Q: What resend limit applies from the check-in flow? → A: At most three resend requests per booking in 15 minutes. After the limit is reached, the system sends no further request; an authorized actor may use the documented override path if resend is unavailable.
- Q: Which ticket states are included in a booking resend? → A: Only active, unused tickets. The system excludes tickets that are cancelled, revoked, refunded, `NO_SHOW` or already `CHECKED_IN`.
- Q: In which session states may onsite staff request ticket resend? → A: `PUBLISHED`, `SALES_PAUSED` and `IN_PROGRESS`, regardless of `check_in_opens_at`. Resend is rejected in `CANCELLATION_REQUESTED`, `CANCELLED` and `ATTENDANCE_FINALIZED`.
- Q: Which actors may override check-in for a session? → A: An active Staff member must have the session location in `activeLocationIds`; an active Manager has global authority. Neither role may override an invalid ticket or move it to a different session/location.
- Q: What does Staff see when booking lookup has no eligible ticket for the current session and branch? → A: Only a generic no-eligible-ticket result. It does not reveal another ticket, session or branch; Manager uses the separate audited management lookup when support requires it.

### Session 2026-10-24

- Q: If two Staff members scan the same valid ticket nearly simultaneously, how is the second scan handled? → A: Check-in is atomic: exactly one request succeeds; the other returns already checked-in with the recorded check-in time.
- Q: How are customers organized within a fixed branch time slot? → A: A branch slot has one shared capacity and may contain several independently operated groups. Shop assigns groups after booking; groups do not create customer-bookable sessions or separate capacity pools. A Staff member may handle more than one group in the same slot, and booking remains open until the shared slot capacity is full, including close to its start time.
- Q: Does V1 store attendees' operating-group assignments? → A: No. V1 manages only the session/slot, shared capacity, tickets and session Staff. Shop divides attendees into groups onsite without storing group, ticket-to-group or Staff-to-group assignments in Mland.
- Q: How is a session's time selected? → A: Manager selects a configured fixed slot for the branch. The session's start and end times come from that slot and cannot be entered or edited freely on the individual session.
- Q: May more than one session exist for the same branch, date and slot? → A: No. There is exactly one session for each branch, date and configured slot. Onsite operating groups do not create additional sessions.

### Session 2026-10-24

- Q: Who manages fixed slots and what happens when a slot time changes? → A: Manager manages slots per branch. A slot-time change applies only to sessions created after the change; an existing session keeps its inherited scheduled times.
- Q: What happens to existing future sessions when a configured slot is stopped? → A: The slot becomes unavailable for new sessions only. Existing sessions, including future sessions, remain unchanged; Manager uses the audited session-cancellation flow if one must be cancelled.
- Q: How are future sessions created in V1? → A: Manager may create an individual session or bulk-create independent sessions by branch, selected slots, date range and applicable weekdays. Bulk creation is a one-time generator, not an indefinitely running recurrence; later schedule or slot changes never alter sessions already created.
- Q: Where does a new session's capacity come from? → A: Each branch slot has a default capacity. A new session copies it and Manager may adjust that session's capacity subject to the capacity-commitment rule, without changing the slot or other sessions.
- Q: What happens when bulk session generation encounters an existing branch/date/slot combination? → A: It creates every missing session, skips each existing combination without overwriting it and reports the created and skipped results.

### Session 2026-10-24

- Q: May a Manager manage every branch? → A: No. A Manager may manage only branches assigned to them. A higher-privilege actor may manage all branches; Staff authority remains limited to explicitly assigned sessions.
- Q: May Manager create a session for a slot that has already started or passed? → A: No. Manager may create only sessions whose slot start is in the future; booking may still remain available close to that start under feature 003.1 policy.
- Q: Which timezone do Mland slots and sessions use? → A: `Asia/Ho_Chi_Minh` for all current Vietnam branches.
- Q: What state does a newly created session start in? → A: Manager may choose `DRAFT` or `PUBLISHED` when creating individual or bulk sessions; the default is `DRAFT`.
- Q: How are audit reason codes managed in V1? → A: The system controls a fixed reason-code catalogue; each code may require a supplementary note or evidence reference.

### Session 2026-10-24

- Q: What happens if an assigned Staff member or operational-lead Manager becomes inactive? → A: Their operational authority is revoked immediately while assignment/lead history remains. The session needs another active assigned Staff member or active operational lead before onsite operation continues.
- Q: Which feature owns Manager-to-branch assignments? → A: Account/personnel management owns their creation, change and revocation. Workshop operations only reads and enforces the assignment.
- Q: Does Account Management decide Workshop Operations permissions? → A: No. Account Management supplies canonical role, current status and active location-scope facts through its facade. Operations applies its own server-side action rules using those facts together with the target branch and its explicit Staff-session assignments; it never uses a display role, Firebase claim, or retired code as authority.
- Q: May a workshop slot span midnight? → A: No. A slot starts and ends on the same `Asia/Ho_Chi_Minh` calendar date. The current default daily slot starts are 09:00, 13:00 and 16:00.
- Q: What are the default daily slot times? → A: `09:00–12:00`, `13:00–16:00` and `16:00–19:00`. Workshop activity normally takes about 2 hours 30 minutes, and each slot retains 30 minutes of contingency time.
- Q: May a slot or session have zero capacity? → A: No. Capacity must be at least one; Manager uses `SALES_PAUSED` to stop accepting bookings.

### Session 2026-10-24

- Q: Does V1 have an audit reason code for an unlisted exception? → A: Yes. The fixed catalogue includes `OTHER_REQUIRES_NOTE`, which requires a clear note or evidence reference.
- Q: May Manager permanently delete a configured slot? → A: No. V1 retains the slot and Manager only changes whether it is active or inactive.
- Q: How far ahead may Manager create a session? → A: At most 90 days in the future.

### Session 2026-10-25

- Q: What sale-eligibility information does 003.2 provide to 003.1 before a
  booking? → A: For each requested session, 003.2 provides only a public
  snapshot of whether it exists and is customer-selectable, its sale state,
  branch, scheduled start/end, and remaining capacity. 003.1 uses that snapshot
  for booking rules but never reads 003.2 entities or repositories directly.
- Q: When a booking requests capacity in multiple sessions, may the capacity
  result be partial? → A: No. 003.2 decides the entire requested session set
  atomically: it holds the requested participant seats in every session or
  creates no hold in any session.
- Q: How does 003.2 commit capacity after 003.1 receives valid payment
  evidence? → A: 003.2 commits all and only the active holds of that booking as
  one result. If any expected hold is missing or expired, it commits none and
  003.1 must not confirm the booking.
- Q: How does 003.2 process a repeated or late request to release booking
  capacity holds? → A: It releases only the active holds once. A repeated or
  late request is an idempotent no-op and must not change capacity again.

- Q: When 003.2 makes session availability available to 003.4, must OTA
  sellable availability subtract active direct-Mland holds? → A: Yes. It is
  total capacity minus confirmed ticket seats and every active, unexpired
  direct-Mland capacity hold. A release or expiry makes those seats sellable
  again; 003.4 never calculates or writes the capacity itself.
- Q: Which 003.2 changes create an OTA availability/sale-state notification?
  → A: Only a completed local change that alters the sellable-availability or
  sale-state snapshot exposed to OTA. Active-hold creation, release, and expiry
  notify when they change availability; `SALES_PAUSED`, resumed sale, session
  cancellation, or closure notify when they change sale state. Committing a hold
  to a ticket creates no separate notification when the exposed snapshot is
  unchanged.
- Q: What minimum snapshot does 003.2 provide to 003.4 for availability and
  sale-state synchronization? → A: Only opaque session identity, branch scope,
  sellable availability, sale state, source change identity/version, and
  effective time. It includes no PII, Booking/payment data, ticket detail, or
  package/provider mapping; 003.4 owns its own mapping.
- Q: How do 003.2 and 003.4 handle duplicate, out-of-order, or missed
  availability notifications? → A: Delivery is at-least-once. 003.4 applies
  only a newer source version/change identity, ignores duplicate or older
  snapshots, and requests an authoritative 003.2 snapshot resync when it
  detects a gap or cannot establish order. It does not require exactly-once
  delivery.
- Q: What happens to a session when 003.4 cannot synchronize availability to
  one OTA? → A: 003.4 closes or suspends only the affected OTA mapping and
  creates reconciliation work. It does not change 003.2 session sale state,
  capacity, or direct-Mland booking behaviour.
- Q: When 003.4 detects a gap for one session, what scope must authoritative
  003.2 resync return? → A: The current complete snapshot of that one affected
  session at one source version: schedule, availability, and sale state. It does
  not return a whole branch or separate schedule/availability reads.
- Q: When 003.2 changes only internal session data that does not affect
  OTA-observable schedule, availability, or sale state, does it increase the
  session source version? → A: No. The OTA source version increases and a
  snapshot is sent only when OTA-observable schedule, sellable availability, or
  sale state changes.
- Q: In which lifecycle states must 003.2 start or continue making an
  OTA-observable snapshot available for a session? → A: It does not send
  `DRAFT`. It makes the snapshot available on `PUBLISHED` and publishes a
  complete post-state whenever schedule, sellable availability, or sale state
  changes. `SALES_PAUSED`, `IN_PROGRESS`, `ATTENDANCE_FINALIZED`,
  `CANCELLATION_REQUESTED` and `CANCELLED` have their defined sale state;
  `IN_PROGRESS` closes OTA sales. Scheduled start publishes that closure in the
  same transaction. `ATTENDANCE_FINALIZED` is already closed and does not
  advance the version unless another OTA-observable value changes.
- Q: If one completed 003.2 local change changes several OTA-observable values
  of the same session, how does its source version increase? → A: It increases
  exactly once for the transaction's complete post-state. The snapshot at that
  version contains the session's current schedule, availability, and sale state.
- Q: When 003.4 requests authoritative resync but the current session state has
  not changed, does 003.2 increase its source version? → A: No. It returns the
  current snapshot at its existing source version; 003.4 may retry or republish
  it without treating the resync as a business change.
- Q: What V1 mechanism carries an OTA-observable session snapshot from 003.2 to
  003.4? → A: A persistent internal Spring Modulith event publication recorded
  in the same Operations local transaction. 003.4 consumes it at-least-once and
  owns its separate durable outbound OTA delivery; V1 uses no broker.

- Q: Should a Silverclay participant have one QR ticket or one QR per session?
  → A: Each participant has one persistent QR ticket. It is valid for every
  required session of the package; attendance remains independently recorded
  for each ticket/session pair.
- Q: How is a persistent Silverclay QR validated at a session? → A: The
  scanner operates in its current session/branch context. It accepts the QR
  only when the ticket is entitled to that session and branch and has not
  already checked in at that session; it does not let the QR select a session.
- Q: What does a prior check-in at the first Silverclay session do to the
  second session? → A: Check-in is independent for each ticket/session pair.
  A repeat scan at the first session returns already checked-in there, while
  the same QR remains eligible for the second session until it is checked in
  there.
- Q: What ticket projection does Operations provide for Booking's customer
  delivery? → A: It provides only a delivery-safe projection: ticket identity
  or QR reference, ticket code, and entitled sessions with their branch and
  scheduled start/end. It excludes attendance, Staff/audit data and PII;
  003.1 adds its own booking and contact information plus its frozen package
  snapshot; it does not own the Workshop Package master.
- Q: How does Settlement know that a booking/package has completed every
  required session? → A: 003.2 is the authoritative owner of that conclusion.
  For the booking/package context requested by 003.3, it provides a minimal
  settlement-readiness result that is true only when every required session is
  `ATTENDANCE_FINALIZED`. It is false while any required session is not
  finalized, including when it is cancelled. The result includes no individual
  attendance, ticket, Staff, audit or PII detail; 003.3 must not infer it from
  scheduled end time or calculate it from per-session states. It also reports
  whether the 24-hour attendance-correction window has closed and whether any
  required ticket is `NO_SHOW`; Settlement emits a terminal Loyalty outcome
  only after the correction window closes.
- Q: May a Manager replace a booking's required session with a target session
  that is `SALES_PAUSED`? → A: No. Every replacement target must be future,
  `PUBLISHED`, customer-selectable and have capacity for the booking's complete
  participant count. If any source/target condition or any target capacity check
  fails, 003.2 applies no change to any required session, ticket entitlement or
  capacity. `SALES_PAUSED` has no Manager reschedule exception in V1.
- Q: May a Manager use the ordinary customer-requested session-change flow
  after a source session has started, finalized, or entered cancellation? → A:
  No. A source required session must still be future and must not be
  `IN_PROGRESS`, `ATTENDANCE_FINALIZED`, `CANCELLATION_REQUESTED` or
  `CANCELLED`. A `CANCELLATION_REQUESTED` session follows 003.3's separate
  Mland-caused cancellation remedy; the ordinary change flow never rewrites
  check-in or attendance history.

### Session 2026-10-26

- Q: Where does Workshop Operations persist its protection against duplicate facade commands? → A: It owns a dedicated idempotency ledger within the `workshopoperations` module. The ledger is separate from operational audit records and business aggregates such as holds and tickets.
- Q: Which duplicate-facade-command boundary is unique? → A: The unique boundary is `facadeAction` plus `operationReference`. The ledger stores a semantic request fingerprint: the same pair with equivalent content returns its original outcome; the same pair with different content is rejected safely.
- Q: What lifecycle does an idempotency record use? → A: It moves from `IN_PROGRESS` to `COMPLETED`. A completed record retains the terminal business outcome `ACCEPTED` or `REJECTED`; a technical or retryable failure rolls back rather than leaving a completed record, so the caller may retry the same command.
- Q: How does Operations handle an equivalent duplicate command while its first execution is still in progress? → A: It returns the typed safe outcome `IN_PROGRESS` and performs no second business side effect. The caller retries with the same `operationReference` and receives the original completed outcome after the first execution finishes.
- Q: How is `correlationReference` retained without replacing `operationReference`? → A: The idempotency ledger stores the original execution's `correlationReference` separately, and the business audit event for that execution uses the same value. An equivalent retry creates no new business audit event; its transport correlation belongs only to technical request logging.
- Q: What retention and crash-recovery policy applies to Operations idempotency records? → A: A `COMPLETED` record is retained for at least 24 hours before cleanup. A caller must not retry an operation reference after that retention window because duplicate protection is no longer guaranteed. `IN_PROGRESS` uses a configurable lease timeout; after the lease expires, an equivalent retry may reclaim the record for recovery from a crashed execution.
- Q: How does Operations prevent an expired claim from committing after an equivalent retry reclaims the idempotency record? → A: Each claim or reclaim has an incrementing claim version. Completing the record and committing its business effect require the current claim version; a request that has lost its version is rejected or rolled back.

### Session 2026-10-27

- Q: Are a Workshop Branch and a Workshop Location separate resources or the same resource? → A: They are the same canonical physical Mland workshop location. Every `activeLocationId` supplied by Account Management and every session/slot location reference use the canonical `workshop_location.id`. Operations MUST NOT create, maintain, or authorize against an independent branch identifier or a branch-to-location mapping.
- Q: Should Workshop Operations use `branch` or `location` in its public contract and data terminology? → A: It uses `location` throughout public API routes, DTO fields and schema terminology. “Branch” is a legacy synonym only in older prose awaiting rebase; it MUST NOT prescribe a separate resource or identifier.

### Session 2026-10-28

- Q: Which 003 subfeature owns the published Workshop Package catalogue and its required-session rules? → A: 003.2 Workshop Operations owns the published Workshop Package master configuration: description, public price, participant constraints, and required-session rules. It exposes a semantic package-discovery/read boundary to 003.1. 003.1 consumes that boundary and freezes the selected package and applicable price into its booking snapshot; it does not own or mutate the package master.
- Q: Does a Manager manage Workshop Packages through 003.2 REST APIs in V1? → A: Yes. 003.2 provides Manager-only REST management for Workshop Package configuration and publication. 003.1 remains the consumer for public package discovery through the internal semantic boundary and for booking snapshots; it does not expose or own package-management commands.
- Q: Does a Workshop Package attach concrete sessions or define only required-session rules? → A: It defines only the required-session rule. Customers select concrete future sessions during booking; 003.2 authoritatively validates their sale eligibility and reserves capacity. Managers create and manage sessions independently, without editing a package whenever the schedule changes.
- Q: Must the two required Silverclay sessions use the same Workshop Location? → A: Yes. V1 requires both selected Silverclay sessions to use the same canonical `locationId`, although they may use different future dates or slots at that location.
- Q: What lifecycle does a V1 Workshop Package use? → A: `DRAFT → PUBLISHED → RETIRED`. A retired package cannot accept a new booking and cannot be deleted when it has ever been published or frozen into a booking snapshot. Historical bookings retain their frozen snapshot.
- Q: Which module owns the customer-facing Package and session-discovery REST routes? → A: 003.2 owns them because it owns the Package master and authoritative session sale eligibility. The public URLs remain stable for the frontend. 003.1 owns only Booking commands and reads, and consumes the required data through 003.2's internal facade rather than exposing a competing discovery route.
- Q: Should Package publication and retirement be ordinary updates or explicit lifecycle commands? → A: They are explicit Manager-only lifecycle commands below the Package resource. Create/read/update use normal resource routes; publication and retirement use separate commands so authorization and immutable audit clearly record the business transition.
- Q: May a Manager change a published Package's commercial or session-selection rules? → A: Yes. A Manager may change its description, public price, participant constraints or required-session rules. The change applies only to bookings created after the update; each existing booking retains its already frozen Package and financial snapshot.
- Q: Which actor manages Workshop Packages shared across locations? → A: Every active canonical Manager may manage the shared Package catalogue globally. There is no Central/Local Manager tier and `activeLocationIds` does not filter Manager authority.
- Q: How does V1 identify Manager authority without adding an Account role or capability? → A: Operations checks the active canonical `MANAGER` role at command time. Every active Manager has global authority for Manager-authorized actions throughout Feature 003.

- Q: Do public Package discovery and Manager Package management use one REST resource path? → A: Yes. Both use `/api/v1/workshop-packages`. Public callers may only read `PUBLISHED` Packages; active Manager create, update and lifecycle commands are differentiated by method and server-side authorization rather than a separate management namespace.

- Q: Does a Package use a fixed type enum or generic required-session rules? → A: It uses generic `requiredSessionCount` and `sameLocationRequired` rules. Basic Ring (`1`, `true`) and Silverclay (`2`, `true`) are current Package configurations, not a closed package-type enum; future Packages may use other valid values without a code enum change.

- Q: Does V1 need a separate immutable business package code? → A: No. The system-generated `packageId` is the technical identity and an active Manager supplies an editable public `name`. Every Package also has a required public per-participant price; V1 has no separate `packageCode`.

- Q: What participant constraints does a V1 Workshop Package define? → A: It defines `minParticipants` and `maxParticipants` for one booking. A booking's participant count must be within that inclusive range and must still fit the authoritative available capacity of every selected required session.

- Q: Which REST command performs Package publish or retirement? → A: An active Manager uses `POST /api/v1/workshop-packages/{packageId}/lifecycle-transitions` with `targetState` `PUBLISHED` or `RETIRED`. Package lifecycle state is never changed through a generic `PATCH` field.
- Q: Which public route discovers customer-selectable future sessions for one Package? → A: 003.2 exposes `GET /api/v1/workshop-packages/{packageId}/sessions`. It is public and returns only `PUBLISHED`, customer-selectable future sessions that satisfy the Package's selection rules. The existing `GET /api/v1/workshop-sessions` remains Manager/Staff-scoped and is never opened for public discovery.
- Q: Where is a Package's participant and required-session rule rechecked when a Booking reserves capacity? → A: 003.1 supplies the selected `packageReference` with its reserve command. Operations rechecks that the Package is `PUBLISHED`, participant bounds, required-session count, same-location rule, sale eligibility and capacity as one all-or-nothing reserve decision. Booking, the frontend and a stale discovery snapshot cannot independently authorize a selection.
- Q: What availability does public Package session discovery disclose? → A: It returns the exact current `remainingCapacity`, together with session identity, canonical location, scheduled start/end, sale state and `customerSelectable`. This is a customer-selection snapshot only; the atomic reserve command remains the authoritative final capacity decision.
- Q: What is the canonical Workshop Session identity shared with Booking? → A: Operations keeps its UUID session identity. 003.1 consumes it only as an opaque UUID/string `sessionReference`; it must retire its legacy `int64` session identifier contract. V1 does not accept two session identity formats.
- Q: Which safe reasons distinguish Package reserve refusal? → A: Operations returns `PACKAGE_NOT_PUBLISHED` when the Package is no longer bookable and `PACKAGE_SELECTION_INVALID` when participant bounds, required-session count or same-location rules fail. Capacity exhaustion remains `INSUFFICIENT_CAPACITY`. These reasons are safe semantic outcomes, not internal rule or persistence detail.
- Q: Which filters are required for public Package session discovery? → A: `locationId` is required. `from` and `to` are optional customer date filters; clients may apply a short default window when absent. The result remains limited to future, `PUBLISHED`, customer-selectable sessions for that Package at the one requested location.
- Q: How is Silverclay's same-day non-adjacent-session rule retained without making Booking own a Silverclay special case? → A: Package configuration adds generic non-negative `minimumSameDaySlotGap`. It is `1` for Silverclay, meaning two selected sessions on the same local day must have at least one configured slot between them; it is `0` for Packages with no same-day separation requirement, including Basic Ring. Operations validates it atomically with reserve.

## Business model

**Terminology rule:** Except for the Git feature-branch label and the ordinary
word “branch” in a lifecycle decision tree, every remaining legacy prose use of
“branch” in this document means the canonical Workshop Location. It never means
an independent resource, identifier, table, route, DTO field, or authorization
scope. The target implementation and all public contracts use `location` and
`locationId` only.

- A **Workshop Location** is the canonical physical Mland workshop site, identified by `workshop_location.id`. Account location scope and session/slot location scope compare that one identity directly. “Workshop Branch” is a legacy synonym in older prose only; it is not an API, DTO, schema or independent resource term.
- A **Workshop Package** is the Operations-owned master offer that a customer may
  discover before booking. It has a system-generated `packageId`, editable
  public `name`, description, required public per-participant price,
  inclusive `minParticipants`/`maxParticipants` constraints and complete
  required-session rules, but never
  attaches concrete dated sessions. A customer selects eligible future sessions
  during booking; Operations validates and reserves their capacity
  authoritatively. Its generic required-session rule contains
  `requiredSessionCount`, `sameLocationRequired` and non-negative
  `minimumSameDaySlotGap`. Basic Ring (`1`, `true`, `0`) and Silverclay (`2`,
  `true`, `1`) are current configurations, not a closed
  package-type enum. A package may be published only when this feature can
  evaluate its required sessions through the authoritative location/session
  sale-eligibility boundary. Booking reads this master data but stores its own
  immutable selected-package and price snapshot when it creates a booking. Its
  V1 lifecycle is `DRAFT → PUBLISHED → RETIRED`; retirement ends new booking
  without deleting historical evidence.
- A booking is the commercial order. A workshop item is the product being made;
  an attendance ticket is one person's persistent QR right to join every
  required session of its package. Attendance remains per ticket/session pair.
- Capacity is counted from active attendance tickets in each session.
- Mland schedules sessions in fixed, non-overlapping time slots. A session is the
  location's shared slot for booking and capacity, not one customer group. Shop
  may divide attendees into several independent operating groups after booking,
  but those groups do not create separate bookable sessions or capacity pools.
  V1 does not store groups or their attendee/Staff assignments; shop handles
  this division onsite. One Staff member may operate more than one group in the
  same slot. Booking remains available until the slot's shared capacity is full,
  including close to its start time, subject to the normal booking/payment
  rules of feature 003.1.
- A Manager creates a session by selecting one configured slot for its location.
  The session inherits that slot's start and end times; V1 does not allow free
  entry or per-session editing of those times. There is exactly one session for
  each location, date and configured slot; onsite operating groups never create
  another session.
- Manager may create a session individually or bulk-create sessions by location,
  selected slots, date range and applicable weekdays. Bulk creation produces
  independent session records once; V1 has no indefinitely running recurrence,
  and a later change to slots or generation settings never alters an existing
  session.
- When creating an individual session or a bulk set, Manager may choose its
  initial state as `DRAFT` or `PUBLISHED`; `DRAFT` is the default.
- Manager may create a session only when its configured slot start is no more
  than 90 days in the future.
- All slot and session scheduling rules use `Asia/Ho_Chi_Minh`, the common
  timezone of Mland's current Vietnam branches.
- A workshop slot must start and end on the same `Asia/Ho_Chi_Minh` calendar
  date. The initial daily slot configuration is `09:00–12:00`, `13:00–16:00`
  and `16:00–19:00`. Workshop activity normally takes about 2 hours 30 minutes;
  each slot retains 30 minutes of contingency time. Slots for the same branch
  must not overlap.
- A session may be created only when its configured slot start is in the future.
  This does not set a booking cutoff; feature 003.1 controls whether booking is
  still available close to the start time.
- Manager manages the reusable configured slots for each branch. Changing a
  slot's hours affects only sessions created afterwards; an existing session
  retains the start and end times it inherited when it was created.
  Stopping a slot prevents only future session creation; it never changes or
  cancels an existing session.
  V1 never permanently deletes a configured slot; Manager only changes whether
  it is active or inactive.
- Each configured branch slot has a default capacity. A new session copies that
  value as its own capacity; changing the session capacity does not change its
  slot or any other session. Slot and session capacity must be at least one;
  `SALES_PAUSED`, not zero capacity, stops new booking.
- A Staff member normally checks in one ticket by QR. Individual `ticket_code`
  is a manual fallback; `booking_code` only finds a booking and never checks in
  an unspecified ticket. In a Staff check-in flow, a booking-code lookup returns
  only tickets assigned to that Staff member's current session and branch. A
  Manager management lookup may return the full ticket set across sessions and
  branches, and is audited. Entering the exact `ticket_code` for its assigned
  session and branch is a normal fallback, not a check-in override.
- Active Staff whose location scope contains the session location or any active
  Manager may request that a booking's full set of active, unused tickets be resent only to the booking
  contact's existing delivery channel. This may include tickets for other
  sessions or branches, but excludes tickets that are cancelled, revoked,
  refunded, `NO_SHOW` or already `CHECKED_IN`. Feature 003.1 owns ticket
  delivery; this feature permits at most three resend requests per booking in
  15 minutes, records each request for audit, and never permits the onsite actor
  to replace the recipient. Resend does not broaden that actor's check-in
  authority. It is allowed only while the session is `PUBLISHED`, `SALES_PAUSED`
  or `IN_PROGRESS`, regardless of `check_in_opens_at`, and is rejected after
  cancellation is requested, cancellation completes or attendance is finalized.
- V1 check-in requires an online connection to Mland. It does not persist
  offline scans for later synchronization.
- Ring workshops have no `check_in_closes_at`. A valid ticket may be checked in
  only at or after the Manager-configured `check_in_opens_at`, which defaults
  to 60 minutes before session start. Guests may still be checked in when late,
  until attendance is finalized. Check-in records arrival only; Staff controls
  waiting seats and workshop start onsite. Manager may configure this value only
  while the session is `DRAFT`, `PUBLISHED` or `SALES_PAUSED`; it is locked at
  `IN_PROGRESS`. The system records whether its value is the default or a
  Manager override. If it is default, changing the session's configured slot
  recomputes the value to 60 minutes before the new slot start time. If it is an override, the
  value remains unchanged; before saving the schedule change, Manager sees the
  old check-in time and a warning to keep it or adjust it, and the acknowledgement
  is audited.
- A session uses `DRAFT`, `PUBLISHED`, `SALES_PAUSED`, `IN_PROGRESS`,
  `ATTENDANCE_FINALIZED`, `CANCELLATION_REQUESTED`, and `CANCELLED`. Only `PUBLISHED`
  accepts new bookings. `SALES_PAUSED` stops new bookings and capacity holds but
  preserves existing ticket commitments and unexpired holds until their existing
  deadlines; it may return to `PUBLISHED`. A session becomes
  `IN_PROGRESS` when onsite operation starts and may not return to a sales state.
  From `IN_PROGRESS`, it may proceed only to `ATTENDANCE_FINALIZED` through a
  manual whole-session action by an authorized Staff or Manager after they confirm
  that the session has actually ended, or to `CANCELLATION_REQUESTED` for an
  interruption before finalization. This action is available only while the
  session is `IN_PROGRESS`, may occur earlier or later than scheduled, and
  records audit evidence. Scheduled end time does not finalize attendance
  automatically. When a session has a confirmed ticket or unexpired payment
  hold, `CANCELLATION_REQUESTED` is available before finalization and follows
  the feature-003.3 handoff rule; after that handling is complete, it becomes
  terminal `CANCELLED`. Without either commitment, Manager may cancel directly
  to terminal `CANCELLED` with audit.
- A session may be `PUBLISHED` without any Staff roster or lead record. At
  `checkInOpensAt`, check-in opens while it remains `PUBLISHED`; at scheduled
  start, the scheduler moves it to `IN_PROGRESS`. Active Staff in the session
  location or any active Manager may then operate it.
 - An active Manager has global Workshop Operations authority across all
   canonical locations and sessions. An active Staff member may operate only
   sessions whose canonical location is in that actor's `activeLocationIds`.
   Session assignment and operational-lead status are not authorization
   prerequisites.
 - Account Management owns the canonical role, active status and
   `activeLocationIds` facts consumed through its public facade. Workshop
   Operations does not create or mutate those account facts.
- An inactive Staff member or Manager immediately loses all operational
  authority. Staff authorization remains location-scoped; Manager authorization
  remains global. Optional roster and audit history may be retained.
- V1 uses a system-controlled, fixed catalogue of audit reason codes. A code
  may require a supplementary short note or evidence reference; Managers do
  not create or alter the catalogue per branch. It includes
  `OTHER_REQUIRES_NOTE` for an unlisted exception, which always requires a
  clear note or evidence reference.

## User scenarios

### Prioritized user-story map

| Story | Priority | Scenario group | Independent test |
| --- | --- | --- | --- |
| US1 | P1 | Manager configures branches, fixed slots and future sessions. | A scoped Manager creates non-overlapping slots, creates a future session and re-runs bulk generation without duplicate sessions. |
| US2 | P1 | Scheduled start and role/location-scoped operation. | Check-in opens at `checkInOpensAt`; a published session auto-starts at its scheduled start time; Staff is limited by location and active Manager is global. |
| US3 | P1 | Staff verifies and checks in one individual ticket. | Two concurrent scans of one valid QR create one attendance action; the later scan returns `ALREADY_CHECKED_IN` with the original `checkedInAt`. |
| US4 | P2 | Staff recovers ticket access and handles authorized exceptions. | A resend returns only active unused tickets to the existing delivery channel and an override cannot cross session/branch. |
| US5 | P2 | Authorized actor finalizes attendance and Manager handles cancellation/correction. | Finalization marks only unchecked active tickets `NO_SHOW`; cancellation with commitments creates a 003.3 handoff without settlement side effects. |

### Manager configures an operable session

Manager creates or updates a session's location, configured slot, capacity and
sale state. Capacity cannot be reduced below confirmed attendance tickets
plus unexpired payment capacity holds. An expired hold no longer constrains
capacity. Every operational configuration change is audited. The Manager may
publish a draft, pause/resume sales and request cancellation before attendance
finalization. Publication does not require a Staff assignment. At
`checkInOpensAt`, check-in becomes available while the session remains
`PUBLISHED`. At the session's scheduled start time, a recoverable scheduler
auto-starts the eligible published session and records a system audit event. An active Staff in the session
location or any active Manager may issue an explicit early-start/recovery
command. The Manager may directly change a session's slot or location only
when it has neither confirmed tickets nor unexpired payment capacity holds;
otherwise the Manager must use the `CANCELLATION_REQUESTED` handoff. During
`IN_PROGRESS`, optional Staff roster changes are informational/audited only and
do not grant or revoke authority.

### Manager works across canonical locations

A Manager may create/configure slots and sessions and view management lookup
data across every canonical location. Staff authority is derived from active
role/status and direct location scope, not a session assignment.

### Account becomes inactive during operations

When an actor becomes inactive, their authority is removed immediately and
history remains auditable. Staff operations are denied outside active location
scope; an active Manager remains globally authorized within Workshop Operations.

### Authorized actor records an operational exception

For an override, attendance correction or cancellation request, the actor
selects a reason from the system-controlled catalogue. The selected code may
require a short note or evidence reference; Manager cannot create ad-hoc codes
for a branch. `OTHER_REQUIRES_NOTE` is available for an unlisted exception and
always requires a clear note or evidence reference.

### Manager creates future sessions

Manager may create one session for a chosen location, date and configured slot,
or bulk-create sessions for selected slots across a date range and chosen
weekdays. The bulk action reports each created or skipped duplicate combination.
It is a one-time generator: every resulting session has its own lifecycle and
is not retroactively changed by a later slot or generation-setting change. A
session can be created only before its slot begins; this does not itself close
booking before the session start. Manager may choose `DRAFT` or `PUBLISHED` as
the initial state for the creation; `DRAFT` is the default. A created session
must start no more than 90 days in the future.

### Manager manages location slots

Manager creates, changes or stops offering reusable fixed slots for a location.
Slot configuration changes are audited and apply only when creating future
sessions; they never alter or cancel an existing session. If a future session
must be cancelled, Manager uses the audited session-cancellation flow.
V1 retains each configured slot and uses active/inactive status rather than
permanent deletion.
Each slot has a default capacity, which a new session copies as its own capacity.
Slots begin and end on the same local calendar date; the initial daily slot
configuration is `09:00–12:00`, `13:00–16:00` and `16:00–19:00`. The expected
workshop activity duration is 2 hours 30 minutes, with 30 minutes contingency
time in each slot. Slots for the same location do not overlap. Capacity must be
at least one; Manager pauses sales rather than setting capacity to zero.

### Shop organizes groups within a session

A session represents one location's fixed time slot and has one shared booking
capacity. After customers book, shop may divide its attendees into independent
operating groups. Customers do not select a group when booking, and a group
does not create a separate session or capacity pool. V1 does not store groups
or their attendee/Staff assignments; a Staff member may operate multiple groups
within the same session.

### Optional Staff roster metadata

Managers may record an optional Staff roster for operational visibility and
audit. The roster is not an authorization boundary: every active Staff member
in the session location may operate the session, and every active Manager has
global Workshop Operations authority. No operational-lead handover exists in
the target V1 design.

### Manager cancels an operable session with customer commitments

Manager may move a session with valid tickets to `CANCELLATION_REQUESTED`. This
immediately stops normal operation and check-in, but does not revoke tickets,
move customers, release capacity or refund money in this feature. The system
creates an audited handoff case for feature 003.3 to resolve those consequences.
After feature 003.3 reports that every affected customer case is resolved or has
an audited Manager decision, the session becomes terminal `CANCELLED`.

### Manager cancels a session without customer commitments

If a session has neither confirmed ticket nor unexpired payment capacity hold,
Manager may transition it directly to terminal `CANCELLED`. The system records
the actor, time, prior state and cancellation reason; it does not create a
feature-003.3 handoff.

### Staff checks in one attendee

An active Staff member whose location scope contains the session location, or
any active Manager, scans a valid QR for the correct session/location at or
after `check_in_opens_at`. The scheduler has already moved the session to
`IN_PROGRESS`. The system records the ticket,
Staff and time and prevents reuse. A manual ticket-code lookup produces the
same ticket-specific outcome. The result shows only ticket code,
session/branch, ticket status and check-in outcome. A first successful scan
returns `CHECKED_IN`; a repeated scan returns `ALREADY_CHECKED_IN` with the
original `checkedInAt`, both as `200 OK`, and never creates a second attendance
action.

### Staff looks up a booking with tickets in multiple sessions

When Staff enters a `booking_code` while operating a session, the system shows
only tickets for that session and branch. It does not reveal or allow selection
of tickets for another session or branch. A Manager may use the separate
management lookup for cross-session customer support; that lookup is audited.
If no eligible ticket exists for the current session and branch, Staff sees only
a generic no-eligible-ticket result.

### Staff requests ticket resend for a customer

If a customer cannot present QR or `ticket_code`, an active Staff member whose
location scope contains the session location or any active Manager may request resend to the booking contact's existing
delivery channel. The system applies the feature-003.1 delivery throttle and
resends the booking's full set of active, unused tickets, including any ticket
for another session or branch. It excludes tickets that are cancelled, revoked,
refunded, `NO_SHOW` or already `CHECKED_IN`. It records the actor,
ticket/booking correlation, recipient channel and outcome. Staff cannot replace
the recipient or use the resend to check in a ticket outside the current
session/branch. Resend is unavailable when delivery reports an error, or when
the customer confirms they did not receive or cannot access the ticket after
resend; it has no fixed waiting period. At most three resend requests per
booking are allowed in 15 minutes; afterwards Staff uses the authorized override
path when applicable. This action is available only when the session is
`PUBLISHED`, `SALES_PAUSED` or `IN_PROGRESS`; it is not gated by
`check_in_opens_at`.

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

### Authorized actor handles an exception

An active Staff member whose location scope contains the session location, or
any active Manager, selects a particular unused ticket for an exceptional case, selects a
standardized reason code and leaves the required audit evidence. The reason
code may require a short note or an evidence reference. The ticket must still
be valid for that session and branch; override cannot be used as a session or
branch change. A booking code alone is never enough to check in every ticket.
Booking lookup may show its ticket set and each ticket's used/unused status, but
not the booking contact's full email or phone number. After a ticket resend
fails, the same authorized actor may override each particular unused ticket
with `TICKET_REDELIVERY_UNAVAILABLE`, a short note and audit linked to that
resend outcome.

### Authorized actor finalizes attendance

After the session actually ends, an active Staff member whose location scope
contains the session location or any active Manager reviews attendance and finalizes the whole session in
one action. Checked-in tickets remain
`CHECKED_IN`; unchecked tickets become `NO_SHOW`. Only Manager may make a later
correction, with before/after audit evidence.

## Functional requirements

- **FR-000**: The system MUST let an authorized Manager configure and publish
  Workshop Packages through Manager-only REST APIs, including public `name`,
  description, required public per-participant price, participant constraints
  and complete required-session rules. The system-generated `packageId` is the
  technical identity; V1 MUST NOT require a separate business `packageCode`. It
  MUST restrict those shared Package commands to an active canonical Manager.
  Every active Manager has global authority throughout Feature 003, independent
  of `activeLocationIds`; this is not a new Account role/capability.
  It MUST own public customer discovery of published packages and
  customer-selectable future sessions, and provide 003.1 the corresponding
  semantic internal read boundary. A package MUST define session-count and
  selection rules only; it MUST NOT attach concrete dated sessions.
  Public Package discovery and Manager Package management use the same
  `/api/v1/workshop-packages` resource path. Public callers may read only
  `PUBLISHED` Packages; active Manager methods and lifecycle commands require
  server-side authorization.
  Customer session discovery uses
  `GET /api/v1/workshop-packages/{packageId}/sessions`; it returns only
  `PUBLISHED`, customer-selectable future sessions satisfying that Package's
  rules. Each result returns exact current `remainingCapacity`, session
  identity (the Operations UUID, consumed by Booking as an opaque
  `sessionReference`), canonical location, scheduled start/end, sale state and
  `customerSelectable`. It is a customer-selection snapshot, never a capacity
  guarantee. The Manager/Staff-scoped `GET /api/v1/workshop-sessions` remains
  a separate non-public operational search route.
  Public session discovery requires canonical `locationId`; optional `from` and
  `to` date filters narrow the future result window without changing Package
  selection rules.
  Every reserve request from 003.1 MUST carry the selected `packageReference`.
  Operations MUST atomically recheck that the Package is `PUBLISHED`, the
  requested participant count is within its bounds, the complete selected
  session set satisfies its required-session count, location rule and any
  same-day slot-gap rule, and every session remains sale-eligible with capacity.
  It reserves every requested
  session or none. A frontend, Booking snapshot or prior discovery result never
  independently authorizes that selection.
  The one canonical session identity is Operations' UUID. 003.1 may carry it
  only as an opaque UUID/string `sessionReference`; V1 MUST NOT accept a legacy
  numeric session identity or support a dual-format lookup.
  A reserve refusal caused by a non-published Package MUST return the safe
  semantic reason `PACKAGE_NOT_PUBLISHED`; participant-bound, required-session
  count or same-location failure MUST return `PACKAGE_SELECTION_INVALID`.
  Capacity exhaustion remains `INSUFFICIENT_CAPACITY`; no response exposes
  internal Package/persistence detail.
  Package create/read/update use resource operations; `PUBLISHED` and
  `RETIRED` transitions use explicit Manager-only lifecycle commands with
  immutable audit evidence. The command is
  `POST /api/v1/workshop-packages/{packageId}/lifecycle-transitions` with
  `targetState` `PUBLISHED` or `RETIRED`; a generic `PATCH` MUST NOT alter the
  lifecycle state. A change to a published Package's description,
  public price, participant constraints or required-session rules applies only
  prospectively; every existing Booking retains its frozen Package and
  financial snapshot.
  A Package MUST define inclusive `minParticipants` and `maxParticipants` for
  one booking, with a valid minimum no greater than its maximum. Booking's
  participant count MUST be inside that range and still fit every selected
  required session's authoritative available capacity.
  A Package MUST expose generic `requiredSessionCount`,
  `sameLocationRequired` and non-negative `minimumSameDaySlotGap` rules.
  Basic Ring (`1`, `true`, `0`) and Silverclay (`2`, `true`, `1`) are current
  configurations, not a closed type enum. A value of `1` requires at least one
  configured slot between two selected sessions on the same local date. 003.1 MUST NOT
  access Operations entities or repositories, mutate package
  master data, or derive the required-session set independently; it freezes the
  selected package and applicable price only in its own booking snapshot. A
  Package MUST transition only `DRAFT → PUBLISHED → RETIRED`; `RETIRED` MUST
  reject new booking and MUST NOT delete a package that has ever been published
  or frozen into a booking snapshot.
- **FR-001**: The system MUST keep capacity, Staff assignment and attendance
  isolated by session and location.
- **FR-001b**: The system MUST use one shared capacity for each location's fixed
  session slot. V1 MUST NOT store operational groups or ticket-to-group or
  Staff-to-group assignments. Shop may divide attendees onsite, but those
  groups MUST NOT create customer-bookable sessions or separate capacity pools;
  a Staff member MAY operate more than one group in the same session.
- **FR-001c**: The system MUST require each session to select a configured fixed
  slot for its location and derive its start and end time from that slot. V1 MUST
  NOT permit free time entry or per-session time editing.
- **FR-001d**: The system MUST enforce uniqueness of `location`, session date and
  configured slot. It MUST reject creation of a second session for the same
  combination; onsite operating groups MUST NOT create additional sessions.
- **FR-001e**: The system MUST allow only Manager to create, change or stop
  offering configured fixed slots for a location, with audit. A slot-time change
  MUST affect only sessions created after that change; an existing session MUST
  retain the start and end times inherited when it was created. Stopping a slot
  MUST prevent only new-session creation and MUST NOT alter or cancel existing
  sessions; session cancellation MUST use the audited session lifecycle. Each
  slot MUST have a default capacity, which a new session copies as its own
  capacity; a later session-capacity change MUST NOT alter the slot or another
  session. Slot and session capacity MUST be at least one; Manager MUST use
  `SALES_PAUSED`, not zero capacity, to stop accepting new bookings. V1 MUST
  retain each configured slot and allow only active/inactive state changes, not
  permanent deletion.
- **FR-001f**: The system MUST allow only Manager to create a session either
  individually or by a one-time bulk generation for a location, selected slots,
  date range and applicable weekdays. Bulk generation MUST create independent
  session records, create every missing combination, skip every existing
  `location`/date/slot combination without overwriting it, and report the created
  and skipped results. It MUST NOT create an indefinitely running recurrence or
  alter an existing session when settings change later.
- **FR-001g**: The system MUST reject individual or bulk creation of a session
  whose configured slot start is not in the future. This constraint MUST NOT
  itself impose a booking cutoff before a future session starts. It MUST also
  reject creation when the configured slot start is more than 90 days ahead.
- **FR-001h**: The system MUST evaluate slot/session scheduling, future-start
  validation and session-date uniqueness in `Asia/Ho_Chi_Minh` for all current
  Vietnam locations.
- **FR-001j**: The system MUST reject a configured slot whose end is not on the
  same `Asia/Ho_Chi_Minh` calendar date as its start. The initial daily slot
  configuration MUST provide `09:00–12:00`, `13:00–16:00` and `16:00–19:00`.
  The system MUST reject overlapping configured slots for the same location.
- **FR-001i**: The system MUST allow Manager to choose `DRAFT` or `PUBLISHED`
  as the initial state for an individual or bulk session creation, and MUST
  default the chosen state to `DRAFT` when none is supplied.
- **FR-001a**: The system MUST reject a capacity reduction below confirmed
  attendance tickets plus unexpired payment capacity holds for that session.
- **FR-002**: The system MUST create exactly one check-in record per ticket per
  session and MUST NOT create a second check-in record/action for a repeated
  normal scan.
- **FR-002a**: The system MUST require an online Mland connection for V1
  check-in and MUST NOT create an offline scan queue.
- **FR-002b**: The system MUST allow only Manager to reverse a recorded
  check-in, before or after attendance finalization, with a standardized reason
  code and immutable before/after audit evidence; it MUST NOT delete the
  original scan. Before finalization it MUST return the ticket to not checked-in;
  after finalization it MUST require Manager to select `CHECKED_IN` or `NO_SHOW`.
- **FR-002c**: When a ticket is scanned again at the same session after it has
  been checked in there, the system MUST return `200 OK` with `checkInResult`
  `ALREADY_CHECKED_IN` and the most recent `checkedInAt`; it MUST show only its
  already-checked-in status and most recent check-in time;
  it MUST NOT expose booking-contact data or the prior Staff actor.
- **FR-002d**: The system MUST make check-in atomic per ticket/session pair. If
  concurrent valid check-in requests target the same ticket at the same
  session, exactly one request MUST create the check-in record; every other
  request MUST return the already-checked-in result with its recorded check-in
  time.
- **FR-003**: The system MUST validate ticket state, entitlement to the current
  session, and location before normal check-in and MUST permit it to an active
  Staff member whose `activeLocationIds` contains the session location or any
  active Manager. A session assignment and operational-lead record are not
  authorization prerequisites.
- **FR-003a**: The system MUST limit ticket lookup/check-in results to ticket
  code, session/location, ticket status and check-in outcome; booking lookup may
  show ticket used/unused status but MUST NOT expose the booking contact's full
  email or phone number.
- **FR-003b**: In a Staff check-in flow, the system MUST return from a
  `booking_code` lookup only tickets assigned to the current session and location,
  and MUST NOT allow another ticket to be selected or checked in. It MUST allow
  Manager-only, audited management lookup of the full ticket set across sessions
  and locations, without exposing the booking contact's full email or phone
  number.
- **FR-003d**: If a `booking_code` lookup has no eligible ticket for the
  current session and location, the Staff check-in flow MUST return only a generic
  no-eligible-ticket result and MUST NOT reveal another ticket, session or
  location.
- **FR-003e**: The feature MUST provide 003.1 only a delivery-safe ticket
  projection containing ticket identity or QR reference, ticket code, and the
  entitled sessions' location and scheduled start/end. It MUST NOT expose
  attendance, Staff/audit data, or personally identifiable information in that
  projection.
- **FR-003c**: The system MUST allow only an active Staff member whose
  `activeLocationIds` contains the session location or any active Manager to
  request resend of a booking's full
  set of active, unused tickets to the booking contact's existing delivery
  channel, including tickets for other sessions or locations. It MUST exclude
  tickets that are cancelled, revoked, refunded, `NO_SHOW` or already
  `CHECKED_IN`; apply the feature-003.1 delivery throttle of at most three
  requests per booking in 15 minutes; prohibit recipient replacement or expanded
  check-in authority; and audit actor, ticket/booking correlation, recipient
  channel and outcome. It MUST permit the request only while the session is
  `PUBLISHED`, `SALES_PAUSED` or `IN_PROGRESS`, regardless of
  `check_in_opens_at`, and reject it in `CANCELLATION_REQUESTED`, `CANCELLED`
  or `ATTENDANCE_FINALIZED`.
- **FR-004**: The system MUST require a reason and audit record for exceptional
  check-in. An overriding Staff member MUST be active and have the session
  location in `activeLocationIds`; an overriding Manager MUST be active and has
  global Workshop Operations authority. Entering an exact
  `ticket_code` is a normal fallback and MUST NOT require this permission when
  ticket, session and location validation succeeds. After ticket resend fails, it
  MUST permit only those actors to override each particular unused ticket using
  `TICKET_REDELIVERY_UNAVAILABLE`, a short note and audit linked to the resend
  outcome. Resend failure means either a delivery error or customer confirmation
  of non-receipt/inability to access after resend; it MUST NOT require a fixed
  wait. Override MUST not bypass the ticket's assigned session or location.
- **FR-005**: The system MUST not block check-in merely because a workshop has
  started; it MUST reject normal check-in before `check_in_opens_at` and after
  attendance is finalized.
- **FR-005a**: The system MUST default `check_in_opens_at` to 60 minutes before
  session start and allow Manager to configure it per session only while the
  session is `DRAFT`, `PUBLISHED` or `SALES_PAUSED`; it MUST lock the value when
  the session enters `IN_PROGRESS`.
- **FR-005b**: The system MUST record whether `check_in_opens_at` is default or
  Manager-overridden. When Manager changes a session's configured slot, it MUST
  recompute a default value to 60 minutes before the new slot start time. For an
  override, it MUST preserve the value and display the old check-in time plus a
  warning before saving, allowing Manager to keep or adjust it; it MUST audit
  the Manager acknowledgement and resulting values.
- **FR-006**: The system MUST allow authorized finalization to assign `NO_SHOW`
  only to unchecked tickets of that session; finalization is allowed to an
  active Staff member whose `activeLocationIds` contains the session location
  or any active Manager, applies
  to the whole session in one action, and MUST NOT occur automatically at the
  scheduled end time. It MUST be allowed only while the session is
  `IN_PROGRESS` after the actor confirms that the session has actually ended;
  it may occur before or after `scheduled_end_at` and MUST record that
  confirmation in the audit trail.
- **FR-007**: The system MUST record actor, time, source and correlation data
  for check-ins, resend requests, overrides, finalization and corrections.
- **FR-007a**: The system MUST require a standardized reason code for a
  check-in override, finalized-attendance correction or session cancellation
  request, and MUST require a short note or evidence reference whenever that
  reason code defines it. V1 MUST validate the code against a fixed,
  system-controlled catalogue; Manager MUST NOT create or alter reason codes
  per location. The catalogue MUST include `OTHER_REQUIRES_NOTE`, which MUST
  require a clear note or evidence reference.
- **FR-008**: The system MUST allow only Manager to correct finalized attendance
  and MUST preserve the before/after state and correction reason in the audit
  record.
- **FR-009**: The system MUST allow only Manager to request cancellation of an
  operable session with a confirmed ticket or unexpired payment capacity hold;
  it MUST stop normal operation/check-in and create an audited handoff without
  performing reschedule, refund, ticket revocation or capacity release in this
  feature.
- **FR-009a**: The system MUST allow only Manager to transition a session with
  neither confirmed ticket nor unexpired payment capacity hold directly to
  terminal `CANCELLED`, recording actor, timestamp, prior state and cancellation
  reason without creating a feature-003.3 handoff.
- **FR-010**: The system MUST enforce the session lifecycle `DRAFT`,
  `PUBLISHED`, `SALES_PAUSED`, `IN_PROGRESS`, `ATTENDANCE_FINALIZED` and
  `CANCELLATION_REQUESTED`, and `CANCELLED`; only `PUBLISHED` may accept new
  bookings, and only Manager may publish, pause/resume sales or request
  cancellation. Once a session enters `IN_PROGRESS`, it MUST NOT return to
  `PUBLISHED` or `SALES_PAUSED`; before finalization it may move only to
  `ATTENDANCE_FINALIZED` or `CANCELLATION_REQUESTED`.
 - **FR-010b**: The system MAY publish a session without Staff assignment.
   At `checkInOpensAt`, the system MUST make check-in available while the
   session remains `PUBLISHED`. At the session's scheduled start time, a
   recoverable scheduler MUST transition an eligible `PUBLISHED` session to
   `IN_PROGRESS` and record a system audit event. A manual transition remains
   available only for explicit early start or recovery.
- **FR-010d**: The scheduled transition MUST be idempotent and safe across
  restart/retry. An explicit early-start/recovery command MUST be allowed to an
  active Staff in the session location or any active Manager and MUST audit the
  actor, timestamp and previous state.
- **FR-010e**: Staff roster changes are optional operational metadata and MUST
  not change authorization. Active Staff authority is determined by role,
  status and location scope; active Manager authority is global.
- **FR-010f**: The target V1 design MUST NOT create, require or expose a
  session operational-lead role or handover operation.
- **FR-010c**: The system MUST reject new bookings and capacity holds while a
  session is `SALES_PAUSED`, but MUST allow an unexpired hold created before
  the pause to complete payment until its existing deadline.
- **FR-010h**: For a requested booking session, the feature MUST provide 003.1
  only a public sale-eligibility snapshot containing existence/customer-
  selectability, sale state, location, scheduled start/end, and remaining
  capacity. The snapshot MUST NOT expose this feature's entities or repositories.
- **FR-010i**: When 003.1 requests participant capacity for more than one
  session, the feature MUST decide the complete requested set atomically. It
  MUST hold the requested number of seats in every requested session or create
  no hold in any requested session; it MUST NOT return a partial result.
- **FR-010j**: On valid payment confirmation, the feature MUST commit all and
  only the active holds belonging to that booking as one result. If any expected
  hold is missing or expired, it MUST commit none; 003.1 then MUST NOT confirm
  the booking.
- **FR-010k**: A request to release booking capacity holds for expiry or pending
  cancellation MUST release only active holds exactly once. A repeated or late
  request MUST be an idempotent no-op and MUST NOT change capacity again.
- **FR-010ka**: Every state-changing `WorkshopOperationsFacade` command MUST
  use a dedicated idempotency ledger owned by this module. The ledger MUST be
  separate from operational audit records and from business aggregates such as
  capacity holds and attendance tickets.
- **FR-010kb**: The idempotency ledger MUST enforce uniqueness for
  `facadeAction` plus `operationReference` and retain a semantic request
  fingerprint. It MUST return the original outcome only for an equivalent
  command; reuse of the same pair with different content MUST be rejected
  safely.
- **FR-010kc**: An idempotency record MUST move from `IN_PROGRESS` to
  `COMPLETED`. `COMPLETED` MUST retain the immutable terminal business outcome
  `ACCEPTED` or `REJECTED`; a technical or retryable failure MUST roll back
  rather than leave a completed record, allowing the equivalent command to be
  retried with the same `operationReference`.
- **FR-010kd**: When an equivalent command encounters an existing
  `IN_PROGRESS` idempotency record, the feature MUST return the typed safe
  outcome `IN_PROGRESS` and MUST perform no second business side effect. The
  caller MAY retry with the same `operationReference` to obtain the original
  completed outcome.
- **FR-010ke**: The idempotency ledger MUST retain the original execution's
  `correlationReference` separately from `operationReference`. The business
  audit event for that execution MUST use that correlation reference. An
  equivalent retry MUST NOT create a duplicate business audit event; its
  transport correlation belongs only to technical request logging.
- **FR-010kf**: A `COMPLETED` idempotency record MUST be retained for at least
  24 hours before cleanup. A caller MUST NOT retry an `operationReference`
  after that retention window because duplicate protection is no longer
  guaranteed. An `IN_PROGRESS` record MUST use a configurable lease timeout;
  after its lease expires, an equivalent retry MAY reclaim the record to recover
  from a crashed execution.
- **FR-010kg**: Each claim or reclaim of an `IN_PROGRESS` idempotency record
  MUST have an incrementing claim version. Completing the record and committing
  its business effect MUST require the current claim version; a request that
  has lost its version MUST be rejected or rolled back.
- **FR-010l**: When this feature makes session availability available to 003.4,
  its sellable capacity MUST equal total capacity minus confirmed ticket seats
  and all active, unexpired direct-Mland capacity holds. Feature 003.4 MAY
  consume that availability but MUST NOT calculate or write the capacity itself.
- **FR-010m**: This feature MUST create an availability/sale-state notification
  for 003.4 only after a completed local change alters the sellable-capacity or
  sale-state snapshot. Active-hold creation, release, and expiry notify when
  availability changes; sales pause/resume and session cancellation/closure
  notify when sale state changes. Hold-to-ticket commitment MUST NOT create a
  separate notification when that snapshot is unchanged.
- **FR-010n**: The availability/sale-state snapshot provided to 003.4 MUST
  contain only opaque session identity, location scope, sellable availability,
  sale state, source change identity/version, and effective time. It MUST NOT
  contain PII, Booking/payment data, ticket detail, or package/provider mapping.
- **FR-010o**: Availability/sale-state notification delivery to 003.4 MUST be
  treated as at-least-once. 003.4 MUST apply only a newer source version/change
  identity and ignore duplicate or older snapshots; when it detects a gap or
  cannot establish order, it MUST obtain an authoritative 003.2 snapshot resync.
  This requirement MUST NOT depend on exactly-once delivery.
- **FR-010p**: A failed 003.4 synchronization MUST close or suspend only the
  affected OTA mapping and create its reconciliation work. It MUST NOT change
  this feature's session sale state, capacity, or direct-Mland booking behaviour.
- **FR-010q**: When 003.4 requests authoritative resync for a source-version
  gap or uncertain order, this feature MUST return the current complete
  OTA-observable snapshot of the one affected session at one source version:
  schedule, sellable availability, and sale state. It MUST NOT require 003.4 to
  merge separate schedule/availability reads or consume an entire location.
- **FR-010r**: The per-session OTA source version MUST increase, and an
  OTA-observable snapshot MUST be made available, only when that session's
  schedule, sellable availability, or sale state changes. Internal-only changes,
  including Staff assignment or audit evidence, MUST NOT increase that version
  or produce an OTA snapshot.
- **FR-010s**: The feature MUST NOT make an OTA-observable snapshot available
  for a `DRAFT` session. It MUST make a snapshot available when a mapped Basic
  Ring session is `PUBLISHED`, and publish the complete post-state whenever an
  OTA-observable value changes. `IN_PROGRESS`, `ATTENDANCE_FINALIZED`,
  `CANCELLATION_REQUESTED` and `CANCELLED` have closed OTA sale state. The
  automatic `PUBLISHED` to `IN_PROGRESS` transition at `scheduledStartAt` MUST
  persist the session transition, complete closed snapshot/version and
  persistent Modulith publication in the same Operations transaction. An
  `ATTENDANCE_FINALIZED` transition MUST NOT create another snapshot/version
  when the observable post-state is already closed and otherwise unchanged.
- **FR-010t**: A completed local transaction that changes one or more
  OTA-observable values of one session MUST increase that session's OTA source
  version exactly once. The snapshot at that version MUST represent the complete
  post-transaction schedule, sellable availability, and sale state; it MUST NOT
  expose an intermediate combination of those values.
- **FR-010u**: An authoritative resync that does not change the session's
  OTA-observable post-state MUST return the current complete snapshot at its
  existing source version. It MUST NOT increase that version or create a new
  business change; 003.4 MAY retry or republish the returned snapshot under its
  own mapping/delivery controls.
- **FR-010v**: For V1, every OTA-observable session snapshot from this feature
  to 003.4 MUST be carried by a persistent internal Spring Modulith event
  publication recorded in the same local Operations transaction as its source
  change. 003.4 consumes that publication at-least-once and owns separate
  durable outbound delivery to providers. V1 MUST NOT require a message broker.
- **FR-010w**: For a booking/package context requested by 003.3, this feature
  MUST provide only its authoritative settlement-readiness conclusion. It MUST
  be true only when every required session is `ATTENDANCE_FINALIZED`, and false
  when any required session is not finalized, including cancellation. It MUST
  not expose individual attendance, ticket, Staff, audit or PII detail, and
  003.3 MUST NOT derive readiness from scheduled end time or per-session state.
- **FR-010x**: For a 003.3-requested complete required-session replacement,
  every target session MUST be future, `PUBLISHED`, customer-selectable and
  have capacity for the booking's complete participant count. This feature MUST
  apply every requested capacity and ticket-entitlement change together or none;
  if any source/target eligibility or capacity condition fails, it MUST leave
  every existing required session, ticket entitlement and capacity unchanged.
  `SALES_PAUSED` MUST have no Manager reschedule exception in V1.
- **FR-010y**: An ordinary 003.3-requested customer session change MUST accept
  a source required session only while it is future and not `IN_PROGRESS`,
  `ATTENDANCE_FINALIZED`, `CANCELLATION_REQUESTED` or `CANCELLED`. It MUST NOT
  rewrite check-in or attendance history. A source in
  `CANCELLATION_REQUESTED` MUST use the separate Mland-caused cancellation
  remedy instead.
- **FR-010z**: When a complete required-session replacement succeeds, this
  feature MUST update every affected ticket's entitlement to the resulting
  required-session set and transfer the booking's complete participant capacity
  from replaced sessions to replacement sessions as the same all-or-none result.
  The persistent ticket QR MUST remain unchanged; this feature MUST NOT issue a
  replacement ticket or retain entitlement/capacity in a replaced session.
- **FR-010aa**: This feature MUST provide 003.1 an authorized, read-only,
  current delivery-safe ticket/session-entitlement projection for booking and
  Magic-Link reads. Following a successful required-session replacement or a
  verified-refund consequence, that projection MUST reflect the resulting
  entitlement, location and schedule rather than a superseded session. It MUST
  continue to exclude attendance, Staff, audit and PII detail; it does not make
  003.1 an owner of tickets or schedules.
- **FR-010a**: The system MUST transition `CANCELLATION_REQUESTED` to terminal
  `CANCELLED` only after feature 003.3 reports all affected customer cases
  resolved or an audited Manager decision for each. `CANCELLED` MUST reject
  normal operation and check-in whether it was reached directly or through
  `CANCELLATION_REQUESTED`.
- **FR-011**: The system MUST reject a direct session slot or location change
  whenever confirmed tickets or unexpired payment capacity holds exist; Manager
  MUST use the audited `CANCELLATION_REQUESTED` handoff instead.
- **FR-012**: The system MUST grant an active Manager global Workshop Operations
  authority across all locations and sessions. Staff authority MUST remain
  limited to sessions whose location is in the Staff actor's active location
  scope.
- **FR-012a**: The system MUST revoke workshop operational authority immediately
  when the actor becomes inactive. It MUST reject Staff operations outside the
  actor's location scope and MUST not depend on session assignment or an
  operational-lead record.
- **FR-012b**: The system MUST consume, but MUST NOT create, modify or revoke,
  Manager-to-location assignments. Account/personnel management owns that
  assignment lifecycle.

## Success Criteria

- **SC-001**: A scoped Manager can create an individual future session or run a
  bulk generation within 90 future days; duplicate location/date/slot combinations
  are skipped and reported without overwriting an existing session.
- **SC-002**: Confirmed attendance tickets plus unexpired capacity holds never
  exceed the session capacity, including when concurrent hold requests occur.
- **SC-003**: Concurrent normal scans for one valid ticket produce exactly one
  attendance action; every later scan reports `ALREADY_CHECKED_IN` and the
  original `checkedInAt` without disclosing booking-contact data.
- **SC-004**: An inactive actor or actor outside the required location/session
  scope cannot configure, start, check in, finalize or retrieve protected
  workshop data.

## Out of scope

Workshop checkout/bills, payment collection, refund, customer self-service
changes, OTA callbacks and ticket delivery to individual participants.
