# Implementation Plan: Workshop Operations

> **OTA đã bị loại 2026-11-25** (chưa có tài khoản/sandbox đối tác): feature 003.4 và mọi hành vi OTA không còn hiệu lực. Phần còn nhắc OTA/003.4 bên dưới (Clarifications, câu "superseded") chỉ là lịch sử. Xem [quyết định loại OTA](../ota-removal-2026-11-25.md).

**Date created**: 2026-10-24
**Date updated**: 2026-11-17
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Branch**: `003.2-workshop-operations` | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from
`ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/spec.md`

## Authorization and scheduled-start rebase

V1 uses canonical role/status/location data from the public Account facade.
Staff authority is limited by `session.locationId ∈ activeLocationIds`; a
session assignment is operational metadata, not an authorization prerequisite.
*(Superseded 2026-11-06, spec "Staff session-assignment access": Staff also
needs an active assignment to the session, checked at command time.)*
An active Manager has global Workshop Operations authority and there is no
operational-lead model in the target contract. The capability key
`workshop.checkin.override` and `AccountFacade.can(...)` are not used.

At `checkInOpensAt`, check-in becomes available while an eligible session
remains `PUBLISHED` or `SALES_PAUSED` *(extended 2026-11-15, spec Clarifications
Q2; the scheduler also starts a `SALES_PAUSED` session)*. At the session's scheduled start time, a recoverable
scheduler transitions it to `IN_PROGRESS` and records a system audit event. The transition must
be idempotent, timezone-aware and safe across restart/retry. A manual state
transition remains only for explicit early start or recovery.

