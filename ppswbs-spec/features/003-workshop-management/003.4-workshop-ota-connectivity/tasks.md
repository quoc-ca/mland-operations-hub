# Tasks: Workshop OTA Connectivity

**Date created**: 2026-10-24
**Date updated**: 2026-10-27
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Input**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [ota-connectivity-contract.md](./contracts/ota-connectivity-contract.md), [ota-connectivity.openapi.yaml](./contracts/ota-connectivity.openapi.yaml), and [quickstart.md](./quickstart.md)

**Prerequisites**: 003.1 Booking, 003.2 Operations, and 003.3 Settlement expose the facade operations assumed by the design. Provider onboarding evidence is a release gate, not an implementation shortcut.

**Tests**: Required. The feature specification and plan require module, migration, contract, authorization, callback authentication/redaction, replay/out-of-order, outage recovery, ticket, cancellation, and finance-separation coverage.

**Organization**: Tasks are grouped by user story. US2 is delivered before US1 because a mapping must be safely sales-closed/prepared before any booking-enabled behavior can exist; both stories remain P1.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Different files with no dependency on unfinished task.
- **[USn]**: User-story traceability label.
- Account Management supplies canonical role/status and location-scope facts; OTA Connectivity applies its own server-side action rules with the target canonical `workshop_location.id`/resource. No task may use UI visibility, Firebase claims or retired role codes as authorization.

## Phase 1: Setup and design-gate evidence

**Purpose**: Establish module paths, test fixtures, and the non-code evidence needed to distinguish a candidate channel from a booking-enabled channel.

- [ ] T001 Create the OTA module package skeleton (`facade`, `application`, `domain`, `persistence`, `adapter`, `api`, and `integration`) under `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/` and register its Modulith detection test location in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/`.
- [ ] T002 [P] Create OTA adapter simulator fixtures and sanitized representative callback payloads in `ppswbs_backend/src/test/resources/ota-connectivity/`; fixtures MUST contain no production secret, raw token, card data, or unnecessary PII.
- [ ] T003 [P] Create a provider-readiness evidence template in `ppswbs-spec/features/003-workshop-management/003.4-workshop-ota-connectivity/provider-readiness/README.md` covering contract/onboarding, callback authentication, quota, ticket/scan proof, cancellation/refund, payout, and late-booking/capacity responsibility.
- [ ] T004 Record the actual approved Klook and/or GetYourGuide readiness evidence in `ppswbs-spec/features/003-workshop-management/003.4-workshop-ota-connectivity/provider-readiness/<provider>.md` before requesting `BOOKING_ENABLED`; leave booking disabled when any evidence is absent.
- [ ] T005 [P] Document the approved feature-package/module-detection rule and the facade-only dependency rule in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/package-info.java`.

---

## Phase 2: Foundational safety and persistence

**Purpose**: Build the shared boundary, durable state, authorization, and observability that block every user story.

**⚠️ CRITICAL**: Complete this phase before enabling any story behavior. Do not create a provider-specific adapter that bypasses the neutral port or another feature's facade.

