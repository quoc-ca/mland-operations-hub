# Tasks: Customer Workshop Booking Lifecycle

**Input**: Design documents from `ppswbs-spec/features/002-workshop-booking/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`,
`contracts/workshop-booking.openapi.yaml`, and `quickstart.md`.

**Scope gate**: Do not implement `CHECKED_IN`, `COMPLETED`, `NO_SHOW`, final
payment, or External Registration System synchronization in this feature.

## Phase 1: Setup

**Purpose**: Establish the feature contract and implementation boundaries.

- [x] T001 [P] Create the feature package directories under `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/{web,application,domain,infrastructure,facade}` and matching test directories.
- [x] T002 [P] Add the payment adapter boundary under `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/payments/{facade,infrastructure}` without coupling booking code to VNPay SDK details.
- [x] T003 [P] Copy and register the Workshop Booking OpenAPI contract at `ppswbs-spec/features/002-workshop-booking/contracts/workshop-booking.openapi.yaml` as the source contract for package, booking, payment and lookup endpoints.
- [x] T004 Remove the legacy email-confirmation classes, endpoints, token states and pre-payment flow from `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/`; replace their customer-facing behavior with post-payment notification/Magic Link tasks and record the migration decision in `ppswbs-spec/features/002-workshop-booking/plan.md`.
- [x] T004a Verify the active database target is development/test, record the target/schema and backup decision, and obtain explicit confirmation before any reset; never run a reset against production or real customer data.

## Phase 2: Foundational

**Purpose**: Blocking persistence, state, security and transaction foundations.

- [x] T005 Create append-only Flyway migration `ppswbs_backend/src/main/resources/db/migration/V2__workshop_booking.sql` for packages, sessions/slots, bookings, invoices, capacity holds, payment attempts/events, tickets, notification intents and external handoff records; preserve migration history and production/customer data, while applying any approved reset only to verified development/test data.
- [x] T006 Define JPA entities and repositories in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/domain/` and `infrastructure/` with unique booking code, participant-count capacity, immutable amount/currency snapshots and required timestamps from `data-model.md`.
- [x] T007 [P] Replace the legacy booking enum with explicit service states `PENDING_PAYMENT`, `CONFIRMED`, `EXPIRED`, `CANCELLED` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/domain/`; allow `CANCELLED` only for explicit pre-payment hold release, define booking financial states `UNPAID`, `DEPOSIT_PAID`, `FULLY_PAID`, `REFUND_PENDING`, `REFUNDED`, define `FAILED` only on `PaymentAttempt`, and reserve later booking states without implementing their transitions.
- [x] T008 [P] Implement typed safe errors, correlation IDs and redacted audit events using the existing `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/common/` conventions; prohibit OTP, raw tokens, secrets and provider payloads in logs.
- [x] T008a [P] Implement the Member identity boundary using the existing Firebase Admin verification conventions in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/`; verify the Firebase ID token before reading its UID, map only the verified UID to Member ownership, and reject missing, expired, malformed or mismatched tokens without logging raw tokens.
- [x] T009 Implement booking state-transition and financial-state guards in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/domain/` so failed payment attempts remain `PENDING_PAYMENT/UNPAID` while the hold is active and invalid jumps are rejected.
- [x] T009a Add attempt-level payment state handling in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/payments/` so `FAILED` is stored on `PaymentAttempt` only and booking retry remains available during the active hold.
- [x] T010 Implement transactional capacity-hold primitives in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/application/` and `infrastructure/`; lock the selected session, reserve `participant_count`, persist a 15-minute deadline and release each hold generation at most once.
- [x] T011 [P] Define the payment facade and VNPay adapter port in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/payments/facade/` and `infrastructure/`; model signed result, provider reference, amount/currency matching and provider-event idempotency without exposing OTP.
- [x] T012 [P] Add MySQL migration and repository integration tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/` for constraints, unique booking code, participant holds, immutable snapshots, production-data protection and the approved development/test reset/recreate path.
- [x] T013 Verify module boundaries with `ApplicationModules.verify()` and add/adjust the test under `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/` so controllers, entities, repositories and adapters are not accessed across module boundaries.

**Checkpoint**: Foundation is complete only when the migration, state guards,
capacity locking, payment facade and module-boundary tests pass.

## Phase 3: User Story 1 - Discover a workshop package (Priority: P1)

