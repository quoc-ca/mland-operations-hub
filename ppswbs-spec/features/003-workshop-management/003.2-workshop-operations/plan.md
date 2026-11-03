# Implementation Plan: Workshop Operations

**Date created**: 2026-10-24
**Date updated**: 2026-10-31
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Branch**: `003.2-workshop-operations` | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from
`ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/spec.md`

## Authorization and scheduled-start rebase

V1 uses canonical role/status/location data from the public Account facade.
Staff authority is limited by `session.locationId ∈ activeLocationIds`; a
session assignment is operational metadata, not an authorization prerequisite.
An active Manager has global Workshop Operations authority and there is no
operational-lead model in the target contract. The capability key
`workshop.checkin.override` and `AccountFacade.can(...)` are not used.

At `checkInOpensAt`, check-in becomes available while an eligible session
remains `PUBLISHED`. At the session's scheduled start time, a recoverable
scheduler transitions it to `IN_PROGRESS` and records a system audit event. The transition must
be idempotent, timezone-aware and safe across restart/retry. A manual state
transition remains only for explicit early start or recovery.

**Status**: Design draft — internal capacity semantics and facade-command
idempotency are clarified; implementation must rebase the affected task before
changing persistence or code.

## Summary

Build the operational backbone for Workshop Packages and sessions: canonical
workshop locations, reusable slots, sessions, capacity commitments, individual
attendance tickets, authorised check-in, exception audit, and manual attendance
finalisation.

The agreed target boundary is deliberately different from legacy code:
`workshopoperations` owns every operational record needed to decide capacity or
attendance, including the attendance ticket, QR reference and ticket code.
`workshopbooking` owns the commercial booking, payment, invoice and customer
ticket delivery. Booking reaches Operations only through the synchronous
`WorkshopOperationsFacade`, whose capacity and delivery-safe ticket-projection
operations are fixed in the shared
[003 internal facade contract](../contracts/workshop-interfeature-facades.md).
No module may read another module's repositories or entities directly.

### Canonical Workshop Location Boundary

`Workshop Location` is the one physical-site resource for Workshop Operations.
Its canonical identity is `workshop_location.id` (`Long` / REST `int64`). The
`activeLocationIds` supplied by Account Management contain those same values;
authorization therefore compares `session.locationId ∈ actor.activeLocationIds`
directly. There is no `WorkshopBranch`, `branchId`, `workshop_branches` table,
UUID branch reference, or branch-to-location mapping in the target.

Public REST routes and DTO fields use `location`, including
`/workshop-locations` and `locationId`. The current branch-named code,
contracts and tests are legacy and must be replaced rather than adapted with a
duplicate identifier.

### Workshop Package Ownership and Discovery Boundary

`workshopoperations` owns the Workshop Package master: editable public name and
description, required per-participant public price, inclusive participant
bounds, generic required-session rules and lifecycle. A Package has only a
system-generated `packageId`; V1 has no separate business package code and no
concrete dated-session attachment. It also defines a non-negative
`minimumSameDaySlotGap`: Basic Ring currently uses `1`/same-location/gap `0`,
while Silverclay uses `2`/same-location/gap `1` (one configured slot must fall
between same-local-day selected sessions). These are configuration examples,
not a closed Package type.

The lifecycle is `DRAFT -> PUBLISHED -> RETIRED`. A retired Package rejects new
selection but is never deleted after publication or after a Booking has frozen
it. Published Package edits are prospective: 003.1 owns its selected Package
and financial snapshots and must not recalculate them from master data.

Operations owns public discovery of published Packages and customer-selectable
future sessions, as well as active-Manager Package administration on the same
Package resource. A public caller can observe only `PUBLISHED` Packages. Every
active canonical `MANAGER` has global authority for Manager-authorized actions
throughout Feature 003; `activeLocationIds` does not filter Manager access.
There is no Central Manager tier or extra Account role/capability.

Public Package routes include `GET /workshop-packages/{packageId}/sessions` for
customer discovery. It requires a canonical `locationId` and accepts optional
`from`/`to` date filters. It returns only future `PUBLISHED`,
customer-selectable sessions satisfying the Package rules, with exact current
remaining capacity as a non-guaranteed selection snapshot. The Manager/Staff
`GET /workshop-sessions` route remains protected and operational-only. 003.1
must retire its competing legacy discovery route.

