# Data Model: Workshop Operations

**Date created**: 2026-10-24
**Date updated**: 2026-10-31
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Authorization and lifecycle rebase

The target model does not persist capability grants or operational-lead
records. Account role/status/active-location data remains owned by Account
Management and is consumed read-only. Session Staff assignment records, if
retained for roster/audit visibility, do not determine authorization. Manager
authority is global while the account is active. `checkInOpensAt` is the
independent check-in availability threshold; the scheduled start timestamp
drives the later `PUBLISHED -> IN_PROGRESS` state transition. Neither event
requires a new session ownership record.

## Ownership Map

| Record | Owning module | Notes |
| --- | --- | --- |
| Canonical Account role/status and active location scope | Account Management | Consumed through a facade; never copied as authorization truth. Operations compares a session's canonical `location_id` directly with those location IDs and applies its own action rules. |
| Commercial booking, payer, payment, invoice, customer delivery | Workshop Booking | Referenced by immutable external booking IDs/codes only. |
| Workshop Package master, canonical workshop location, slot, session, hold, attendance ticket, attendance and audit | Workshop Operations | The aggregate model defined below. A location is not duplicated as an independent branch resource. Package master data is not commercial Booking data. |
| Facade command idempotency ledger | Workshop Operations | Technical persistence for exactly-one business execution within its retention window; not an audit or business aggregate. |
| Settlement, refunds, reschedule resolution | Workshop Settlement and Changes (003.3) | Receives cancellation handoffs; is not implemented here. |

## Core Records

### WorkshopPackage

`packageId` is a system-generated technical identity. A Package has an editable
public `name`, description, required public per-participant price, inclusive
`minParticipants` and `maxParticipants`, generic `requiredSessionCount`,
`sameLocationRequired`, non-negative `minimumSameDaySlotGap`, lifecycle state
and audit timestamps/references. It
defines selection rules only: it has no concrete dated-session attachment,
branch mapping or local copy of a Location.

Constraints and ownership invariants:

- participant bounds are positive, inclusive and `minParticipants <=
  maxParticipants`;
- required session count is positive; `sameLocationRequired` is a rule that
  applies to the customer-selected required-session set, not a storage
  relationship to one or more sessions;
- the current Basic Ring and Silverclay configurations are respectively
  `(requiredSessionCount: 1, sameLocationRequired: true,
  minimumSameDaySlotGap: 0)` and `(requiredSessionCount: 2,
  sameLocationRequired: true, minimumSameDaySlotGap: 1)`; a value of `1`
  requires at least one configured slot between two selected sessions on the
  same local date. They do not form a closed Package-type enum;
- state transitions are `DRAFT -> PUBLISHED -> RETIRED`; `RETIRED` rejects new
  Booking selection and a Package that has been published or frozen into a
  Booking snapshot is never deleted;
- edits to a published Package are prospective only. Booking owns the selected
  Package and financial snapshots for an accepted Booking and must never
  recompute them from this master record;
- a Package has no separate business package code in V1.

Package commands are Operations-owned. Any active canonical `MANAGER` may
administer Packages globally; `activeLocationIds` does not narrow Manager
authority anywhere in Feature 003. There is no Central Manager tier or persisted
Package-to-Manager assignment. Staff operational actions remain location-scoped.

### WorkshopLocation

Canonical `workshop_location.id` (`Long`), immutable `code`, display name,
address snapshot, active flag, created and updated timestamps. A location is
never hard-deleted while it has session history. This is the same identity held
in Account Management `activeLocationIds`; it has no separate branch ID,
UUID reference or mapping record.

### WorkshopSlot

`id`, `location_id`, label, local start/end time, default capacity, active flag,
version and timestamps. Configured slots for a location may not overlap whether
they are active or inactive. Updating a slot affects only future session
creation; it never rewrites an existing session.

### WorkshopSession

`id`, `location_id`, `slot_id`, local workshop date, `scheduled_start_at`,
`scheduled_end_at`, `check_in_opens_at`, `check_in_opening_source`
(`DEFAULT` or `MANAGER_SET`), capacity, lifecycle state, version, timestamps
and optional cancellation-handoff reference.

Constraints:

- unique `(location_id, workshop_date, slot_id)`;
- capacity is at least one;
- start must precede end;
- no direct location/slot change once confirmed tickets or unexpired holds exist;
- only `PUBLISHED` accepts new holds; state rules are in the specification.

### Optional SessionStaffRoster

If retained, roster records contain `session_id`, target Mland `account_id`,
active interval, assigning actor and audit reference for operational visibility
only. They do not grant or revoke authorization. There is no
`SessionOperationalLead` record in the target V1 model, no lead handover and no
separate personnel identifier. Inactive accounts lose operational use through
the Account facade status check without erasing historical roster/audit data.

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
requests. The business grouping is the booking reference and requested session
set carried by the approved facade command/outcome. Capacity holds remain
business records; the separate `FacadeCommandIdempotencyRecord` below owns
duplicate-command persistence and must not be inferred from audit metadata.

### FacadeCommandIdempotencyRecord

An Operations-owned technical record for every state-changing
`WorkshopOperationsFacade` command. Its logical identity is
`facadeAction + operationReference`; that pair is unique. It contains only the
semantic request fingerprint, status (`IN_PROGRESS` or `COMPLETED`), immutable
terminal business outcome when completed (`ACCEPTED` or `REJECTED`), original
`correlationReference`, claim version, in-progress lease-expiry, completed-at,
retention-expiry and timestamps.

The record is separate from `OperationsAuditEvent`, `CapacityHold`,
`AttendanceTicket` and their metadata. It stores neither a raw command payload,
Firebase token, PII, QR value, audit JSON nor a public REST response. Its safe
outcome is the existing typed facade outcome, sufficient to return the original
business result for an equivalent retry.

An initial command durably claims `IN_PROGRESS`. The same logical identity with
a different fingerprint is rejected. The same fingerprint returns `IN_PROGRESS`
while its lease is live, or may atomically reclaim an expired record by advancing
the claim version. Completion must conditionally prove the current claim version
inside the same local MySQL transaction as all command business effects. A
stale claimant therefore commits neither a terminal ledger outcome nor capacity,
ticket, attendance or audit side effects. Technical failure rolls back the
business transaction and leaves no completed outcome.

`COMPLETED` records remain available for at least 24 hours. Cleanup is eligible
only after their retention expiry. The caller must not retry after that window;
the ledger does not promise duplicate protection once the record has been
removed. Lease duration is configuration owned by Operations deployment, not a
client-supplied value.

### Cross-module facade boundary

Operations exposes two synchronous internal boundaries to Booking. The capacity
boundary returns only sale-eligibility snapshots and the outcomes of atomic
reserve, commit and idempotent-release requests. The ticket-projection boundary
returns only a post-commit delivery-safe projection: ticket identity or QR
reference, ticket code, and entitled sessions with location and scheduled
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

### Fixed workshop add-on catalogue

Operations owns an add-on entry identified externally by an opaque UUID and
scoped to one Package and one canonical `workshop_location.id`. It retains a
customer-safe description, positive whole-VND price and lifecycle (`DRAFT`,
`ACTIVE`, `RETIRED`). Active Manager changes are audited. Editing is prospective;
each accepted Settlement charge stores the selected description and price
snapshot and remains unchanged when the catalogue entry changes or retires. The
Operations facade returns an eligible snapshot only for an active entry,
published Package and active location; it never returns an internal numeric
add-on row ID.

### Package-discovery boundary

Operations also owns a semantic internal read boundary for 003.1 to discover a
currently `PUBLISHED` Package and evaluate its generic participant and
required-session rules. Its selection snapshot contains the opaque
`packageReference`, public per-participant price, participant bounds,
required-session count, same-location rule and same-day slot-gap rule. It
returns Package facts only;
it does not return
Operations entities/repositories, Account data, Booking data, payment data,
attendance, ticket data or PII. Booking consumes that result to select and
freeze a Package snapshot and never mutates Package master data.

The shared interfeature facade contract defines this as
`PublishedPackageSelectionQuery(packageReference)` and
`PublishedPackageSelectionSnapshot(packageReference, publicPerParticipantPrice,
minParticipants, maxParticipants, requiredSessionCount, sameLocationRequired,
minimumSameDaySlotGap)`.
It is a conceptual Java boundary contract, not approval of persistence shape.