**Goal**: Guests and Members can browse published packages and view details.

**Independent Test**: Published package listing and details work without
creating a booking and unpublished packages cannot start a booking.

- [x] T014 [P] [US1] Add package/session read DTOs and validation in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/application/` for current price, currency, deposit basis, supported options and selectable sessions.
- [x] T015 [US1] Implement published-package and package-detail queries in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/application/WorkshopBookingService.java` using repositories/facades only and excluding unpublished or inactive packages.
- [x] T016 [US1] Implement REST endpoints from the contract in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/web/WorkshopPackageApiController.java` with versioned plural kebab-case paths and typed safe errors.
- [x] T017 [P] [US1] Implement Thymeleaf package listing/details pages and core links in `ppswbs_backend/src/main/resources/templates/workshop-bookings/` that remain usable without htmx/JavaScript.
- [x] T018 [P] [US1] Add package contract and integration tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/WorkshopPackageApiIT.java` for published filtering, details, unavailable package and safe not-found behavior.

**Checkpoint**: US1 can be demonstrated independently with published package
listing and details.

## Phase 4: User Story 2 - Create a pending workshop booking (Priority: P1)

**Goal**: A Guest or Member can submit valid booking details and obtain a
`PENDING_PAYMENT/UNPAID` booking with a 15-minute participant hold.

**Independent Test**: Valid submission creates one pending booking/invoice;
invalid package/session/capacity/contact input creates no customer booking.

- [x] T019 [P] [US2] Add create-booking DTOs in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/application/` requiring valid Guest email and phone, auto-filling Member defaults from the Member facade, allowing edited contact values for booking on behalf of another person, and excluding raw credential fields.
- [x] T020 [US2] Implement package/session/capacity/design revalidation and invoice snapshot calculation in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/application/WorkshopBookingService.java`; deposit uses the approved 50% baseline and preserves amount/currency snapshots.
- [x] T021 [US2] Implement atomic booking creation plus capacity hold in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/application/WorkshopBookingService.java`, including unique booking code, `PENDING_PAYMENT`, `UNPAID`, hold expiry and no incomplete customer-facing row on rollback.
- [x] T022 [US2] Implement the create-booking REST endpoint in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/web/WorkshopBookingApiController.java` and matching Thymeleaf form/fragment in `ppswbs_backend/src/main/resources/templates/workshop-bookings/`.
- [x] T023 [P] [US2] Add authorization tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/BookingAuthorizationIT.java` for Guest booking, Member profile use, ownership boundaries and safe validation errors.
- [x] T023a [P] [US2] Add Member-owned pending-payment authorization tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/BookingAuthorizationIT.java` proving an authenticated Member can retry only an owned active pending booking and cannot access another Member's booking.
- [x] T023b [P] [US2] Add contact validation tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/WorkshopBookingCreationIT.java` proving Guest email/phone are both required and Member profile values are auto-filled but editable and snapshotted.
- [x] T023c [P] [US2] Add Firebase identity and ownership tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/FirebaseIdentityAuthorizationIT.java` proving valid verified UID access, missing/expired/malformed token rejection, UID-to-Member mismatch rejection, and absence of raw token values in logs or audit records.
- [x] T024 [P] [US2] Add booking creation integration tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/WorkshopBookingCreationIT.java` for valid booking, unpublished package, unavailable session, missing capacity, insufficient capacity, invalid contact data and transaction rollback.
- [x] T025 [P] [US2] Add concurrency tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/CapacityHoldConcurrencyIT.java` proving two requests cannot oversubscribe the last participant capacity and expiry releases exactly one hold.

**Checkpoint**: US2 is an independently testable MVP booking creation slice.

## Phase 5: User Story 3 - Pay the workshop deposit (Priority: P1)

**Goal**: A pending booking can start/retry payment and becomes confirmed only
after one valid signed non-duplicate provider result.

**Independent Test**: Success confirms once and issues QR/notification intent;
failure, mismatch, duplicate, late and redirect-only paths never settle twice.

