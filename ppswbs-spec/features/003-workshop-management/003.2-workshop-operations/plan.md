# Implementation Plan: Workshop Operations

**Date created**: 2026-10-24
**Date updated**: 2026-10-24
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Branch**: `003.2-workshop-operations` | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from
`ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/spec.md`

**Status**: Design ready; implementation has not started.

## Summary

Build the operational backbone for workshop sessions: branches, reusable slots,
sessions, capacity commitments, individual attendance tickets, authorised
check-in, exception audit, and manual attendance finalisation.

The agreed target boundary is deliberately different from legacy code:
`workshopoperations` owns every operational record needed to decide capacity or
attendance, including the attendance ticket, QR reference and ticket code.
`workshopbooking` owns the commercial booking, payment, invoice and customer
ticket delivery. Booking reaches Operations only through a named facade to hold
capacity and issue/retrieve tickets after the commercial event that permits it.
No module may read another module's repositories or entities directly.

## Technical Context

| Area | Decision |
| --- | --- |
| Runtime | Java 21, Spring Boot 3.4.x, Spring Modulith |
| Persistence | Spring Data JPA, MySQL, append-only Flyway migrations |
| Identity and access | Firebase Authentication for identity; MySQL-backed account/personnel facade for active status, capabilities and Manager branch scope |
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
    │   ├── dto/             # request/response and facade boundary types
    │   ├── dao/             # JPA repositories and persistence access
    │   ├── entity/          # internal JPA session, ticket and audit entities
    │   ├── service/         # transactions, use cases and workshop policies
    │   └── facade/          # only supported cross-module entry points
    ├── workshopbooking/
    │   ├── controller/
    │   ├── dto/
    │   ├── dao/
    │   ├── entity/
    │   ├── service/         # commercial booking/payment and delivery
    │   └── facade/          # calls Operations facade; performs delivery
    └── accountmanagement/
        └── facade/          # account/personnel capability boundary
```

The React + Vite client remains outside this backend module tree. It may call
only the versioned REST contract and must not be treated as an authorization
boundary.

## Implementation Sequence

1. **Establish integration contracts.** Define an Operations facade for
   capacity holds, commitment confirmation/release, ticket issue and
   ticket-validity projection for commercial outcomes reported by 003.3, plus
   delivery-safe ticket summaries. Define the consumed account/personnel facade
   for actor activity, required capability and Manager branch scope. Do not map
   a UI role name to permissions inside Operations.
2. **Create the operational aggregate.** Model branch, reusable branch slot,
   session, active Staff assignment, one Manager operational lead, capacity hold
   and individual attendance ticket. Enforce branch/date/slot uniqueness,
   positive capacity and the no-overlap slot rule.
3. **Implement session administration.** Support single and bounded bulk
   session generation (at most 90 future days), duplicate reporting, slot
   activation/deactivation, lifecycle transitions, schedule change warnings and
   state-aware assignment/lead handover.
4. **Make capacity transactional.** Reserve only against `PUBLISHED` sessions;
   retain pre-pause unexpired holds; confirm or release holds atomically. Lock
   the session/commitment rows within a transaction so concurrent reservations
   cannot oversell capacity.
5. **Implement tickets and check-in.** Generate individual opaque QR references
   and unique ticket codes. Validate session, branch, state, opening time,
   actor scope and unused status in one transaction. QR is primary, exact ticket
   code is normal fallback, booking code is lookup-only. Overrides remain in a
   separate permissioned, reasoned and audited path.
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

## Complexity Tracking

No Constitution exception is requested. The ownership move is necessary to
avoid a Booking-to-Operations-to-Booking cycle and to keep capacity and
attendance decisions in one transactional module.
