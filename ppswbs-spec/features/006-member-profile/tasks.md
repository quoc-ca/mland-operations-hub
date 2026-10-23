# Tasks: 006-member-profile
Approval/source: user-approved implementation plan, 2026-10-19. Checkboxes are implementation progress, not reviewer sign-off. Implementation was removed at the user's request on 2026-10-19; only design artifacts remain.
Revision source: the user's approved authentication/home-redirect/no-policy-gate correction in this conversation. T001 records the original design task; revised-document analysis is separate from runtime completion. Task IDs, dependency order and completion markers are retained. Use the [spec navigation table](spec.md#authentication-and-navigation) and [seven verification scenarios](quickstart.md).
## Phase 1: Design and prerequisites
- [x] T001 Create approved spec/plan/data model/OpenAPI, run read-only speckit-analyze and record coverage (FR-001–FR-018, SC-001).
## Phase 2: Tests and core
- [ ] T002 Add profile validation/service/API/web security and provider boundary tests, including the seven quickstart scenarios: separate sign-in/registration/resumed verification, both allowed Member statuses, no policy lookup/gate, fixed home redirect, explicit resend and stale-credential recovery (FR-001–FR-016, FR-018, SC-003–SC-004).
- [ ] T003 Add profile entity/repository/V4 migration; serialized writes and atomic safe audit (FR-004–FR-009, FR-017).
- [ ] T004 Implement Firebase profile identity/session boundary and isolated API/web filter chains, both allowed statuses without policy gating, CSRF and noncacheable responses; successful session establishment ends at `/` (FR-001–FR-003, FR-010–FR-016, FR-018).
- [ ] T005 Implement shared profile/bootstrap services and strict API controllers; keep 003 eligibility separate from other features' policy-dependent entitlement, preserving statuses/history and eliminating profile policy lookups (FR-002–FR-009, FR-014–FR-018).
## Phase 3: UI and verification
- [ ] T006 Implement Thymeleaf profile/auth pages and auth-only Firebase JS according to the spec navigation table, secure cookie/logout and bilingual feedback. Do not add profile policy pages, automatic resends or automatic profile redirects; do not implement the home page (FR-010–FR-016, FR-018, SC-003–SC-004).
- [ ] T007 Run focused tests, existing regression suite and Modulith; MySQL migration/upgrade/concurrency/rollback and Firebase Emulator smoke (SC-001–SC-003).
- [ ] T008 Review diff/requirements, verify no-JS profile forms and all navigation/no-policy scenarios, and record final commands/results/limitations; distinguish the stale web/Figma prototype from the current spec (FR-011–FR-016, SC-001, SC-003–SC-004).
Dependencies: T001 before any code/test; T002 before T003–T006; T003/T004 before T005/T006; T007 after implementation; T008 after verification. No tasks alter booking/design business logic.
