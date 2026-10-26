# Implementation Plan: Workshop Operations

**Date created**: 2026-10-24
**Date updated**: 2026-10-26
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Branch**: `003.2-workshop-operations` | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from
`ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/spec.md`

**Status**: Design draft — internal capacity semantics clarified; implementation-contract details remain unapproved.

## Summary

Build the operational backbone for workshop sessions: branches, reusable slots,
sessions, capacity commitments, individual attendance tickets, authorised
check-in, exception audit, and manual attendance finalisation.

The agreed target boundary is deliberately different from legacy code:
`workshopoperations` owns every operational record needed to decide capacity or
attendance, including the attendance ticket, QR reference and ticket code.
`workshopbooking` owns the commercial booking, payment, invoice and customer
ticket delivery. Booking reaches Operations only through the synchronous
`WorkshopOperationsFacade`, whose capacity and delivery-safe ticket-projection
operations are fixed in the shared
[003 internal facade contract](../contracts/workshop-interfeature-facades.md).
No module may read another module's repositories or entities directly.

### Resolved OTA-Observable Snapshot Boundary

Operations remains the sole source of session schedule, capacity and direct-
Mland sale state. For a mapped Basic Ring session, it makes a schedule snapshot
and an availability/sale-state snapshot available to 003.4 only after a
completed local change alters OTA-observable data. The schedule snapshot is
limited to opaque session identity, branch, scheduled start/end, source change
identity/version and effective time. The availability/sale-state snapshot is
limited to opaque session identity, branch scope, sellable availability, sale
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

One participant has one persistent QR ticket, including for Silverclay. Its
validity is evaluated against its entitled session in the scanner's current
session/branch context, and attendance is independent for every ticket/session
pair. Operations supplies Booking only a delivery-safe ticket projection: QR
reference or ticket identity, ticket code, and each entitled session's branch
and scheduled start/end. Booking adds commercial/contact information and sends
it to the customer. `WorkshopOperationsFacade` supplies sale eligibility and
atomic reserve/commit/release outcomes, plus only the delivery-safe projection
after successful commitment. This is an internal synchronous boundary: its
facade types and operations are fixed in the shared 003 contract; database
shape, locking mechanism, and public HTTP routes remain unapproved.

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

## Technical Context

| Area | Decision |
| --- | --- |
| Runtime | Java 21, Spring Boot 3.4.x, Spring Modulith |
| Persistence | Spring Data JPA, MySQL, append-only Flyway migrations |
| Identity and access | Firebase Authentication for identity; Account Management facade for canonical role, active status and location scope; Operations applies its own action rules with target branch and Staff assignment. |
| API | Versioned REST under `/api/v1`, plural kebab-case resources, DTOs and Bean Validation; contract in `contracts/` |
| UI boundary | A separate React + Vite client renders Manager and Staff workflows and consumes REST APIs; the backend does not render application UI or use Thymeleaf/htmx as a UI baseline |
| API security transport | Firebase bearer token on protected API requests. Production origins remain unapproved: use an environment allowlist, deny unspecified origins, do not enable credentialed CORS by default, and do not globally disable CSRF. |
| Time | Store instants; display and validate workshop schedules in `Asia/Ho_Chi_Minh` |
| Testing | JUnit 5, Spring Boot/Security/Modulith tests, plus MySQL-backed Flyway integration verification |

The first rollout configures the existing Vietnam branches and three non-overlapping
default slots: `09:00–12:00`, `13:00–16:00`, and `16:00–19:00`. A normal
workshop uses about 2.5 hours; the remaining 30 minutes is operational buffer.

## Constitution Check

| Rule | Planned response | Result |
| --- | --- | --- |
| Modular ownership | `workshopoperations` exposes an explicit facade; Booking and account/personnel are dependencies only through facades. | Pass |
| No business logic in `common` | Session, ticket, capacity and attendance policies remain in Operations. Shared audit plumbing is technical only. | Pass |
| JPA/Flyway discipline | JPA repositories only; a new append-only migration is selected at implementation time after checking the latest migration. | Pass |
| Security boundary | Firebase verifies identity; server-side capability/scope checks guard every operation. No client-supplied branch or actor is trusted. | Pass |
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