**Status**: Approved 2026-11-15 by Hồ Hoàng Quốc (owner Feature 003) for the
2026-11-15 content (spec Clarifications Session 2026-11-15, Q1 update).
*(Superseded 2026-11-15: "Approved — updated 2026-11-14 and re-approved the same
day".)* Tasks Phase 1–13 and
Phase 14 T090–T113 are implemented (tasks.md evidence, including T092, T096,
T097); Phase 15 T114–T123 (spec Clarifications 2026-11-15) and Phase 16 T124–T125 (convergence), together T114–T125, are open; Phase 17
T126–T142 (spec "Checklist follow-up (payment-error-access.md), answered
2026-11-15") are open, mechanisms in "Checklist follow-up 2026-11-15" below.
*(Superseded 2026-11-14: "Design draft — internal capacity semantics and
facade-command idempotency are clarified; implementation must rebase the
affected task before changing persistence or code.")*

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
concrete dated-session attachment. It defines non-negative
`minimumSameDaySlotGap` and `requiredFoundryWorkingDaysBetweenSessions`: the confirmed catalogue is in
[catalogue.md](./catalogue.md). Signature Brass, Signature Silver 925 and both
Silver Clay offers select one session; Wax Casting selects two. Wax Casting
requires at least two Monday–Friday local dates strictly between the selected
sessions, skipping Saturday and Sunday. This date rule is separate from the
same-local-date slot-gap field and must be persisted, snapshotted and enforced
by Operations before Wax Casting can be published.

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

*(Added 2026-11-15, owner decision Hồ Hoàng Quốc, spec FR-012a/FR-012c, CHK018
and CHK024.)* The facts are read at command time and never cached. If the
Account facade throws or times out, `WorkshopOperationsAuthorizationService`
converts the failure into a dependency-unavailable exception answered `503
DEPENDENCY_UNAVAILABLE` before any business write (T128). Only `MANAGER` is a
global workshop actor: `isGlobalWorkshopActor` no longer accepts `ADMIN`, and
location creation (`WorkshopLocationService`) requires an active Manager
instead of the ADMIN-only `requireAllBranchActor` (T129).

### Resolved Ticket/Session Boundary

One participant has one persistent QR ticket, including for a multi-session
package such as Wax Casting. Its
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

*(Superseded 2026-11-14/15 by FR-032, FR-034 and T097: booking-wide settlement readiness and the complete required-session replacement below were removed; readiness and attendance outcome are per ticket (`getTicketAttendanceOutcome`) and replacement is per ticket (`replaceTicketSessions`, T094). The text below is kept as history.)*

For a booking/package context supplied by 003.3, Operations alone determines
settlement readiness. It returns only the conclusion: ready is true only when
every required session is `ATTENDANCE_FINALIZED`; any unfinished or cancelled
required session yields false. It exposes no attendance, ticket, Staff, audit or
PII detail, and Settlement must not infer readiness from schedule time or
per-session records.

For the direct-booking open folio (FR-014, D2/D3, 2026-11-05), Operations also
exposes `BookingAttendanceOutcomeQuery` (`terminal`, `ATTENDED`/`NO_SHOW`/
`PARTIAL`) and publishes the reference-only `BookingAttendanceFinalizedEvent`
once per booking when the 24-hour correction window after the last required
session's finalization closes. Readiness keeps gating only finalization;
Settlement decides folio closure from the outcome query (003.2 T088).
*(Superseded 2026-11-14, BE-DESIGN-0004: the booking-wide query, event and
outbox are removed with T092 and replaced by the per-ticket
`getTicketAttendanceOutcome` and `TicketAttendanceFinalizedEvent`; see
"Per-ticket operations (Phase 14)".)*

For a Manager-mediated customer change, 003.3 supplies the complete resulting
required-session set. Operations accepts a source only while future and outside
`IN_PROGRESS`, `ATTENDANCE_FINALIZED`, `CANCELLATION_REQUESTED` and `CANCELLED`.
Every target must be future, `PUBLISHED`, customer-selectable and have capacity
for one seat per selected ticket *(FR-034; was "the full participant count")*. Operations updates all affected entitlements and
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
is no longer guaranteed. *(Added 2026-11-15, owner decision Hồ Hoàng Quốc, spec
FR-010kf, CHK016:)* a retry after cleanup is a new command and the business
state checks (ticket no longer active, hold no longer active) stop a second
effect; no tombstone or longer retention is added; T134 tests it for
`cancelTicket`, `commitBookingHolds` and `releaseBookingHolds`. No raw request payload, Firebase token, PII, QR value,
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
| Security boundary | Firebase verifies identity; server-side role/scope checks guard every operation *(2026-11-15: was "capability/scope checks"; there is no capability key)*. No client-supplied location or actor is trusted. | Pass |
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
   initial snapshot. A successful replacement or ticket cancellation
   *(replaces "verified-refund consequence", spec Clarifications Q5, 2026-11-15)*
   therefore becomes visible to the existing Booking/Magic-Link access without
   Booking copying or owning a session schedule.
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

Step 8 annotation *(superseded 2026-11-06)*: a finalizing Staff member also
needs an active assignment to the session.

## Per-ticket operations (Phase 14)

**Added 2026-11-14** (spec FR-030–FR-036, decision 2026-11-12; T092 decision
2026-11-14). Names below were confirmed by read-only grep of
`ppswbs_backend/src` on 2026-11-14; the evidence for each task is in tasks.md.
Updated 2026-11-14; owner approval pending *(superseded 2026-11-15: "re-approved
the same day by Hồ Hoàng Quốc")*.

| Task | Status | Mechanism |
| --- | --- | --- |
| T090 group capacity (FR-030) | Implemented | `WorkshopOperationsFacade.reserveGroupCapacity` → `GroupCapacityService`. Locks every involved session once through `findByReferenceInWithLock` in sorted reference order, then counts holds and seats with locking reads (the caller may hold an older REPEATABLE READ snapshot). All lines or none, demand summed per session, one hold per line per session, 12-minute hold, failing line indexes on refusal, idempotent replay. Commit at confirmation is done by 003.1 `GroupConfirmationService`, which calls `commitBookingHolds` per line reference; release after confirmation is per ticket (T091). |
| T091 per-ticket cancellation (FR-031) | Implemented | `cancelTicket` → `TicketCancellationService`. V131 `ticket_entitlement_releases` (unique `ticket_id`, `session_id`) keeps one row per released future seat; the entitlement row is removed, the line's confirmed hold is decremented, checked-in/no-show sessions are untouched. QR (CHK010, 2026-11-15): the ticket's validity becomes `CANCELLED` and its QR is kept (no new generation, no `ticket_qr_generations` row); check-in and override answer `409 TICKET_CONFLICT`; `TICKET_QR_REVOKED` stays for recipient change. |
| T093 QR reissue (FR-033) | Implemented | `reissueTicketQr` → `TicketQrReissueService`. V126 adds `attendance_tickets.qr_generation` and `ticket_qr_generations` (revoked QR values, reason `RECIPIENT_CHANGED`); check-in and override refuse a revoked QR with `TICKET_QR_REVOKED`; refused after any check-in. |
| T094 per-ticket replacement (FR-034) | Implemented | `replaceTicketSessions` → `TicketSessionReplacementService`. V131 `ticket_session_replacements` (type `CUSTOMER_ADVANCE`, `CUSTOMER_LATE_ARRIVAL`, `MLAND`; `MLAND` never consumes quota). Lock order: every source and target session in sorted reference order, then each ticket, then its entitlements; all or none across the selected tickets. |
| T095 reschedule quota (FR-035) | Implemented | V131 adds `workshop_sessions.reschedule_quota_override` and `reschedule_quota_used`; effective quota is the override or `max(1, capacity / 4)`. Route `PUT /api/v1/workshop-sessions/{sessionId}/reschedule-quota`. |
| T092 per-ticket attendance event (FR-032) | Done 2026-11-14 | See below and tasks.md. |
| T096 MySQL evidence | Done | See tasks.md. |
| T097 booking-wide cleanup | Done 2026-11-15 | See below. |

**T092 decision (owner Hồ Hoàng Quốc, 2026-11-14, ERROR-LOG BE-DESIGN-0004):**

1. Publish `TicketAttendanceFinalizedEvent(eventId, ticketReference,
   windowClosedAt)` once per ticket when its outcome becomes terminal. It is
   reference-only: no PII and no outcome; the consumer reads
   `getTicketAttendanceOutcome`.
2. The key is `attendance_tickets.reference`; 003.3 resolves it through 003.1
   `findTicketBookingByOperationsReference`.
3. A new append-only migration creates
   `workshop_ticket_attendance_finalization_outbox`, unique (`ticket_reference`,
   `event_type`). Same scheduler, conditional claim, lease and at-least-once
   delivery with consumer deduplication by `eventId` as BE-DESIGN-0001.
4. In the same change, remove `BookingAttendanceFinalizedEvent`, its outbox
   `workshop_booking_attendance_finalization_outbox` (created by
   `V107__booking_attendance_finalization_outbox.sql`; dropped only by a NEW
   migration, V107 is not edited) and `BookingAttendanceOutcomeQuery`
   (with `getBookingAttendanceOutcome`). 003.3 T105 replaces the consumer
   (`BookingAttendanceFinalizedEventConsumer` today).
5. No separate readiness query. `getTicketAttendanceOutcome` already exists and
   returns `BookingAttendanceOutcome(terminal, outcome)` today; T092 adds
   `windowClosesAt` and decides whether to rename the return type (the name is
   not decided in this plan).

**Decided 2026-11-14 (Hồ Hoàng Quốc):** the 24-hour correction window is
anchored **per ticket** — 24 hours after the latest finalization among that
ticket's own sessions, which is what `BookingAttendanceOutcomeService.evaluateTicket`
already measures. The correction deadline follows the same anchor, so no
correction can land after that ticket's terminal event (spec FR-032).

**Booking-wide types still present:** `BookingSettlementReadinessQuery`,
`BookingCancellationEligibilityQuery`, `ReplaceRequiredSessionsCommand` and
`CancelBookingCommand` stay on `WorkshopOperationsFacade` until 003.3 T106
leaves no caller; their removal is T097.
*(Updated 2026-11-15: T097 is done. It removed `BookingCancellationEligibilityQuery`,
`ReplaceRequiredSessionsCommand`, `CancelBookingCommand` and the booking-wide
settlement/attendance facade methods. Only `BookingSettlementReadinessQuery`
remains, kept for `hasBookingServiceStarted` and `isStaffAssignedToBooking`.)*

## Checklist follow-up 2026-11-15 (Phase 17)

**Source:** spec Clarifications "Checklist follow-up (payment-error-access.md),
answered 2026-11-15" (owner decision Hồ Hoàng Quốc, 2026-11-15), the spec
"Error catalogue" and SC-005–SC-008. Class names below were read from
`ppswbs_backend/src` on 2026-11-15; new class names are proposed and final
names are chosen at implementation.

| Task | Decision | Mechanism |
| --- | --- | --- |
| T126 | CHK014 | `CheckInService` throws a new `WorkshopCheckInNotOpenException(checkInOpensAt)` instead of `WorkshopLifecycleException` when `now < checkInOpensAt`; `WorkshopOperationsExceptionHandler` answers `409 CHECK_IN_NOT_OPEN` and adds `checkInOpensAt` to `ProblemDetailResponse`. All other codes stay as in the catalogue. |
| T127 | CHK020 | The resend throttle in `TicketResendService` already counts a fixed window from the first request; `WorkshopRateLimitException` gains `retryAfterSeconds` = ceil(windowStart + 15 min − now), min 1; the handler sets the `Retry-After` header on `429 REDELIVERY_THROTTLED`. |
| T128 | CHK018 | Account facade failures are wrapped once in `WorkshopOperationsAuthorizationService` (proposed `WorkshopDependencyUnavailableException`) → `503 DEPENDENCY_UNAVAILABLE`; no fallback, no cache. 401 stays only for token failures (CHK028; current behaviour, verified by test). |
| T129 | CHK024 | Remove `ADMIN` from `isGlobalWorkshopActor`; replace `requireAllBranchActor`/`isAllLocationActor`/`isAllBranchActor` uses in `WorkshopLocationService`, `WorkshopLifecycleService`, `WorkshopSessionService` with the Manager check; the ADMIN branches of `WorkshopResponsibilityService` belong to the operational-lead code removed by T124. |
| T130 | CHK029 | `WorkshopResponsibilityService` assignment path: ineligible assignee → proposed `WorkshopStaffNotEligibleException(reason)` → `409 STAFF_NOT_ELIGIBLE` + `reason`; session state outside `DRAFT`/`PUBLISHED`/`SALES_PAUSED`/`IN_PROGRESS` → `409 INVALID_STATE`; duplicate active assignment stays a no-op. |
| T131 | CHK032 | One proposed `OperationsDeniedAttemptRecorder` writes the denied `OperationsAuditEvent` (data-model "Denied-attempt audit") in a `REQUIRES_NEW` transaction, called from the refusal points of the listed actions (or from the exception handler for routes tagged as sensitive); refused normal check-ins and reads only log. |
| T132 | CHK004 | `CapacityHoldService` commit guard (today it refuses only `CANCELLED`/`ATTENDANCE_FINALIZED`) also refuses `CANCELLATION_REQUESTED` with `REJECTED INVALID_STATE`; no hold is touched, so expiry releases it as usual. |
| T133 | CHK009 | A proposed `UnresolvedCancellationAlertScheduler` reuses the existing scheduling mechanism of `WorkshopSessionStartScheduler`: Spring `@Scheduled(fixedDelayString = …)` under the `workshop.operations.scheduler.*` properties, `Instant.now()` as time source, UTC instants compared with `scheduled_end_at + 24h`. It selects `CANCELLATION_REQUESTED` sessions past that instant without a `CANCELLATION_UNRESOLVED_ALERT` audit event, reads `AccountFacade.listActiveManagerEmails()` (already an allowed dependency) and hands the emails to delivery; with an empty list it writes only a WARN log and no marker. *(Decided 2026-11-16, F06: the `SENT` audit event is written only after `NotificationFacade` returns success; disabled or failed sending gives a WARN log and no marker so the next scan retries; `NO_ACTIVE_MANAGER` is withdrawn.)* Each session is processed in its own transaction (same pattern as T125). **Channel decided (approved by Hồ Hoàng Quốc 2026-11-15; supersedes the former Open status of this row):** `workshopoperations` gains the dependency `notificationmanagement :: facade` (T133 adds it to `allowedDependencies` in `package-info.java`) and sends the alert through `NotificationFacade` with a new alert email template in the notification module. Recipients: `AccountFacade.listActiveManagerEmails()`; none active means WARN and no email and no marker (F06, 2026-11-16). The dependency and template approved by Hồ Hoàng Quốc 2026-11-16 (F06-email): the template `WORKSHOP_CANCELLATION_UNRESOLVED_ALERT` is added by a Flyway migration (definition in `notification_template_definition` + first version in `notification_template_version`, status per the module convention) and the 007 module owner is informed by the 003 owner after the change (convention, not a technical gate); release is not blocked by 007. One `NotificationFacade` intent per active Manager (`businessEventReference` `<sessionReference>:CANCELLATION_UNRESOLVED:<managerAccountId>`); `SENT` audit only when all intents were accepted. T133 acceptance adds the migration, a 007 preview-API render with sample data, and an allowlist test. The names `CANCELLATION_UNRESOLVED_ALERT`, `SENT`, `NO_ACTIVE_MANAGER` and the one-alert-per-session rule are approved by Hồ Hoàng Quốc 2026-11-15. |
| T134 | CHK016 | Test only: delete the ledger row (simulating `FacadeCommandIdempotencyCleanupService`) and replay the same command. |
| T135–T137 | FR-012c | `PATCH /workshop-locations/{locationId}` (Manager only, added to V1 by Hồ Hoàng Quốc 2026-11-15): `WorkshopLocationController` + `WorkshopLocationService.updateLocation` change `display_name`, `address_snapshot` and `active` of the existing `WorkshopLocation`; `code` immutable; `Idempotency-Key` through the existing REST idempotency executor; one audit event; frontend edit action on the administration page. Deactivation rule is confirmed by the owner 2026-11-15 (`409 INVALID_STATE` with a message while a not-yet-ended session uses the location; rename, address change and reactivation always allowed). No migration. |
| T139 | CHK020 | `POST /workshop-sessions/{sessionId}/ticket-resends` answers `409 INVALID_STATE` with `reason` for two more cases (owner decision Hồ Hoàng Quốc 2026-11-15): no `ACTIVE` unused ticket in the booking (`NO_ELIGIBLE_TICKET`) and Booking refusing the redelivery (`REDELIVERY_REJECTED_BY_BOOKING`); never 400/404, so a booking's existence is not revealed. The `reason` names are final (owner 2026-11-16, option A). Today the code throws `WorkshopValidationException` (`400 VALIDATION_ERROR`) for both (`TicketResendService` ~line 58, `WorkshopCheckInController` ~line 71) and saves the throttle counter before the Booking call, so a Booking refusal still counts; T139 changes both to `409 INVALID_STATE` with the reasons and moves the increment so only a request handed to Booking and accepted counts. |
| T140 | CHK015 | Test and OpenAPI-description task, no new mechanism (spec FR-010kh): the five operational routes take no `Idempotency-Key`; a repeat is decided by state under the existing session/ticket lock (`409 INVALID_STATE`; same-value correction `200` without a second audit; resend limited only by FR-003c). |
| T141 | CHK025 | Test task (spec FR-007c): every Manager-only action in the FR-007c list writes its success audit event with actor, time, source and correlation ID; add the missing ones if a test shows a gap (slot change, session create, publish/pause, Staff assign/revoke). |
| T142 | FR-007a, FR-013 | Test-only: the reason catalogue is enforced wherever a reason is required (unknown code `400 VALIDATION_ERROR`; `OTHER_REQUIRES_NOTE` without note or evidence reference `400 VALIDATION_ERROR`) and add-on visibility (Manager reads `DRAFT`/`RETIRED` on the administration route, the public route returns only `ACTIVE`, Staff/Admin 403). |
| T138 | decision D | Documentation/QA check only, no code: find out whether FE-001, Loyalty `ProductBranchPicker` or Product management let an Admin call `GET /workshop-locations`; see "Open items" below. |

**Approved contract names (approved by Hồ Hoàng Quốc 2026-11-15; replaces "proposed" for these names only).** *(Re-approved as-is 2026-11-16; 401 and 503 are not audited. The resend `409 INVALID_STATE` reasons `NO_ELIGIBLE_TICKET` and `REDELIVERY_REJECTED_BY_BOOKING` are final too, option A 2026-11-16.)* The `reason` values of `STAFF_NOT_ELIGIBLE` (`ACCOUNT_NOT_FOUND`, `NOT_STAFF`, `NOT_ACTIVE`, `LOCATION_NOT_IN_SCOPE`), the audit outcome `DENIED`, the audit action `CANCELLATION_UNRESOLVED_ALERT` with outcome `SENT` (`NO_ACTIVE_MANAGER` withdrawn 2026-11-16), and the rule of one alert per session. Java class names such as `WorkshopStaffNotEligibleException` or `OperationsDeniedAttemptRecorder` stay implementation choices.

**Open items.**

- Deactivating a location while a not-yet-ended session uses it: `409 INVALID_STATE` (T135), confirmed by the owner 2026-11-15 (closed).
- Admin and `GET /workshop-locations`: **decided 2026-11-16 by Hồ Hoàng Quốc, option A, conditional on the T138 result** (supersedes "stays `403`, return to the owner"). T138 first checks whether FE-001 (the Admin screen assigning `activeLocationIds`) calls this route. If yes: Admin gets a separate read-only grant to the location list only (reference data, explicitly NOT a business permission; no create/edit/assignment/audit rights), documented in FR-012c, the 403 row of the error catalogue and the OpenAPI role text; T129/T138 tests expect Admin `200` on `GET /workshop-locations` and `403` on every other 003.2 route. If FE-001 does not call it, Admin stays `403`.
  **T138 result (2026-11-16, read-only code check; responsible member Hồ Hoàng Quốc): FE-001 does NOT call `GET /workshop-locations`.** `InternalAccountCreatePage.tsx` uses a hard-coded `AVAILABLE_LOCATIONS` list and `AdminAccountDetailPage.tsx` shows names from the same list; the only account-side calls are `/api/v1/admin/accounts/**`. The other callers (`catalogueApi.listBranches`, Loyalty `loyaltyApi.listWorkshopBranches`, `ProductBranchPicker`, the 003.2 `operationsAdminApi.listLocations`) belong to Product, Loyalty and Operations screens, and the backend denies Admin there (`CatalogueAuthorization`: Admin holds no catalogue capability; `LoyaltyAuthorization`: only Manager manages programmes). **Outcome: Admin stays `403` on `GET /workshop-locations`; T129 removes Admin everywhere; no spec/OpenAPI change needed.** Follow-up outside 003.2: FE-001 keeps a hard-coded location list (a stale-data risk, not a permission issue).
- No location edit route: resolved at document level by T135/T137 (`PATCH /workshop-locations/{locationId}`, Manager edits, Admin `403`); confirmed when T135 is implemented (2026-11-16).
- Self-chosen names approved as-is 2026-11-16 (Hồ Hoàng Quốc): `STAFF_NOT_ELIGIBLE` reasons, audit outcome `DENIED`, audit action `CANCELLATION_UNRESOLVED_ALERT` (outcome `SENT` only), 401/503 not audited, alert sent once per session (closed). The two resend reasons `NO_ELIGIBLE_TICKET` and `REDELIVERY_REJECTED_BY_BOOKING` (`409 INVALID_STATE`) are also final (option A, 2026-11-16, after comparing with the code); T139 aligns the code.
- T048 contract-drift test may stay red until T126, T128, T129 and T139 are done (owner, 2026-11-16).
- Alert email template content and the `notificationmanagement :: facade` dependency: resolved 2026-11-16, approved by Hồ Hoàng Quốc (F06-email); the template is added by a Flyway migration in T133 and the 007 module owner is only informed after the change (convention, not a technical gate). *Amended 2026-11-16 (T133, owner option c): `unresolvedTicketCount` is not in the template or allowlist (no count facade in 003.3); `businessEventReference` is `<sessionReference>:CANCELLATION_UNRESOLVED` (the Account facade returns emails only). Metric `workshop.session.auto_start.late` (T119) is scrapeable since 2026-11-16 (owner request): `spring-boot-starter-actuator` + `micrometer-registry-prometheus`, only `/actuator/prometheus` exposed, on management port `MANAGEMENT_SERVER_PORT` (default 8081) bound to `MANAGEMENT_SERVER_ADDRESS` (default 127.0.0.1); `ManagementSecurityConfiguration` permits actuator endpoints without a bearer, the public API port does not serve them. Test: `WorkshopMetricsScrapeIT`. Deployment must keep the management port off the public network.*

**Mechanisms for Phase 15 and 16 (T114–T125).** Read-only verification tasks first read the code and become a fix only if it differs from the spec.

| Task | Mechanism |
| --- | --- |
| T114 | `CheckInService` accepts `PUBLISHED`, `SALES_PAUSED`, `IN_PROGRESS`; `WorkshopSessionStartScheduler` also starts `SALES_PAUSED` sessions with a system audit event; the snapshot builder emits `saleState=SALES_PAUSED` for a paused session (not `CLOSED`); the scheduler and the manual `PUBLISHED`/`SALES_PAUSED` to `IN_PROGRESS` transition both publish the complete closed snapshot and bump the version exactly once (add the manual `SALES_PAUSED` case and a unit test of the enum mapping). *(F04 reopened 2026-11-16, option B.)* |
| T115 | Session response gains the `missingStaff` flag (true from `checkInOpensAt` while no Staff assignment is active); `WorkshopResponsibilityService` revoke refuses the last active assignment after `checkInOpensAt` with `409 LAST_STAFF_ASSIGNMENT_CONFIRMATION_REQUIRED` unless the request carries `confirmLastAssignmentRemoval: true` (query parameter on the DELETE route); both the refusal and the confirmed removal are audited. Names accepted 2026-11-15 (owner Hồ Hoàng Quốc). Scope (CHK030): the flag and the guard apply only while the session is `PUBLISHED`, `SALES_PAUSED` or `IN_PROGRESS`; in other states `missingStaff` is false and no confirmation is needed. |
| T116 | New read-only audited Manager route `GET /workshop-booking-lookups/{bookingCode}` (name proposed) for a booking's cross-session ticket set: `ticketCode`, `sessionId`, `locationId`, `validityState`, `attendanceState`, no contact data; Manager only; unknown code `404 RESOURCE_NOT_FOUND` (FR-003b, CHK026). Route and schemas are already in the OpenAPI contract. |
| T117 | Verify `cancelTicket` revokes at call time with no refund dependency (FR-031); remove refund-trigger wording in 003.2. CHK010: ticket `CANCELLED`, QR kept, scan → `409 TICKET_CONFLICT`. |
| T118 | Verify `GroupCapacityService` checks each line against its own Package bounds; add the mixed-package MySQL test. |
| T119 | `WorkshopSessionStartScheduler` logs at ERROR and increments a metric (no email; F14, 2026-11-16) for a start later than 5 minutes after `scheduledStartAt` and still catches up on restart. |
| T120 | Read-only check that Loyalty `BRANCH` equals `workshop_location.id`; defect task in Loyalty/Booking if not. |
| T121 | Verify and test the Staff location-plus-assignment rule on every Staff operation (`WorkshopOperationsAuthorizationService`). |
| T122 | Verify that `completeCancellation` (facade method `completeCancellationHandoff`) moves `CANCELLATION_REQUESTED` to `CANCELLED` only when every ticket is resolved, rejects an early call with `CANCELLATION_HANDOFF_UNRESOLVED` (no state change, repeatable; decided 2026-11-16), is idempotent by `operationReference`, and that `CANCELLED` rejects check-in. |
| T123 | *(Removed 2026-11-25 with OTA.)* |
| T124 | Delete the operational-lead code and the "responsible operator" guard; T115 rule replaces it. |
| T125 | Per-session `REQUIRES_NEW` transaction in `WorkshopSessionStartScheduler.transitionDueSessions` through a separate bean. |

003.3 T167 (003.3 tasks.md) adds the caller of `completeCancellation`; 003.2
T122 then verifies the transition end to end.

## Package availability per location (Phase 18, plan 2026-11-17)

**Spec:** "Package availability per location", FR-037–FR-046, SC-009–SC-012, US6
(owner answers 2026-11-17). Backend and contract scope partially approved the
same day in `approval.md` (PARTIALLY APPROVED); UI, 003.1, 003.3 stay excluded there. Tasks: Phase 18 (T144–T162) in `tasks.md`.

**Design decisions (proposed):**

1. **One new Operations-owned record**, `WorkshopPackageLocationAvailability`
   (see data-model). A missing row means `INACTIVE`, so a new Package or location
   needs no insert and the "new pairs start `INACTIVE`" rule costs nothing. The
   matrix read synthesizes missing cells.
2. **One check, one place.** A single Operations service answers "is Package P
   sellable at location L" (`PUBLISHED` + pair `ACTIVE` + location active). The
   same answer feeds public discovery, the atomic reserve (FR-039), the ticket
   change validation (FR-042) and the change-option listing, so the four paths
   cannot disagree.
3. **Reserve and change rules.** Reserve and `replaceTicketSessions` check the
   pair for every target session inside their existing all-or-none transaction.
   No extra lock: a reserve that commits before a deactivation is an ordinary
   earlier sale (FR-040); one that starts after is refused. No partial result.
4. **Toggle command** `PUT` per cell, Manager only, `Idempotency-Key` required,
   audited once per real change; same state is a no-op without audit (FR-038).
   Optimistic `version` on the row for two Managers editing the same cell.
5. **`sameLocationRequired` unchanged in the Package record** (FR-041). Cross-
   location is allowed by availability, not by clearing the flag; the existing
   candidate filter that pins a change to the old location is replaced by an
   availability filter (003.3 impact below).
6. **Staff scope of a change stays with 003.3**, which knows the actor:
   `replaceTicketSessions` has no actor. 003.3 applies the Staff scope and assignment rule and needs no Manager when
   the target location differs (FR-042, amended 2026-11-17, owner Hồ Hoàng Quốc);
   003.2 enforces availability, one-location and quota.
7. **Location name** is added to the customer session read, the Manager/Staff
   session list and the change options (FR-044). `locationId` stays.

**Boundary and Modulith:** all new code stays in `workshopoperations`; 003.3 and
003.1 reach it only through `WorkshopOperationsFacade` (new read
`activeLocationsForPackage(packageReference)` and the new rejection reason). No
new module dependency, no cycle. `ApplicationModules.verify()` must still pass.

**Cross-feature impact (to be tasked in each feature, not edited here):**

- **003.1:** the session-discovery client and reserve error mapping handle
  `PACKAGE_NOT_AVAILABLE_AT_LOCATION`; the location picker lists only locations
  returned by `GET /workshop-packages/{packageId}/locations`; the session list
  shows the location name. Cart snapshots are unchanged because price and rules
  are identical across locations.
- **003.1 (decided 2026-11-17):** `WorkshopBookingFacadeImpl.getTicketFolioContext`
  must derive `locationReference` and `sessionReferences` from the ticket's
  current Operations sessions, not from the line copy (`BookingGroupLineSession`,
  not updated by a change, shared by every ticket of the line). 003.3 authorization,
  folio and collection branch read this value unchanged.
- **003.3:** `TicketChangeOptionsService.candidatesFor` stops filtering by the
  old location and filters by the active-locations read; options carry the
  location name; `TicketChangeService` needs no Manager when the
  target location differs (amended 2026-11-17); a cross-location move updates the ticket's location
  of record, re-attributes the folio to the new location and lists the old-
  location add-ons to the Manager (FR-045). The change-option wording of
  003.3 FR-044/FR-044a needs a matching edit.

**Resolved with the owner / verified 2026-11-17:**

- **Mland replacement (spec FR-046, owner decision):**
  `findEquivalentReplacementSessions` drops "same branch" and uses the sellable
  locations of the Package; candidates carry `locationId` and `locationName`.
  003.3 stores the list as before; the chosen session moves the ticket under
  FR-045.
- **Audit action name (verified in code):** `operations_audit_events.action` is a
  free `varchar(80)`, not an enum; package events use `WORKSHOP_PACKAGE_CREATED`,
  `_UPDATED` and `_PUBLISHED`/`_RETIRED` (`WorkshopPackageService.recordAudit`).
  Use **`WORKSHOP_PACKAGE_AVAILABILITY_CHANGED`**, `session_id` null, `outcome`
  `SUCCESS`, and `metadata` JSON `{packageId, locationId, from, to}`. A refused
  attempt by Staff/Admin follows the existing denied-attempt audit rule.
- **Seed names (verified in `V161__add_data.sql`):** the Packages are
  `Signature Workshop - Silver 925`, `Signature Workshop - Brass`,
  `Silver Clay Workshop - 5g`, `Silver Clay Workshop - 7g`, `Wax Casting
  Workshop`; locations `HN-HOANKIEM` (id 1), `HN-TAYHO` (id 2), `DN-HAICHAU`
  (id 3, inactive). The latest migration in the repo is `V177`; choose the next
  free version when implementing, after fetching `origin/develop`. Not checked
  against a live database (no credentials used).

**Frontend:** a Manager-only matrix page (rows = locations, columns = Packages,
checkbox = `ACTIVE`; Đà Nẵng row present but empty while inactive); location
name in 003.1 and 003.3 pickers and lists.

**Verification plan:** unit tests for the sellability service (each combination
of lifecycle, pair, location active); MySQL IT for migration seed (Hoàn Kiếm 5,
Tây Hồ 3, Đà Nẵng 0) and for the reserve/toggle race; contract tests for 403 on
Staff/Admin read and write, 409 on inactive discovery, no-op audit; Modulith
verify; 003.3 tests for cross-location change, Wax Casting both sessions at one
location, and Staff refused on a different location.

## Audit review for Manager (Phase 19, plan 2026-11-17)

**Spec:** "Audit review for Manager", FR-047–FR-055, SC-013, US7 (owner answers
2026-11-17 in Clarifications). **Status: DRAFT/PROPOSED**; no approval in
`approval.md` yet. Tasks: Phase 19 (T169–T174) in `tasks.md`.

**Design decisions (proposed):**

1. **No new table, no migration.** Both sources already exist
   (`operations_audit_events` V7/V66, `settlement_business_audits` V30/V36). The
   feature adds read paths only. Verified in code on 2026-11-17: no controller or
   paged query reads either table today.
2. **Two read routes in `workshopoperations`** (OpenAPI): `GET
   /workshop-audit/operations-events` and `GET /workshop-audit/settlement-records`,
   active global Manager only via `WorkshopOperationsAuthorizationService.
   requireGlobalManager`; the refused Operations read is wrapped by
   `OperationsDeniedAttemptRecorder.guard` (FR-048). The Settlement route is
   served by **`workshopsettlement` (003.3) itself**, not by Operations: 003.3 already
   depends on `workshopoperations :: facade` (11 files; `package-info.java`), so an
   Operations call into a 003.3 facade would close a module cycle and fail
   `ApplicationModules.verify()` (analysis finding F1, 2026-11-17). 003.3 reuses the
   Operations guard through the existing Operations facade or its own
   `WorkshopSettlementAuthorizationService` (global active Manager, Staff and Admin 403)
   and `AccountFacade` for names; Operations does not know about the Settlement route.
   The screen calls both routes independently.
3. **Keyset paging by `id desc`** with an opaque `cursor`, `limit` 1–100, default
   50. Chosen over offset paging because rows are appended while the Manager
   browses (FR-050), and `occurred_at` alone is not unique. Existing indexes
   cover actor, session and time filters; the action and outcome filters scan
   within the time bound, so a default time window is applied when no `from` is
   given (proposal: last 7 days; owner may change) and is shown on the screen.
4. **Metadata whitelist** in one class, applied after parsing the stored JSON
   with a tolerant parser (invalid JSON → empty metadata, FR-051 edge case).
   Whitelist starts with the keys seen today: `fromState`, `toState`,
   `bookingCode`, `ticketCode`, `bookingReference`, `reasonCode`,
   `ticketReference`, `qrGeneration`, `ticketCount`, `releasedSeats`,
   `movedTickets`, `recipientCount`, `code`, `status`. Unknown keys are dropped
   so a future writer cannot leak a field by accident. `assignedAccountId` is
   also on the list and is shown as an account id only. All metadata values are returned
   as strings.
5. **Actor display** through the account owner's facade (`findAccountFullName`
   exists for one id). One call per distinct actor on the page, at most the page
   size; a batch method is a proposal for the account owner, not assumed. A failed
   or empty lookup shows the id only; it never fails the page.
6. **Settlement reason text** is returned in full to the Manager (owner decision)
   and never logged; DTO `toString` and request/response logging exclude it.
7. **Frontend:** one Manager-only page `/operations/audit` under
   `StaffArea`, two tabs, filter bar, table, cursor "load more", empty/error/
   loading states; menu entry in `OperationsSidebar` inside `canManageOperations`.
   **Blocked until a Figma or equivalent approved UI exists** (frontend gate F01,
   F05). Staff typing the URL follows the open decision FE-DESIGN-0006
   (redirect vs denial page); this page adopts whatever is decided there.

**Module impact:** no new dependency between modules. The Settlement route, service and
repository query are added inside `workshopsettlement` (003.3 owner agreed 2026-11-17, B5);
`workshopoperations` gains none. Both modules keep one-way dependencies.
No change to 003.1. Existing audit writers are unchanged.

**Failure and recovery:** 400 `VALIDATION_ERROR` (existing code; no new code is added to the closed error
catalogue) for `from` after `to`, bad `limit` or malformed
cursor; 401/403 as for the matrix; 503 safe error with correlation id; the screen
keeps filters on error (FR-055).

**Verification plan:** unit tests for the whitelist, cursor encoding and filter
combinations; MySQL IT for keyset paging with concurrent inserts (no duplicate,
no skipped row), for the 7-day default window and for 403 on Staff/Admin with a
`DENIED` row written for the Operations route; contract test that no
non-whitelisted metadata key and no e-mail-like value is returned; Modulith
verify; FE component and page tests, then a real-backend run against the dev DB.

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
  `contracts/workshop-operations.openapi.yaml`. *(Added 2026-11-15:)* every
  returned `code` is in the `Problem.code` enum (spec "Error catalogue");
  `401` only for token failures and `403` for no account/inactive/Admin;
  `CHECK_IN_NOT_OPEN` with `checkInOpensAt`; `429` with `Retry-After`; `503`
  with no side effect when the Account facade fails; `STAFF_NOT_ELIGIBLE` with
  each `reason`; denied-attempt audit rows without PII (T126–T131).
- Modulith-test that Operations does not depend on Booking internals and that
  Booking uses only the Operations facade.

## Complexity Tracking

No Constitution exception is requested. The ownership move is necessary to
avoid a Booking-to-Operations-to-Booking cycle and to keep capacity and
attendance decisions in one transactional module.