- [ ] T006 Inspect deployed Flyway history, select the next append-only migration identifier, then create the OTA migration in `ppswbs_backend/src/main/resources/db/migration/` for channel connection, mapping, price snapshot, outbound change, delivery attempt, inbound event, channel booking, booking-ticket link, customer-payment evidence, payout position/ledger entry, add-on bill link, and reconciliation work item; preserve `data-model.md` integrity rules, including provider-scoped uniqueness for external/event references and source-version change keys.
- [ ] T007 [P] Create JPA entities, enums, repositories, and migration test support for foundational mapping/change/event/reconciliation records in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/persistence/` and `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/persistence/`.
- [ ] T008 [P] Verify 003.1/003.2/003.3 facade compatibility and opaque identifier conversion against the upstream-facade section of `ppswbs-spec/features/003-workshop-management/003.4-workshop-ota-connectivity/contracts/ota-connectivity-contract.md`, then define the OTA public facade and adapter-neutral boundary types in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/{facade,adapter}/`; stop and report an upstream gap rather than access another feature's entity, repository, or service.
- [ ] T009 Implement server-side canonical account fact, location-scope and OTA action-rule authorization plus safe typed/correlation-linked error mapping in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/application/OtaAuthorizationService.java` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/api/OtaExceptionHandler.java`.
- [ ] T010 [P] Implement redacted integration audit logging and safe correlation generation in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/integration/OtaAuditCorrelationService.java`; exclude credentials, access tokens, signing secrets, raw sensitive bodies, payment cards, and avoidable customer data.
- [ ] T011 **DEPENDS ON 003.2 T053–T055**: Consume the persistent internal Spring Modulith OTA-observable publication from 003.2 in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/integration/` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/application/OtaSyncDeliveryService.java`; accept at-least-once replay idempotently, materialize one durable outbound change before any provider call, retain append-only delivery attempts, move only the affected mapping to sales-closed with reconciliation after retry exhaustion, and use no broker or provider call inside the Operations transaction.
- [ ] T012 Implement authenticated inbound-event receipt, provider-scoped deduplication, safe payload fingerprinting, and replay outcome storage in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/integration/OtaInboundEventService.java`.
- [ ] T013 Add module-boundary verification in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/OtaConnectivityModuleTest.java` and verify that other features can access OTA only through `facade/` boundary types.
- [ ] T014 [P] Add empty-database and supported-upgrade migration coverage for the append-only OTA migration selected after Flyway history inspection in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/persistence/OtaConnectivityMigrationIT.java`.
- [ ] T015 [P] Add authorization, location-isolation, typed-error/correlation, audit-redaction, and provider-data-allowlist rejection coverage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/security/OtaAuthorizationIT.java`; prove a non-allowlisted field reaches no entity, audit, error response, or frontend DTO.

**Checkpoint**: The module has durable, auditable foundation state; no channel is booking-enabled merely because a connection or mapping exists.

---

## Phase 3: User Story 2 — Manager publishes eligible channel inventory (Priority: P1)

**Goal**: An authorized Manager can prepare and synchronize Mland-owned inventory safely, while commercial/ticket gates and delivery failure keep OTA sales closed.

**Independent Test**: Create an approved preparation mapping, publish availability, then attempt Klook publication without commercial confirmation. The valid mapping gets a durable delivery request; the Klook mapping remains `PENDING_COMMERCIAL_CONFIRMATION`/sales-closed with audit evidence. Exhausted delivery retries close sale and create reconciliation work without capacity change.

### Tests for User Story 2

- [ ] T016 [P] [US2] Add REST contract/validation/error tests for channel listing, mapping creation, sync, sales-close, and reconciliation endpoints in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/api/OtaChannelControllerIT.java`.
- [ ] T017 [P] [US2] Add adapter-contract tests for schedule, availability, sales-close, sanitized delivery outcome, transient retry, and exhausted-retry behavior in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/adapter/OtaInventoryAdapterContractTest.java`.
- [ ] T018 [P] [US2] Add integration coverage for persistent 003.2 Spring Modulith publication replay after consumer failure/restart, automatic schedule/availability/sale-state synchronization, unified per-session source-version duplicate/older rejection, gap/uncertain-order suspension, authoritative one-session resync at the existing version, no capacity commitment during preparation mode, and reopen only after confirmed re-sync in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/application/OtaInventorySyncIT.java`.

### Implementation for User Story 2

