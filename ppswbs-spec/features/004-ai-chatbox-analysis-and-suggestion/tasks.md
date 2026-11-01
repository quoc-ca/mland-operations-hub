# Tasks: Independent Feature-04 Design Workspace

Tasks are ordered by dependency. The implementation agent must not code past the
ownership gate until the unresolved decisions in `spec.md` are approved.

- [x] T001 Read `AGENT.md` and `FE-004-DESIGN-INDEPENDENT-HANDOFF.md`.
- [x] T002 Record the independent boundary and list unresolved ownership/review/retention decisions.
- [x] T003 Approve Guest ownership proof, text-only request policy, human-review policy, withdrawal/revision and image retention.
- [x] T004 Update `spec.md`, `design.md`, `data-model.md`, `entities.md`, `plan.md` and shared `APIs.md` after T003.
- [x] T005 Rebase OpenAPI: remove booking/payment/location/session fields and define independent request/owner inputs and projections.
- [x] T006 Remove booking fields and legacy booking dependencies from entities, services, controllers, facades and module declaration.
- [x] T007 Implement canonical Account facade identity and independent Member/approved Guest ownership isolation.
- [x] T008 Implement optional description/image command, consent-before-storage, image validation, atomic rate limiting and idempotency.
- [x] T009 Implement independent AI analysis/result state, temporary deletion and retention policy.
- [x] T010 Implement approved Staff/Manager review policy, revision/withdrawal transitions and audit constraints.
- [x] T011 Preserve Manager-only price suggestion and Admin technical/audit projection without catalogue auto-publication.
- [ ] T012 Replace booking-dependent React screens with the standalone Design Workspace flow.
- [x] T013 Add Flyway append-only migration for independent ownership/data model (V12__independent_design_request.sql).
- [x] T014 Add unit, controller, authorization, provider-contract, concurrency/idempotency and retention tests.
- [x] T015 Add negative tests proving no booking/payment/capacity/ticket/custom-order mutation or dependency.
- [x] T016 Run compile, Modulith, MySQL migration, OpenAPI and test verification; record exact results.
- [x] T017 Update `version.md` with implementation commit, test commands, results and remaining blockers.

Dependencies: T003 blocks T004–T015. T004/T005/T006 block implementation. T007–T011
block frontend integration. T013 and T014–T016 are release gates.