Customer REST discovery is separate from the facade: for one Package and one
required canonical `locationId`, optional `from`/`to` filters return only
future `PUBLISHED`, customer-selectable sessions. Each public result exposes
the Operations UUID as an opaque `sessionReference`, canonical location,
scheduled start/end, sale state, `customerSelectable` and exact current
`remainingCapacity`. It never exposes hold, ticket, Booking, Staff, audit or
PII data, and its capacity value is a selection snapshot rather than a reserve
guarantee.

### OTA-observable session projection

#### Approved T053-T055 boundary

Operations owns the durable `WorkshopSessionOtaSnapshot` projection and one
monotonic `sourceVersion` sequence per session. Its allowed fields are
`sessionReference`, `locationReference`, `scheduledStart`, `scheduledEnd`,
`sellableAvailability`, `saleState`, `sourceVersion`, `changeReference` and
`effectiveAt`. The persistent internal event is
`WorkshopSessionOtaSnapshotChanged`, carrying the complete snapshot after the
owning transaction commits. It contains no booking/payment, ticket,
attendance, Staff, PII or OTA mapping data.

The source-version row is updated transactionally with every completed change
to an OTA-observable value. `(sessionReference, sourceVersion)` is unique and
versions increase once per transaction even when schedule, availability and
sale state change together. Internal-only changes do not advance the version.
The Operations resync query returns one complete current snapshot at the
existing version; it never creates a business change.

003.4 is a consumer only: it deduplicates older/equal versions, requests the
single-session resync on a gap or unknown ordering, and owns OTA mapping and
provider delivery. Consumer or provider failure cannot mutate Operations
capacity, sale state or direct booking. No broker or external OTA call is part
of the Operations transaction.

Operations makes conceptual, non-public schedule and availability/sale-state
snapshots available to 003.4 after a completed local change alters
OTA-observable data. They are not Operations entities, physical schema
proposals, public REST DTOs, or OTA provider payloads. The schedule snapshot
contains opaque session identity, location, scheduled start/end, source change
identity/version and effective time. The availability/sale-state snapshot
contains opaque session identity, location scope, sellable availability, sale
state, source change identity/version and effective time. Neither contains PII,
Booking/payment data, ticket detail or OTA mapping.

Sellable availability is total capacity less confirmed ticket seats and active,
unexpired direct-Mland holds. Each session has one monotonically increasing
source-version sequence across both snapshot kinds. A completed transaction that
changes one or more OTA-observable values advances it once and yields the full
post-state; internal-only changes do not advance it or expose a snapshot. A
`DRAFT` session exposes none. `PUBLISHED` makes the snapshot available;
`SALES_PAUSED` retains its distinct sale state. `IN_PROGRESS`,
`ATTENDANCE_FINALIZED`, `CANCELLATION_REQUESTED` and `CANCELLED` expose closed
sale state. The scheduled-start transition from `PUBLISHED` to `IN_PROGRESS`
persists the session state, complete closed snapshot/version and Modulith event
in the same Operations transaction. `ATTENDANCE_FINALIZED` does not create a
second version when the snapshot is already closed and no other observable
value changed. Hold creation, release and expiry can change availability;
pause/resume, start and cancellation/closure can change sale state. Hold-to-
ticket commitment does not create a snapshot when the exposed post-state is
unchanged.

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
- A Booking references a Package only through Booking-owned frozen snapshot
  data. Operations Package master changes never rewrite an existing Booking,
  payment or final-bill amount.
- The database enforces unique public ticket codes and QR references and the
  session location/date/slot uniqueness; service validation explains failures.
- Every state-changing Operations facade command uses the dedicated idempotency
  ledger. The unique logical identity, semantic fingerprint, lease/reclaim and
  claim-version conditional completion above preserve no-more-than-one business
  execution for an equivalent command during the retention window.
- Capacity-changing and check-in operations additionally use their approved
  aggregate-level concurrency protection to preserve all-or-nothing capacity
  and ticket/session invariants; it is independent of the facade-command
  ledger and must not be substituted by an audit lookup.
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

The append-only migration for the idempotency ledger must preserve existing
Operations data, introduce the logical unique identity and fields needed for
fingerprint, status, original correlation, claim version, lease and retention,
and be verified on MySQL for concurrent claim/reclaim/finalisation. The specific
migration version is selected only at implementation time.
