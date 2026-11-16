# Feature Specification: Workshop Operations

**Date created**: 2026-10-24
**Date updated**: 2026-11-17
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature 003 authorization decision (2026-10-29)**: Every active canonical
`MANAGER` has global location authority for Manager-authorized actions across
all 003 subfeatures. `activeLocationIds` does not restrict Manager authority;
Staff actions remain location-scoped, Member ownership remains enforced, and
Admin-only technical actions remain Admin-only. The Central Manager distinction
is removed. *(Staff wording superseded 2026-11-06: Staff actions need the
session location in scope **and** an active assignment to the session; see
"Staff session-assignment access".)* *(Admin wording superseded 2026-11-15,
owner decision Hồ Hoàng Quốc, checklist CHK024: an Admin is the system
administrator only and has no Workshop Operations business authority; 003.2 has
no Admin-only action, and creating or editing a workshop location is a Manager
action (FR-012c).)*

**Feature Branch**: `003.2-workshop-operations`

**Status**: Implemented through tasks Phase 14 T090–T113 (tasks.md evidence);
Phase 15 tasks T114–T125 (Clarifications Session 2026-11-15 and its convergence, Phase 16) open; Phase 17
tasks T126–T142 (Checklist follow-up 2026-11-15, its second pass and the
checklist follow-up analysis) open. Updated
2026-11-15; **Approved 2026-11-15 by Hồ Hoàng Quốc (owner Feature 003), whole
spec content of that date including Clarifications Session 2026-11-15**
(supersedes "approval pending", Q1). Analyze follow-up approved 2026-11-16 by Hồ Hoàng Quốc.
*(Superseded 2026-11-15: "T092 and T096 open; owner re-approval pending", and
2026-11-14: "Draft — not implemented".)*  

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
- An active `STAFF` may operate a session whose canonical `locationId` is in
  the actor's `activeLocationIds`. *(Superseded 2026-11-06: a Staff member must
  also be actively assigned to the session; see "Staff session-assignment
  access" below.)*
- An active `MANAGER` has global Workshop Operations authority across all
  locations and sessions. No location-scope, session-assignment or
  operational-lead check is required for Manager authority.
- `operational lead` is retired from the target V1 design. Assignment/lead
  endpoints and lead-only authority are not part of the target contract.
- *(Extended 2026-11-15, Clarifications Q2: `PUBLISHED` here also means
  `SALES_PAUSED` for check-in and scheduled start.)* At `checkInOpensAt`, check-in becomes available while an eligible `PUBLISHED`
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
  window has closed. *(Superseded in part 2026-11-12/14 by FR-032: the outcome
  is per ticket (window anchored per ticket, decided 2026-11-14, FR-032).)*
  *(Superseded 2026-11-15 by FR-032: the earlier "open owner decision recorded in FR-032" is closed.)*

Any older paragraph, example or scenario that requires a Staff assignment,
Manager operational lead, capability key or manual scheduled start is legacy
wording and must be read through this amendment until the affected section is
rewritten.

## Staff session-assignment access (owner decision, 2026-11-06)

This amendment supersedes the 2026-10-28 rule that a session-level Staff
assignment is not an authorization prerequisite, and every older statement of
the form "an active Staff whose location scope contains the session location
may …" (check-in, booking lookup, ticket resend, check-in override, attendance
finalization, state transition, session read). Read each of them as "an active
Staff whose location scope contains the session location **and who is actively
assigned to that session** may …".