The shared 003 interfeature-facade contract supplies the approved semantic
Package discovery query/snapshot and requires `packageReference` on the
capacity-reserve command. Operations rechecks Package publication, participant
and selected-session rules atomically with reserve; Booking cannot authorize a
selection from a stale discovery snapshot.

### Fixed workshop add-on catalogue

Operations owns fixed customer-selectable add-ons, each scoped to one Package
and one canonical workshop location. An active Manager has global authority to
create a draft, prospectively edit, activate, or retire an entry; every
state-changing command is audited. Public discovery returns active entries only
for a published Package and active location. Price is a positive whole-VND
amount. Settlement obtains the eligible price through the Operations facade
and stores the selected amount and description in its immutable charge, so
later catalogue changes do not rewrite an existing bill. Customer requests
cannot supply a price.

### Account Target-Authorization Dependency

For actor authorization, Operations consumes the read-only Account Management
target-account boundary for `accountId` existence, canonical role, current
status and active location IDs. Operations must not read Account entities or
repositories or create a duplicate role store. There is no capability decision
call or operational-lead handover boundary in the target V1 design.

### Resolved OTA-Observable Snapshot Boundary

Operations remains the sole source of session schedule, capacity and direct-
Mland sale state. For a mapped Basic Ring session, it makes a schedule snapshot
and an availability/sale-state snapshot available to 003.4 only after a
completed local change alters OTA-observable data. The schedule snapshot is
limited to opaque session identity, location, scheduled start/end, source change
identity/version and effective time. The availability/sale-state snapshot is
limited to opaque session identity, location scope, sellable availability, sale
state, source change identity/version and effective time. Neither contains PII,
Booking/payment data, ticket detail or OTA mapping. Sellable availability
subtracts confirmed ticket seats and active, unexpired direct-Mland holds.

Each session has one monotonically increasing source-version sequence shared by
both snapshot kinds. A completed transaction that changes one or more
OTA-observable values advances it once and exposes the complete post-state; an
internal-only change does neither. An authoritative resync returns the current
complete snapshot for one affected session at its existing version. 003.4 owns
provider mapping/delivery: it deduplicates at-least-once delivery, resyncs after
a gap, and suspends only its affected mapping on a gap/order or provider
delivery failure. Operations does not call an external OTA and that failure
never pauses direct-Mland sales or changes Operations capacity.

### Resolved Ticket/Session Boundary

### Approved OTA Publication Contract (T053-T055)

The Operations module owns the internal `WorkshopSessionOtaSnapshot` boundary
and its durable per-session source version. The snapshot contains only
`sessionReference`, `locationReference`, `scheduledStart`, `scheduledEnd`,
`sellableAvailability`, `saleState`, `sourceVersion`, `changeReference` and
`effectiveAt`. `WorkshopSessionOtaSnapshotChanged` is the persistent internal
Spring Modulith publication. It carries the complete snapshot and no booking,
payment, ticket, attendance, Staff, PII or OTA mapping data.

The source version is allocated in the same Operations transaction that changes
an observable session value. A unique `(sessionReference, sourceVersion)`
invariant and conditional publication claim make retries at-least-once but
ordered. An internal-only change does not allocate a version. The resync
boundary is an Operations-owned read-only facade query returning the complete
current snapshot for exactly one session at its existing version; resync never
allocates a version or changes business state.

003.4 consumes the event, ignores duplicate/older versions, requests resync on
a gap or unknown order, and owns provider mapping/delivery. A 003.4 failure
may suspend only its own mapping. It cannot mutate Operations capacity, sale
state or direct-Mland booking. V1 uses no broker and no external OTA call in
the Operations transaction. Migration version remains a tech-lead-selected
append-only value at implementation time.

One participant has one persistent QR ticket, including for Silverclay. Its
validity is evaluated against its entitled session in the scanner's current
session/location context, and attendance is independent for every ticket/session
pair. Operations supplies Booking only a delivery-safe ticket projection: QR
reference or ticket identity, ticket code, and each entitled session's location
and scheduled start/end. Booking adds commercial/contact information and sends
it to the customer. `WorkshopOperationsFacade` supplies sale eligibility and
atomic reserve/commit/release outcomes, plus only the delivery-safe projection
after successful commitment. This is an internal synchronous boundary: its
facade types and operations are fixed in the shared 003 contract; database
shape and locking mechanism remain implementation decisions. Public HTTP
location routes and DTO fields are defined by this feature's OpenAPI contract.

