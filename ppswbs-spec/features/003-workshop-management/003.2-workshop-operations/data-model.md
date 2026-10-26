# Data Model: Workshop Operations

**Date created**: 2026-10-24
**Date updated**: 2026-10-26
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Ownership Map

| Record | Owning module | Notes |
| --- | --- | --- |
| Canonical Account role/status and active location scope | Account Management | Consumed through a facade; never copied as authorization truth. Operations combines those facts with its branch/session-assignment data to apply its own action rules. |
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

For a booking that requests capacity in multiple sessions, the separate
session holds form one logical commitment set. Creation is all-or-nothing:
every requested session receives the same requested participant count or no
hold exists. Confirmation succeeds only when every expected hold is active;
release changes active holds once and is idempotent for repeated or late
requests. The grouping identity and persistence mechanism remain deferred to
the approved cross-feature facade and implementation design.

### Cross-module facade boundary

Operations exposes two synchronous internal boundaries to Booking. The capacity
boundary returns only sale-eligibility snapshots and the outcomes of atomic
reserve, commit and idempotent-release requests. The ticket-projection boundary
returns only a post-commit delivery-safe projection: ticket identity or QR
reference, ticket code, and entitled sessions with branch and scheduled
start/end. Neither boundary exposes Operations entities, repositories,
attendance, Staff/audit data or PII. These are conceptual contracts, not
approval of Java names, DTO fields, persistence shape or public HTTP routes.

For 003.3 Settlement, Operations also owns two conceptual outcomes, not physical
records or public DTOs: a booking/package settlement-readiness conclusion, and
an all-or-none complete required-session replacement outcome. Readiness is true
only when every required session is `ATTENDANCE_FINALIZED`. A successful
replacement moves the complete participant-capacity commitment and each affected
ticket's entitlement from every replaced session to the complete resulting set;
the persistent QR remains the same. Failure changes neither capacity nor
entitlement. Individual attendance detail, ticket detail, Staff/audit and PII
are never returned for this purpose.

### OTA-observable session projection

Operations makes conceptual, non-public schedule and availability/sale-state
snapshots available to 003.4 after a completed local change alters
OTA-observable data. They are not Operations entities, physical schema
proposals, public REST DTOs, or OTA provider payloads. The schedule snapshot
contains opaque session identity, branch, scheduled start/end, source change
identity/version and effective time. The availability/sale-state snapshot
contains opaque session identity, branch scope, sellable availability, sale
state, source change identity/version and effective time. Neither contains PII,
Booking/payment data, ticket detail or OTA mapping.

Sellable availability is total capacity less confirmed ticket seats and active,
unexpired direct-Mland holds. Each session has one monotonically increasing
source-version sequence across both snapshot kinds. A completed transaction that
changes one or more OTA-observable values advances it once and yields the full
post-state; internal-only changes do not advance it or expose a snapshot. A
`DRAFT` session exposes none. `PUBLISHED` and `SALES_PAUSED` sessions expose or
refresh a snapshot; `CANCELLATION_REQUESTED` and `CANCELLED` expose closed sale
state. Hold creation, release and expiry can change availability; pause/resume
and cancellation/closure can change sale state. Hold-to-ticket commitment does
not create a snapshot when the exposed post-state is unchanged.

The snapshot is carried to 003.4 by a persistent internal Spring Modulith event
publication recorded in the same local Operations transaction as its source
change; V1 uses no message broker. Delivery to 003.4 is at-least-once: that
consumer ignores duplicate/older source versions and requests an authoritative
resync if it detects a gap or cannot establish order. Resync returns the complete
current schedule, availability and sale state of the one affected session at its
existing source version; it is not a business change. A failure in 003.4 can
suspend only its own OTA mapping and must not mutate Operations capacity, sale
state or direct-Mland booking behaviour.

### AttendanceTicket

`id`, immutable commercial booking reference, per-booking sequence, unique
public ticket code, opaque QR lookup reference, validity state (`ACTIVE`,
`CANCELLED`, `REVOKED`, `REFUNDED`), issue/revoke timestamps, version and safe
audit links. Feature 003.3 reports cancellation, refund and reschedule
consequences through a facade; Operations records their resulting
ticket-validity projection but does not decide those commercial outcomes.

Each ticket has one or more **session entitlements**. An entitlement associates
one ticket with one session and carries that ticket's attendance state for that
session (`NOT_CHECKED_IN`, `CHECKED_IN`, `NO_SHOW`). This is a conceptual
relationship, not approval of a database table, key or persistence shape. A
non-active ticket is never checked in; an active ticket can be independently
unchecked, checked in, or converted to no-show in each entitled session. There
is one ticket per participant, not one ticket per booking or per session.

### AttendanceAction and OperationsAuditEvent

Attendance actions record check-in, override, finalisation consequence or
Manager correction with ticket/session, actor, time, channel, reason code and
safe reason note/evidence reference. Operations audit events record state,
assignment, lead and cancellation changes with before/after-safe metadata.
Both are append-only; corrections add a new event and update the ticket
entitlement attendance projection rather than altering historical evidence.

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

For a multi-session commitment set, confirmation and release have an
all-or-nothing business result; no session may be left as a newly committed or
released partial result of that request.

### Attendance lifecycle

```text
ACTIVE / NOT_CHECKED_IN -> ACTIVE / CHECKED_IN
ACTIVE / NOT_CHECKED_IN -> ACTIVE / NO_SHOW   (whole-session finalisation)
```

This lifecycle applies independently to each ticket/session entitlement. After
that session's finalisation, only the Manager correction path may change the
projection; the original action remains immutable.

## Referential and Concurrency Rules

- A ticket references one commercial booking; each entitlement references one
  session. A ticket may be entitled to more than one session.
- The database enforces unique public ticket codes and QR references and the
  session branch/date/slot uniqueness; service validation explains failures.
- Capacity-changing and check-in operations use an approved concurrency
  mechanism to preserve their respective all-or-nothing and ticket/session
  idempotency invariants; the mechanism remains unselected.
- Every Booking-to-Operations capacity command participates in one local MySQL
  transaction spanning the Booking writes and synchronous capacity-facade work.
  Gateway communication remains outside that transaction; an error rolls back
  the local command outcome rather than requiring a compensating hold release.
- Events may run only after an Operations or Booking local state transition for
  non-critical notification/downstream handoff; they do not replace the
  synchronous capacity or ticket-projection boundaries. The approved 003.4
  OTA-observable schedule and availability/sale-state projections are such
  post-transition handoffs, use the unified source-version/resync behaviour
  above, and are carried by persistent internal Spring Modulith event
  publications; no V1 broker is selected.
- Audit records use immutable identifiers, actor IDs and safe metadata only;
  no password, Firebase token or unnecessary participant PII is persisted.

## Migration Rules

Select the next Flyway version only when implementation begins, after inspecting
the live migration history. Do not edit prior migrations or use the legacy reset
migration in production. Existing booking-level tickets require a separately
approved mapping/reissue run; schema migration alone must preserve source IDs
and must not silently revoke, split or email tickets.
