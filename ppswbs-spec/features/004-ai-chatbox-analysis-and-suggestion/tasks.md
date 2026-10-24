# Tasks: AI Chatbox Analysis and Suggestion

Tasks are ordered by dependency. Code tasks must wait for the feature spec, plan, contract and required design decisions to be approved.

- [ ] T001 Confirm Report 3 role/permission mapping and record the physical role-code gate.
- [ ] T002 Finalize `spec.md`, `data-model.md`, error vocabulary, retention rules and state transitions.
- [ ] T002a Create and review the Feature-04 Permission Matrix for actors, endpoints, scopes and forbidden side effects.
- [ ] T002b Create and review the Feature-04 Validation Matrix for pipeline order, fields, business rules, errors and provider boundaries.
- [ ] T003 Finalize and validate `contracts/ai-chatbox-analysis.openapi.yaml`.
- [ ] T004 Reconcile `custom_design_requests`/review ownership and add an approved Flyway/JPA mapping for analysis, consent, version and suggestion evidence.
- [ ] T005 Define provider-neutral ports and Gemini/storage adapters with safe response/error mapping.
- [ ] T006 Implement basic AI support scope validation, public-context sanitization, rate limiting and retention registration.
- [ ] T007 Implement Member-only consent, image validation, temporary storage, versioned request creation and idempotency.
- [ ] T007a Implement normalized allowlisted provider commands, prompt-injection detection, provider-output policy validation and safe security error mapping.
- [ ] T007b Implement atomic per-Member image rate limiting: three attempts per rolling five minutes, `429 IMAGE_RATE_LIMITED`, `Retry-After` and duplicate-idempotency exemption.
- [ ] T008 Implement Gemini image analysis, response schema validation, provider correlation and post-processing deletion.
- [ ] T009 Implement Staff/Manager review list/detail/decision with threshold, branch/delegation and separation-of-duties checks.
- [ ] T010 Implement Clarification Required and new design-version flow; prevent stale decision reuse.
- [ ] T011 Implement Manager advisory price suggestion, validation, manual fallback and separate catalogue application/publication handoff.
- [ ] T011a Enforce price-suggestion visibility: authorized Manager access, limited Admin technical/audit access and no default Staff/customer access.
- [ ] T012 Add Thymeleaf/htmx screens and no-JavaScript fallback for core actions and safe error states.
- [ ] T013 Add cleanup/background-job handling for text retention and temporary image deletion.
- [ ] T014 Add unit, controller, integration, provider-contract, migration, authorization, concurrency/idempotency and Modulith tests.
- [ ] T014a Add endpoint authorization tests traced to the Permission Matrix.
- [ ] T014b Add field/business validation tests traced to the Validation Matrix.
- [ ] T014c Validate matrix-to-OpenAPI consistency before implementation approval.
- [ ] T015 Run verification, review diff/requirements coverage and record limitations or verification exceptions.

Dependencies: T001–T003 before T004–T012; T004/T005 before persistence/provider implementation; T006–T011 before T012–T015.