### Resolved Settlement and Required-Session-Change Boundary

For a booking/package context supplied by 003.3, Operations alone determines
settlement readiness. It returns only the conclusion: ready is true only when
every required session is `ATTENDANCE_FINALIZED`; any unfinished or cancelled
required session yields false. It exposes no attendance, ticket, Staff, audit or
PII detail, and Settlement must not infer readiness from schedule time or
per-session records.

For a Manager-mediated customer change, 003.3 supplies the complete resulting
required-session set. Operations accepts a source only while future and outside
`IN_PROGRESS`, `ATTENDANCE_FINALIZED`, `CANCELLATION_REQUESTED` and `CANCELLED`.
Every target must be future, `PUBLISHED`, customer-selectable and have capacity
for the full participant count. Operations updates all affected entitlements and
capacity together or changes none. The persistent QR is retained; a replaced
session retains neither entitlement nor capacity. `SALES_PAUSED` has no V1
Manager-reschedule exception. A cancellation-requested source uses 003.3's
separate Mland-caused remedy instead.

These are synchronous internal boundaries. Their facade operations, boundary
types, typed outcomes, and idempotency rules are fixed in the shared 003
contract; tables, locks, events, and public HTTP routes remain separate
decisions.

### Resolved Facade Command Idempotency Boundary

Every state-changing `WorkshopOperationsFacade` command is protected by an
Operations-owned idempotency ledger. The ledger is a technical persistence
record distinct from capacity holds, tickets and immutable operational audit
events. Its logical identity is the pair `facadeAction` and caller-generated
`operationReference`; it retains a semantic request fingerprint so the same
pair with different semantic content is rejected safely. An equivalent retry
returns the original immutable typed business outcome after completion.

The ledger starts as `IN_PROGRESS` and completes as `COMPLETED` with the
terminal business outcome `ACCEPTED` or `REJECTED`. An equivalent duplicate
that finds a live `IN_PROGRESS` record receives the typed `IN_PROGRESS` outcome
and creates no business side effect. Technical or retryable failures roll back
the business transaction without producing a completed outcome, so the caller
may retry with the same operation reference.

The durable claim includes a configurable lease and an incrementing claim
version. An equivalent retry may reclaim only an expired claim. The business
transaction may commit its aggregate changes and complete the ledger only if a
conditional finalisation proves that its claim version remains current. A
reclaimed, stale claimant therefore cannot commit an old side effect. The
initial claim/reclaim is durable so duplicates can observe `IN_PROGRESS`; the
terminal ledger update and all command business effects share one local MySQL
transaction and either commit or roll back together.

The ledger retains the original execution's `correlationReference` separately
from `operationReference`; the one business audit event for that execution uses
the same correlation reference. Equivalent retries create no duplicate business
audit event. Completed records are retained for at least 24 hours before
cleanup; a caller must not retry after that window because duplicate protection
is no longer guaranteed. No raw request payload, Firebase token, PII, QR value,
or audit metadata is used as the ledger or outcome store.

## Technical Context

| Area | Decision |
| --- | --- |
| Runtime | Java 21, Spring Boot 3.4.x, Spring Modulith |
| Persistence | Spring Data JPA, MySQL, append-only Flyway migrations |
| Identity and access | Firebase Authentication for identity; Account Management facade for canonical role, active status and location scope; Operations applies its own action rules with target location and Staff assignment. |
| API | Versioned REST under `/api/v1`, plural kebab-case resources, DTOs and Bean Validation; contract in `contracts/` |
| UI boundary | A separate React + Vite client renders Manager and Staff workflows and consumes REST APIs; the backend does not render application UI or use Thymeleaf/htmx as a UI baseline |
| API security transport | Firebase bearer token on protected API requests. Production origins remain unapproved: use an environment allowlist, deny unspecified origins, do not enable credentialed CORS by default, and do not globally disable CSRF. |
| Time | Store instants; display and validate workshop schedules in `Asia/Ho_Chi_Minh` |
| Testing | JUnit 5, Spring Boot/Security/Modulith tests, plus MySQL-backed Flyway integration verification |
| Facade command idempotency | Operations-owned persisted ledger; local MySQL claim/finalisation transactions; configurable in-progress lease; completed records retained at least 24 hours |

