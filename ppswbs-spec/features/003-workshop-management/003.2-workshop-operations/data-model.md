# Data Model: Workshop Operations

**Date created**: 2026-10-24
**Date updated**: 2026-10-24
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Ownership Map

| Record | Owning module | Notes |
| --- | --- | --- |
| Account, active status, capability, Manager branch assignment | Account/personnel | Consumed through a facade; never copied as authorization truth. |
| Commercial booking, payer, payment, invoice, customer delivery | Workshop Booking | Referenced by immutable external booking IDs/codes only. |
| Workshop branch, slot, session, hold, attendance ticket, attendance and audit | Workshop Operations | The aggregate model defined below. |
| Settlement, refunds, reschedule resolution | Workshop Settlement and Changes (003.3) | Receives cancellation handoffs; is not implemented here. |

## Core Records

### WorkshopBranch

`id`, immutable `code`, display name, address snapshot, active flag, created and
updated timestamps. A branch is never hard-deleted while it has session history.

### WorkshopSlot

`id`, `branch_id`, label, local start/end time, default capacity, active flag,
version and timestamps. Configured slots for a branch may not overlap whether
they are active or inactive. Updating a slot affects only future session
creation; it never rewrites an existing session.

### WorkshopSession

`id`, `branch_id`, `slot_id`, local workshop date, `scheduled_start_at`,
`scheduled_end_at`, `check_in_opens_at`, `check_in_opening_source`
(`DEFAULT` or `MANAGER_SET`), capacity, lifecycle state, version, timestamps
and optional cancellation-handoff reference.

Constraints:

- unique `(branch_id, workshop_date, slot_id)`;
- capacity is at least one;
- start must precede end;
- no direct branch/slot change once confirmed tickets or unexpired holds exist;
- only `PUBLISHED` accepts new holds; state rules are in the specification.

### SessionStaffAssignment and SessionOperationalLead

Assignments retain `session_id`, personnel reference, active interval, assigning
actor and audit reference. A session has zero or more Staff assignments and at
most one active Manager operational lead. The lead is a session-level role,
not a global role grant. Inactive personnel immediately lose operational use
without erasing their historical assignment.

### CapacityHold

`id`, `session_id`, commercial booking reference, requested ticket count,
state (`ACTIVE`, `CONFIRMED`, `RELEASED`, `EXPIRED`), expires-at, version and
timestamps. Holds are created only for a published session. A confirmed hold is
the source commitment from which individual tickets are issued.

### AttendanceTicket

`id`, immutable commercial booking reference, `session_id`, per-booking
sequence, unique public ticket code, opaque QR lookup reference, validity state
(`ACTIVE`, `CANCELLED`, `REVOKED`, `REFUNDED`) and attendance state
(`NOT_CHECKED_IN`, `CHECKED_IN`, `NO_SHOW`), issue/revoke/check-in timestamps,
version and safe audit links. Feature 003.3 reports cancellation, refund and
reschedule consequences through a facade; Operations records their resulting
ticket-validity projection but does not decide those commercial outcomes.

The two states are intentionally separate: a non-active ticket is never checked
in; an active ticket can be unchecked, checked in, or converted to no-show at
session finalisation. There is one ticket per participant, not one per booking.

### AttendanceAction and OperationsAuditEvent

Attendance actions record check-in, override, finalisation consequence or
Manager correction with ticket/session, actor, time, channel, reason code and
safe reason note/evidence reference. Operations audit events record state,
assignment, lead and cancellation changes with before/after-safe metadata.
Both are append-only; corrections add a new event and update the ticket
projection rather than altering historical evidence.

### CancellationHandoff

`id`, session, requester, requested-at, standard reason, optional required
evidence, status and reference returned by 003.3. It prevents a requested
cancellation from being treated as an already-resolved cancellation.

## State Models

### Session lifecycle

```text
DRAFT -> PUBLISHED -> IN_PROGRESS -> ATTENDANCE_FINALIZED
              |              |
              v              v
        SALES_PAUSED --------> IN_PROGRESS
              |
              +---- resumes to PUBLISHED

pre-finalization session with confirmed ticket or unexpired hold
  -> CANCELLATION_REQUESTED -> CANCELLED
pre-finalization session with neither confirmed ticket nor unexpired hold
  -> CANCELLED
```

`CANCELLED` and `ATTENDANCE_FINALIZED` are terminal. Only an authorised active
operator may take a transition, and transition audit is mandatory.

### Capacity hold lifecycle

```text
ACTIVE -> CONFIRMED
ACTIVE -> RELEASED
ACTIVE -> EXPIRED
```

No state reopens a released or expired hold. A commercial retry creates a new
hold, subject to session capacity and lifecycle.

### Attendance lifecycle

```text
ACTIVE / NOT_CHECKED_IN -> ACTIVE / CHECKED_IN
ACTIVE / NOT_CHECKED_IN -> ACTIVE / NO_SHOW   (whole-session finalisation)
```

After finalisation, only the Manager correction path may change the projection;
the original action remains immutable.

## Referential and Concurrency Rules

- A ticket references exactly one session and commercial booking reference.
- The database enforces unique public ticket codes and QR references and the
  session branch/date/slot uniqueness; service validation explains failures.
- A capacity-changing operation locks the session and its relevant active holds.
- Ticket check-in locks the ticket row. A second simultaneous scan observes the
  first result and receives a conflict rather than generating another check-in.
- Audit records use immutable identifiers, actor IDs and safe metadata only;
  no password, Firebase token or unnecessary participant PII is persisted.

## Migration Rules

Select the next Flyway version only when implementation begins, after inspecting
the live migration history. Do not edit prior migrations or use the legacy reset
migration in production. Existing booking-level tickets require a separately
approved mapping/reissue run; schema migration alone must preserve source IDs
and must not silently revoke, split or email tickets.
