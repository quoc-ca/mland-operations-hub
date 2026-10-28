# Tasks: Workshop Operations

**Date created**: 2026-10-24
**Date updated**: 2026-10-28
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md),
[data-model.md](data-model.md),
[OpenAPI contract](contracts/workshop-operations.openapi.yaml),
[quickstart.md](quickstart.md), Constitution, `spec-general.md`, and
`ppswbs-spec/AGENT.md`.

**Prerequisites**: This task list is a SPEC handoff only. Execute implementation
under `ppswbs_backend/AGENT.md`; do not treat the legacy Booking-owned session
and ticket model as the target architecture.

**Tests**: Required. The specification and plan explicitly require lifecycle,
authorization, migration, contract, atomicity and Modulith-boundary coverage.

**Organization**: Tasks are grouped into independently verifiable operational
stories. `workshopoperations` owns canonical workshop location, slot, session, capacity hold,
attendance ticket and check-in. `workshopbooking` owns commercial booking,
payment, invoice and delivery, and may interact only through the Operations
facade.

**Authorization and lifecycle rebase note (2026-10-28)**: The approved V1 rule is role-based
and location-scoped for Staff, with no capability key, per-account override,
session-assignment prerequisite or Manager operational-lead prerequisite for
the operations listed below. An active Manager has global workshop authority.
At `checkInOpensAt`, check-in becomes available while the session remains
`PUBLISHED`; at the session's scheduled start time, a scheduler automatically
transitions eligible sessions to `IN_PROGRESS`. Manual transition remains only
for explicit early start or recovery.
Existing `[X]` markers on tasks first implemented under the former assignment/
lead boundary are historical evidence only; the re-based behavior must be
revalidated before those tasks are treated as complete for this rule.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can proceed in parallel with tasks that touch different files once
  listed dependencies are satisfied.
- **[USn]**: Maps a task to its user story.
- Every task names its primary file path; supporting test fixtures may be added
  beside the named test file.

## Phase 1: Setup and Architecture Rebase

**Purpose**: Establish the target Modulith module without extending legacy
Booking ownership.

- [X] T001 Create `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/package-info.java` and register `workshopoperations` as a distinct Modulith module with only `facade` boundary types public; retain `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/common/` outside `features/` as the technical kernel.
- [X] T002 [P] Create the target package skeleton at `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/{controller,dto,dao,entity,service,facade}/` without moving business behavior into the root `common` package.
- [X] T003 [P] Update `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/ModulithArchitectureTests.java` to discover only `features/*` business modules and verify that Operations has no dependency on Booking internals.
- [X] T004 Record the legacy-to-target rebase inventory in `ppswbs_backend/docs/workshop-operations-legacy-rebase.md`, covering the old Booking-owned `WorkshopSession` and one-ticket-per-booking `BookingTicket`; prohibit automatic ticket split, revoke, reset or email during schema migration.

---

## Phase 2: Foundational Boundaries, Persistence and Security

**Purpose**: Build prerequisites that block all operational stories.

**⚠️ CRITICAL**: Complete this phase before building a Manager or Staff flow.