- **Visibility**: the session list returned to a Staff member contains only the
  sessions they are actively assigned to. Opening a session, its attendance
  roster or any session operation for an unassigned session is refused with
  `403` (`Staff member is not assigned to this workshop session`). Managers
  remain global and see and operate every session. An Admin is the system
  administrator only and has no Workshop Operations business authority
  (decided 2026-11-15, owner decision Hồ Hoàng Quốc, CHK024; FR-012c).
  *(Superseded 2026-11-15: "Manager and Admin remain global and see and operate
  every session in scope".)*
- **Both checks are made at command time**: active account status, the session
  location inside the actor's `activeLocationIds`, and an active assignment
  (`session_staff_assignments.active = true`). Revoking an assignment removes
  the Staff member's access immediately; assignment history remains.
- **Only a Manager assigns or revokes Staff** for a session. Several Staff may
  be assigned to one session; the session response carries
  `assignedStaffAccountIds`.
  *(Owner decision 2026-11-16, Hồ Hoàng Quốc: every active Staff member is
  assigned by default to the sessions of the location in their scope. The system
  creates the `session_staff_assignments` rows when a session is created and in a
  periodic sync (a Staff member added to a location or activated later gets the
  sessions that already exist). A row that a Manager revoked is never re-added.
  Only a Manager still revokes or re-assigns; the audit action is
  `SESSION_STAFF_AUTO_ASSIGNED`. Implemented by `StaffAutoAssignmentService`.)*
- **Publication and start do not require an assignment** (FR-010b is
  unchanged). A session without an assigned Staff is visible and operable by
  Managers only until a Manager assigns someone. *(Added 2026-11-15,
  Clarifications Q3: from `checkInOpensAt` such a session is flagged "missing
  Staff", and revoking the last assignment after that instant needs explicit
  Manager confirmation.)*
- **Attendance roster**: `GET /workshop-sessions/{sessionId}/attendance-roster`
  lists the session's valid tickets with their check-in state
  (`NOT_CHECKED_IN`, `CHECKED_IN`, `NO_SHOW`), the booking reference and the
  check-in time, and no customer contact data. Same authority as booking
  lookup.
- **Attendance finalization** is only the dedicated
  `attendance-finalizations` action; a plain state transition to
  `ATTENDANCE_FINALIZED` is refused because it would skip marking unchecked
  tickets `NO_SHOW` and recording the finalization time.
- **Eligibility and session state of an assignment** *(added 2026-11-15, owner
  decision Hồ Hoàng Quốc, checklist CHK029; code task T130)*: the assigned
  account MUST exist, have the canonical role `STAFF`, be `ACTIVE` and have the
  session location in its `activeLocationIds` (Account facade, read at command
  time). Every reason the assignee is not eligible is answered `409
  STAFF_NOT_ELIGIBLE` with the reason (`ACCOUNT_NOT_FOUND`, `NOT_STAFF`,
  `NOT_ACTIVE`, `LOCATION_NOT_IN_SCOPE`); `403` is used only when the caller is
  not an active Manager. Assigning an already actively assigned Staff member is
  a no-op that returns `200` with the unchanged session. A Staff member may be
  assigned only while the session is `DRAFT`, `PUBLISHED`, `SALES_PAUSED` or
  `IN_PROGRESS`; in `CANCELLATION_REQUESTED`, `CANCELLED` or
  `ATTENDANCE_FINALIZED` the assignment is refused with `409 INVALID_STATE`.
  Revocation is allowed in every state, subject only to the last-assignment
  guard of Clarifications Q3 (T115), which applies only while the session is
  `PUBLISHED`, `SALES_PAUSED` or `IN_PROGRESS` (CHK030, owner decision Hồ Hoàng
  Quốc 2026-11-15). *(Supersedes the earlier code behaviour of
  `409 INVALID_STATE` for an ineligible account and `403
  LOCATION_SCOPE_DENIED` for an assignee outside the location.)*
- **Downstream**: Settlement (003.3) and the OTA shop add-on (003.4) apply the
  same rule through the booking: a Staff member may act on a booking only while
  assigned to a session the booking holds a valid ticket for.

## Per-ticket capacity, attendance, QR and changes (decision 2026-11-12)

**Source:** owner decisions recorded 2026-11-11/12 in
`artifacts/workshop-changes-to-implement.md`. **Status (updated 2026-11-15):**
T090–T097 (FR-030–FR-036, including T092 and T096) are implemented with
evidence in tasks.md; approved 2026-11-15 by Hồ Hoàng Quốc (see Status). *(Superseded 2026-11-15: "T092
and T096 open".)* *(Superseded
2026-11-14: "specified, not yet implemented (tasks Phase 14, T090–T096)".)* It serves 003.1 FR-030–FR-043 (booking group, one
ticket booking per person, ticket recipients) and 003.3 FR-030–FR-045
(per-ticket folio, cancellation, Mland remedy and changes).

**Supersedes in part:** booking-wide capacity, readiness and attendance
outcome (FR-010h–FR-010k, FR-010w, FR-014) now apply per ticket booking;
complete-booking replacement with the booking's full participant count
(FR-010x) becomes per-ticket replacement; FR-010y's ban on an `IN_PROGRESS`
source gains the late-arrival exception below. Superseded wording is read
through this section.

- **FR-030**: A ticket booking holds exactly one seat in each of its required
  sessions. For a booking group, 003.1 reserves all lines (any packages and
  sessions) in one command, all or none, and commits or releases the whole group
  together until confirmation; afterwards capacity is committed and released
  per ticket. All channels (direct and OTA) consume the same session capacity.
- **FR-031**: Cancelling a ticket (customer, 003.3 FR-041; Mland, FR-043)
  releases the ticket's seats in every future session of that ticket and
  revokes its future entitlements; it never touches another ticket of the group
  and never rewrites attendance history. *(Added 2026-11-15, owner decision Hồ
  Hoàng Quốc, CHK010, T117)* Timing and effect: at the moment 003.3 calls
  `cancelTicket`, no later. The ticket's validity becomes `CANCELLED`; its QR
  value is **not** regenerated or recorded as a revoked generation (that is the
  recipient-change mechanism of FR-033), it is refused by the ticket state:
  a scan, check-in or override of that ticket in any session answers `409
  TICKET_CONFLICT` and records no attendance. `TICKET_QR_REVOKED` stays for a QR
  replaced by a recipient change only. Sessions already `CHECKED_IN`/`NO_SHOW`
  or finalized keep their attendance record (they are not released), so a
  ticket cancelled after attending session 1 of 3 releases sessions 2–3 only.
- **FR-032** *(rewritten 2026-11-14, owner decision Hồ Hoàng Quốc, ERROR-LOG
  BE-DESIGN-0004, task T092)*: Readiness and the attendance outcome are per
  ticket. The ticket's outcome is read through the existing
  `getTicketAttendanceOutcome(TicketAttendanceOutcomeQuery(ticketReference))`:
  `terminal` when every session of the ticket is `ATTENDANCE_FINALIZED` and the
  24-hour correction window has closed; outcome `ATTENDED`, `PARTIAL` or
  `NO_SHOW` (every session of that ticket is a final `NO_SHOW`). There is no
  separate per-ticket readiness query: the per-ticket outcome carries `terminal`
  and gains `windowClosesAt` (T092; today the method returns
  `BookingAttendanceOutcome(terminal, outcome)`, and T092 decides whether the
  return type is renamed — the name is not decided here).
  - **Event**: Operations publishes
    `TicketAttendanceFinalizedEvent(eventId, ticketReference, windowClosedAt)`
    once per ticket when its outcome becomes terminal. It is reference-only: no
    participant, PII or outcome payload; the consumer reads
    `getTicketAttendanceOutcome`.
  - **Key**: `ticketReference` is `attendance_tickets.reference`; 003.3
    resolves it to its ticket booking through 003.1
    `findTicketBookingByOperationsReference`.
  - **Delivery**: a new append-only migration creates
    `workshop_ticket_attendance_finalization_outbox`, unique
    (`ticket_reference`, `event_type`), using the same scheduler, conditional
    claim, lease and at-least-once delivery with consumer deduplication (by
    `eventId`) as BE-DESIGN-0001.
  - **Removal (same change as T092)**: the booking-wide
    `BookingAttendanceFinalizedEvent`, its outbox
    `workshop_booking_attendance_finalization_outbox` (dropped only by a NEW
    migration; V107 is not edited) and `BookingAttendanceOutcomeQuery` are
    removed; 003.3 T105 replaces the consumer.
  - **Decided 2026-11-14 (Hồ Hoàng Quốc, owner Feature 003):** the 24-hour
    correction window is anchored **per ticket** — 24 hours after the latest
    finalization among that ticket's own sessions, which is what
    `BookingAttendanceOutcomeService.evaluateTicket` already measures. The
    correction deadline a Staff member is held to is therefore per ticket as
    well, so a correction can never be accepted after that ticket's terminal
    event has been published. A booking-line anchor is refused because a group
    line is shared by several tickets, so its anchor can fall later than a
    ticket's own.
  - *(Superseded 2026-11-14: "`BookingAttendanceFinalizedEvent` is published
    once per ticket booking. The query and event still carry no participant or
    PII payload.")*
- **FR-033**: To change a ticket's recipient (003.1 FR-041), Operations MUST
  revoke the ticket's current QR immediately and issue a new opaque QR for the
  same entitlements, idempotently; the revoked QR is refused at check-in. It is
  refused once any session of the ticket is checked in. A resend to the same
  recipient keeps the QR. The on-site resend rule is unchanged: a resend never
  replaces the recipient.
- **FR-034**: For a 003.3 ticket change (FR-044) Operations replaces the
  sessions of each selected ticket, all or none across the selected tickets,
  one seat per ticket per target session, keeping QR and attendance history.
  Each target must be future, `PUBLISHED`, customer-selectable, have capacity
  and have reschedule quota left (FR-035), and the resulting set must satisfy
  the package rules (distinctness, location, same-day slot gap, Wax Casting
  foundry working days). Late-arrival exception: a source session that is
  `IN_PROGRESS` and not yet ended may be replaced; the replacement of the missed
  session must start no later than the old session's end + 7 days; a following
  session moved with it may be outside that window but must keep the package
  order and gap. A Mland-caused replacement uses the same checks but is not a
  customer change.
- **FR-035** *(values decided 2026-11-12)*: Each session has one reschedule
  quota: the maximum number of tickets it may receive by customer change,
  counted per moved ticket and shared by advance changes and late-arrival
  support. The default is 25% of the session capacity rounded down, at least 1
  (for example 3 for a 12-seat session); a Manager may change it per session,
  including 0 to close the session to customer changes. When capacity changes,
  a session still on the default recomputes it; a Manager-set value is kept.
  Mland-caused replacements do not consume or check the quota (capacity only).
  The quota never replaces the capacity check; a moved ticket takes one seat.
  The 25% default is a starting value to review after operating data exists.
  **Decided 2026-11-16 (Hồ Hoàng Quốc, owner Feature 003):** a quota change requires a free-text
  reason (required, at most 500 characters, as in `RescheduleQuotaRequest`); it is not a code of the
  FR-007a catalogue, which covers only check-in override, finalized-attendance correction and session
  cancellation. The audit event `RESCHEDULE_QUOTA_SET` records that text.
- **FR-036**: OTA tickets keep the OTA's policy; these customer change and
  cancellation rules do not apply to them.
  **Decided 2026-11-14 (Hồ Hoàng Quốc, owner Feature 003): 003.2 refuses.**
  `cancelTicket`, `replaceTicketSessions` and `reissueTicketQr` MUST refuse a
  ticket of an OTA-origin booking with one typed failure of their own
  (`REJECTED` with the single reason `OTA_TICKET_NOT_SUPPORTED`, implemented by
  T096; name confirmed 2026-11-15, CHK013), whatever the caller is.
  Operations owns the ticket and its sales channel, so one guard there protects
  every caller — 003.3, Staff screens and any later OTA path — instead of each
  caller repeating the check. Today no such guard exists (read-only grep of
  `ppswbs_backend/src`, 2026-11-14: the only OTA reference in the first two
  services is `WorkshopOtaSnapshotService` publishing the observable snapshot,
  not a refusal), so adding it is part of the FR-036 work and T096's "OTA
  ticket refused" case verifies it.
  **Amended 2026-11-15 (owner Hồ Hoàng Quốc, via 003.4 Clarifications Session
  2026-11-15):** the refusal stays for every general caller, but 003.2 also
  provides one separate facade operation, callable only by 003.4, that revokes
  named tickets of an OTA booking after the OTA has confirmed their
  cancellation (task T123).

## Package availability per location (owner request 2026-11-17)

**Source:** request of Hồ Hoàng Quốc (owner Feature 003), 2026-11-17. **Status:
DRAFT/PROPOSED** — three owner points answered 2026-11-17 (initial
availability, matrix access, financial records on a location move); the rest
awaits owner approval. It serves 003.1 (session discovery and
reserve) and 003.3 (ticket change, FR-044), whose specs need matching edits after
this section is approved.

**Observed baseline (facts, not decisions):** the Package catalogue is global
(Q "Which actor manages Workshop Packages shared across locations"); every
Package has `sameLocationRequired`, which today means the required sessions of
one ticket must share a location; public session discovery already requires one
`locationId`; the five catalogue Packages all have `sameLocationRequired` on,
and the 003.3 change-option query keeps the replacement session at the original
location because of that flag (owner handoff 2026-11-17). Nothing records
whether a Package is sold at a given location.

**Intent:** some Packages are sold at one location and not at another. Price and
Package rules stay the same at every location. A Manager decides, per Package
and per location, whether it is sold there.

- **FR-037** *(Package–location availability)*: For every pair of Package and
  workshop location, 003.2 MUST hold an availability state `ACTIVE` or
  `INACTIVE`. A Package is sellable at a location only when its lifecycle state
  is `PUBLISHED` **and** its availability at that location is `ACTIVE`. Name,
  description, per-participant price, participant bounds and required-session
  rules remain one value per Package; there is no per-location price or rule.
  Initial availability when this section takes effect (owner decision Hồ Hoàng
  Quốc, 2026-11-17): Mland Hà Nội – Hoàn Kiếm has all five Packages `ACTIVE`;
  Mland Hà Nội – Tây Hồ has Signature Brass, Signature Silver 925 and Wax Casting
  `ACTIVE` and Silver Clay 5g and Silver Clay 7g `INACTIVE`; Mland Đà Nẵng – Hải
  Châu (not active) has every Package `INACTIVE`. A Package or location created
  later starts `INACTIVE` at every pair until a Manager enables it (confirmed
  2026-11-17).
- **FR-038** *(Manager matrix)*: An active Manager MUST be able to read all
  workshop locations and all Packages as one Package × location matrix and set
  one pair `ACTIVE` or `INACTIVE` through an explicit, audited Manager command.
  The matrix shows Packages as columns and locations as rows; a ticked cell
  means `ACTIVE` and an unticked cell means `INACTIVE` (owner clarification
  2026-11-17).
  The audit record MUST hold the actor, Package, location, prior and new state
  and time. Setting a pair to the state it already has changes nothing and
  writes no audit record. Only an active Manager may read or write the matrix;
  Staff and Admin are refused (`403`) on both (owner decision Hồ Hoàng Quốc,
  2026-11-17).
- **FR-039** *(Customer discovery and reserve)*: When a Package is `INACTIVE` at a
  location, public session discovery for that Package and location MUST return
  no selectable session and a safe reason `PACKAGE_NOT_AVAILABLE_AT_LOCATION`;
  the same location must not offer the Package in any customer selection. The
  atomic reserve decision (FR-030) MUST recheck, for every required session, that
  the Package is `ACTIVE` at that session's location and refuse the whole
  reserve with `PACKAGE_NOT_AVAILABLE_AT_LOCATION` otherwise. A stale discovery
  result never authorizes a selection. The matrix does **not** block OTA
  bookings: an OTA booking (003.4) is accepted as today even when the pair is
  `INACTIVE` (owner decision Hồ Hoàng Quốc, 2026-11-17; an OTA listing is tied
  to one Package and one location, e.g. the Klook listing for the 750,000 ₫
  basic Package at Mã Mây, Hoàn Kiếm). Changing the matrix never updates an OTA
  listing; doing so is a separate, explicit OTA step owned by 003.4 and is out
  of scope here.
- **FR-040** *(Existing commitments are untouched)*: Setting a pair `INACTIVE`
  MUST NOT cancel, move or refuse operation of any existing booking, ticket,
  session, attendance or already-unexpired capacity hold; holds created before
  the change may still be committed until their own `expiresAt` (same rule as a
  sales pause, SC-005). Future sessions keep existing state and can still be
  operated; the Manager may pause or cancel them separately. A Manager can set a
  pair `INACTIVE` at any time, even while bookings exist; the command is never
  blocked by existing bookings (owner decision Hồ Hoàng Quốc, 2026-11-17).
- **FR-041** *(`sameLocationRequired` meaning)*: `sameLocationRequired` keeps
  one meaning: all required sessions of one ticket must be at the same location.
  It trivially holds for a one-session Package. It no longer means that a ticket
  must stay at its original location when changed; that is decided by FR-042.
- **FR-042** *(Ticket change across locations — serves 003.3 FR-044)*: A change
  MAY move a ticket to sessions at a different location when the Package is
  `ACTIVE` at that location. The resulting sessions of one ticket MUST all be at
  one location when `sameLocationRequired` is set, so a Wax Casting ticket moves
  both sessions to one location where it is sold; changing only one of its two
  sessions to another location is refused with `INVALID_REPLACEMENT` (the
  existing replacement refusal; corrected 2026-11-17 from
  `PACKAGE_SELECTION_INVALID`, which belongs to reserve). A
  Staff member can change a ticket only inside a location in their
  `activeLocationIds` (and under the existing assignment rule); a change whose
  target is in a different location needs an active Manager. A target at an
  `INACTIVE` location is never offered and is refused at commit
  (`PACKAGE_NOT_AVAILABLE_AT_LOCATION`), including a change request approved
  before the pair was set `INACTIVE`. The existing seat, quota, QR and
  attendance-history rules of FR-034 apply unchanged.
- **FR-043** *(Sessions are independent per location)*: Capacity, state,
  reschedule quota (FR-035), check-in opening time and Staff assignments belong
  to each session. Two sessions in the same slot at different locations are
  distinct sessions, are not duplicates and do not conflict. The reschedule
  quota consumed by a change is the target session's own quota. Slot-gap and
  foundry-working-day rules are evaluated on the sessions of one ticket only.
- **FR-044** *(Location name shown)*: Customer session discovery, ticket-change
  options and Manager/Staff session lists MUST return the location's display name
  together with its identifier, so a customer can tell sessions of the same date
  and slot apart ("branch – date/time – seats left – changes left").
- **FR-045** *(Effects of a cross-location move)*: After a ticket moves to
  another location, the location of record of the ticket is the new location;
  Staff access follows the new location and the new session assignment (FR in
  "Staff session-assignment access"); Staff of the old location lose access to
  that ticket going forward but audit history stays. Price is identical across
  locations, so the move itself changes no amount. How money already collected,
  folio lines and add-ons tied to the old location are carried (owner decision
  Hồ Hoàng Quốc, 2026-11-17): amounts stay unchanged; money already
  collected keeps the branch where it was collected (each collection already
  records its branch); collections, charges and bills made after the move are
  attributed to the new location; and add-ons already sold at the old location
  stay as sold and are listed to the Manager for follow-up (refined 2026-11-17,
  owner decision Hồ Hoàng Quốc). The location of a ticket is always read from
  the ticket's current session in Operations, never from a copy taken at booking
  time, so a moved ticket's Staff access, folio and new collections follow the
  new location (owner decision 2026-11-17).
- **FR-046** *(Mland-caused replacement across locations; owner decision Hồ Hoàng
  Quốc, 2026-11-17)*: When a session cancelled by Mland leaves a ticket needing a
  replacement, the equivalent-session offer (003.3, via the 003.2 query of
  FR-034) MUST list every future, `PUBLISHED`, customer-selectable session with a
  seat and reschedule quota left for the same Package at **any** location where
  the Package is `ACTIVE`, not only the original location, ordered by start and
  showing the location name (FR-044). The buyer picks one; the ticket then moves
  to that session's location under FR-045. The one-location rule of FR-041 still
  applies: the offer replaces only the cancelled session and the ticket's other
  session is unchanged, so for a Wax Casting ticket the candidates stay at the
  location of the unchanged session; moving both sessions to another location is
  a customer change under FR-042, not a Mland offer. No Manager is needed because the replacement
  is Mland-caused. An empty list keeps today's direct 100% bank refund.

**Edge cases:** a Package inactive at every location is not sellable anywhere;
a Package `RETIRED` or not `PUBLISHED` stays refused regardless of availability
(`PACKAGE_NOT_PUBLISHED` wins); a deactivation racing a reserve gives one
consistent outcome (reserve either sees `ACTIVE` and commits, or `INACTIVE` and is
refused, never partially reserved); a location that is not active is not sold at
whatever the pair state says; a customer holding tickets at two locations is not
treated as a duplicate booking.

**Assumptions:** the owner has not set per-location price, per-location rules or
a per-location Package lifecycle; those stay out of scope. The safe error code
name is a proposal until the contract is drafted.

## Audit review for Manager (owner request 2026-11-17)

**Source:** request of Hồ Hoàng Quốc (owner Feature 003), 2026-11-17, raised
while checking where the package availability changes (FR-038) can be reviewed.
**Status: DRAFT/PROPOSED** — owner answers of 2026-11-17 are recorded below
(Clarifications, session 2026-11-17 audit review); no Tech Lead approval yet.

**Observed baseline (facts, not decisions):** Operations already records audit
rows for session, attendance, check-in override, check-in reversal, attendance
correction, Staff auto-assignment, master-data and package availability changes,
and for denied attempts (FR-007, FR-007b, FR-038). Each row carries the actor,
the session when there is one, the action, the outcome (`SUCCESS`, `FAILURE`,
`DENIED`, `SENT`), the correlation id, a small set of business identifiers and
the time. Feature 003.3 keeps separate settlement audit records (aggregate type
and reference, action code, actor reference, correlation reference, evidence and
reason reference, time) with no outcome and no session. Today both can only be
read by querying the database; no screen and no read route exists.

**Intent:** a Manager can review who did what, when and with what outcome, without
database access, so that denied attempts, overrides, reversals, availability
changes and settlement actions can be checked after the fact.

**Owner decisions 2026-11-17 (Hồ Hoàng Quốc):** (1) both sources are in scope,
shown as two tabs; (2) the Manager sees row metadata through a whitelist of known
keys; (3) the actor shows as id plus display name; (4) the proposed defaults
(Manager-only, filters, paging, no export, no retention change, reads not
audited) are accepted; (5) the settlement tab shows the full reason text; (6)
Manager-only for both tabs.

- **FR-047** *(audit review screen)*: An active Manager can open an audit review
  screen with two tabs, **Operations** and **Settlement** (FR-052), each listing
  rows newest first. The screen only reads; nothing on it changes an audit row or
  any business data.
- **FR-048** *(access)*: Only an active Manager, with global authority under the
  Feature 003 authorization decision, can read audit rows from either source.
  Staff, Admin, Member and unauthenticated callers are refused with the existing
  forbidden or unauthenticated response and see no audit data. The server decides;
  hiding the menu entry is not the control. A refused Operations-tab read is itself
  audited `DENIED` under FR-007b; a refused Settlement-tab read is recorded the way
  003.3 already records its authorization denials (`DENIED_<reason>`).
- **FR-049** *(filters)*: The Operations tab filters by action, outcome, actor,
  session and a time range, alone or combined. The Settlement tab filters by
  action code, actor, aggregate type, aggregate reference and a time range (no
  outcome or session filter exists for that source). The default view is the last 7 days
  with no other filter (shown on the screen). An empty result shows an empty state, not an
  error. A reversed time range is refused with a validation error and the filters
  stay filled.
- **FR-050** *(paging)*: Each tab is paged with a bounded page size and a stable
  order, so rows written while the Manager browses do not duplicate or skip a row
  between pages.
- **FR-051** *(data shown)*: The Operations tab shows time, actor, action, outcome,
  session when present, correlation id and the metadata keys on a documented
  whitelist (state change, booking code, ticket code, reason code and similar
  business identifiers); any key not on the whitelist is not returned. Personal
  data (e-mail, phone, full name) is never shown from Operations metadata, which
  FR-007b already forbids storing. The actor appears as account id plus display
  name; an account that no longer exists appears by id only.
- **FR-052** *(settlement tab)*: The Settlement tab shows time, action code,
  aggregate type and reference, actor reference with the same actor display as
  FR-051, correlation reference, evidence reference and the **full reason text**
  (owner decision). Because that reason is free text of up to 500 characters that
  may contain personal data typed by the actor, it is visible only to the global
  Manager (FR-048) and is never written to logs. The settlement data is served by a route inside 003.3, which owns its tables;
  Operations does not call 003.3 (a call from Operations into 003.3 would form a module
  cycle). The Settlement actor filter matches the stored reference `account:<id>`. **New actions need no change here** (request 2026-11-17): the tab lists
  every settlement audit row by its stored action code, with no fixed action list,
  so actions 003.3 adds later (for example `TICKET_LOCATION_CHANGED` once 003.3
  T184 is done) appear automatically; until then it shows every row that exists
  today. **Placement:** the screen and the Operations route live in 003.2 (Operations owns
  its audit table, the Manager guard and the Operations menu); the Settlement route
  lives in 003.3 (owner agreed, B5). The page calls both.
- **FR-053** *(reading is not audited)*: Reading either list does not write an audit
  row, because every read would otherwise grow the table it reads. The refused
  read of FR-048 is the only audited read event.
- **FR-054** *(no export, no retention change)*: There is no file export and no
  edit or delete of audit rows from the screen. Existing retention of audit rows is
  unchanged; this section sets no retention period.
- **FR-055** *(failure and recovery)*: If a read fails, the Manager sees a safe
  error with the correlation id and a retry, with filters preserved. A lost
  session returns the Manager to sign-in and back to the same filtered view.

**Edge cases:** metadata that is not valid structured data is shown as no metadata,
never as an error for the whole page; a row with no session or no actor (system
action) lists with those cells empty; rows for a deleted account or session still
list by id; a very large range returns only the first bounded page; an unknown
action string in the filter returns an empty list, not an error.

**Assumptions:** no new audit action is introduced here; the Manager is trusted
with every location's rows under the global Manager decision; the actor display
name comes from the account owner feature's published read operation, not from
account tables; screen design needs a Figma or equivalent approved UI before any
frontend work (frontend gate F01, F05); the Settlement route and its filter contract belong to feature 003.3 (owner agreed 2026-11-17).

## Scope

This feature owns the Workshop Package catalogue/configuration and publication,
plus Manager and Staff operation of published workshop sessions: session/location
capacity, optional Staff roster records, individual attendance-ticket check-in, audited
exception handling and attendance finalization. It consumes confirmed direct
bookings from feature 003.1; it does not create customer bookings, collect money,
perform refunds or connect directly to external OTAs.

## Clarifications

*(Note 2026-11-14: every clarification below that grants Staff authority by
location scope alone is superseded by the 2026-11-06 "Staff session-assignment
access" amendment: a Staff member needs the session location in
`activeLocationIds` **and** an active assignment to the session. Affected
answers are marked inline.)*

### Session 2026-11-17 audit review (owner Hồ Hoàng Quốc)

- Q: Which audit sources does the Manager screen read? → A: Operations audit rows and 003.3 settlement audit records, as two tabs with their own filters (FR-047, FR-049, FR-052).
- Q: Does the Manager see row metadata? → A: Yes, through a whitelist of known keys; unknown keys are not returned (FR-051).
- Q: How is the actor shown? → A: Account id plus display name; id only when the account is gone (FR-051).
- Q: Defaults (global Manager only, Staff/Admin 403 and `DENIED`, filters, paging, no export, retention unchanged, reads not audited)? → A: Accepted (FR-048 to FR-050, FR-053, FR-054).
- Q: How is the settlement reason text shown? → A: Full text, visible only to the global Manager and never logged (FR-052). Risk noted: free text up to 500 characters may hold personal data.
- Q: Who may see the Settlement tab? → A: Global Manager only (FR-048).

### Session 2026-10-23

- Q: Who may finalize attendance for a session and correct it later? → A: A Staff member assigned to that session or a Manager may finalize attendance. Only a Manager may correct attendance after finalization, and the correction requires audit evidence.
- Q: What happens when Manager cancels a session that already has valid tickets? → A: Manager marks the session `CANCELLATION_REQUESTED`, stops its operations and creates an audited handoff case. Feature 003.3 owns customer notification, manual reschedule, refund, QR replacement and capacity consequences.
- Q: Which Staff may perform normal ticket check-in? → A: An active Staff member whose `activeLocationIds` contains the session location, or any active Manager, may perform normal check-in. No session assignment or capability key is required. *(Superseded 2026-11-06: Staff also needs an active assignment to the session.)*
- Q: Which commitments constrain a capacity reduction? → A: Manager may not reduce capacity below the total of confirmed attendance tickets and unexpired payment capacity holds. An expired hold no longer constrains capacity.
- Q: May check-in override move a ticket to a different session or location? → A: No. Override is limited to verification exceptions for an unused valid ticket at its assigned session and location. A change of session or location is handled by feature 003.3.
### Session 2026-10-23

- Q: Which operational states does a session use? → A: The lifecycle is `DRAFT → PUBLISHED → SALES_PAUSED → IN_PROGRESS → ATTENDANCE_FINALIZED`; sales may resume from `SALES_PAUSED`, and `CANCELLATION_REQUESTED` is a pre-finalization cancellation branch.
- Q: When may a customer check in? → A: A valid ticket may be checked in only at or after the Manager-configured `check_in_opens_at`, whose default is 60 minutes before the session start, while the session is `PUBLISHED`, `SALES_PAUSED` *(added 2026-11-15, Q2)* or `IN_PROGRESS`. There is no automatic check-in closing time; check-in ends when attendance is finalized.
- Q: May Manager directly change the slot or branch of a session with confirmed tickets or unexpired payment holds? → A: No. Direct slot/branch changes are allowed only when neither exists. Otherwise Manager uses `CANCELLATION_REQUESTED`, and feature 003.3 resolves customer notification, reschedule/refund and ticket consequences with audit. *(Deferred in V1, 2026-11-15, see T112: direct slot/branch change of an existing session is not offered; it is refused with 400.)*
- Q: How must exceptional operational actions record their audit reason? → A: Check-in override, finalized-attendance correction and session cancellation request require a standardized reason code. A short note or evidence reference is optional or required by the chosen reason code.
- Q: What customer information may Staff see during ticket or booking lookup? → A: Check-in displays only ticket code, session/branch, ticket status and check-in result. Booking lookup displays its tickets and their used/unused status; it does not expose the booking contact's full email or phone number.

### Session 2026-10-23

- Q: Which state follows `CANCELLATION_REQUESTED` after all affected customer cases are resolved? → A: Feature 003.3 reports completion only after all affected customers are resolved (a ticket resolved as in FR-010a; the audited Manager decision was removed, 003.3 A03, 2026-11-16); the session then becomes terminal `CANCELLED` and cannot operate or check in again.
- Q: Does V1 support check-in while Mland is offline? → A: No. V1 check-in is online only; it has no offline scan queue. Staff uses a backup connection or waits for recovery to preserve ticket reuse protection and audit consistency.
- Q: Must a session have an assigned Staff member before publication or start? → A: No. Check-in opens at `checkInOpensAt` while the session remains `PUBLISHED`; a scheduled-start job moves the eligible session to `IN_PROGRESS` at its scheduled start time. Active Staff in the session location or any active Manager may operate it without a session assignment or operational-lead record. *(Staff part superseded 2026-11-06: Staff also needs an active assignment; "check-in while `PUBLISHED`" extended 2026-11-15, Q2, to `SALES_PAUSED`; Q3 sets the missing-Staff flag.)*

### Session 2026-10-24

- Q: What happens to unexpired payment capacity holds when Manager pauses sales? → A: `SALES_PAUSED` rejects new bookings and holds but preserves unexpired holds created before the pause. Those holds may complete payment until their existing deadline, then expire and release capacity normally.
- Q: Who may reverse an erroneous check-in? → A: Only Manager may reverse a recorded check-in before or after attendance finalization. Before finalization, the ticket returns to not checked-in and may be scanned again. After finalization, Manager must select the correct final attendance (`CHECKED_IN` or `NO_SHOW`). The reversal requires a standardized reason code and preserves before/after audit evidence rather than deleting the original scan.
- Q: What is shown when a ticket is scanned again after check-in? → A: The result states that the ticket is already checked in and shows its most recent check-in time only; it does not expose booking-contact data or the identity of the Staff member who scanned it.
- Q: Does a ticket need a separate `COMPLETED` or `ATTENDED` status? → A: No. A ticket remains `CHECKED_IN` after attendance finalization; an unchecked ticket becomes `NO_SHOW`. Workshop closure is inferred from `CHECKED_IN` plus the corresponding final bill being `PAID`, while the session itself records `ATTENDANCE_FINALIZED`.
- Q: Is attendance finalized automatically at the scheduled end time? → A: No. An authorized Staff member in the session location or any active Manager manually finalizes the entire session after it actually ends. Scheduled end time is informational only.

### Session 2026-10-24

- Q: May a session return to `PUBLISHED` or `SALES_PAUSED` after it enters `IN_PROGRESS`? → A: No. `IN_PROGRESS` is one-way: it may transition only to `ATTENDANCE_FINALIZED` or, for an interruption before finalization, `CANCELLATION_REQUESTED`. It must never reopen sales.
- Q: When may a session with no checked-in ticket be finalized? → A: The same as any session: only while it is `IN_PROGRESS`, after an authorized Staff member in the location or any active Manager confirms it has actually ended. It may finish earlier or later than scheduled; finalization is not gated by `scheduled_end_at`, requires confirmation and audit, and marks every unchecked ticket `NO_SHOW`.
- Q: Who may start onsite operation by moving a session to `IN_PROGRESS`? → A: A scheduler moves an eligible `PUBLISHED` session to `IN_PROGRESS` at `scheduledStartAt` and records a system audit event. An explicit state-transition command remains available to an active Staff in the session location or any active Manager for early start/recovery. *(Staff: also needs an active assignment, 2026-11-06; scheduler also starts `SALES_PAUSED`, 2026-11-15 Q2.)*
- Q: May Manager change Staff roster records while a session is `IN_PROGRESS`? → A: Optional roster changes are informational/audited only and never change authorization; active Staff authority comes from role/status and location scope. *(Superseded 2026-11-06: Staff authority also needs an active session assignment, so a Manager assigning or revoking Staff does change access.)*
- Q: Until when may Manager change `check_in_opens_at`? → A: Manager may configure it only while the session is `DRAFT`, `PUBLISHED` or `SALES_PAUSED`. It is locked once the session enters `IN_PROGRESS`.

### Session 2026-10-24

- Q: What happens to `check_in_opens_at` when Manager changes a session's configured slot? → A: A default value becomes 60 minutes before the new slot start time. A Manager-set override remains unchanged, but the system must show its old check-in time and a warning before saving the slot change so Manager can keep it or adjust it; the acknowledgement is audited. *(Deferred in V1, 2026-11-15, see FR-005b and T112: the slot change itself is out of V1, so this recompute and acknowledgement are not live behaviour.)*
- Q: Is there a session operational-lead role? → A: No. V1 gives every active Manager global Workshop Operations authority; no lead assignment or handover is required.
- Q: How is a session cancelled when it has no confirmed ticket or unexpired payment hold? → A: Manager transitions it directly to terminal `CANCELLED` with audit. Only a session with either commitment enters `CANCELLATION_REQUESTED` and the feature-003.3 handoff.

### Session 2026-10-24

- Q: Which tickets appear when Staff looks up a multi-session booking by `booking_code`? → A: Staff sees only tickets for the session and location they are operating; other tickets are not shown and cannot be checked in. Manager uses a separate audited management lookup to view the full ticket set when support requires it.
- Q: Who may request ticket resend from the check-in flow? → A: Active Staff in the session location and any active Manager may request resend only to the booking contact's existing delivery channel. Ticket delivery remains owned by feature 003.1; the request is rate-limited and audited. *(Staff: also needs an active assignment, 2026-11-06.)*
- Q: Who may override check-in after ticket resend fails? → A: An active Staff whose location scope contains the session location or any active Manager may override each unused ticket only for its assigned session and location, using the standardized reason `TICKET_REDELIVERY_UNAVAILABLE`, a short note and audit linked to the failed resend outcome. *(Superseded 2026-11-06: the Staff member must also be actively assigned to the session.)*
- Q: When is ticket resend unavailable for override purposes? → A: When delivery reports an error, or the customer confirms they did not receive or cannot access the ticket after resend. No fixed waiting period applies; Staff records a short note and the system audits the reason and outcome.
- Q: Which tickets are included when Staff requests resend from the check-in flow? → A: The system resends the full ticket set in the booking to the booking contact's existing delivery channel, including tickets for other sessions or locations. Staff check-in authority remains limited to the current session and location.

### Session 2026-10-24

- Q: What resend limit applies from the check-in flow? → A: At most three resend requests per booking in 15 minutes. After the limit is reached, the system sends no further request; an authorized actor may use the documented override path if resend is unavailable.
- Q: Which ticket states are included in a booking resend? → A: Only active, unused tickets. The system excludes tickets that are cancelled, revoked, refunded, `NO_SHOW` or already `CHECKED_IN`.
- Q: In which session states may onsite staff request ticket resend? → A: `PUBLISHED`, `SALES_PAUSED` and `IN_PROGRESS`, regardless of `check_in_opens_at`. Resend is rejected in `CANCELLATION_REQUESTED`, `CANCELLED` and `ATTENDANCE_FINALIZED`.
- Q: Which actors may override check-in for a session? → A: An active Staff member must have the session location in `activeLocationIds`; an active Manager has global authority. Neither role may override an invalid ticket or move it to a different session/location. *(Superseded 2026-11-06: the Staff member must also be actively assigned to the session.)*
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

- Q: May a Manager manage every branch? → A: No. A Manager may manage only branches assigned to them. A higher-privilege actor may manage all branches; Staff authority remains limited to explicitly assigned sessions. *(Superseded 2026-10-29: every active Manager has global authority across all locations; there is no branch-assigned Manager and no separate higher-privilege workshop actor. Noted 2026-11-15, CHK023.)*
- Q: May Manager create a session for a slot that has already started or passed? → A: No. Manager may create only sessions whose slot start is in the future; booking may still remain available close to that start under feature 003.1 policy.
- Q: Which timezone do Mland slots and sessions use? → A: `Asia/Ho_Chi_Minh` for all current Vietnam branches.
- Q: What state does a newly created session start in? → A: Manager may choose `DRAFT` or `PUBLISHED` when creating individual or bulk sessions; the default is `DRAFT`.
- Q: How are audit reason codes managed in V1? → A: The system controls a fixed reason-code catalogue; each code may require a supplementary note or evidence reference.

### Session 2026-10-24

- Q: What happens if an assigned Staff member or operational-lead Manager becomes inactive? → A: Their operational authority is revoked immediately while assignment/lead history remains. The session needs another active assigned Staff member or active operational lead before onsite operation continues.
- Q: Which feature owns Manager-to-branch assignments? → A: Account/personnel management owns their creation, change and revocation. Workshop operations only reads and enforces the assignment. *(Superseded 2026-10-29: Operations reads no Manager-to-location assignment; Manager authority is global. Noted 2026-11-15, CHK023.)*
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
  `IN_PROGRESS` closes OTA sales. Scheduled start from `PUBLISHED` or `SALES_PAUSED` publishes that closure in the
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

- Q: Should a multi-session (currently Wax Casting) participant have one QR ticket or one QR per session?
  → A: Each participant has one persistent QR ticket. It is valid for every
  required session of the package; attendance remains independently recorded
  for each ticket/session pair.
- Q: How is a persistent multi-session QR validated at a session? → A: The
  scanner operates in its current session/branch context. It accepts the QR
  only when the ticket is entitled to that session and branch and has not
  already checked in at that session; it does not let the QR select a session.
- Q: What does a prior check-in at the first multi-session workshop session do to the
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
  scheduled end time or calculate it from per-session states. **Superseded
  2026-11-05 (D2/D3/D6):** readiness carries only `ready`; the terminal
  attendance outcome (24-hour correction window closed, `ATTENDED`/`NO_SHOW`/
  `PARTIAL`) is exposed by `BookingAttendanceOutcomeQuery` (FR-014).
  *(Superseded 2026-11-14 by FR-032: the outcome is per ticket through
  `getTicketAttendanceOutcome`; `BookingAttendanceOutcomeQuery` is removed with
  T092.)*
  Settlement emits a terminal Loyalty outcome only after the correction window
  closes; a `PARTIAL` booking (some tickets `NO_SHOW`) still earns points, and
  only a full `NO_SHOW` blocks earn.
- Q: May a Manager replace a booking's required session with a target session
  that is `SALES_PAUSED`? → A: No. Every replacement target must be future,
  `PUBLISHED`, customer-selectable and have capacity for the booking's complete
  participant count *(per ticket since FR-034, 2026-11-12: one seat per selected
  ticket per target)*. If any source/target condition or any target capacity check
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

### Session 2026-11-03

- Q: How do Manager Operations REST writes apply the module-owned idempotency
  ledger? → A: Every state-changing endpoint used by package, package add-on,
  location, slot or session administration MUST require an `Idempotency-Key`
  header (1–128 characters). The key is the durable operation reference and is
  globally unique across Operations REST commands, while the ledger retains the
  action, request scope and semantic fingerprint. Reusing the same key with the
  same semantic command MUST replay the original response without another audit
  event or business mutation. Reusing it with a changed payload, scope or action
  MUST return typed `409 IDEMPOTENCY_CONFLICT`; an equivalent request while the
  first claim is active MUST return typed `409 IDEMPOTENCY_IN_PROGRESS`. A
  failed technical command abandons the in-progress claim so the same command
  can be retried safely. `X-Correlation-Id` remains tracing/audit context only
  and MUST NOT be used for deduplication.
- Q: Should Workshop Operations use `branch` or `location` in its public contract and data terminology? → A: It uses `location` throughout public API routes, DTO fields and schema terminology. “Branch” is a legacy synonym only in older prose awaiting rebase; it MUST NOT prescribe a separate resource or identifier.

### Session 2026-10-28

- Q: Which 003 subfeature owns the published Workshop Package catalogue and its required-session rules? → A: 003.2 Workshop Operations owns the published Workshop Package master configuration: description, public price, participant constraints, and required-session rules. It exposes a semantic package-discovery/read boundary to 003.1. 003.1 consumes that boundary and freezes the selected package and applicable price into its booking snapshot; it does not own or mutate the package master.
- Q: Does a Manager manage Workshop Packages through 003.2 REST APIs in V1? → A: Yes. 003.2 provides Manager-only REST management for Workshop Package configuration and publication. 003.1 remains the consumer for public package discovery through the internal semantic boundary and for booking snapshots; it does not expose or own package-management commands.
- Q: Does a Workshop Package attach concrete sessions or define only required-session rules? → A: It defines only the required-session rule. Customers select concrete future sessions during booking; 003.2 authoritatively validates their sale eligibility and reserves capacity. Managers create and manage sessions independently, without editing a package whenever the schedule changes.
- Q: What is the confirmed Mland Workshop package catalogue? → A: The five
  commercial offers and their supplied prices/material facts are canonical in
  [catalogue.md](./catalogue.md): Signature Brass, Signature Silver 925, Silver
  Clay 5g, Silver Clay 7g and Wax Casting. Silver Clay is a one-session offer;
  Wax Casting is the two-session offer. Older “Basic Ring” and two-session
  “Silverclay” examples are superseded for new catalogue configuration.
- Q: How does Operations enforce Wax Casting's two-session spacing? → A: The
  two distinct, chronological sessions must use the same location and have
  at least two foundry-working local dates strictly between their dates.
  Monday–Friday are foundry-working dates; Saturday and Sunday are skipped.
  Session time/slot is irrelevant, so Monday's final slot followed by Thursday's
  first slot is valid. This is not the legacy same-day slot-gap rule.
- Q: What lifecycle does a V1 Workshop Package use? → A: `DRAFT → PUBLISHED → RETIRED`. A retired package cannot accept a new booking and cannot be deleted when it has ever been published or frozen into a booking snapshot. Historical bookings retain their frozen snapshot.
- Q: Which module owns the customer-facing Package and session-discovery REST routes? → A: 003.2 owns them because it owns the Package master and authoritative session sale eligibility. The public URLs remain stable for the frontend. 003.1 owns only Booking commands and reads, and consumes the required data through 003.2's internal facade rather than exposing a competing discovery route.
- Q: Should Package publication and retirement be ordinary updates or explicit lifecycle commands? → A: They are explicit Manager-only lifecycle commands below the Package resource. Create/read/update use normal resource routes; publication and retirement use separate commands so authorization and immutable audit clearly record the business transition.
- Q: May a Manager change a published Package's commercial or session-selection rules? → A: Yes. A Manager may change its description, public price, participant constraints or required-session rules. The change applies only to bookings created after the update; each existing booking retains its already frozen Package and financial snapshot.
- Q: Which actor manages Workshop Packages shared across locations? → A: Every active canonical Manager may manage the shared Package catalogue globally. There is no Central/Local Manager tier and `activeLocationIds` does not filter Manager authority.
- Q: How does V1 identify Manager authority without adding an Account role or capability? → A: Operations checks the active canonical `MANAGER` role at command time. Every active Manager has global authority for Manager-authorized actions throughout Feature 003.

- Q: Do public Package discovery and Manager Package management use one REST resource path? → A: Yes. Both use `/api/v1/workshop-packages`. Public callers may only read `PUBLISHED` Packages; active Manager create, update and lifecycle commands are differentiated by method and server-side authorization rather than a separate management namespace.

- Q: Does a Package use a fixed type enum or generic required-session rules? → A: It uses generic `requiredSessionCount` and `sameLocationRequired` rules. The four Signature/Silver Clay catalogue offers currently require one selected session and Wax Casting requires two; these are Package configurations, not a closed package-type enum.

- Q: Does V1 need a separate immutable business package code? → A: No. The system-generated `packageId` is the technical identity and an active Manager supplies an editable public `name`. Every Package also has a required public per-participant price; V1 has no separate `packageCode`.

- Q: What participant constraints does a V1 Workshop Package define? → A: It defines `minParticipants` and `maxParticipants` for one booking *(per booking line, 2026-11-15 Q6)*. A booking line's ticket count must be within that inclusive range and must still fit the authoritative available capacity of every selected required session.

- Q: Which REST command performs Package publish or retirement? → A: An active Manager uses `POST /api/v1/workshop-packages/{packageId}/lifecycle-transitions` with `targetState` `PUBLISHED` or `RETIRED`. Package lifecycle state is never changed through a generic `PATCH` field.
- Q: Which public route discovers customer-selectable future sessions for one Package? → A: 003.2 exposes `GET /api/v1/workshop-packages/{packageId}/sessions`. It is public and returns only `PUBLISHED`, customer-selectable future sessions that satisfy the Package's selection rules. The existing `GET /api/v1/workshop-sessions` remains Manager/Staff-scoped and is never opened for public discovery.
- Q: Where is a Package's participant and required-session rule rechecked when a Booking reserves capacity? → A: 003.1 supplies the selected `packageReference` with its reserve command. Operations rechecks that the Package is `PUBLISHED`, participant bounds, required-session count, same-location rule, sale eligibility and capacity as one all-or-nothing reserve decision. Booking, the frontend and a stale discovery snapshot cannot independently authorize a selection.
- Q: What availability does public Package session discovery disclose? → A: It returns the exact current `remainingCapacity`, together with session identity, canonical location, scheduled start/end, sale state and `customerSelectable`. This is a customer-selection snapshot only; the atomic reserve command remains the authoritative final capacity decision.
- Q: What is the canonical Workshop Session identity shared with Booking? → A: Operations keeps its UUID session identity. 003.1 consumes it only as an opaque UUID/string `sessionReference`; it must retire its legacy `int64` session identifier contract. V1 does not accept two session identity formats.
- Q: Which safe reasons distinguish Package reserve refusal? → A: Operations returns `PACKAGE_NOT_PUBLISHED` when the Package is no longer bookable and `PACKAGE_SELECTION_INVALID` when participant bounds, required-session count or same-location rules fail. Capacity exhaustion remains `INSUFFICIENT_CAPACITY`. These reasons are safe semantic outcomes, not internal rule or persistence detail.
- Q: Which filters are required for public Package session discovery? → A: `locationId` is required. `from` and `to` are optional customer date filters; clients may apply a short default window when absent. The result remains limited to future, `PUBLISHED`, customer-selectable sessions for that Package at the one requested location.
- Q: What happens to the former Silverclay same-day non-adjacent rule? → A: It
  was an illustrative legacy configuration, not a property of the confirmed
  Silver Clay catalogue. `minimumSameDaySlotGap` remains a generic field for a
  same-local-date package rule, but it must not be used for Wax Casting, whose
  foundry-working-date requirement is represented separately.

### Session 2026-11-15

- Q: Has the owner approved the 2026-11-15 content of this spec? → A: No.
  Approval is pending for the whole 2026-11-15 content, including T092 and T096
  work and this session's clarifications; the owner (Hồ Hoàng Quốc) approves once,
  after this clarify session ends. `spec.md`, `plan.md` and `data-model.md` all
  carry "approval pending". *(Supersedes the plan's "re-approved 2026-11-14".)*
  **Update, same day:** after the session ended the owner approved the content
  (option C of this question): "Approved 2026-11-15 by Hồ Hoàng Quốc".
- Q: When a session is `SALES_PAUSED` at `checkInOpensAt` or at its scheduled
  start, may it be checked in and auto-started? → A: Yes. `SALES_PAUSED` only
  stops sales (new bookings and holds). From `checkInOpensAt` check-in is
  available while the session is `PUBLISHED` **or** `SALES_PAUSED`; at the
  scheduled start the scheduler moves a `PUBLISHED` or `SALES_PAUSED` session to
  `IN_PROGRESS` with a system audit event *(F04 reopened 2026-11-16, option B: the OTA `saleState` enum distinguishes `SALES_PAUSED`
  from `CLOSED`; the automatic `SALES_PAUSED` to `IN_PROGRESS` transition
  therefore publishes the complete closed snapshot and advances the version
  exactly once, in the same Operations transaction.)*. Unexpired holds of a
  paused session still complete payment until their deadline. Every earlier
  statement that ties check-in or auto-start to `PUBLISHED` alone is read through
  this answer. `IN_PROGRESS` still never returns to `PUBLISHED`/`SALES_PAUSED`.
- Q: Must a session have at least one assigned Staff before it can be operated,
  and does that bind a Manager? → A: Staff work requires at least one active
  assignment to the session (the 2026-11-06 rule: an unassigned Staff member
  cannot operate it, so a session with no assigned Staff has no Staff
  operator). A Manager is **not** bound: an active Manager may still check in,
  resend and finalize, with the actor audited. Additionally: (1) from
  `checkInOpensAt`, a session with no active Staff assignment is reported to
  Managers as "missing Staff" in the session response (a flag, not a block; the
  response field is `missingStaff`, true from `checkInOpensAt` while no Staff
  assignment is active, accepted 2026-11-15 by the owner Hồ Hoàng Quốc);
  (2) once `checkInOpensAt` has passed, revoking the **last** active assignment
  of a session without confirmation is refused `409` with `Problem.code`
  `LAST_STAFF_ASSIGNMENT_CONFIRMATION_REQUIRED`; the Manager repeats the request
  with `confirmLastAssignmentRemoval: true` (an optional boolean query parameter,
  default false, on the DELETE route, which has no body; đã xác nhận bởi Hồ Hoàng
  Quốc 2026-11-15) and it is accepted. Both the refusal and the
  accepted removal are audited (names accepted 2026-11-15, T115). Publication, scheduled start and Manager early start still need no
  assignment (FR-010b unchanged). *(Answers the former "may a Manager remove the
  last Staff from an `IN_PROGRESS` session" question.)*
  *(Scope added 2026-11-15, owner decision Hồ Hoàng Quốc, CHK030, T115)* Both
  the `missingStaff` flag and the last-assignment guard apply only while the
  session is `PUBLISHED`, `SALES_PAUSED` or `IN_PROGRESS`. In `DRAFT`,
  `CANCELLATION_REQUESTED`, `CANCELLED` and `ATTENDANCE_FINALIZED`,
  `missingStaff` is always false and removing the last active assignment needs
  no confirmation (revocation stays allowed in every state, line above). A
  Manager is exempt from the assignment requirement, not from the guard: the
  guard concerns the Manager's own removal of Staff and is never skipped by role.
- Q: Is the Manager-only lookup of a booking's full ticket set across sessions
  and locations part of V1? → A: Yes. FR-003b's Manager management lookup is V1
  scope, unchanged: read-only, audited, never exposes the booking contact's full
  email or phone. A grep of tasks.md, plan.md and the OpenAPI contract on
  2026-11-15 found no task or route for it, so it becomes an open task (added in
  the sync step), not a deferral.
- Q: When are a ticket's remaining entitlements and QR revoked now that there is
  no VNPay Refund? → A: At the moment 003.3 calls `cancelTicket` (customer or
  Mland cancellation, FR-031); a refund is not a revocation event and Operations
  does not wait for money to move. FR-010aa's "verified-refund consequence" is
  superseded: the projection reflects the resulting entitlement after a
  required-session replacement or a ticket cancellation only.
- Q: Are a Package's `minParticipants`/`maxParticipants` counted per booking
  group or per booking line? → A: Per booking line: the number of tickets on a
  line must be within that line's Package inclusive range. There is no group-wide
  min/max; the group is limited only by each session's remaining capacity.
  *(Supersedes "a booking's participant count" in the 2026-10-26 Package rule
  and FR-001-level wording, read per line from FR-030 on.)*
- Q: How late may the scheduled auto-start be? → A: Within 5 minutes of
  `scheduledStartAt`, including across one restart. A start later than 5 minutes
  is a monitored failure (*2026-11-16, F14: an `ERROR` log plus a metric, no
  email*; not silent); the scheduler still
  catches up and starts the session whenever the system is back, with no upper
  bound on catching up. The explicit early-start/recovery command remains
  available meanwhile (FR-010d).
- Q: Which sessions does Operations publish OTA snapshots for, and how is the
  OTA-visible capacity computed? → A: Sessions are shared by all Packages (a
  session has no package; `data-model.md` WorkshopSession), so there is no
  per-package filter and no OTA-only quota. The snapshot is the session's full
  remaining capacity, as today; direct and OTA sales consume the same capacity
  (FR-030). In V1 the business sells only the cheapest Package on the OTA
  (Signature Workshop — Brass, 750,000 VND direct; the OTA's own higher price is
  not stored or owned by Operations); which offer is sold is decided only by the
  003.4 mapping, not by 003.2. *(Business fact stated by the owner, 2026-11-15;
  catalogue.md still names the V1 offer "Basic Ring" — naming is a 003.4
  question.)*
- Q: Is the Loyalty `BRANCH` identifier the same as `workshop_location.id`? →
  A: Yes, one identifier: Loyalty `BRANCH` ≡ `workshop_location.id`
  (`locationId` in APIs), with no mapping table. If Loyalty code is found to use
  a different key, that is a defect task in Loyalty/Booking, not a spec change.

#### Checklist follow-up (payment-error-access.md), answered 2026-11-15

*(Final decisions of Hồ Hoàng Quốc, owner Feature 003 (owner decision Hồ Hoàng
Quốc, 2026-11-15), on checklist items CHK004, CHK009, CHK014, CHK016, CHK018,
CHK020, CHK024, CHK028, CHK029 and CHK032, plus documentation sync with no new
decision for CHK002, CHK012, CHK013, CHK023, CHK027, CHK034, CHK037, CHK039 and
two extra findings. Rule text is in the FRs named below, the "Error catalogue"
and SC-005–SC-008; code work is Phase 17 of tasks.md (T126–T142) and 003.3
T167.)*

- Q (CHK004): May an unexpired hold be committed after the session auto-started
  or after a cancellation was requested? → A: A commit is accepted while the
  session is `PUBLISHED`, `SALES_PAUSED` or `IN_PROGRESS` and refused `REJECTED
  INVALID_STATE` once it is `CANCELLATION_REQUESTED`, `CANCELLED` or
  `ATTENDANCE_FINALIZED`; the refused hold expires and releases normally
  (FR-010k) and the money follows the decided late-payment path (003.1 FR-040,
  100% refund). The ticket set of a cancellation handoff is therefore frozen at
  the request (FR-010c, FR-010j; T132).
- Q (CHK009): When is a cancellation handoff resolved, and what if it stays
  open? → A: Each affected ticket is resolved once it is moved to a replacement
  session, or a bank refund request is opened for it (no wait for `REFUNDED`),
  or the ticket has no collected money (FREE/deferred) and the buyer's answer or default is recorded and the folio is CLOSED (003.3 A03, 2026-11-16). 003.3 then calls
  `completeCancellation` (today no 003.3 service calls it; 003.3 T167). If the
  session is still `CANCELLATION_REQUESTED` 24 hours after `scheduledEndAt`,
  Operations alerts the active Managers through
  `AccountFacade.listActiveManagerEmails()`; with no active Manager it writes an
  audit entry and a WARN log. SC-008 added (FR-010a; T133).
- Q (CHK014): Which codes and statuses do operational refusals use, and how is
  an early check-in told apart? → A: The "Error catalogue" below is the closed
  list and the OpenAPI `Problem.code` enum. A normal check-in before
  `checkInOpensAt` answers `409 CHECK_IN_NOT_OPEN` with `checkInOpensAt` in the
  body, so the Staff screen can say when check-in opens or suggest the
  `EARLY_CHECKIN` override (FR-005; T126).
- Q (CHK020): What does a fourth resend inside 15 minutes return? → A: `429
  REDELIVERY_THROTTLED` with a `Retry-After` header: the seconds until the
  15-minute window counted from the first request of that window ends; the
  frontend suggests the `TICKET_REDELIVERY_UNAVAILABLE` override (FR-003c;
  T127).
- Q (CHK028): When is `401` returned? → A: Only for a missing, malformed,
  invalid or expired bearer token. A verified identity with no Mland account,
  an inactive account, a wrong role or an actor outside the required scope gets
  `403`. Every operation protected by `bearerAuth` declares `401` (Error
  catalogue, SC-004).
- Q (CHK016): What happens when a caller retries an `operationReference` after
  the 24-hour idempotency record was cleaned up? → A: It is a new command. The
  business-state checks prevent a repeated effect (an already cancelled ticket
  answers `INVALID_STATE` or `TICKET_NOT_FOUND`; holds no longer active are not
  committed or released again). Tests cover `cancelTicket`,
  `commitBookingHolds` and `releaseBookingHolds` (FR-010kf; T134).
- Q (CHK018): What if the Account facade cannot supply role, status or
  `activeLocationIds`? → A: Fail closed with `503 DEPENDENCY_UNAVAILABLE`, no
  business side effect and no cached account facts (FR-012a; T128).
- Q (CHK024): Who creates and edits workshop locations, and what may an Admin do
  in 003.2? → A: An active Manager creates and edits workshop locations (global
  authority). An Admin has no Workshop Operations business authority at all;
  the code must stop treating `ADMIN` as a global workshop actor
  (`isGlobalWorkshopActor`) and stop restricting location creation to `ADMIN`
  (FR-012c; T129). *(Differs from the reviewer's recommendation "location is an
  Admin technical action"; the owner chose the Manager.)*
- Q (CHK029): Who may be assigned to a session, in which session states, and
  with which refusal? → A: Rules in "Staff session-assignment access",
  bullet "Eligibility and session state of an assignment": `STAFF`, `ACTIVE`,
  session location in scope, otherwise `409 STAFF_NOT_ELIGIBLE` with the
  reason; `403` only when the caller is not a Manager; a duplicate assignment is
  a no-op; assignment only in `DRAFT`, `PUBLISHED`, `SALES_PAUSED`,
  `IN_PROGRESS`, otherwise `409 INVALID_STATE`; revocation always allowed except
  the T115 last-assignment guard (T130).
- Q (CHK032): Are refused attempts audited? → A: Yes, for sensitive actions
  only: check-in override, check-in reversal, attendance correction,
  attendance finalization, session cancellation request, Staff assignment or
  revocation, the Manager management booking lookup (FR-003b) and ticket resend
  (including a throttled `429`). The entry holds actor, action, session,
  refusal code, time and correlation, with no customer PII. Refused normal
  check-ins and reads are logged, not audited. Reason given by the owner: the
  Staff screen shows only assigned sessions, but a `403` still happens after a
  revocation mid-shift, from a stale tab or a direct API call (FR-007b; T131).
- Q (CHK009 delivery channel, 2026-11-15): How is the 24-hour alert emailed? → A:
  `workshopoperations` gains the dependency `notificationmanagement :: facade`
  (declared in `package-info.java` `allowedDependencies` by code in T133) and the
  alert goes through `NotificationFacade` with a new alert template in the
  notification module; the notification-module owner (Feature 007) must approve
  the template content before sending is enabled *(superseded 2026-11-16, F06-email:
  dependency and template approved by Hồ Hoàng Quốc, 007 owner only informed)*. Recipients come from
  `AccountFacade.listActiveManagerEmails()`; with no active Manager Operations
  sends nothing and writes an audit entry and a WARN log. *(Approved by Hồ Hoàng
  Quốc 2026-11-15; replaces the open channel decision.)*
- Q (FR-012c, location edit route, 2026-11-15): Is the location edit route in V1?
  → A: Yes. `PATCH /workshop-locations/{locationId}`, Manager only, renames a
  location, changes its address and deactivates or reactivates it, with
  `Idempotency-Key` and audit (T135–T137). The deactivation rule while a
  not-yet-ended session uses the location is `409 INVALID_STATE` with a message;
  renaming, changing the address and reactivating are always allowed.
  *(Approved by Hồ Hoàng Quốc 2026-11-15, including the deactivation rule.)*
- Q (names of the Checklist follow-up, 2026-11-15): May the names chosen in the
  follow-up become contract? → A: Yes. The `reason` values of
  `STAFF_NOT_ELIGIBLE` (`ACCOUNT_NOT_FOUND`, `NOT_STAFF`, `NOT_ACTIVE`,
  `LOCATION_NOT_IN_SCOPE`), the audit outcome `DENIED` of a refused attempt, the
  audit action `CANCELLATION_UNRESOLVED_ALERT` with outcome `SENT`
  *(`NO_ACTIVE_MANAGER` withdrawn 2026-11-16, F06)*, and the rule of one alert per session are contract
  names. *(Approved by Hồ Hoàng Quốc 2026-11-15.)*
- Q (Admin and `GET /workshop-locations`, 2026-11-15): Does an Admin keep read
  access to the location list? → A: No, it stays `403` (decision D). The owner
  asked to confirm that no Admin screen depends on it: if Admin does nothing
  with loyalty or products, it is not needed. QA task T138 checks FE-001
  (branch assignment to an account), Loyalty `ProductBranchPicker` and Product
  management; if any Admin screen uses the route, the question returns to the
  owner. *(Updated 2026-11-16: decided option A, conditional on the T138
  result; see "Analyze follow-up, answered 2026-11-16", open item 2.)*
- Q (CHK002/012/013/023/027/034/037/039, EXTRA-1, EXTRA-2; documentation sync,
  no new decision): → A: `applyVerifiedRefundConsequence` is marked superseded
  for every non-OTA caller and the shared rejection reasons gain the ticket
  reasons (facade contract); `OTA_TICKET_NOT_SUPPORTED` is the fixed name
  (FR-036); "scoped Manager" wording is superseded by the global Manager
  (US1, SC-001, FR-012b, two 2026-10-24 answers); booking lookup without an
  eligible ticket is one generic `404 RESOURCE_NOT_FOUND` (FR-003d); the
  permitted callers of each ticket command are listed (facade contract);
  SC-005–SC-007 added and SC-004 lists the protected data; the OpenAPI
  `WorkshopPackageState` reference is corrected to `PackageState`; FR-010e and
  FR-012 are rewritten to the 2026-11-06 Staff assignment rule and the roster
  wording in the user scenarios is marked superseded.

#### Checklist follow-up, second pass (payment-error-access.md), answered 2026-11-15

*(Items CHK010, CHK015, CHK025, CHK026, CHK030 and CHK035 were left open by the
independent tick. Owner decisions of Hồ Hoàng Quốc, 2026-11-15, for CHK010,
CHK015, CHK026 and CHK030; documentation sync with no new decision for CHK025
and CHK035. Code and test work: tasks T115–T117, T140, T141.)*

- Q (CHK010): What happens to a ticket's QR and status when 003.3 calls
  `cancelTicket`? → A: Timing is the call itself. The QR is kept; the ticket
  becomes `CANCELLED` and a scan answers `409 TICKET_CONFLICT`; the new mechanism
  `TICKET_QR_REVOKED` stays for recipient change only; attended sessions keep
  their record and only future sessions are released (FR-031).
- Q (CHK015): How are repeated or concurrent operational commands handled? → A:
  No `Idempotency-Key` on those five routes; the business state decides
  (`409 INVALID_STATE`, same-value correction is a `200` no-op, resend is
  throttled only) — FR-010kh.
- Q (CHK030): For which session states do `missingStaff` and the last-assignment
  guard apply? → A: Only `PUBLISHED`, `SALES_PAUSED` and `IN_PROGRESS`; in every
  other state the flag is false and no confirmation is needed (Staff
  session-assignment access, Q3 scope note).
- Q (CHK026): What is the Manager cross-session lookup route? → A:
  `GET /workshop-booking-lookups/{bookingCode}`, Manager only, ticket set
  without contact data, `404 RESOURCE_NOT_FOUND` for an unknown code, audited
  (FR-003b, T116). Route name proposed until coded.
- Q (CHK025, documentation sync): Are the Manager-only actions enumerated with
  their audit? → A: FR-007c consolidates the existing rules into one list.
- Q (CHK035, documentation sync): Are handoff open, read and complete symmetric
  between 003.2 and 003.3? → A: The facade contract now defines ticket-level
  `resolved` exactly as FR-010a does and states that the open command keeps
  booking references while 003.3 derives the ticket set (facade contract,
  `WorkshopSettlementFacade` rows). The use of `CANCELLATION_HANDOFF_UNRESOLVED`
  as the rejection of an early `completeCancellation` is decided 2026-11-16
  (owner Hồ Hoàng Quốc; see FR-010a, T122, 003.3 T167).

#### Analyze follow-up, answered 2026-11-16

*(Final decisions of Hồ Hoàng Quốc, owner Feature 003, 2026-11-16, on analysis findings F04, F05, F06, F14 and F15, and on the four remaining open items 2 to 5. Rule text is in FR-003c, FR-007b, FR-010a, FR-010d, FR-010kh and FR-010s; tasks T114, T119, T127, T131, T133, T139, T140.)*

- Q (F04): Does the scheduled start of a `SALES_PAUSED` session publish an OTA
  snapshot? → A: Yes. *(F04 reopened 2026-11-16, option
  B: the OTA `saleState` enum distinguishes `SALES_PAUSED` from `CLOSED`; the
  automatic `SALES_PAUSED` to `IN_PROGRESS` transition therefore publishes the
  complete closed snapshot and advances the version exactly once, in the same
  Operations transaction.)*
- Q (F05): Which refusals are denied-attempt audits? → A: option C: `403`, a
  `409` that is an authority or eligibility refusal, `429`, and the two resend
  `409 INVALID_STATE` cases (`NO_ELIGIBLE_TICKET`,
  `REDELIVERY_REJECTED_BY_BOOKING`). A repeated reversal, finalization or
  cancellation request answers `409 INVALID_STATE` as a state conflict and is
  **not** audited (FR-007b, FR-010kh).
- Q (F06): When is `CANCELLATION_UNRESOLVED_ALERT` recorded? → A: options A+C:
  the audit event (outcome `SENT`) is written only after `NotificationFacade`
  returns success. If sending is disabled or fails, only a WARN log is written
  and no marker, so the next scan retries; with no active Manager only a WARN
  log, no marker. The outcome `NO_ACTIVE_MANAGER` is withdrawn. *(Release is no
  longer blocked by Feature 007, see F06-email below.)*
- Q (F06-email): Who approves the alert email and its dependency, and what is the
  content? → A: Hồ Hoàng Quốc, owner of Feature 003, 2026-11-16, adopting the
  draft as-is. (1) The dependency `workshopoperations -> notificationmanagement
  :: facade` is APPROVED (added to `allowedDependencies` in `package-info.java`
  when T133 is implemented). Dependency and template approved by Hồ Hoàng Quốc
  2026-11-16; the template is added by a Flyway migration (definition in
  `notification_template_definition` + first version in
  `notification_template_version`, status per the module convention); the 007
  module owner is informed by the 003 owner after the change (convention, not a
  technical gate). Release is not blocked by 007. F06 rules stay: SENT audit only
  after `NotificationFacade` succeeds; WARN otherwise; no marker; retry on the next
  scan; no active Manager means WARN and no marker. (2) Template code
  `WORKSHOP_CANCELLATION_UNRESOLVED_ALERT`, `owning_module` `workshopoperations`;
  `allowed_placeholders`: `sessionReference`, `sessionName`, `locationName`,
  `scheduledEndAt`, `hoursSinceScheduledEnd`; no
  credentials, no buyer personal data. Subject: `[Mland] Buổi workshop
  {{sessionName}} vẫn còn vé chưa xử lý sau khi bị hủy`.
  Body (plain + simple HTML): "Kính gửi Quản lý chi nhánh {{locationName}}, Buổi
  workshop {{sessionName}} (mã {{sessionReference}}) kết thúc theo lịch lúc
  {{scheduledEndAt}} nhưng vẫn ở trạng thái chờ hủy sau {{hoursSinceScheduledEnd}}
  giờ. Vẫn còn vé chưa được giải quyết (đổi buổi hoặc mở yêu
  cầu hoàn tiền). Vui lòng kiểm tra các vé của buổi này trong màn hình quản lý và
  xử lý. Đây là cảnh báo gửi một lần cho mỗi buổi. Trân trọng, Hệ thống Mland."
  (3) Recipients: every active Manager (global scope, decision D) via the Account
  facade active-manager email list; one `NotificationFacade` intent per Manager
  with `businessEventReference` =
  `<sessionReference>:CANCELLATION_UNRESOLVED:<managerAccountId>` (idempotent).
  Audit `CANCELLATION_UNRESOLVED_ALERT` outcome `SENT` is written only when all
  intents were accepted (partial failure means WARN, no marker, retry next scan;
  idempotent references prevent duplicate mails). *Amended 2026-11-16 by the 003
  owner (T133, option c): the `unresolvedTicketCount` placeholder is removed from
  the allowlist, subject and body, because Operations does not know which tickets
  003.3 has resolved and no count facade exists; the email only states that
  tickets remain unresolved. `businessEventReference` is
  `<sessionReference>:CANCELLATION_UNRESOLVED` (the Account facade returns emails
  only, no account id; Notification already de-duplicates per recipient
  fingerprint).* T133 acceptance adds the
  migration, a preview check and an allowlist test.
- Q (F14): How is a late auto-start (more than 5 minutes) reported? → A: option
  A: an `ERROR` log plus a metric for operations monitoring; no email.
- Q (F15): Do refused resends count toward three per 15 minutes? → A: option A:
  no. A resend refused with `NO_ELIGIBLE_TICKET` or
  `REDELIVERY_REJECTED_BY_BOOKING` does not count; the counter belongs to
  Operations and counts only a request actually processed.
- Q (open item 2, Admin and `GET /workshop-locations`, 2026-11-16): Does an
  Admin get read access to the location list? → A: option A, **decided:
  conditional on the T138 result** (Hồ Hoàng Quốc, 2026-11-16). T138 first
  checks whether FE-001 (the Admin screen assigning `activeLocationIds`) calls
  `GET /workshop-locations`. If it does, an Admin receives a separate read-only
  grant to the location list only: reference data, explicitly **not** a
  business permission (no create, edit, assignment or audit right), documented
  in FR-012c and the 403 row of the error catalogue (FR-007b scope), and in the
  OpenAPI role text; the T129/T138 tests then expect Admin `200` on
  `GET /workshop-locations` and `403` on every other 003.2 route. If FE-001
  does not call it, the Admin stays `403`. *(Supersedes the 2026-11-15 answer
  above: "stays 403, the question returns to the owner".)*
- Q (open item 3, no location edit route, 2026-11-16): → A: resolved at
  document level: the route is `PATCH /workshop-locations/{locationId}`,
  Manager edits, Admin `403` (FR-012c, T135, T137). It is confirmed when T135 is
  implemented.
- Q (open item 4, self-chosen names, 2026-11-16): → A: **APPROVED as-is**
  (Hồ Hoàng Quốc): `STAFF_NOT_ELIGIBLE` reasons `ACCOUNT_NOT_FOUND`,
  `NOT_STAFF`, `NOT_ACTIVE`, `LOCATION_NOT_IN_SCOPE`; audit outcome `DENIED`;
  audit action `CANCELLATION_UNRESOLVED_ALERT` (outcome `SENT` only); `401` and
  `503` are not audited; the cancellation-unresolved alert is sent once per
  session. These are no longer proposed or to be confirmed. This does **not**
  cover the two resend reasons below.
- Q (open item 5, resend 409 reasons and T048, 2026-11-16): → A: **resolved,
  option A** (Hồ Hoàng Quốc, after comparing with `TicketResendService` and
  `WorkshopCheckInController`): `409 INVALID_STATE` with `reason`
  `NO_ELIGIBLE_TICKET` (no `ACTIVE` unused ticket) and
  `REDELIVERY_REJECTED_BY_BOOKING` (Booking refused redelivery); both names are
  **final**. A session state outside `PUBLISHED`/`SALES_PAUSED`/`IN_PROGRESS`
  stays `409 INVALID_STATE` without these reasons. The code differs today (verified
  2026-11-16): both cases return `400 VALIDATION_ERROR`
  (`WorkshopValidationException`), and a request refused by Booking still
  increments the three-per-15-minutes counter because the counter is saved before
  the Booking call. T139 aligns the code: both become `409 INVALID_STATE` with the
  reasons, and the throttle increment moves so that only a request actually handed
  to Booking and accepted counts (a refusal, including a Booking refusal, never
  counts); T127 adds the `Retry-After` header on `429`. The T048 contract-drift
  test may stay red until T126, T128, T129 and T139 are done.

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
  `requiredSessionCount`, `sameLocationRequired`, non-negative
  `minimumSameDaySlotGap` and non-negative
  `requiredFoundryWorkingDaysBetweenSessions`. The four Signature/Silver Clay
  offers use one required session and a foundry-working-day value of `0`; Wax
  Casting uses two and a value of `2`. For Wax Casting, Operations counts
  Monday–Friday local dates strictly between the two sessions and excludes
  Saturday/Sunday. These configurations are not
  a closed package-type enum. A package may be published only when this feature can
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
  `checkInOpensAt`, check-in opens while it remains `PUBLISHED` or `SALES_PAUSED`
  *(2026-11-15, Q2)*; at scheduled
  start, the scheduler moves it to `IN_PROGRESS`. Active Staff in the session
  location **and actively assigned to it** *(2026-11-06)* or any active Manager may then operate it.
 - An active Manager has global Workshop Operations authority across all
   canonical locations and sessions. An active Staff member may operate only
   sessions whose canonical location is in that actor's `activeLocationIds`.
   Session assignment and operational-lead status are not authorization
   prerequisites. *(Staff: an active assignment IS required, 2026-11-06; the
   operational-lead record stays retired; Manager unaffected, Q3.)*
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
- **Reason-code catalogue (backfilled 2026-11-12 from implementation):**

  | Code | Used for | Note | Evidence |
  |---|---|:-:|:-:|
  | `TICKET_REDELIVERY_UNAVAILABLE` | Check-in override after a failed ticket redelivery | Required | Required (failed-redelivery correlation reference) |
  | `CUSTOMER_DEVICE_UNAVAILABLE` | Check-in override: customer device dead, damaged or unavailable onsite | — | — |
  | `EARLY_CHECKIN` | Check-in override: customer admitted before the check-in window opens | — | — |
  | `PHYSICAL_VOUCHER_VERIFIED` | Check-in override: printed confirmation verified by Staff | — | — |
  | `INSTRUCTOR_UNAVAILABLE` | Session cancellation: instructor/craftsperson unavailable | Required | — |
  | `FACILITY_ISSUE` | Session cancellation: facility, power, tool or equipment failure | Required | — |
  | `WEATHER_EMERGENCY` | Session cancellation: severe weather or natural disaster | Required | — |
  | `RECORDING_ERROR` | Attendance correction: scanning error or mistaken identification | Required | — |
  | `OTHER_REQUIRES_NOTE` | Any unlisted exception | Required unless an evidence reference is given | Accepted in place of the note |

  An unrecognized code is rejected. The catalogue is fixed in code; changing it
  is a spec change.
- **Visible-location configuration (backfilled 2026-11-12 from 8d8e5a4):** the
  deployment setting `workshop.operations.visible-location-ids`
  (environment `WORKSHOP_OPERATIONS_VISIBLE_LOCATION_IDS`) lists the location
  IDs shown by public package/session/location discovery and Operations lists.
  Empty means every active location is visible. It is an environment filter
  (for example to hide test locations), not a business permission, and it does
  not replace Staff location scope.

## User scenarios

### Prioritized user-story map

| Story | Priority | Scenario group | Independent test |
| --- | --- | --- | --- |
| US1 | P1 | Manager configures branches, fixed slots and future sessions. | An active Manager creates a workshop location (FR-012c), creates non-overlapping slots, creates a future session and re-runs bulk generation without duplicate sessions. |
| US2 | P1 | Scheduled start and role/location-scoped operation. | Check-in opens at `checkInOpensAt`; a published or sales-paused session auto-starts at its scheduled start time (within 5 minutes, Q7); Staff is limited by location and active assignment and active Manager is global. |
| US3 | P1 | Staff verifies and checks in one individual ticket. | Two concurrent scans of one valid QR create one attendance action; the later scan returns `ALREADY_CHECKED_IN` with the original `checkedInAt`. |
| US4 | P2 | Staff recovers ticket access and handles authorized exceptions. | A resend returns only active unused tickets to the existing delivery channel and an override cannot cross session/branch. |
| US5 | P2 | Authorized actor finalizes attendance and Manager handles cancellation/correction. | Finalization marks only unchecked active tickets `NO_SHOW`; cancellation with commitments creates a 003.3 handoff without settlement side effects. |
| US6 | P1 | Manager decides which Package is sold at which location; customers and ticket changes follow it. | A Manager sets a Package `INACTIVE` at location A: discovery at A returns no session, reserve at A is refused, existing bookings at A are unchanged, and a ticket change can still move that Package to location B where it is `ACTIVE`. |
| US7 | P2 | Manager reviews workshop audit (Operations and Settlement tabs). | A Manager opens the audit screen, filters by action `WORKSHOP_PACKAGE_AVAILABILITY_CHANGED` and outcome `DENIED`, and sees each row with actor, time and correlation id; Staff and Admin get 403 and no data. |

### Manager configures an operable session

Manager creates or updates a session's location, configured slot, capacity and
sale state. Capacity cannot be reduced below confirmed attendance tickets
plus unexpired payment capacity holds. An expired hold no longer constrains
capacity. Every operational configuration change is audited. The Manager may
publish a draft, pause/resume sales and request cancellation before attendance
finalization. Publication does not require a Staff assignment. At
`checkInOpensAt`, check-in becomes available while the session remains
`PUBLISHED`. At the session's scheduled start time, a recoverable scheduler
auto-starts the eligible published or sales-paused session (Q2) and records a system audit event. An active Staff in the session
location and assigned to it (2026-11-06) or any active Manager may issue an explicit early-start/recovery
command. The Manager may directly change a session's slot or location only
when it has neither confirmed tickets nor unexpired payment capacity holds;
otherwise the Manager must use the `CANCELLATION_REQUESTED` handoff.
*(Deferred in V1, 2026-11-15, Hồ Hoàng Quốc, see T112: slot, location and date
of an existing session are not changed at all; the PATCH refuses them with 400.)*
During
`IN_PROGRESS`, optional Staff roster changes are informational/audited only and
do not grant or revoke authority. *(Superseded 2026-11-06: a Staff assignment
grants and revokes the Staff member's access to the session; see "Staff
session-assignment access". Noted 2026-11-15.)*

### Manager works across canonical locations

A Manager may create/configure slots and sessions and view management lookup
data across every canonical location. Staff authority is derived from active
role/status and direct location scope, not a session assignment. *(Superseded
2026-11-06: a Staff assignment grants and revokes the Staff member's access to
the session; see "Staff session-assignment access". A Manager also creates and
edits the workshop locations themselves, FR-012c, 2026-11-15.)*

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
the target V1 design. *(Superseded 2026-11-06: a Staff assignment grants and
revokes the Staff member's access to the session; see "Staff
session-assignment access". Noted 2026-11-15.)*

### Manager cancels an operable session with customer commitments

Manager may move a session with valid tickets to `CANCELLATION_REQUESTED`. This
immediately stops normal operation and check-in, but does not revoke tickets,
move customers, release capacity or refund money in this feature. The system
creates an audited handoff case for feature 003.3 to resolve those consequences.
After feature 003.3 reports that every affected customer case is resolved (FR-010a; no audited Manager decision, 003.3 A03, 2026-11-16), the session becomes terminal `CANCELLED`.

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
  ticket count of each booking line is within that line's Package bounds
  *(per line, Q6; no group-wide bound)*, the complete selected
  session set satisfies its required-session count, location rule, any
  same-day slot-gap rule and any foundry-working-date rule, and every session
  remains sale-eligible with capacity.
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
  one booking line *(per line, Clarifications 2026-11-15 Q6; was "one
  booking")*, with a valid minimum no greater than its maximum. The ticket
  count of the line MUST be inside that range and still fit every selected
  required session's authoritative available capacity.
  A Package MUST expose generic `requiredSessionCount`,
  `sameLocationRequired`, non-negative `minimumSameDaySlotGap` and non-negative
  `requiredFoundryWorkingDaysBetweenSessions` rules. The four Signature/Silver
  Clay catalogue offers use one selected session and a foundry-working-day value
  of `0`; Wax Casting uses two and a value of `2`. Operations must count
  at least two Monday–Friday local dates strictly between Wax Casting sessions,
  excluding Saturday/Sunday; `minimumSameDaySlotGap` never satisfies that rule.
  003.1 MUST NOT
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
  *(Decided 2026-11-15, Hồ Hoàng Quốc, T112: `PATCH /workshop-sessions/{id}`
  carries and checks `version`; a mismatch returns 409
  `OPTIMISTIC_LOCK_CONFLICT`.)*
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
  authorization prerequisites. *(Superseded 2026-11-06 by the Staff
  session-assignment amendment: Staff needs location scope **and** an active
  assignment to the session; the operational-lead record stays retired.)*
- **FR-003a**: The system MUST limit ticket lookup/check-in results to ticket
  code, session/location, ticket status and check-in outcome; booking lookup may
  show ticket used/unused status but MUST NOT expose the booking contact's full
  email or phone number.
- **FR-003b**: In a Staff check-in flow, the system MUST return from a
  `booking_code` lookup only tickets assigned to the current session and location,
  and MUST NOT allow another ticket to be selected or checked in. It MUST allow
  Manager-only, audited management lookup of the full ticket set across sessions
  and locations, without exposing the booking contact's full email or phone
  number. *(Added 2026-11-15, owner decision Hồ Hoàng Quốc, CHK026, T116)* The
  route is `GET /workshop-booking-lookups/{bookingCode}` (route name proposed
  until T116 is coded, as for T139). Manager only; Staff, Admin and any other
  role get `403`. It returns the booking code and, for each ticket, `ticketCode`,
  `sessionId`, `locationId`, `validityState`, `attendanceState` and
  `checkedInAt` (null when not checked in; added 2026-11-15); no contact
  data, QR value or payment data. An unknown code answers `404
  RESOURCE_NOT_FOUND` (the Manager is allowed to see every booking, so there is
  nothing to conceal, unlike FR-003d). Every successful lookup is audited with
  actor, booking code and correlation ID; a refused one is a denied-attempt
  audit (FR-007b).
- **FR-003d**: If a `booking_code` lookup has no eligible ticket for the
  current session and location, the Staff check-in flow MUST return only a generic
  no-eligible-ticket result and MUST NOT reveal another ticket, session or
  location. This generic result is `404` with code `RESOURCE_NOT_FOUND` and one
  fixed message; an unknown booking code, a booking with tickets only for
  another session, and one with tickets only at another location MUST return an
  identical response *(documentation sync 2026-11-15, CHK027; matches
  `CheckInService` today)*.
- **FR-003e**: The feature MUST provide 003.1 only a delivery-safe ticket
  projection containing ticket identity or QR reference, ticket code, and the
  entitled sessions' location and scheduled start/end. It MUST NOT expose
  attendance, Staff/audit data, or personally identifiable information in that
  projection.
- **FR-003c**: The system MUST allow only an active Staff member whose
  `activeLocationIds` contains the session location *(superseded 2026-11-06:
  and who is actively assigned to the session)* or any active Manager to
  request resend of a booking's full
  set of active, unused tickets to the booking contact's existing delivery
  channel, including tickets for other sessions or locations. It MUST exclude
  tickets that are cancelled, revoked, refunded, `NO_SHOW` or already
  `CHECKED_IN`; apply the feature-003.1 delivery throttle of at most three
  requests per booking in 15 minutes *(2026-11-16, F15: the counter belongs to
  Operations and counts only a request actually processed; a resend refused
  with `NO_ELIGIBLE_TICKET` or `REDELIVERY_REJECTED_BY_BOOKING` does not count)*; prohibit recipient replacement or expanded
  check-in authority; and audit actor, ticket/booking correlation, recipient
  channel and outcome. It MUST permit the request only while the session is
  `PUBLISHED`, `SALES_PAUSED` or `IN_PROGRESS`, regardless of
  `check_in_opens_at`, and reject it in `CANCELLATION_REQUESTED`, `CANCELLED`
  or `ATTENDANCE_FINALIZED`. *(Added 2026-11-15, owner decision Hồ Hoàng Quốc,
  CHK020, T127)* A request beyond the throttle MUST be refused with `429
  REDELIVERY_THROTTLED`, send nothing, and carry a `Retry-After` header whose
  value is the whole number of seconds (rounded up, at least 1) until the
  15-minute window counted from the first request of that window ends; the
  Staff screen then suggests the `TICKET_REDELIVERY_UNAVAILABLE` override
  (FR-004) instead of another resend.
- **FR-004**: The system MUST require a reason and audit record for exceptional
  check-in. An overriding Staff member MUST be active and have the session
  location in `activeLocationIds` *(superseded 2026-11-06: and be actively
  assigned to the session)*; an overriding Manager MUST be active and has
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
  attendance is finalized. *(Added 2026-11-15, owner decision Hồ Hoàng Quốc,
  CHK014, T126)* A normal check-in before `check_in_opens_at` MUST be refused
  with `409 CHECK_IN_NOT_OPEN` whose body carries `checkInOpensAt`, distinct
  from the `409 INVALID_STATE` of a session that is not open for check-in
  (for example finalized or cancelled); the Staff screen shows the opening time
  and may offer the `EARLY_CHECKIN` override.
- **FR-005a**: The system MUST default `check_in_opens_at` to 60 minutes before
  session start and allow Manager to configure it per session only while the
  session is `DRAFT`, `PUBLISHED` or `SALES_PAUSED`; it MUST lock the value when
  the session enters `IN_PROGRESS`. *(Note 2026-11-15: with the FR-005b slot
  change deferred in V1, recording default versus override is the only
  remaining FR-005a/FR-005b behaviour.)*
- **FR-005b**: The system MUST record whether `check_in_opens_at` is default or
  Manager-overridden. When Manager changes a session's configured slot, it MUST
  recompute a default value to 60 minutes before the new slot start time. For an
  override, it MUST preserve the value and display the old check-in time plus a
  warning before saving, allowing Manager to keep or adjust it; [it MUST audit
  the Manager acknowledgement and resulting values *(deferred in V1, not V1
  scope, 2026-11-15)*].
  *(Decided 2026-11-15, Hồ Hoàng Quốc: V1 does not change the slot, location or
  date of an existing session (T112), so the slot-change recompute, warning and
  acknowledgement are deferred with that capability and `SessionWrite` carries
  no acknowledgement field in V1.)*
- **FR-006**: The system MUST allow authorized finalization to assign `NO_SHOW`
  only to unchecked tickets of that session; finalization is allowed to an
  active Staff member whose `activeLocationIds` contains the session location
  *(superseded 2026-11-06: and who is actively assigned to the session)*
  or any active Manager, applies
  to the whole session in one action, and MUST NOT occur automatically at the
  scheduled end time. It MUST be allowed only while the session is
  `IN_PROGRESS` after the actor confirms that the session has actually ended;
  it may occur before or after `scheduled_end_at` and MUST record that
  confirmation in the audit trail.
- **FR-007**: The system MUST record actor, time, source and correlation data
  for check-ins, resend requests, overrides, finalization and corrections.
- **FR-007b** *(added 2026-11-15, owner decision Hồ Hoàng Quốc, CHK032, T131)*:
  A refused attempt (`403`, a `409` that is an authority or eligibility refusal,
  `429`, or the two ticket-resend `409 INVALID_STATE` cases with `reason`
  `NO_ELIGIBLE_TICKET` or `REDELIVERY_REJECTED_BY_BOOKING`; decided 2026-11-16,
  F05) at a check-in override, check-in reversal, attendance correction,
  attendance finalization, session cancellation request, Staff assignment or
  revocation, the Manager management booking lookup (FR-003b) or a ticket
  resend (including a throttled resend) MUST record a minimal denied-attempt
  audit entry: actor account ID (when authenticated), action, session
  reference, refusal code, time and correlation ID, with no customer PII (no
  contact data, QR value or token). Refused normal check-ins and refused reads
  are logged, not audited. A `401` (no verified identity) and a `503`
  (FR-012a) are not audited. A repeated reversal, finalization or cancellation
  request answered `409 INVALID_STATE` is a state conflict, not an authority or
  eligibility refusal, and is **not** audited (FR-010kh; F05, 2026-11-16).
- **FR-007c** *(added 2026-11-15, CHK025, T141; consolidates existing rules, no
  new decision beyond the Q4 lookup audit)*: Manager-only actions and their
  audit, all with actor, time, source and correlation ID (FR-007) and the prior
  state or before/after values where the action changes data:
  | Action (rule) | Audited success event |
  | --- | --- |
  | Create, change or stop a slot (FR-001e) | one event per change: `SLOT_CREATED`, `SLOT_UPDATED` (prior and new label, times, capacity), `SLOT_ACTIVATED` / `SLOT_DEACTIVATED` |
  | Create a session, individually or in bulk (FR-001f) | one `SESSION_CREATED` event per created session, plus the created/skipped report |
  | Publish, pause or resume sales (FR-010) | one `SESSION_STATE_TRANSITION` event with prior and new state |
  | Change a session's capacity (Business model; a location change is deferred, V1 — see T112) | one `SESSION_CAPACITY_CHANGED` event with prior and new capacity |
  | Create or edit a workshop location (FR-012c) | `LOCATION_CREATED`; one event per applied edit with prior and new value: `LOCATION_RENAMED`, `LOCATION_ADDRESS_CHANGED`, `LOCATION_DEACTIVATED`, `LOCATION_REACTIVATED` |
  | Set or clear a session's reschedule quota (FR-035) | one event `RESCHEDULE_QUOTA_SET`, prior and new quota, reason text (free text, FR-035) |
  | Assign or revoke a Staff member (Staff session-assignment access) | one event per assignment or removal; a refused removal of the last Staff and the confirmed one are both audited (Q3) |
  | Reverse a check-in (FR-002b) | before/after, reason code |
  | Correct finalized attendance (FR-008) | before/after, reason code |
  | Request cancellation or cancel directly (FR-009, FR-009a) | prior state, reason code |
  | Manager booking lookup (FR-003b) | actor, booking code |
  A refused attempt at a sensitive action is audited as `DENIED` under FR-007b.
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
  `ATTENDANCE_FINALIZED` or `CANCELLATION_REQUESTED`; or directly `CANCELLED`
  under FR-009a when no ticket or unexpired hold exists.
 - **FR-010b**: The system MAY publish a session without Staff assignment.
   At `checkInOpensAt`, the system MUST make check-in available while the
   session remains `PUBLISHED` or `SALES_PAUSED` *(extended 2026-11-15,
   Clarifications Q2)*. At the session's scheduled start time, a
   recoverable scheduler MUST transition an eligible `PUBLISHED` or
   `SALES_PAUSED` session to `IN_PROGRESS` and record a system audit event. A manual transition remains
   available only for explicit early start or recovery.
- **FR-010d**: The scheduled transition MUST be idempotent and safe across
  restart/retry. An explicit early-start/recovery command MUST be allowed to an
  active Staff in the session location and actively assigned to it
  *(2026-11-06)* or any active Manager and MUST audit the
  actor, timestamp and previous state. The scheduled start MUST happen within
  5 minutes of `scheduledStartAt`, including across one restart; a later start
  is logged at `ERROR` level and increments a monitoring metric (no email), and
  the scheduler still catches up *(Q7, 2026-11-15; channel decided 2026-11-16,
  F14)*.
- **FR-010e** *(rewritten 2026-11-15 per the 2026-11-06 Staff
  session-assignment amendment; was "Staff roster changes are optional
  operational metadata and MUST not change authorization")*: A Staff session
  assignment is an authorization record, not optional metadata. An active Staff
  member may operate a session only while its location is in the actor's
  `activeLocationIds` **and** the Staff member is actively assigned to it
  (`session_staff_assignments.active = true`), both checked at command time.
  Only a Manager assigns or revokes, under the eligibility and session-state
  rules of "Staff session-assignment access"; a revocation removes access
  immediately and assignment history remains. Active Manager authority is
  global and needs no assignment.
- **FR-010f**: The target V1 design MUST NOT create, require or expose a
  session operational-lead role or handover operation.
- **FR-010c**: The system MUST reject new bookings and capacity holds while a
  session is `SALES_PAUSED`, but MUST allow an unexpired hold created before
  the pause to complete payment until its existing deadline. *(Added
  2026-11-15, owner decision Hồ Hoàng Quốc, CHK004, T132)* A commit for an
  unexpired hold is accepted while the session is `PUBLISHED`, `SALES_PAUSED`
  or `IN_PROGRESS`, and refused with `INVALID_STATE` once the session is
  `CANCELLATION_REQUESTED`, `CANCELLED` or `ATTENDANCE_FINALIZED`; the refused
  hold then expires and releases normally (FR-010k) and 003.1 follows its
  late-payment path (003.1 FR-040, 100% bank refund through 003.3).
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
  the booking. It MUST also commit none, with `REJECTED INVALID_STATE`, when
  any session of those holds is `CANCELLATION_REQUESTED`, `CANCELLED` or
  `ATTENDANCE_FINALIZED` *(added 2026-11-15, CHK004; see FR-010c)*.
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
  guaranteed. *(Added 2026-11-15, owner decision Hồ Hoàng Quốc, CHK016, T134)*
  If a caller still retries after cleanup, the command is treated as new; the
  business-state checks MUST prevent a repeated effect: `cancelTicket` on an
  already cancelled ticket answers `REJECTED` (`INVALID_STATE` or
  `TICKET_NOT_FOUND`) and releases nothing; `commitBookingHolds` and
  `releaseBookingHolds` on holds that are no longer active commit or release no
  seat again (FR-010j, FR-010k). The outcome may differ from the first
  execution (for example `ACCEPTED` then `REJECTED`); no tombstone is kept.
  An `IN_PROGRESS` record MUST use a configurable lease timeout;
  after its lease expires, an equivalent retry MAY reclaim the record to recover
  from a crashed execution.
- **FR-010kg**: Each claim or reclaim of an `IN_PROGRESS` idempotency record
  MUST have an incrementing claim version. Completing the record and committing
  its business effect MUST require the current claim version; a request that
  has lost its version MUST be rejected or rolled back.
- **FR-010kh** *(added 2026-11-15, owner decision Hồ Hoàng Quốc, CHK015,
  T140)*: The Staff/Manager operational routes — check-in reversals, attendance
  corrections, attendance finalizations, cancellation requests and ticket
  resends — take **no** `Idempotency-Key`; the ledger of Q 2026-11-03 stays
  limited to the administration routes and facade commands. The routes that
  require `Idempotency-Key` are exactly: `POST /workshop-packages`, `PATCH
  /workshop-packages/{packageId}`, `POST .../lifecycle-transitions` (package),
  `POST` and `PATCH` of package add-ons and their `lifecycle-transitions`,
  `POST /workshop-locations`, `PATCH /workshop-locations/{locationId}`, `POST
  /workshop-locations/{locationId}/slots`, `PATCH /workshop-slots/{slotId}`,
  `POST /workshop-sessions`, `POST /workshop-sessions/bulk`, `PATCH
  /workshop-sessions/{sessionId}`, `PUT .../reschedule-quota`, `POST
  .../state-transitions`, and `PUT` and `DELETE .../staff-assignments`. A retry
  of a refused `DELETE` with `confirmLastAssignmentRemoval=true` MUST use a new
  `Idempotency-Key`. A repeated or
  concurrent request is decided by the business state read under the existing
  session/ticket lock, so the second of two identical requests sees the state
  the first produced: (a) a reversal of a ticket that is no longer checked in,
  a finalization of a session that is not `IN_PROGRESS`, and a cancellation
  request of a session not in an operable state each answer `409
  INVALID_STATE`, with no second audit of success and no denied-attempt audit
  either (a state conflict, not an authority or eligibility refusal; decided
  2026-11-16, F05, FR-007b) *(Superseded 2026-11-16: the earlier text called
  this refusal a denied attempt under FR-007b)*; (b) a correction that sets the value the ticket
  already has answers `200` with the unchanged ticket and writes no second
  audit event; (c) a resend is not deduplicated — each accepted request is a
  new delivery and is limited only by the three-per-15-minutes rule (FR-003c).
  A true parallel pair is serialised by the lock; the loser receives (a) or (b),
  or `409 OPTIMISTIC_LOCK_CONFLICT` when the version check fires first. The UI
  SHOULD disable the control while a request is pending.
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
  automatic `PUBLISHED` or `SALES_PAUSED` to `IN_PROGRESS` transition at `scheduledStartAt` MUST
  persist the session transition, complete closed snapshot/version and
  persistent Modulith publication in the same Operations transaction.
  *(F04 reopened 2026-11-16, option B: the OTA `saleState` enum distinguishes `SALES_PAUSED` from
  `CLOSED`; the automatic `SALES_PAUSED` to `IN_PROGRESS` transition therefore
  publishes the complete closed snapshot and advances the version exactly once,
  in the same Operations transaction.)* An
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
- **FR-010w** *(superseded in part 2026-11-12 by FR-032)*: For a booking/package context requested by 003.3, this feature
  MUST provide only its authoritative settlement-readiness conclusion. It MUST
  be true only when every required session is `ATTENDANCE_FINALIZED`, and false
  when any required session is not finalized, including cancellation. It MUST
  not expose individual attendance, ticket, Staff, audit or PII detail, and
  003.3 MUST NOT derive readiness from scheduled end time or per-session state.
  *(Superseded part, 2026-11-12/14: "a booking/package context" and "every
  required session" now mean one ticket and that ticket's sessions (FR-032);
  the booking-wide `BookingSettlementReadinessQuery` remains only until 003.3
  T106 leaves no caller (T097). *(Updated 2026-11-15: T097 is complete and
  removed the booking-wide types except `BookingSettlementReadinessQuery`,
  kept only for `hasBookingServiceStarted` and `isStaffAssignedToBooking`.)*
  The no-detail and no-derivation rules still
  hold.)*
- **FR-010x** *(superseded in part 2026-11-12 by FR-034)*: For a 003.3-requested complete required-session replacement,
  every target session MUST be future, `PUBLISHED`, customer-selectable and
  have capacity for the booking's complete participant count. This feature MUST
  apply every requested capacity and ticket-entitlement change together or none;
  if any source/target eligibility or capacity condition fails, it MUST leave
  every existing required session, ticket entitlement and capacity unchanged.
  `SALES_PAUSED` MUST have no Manager reschedule exception in V1.
  *(Superseded part, 2026-11-12: "complete required-session replacement" with
  "the booking's complete participant count" becomes per-ticket replacement of
  the selected tickets, one seat per ticket per target, plus the reschedule
  quota check for customer changes (FR-034/FR-035, T094/T095). The all-or-none
  rule, target eligibility and the `SALES_PAUSED` rule still hold. The
  booking-wide `ReplaceRequiredSessionsCommand` remains only until no caller is
  left (T097).)* *(Updated 2026-11-15: T097 is complete and the booking-wide
  `ReplaceRequiredSessionsCommand` is removed.)*
- **FR-010y** *(superseded in part 2026-11-12 by FR-034 (late-arrival exception))*: An ordinary 003.3-requested customer session change MUST accept
  a source required session only while it is future and not `IN_PROGRESS`,
  `ATTENDANCE_FINALIZED`, `CANCELLATION_REQUESTED` or `CANCELLED`. It MUST NOT
  rewrite check-in or attendance history. A source in
  `CANCELLATION_REQUESTED` MUST use the separate Mland-caused cancellation
  remedy instead. *(Superseded part, 2026-11-12: "not `IN_PROGRESS`" gains the
  FR-034 late-arrival exception — an `IN_PROGRESS`, not-yet-ended source may be
  replaced when the replacement starts no later than the old end + 7 days
  (T094, `CUSTOMER_LATE_ARRIVAL`). The other source states and the
  no-history-rewrite rule still hold.)*
- **FR-010z** *(superseded in part 2026-11-12 by FR-034, noted 2026-11-15)*: When a complete required-session replacement succeeds, this
  feature MUST update every affected ticket's entitlement to the resulting
  required-session set and transfer the booking's complete participant capacity
  from replaced sessions to replacement sessions as the same all-or-none result.
  The persistent ticket QR MUST remain unchanged; this feature MUST NOT issue a
  replacement ticket or retain entitlement/capacity in a replaced session.
  *(Superseded part, 2026-11-12: "complete required-session replacement" and
  "the booking's complete participant capacity" become per-ticket replacement of
  the selected tickets, one seat per ticket per target (FR-034, T094). The
  unchanged QR and no-replacement-ticket rules still hold.)*
- **FR-010aa** *(superseded in part 2026-11-12 by FR-034, noted 2026-11-15)*: This feature MUST provide 003.1 an authorized, read-only,
  current delivery-safe ticket/session-entitlement projection for booking and
  Magic-Link reads. Following a successful required-session replacement or a
  ticket cancellation (FR-031; *replaces "verified-refund consequence",
  Clarifications Q5, 2026-11-15*), that projection MUST reflect the resulting
  entitlement, location and schedule rather than a superseded session. It MUST
  continue to exclude attendance, Staff, audit and PII detail; it does not make
  003.1 an owner of tickets or schedules.
- **FR-010a**: The system MUST transition `CANCELLATION_REQUESTED` to terminal
  `CANCELLED` only after feature 003.3 reports all affected customer cases
  resolved as defined below. `CANCELLED` MUST reject
  normal operation and check-in whether it was reached directly or through
  `CANCELLATION_REQUESTED`. *(Added 2026-11-15, owner decision Hồ Hoàng Quốc,
  CHK009, T133 and 003.3 T167)* An affected ticket is resolved when it has been
  moved to a replacement session, or a bank refund request has been opened for
  it (Operations does not wait for `REFUNDED`), or the ticket has no collected money (FREE/deferred) and the buyer's answer or default is recorded and the folio is CLOSED (003.3 A03, 2026-11-16). 003.3 MUST call `completeCancellation`
  (`CompleteCancellationHandoffCommand`) as soon as the last affected ticket of
  the handoff is resolved. *(Decided 2026-11-16, owner Hồ Hoàng Quốc)* When
  `completeCancellation` is called for a session in `CANCELLATION_REQUESTED`
  and Operations finds any ticket of that session still unresolved (not
  exchanged, no refund request opened, no zero-money answer/default recorded
  with the folio CLOSED), Operations REJECTS the call with reason
  `CANCELLATION_HANDOFF_UNRESOLVED` (facade result `REJECTED`; REST-equivalent
  409 `INVALID_STATE` where it surfaces), changes no state, and the call is
  safely repeatable. When every ticket is resolved the call moves the session to
  `CANCELLED`, and a replay returns the same result (idempotent by
  `operationReference`). If a session is still `CANCELLATION_REQUESTED` 24
  hours after its `scheduledEndAt`, Operations MUST alert the active Managers
  once per session through `AccountFacade.listActiveManagerEmails()`; when no
  Manager is active it sends no email and writes a WARN log only, no marker.
  *(Superseded 2026-11-16, F06: the earlier text wrote an audit entry in that
  case.)* The audit event `CANCELLATION_UNRESOLVED_ALERT` (outcome `SENT`) is
  written only after `NotificationFacade` returns success; if sending is
  disabled or fails, only a WARN log is written and no marker, so a later scan
  retries. *(Updated 2026-11-16, F06-email: the dependency and template are
  approved by Hồ Hoàng Quốc 2026-11-16; the template is added by a Flyway
  migration (definition in `notification_template_definition` plus first version
  in `notification_template_version`, status per the module convention); the 007
  module owner is informed by the 003 owner after the change (convention, not a
  technical gate). Release is no longer blocked by Feature 007. The alert is one
  `NotificationFacade` intent per active Manager with `businessEventReference` =
  `<sessionReference>:CANCELLATION_UNRESOLVED:<managerAccountId>`; the audit
  `SENT` is written only when all intents were accepted, a partial failure is
  WARN with no marker and retried at the next scan, and the idempotent references
  prevent duplicate mails.)*
  A handoff with no affected ticket (only unexpired holds) is completed when 003.3
  receives it; the holds expire (FR-010c). *(Added 2026-11-15, owner decision Hồ
  Hoàng Quốc.)*
  The alert is an email sent through `NotificationFacade` (module
  `notificationmanagement :: facade`, approved as a dependency of
  `workshopoperations` by Hồ Hoàng Quốc 2026-11-15) using a new alert template in
  the notification module, whose content Hồ Hoàng Quốc approved 2026-11-16 (template
  `WORKSHOP_CANCELLATION_UNRESOLVED_ALERT`, see the follow-up Q/A F06-email).
  The alert decides nothing and does not change the session state.
- **FR-011**: The system MUST reject a direct session slot or location change
  whenever confirmed tickets or unexpired payment capacity holds exist; Manager
  MUST use the audited `CANCELLATION_REQUESTED` handoff instead.
  *(Deferred in V1, 2026-11-15, see T112: a direct slot, location or date change
  of an existing session is refused unconditionally with 400, with or without
  tickets or holds.)*
- **FR-012**: The system MUST grant an active Manager global Workshop Operations
  authority across all locations and sessions. Staff authority MUST be limited
  to sessions whose location is in the Staff actor's `activeLocationIds`
  **and** to which the Staff member is actively assigned *(rewritten
  2026-11-15 per the 2026-11-06 amendment; earlier wording "limited to sessions
  whose location is in the Staff actor's active location scope" superseded)*.
- **FR-012a**: The system MUST revoke workshop operational authority immediately
  when the actor becomes inactive. It MUST reject Staff operations outside the
  actor's location scope and MUST not depend on session assignment or an
  operational-lead record. *(Superseded in part 2026-11-06 by the Staff
  session-assignment amendment: Staff operations MUST also be rejected without
  an active assignment to the session, and revoking the assignment removes
  access immediately; "MUST not depend on session assignment" no longer holds.
  The operational-lead record stays retired.)* *(Added 2026-11-15, owner
  decision Hồ Hoàng Quốc, CHK018, T128)* When the Account facade cannot supply
  the actor's role, status or `activeLocationIds` (exception or timeout), the
  command MUST fail closed with `503 DEPENDENCY_UNAVAILABLE` and no business
  side effect; Operations MUST NOT use cached account facts and the client may
  retry. Scheduler work that does not need account facts (scheduled start) is
  unaffected.
- **FR-012b**: The system MUST consume, but MUST NOT create, modify or revoke,
  Manager-to-location assignments. Account/personnel management owns that
  assignment lifecycle. *(Superseded 2026-10-29: Manager authority is global,
  so Operations consumes no Manager-to-location assignment; Staff location
  scope (`activeLocationIds`) is still consumed read-only from Account
  Management. Noted 2026-11-15, CHK023.)*
- **FR-012c** *(added 2026-11-15, owner decision Hồ Hoàng Quốc, CHK024, T129)*:
  An active Manager MUST be the only actor who creates and edits workshop
  locations (`POST /workshop-locations` and `PATCH /workshop-locations/{locationId}`, the
  latter added to V1 on 2026-11-15, owner decision Hồ Hoàng Quốc, T135: rename,
  address, deactivate and reactivate; the `code` is immutable), with global
  authority. *(Superseded 2026-11-15: "any later location edit or deactivation
  route follows the same rule", a future route.)* Confirmed rule (owner decision Hồ Hoàng Quốc
  2026-11-15): renaming, changing the address and reactivating are always
  allowed; deactivating (`active=false`) is refused with `409 INVALID_STATE` and
  a message while a session that has not ended (state other than `ATTENDANCE_FINALIZED` or
  `CANCELLED`) uses it. An Admin
  is the system administrator only and MUST have no Workshop Operations
  business authority: no session, slot, location, Package, add-on, attendance
  or Staff-assignment action and no global session visibility; an Admin
  calling a 003.2 business route gets `403`. Assigning `activeLocationIds` to
  Staff for a new location stays in Account Management (FE-001).
  *(Decided 2026-11-16, Hồ Hoàng Quốc, option A, conditional on the T138
  result:)* if T138 finds that FE-001 calls `GET /workshop-locations`, an Admin
  gets a separate read-only grant to the location list only. It is reference
  data and explicitly NOT a business permission: no create, edit, assignment
  or audit right, and `403` on every other 003.2 route. If FE-001 does not
  call the route, the Admin stays `403` on it too.
- **FR-013**: Workshop add-on inventory MUST expose separate public and
  management contracts. The public package/location add-on read MUST expose
  only currently `ACTIVE` add-ons that are eligible for customer display. The
  Manager management read MUST expose `DRAFT`, `ACTIVE` and `RETIRED` add-ons
  for the selected package and location so that a page reload cannot hide
  lifecycle state. Add-on creation, editing and lifecycle transitions MUST use
  the canonical authenticated Operations write routes; the management
  inventory route is read-only and MUST NOT be used as a second create/update
  contract. Frontend code MUST NOT fabricate missing lifecycle states when the
  management API returns an empty result.
- **FR-014** *(superseded 2026-11-14 by FR-032; the booking-wide event, its
  outbox and query are removed with T092 — earlier marker: "superseded in part
  2026-11-12 by FR-032 (per ticket)")*: For the direct-booking open folio (owner decisions D2/D3,
  2026-11-05, see `../contracts/workshop-interfeature-facades.md`), this
  feature MUST publish a reference-only post-commit
  `BookingAttendanceFinalizedEvent(bookingReference, finalizedAt)` once per
  booking when its booking-wide attendance becomes terminal, and MUST expose
  `BookingAttendanceOutcomeQuery(bookingReference)` returning `terminal` and
  `outcome` (`ATTENDED`, `NO_SHOW`, `PARTIAL`). `terminal` is true only when
  every required session is `ATTENDANCE_FINALIZED` **and** the 24-hour Manager
  attendance-correction window for the booking has closed; the event is
  published at that point, not at each session finalization, so no later
  correction can change the outcome. `NO_SHOW` only when every ticket on every
  required session is a final `NO_SHOW`. The event and query carry no ticket,
  participant or PII payload and never decide a financial outcome; Settlement
  (003.3) owns folio closure. *(Superseded text above, kept for history:
  `BookingAttendanceFinalizedEvent(bookingReference, finalizedAt)`,
  `BookingAttendanceOutcomeQuery(bookingReference)` and the booking-wide
  terminal rule are replaced by the per-ticket
  `TicketAttendanceFinalizedEvent(eventId, ticketReference, windowClosedAt)` and
  `getTicketAttendanceOutcome` (FR-032). Still in force: reference-only, no PII,
  no financial decision in Operations; Settlement owns folio closure.)*

### Error catalogue

*(Decided 2026-11-15, owner decision Hồ Hoàng Quốc, checklist CHK014 with
CHK018, CHK020, CHK028 and CHK029; code task T126 and the tasks named per row.
This is the closed list of `Problem.code` values the Workshop Operations REST
API returns; the OpenAPI `Problem.code` enum mirrors it. Facade outcomes use
the typed rejection reasons of `../contracts/workshop-interfeature-facades.md`,
not this list. Every body is `application/problem+json` with `type`, `title`,
`status`, `code`, an optional safe `detail` and the correlation ID; no body
reveals another session, location, ticket or customer contact data.)*

| HTTP | `code` | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Bean Validation or a request rule fails (including a changed slot, location or date on session PATCH). |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | A state-changing administration route (those listed in FR-010kh) is called without `Idempotency-Key`. |
| 401 | `UNAUTHORIZED` | Only a missing, malformed, invalid or expired bearer token (CHK028). Declared on every `bearerAuth` operation. |
| 403 | `FORBIDDEN` | Verified identity but no Mland account, account not `ACTIVE`, wrong role (including any Admin, FR-012c; exception decided 2026-11-16, conditional on T138: an Admin may read `GET /workshop-locations` only, as reference data and not a business permission), or a Staff member not actively assigned to the session. |
| 403 | `LOCATION_SCOPE_DENIED` | A Staff caller whose `activeLocationIds` excludes the session location. Never used for an assignee (see `STAFF_NOT_ELIGIBLE`). Check order for a Staff caller: location scope first (`LOCATION_SCOPE_DENIED`), then assignment (`FORBIDDEN`). |
| 404 | `RESOURCE_NOT_FOUND` | Unknown resource, and the single generic booking-lookup result of FR-003d. |
| 409 | `INVALID_STATE` | The session or ticket state does not allow the action (for example check-in after finalization, finalization outside `IN_PROGRESS`, a Staff assignment outside `DRAFT`/`PUBLISHED`/`SALES_PAUSED`/`IN_PROGRESS`, deactivating a location that a not-yet-ended session uses (confirmed 2026-11-15, T135), a ticket resend when the booking has no `ACTIVE` unused ticket (`reason` `NO_ELIGIBLE_TICKET`) or when Booking refuses the redelivery (`reason` `REDELIVERY_REJECTED_BY_BOOKING`; T139)). For resend the status is always 409, never 400 or 404, so the response does not reveal whether a booking exists. The two `reason` names are final (owner, 2026-11-16, option A); the code returns `400 VALIDATION_ERROR` today and T139 aligns it. `Problem.reason` therefore appears with `STAFF_NOT_ELIGIBLE` and with this resend `INVALID_STATE`. |
| 409 | `CHECK_IN_NOT_OPEN` | Normal check-in before `checkInOpensAt`; the body adds `checkInOpensAt` (FR-005, T126). |
| 409 | `TICKET_CONFLICT` | The ticket is not valid for check-in (cancelled — including after `cancelTicket`, FR-031, CHK010 — revoked, refunded). |
| 409 | `TICKET_QR_REVOKED` | A QR revoked by a recipient change (FR-033). |
| 409 | `INSUFFICIENT_CAPACITY` | Capacity below commitments or no seat left. |
| 409 | `SLOT_OVERLAP_CONFLICT` | A slot overlaps another slot of the location. |
| 409 | `DUPLICATE_SESSION` | A session already exists for the location, date and slot. |
| 409 | `OPTIMISTIC_LOCK_CONFLICT` | Stale `version` on session PATCH (T112). |
| 409 | `IDEMPOTENCY_CONFLICT` | Same `Idempotency-Key` with a different command. |
| 409 | `IDEMPOTENCY_IN_PROGRESS` | Same `Idempotency-Key` while the first request still runs. |
| 409 | `STAFF_NOT_ELIGIBLE` | The account to assign does not exist, is not `STAFF`, is not `ACTIVE` or lacks the session location in scope; the body adds `reason` (`ACCOUNT_NOT_FOUND`, `NOT_STAFF`, `NOT_ACTIVE`, `LOCATION_NOT_IN_SCOPE`) (CHK029, T130). |
| 429 | `REDELIVERY_THROTTLED` | A fourth resend for the booking within 15 minutes; `Retry-After` header in seconds (FR-003c, T127). |
| 500 | `INTERNAL_ERROR` | Unexpected technical failure; no internal detail. |
| 503 | `DEPENDENCY_UNAVAILABLE` | The Account facade cannot supply role, status or `activeLocationIds`; fail closed, no side effect (FR-012a, T128). |
| 409 | `LAST_STAFF_ASSIGNMENT_CONFIRMATION_REQUIRED` | Revoking the last active Staff assignment after `checkInOpensAt` without `confirmLastAssignmentRemoval: true` (Clarifications Q3, T115; name accepted 2026-11-15). Refusal and the later confirmed removal are both audited. |

The confirmation-required refusal for revoking the last assignment after
`checkInOpensAt` (Clarifications Q3) is the `LAST_STAFF_ASSIGNMENT_CONFIRMATION_REQUIRED`
row above; T115 implements it and the contract test keeps the code in the closed enum.

## Success Criteria

- **SC-001**: An active Manager can create an individual future session or run a
  bulk generation within 90 future days; duplicate location/date/slot combinations
  are skipped and reported without overwriting an existing session.
- **SC-002**: Confirmed attendance tickets plus unexpired capacity holds never
  exceed the session capacity, including when concurrent hold requests occur.
- **SC-003**: Concurrent normal scans for one valid ticket produce exactly one
  attendance action; every later scan reports `ALREADY_CHECKED_IN` and the
  original `checkedInAt` without disclosing booking-contact data.
- **SC-004** *(rewritten 2026-11-15, CHK039/CHK028; was "An inactive actor or
  actor outside the required location/session scope cannot configure, start,
  check in, finalize or retrieve protected workshop data")*: An
  unauthenticated caller (`401`), an inactive actor, an Admin, or a Staff
  member whose `activeLocationIds` excludes the session location or who is not
  actively assigned to the session (`403`) cannot configure, start, check in,
  override, resend, finalize or correct that session, and cannot read its
  session detail or list entry, attendance roster, booking lookup, ticket or
  check-in results, or Staff assignments; every such attempt is refused with no
  business side effect.
- **SC-005** *(added 2026-11-15, CHK037)*: While a session is `SALES_PAUSED`,
  100% of new booking holds are refused, and every unexpired hold created
  before the pause can still be committed until its own `expiresAt`.
- **SC-006** *(added 2026-11-15, CHK037/CHK020)*: A fourth ticket-resend request
  for the same booking within 15 minutes is refused with `429` and a
  `Retry-After` header, sends nothing, and is audited as a denied attempt.
- **SC-007** *(added 2026-11-15, CHK037)*: 100% of Staff operations on a
  session the Staff member is not actively assigned to — including
  immediately after the assignment is revoked — are refused with `403` and
  leave no business side effect.
- **SC-008** *(added 2026-11-15, CHK009/CHK037, owner decision Hồ Hoàng
  Quốc)*: Every session in `CANCELLATION_REQUESTED` becomes `CANCELLED` once
  each affected ticket is moved, has a bank refund request opened, or the ticket has no collected money (FREE/deferred) and the buyer's answer or default is recorded and the folio is CLOSED (003.3 A03, 2026-11-16); a session still unresolved 24 hours after
  `scheduledEndAt` raises exactly one Manager alert (or, with no active
  Manager, one audit entry and a WARN log).
- **SC-009** *(added 2026-11-17, FR-037–FR-040)*: 100% of reserve attempts for a
  Package at a location where it is `INACTIVE` are refused with no capacity
  consumed, while every booking and ticket created before the change keeps
  working unchanged.
- **SC-010** *(added 2026-11-17, FR-038)*: A Manager can switch any Package ×
  location pair in one action, sees the result immediately in the matrix, and
  every change has one audit record; no non-Manager can switch a pair.
- **SC-011** *(added 2026-11-17, FR-042)*: A ticket change to a location where
  the Package is `INACTIVE` is never offered and never commits; a Wax Casting
  change always ends with both sessions at one location where it is `ACTIVE`.
- **SC-012** *(added 2026-11-17, FR-044)*: Every session shown to a customer or
  to staff in discovery, change options and lists carries its location name, so
  two sessions at the same date and slot are never indistinguishable.

- **SC-013** *(added 2026-11-17, FR-047 to FR-050)*: A Manager finds every denied attempt, availability change or settlement action of a chosen day in under one minute without database access, and no non-Manager can read any audit row.

## Out of scope

Workshop checkout/bills, payment collection, refund, customer self-service
changes, OTA callbacks and ticket delivery to individual participants.