The first rollout configures the existing Vietnam locations and three non-overlapping
default slots: `09:00–12:00`, `13:00–16:00`, and `16:00–19:00`. A normal
workshop uses about 2.5 hours; the remaining 30 minutes is operational buffer.

## Constitution Check

| Rule | Planned response | Result |
| --- | --- | --- |
| Modular ownership | `workshopoperations` exposes an explicit facade; Booking and account/personnel are dependencies only through facades. | Pass |
| No business logic in `common` | Session, ticket, capacity and attendance policies remain in Operations. Shared audit plumbing is technical only. | Pass |
| JPA/Flyway discipline | JPA repositories only; a new append-only migration is selected at implementation time after checking the latest migration. | Pass |
| Security boundary | Firebase verifies identity; server-side capability/scope checks guard every operation. No client-supplied location or actor is trusted. | Pass |
| API quality | Versioned DTO contract, validation, problem responses and OpenAPI are designed before controllers. | Pass |
| Verifiability | Lifecycle, capacity races, authorization, check-in and finalisation are explicit test slices. | Pass |

## Legacy Baseline and Required Rebase

The current implementation is evidence of an older model, not the target
architecture. It currently places `WorkshopSession` and a booking-level
`BookingTicket` in `workshopbooking`; the latter has a one-ticket-per-booking
constraint and cannot represent the agreed individual-ticket model. It also has
an older location/capacity representation and incomplete server authorization.

Implementation must therefore migrate and replace that ownership safely rather
than extending it. In particular, it must not copy the legacy unique-ticket
constraint, silently alter customer tickets, reset production data, or infer
Manager authority from a display role. Existing data needs an operator-approved
mapping/reissue run before production cut-over; the Flyway schema migration must
be reversible operationally by preserving source references and audit history.

The current 003.2 implementation independently creates `WorkshopBranch` /
`workshop_branches`, uses UUID branch references and compares branch IDs with
Account location scope. That implementation contradicts the canonical-location
boundary. T013–T019, including their branch-named controllers, DTOs, tests and
contract assertions, require replacement after this rebase; they are not a
compatibility layer.

## Target Module Structure

```text
ppswbs_backend/src/main/java/.../
├── common/                  # technical kernel only; not a business module
└── features/
    ├── workshopoperations/
    │   ├── controller/      # versioned REST transport and validation
    │   ├── dto/             # REST request/response types only
    │   ├── dao/             # JPA repositories and persistence access
    │   ├── entity/          # internal JPA session, ticket and audit entities
    │   ├── service/         # transactions, use cases and workshop policies
    │   └── facade/          # only supported cross-module entry points and boundary types
    ├── workshopbooking/
    │   ├── controller/
    │   ├── dto/
    │   ├── dao/
    │   ├── entity/
    │   ├── service/         # commercial booking/payment and delivery
    │   └── facade/          # calls Operations facade; performs delivery
    └── accountmanagement/
        └── facade/          # canonical account role/status/location facts
```

The React + Vite client remains outside this backend module tree. It may call
only the versioned REST contract and must not be treated as an authorization
boundary.

## Implementation Sequence

1. **Establish Package master and discovery boundaries.** Model the
   Operations-owned Package lifecycle and prospective-edit invariant before
   Booking integration. Implement active-Manager authorization globally,
   independent of `activeLocationIds`, without adding an Account role/capability.
   Expose public reads only for `PUBLISHED` Packages and protected Package
   create/update/lifecycle commands. Implement the approved semantic Package
   discovery facade and require `packageReference` on reserve before 003.1
   consumes it; Booking may freeze its returned facts but must not mutate the
   master. Implement public customer session discovery only under
   `GET /workshop-packages/{packageId}/sessions` with required `locationId`
   and optional date filters; do not expose the Manager/Staff session search.