- [X] T005 Consume the published Account Management boundary for canonical account role, active status and location scope through `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/accountmanagement/facade/`; Operations applies its own action rules with target location and explicit Staff-session assignment, and must not use a display role, Firebase claim or retired code as authority or edit Account Management code.
- [X] T006 Complete the persisted facade-command idempotency boundary before accepting `WorkshopOperationsFacade` state-changing capacity, ticket, cancellation or settlement-handoff commands in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/facade/WorkshopOperationsFacadeImpl.java`; mark this gate complete only after T060–T066 and their real-MySQL evidence pass. The facade remains internal and exposes no JPA entity.
- [X] T060 Add one append-only Flyway migration under `ppswbs_backend/src/main/resources/db/migration/` for the Operations-owned idempotency ledger, after inspecting deployed Flyway history to select its identifier; preserve retained data and encode the logical unique identity `facadeAction + operationReference`, fingerprint, lifecycle, original correlation reference, claim version, lease and completed-retention data without repurposing audit, hold or ticket tables.
- [X] T061 Implement the separate ledger entity and repository boundary in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/entity/FacadeCommandIdempotencyRecord.java` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/dao/FacadeCommandIdempotencyRecordRepository.java`; support durable unique claim, equivalent fingerprint lookup and conditional current-claim completion without exposing the record outside `workshopoperations`.
- [X] T062 Implement claim handling for each state-changing facade command in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/facade/WorkshopOperationsFacadeImpl.java`: same identity/different semantic fingerprint rejects safely; a live equivalent claim returns typed `IN_PROGRESS`; an expired equivalent claim may reclaim only through the Operations deployment-configured lease; every initial claim or reclaim advances `claimVersion`.
- [X] T063 Make terminal ledger completion and its command business effect one local MySQL transaction in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/facade/WorkshopOperationsFacadeImpl.java`; conditionally prove the current `claimVersion` before committing `COMPLETED` with immutable `ACCEPTED` or `REJECTED`, roll back technical/retryable failure without a terminal record, and reject/roll back a stale claimant before any business side effect.
- [X] T064 Preserve safe correlation and audit behavior in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/facade/WorkshopOperationsFacadeImpl.java` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/entity/OperationsAuditEvent.java`: retain the original `correlationReference` separately from `operationReference`, write the business audit event only for the first execution, and keep duplicate-request transport correlation out of the immutable business audit record.
- [X] T065 Implement completed-record cleanup in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/service/FacadeCommandIdempotencyCleanupService.java` and its Operations deployment configuration under `ppswbs_backend/src/main/resources/`; retain `COMPLETED` records for at least 24 hours, clean only eligible terminal records, never clean a live `IN_PROGRESS` claim, and leave the lease duration configurable rather than choosing a production value in code.
- [X] T066 Add real-MySQL integration coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/WorkshopOperationsMySQLIT.java` for sequential equivalent duplicate outcome reuse, concurrent equivalent `IN_PROGRESS`, semantic fingerprint mismatch, expired-lease reclaim, stale-claimant rollback, completed-record retention/eligible cleanup, and non-disclosure of raw command payload, token, QR or PII; run it against an approved MySQL environment and retain actual pass evidence before marking T006 complete.

**T006 dependency graph**: `T060 → T061 → T062 → T063 → T064 → T065 → T066 → T006`. T006 may be marked complete only after all replacement tasks are complete and the MySQL evidence is real. Only then may the backend owner update `BE-DESIGN-0002` in `ppswbs_backend/ERROR-LOG.md` as resolved under the backend AGENT process.
- [X] T007 [P] Rebase immutable `WorkshopOperationsFacade` boundary types in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/facade/` to the location-reference terminology in `features/003-workshop-management/contracts/workshop-interfeature-facades.md`, so every target location reference denotes canonical `workshop_location.id`; keep Firebase tokens, booking-contact PII, HTTP DTOs and entities out of these types, and do not retain an independent branch identifier.
- [X] T008 Rebase Operations persistence through an append-only Flyway migration selected only after checking the current Flyway history in `ppswbs_backend/src/main/resources/db/migration/`: replace independent branch persistence with canonical `workshop_locations`, reference it through `location_id`, and add no branch-to-location mapping or duplicate location ID. Preserve any non-disposable environment through the approved preservation path; a reset is permitted only for preflight-confirmed dev/test data with no retention requirement.
- [X] T009 Rebase JPA entities and DAOs in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/entity/` and `dao/` to use canonical Workshop Location and `location_id`, including unique `(location_id, workshop_date, slot_id)`, unique public ticket code/QR reference, positive capacity, version fields and lock-capable queries; remove the target dependency on `WorkshopBranch` without creating a compatibility mapping.
- [X] T010 Rebase `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/service/WorkshopOperationsAuthorizationService.java` and `controller/WorkshopOperationsCorsConfiguration.java` to enforce server-side Firebase identity, active actor state, canonical role/location-scope facts from T005, and direct `session.locationId ∈ actor.activeLocationIds` scope checks plus explicit Staff session assignment; use environment allowlist CORS, no credentialed CORS by default and no global CSRF disable.
- [X] T011 Rebase typed, correlation-linked module error mapping in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/controller/WorkshopOperationsExceptionHandler.java` and the associated internal scope exception/tests from retired Branch terminology to Location terminology. Public typed errors MUST use a location-scoped code/title and MUST NOT expose entities, booking-contact PII or token details.
- [X] T012 Rebase MySQL/Flyway, module-boundary and API-security verification coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/WorkshopOperationsFoundationIT.java`, including canonical-location migration application, direct location-scope allow/deny behavior, CORS allow/deny behavior, CSRF not globally disabled and the invariants introduced by T007–T011; retain real-MySQL evidence.

**Checkpoint**: Operations is a verifiable module with persistence, facade
boundaries and server authorization. No legacy Booking entity is its source of
truth.

---

## Phase 3: User Story 1 — Manager Configures Slots and Future Sessions (Priority: P1) 🎯 MVP

**Goal**: A scoped Manager can manage reusable fixed workshop-location slots and create
future sessions with shared capacity, without changing existing sessions when a
slot later changes.

**Independent Test**: With an authorized Manager for one canonical workshop location, create the
three default non-overlapping slots, create and publish one future session, then
bulk-create a bounded range twice and observe only missing location/date/slot
combinations created using the same `workshop_location.id` present in the actor's `activeLocationIds`.

- [X] T013 [P] [US1] Replace legacy branch request/response DTOs in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/dto/` with Location, Slot and Session DTOs using canonical `locationId` (`int64`), `Asia/Ho_Chi_Minh` same-day slot times, `defaultCapacity >= 1`, session `capacity >= 1`, and no free-form session start/end fields.
- [X] T014 [P] [US1] Replace branch-slot/session contract coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/WorkshopSessionContractIT.java` with tests for `/workshop-locations`, `locationId` (`int64`) and the current `contracts/workshop-operations.openapi.yaml`; reject the retired branch routes and fields.
- [X] T015 [US1] Replace `WorkshopBranchService.java` with scoped canonical workshop-location creation/listing in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/service/`; preserve location history and do not expose a location outside the Manager's `activeLocationIds`.
- [X] T016 [US1] Rebase slot creation/edit and active/inactive behavior in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/service/WorkshopSlotService.java`; reject overlap with every configured slot at the same canonical location, including inactive slots, apply slot changes only to later sessions and never permanently delete a slot.
- [X] T017 [US1] Rebase individual and one-time bulk future-session generation in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/service/WorkshopSessionService.java`; enforce one canonical location/date/slot session, future start, maximum 90 future days, duplicate skip/report, initial `DRAFT`/`PUBLISHED` state and audited preservation of a Manager-set `check_in_opens_at` override.
- [X] T018 [US1] Replace branch REST handlers with `/workshop-locations` handlers in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/controller/WorkshopLocationController.java` and rebase slot/session handlers in `controller/WorkshopSessionController.java` to matching OpenAPI paths, `locationId` DTO validation and direct scoped Manager access.
- [X] T019 [US1] Replace branch-based integration coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/WorkshopSessionManagementIT.java` with canonical-location scope, overlap against active/inactive slots, 90-day limit, duplicate skip/report, inherited schedule immutability and location-scope denial coverage on real MySQL.