- [ ] T019 [P] [US2] Implement channel connection, mapping, commercial-status, immutable price snapshot, provider-data allowlist, and opaque credential-reference records in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/persistence/`; enforce initial `PER_PERSON` eligibility, prevent unconfirmed Klook terms from leaving `PENDING_COMMERCIAL_CONFIRMATION`, and never persist a secret/token.
- [ ] T020 [US2] Implement mapping lifecycle rules (`DRAFT`, preparation/sales-closed, `BOOKING_ENABLED`, suspended, disabled) and provider-readiness validation in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/application/OtaChannelMappingService.java`; only written readiness evidence may request `BOOKING_ENABLED`.
- [ ] T021 [US2] Implement the provider-neutral inventory publication port plus per-provider preparation adapters and gated connection configuration in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/adapter/`; map only approved schedule, availability, sales state, price, and allowlisted operational fields, using only opaque credential references and provider-approved timeout/quota/auth values.
- [ ] T022 [US2] Complete accepted Operations snapshot handling in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/application/OtaSessionChangePublisher.java` after T011: ignore duplicate/older source versions, suspend only the affected mapping and request authoritative resync on a gap or uncertain order, and never read Operations entities/repositories or recalculate availability.
- [ ] T023 [US2] Implement Manager location-scoped sync/reconciliation and Admin connection/cross-location recovery use cases in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/application/OtaReconciliationService.java`; audit actor, canonical location, channel, scope, reason, and outcome.
- [ ] T024 [US2] Implement versioned REST DTOs/controllers under `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/api/` for `ota-channels`, `ota-channel-mappings`, mapping price terms, and `ota-reconciliation-work-items` exactly to `ppswbs-spec/features/003-workshop-management/003.4-workshop-ota-connectivity/contracts/ota-connectivity.openapi.yaml`, including safe loading, empty, validation, success, and failure response states.
- [ ] T025 [US2] After the approved React/Vite frontend baseline exists, implement the channel configuration/reconciliation API client and loading/empty/validation/success/error recovery views in `ppswbs_frontend/src/ota-connectivity/{api,pages}/`; do not scaffold the frontend implicitly, expose credentials, or replace backend authorization with route guards.

**Checkpoint**: A candidate OTA may be prepared and synchronized only sales-closed. Failed delivery is visible and safe; no prepared mapping accepts an OTA booking.

---

## Phase 4: User Story 1 — OTA guest secures workshop places (Priority: P1)

**Goal**: A provider-approved booking creates one Mland booking, reserves capacity, and issues exactly one active Mland ticket per participant exactly once.

**Independent Test**: For an evidence-approved `PER_PERSON` mapping, submit the same authenticated provider booking for `N` participants twice. Exactly one channel booking, one Mland booking/capacity commitment, and `N` ticket links exist; insufficient capacity, disabled mappings, and shared-voucher-only options persist no change.

### Tests for User Story 1

- [ ] T026 [P] [US1] Add authenticated callback contract tests for booking request validation, safe acknowledgement/error, and no credential/raw-payload leakage in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/api/OtaProviderEventControllerIT.java`.
- [ ] T027 [P] [US1] Add booking idempotency/concurrency integration tests for duplicate external reference, idempotency key, competing final capacity, disabled mapping, and `N`-participant ticket issuance in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/application/OtaBookingIT.java`.
- [ ] T028 [P] [US1] Add per-provider adapter tests proving a shared voucher cannot satisfy the booking-enabled ticket/scan gate in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/adapter/OtaTicketDeliveryGateTest.java`.

### Implementation for User Story 1

