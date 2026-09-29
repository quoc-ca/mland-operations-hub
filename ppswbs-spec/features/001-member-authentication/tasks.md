# Tasks: Member Registration and Sign-in

**Input**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md), [data-model.md](./data-model.md), [API contract](./contracts/member-auth.openapi.yaml)  
**Tests**: Required by the project working agreement and this security-sensitive feature. Write the listed test first and confirm it fails before corresponding implementation.

## Phase 1: Decision and delivery setup

**Purpose**: Clear the explicitly documented release and architecture gates. No implementation starts before T001.

- [ ] T001 Ratify the Firebase-Web-SDK JavaScript exception and record it in `ppswbs-spec/.specify/memory/constitution.md` as required by `ppswbs-spec/features/001-member-authentication/plan.md`.
- [ ] T002 [P] Obtain approved bilingual Terms/Privacy content, versions, effective-date ownership, and content locations documented in `ppswbs-spec/features/001-member-authentication/quickstart.md`.
- [ ] T003 [P] Configure non-committed Firebase environments (Google + Email/Password providers, authorized domains, action URLs, Admin credentials, Auth Emulator) following `ppswbs-spec/features/001-member-authentication/quickstart.md`.
- [ ] T004 [P] Select/configure a production transactional-mail sender and test captured-mail adapter for booking confirmation; document secret injection in `ppswbs-spec/features/001-member-authentication/quickstart.md`.

## Phase 2: Foundational security and persistence

**Purpose**: Establish shared infrastructure that blocks every user story.

- [ ] T005 Add required Spring Security, validation, Thymeleaf/htmx, Firebase Admin, Flyway, Testcontainers, and test dependencies in `ppswbs_backend/pom.xml` without adding a client framework.
- [ ] T006 [P] Add environment-backed Firebase/public runtime configuration, production Emulator rejection, and locale configuration in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/configuration/` and `ppswbs_backend/src/main/resources/application*.yml`.
- [ ] T007 [P] Add Flyway migration(s) for `members`, `policy_documents`, `member_policy_acceptances`, and `member_auth_audit_events` in `ppswbs_backend/src/main/resources/db/migration/` according to `data-model.md`.
- [ ] T008 [P] Implement typed API error envelope, correlation ID propagation, safe audit-event writer, and non-secret logging policy in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/common/`.
- [ ] T009 Implement Firebase Admin token verifier and Spring Security bearer authentication integration in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/configuration/`.
- [ ] T010 Implement Member JPA entities/repositories and an entitlement evaluator (UID/status/current-policy acceptance) in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/members/{domain,infrastructure,application}/`.
- [ ] T011 Configure Firebase Auth Emulator and Testcontainers MySQL integration profiles in `ppswbs_backend/src/test/resources/` and test support under `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/support/`.
- [ ] T012 Add failing foundation integration tests for invalid/expired token rejection, UID uniqueness, audit redaction, and current-policy entitlement in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/members/`.

**Checkpoint**: Foundation rejects unauthenticated/invalid Member requests and can evaluate a verified UID without storing credentials.

## Phase 3: User Story 1 — Create a Member (Priority: P1) 🎯 MVP

**Goal**: A Guest can use prominent Google or fallback email/password registration, accept current policies, create exactly one Member, and receive Member entitlement.

**Independent test**: In Auth Emulator, register/sign in once and provision with `PUT /api/v1/members/me`; accept both current policies; repeat with same UID and verify one Member. A different UID with matching email is not merged.

- [ ] T013 [P] [US1] Add failing MockMvc/service tests for idempotent `PUT /api/v1/members/me`, pending consent, active consent, UID collision, and non-merge behavior in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/members/MemberProvisioningIT.java`.
- [ ] T014 [P] [US1] Add failing tests for policy-version validation and immutable acceptance evidence in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/members/PolicyAcceptanceIT.java`.
- [ ] T015 [US1] Implement transactional UID-only Member provisioning and `PUT /api/v1/members/me` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/members/{application,web}/`.
- [ ] T016 [US1] Implement public current-policy read and authenticated policy-acceptance API in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/members/{application,web}/`.
- [ ] T017 [P] [US1] Create bilingual sign-in, registration, policy-acceptance, and safe `noscript` Thymeleaf pages/fragments in `ppswbs_backend/src/main/resources/templates/member-auth/` and `ppswbs_backend/src/main/resources/i18n/messages*.properties`.
- [ ] T018 [US1] Implement Firebase Web SDK Google/email-password registration and bearer provisioning flow, generic provider errors, and no committed Firebase configuration in `ppswbs_backend/src/main/resources/static/js/member-auth.js` plus `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/members/web/`.
- [ ] T019 [US1] Add Member creation/consent/provider-failure audit coverage and verify raw Firebase tokens or emails are absent from responses/logs in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/members/`.