**Checkpoint**: A Manager can independently configure and publish an operable
future session; this is the first deliverable increment.

---

## Phase 4: User Story 2 — Authorized Actor Starts and Operates a Session (Priority: P1)

**Goal**: A scheduled-start worker and authorized actors can operate an eligible
session under the role/location rule.

**Independent Test**: Publish a session, verify the scheduler transitions it at
`checkInOpensAt`, verify Staff access only inside the session location and
global active-Manager access, then make an actor inactive and verify authority
is immediately denied.

- [X] T020 [P] [US2] Retire the operational-lead DTO and keep only lifecycle plus optional Staff-roster DTOs in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/dto/`; use canonical `locationId` and include reason/evidence only where the specification requires an audit reason.
- [X] T021 [P] [US2] Replace lifecycle contract coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/WorkshopLifecycleContractIT.java` for the location-based OpenAPI state, scheduled auto-start and optional Staff-roster endpoints; remove operational-lead endpoint/schema assertions and do not accept legacy branch or personnel fields.
- [X] T022 [US2] After Account Management publishes target-account existence, canonical role, status and `activeLocationIds` query evidence, implement normal lifecycle transitions and sales-pause hold rule in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/service/WorkshopLifecycleService.java`: `DRAFT`, `PUBLISHED`, `SALES_PAUSED`, `IN_PROGRESS` and `ATTENDANCE_FINALIZED`, with forbidden transitions rejected and the approved authorization rule enforced (active Staff is location-scoped; active Manager has global authority). Make check-in available at `checkInOpensAt` while the session remains `PUBLISHED`; add a recoverable, idempotent scheduler that transitions eligible `PUBLISHED` sessions to `IN_PROGRESS` at scheduled start time and audits a system actor; retain the manual transition only for explicit early start/recovery. `CANCELLATION_REQUESTED` and `CANCELLED` remain valid session states but are created only by the reasoned cancellation flow and 003.3 settlement handoff in T045/T046, never by the generic T022/T024 state-transition endpoint.
- [X] T023 [US2] Retire operational-lead handover and remove it from `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/service/WorkshopResponsibilityService.java`; any retained Staff assignment/roster record is informational/audit only and must not be an authorization prerequisite, while active Staff authority is determined by role/status plus canonical location scope and active Manager authority is global.
- [X] T024 [US2] After T022/T023 and Account Management target-account query verification, implement state-transition and optional Staff-roster REST handlers in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/controller/WorkshopSessionController.java`; remove the operational-lead handler, apply the role/location rule and immutable audit entries, and preserve manual transition only for explicit early start/recovery.
- [X] T025 [US2] After T022-T024, add real-MySQL lifecycle/security integration coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/WorkshopLifecycleIT.java` for global active-Manager access, active Staff access only within `activeLocationIds`, inactive actor revocation, publication without Staff, check-in opening at `checkInOpensAt`, scheduled-start auto-transition at session start with restart/retry idempotency, explicit early-start recovery, sales pause and audit evidence; do not require a session assignment or operational-lead record for the re-based authority rule.

**Checkpoint**: Onsite authority is explicit, location-scoped and immediately
revoked when an actor becomes inactive.

---

## Phase 5: User Story 3 — Staff Checks In One Attendance Ticket (Priority: P1)

**Goal**: An authorized onsite actor can check in exactly one valid ticket by QR
or exact ticket code, while capacity and ticket ownership remain in Operations.

**Independent Test**: Confirm a capacity hold through the public facade,
issue individual tickets, open check-in, scan one QR twice concurrently and
observe exactly one check-in plus the recorded time on the later response.

- [X] T026 [P] [US3] Add hold, ticket issue, QR/ticket-code `CheckInResult` and privacy-safe result DTOs in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/dto/`; normal scan results contain only ticket code, session/location, ticket status, `checkInResult` and `checkedInAt`.
- [X] T027 [P] [US3] Add facade and REST contract tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/AttendanceTicketContractIT.java` for capacity, ticket and normal check-in behavior.
- [X] T028 [US3] Implement lock-protected hold create/confirm/release/expiry and capacity-reduction protection in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/service/CapacityHoldService.java`; reserve only in `PUBLISHED`, preserve an unexpired pre-pause hold and never oversell attendance-ticket capacity.
- [X] T029 [US3] Implement individual opaque QR reference and unique ticket-code issue plus delivery-safe ticket retrieval in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/service/AttendanceTicketService.java`; do not retain a one-ticket-per-booking constraint.
- [X] T030 [US3] Implement normal QR/exact-ticket-code verification in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/service/CheckInService.java`; require online request, an auto-started or explicitly recovered `IN_PROGRESS` session, `check_in_opens_at` already reached, same session/location, active unused ticket and an active actor authorized by the re-based rule: Staff must have `session.locationId` in `activeLocationIds`, while an active Manager has global authority; do not require Staff session assignment, Manager operational-lead status or the capability key.
- [X] T031 [US3] Implement Operations facade and check-in/booking-lookup REST handlers in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/facade/WorkshopOperationsFacade.java` and `controller/WorkshopCheckInController.java`; booking code locates tickets only and never itself checks in an unspecified ticket, while a repeat scan returns `200 OK`, `ALREADY_CHECKED_IN` and the original `checkedInAt`.
- [X] T032 [US3] Add Operations-side facade contract coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/AttendanceTicketContractIT.java` proving a Booking caller can confirm the complete active hold set and receive delivery-safe individual ticket projections only after verified commercial confirmation; do not edit Booking code or expose Operations entities/DAOs.
- [X] T033 [US3] Add atomicity, capacity and privacy integration tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/CheckInIT.java` for concurrent scans, `200 OK` duplicate response with `ALREADY_CHECKED_IN` and original time, wrong location/session, early scan, booking-code generic no-eligible result and non-disclosure of booking contact data.

**Checkpoint**: Capacity commitment, individual tickets and ordinary check-in are
transactional and independently demonstrable.

---

## Phase 6: User Story 4 — Staff Recovers from Lost Ticket Access and Authorized Exceptions (Priority: P2)

**Goal**: Staff can safely request ticket redelivery and an authorized actor can
handle a documented verification exception without broadening cross-session or
cross-location check-in authority.

**Independent Test**: With a booking that has tickets across sessions, request
redelivery from the current session and verify all active unused tickets go only
to the existing booking-contact channel; exceed three requests in 15 minutes and
verify the fourth is denied.

- [X] T034 [P] [US4] Add resend and reasoned override DTOs in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/dto/`, including the standardized reason, required note/evidence behavior and failed-redelivery correlation.
- [X] T035 [P] [US4] Add resend/override contract tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/TicketRecoveryContractIT.java` for the matching OpenAPI endpoints and typed problem responses.
- [X] T036 [US4] Verify through `WorkshopBookingFacade` that Operations can request only delivery of existing active-unused ticket references; Booking owns delivery to the stored contact channel and Operations must neither change recipient data nor access contact PII or Booking code.
- [X] T037 [US4] Implement audited resend authorization/throttling in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/service/TicketResendService.java`; permit active Staff whose `activeLocationIds` contains the session location or any active Manager in `PUBLISHED`, `SALES_PAUSED` or `IN_PROGRESS`, without assignment, operational-lead or capability prerequisites, and enforce at most three requests per booking in 15 minutes.
- [X] T038 [US4] Implement the separate authorized check-in override path in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/service/CheckInOverrideService.java`; authorize an active Staff only when `session.locationId` is in `activeLocationIds`, and authorize any active Manager globally, without a capability key, session-assignment prerequisite or operational-lead prerequisite; still require a reason/audit record, unused valid same-session/same-location ticket and `TICKET_REDELIVERY_UNAVAILABLE` evidence after a failed resend.
- [X] T039 [US4] Implement ticket resend and override REST handlers in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/controller/WorkshopCheckInController.java` without permitting offline scan queues or booking-code bulk check-in.
- [X] T040 [US4] Add recovery/override integration tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/TicketRecoveryIT.java` for cross-session resend contents, excluded cancelled/revoked/refunded/used/no-show tickets, throttle, failed delivery, permission denial, audit evidence and cross-location/session rejection.

**Checkpoint**: Lost-ticket support remains delivery-safe and exception handling
remains narrowly authorized and auditable.

---

## Phase 7: User Story 5 — Manager Finalizes Attendance and Cancels Safely (Priority: P2)

**Goal**: The responsible onsite operator can finalise a whole session; a Manager
can correct evidence or initiate cancellation without this feature performing
settlement, refund, reschedule or customer notification.

**Independent Test**: Start a session, check in one ticket, finalise it after
actual end, verify unchecked active tickets become `NO_SHOW`, then make a
reasoned Manager correction. Separately request cancellation with commitments
and verify a 003.3 handoff stops normal operation.

- [X] T041 [P] [US5] Add finalisation, check-in reversal, post-finalisation correction and reasoned cancellation DTOs in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/dto/` with post-finalisation outcomes limited to `CHECKED_IN` or `NO_SHOW`.
- [X] T042 [P] [US5] Add finalisation/correction/cancellation contract tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/WorkshopClosureContractIT.java` for the matching OpenAPI endpoints, including `200 CANCELLED` versus `202` handoff responses.
- [X] T043 [US5] Implement whole-session attendance finalisation in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/service/AttendanceFinalizationService.java`; permit an active Staff whose `activeLocationIds` contains the session location or any active Manager while `IN_PROGRESS`, without session assignment or operational-lead prerequisites, require actual-end confirmation/audit and convert every unchecked active ticket to `NO_SHOW` atomically.
- [X] T044 [US5] Implement Manager-only erroneous-check-in reversal and finalized-attendance correction in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/service/AttendanceCorrectionService.java`; authorize any active Manager globally (no operational-lead or location prerequisite), keep correction Manager-only, before finalisation return to not-checked-in, after finalisation select only `CHECKED_IN`/`NO_SHOW`, and retain original actions plus reasoned audit evidence.
- [X] T045 [US5] Implement `CancellationHandoffService.java` so committed-session cancellation atomically records `CANCELLATION_REQUESTED`, the local handoff case and a durable Operations-owned outbox event for asynchronous consumption by 003.3; return `202 HANDOFF_ACCEPTED_LOCALLY` without waiting for a Settlement outcome, allow direct `CANCELLED` with `200` only with neither confirmed ticket nor unexpired hold, preserve operation/correlation references, and leave settlement/customer resolution to 003.3.
- [X] T046 [US5] Implement finalisation, reversal, correction and cancellation REST handlers in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/controller/WorkshopAttendanceController.java` with correct state/authority rejection and immutable audit correlation.
- [X] T047 [US5] Add closure integration coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/WorkshopClosureIT.java` for early/late actual finish, no-attendee session, terminal-state rejection, manager-only correction, direct cancellation `200` eligibility and `202` 003.3 handoff without refund/reschedule side effects.

**Checkpoint**: Attendance closure and cancellation preserve an immutable,
reconcilable operational record without leaking into 003.3 commercial behavior.

---

## Phase 8: Polish, Cut-over Safety and Verification

**Purpose**: Complete the documented target architecture and prepare a safe
handoff to the implementation persona.

- [ ] T048 [P] Add contract drift verification for `ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/contracts/workshop-operations.openapi.yaml` in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/WorkshopOperationsOpenApiIT.java`.
- [ ] T049 [P] Add end-to-end quickstart scenarios from `ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/quickstart.md` to `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/WorkshopOperationsQuickstartIT.java`, including MySQL/Flyway rather than H2-only proof.
- [ ] T050 Update `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/ModulithArchitectureTests.java` so Booking calls only the Operations facade and Operations never imports Booking entity, DAO, service or controller packages.
- [ ] T051 Execute the approved legacy mapping/reissue release runbook in `ppswbs_backend/docs/workshop-operations-legacy-rebase.md` before retiring `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/domain/WorkshopSession.java` and `BookingTicket.java`; do not delete or alter live legacy tickets without the separately approved operator decision and audit evidence.
- [ ] T052 Run the commands in `ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/quickstart.md`, record actual results and any verification exception in `ppswbs_backend/ERROR-LOG.md` only if the backend AGENT requires it after a failed readiness/implementation check.
- [ ] T053 Implement OTA-observable schedule and availability/sale-state projection in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/service/`; advance one per-session source version exactly once for each completed transaction that changes an OTA-observable value, emit the complete post-state only for the eligible lifecycle changes in `spec.md`, and do not expose PII, booking/payment/ticket detail, OTA mapping, or a public HTTP contract.
- [ ] T054 Implement the persistent internal Spring Modulith publication and authoritative one-session resync boundary in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/facade/` and `service/`; record the publication in the same local Operations transaction, use no broker or external OTA call, return resync at the existing source version, and do not make resync a business change.
- [ ] T055 Add MySQL/Modulith integration coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/WorkshopOtaSnapshotIT.java` for `DRAFT`/internal-only no-publication, schedule/availability/sale-state version behavior, hold create/release/expiry, unchanged hold-to-ticket commit, consumer failure/restart at-least-once replay, duplicate/older handling, gap resync, and proof that a 003.4 mapping failure cannot change Operations capacity, sale state, or direct-Mland booking.

---

## Phase 9: Settlement and required-session-change facade handoff

**Purpose**: Supply 003.3 with authoritative outcomes without exposing Operations data or inventing a public REST contract.

- [ ] T056 Implement the approved `WorkshopOperationsFacade` settlement-readiness operation in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/facade/`; it returns only whether every required session is `ATTENDANCE_FINALIZED`, never attendance/ticket/Staff/audit/PII detail or a conclusion inferred from schedule time.
- [ ] T057 Implement the approved complete required-session replacement and post-refund-consequence operations in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/facade/` and `service/`; enforce future eligible sources, future `PUBLISHED` customer-selectable targets, full participant capacity, all-or-none entitlement/capacity transfer, persistent QR, no `SALES_PAUSED` exception, and no attendance-history rewrite.
- [ ] T058 Add facade integration coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/SettlementChangeFacadeIT.java` after T056/T057 for readiness only after every finalization, cancelled/unfinalized false, one/two-session atomic replacement, target/source rejection, unchanged QR, no partial capacity/entitlement result, and verified-refund-only consequence.
- [ ] T059 Implement and test the authorized read-only current delivery-safe ticket/session-entitlement projection in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/facade/`; after replacement or verified refund it exposes current location/schedule/entitlement only, never attendance, Staff, audit or PII.

---

## Phase 10: Workshop Package master and public booking discovery boundary

**Purpose**: make Operations the authoritative owner of generic Workshop Packages and the only public source of customer-selectable session snapshots.

**Independent Test**: a Central Manager can publish a generic Package; a public caller can read only published Package/session snapshots for a location; and a Booking capacity reservation validates the current Package rules atomically.

- [ ] T067 [P] Add Package command/response and customer-safe discovery DTOs in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/dto/`; use `packageId`, `locationId`, opaque UUID `sessionReference`, whole-VND public price, participant bounds, required-session count, same-location rule and nonnegative `minimumSameDaySlotGap` without exposing audit, Staff, booking or ticket data.
- [ ] T068 [P] Add public/Manager Package and customer session-discovery contract coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/WorkshopPackageContractIT.java` against `contracts/workshop-operations.openapi.yaml`; verify `/workshop-packages`, `/workshop-packages/{packageId}`, lifecycle transition and `/workshop-packages/{packageId}/sessions` route/security/schema boundaries without changing the contract.
- [ ] T069 Add an append-only Package persistence migration, Package entity and repository in `ppswbs_backend/src/main/resources/db/migration/` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/{entity,dao}/`; retain published/frozen history, do not create a local Location copy or branch mapping, and select the Flyway version only from the actual migration history.
- [ ] T070 Implement the Operations-owned Central Manager authorization policy in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/service/WorkshopPackageAuthorizationService.java`; require an active canonical `MANAGER` from the public Account facade whose `activeLocationIds` contains every active canonical Workshop Location, with no new Account role/capability or direct Account persistence access.
- [ ] T071 Implement generic Package draft/publish/retire and prospective-edit behavior in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/service/WorkshopPackageService.java`; permit no deletion after publication, keep Basic Ring and Silverclay as data configuration rather than package-name business branches, and audit state-changing execution safely.
- [ ] T072 Implement Manager Package administration and public Package/session-discovery handlers in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/controller/WorkshopPackageController.java`; return only future `PUBLISHED` customer-selectable sessions matching required `locationId` and optional `from`/`to`, exact snapshot `remainingCapacity`, current sale state and opaque UUID reference, while retaining `/workshop-sessions` for protected operational search.
- [ ] T073 Rebase `WorkshopOperationsFacade` Package selection and reserve boundary in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopoperations/{facade,service}/`; provide `PublishedPackageSelectionQuery`, require `packageReference` plus UUID session references for reserve, and atomically recheck Package publication, participant bounds, required count, same-location rule, minimum same-day slot gap, sale eligibility and capacity with typed `PACKAGE_NOT_PUBLISHED`, `PACKAGE_SELECTION_INVALID` and `INSUFFICIENT_CAPACITY` outcomes.
- [ ] T074 Add MySQL/Flyway integration coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/WorkshopPackageIT.java` for Central Manager all-active-location authorization, draft/publish/retire visibility, immutable published/frozen history, Basic Ring `(1,true,0)`, Silverclay `(2,true,1)`, exact capacity snapshot filtering, and no customer disclosure of operational/PII data.
- [ ] T075 Add facade/concurrency integration coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopoperations/WorkshopPackageFacadeIT.java` for stale discovery followed by atomic reserve rejection, Package-not-published, participant/count/location/gap rejection, all-or-none capacity reservation and facade-only non-disclosure to Booking.