- [ ] T029 [P] [US1] Implement channel booking, booking-ticket link, provider-event correlation, and immutable booking snapshot persistence in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/persistence/`; snapshot sales channel, displayed policy/version, booking cutoff, cutoff timezone, retail/target-net amounts, commission/fee facts, currency, accepted amounts, and external correlation; enforce unique provider + external booking reference and one ticket link per participant entitlement.
- [ ] T030 [US1] Implement booking-enable enforcement in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/application/OtaBookingReadinessService.java`; require all provider readiness evidence, `PER_PERSON`, individual ticket/QR and scan proof, and evidence that provider acknowledgement/retry can honor Mland's reject-without-capacity late-booking rule before accepting any booking.
- [ ] T031 [US1] Implement authenticated provider callback adaptation and idempotent booking orchestration in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/integration/OtaProviderEventHandler.java`; use only the verified 003.1/003.2 facade semantics from the upstream-facade section of `contracts/ota-connectivity-contract.md` to obtain/retain one booking plus `N` active tickets for `N` participants, and return the prior persisted result on replay.
- [ ] T032 [US1] Implement the versioned provider-event controller and safe correlation-linked acknowledgement in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/api/OtaProviderEventController.java`; provider authentication is separate from a Mland user session.
- [ ] T033 [US1] Implement authorized, audited shared-voucher incident fallback handling in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/application/OtaVoucherFallbackService.java`; prohibit automatic whole-booking check-in and keep the normal option disabled where individual ticket proof is absent.

**Checkpoint**: Booking works only for a readiness-approved mapping and preserves capacity/ticket integrity under replay and contention.

---

## Phase 5: User Story 3 — OTA cancellation/refund protects inventory (Priority: P1)

**Goal**: Authenticated cancellation/refund events record requests separately from confirmed outcomes, releasing eligible inventory exactly once only after confirmation.

**Independent Test**: Send cancellation requested, duplicate requested, confirmed cancellation/refund, invalid callback, late callback, and out-of-order callback. Only the confirmed valid outcome revokes applicable unused tickets/releases eligible capacity once; all other events are safely recorded or reconciled.

### Tests for User Story 3

- [ ] T034 [P] [US3] Add cancellation/refund lifecycle, duplicate, late, and out-of-order event tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/application/OtaCancellationIT.java`.
- [ ] T035 [P] [US3] Add callback authentication/rejection and reconciliation-queue coverage for invalid provider events in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/integration/OtaCancellationCallbackSecurityIT.java`.

### Implementation for User Story 3

- [ ] T036 [US3] Implement cancellation-request versus confirmed-cancellation/refund state handling and safe reconciliation routing in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/application/OtaCancellationService.java`.
- [ ] T037 [US3] Integrate confirmed outcomes with Operations only through the verified cancellation facade semantics in the upstream-facade section of `contracts/ota-connectivity-contract.md` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/application/OtaCancellationConsequenceService.java`; revoke only applicable unused tickets and release eligible future capacity exactly once.
- [ ] T038 [US3] Extend provider adapter mappings for contract-approved cancellation/refund events in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/adapter/`; do not infer provider cancellation policy before it is evidenced.

**Checkpoint**: A request never releases inventory; an authenticated confirmed outcome causes one auditable consequence.

---

## Phase 6: User Story 4 — Finance reconciles channel revenue (Priority: P2)

**Goal**: Finance sees channel guest payment evidence, Mland receivable, actual payout, and adjustments as separate immutable histories.

**Independent Test**: Reconcile a booking's expected net amount, statement, payout, refund, and variance. The payout position reaches `PAID_OUT` only when a payout reference matches, and no prior gross/fee/refund evidence is rewritten.

### Tests for User Story 4

- [ ] T039 [P] [US4] Add payout lifecycle and append-only ledger integration tests for `NOT_ELIGIBLE` -> `ACCRUED` -> `INVOICED` -> `PAYOUT_PENDING` -> `PAID_OUT` in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/application/OtaPayoutReconciliationIT.java`.
- [ ] T040 [P] [US4] Add customer-payment/refund versus payout/adjustment separation tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/application/OtaFinancialEvidenceIT.java`.

### Implementation for User Story 4

- [ ] T041 [P] [US4] Implement customer payment evidence with exactly `PAID_ON_CHANNEL`, `REFUND_REQUESTED`, `PARTIALLY_REFUNDED`, and `REFUNDED` states in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/persistence/`; do not treat it as direct-Mland payment, booking, or payout state.
- [ ] T042 [P] [US4] Implement immutable payout-position and reconciliation-ledger persistence in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/persistence/`; retain gross sale, commission/fee, customer refund, expected/received payout, FX variance, compensation, and adjustment with amount, currency, time, reference, reason, and related record.
- [ ] T043 [US4] Implement payout transition, statement matching, adjustment/reversal, and dispute workflows in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/application/OtaPayoutReconciliationService.java`; corrections append linked evidence and never overwrite history.
- [ ] T044 [US4] Implement authorized finance/reconciliation DTOs and REST endpoints exactly to `contracts/ota-connectivity.openapi.yaml` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/api/`; after the approved React/Vite baseline exists, implement the corresponding state view in `ppswbs_frontend/src/ota-connectivity/pages/OtaFinanceReconciliationPage.tsx`; redact sensitive integration/customer data.

**Checkpoint**: Finance can explain guest payment, receivable, payout, and direct counter sale without one ambiguous “paid” field.