## Phase 4: User Story 2 — Sign in to protected journeys (Priority: P1)

**Goal**: A returning Member can sign in, return safely to the originally blocked ready-ring checkout/history action, and sign out; Guest booking/lookup remains public.

**Independent test**: Unauthenticated requests to Member history/ready-ring checkout return denial; authenticated active Member succeeds; logout removes access; malicious return URLs never redirect outside the site.

- [ ] T020 [P] [US2] Add failing authorization/return-path tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/members/ProtectedJourneyIT.java`.
- [ ] T021 [US2] Implement `GET /api/v1/members/me/entitlement`, protected-route authorization, suspension denial, and typed `POLICY_ACCEPTANCE_REQUIRED` response in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/members/{web,application}/`.
- [ ] T022 [US2] Implement allow-listed same-origin return-to handling and Firebase persistent-session/logout UI behavior in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/members/web/` and `ppswbs_backend/src/main/resources/static/js/member-auth.js`.
- [ ] T023 [US2] Wire ready-ring checkout and Member-history guards while preserving public workshop booking, deposit, and booking lookup routes in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/`.
- [ ] T024 [US2] Add regression tests proving every protected API request evaluates token plus MySQL entitlement and public Guest routes remain available in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/members/`.

## Phase 5: User Story 5 — Confirm Guest booking email before payment (Priority: P1)

**Goal**: Guest booking has a one-time, 15-minute email-confirmation gate before capacity becomes committed or payment/invoice opens.

**Independent test**: Create Guest booking, verify payment blocked; consume token once before expiry to allow booking flow; let another expire and verify cancellation/capacity release/no invoice; retry both paths safely.

- [ ] T025 [P] [US5] Add failing domain/integration tests for token hashing, one-time confirmation, payment gate, 15-minute expiry, capacity release, and idempotency in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/BookingEmailConfirmationIT.java`.
- [ ] T026 [US5] Add booking linkage/confirmation Flyway migration and JPA entities/repositories in `ppswbs_backend/src/main/resources/db/migration/` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/`.
- [ ] T027 [US5] Implement random-token hashing, captured/production email notification port, server-rendered confirmation endpoint/page, and `POST /api/v1/workshop-bookings/{bookingCode}/email-confirmations` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/` and `ppswbs_backend/src/main/resources/templates/workshop-bookings/`.
- [ ] T028 [US5] Implement idempotent 15-minute expiry job plus booking capacity/invoice/payment integration guard in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/`.
- [ ] T029 [US5] Add booking confirmation/expiry audit events and verify tokens are never logged or persisted plaintext in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/`.

## Phase 6: User Story 3 — Recovery, verification, and Guest-history import (Priority: P2)

**Goal**: Email/password Members can request generic recovery/verification; verified Members can preview then explicitly import eligible Guest booking history without duplication.

**Independent test**: Unverified Member sees no candidates. After Firebase verification and current consent, preview appears; decline changes nothing; confirm links each existing eligible booking once; repeat creates no duplicates.