1. **Implement approved integration contracts.** Implement
   `WorkshopOperationsFacade` from the shared 003 contract: its capacity
   operations handle holds, commitment confirmation/release and sale
   eligibility; its ticket-projection operation supplies customer-delivery
   summaries after successful commitment.
   Consume the Account Management facade for actor activity, canonical role and
   location scope. Operations applies its own action rule with target branch and
   Staff assignment; it does not map a UI role name, Firebase claim or retired
   role code to authority.
   For 003.1, the facade boundary exposes only a sale-eligibility snapshot
   (customer-selectability, sale state, branch, scheduled start/end, remaining
   capacity) and atomic group operations: reserve N seats in every requested
   session or none; commit every active hold of a booking or none; and release
   active holds once. It also supplies Booking a delivery-safe ticket
   projection, never attendance, Staff/audit or PII. The facade is synchronous.
   Every booking-critical capacity call joins the caller's one
   local MySQL transaction; events are limited to non-critical post-transition
   handoff. For an authorized Booking customer read, the same delivery-safe
   boundary returns the current entitlement/branch/schedule rather than an
   initial snapshot. A successful replacement or verified-refund consequence
   therefore becomes visible to the existing Booking/Magic-Link access without
   Booking copying or owning a session schedule.
   For 003.4, Operations exposes only the approved semantic schedule and
   availability/sale-state snapshots for mapped Basic Ring sessions. `DRAFT`
   produces none; `PUBLISHED` and `SALES_PAUSED` expose or refresh the current
   snapshot, while `CANCELLATION_REQUESTED` and `CANCELLED` expose closed sale
   state. Hold creation, release and expiry notify when availability changes;
   pause/resume and session cancellation/closure notify when sale state changes.
   Hold-to-ticket commitment creates no notification when the post-state is
   unchanged. Both snapshot kinds use one per-session source-version sequence;
   a resync returns the one affected session's complete current post-state at
   its existing version. The handoff is a persistent internal Spring Modulith
   event publication recorded in the same Operations transaction; 003.4 consumes
   it at-least-once and owns its separate durable provider delivery. V1 uses no
   message broker. Concrete event/facade types, payload, schema, retry values
   and provider policy remain unapproved.
   Public HTTP routes, Java
   method names, DTO fields and locking mechanics remain unapproved design
   details.
2. **Create the operational aggregate.** Model branch, reusable branch slot,
   session, active Staff assignment, one Manager operational lead, capacity hold
   and individual attendance ticket with session entitlements. Enforce
   branch/date/slot uniqueness, positive capacity and the no-overlap slot rule.
3. **Implement session administration.** Support single and bounded bulk
   session generation (at most 90 future days), duplicate reporting, slot
   activation/deactivation, lifecycle transitions, schedule change warnings and
   state-aware assignment/lead handover.
4. **Make capacity transactional.** Reserve only against `PUBLISHED` sessions;
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
5. **Implement tickets and check-in.** Generate individual opaque QR references
   and unique ticket codes. Validate the scanner's current session/branch,
   ticket entitlement, ticket validity, that ticket/session pair's attendance
   state, opening time and actor scope as one result. QR is primary, exact
   ticket code is normal fallback, booking code is lookup-only. Overrides remain
   in a separate permissioned, reasoned and audited path.
   A repeated normal scan returns its existing check-in result as `200 OK` with
   `ALREADY_CHECKED_IN` and `checkedInAt`; it does not create another action.
6. **Close attendance correctly.** Let an assigned active Staff member or the
   recorded active operational lead finalise the whole session after its actual
   end; convert still-unchecked valid tickets to `NO_SHOW`. Only a Manager may
   perform a post-finalisation correction, with mandatory audit evidence.
7. **Handoff cancellation and delivery.** On cancellation request, stop normal
   operation and create the audited case consumed by feature 003.3. Booking
   obtains only delivery-safe ticket views from Operations; it never owns or
   mutates attendance state.
8. **Migrate and verify.** Add a new Flyway migration after inspecting the
   deployed migration sequence, backfill only through an approved runbook, and
   exercise the flows in `quickstart.md` against MySQL.

## Validation Strategy

- Unit-test lifecycle guards, slot overlap, capacity arithmetic, ticket state
  rules and audit-reason requirements.
- Integration-test JPA constraints and Flyway migration on MySQL.
- Concurrently attempt capacity holds and duplicate check-ins; exactly one
  transaction must succeed where capacity or a ticket is contested.
- Security-test active/inactive Staff, Manager branch boundaries, operational
  lead handover, normal check-in, and separately-authorised override.
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