2. **Implement approved integration contracts.** Implement
   `WorkshopOperationsFacade` from the shared 003 contract: its capacity
   operations handle holds, commitment confirmation/release and sale
   eligibility; its ticket-projection operation supplies customer-delivery
   summaries after successful commitment.
   Consume the Account Management facade for actor activity, canonical role and
   location scope. Operations applies its own action rule with target location and
   Staff assignment; it does not map a UI role name, Firebase claim or retired
   role code to authority.
   For 003.1, the facade boundary exposes only a sale-eligibility snapshot
   (customer-selectability, sale state, location, scheduled start/end, remaining
   capacity) and atomic group operations: reserve N seats in every requested
   session or none; commit every active hold of a booking or none; and release
   active holds once. It also supplies Booking a delivery-safe ticket
   projection, never attendance, Staff/audit or PII. The facade is synchronous.
   Every booking-critical capacity call joins the caller's one
   local MySQL transaction; events are limited to non-critical post-transition
   handoff. For an authorized Booking customer read, the same delivery-safe
   boundary returns the current entitlement/location/schedule rather than an
   initial snapshot. A successful replacement or verified-refund consequence
   therefore becomes visible to the existing Booking/Magic-Link access without
   Booking copying or owning a session schedule.
   For 003.4, Operations exposes only the approved semantic schedule and
   availability/sale-state snapshots for mapped Basic Ring sessions. `DRAFT`
   produces none; `PUBLISHED` makes the current snapshot available, while
   `IN_PROGRESS`, `ATTENDANCE_FINALIZED`, `CANCELLATION_REQUESTED` and
   `CANCELLED` have closed sale state. At scheduled start the scheduler changes
   `PUBLISHED` to `IN_PROGRESS` and persists the complete closed snapshot in
   the same Operations transaction. `ATTENDANCE_FINALIZED` does not allocate a
   second version when the snapshot is already closed and otherwise unchanged.
   Hold creation, release and expiry notify when availability changes;
   pause/resume, start and session cancellation/closure notify when sale state
   changes. Hold-to-ticket commitment creates no notification when the
   post-state is unchanged. Both snapshot kinds use one per-session source-version sequence;
   a resync returns the one affected session's complete current post-state at
   its existing version. The handoff is a persistent internal Spring Modulith
   event publication recorded in the same Operations transaction; 003.4 consumes
   it at-least-once and owns its separate durable provider delivery. V1 uses no
   message broker. Concrete event/facade types, payload, schema, retry values
   and provider policy remain unapproved.
   Java method names remain implementation details. Public HTTP location routes
   and REST DTO fields are defined in the OpenAPI contract. The approved
   facade-command idempotency boundary uses a
   dedicated ledger and claim-version conditional finalisation; its concrete
   JPA/SQL expression remains an implementation detail constrained by the
   ledger invariants below.
3. **Establish facade-command idempotency before capacity work.** Add the
   Operations-owned ledger through an append-only migration selected after
   inspecting the latest deployed version. Claim or reclaim an equivalent
   command durably, reject mismatched fingerprints, return `IN_PROGRESS` for a
   live equivalent claim, and finalise only under the current claim version in
   the same local transaction as business effects. Retain the original
   correlation reference separately from audit metadata; do not reuse audit,
   hold or ticket persistence for deduplication.
4. **Create the operational aggregate.** Model canonical workshop location, reusable location slot,
   session, optional Staff roster, capacity hold and individual attendance
   ticket with session entitlements. Enforce
   location/date/slot uniqueness, positive capacity and the no-overlap slot rule.
5. **Implement session administration.** Support single and bounded bulk
   session generation (at most 90 future days), duplicate reporting, slot
   activation/deactivation, lifecycle transitions, schedule change warnings and
   role/location authorization, check-in opening at `checkInOpensAt` and
   scheduled lifecycle start at the session's scheduled start time; retain
   manual transition only for early start/recovery.