- [ ] T030 [P] [US3] Add failing tests for generic recovery responses, verified-email eligibility, preview/decline/confirm import, all booking statuses, and repeat idempotency in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/members/GuestBookingImportIT.java`.
- [ ] T031 [US3] Implement Firebase Web SDK reset-password and send-verification actions with generic bilingual outcomes in `ppswbs_backend/src/main/resources/static/js/member-auth.js` and templates under `ppswbs_backend/src/main/resources/templates/member-auth/`.
- [ ] T032 [US3] Implement verified-claim refresh, eligible import query, `GET /api/v1/members/me/guest-booking-import-preview`, and explicit idempotent `POST /api/v1/members/me/guest-booking-imports` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/members/{application,web,infrastructure}/`.
- [ ] T033 [US3] Implement import confirmation UI and import/audit evidence (candidate, accepted, linked, skipped) in `ppswbs_backend/src/main/resources/templates/member-auth/` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/members/`.

## Phase 7: User Story 4 — Link another sign-in method (Priority: P2)

**Goal**: A signed-in Member can explicitly link Google or email/password only after proving control of the current Firebase identity; collisions/cancellation do not create a duplicate or merge.

**Independent test**: Link a new provider to current Emulator user; simulate cancellation and credential-in-use; verify generic guidance, unchanged MySQL UID, and no extra Member.

- [ ] T034 [P] [US4] Add browser/integration tests for explicit linking, reauthentication, cancellation, provider failure, and credential collision in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/members/ProviderLinkingIT.java`.
- [ ] T035 [US4] Implement Firebase Web SDK explicit provider-link flow and generic safe outcomes in `ppswbs_backend/src/main/resources/static/js/member-auth.js` and `ppswbs_backend/src/main/resources/templates/member-auth/`.
- [ ] T036 [US4] Record provider-link success/failure audit events without credentials or tokens in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/members/`.

## Phase 8: Cross-cutting hardening and release verification

- [ ] T037 [P] Verify/update the OpenAPI contract against implemented controllers in `ppswbs-spec/features/001-member-authentication/contracts/member-auth.openapi.yaml`.
- [ ] T038 [P] Add security regression coverage for revoked/malformed tokens, open redirect, CSRF decision, suspended account, policy version invalidation, and recovery enumeration resistance in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/members/`.
- [ ] T039 [P] Run accessibility/bilingual/manual browser smoke matrix from `ppswbs-spec/features/001-member-authentication/quickstart.md` and record results in `ppswbs-spec/features/001-member-authentication/verification.md`.
- [ ] T040 Run full unit/integration suite, Firebase Emulator suite, Flyway clean-database migration test, and `git diff --check`; record exact commands/results in `ppswbs-spec/features/001-member-authentication/verification.md`.
- [ ] T041 Re-run SpecKit analyze and converge after implementation, then append only evidenced remaining work to `ppswbs-spec/features/001-member-authentication/tasks.md`.

## Dependencies and execution order

- T001 is a hard gate; T002–T004 must be completed before release and T003 before real auth testing.
- T005–T012 block all stories. T006–T008 can proceed in parallel; T009 depends on T006; T010 depends on T007/T009; T012 depends on T007–T011.
- US1 is the first MVP. US2 depends on US1 entitlement/provisioning. US5 depends on foundational work but can be implemented in parallel with late US1/US2 work if booking ownership is available.
- US3 depends on US1 and US5 confirmation data. US4 depends on US1 browser auth flow.
- T037–T041 run after desired stories; T041 follows verified implementation, not this planning task.

## Parallel opportunities

- After T001, T002/T003/T004 can be owned independently.
- T006, T007, and T008 touch separate concerns; their tests can be prepared in parallel.
- US5 backend work is independent of US2 return-path UI once Phase 2 is complete.
- US3 import APIs and US4 link UX can proceed concurrently after US1, provided they coordinate changes to `member-auth.js`.
