# Tasks: 003-member-profile
Approval/source: user-approved implementation plan, 2026-10-19. Checkboxes are implementation progress, not reviewer sign-off. Implementation was removed at the user's request on 2026-10-19; only design artifacts remain.
## Phase 1: Design and prerequisites
- [x] T001 Create approved spec/plan/data model/OpenAPI, run read-only speckit-analyze and record coverage (FR-001–FR-018, SC-001).
## Phase 2: Tests and core
- [ ] T002 Add profile validation/service/API/web security tests and provider boundary tests (FR-001–FR-016, FR-018).
- [ ] T003 Add profile entity/repository/V4 migration; serialized writes and atomic safe audit (FR-004–FR-009, FR-017).
- [ ] T004 Implement Firebase profile identity/session boundary and isolated API/web filter chains, CSRF and noncacheable responses (FR-001–FR-003, FR-010–FR-014, FR-016, FR-018).
- [ ] T005 Implement shared profile/policy/bootstrap services and strict API controllers (FR-002–FR-009, FR-014–FR-018).
## Phase 3: UI and verification
- [ ] T006 Implement Thymeleaf profile/policy/auth pages and auth-only Firebase JS, secure cookie/logout and bilingual feedback (FR-010–FR-016, FR-018, SC-004).
- [ ] T007 Run focused tests, existing regression suite and Modulith; MySQL migration/upgrade/concurrency/rollback and Firebase Emulator smoke (SC-001–SC-003).
- [ ] T008 Review diff/requirements, verify UI no-JS and record final commands/results/limitations (SC-001, SC-004).
Dependencies: T001 before any code/test; T002 before T003–T006; T003/T004 before T005/T006; T007 after implementation; T008 after verification. No tasks alter booking/design business logic.