- [x] T026 [P] [US3] Add payment initiation DTOs and gateway request mapping in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/payments/` using exact booking amount/currency and a 10-minute provider link expiry.
- [x] T027 [US3] Implement start/retry payment in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/application/WorkshopBookingService.java`; permit retry only for active `PENDING_PAYMENT/UNPAID` holds and never capture or validate gateway OTP.
- [x] T028 [US3] Implement signed payment callback verification and idempotent application in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/payments/infrastructure/` and workshop application services; match signature, provider event, booking reference, amount, currency and state before confirming.
- [x] T029 [US3] Implement the payment initiation and callback endpoints in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/web/WorkshopPaymentApiController.java` with safe typed responses; browser Return URL must only display committed state.
- [x] T030 [P] [US3] Implement confirmed booking QR ticket and notification intent creation in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/application/` and mail adapter under `infrastructure/`; notification failure must not roll back confirmation.
- [x] T030a [P] [US3] Add notification recipient tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/WorkshopDepositPaymentIT.java` proving Guest and Member confirmations/Magic Links use the submitted contact email snapshot, including a Member booking for another person.
- [x] T030b [P] [US3] Add confirmation-email content tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/WorkshopDepositPaymentIT.java` proving the email contains both the QR Check-in ticket and protected Magic Link, without raw tokens or payment payloads.
- [x] T030c [P] [US3] Add QR payload security tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/WorkshopDepositPaymentIT.java` proving the ticket uses only a signed opaque reference and contains no PII, payment data, Magic Link token or secret.
- [x] T030d [P] [US3] Add QR lifecycle tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/WorkshopDepositPaymentIT.java` proving the ticket is valid before workshop end, rejected at/after workshop end, and can be revoked without changing payment evidence.
- [x] T030e [P] [US3] Add notification-resend tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/WorkshopDepositPaymentIT.java` proving resend reuses the same active QR/ticket and does not duplicate or revoke it.
- [x] T030f [P] [US3] Add notification-resend authorization/rate-limit tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/WorkshopBookingSecurityIT.java` proving Guest/Member proof, Staff/Owner authorization/audit, safe errors and abuse throttling.
- [x] T030g [P] [US3] Add notification delivery-failure tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/WorkshopDepositPaymentIT.java` proving confirmation remains committed, payment evidence remains settled, and automatic retry intent is retained without raw email/provider payloads.
- [x] T030h [P] [US3] Add notification retry-policy tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/WorkshopDepositPaymentIT.java` proving at most three backoff retries, then safe Staff/Owner follow-up without changing booking/payment state.
- [x] T031 [P] [US3] Add VNPay/payment adapter contract tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/payments/VnPayAdapterContractTest.java` for signed success, failure, cancellation, pending and unknown provider outcomes without OTP leakage.
- [x] T032 [P] [US3] Add payment integration tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/WorkshopDepositPaymentIT.java` for success, amount/currency/reference mismatch, invalid signature, duplicate callback, failed retry, browser redirect-only, expired hold and notification failure.
- [x] T033 [P] [US3] Add callback race/restart tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/payments/PaymentIdempotencyIT.java` proving one provider event applies at most once and late success cannot confirm an expired booking.
- [x] T033a [US3] Create the durable `ExternalRegistrationHandoff` record and post-confirmation handoff service in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/`, recording booking code, event identity, payload reference, status and retry metadata without blocking local confirmation or storing provider secrets.
- [x] T033b [P] [US3] Add handoff reliability tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/ExternalRegistrationHandoffIT.java` proving one handoff is created per confirmed booking event, duplicate confirmation is idempotent, handoff failure does not roll back booking/payment confirmation, and retry metadata is auditable and redacted.

**Checkpoint**: US3 is complete when a valid payment confirms the booking once
and all failure paths remain safe and retryable where specified.

## Phase 6: User Story 4 - Look up a booking (Priority: P2)

**Goal**: Guest or Member can retrieve an authorized booking summary and QR
ticket without exposing another customer's data.

**Independent Test**: Valid lookup returns the correct summary; malformed,
unknown or unauthorized lookup returns a safe result.