6. **Make capacity transactional.** Reserve only against `PUBLISHED` sessions;
   retain pre-pause unexpired holds; confirm or release holds atomically.
   Protect concurrent reservations from oversell using an approved
   implementation mechanism; the particular lock or concurrency technique is
   deliberately deferred.
   A multi-session request is one all-or-nothing business result. A commit with
   a missing/expired hold commits nothing; an expiry/customer-release retry is
   idempotent and cannot return capacity twice. Each booking-critical capacity
   command joins the caller's one local MySQL transaction, while gateway calls
   remain outside that transaction.
   After a completed local transaction alters OTA-observable schedule,
   sellable availability or sale state, advance that session's unified source
   version once and make its complete post-state available to 003.4. The
   notification is persisted as an internal Spring Modulith event in that same
   transaction and is at-least-once: 003.4 applies only a newer source
   version/change identity and requests an authoritative resync of the one
   affected session on a gap or uncertain order. Resync does not advance the
   source version. A 003.4 sync failure affects only that feature's OTA mapping
   and reconciliation; it never changes an Operations session or direct booking.
7. **Implement tickets and check-in.** Generate individual opaque QR references
   and unique ticket codes. Validate the scanner's current session/location,
   ticket entitlement, ticket validity, that ticket/session pair's attendance
   state, opening time and actor scope as one result. QR is primary, exact
   ticket code is normal fallback, booking code is lookup-only. Overrides remain
   in a separate permissioned, reasoned and audited path.
   A repeated normal scan returns its existing check-in result as `200 OK` with
   `ALREADY_CHECKED_IN` and `checkedInAt`; it does not create another action.
8. **Close attendance correctly.** Let an active Staff member in the session
   location or any active Manager finalise the whole session after its actual
   end; convert still-unchecked valid tickets to `NO_SHOW`. Only a Manager may
   perform a post-finalisation correction, with mandatory audit evidence.
9. **Handoff cancellation and delivery.** On cancellation request, stop normal
   operation and create the audited case consumed by feature 003.3. Booking
   obtains only delivery-safe ticket views from Operations; it never owns or
   mutates attendance state.
10. **Migrate and verify.** Add a new Flyway migration after inspecting the
   deployed migration sequence, backfill only through an approved runbook, and
   exercise the flows in `quickstart.md` against MySQL.

## Validation Strategy

- Contract-test Package create/read/update/publish/retire, public exclusion of
  `DRAFT` and `RETIRED` records, global active-Manager authorization regardless
  of location assignments,
  authorization, participant-bound validation, generic required-session rules
  and immutable/prospective Booking snapshot behaviour.
- Cross-module-test that 003.1 consumes only the approved Package-discovery
  facade and cannot read or mutate Operations Package persistence. Contract-test
  public Package session discovery with required location scope, optional date
  filters, UUID session references, public-safe schedule/state and exact
  non-guaranteed remaining capacity; prove the Manager/Staff search remains
  protected.
- Unit-test lifecycle guards, slot overlap, capacity arithmetic, ticket state
  rules and audit-reason requirements.
- Integration-test JPA constraints and Flyway migration on MySQL.
- Concurrently attempt capacity holds and duplicate check-ins; exactly one
  transaction must succeed where capacity or a ticket is contested.
- MySQL-test facade-command idempotency: sequential equivalent retry returns the
  saved outcome; mismatched fingerprint is rejected; concurrent equivalent
  command observes `IN_PROGRESS`; an expired claim can be reclaimed; and a
  stale claimant cannot complete the ledger or commit a business side effect.
- MySQL-test retention cleanup only for eligible completed ledger records after
  the configured window; prove that no raw request payload, token, QR value or
  PII is stored in the ledger.
- Security-test active/inactive Staff, Staff location boundaries, global Manager
  authority, scheduled auto-start, normal check-in and reasoned override.
- Contract-test REST responses and problem codes against
  `contracts/workshop-operations.openapi.yaml`.
- Modulith-test that Operations does not depend on Booking internals and that
  Booking uses only the Operations facade.
- Verify the OTA-observable boundary using session publication, schedule change,
  active-hold creation/release/expiry, sale pause/resume and session
  cancellation/closure; verify `DRAFT` and internal-only changes produce no
  snapshot and a hold-to-ticket commitment produces none when the exposed
  post-state is unchanged. Verify duplicate/older notifications are harmless,
  a gap triggers one-session authoritative resync at the existing version, and
  003.4 mapping suspension never changes Operations capacity or direct-Mland
  sale state.

## Complexity Tracking

No Constitution exception is requested. The ownership move is necessary to
avoid a Booking-to-Operations-to-Booking cycle and to keep capacity and
attendance decisions in one transactional module.