---

## Phase 7: User Story 5 — Staff sells an OTA add-on transparently (Priority: P2)

**Goal**: Staff can record a free engraving or a consented paid option without mutating OTA commercial or payout facts.

**Independent Test**: Record laser engraving and verify zero charge. Attempt a paid option without acceptance and verify rejection; then accept a quote and create a separate 003.3 bill containing immutable item/price/currency/consent/actor/payment evidence with no OTA finance mutation.

### Tests for User Story 5

- [ ] T045 [P] [US5] Add free-engraving, missing-consent rejection, and paid-add-on separation tests in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/application/OtaAddonBillingIT.java`.
- [ ] T046 [P] [US5] Add authorization and Settlement-facade contract tests for OTA add-on bill creation in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/facade/OtaAddonSettlementContractTest.java`.

### Implementation for User Story 5

- [ ] T047 [US5] Implement add-on quote/acceptance correlation and direct-bill-link persistence exactly to `contracts/ota-connectivity.openapi.yaml` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/persistence/`; `LASER_ENGRAVING` accepts only a zero amount and `PAID_OPTION` requires prior acceptance before a direct bill.
- [ ] T048 [US5] Implement the authorized add-on workflow in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/application/OtaAddonService.java`; use only the verified 003.3 Settlement facade semantics in the upstream-facade section of `contracts/ota-connectivity-contract.md` for the direct-Mland bill and preserve immutable item, price, currency, consent, actor, and payment-reference evidence without changing OTA price, channel payment, commission, payout, settlement, or channel refund.
- [ ] T049 [US5] Implement add-on DTOs/endpoints exactly to `contracts/ota-connectivity.openapi.yaml` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/otaconnectivity/api/`; after the approved React/Vite baseline exists, implement quote/acceptance/loading/error recovery UI in `ppswbs_frontend/src/ota-connectivity/pages/OtaAddonPage.tsx`.

**Checkpoint**: Direct add-ons are transparently consented and financially isolated from the OTA sale.

---

## Phase 8: Polish, cross-cutting verification, and go-live control

**Purpose**: Verify the integrated module and prevent a successful demo from being mistaken for a provider production approval.

- [ ] T050 [P] Execute persistent-publication replay/restart, preparation-sync, retry-exhaustion, confirmed-resync, and authorization scenarios from `ppswbs-spec/features/003-workshop-management/003.4-workshop-ota-connectivity/quickstart.md` and attach safe evidence references to `ppswbs-spec/features/003-workshop-management/003.4-workshop-ota-connectivity/provider-readiness/<provider>.md`.
- [ ] T051 [P] **BLOCKED — provider readiness/onboarding** Execute provider-sandbox booking, individual ticket/QR delivery, and scan evidence for each provider requested for enablement, recording results in `ppswbs-spec/features/003-workshop-management/003.4-workshop-ota-connectivity/provider-readiness/<provider>.md`; do not enable a provider when the evidence is incomplete.
- [ ] T052 Run `ApplicationModules.verify()` and the OTA module, migration, contract, authorization, callback, replay, outage, cancellation, and finance test suites from `ppswbs_backend/`; record any failed/inapplicable check and manual alternative in `ppswbs-spec/features/003-workshop-management/003.4-workshop-ota-connectivity/provider-readiness/verification-log.md`.
- [ ] T053 Verify generated/published REST documentation and backend API behavior against `ppswbs-spec/features/003-workshop-management/003.4-workshop-ota-connectivity/contracts/ota-connectivity.openapi.yaml`; verify frontend integration only if its approved baseline exists, without exposing secrets, raw provider data, or JPA entities.
- [ ] T054 Perform an authorized go-live review per provider using `ppswbs-spec/features/003-workshop-management/003.4-workshop-ota-connectivity/provider-readiness/<provider>.md`; keep the mapping sales-closed if commercial, callback, ticket/scan, cancellation/refund, payout, or late-booking/capacity evidence is missing.

---

## Dependencies and execution order

### Phase dependencies

- **Phase 1** can start immediately. T004 is a hard gate for a real provider's booking enablement, but it does not block simulator-backed preparation synchronization.
- **Phase 2** depends on T001 and blocks all story implementation. T008 must verify the upstream facade contract before any booking, cancellation consequence, or direct-bill integration. T011 follows 003.2 T053–T055, which implement the already-approved persistent internal Spring Modulith publication.
- **US2 (Phase 3)** depends on Phase 2 and is the dependency for US1 because it establishes mapping/readiness/sales-closed behavior.
- **US1 (Phase 4)** depends on Phase 2, US2 lifecycle/readiness work, and T004 for the specific provider that will accept bookings.
- **US3 (Phase 5)** depends on US1 channel-booking/event correlation.
- **US4 (Phase 6)** depends on US1 channel booking and price snapshots; it can proceed in parallel with US3 after US1.
- **US5 (Phase 7)** depends on US1 booking correlation and the verified 003.3 Settlement facade; it can proceed in parallel with US3/US4 once those boundaries are available.
- **Phase 8** depends on the desired story scope and provider evidence. T054 is a release decision, never an automatic implementation outcome.

### User-story completion order

`Foundation → US2 preparation sync → US1 booking (per-provider gate) → {US3 cancellation, US4 finance, US5 add-ons} → cross-cutting verification/go-live review`.

## Parallel opportunities

- T002, T003, and T005 can run in parallel after T001.
- T007, T008, T009, and T010 can run in parallel after T006's migration design is agreed; T011/T012 then consume that foundation, with T011 following 003.2 T053–T055.
- Within US2, T016–T018 and T019 can run in parallel; T020–T025 follow the lifecycle boundary.
- Within US1, T026–T029 can run in parallel; T030–T033 follow readiness/persistence work.
- US3, US4, and US5 can be staffed in parallel after their stated prerequisites, with ownership coordination for shared controller/dto files.

## Parallel example: preparation synchronization

```text
Task: T016 contract/validation tests in .../api/OtaChannelControllerIT.java
Task: T017 adapter contract tests in .../adapter/OtaInventoryAdapterContractTest.java
Task: T018 sync recovery integration tests in .../application/OtaInventorySyncIT.java
Task: T019 mapping and price snapshot persistence in .../persistence/
```

## Implementation strategy

### MVP: safe preparation synchronization

1. Complete Phases 1 and 2.
2. Complete US2 through sales-closed preparation sync, failure closure, and reconciliation.
3. Validate US2 independently using simulator evidence.
4. Do **not** call this an OTA booking launch. Booking is a second increment gated by T004 and T030.

### Incremental delivery

1. Add a provider-neutral readiness/mapping/sync foundation.
2. Add booking only for a provider with complete written and sandbox evidence.
3. Add cancellation consequences and finance reconciliation as separate, auditable increments.
4. Add direct-Mland add-ons through 003.3 without contaminating OTA finance.
5. Perform the per-provider go-live review; a passing test suite alone does not replace a contract/onboarding decision.

## Traceability and risk review

| Requirement area | Primary tasks | Verification |
| --- | --- | --- |
| Mapping/gates/sync and authorization (FR-001–005, FR-017, FR-021–022) | T006–T025 | T013–T018, T050, T052 |
| Persistent Operations handoff/version/resync (FR-004a1–004g) | T011, T018, T022 | T018, T050, T052 |
| Booking, ticket proof, idempotency, and callback safety (FR-006–010) | T026–T033 | T026–T028, T051–T052 |
| Cancellation/refund effects (FR-011) | T034–T038 | T034–T035, T052 |
| Payment/payout and immutable reconciliation (FR-012–016) | T039–T044 | T039–T040, T052 |
| Data minimization and add-ons (FR-018–020) | T010, T045–T049 | T015, T045–T046, T052 |

**Critical review**: The unsafe shortcut is to treat a provider adapter or a green sandbox request as production readiness. It ignores stale inventory, replayed callbacks, individual ticket scan proof, and contractual late-booking liability. This task list therefore makes sales-closed preparation synchronization the MVP, and makes provider evidence a required, auditable input to booking enablement rather than a checkbox an implementer can infer.