- [x] T034 [US4] Finalize and document Guest lookup proof as `booking_code + (normalized contact_phone OR normalized contact_email)`, Member ownership lookup, and protected post-payment Magic Link rules in `ppswbs-spec/features/002-workshop-booking/plan.md` and `contracts/workshop-booking.openapi.yaml` before endpoint implementation.
- [x] T035 [US4] Implement booking lookup DTO, ownership/lookup authorization and safe projection in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/application/` and `facade/`.
- [x] T036 [US4] Implement the lookup endpoint and Thymeleaf page in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/web/` and `ppswbs_backend/src/main/resources/templates/workshop-bookings/`.
- [x] T037 [P] [US4] Add lookup authorization and information-disclosure tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/BookingLookupIT.java` for valid Guest/Member access, wrong proof, unknown code and safe errors.
- [x] T037a [P] [US4] Add Magic Link security tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/BookingMagicLinkIT.java` for hashed storage, expiry, revocation, reuse prevention, read-only access and token/log redaction.
- [x] T037b [P] [US4] Add workshop-end expiry coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/BookingMagicLinkIT.java` proving a link is valid before the workshop end and rejected at/after the workshop end.
- [x] T037c [P] [US4] Add pending-versus-confirmed projection tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/BookingLookupIT.java` proving pending lookup shows countdown/resume-payment without QR and confirmed lookup shows QR/details without payment countdown.
- [x] T037d [US4] Add active pending-hold release behavior and tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/BookingLookupIT.java`; release must be idempotent, must not confirm or issue a ticket, and must append a redacted audit event with actor type/reference, required reason, booking code, outcome and correlation ID.
- [x] T037e [P] [US4] Add tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/BookingLookupIT.java` proving explicit pending-hold release changes status to `CANCELLED`, keeps financial status `UNPAID`, releases capacity once and cannot be retried for payment.
- [x] T037f [P] [US4] Add cancelled-booking view/UI tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/BookingLookupIT.java` and `ppswbs_backend/src/main/resources/templates/workshop-bookings/` proving no payment/QR action is shown and a new-workshop booking action is offered.

**Checkpoint**: US4 is independently testable after US2/US3 data exists.

## Phase 7: Polish and Cross-Cutting Verification

- [x] T038 [P] Update the authoritative API documentation in `ppswbs-spec/APIs.md` and link it to `contracts/workshop-booking.openapi.yaml` without exposing provider secrets or OTP details.
- [x] T039 [P] Add security/error-path checks for correlation IDs, safe errors, authorization, log redaction and absence of raw OTP/provider payloads in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/WorkshopBookingSecurityIT.java`.
- [x] T040 Run MySQL migration/upgrade verification and record exact command/result in the feature evidence; verify empty-database creation, approved development/test reset/recreate behavior and an explicit production/real-customer-data guard.
- [x] T041 Run `ApplicationModules.verify()` and all Workshop Booking tests; record any verification exception and manual alternative in the feature evidence.
- [x] T042 Run every scenario in `ppswbs-spec/features/002-workshop-booking/quickstart.md` and reconcile any failure before claiming completion.
- [x] T043 Run `speckit-analyze` against `spec.md`, `plan.md` and `tasks.md`; resolve critical coverage, ordering or constitution findings before `speckit-implement`.

## Dependencies and execution order

- Setup T001–T004 precedes all other work.
- Foundational T005–T013 blocks every user story.
- US1 T014–T018 can proceed after foundation.
- US2 T019–T025 depends on package/session reads from US1 and foundation.
- US3 T026–T033b depends on the booking/hold/invoice model from US2; T033b depends on T033a.
- US4 T034–T037 depends on the booking data from US2 and confirmed ticket behavior from US3.
- Polish T038–T043 depends on the desired user stories being complete.

## Parallel opportunities

- T001–T003 can run in parallel.
- T007, T008, T011 and T012 can run in parallel after the initial structure exists.
- T014, T017 and T018 can be split after foundation.
- T023–T025 can run in parallel after the create-booking contract is fixed.
- T026, T030 and T031 can run in parallel after payment/domain contracts are fixed.
- T037–T039 can run in parallel after endpoint behavior is stable.

Dependency note: T033b depends on T033a, and both handoff tasks must complete before the final verification gates.

## Implementation strategy

1. Finish setup and foundation; stop if migration, state or module gates fail.
2. Deliver US1 package discovery as the first independently demonstrable slice.
3. Deliver US2 as the MVP booking/hold slice and validate concurrency.
4. Deliver US3 payment confirmation and QR/notification behavior.
5. Deliver US4 lookup and disclosure controls.
6. Run polish, quickstart, migration, boundary and `speckit-analyze` gates.

No task in this file authorizes implementation before the user approves the
complete SpecKit artifact set.