---

## Dependencies and Execution Order

### Phase Dependencies

- **Phase 1** has no implementation dependencies.
- **Phase 2** depends on Phase 1 and blocks every user story.
- **US1** depends on Phase 2.
- **US2** depends on US1 because a session must exist before its responsibility
  can be assigned and its lifecycle can begin.
- **US3** depends on US1 and US2, plus the Booking facade consumer using T006;
  it is the first end-to-end onsite attendance flow.
- **US4** depends on US3 and the Booking delivery facade from T036.
- **US5** depends on US2 and US3; its cancellation handoff depends on the 003.3
  consumer contract but not on 003.3 settlement implementation.
- **Phase 10** depends on canonical Location, capacity-hold and Account-facade
  foundations; T066 also depends on T062--T064 and reuses, rather than bypasses,
  the existing capacity boundary.
- **Phase 8** depends on all desired stories. T051 is a release gate, not a
  prerequisite for local feature development. T053–T055 are the implementation
  prerequisite for 003.4 task T011; they make the approved persistent
  Operations-to-OTA handoff available but do not call an external OTA.

### User Story Dependencies

```text
Foundation
  └── US1: Slot and session configuration
        └── US2: Onsite responsibility and lifecycle
              └── US3: Capacity, ticket and normal check-in
                    ├── US4: Resend and override recovery
              └── US5: Finalisation, correction and cancellation handoff
                    └── Phase 8: OTA-observable snapshot publication/resync
```

### Parallel Opportunities

- T002–T004 can proceed in parallel after T001's target module name is agreed.
- T005, T007 and T008 can proceed in parallel; T009 follows the migration model.
- In each story, the `[P]` DTO and contract-test tasks can proceed together.
- US4 and US5 can proceed in parallel once US3 is complete, provided different
  developers avoid editing the same controller aggregation files at once.
- T053 and T054 follow the session/capacity/lifecycle changes they project; T055
  follows both and may run in parallel with unrelated release-runbook work.
- T060 and T061 can proceed in parallel; T062 and T063 must complete before
  T064--T066, while T067 and T068 follow their respective service/facade work.

## Parallel Example: User Story 3

```text
Task: "T026 Add hold, ticket issue, QR/ticket-code DTOs in .../dto/"
Task: "T027 Add facade and REST contract tests in .../AttendanceTicketContractIT.java"

# After the DTO contract is stable:
Task: "T028 Implement CapacityHoldService.java"
Task: "T029 Implement AttendanceTicketService.java"
```

## Implementation Strategy

### MVP First

1. Complete Phases 1 and 2.
2. Complete US1 and demonstrate safe slot/session configuration.
3. Complete US2 and US3 to demonstrate the first operational MVP: authorized
   session start plus one-ticket QR/ticket-code check-in.
4. Validate the US3 independent test before adding resend/override or closure.

### Incremental Delivery

1. US1 establishes scheduling and capacity configuration.
2. US2 establishes accountable onsite operation.
3. US3 establishes transactional individual attendance.
4. US4 adds recovery without weakening access control.
5. US5 closes attendance and hands commercial cases to 003.3.

## Design Handoff Notes

- No task authorizes a new role mapping, payment/refund behavior, OTA behavior,
  offline queue or group-assignment model.
- T005, T032 and T036 are cross-feature contracts. Their producer implementation
  is owned by Account Management or Workshop Booking, respectively; Operations
  may verify and consume compatible facades but must not edit those features.
- T053–T055 provide the cross-feature dependency consumed by 003.4: a persistent
  internal Spring Modulith publication with one source-version sequence and
  authoritative one-session resync. They are not a provider adapter, broker,
  public REST API, or permission to change OTA mapping/capacity outside 003.2.
- The known legacy model is deliberately not a shortcut. The critical review
  found that retaining Booking-owned session/ticket state would recreate a
  circular dependency and allow capacity/attendance divergence.
