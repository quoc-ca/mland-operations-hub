---

description: "Dependency-ordered implementation tasks for FE-10"

---

# Tasks: Manager Dashboard

**Input**: Design documents from `features/010-manager-dashboard/`

**Prerequisites**: [spec.md](spec.md), [plan.md](plan.md),
[research.md](research.md), [data-model.md](data-model.md),
[quickstart.md](quickstart.md), and [contracts/](contracts/)

**Tests**: Verification tasks are included because the specification and project
constitution require evidence for authorization, public API behavior, KPI
formulas, source quality, read-only behavior, and Modulith boundaries.

**Path convention**: Backend and `documents/` paths are relative to the
repository root. Feature-document paths are relative to `ppswbs-spec/`.

**Implementation gate**: Generating this task list does not implement approved
capability/assignment enforcement, stable branch identity, or source lifecycles. T001 and T002 must be
completed with approved evidence before production implementation starts.

## Phase 1: Setup and decision synchronization

**Purpose**: Resolve the two decisions that code must not invent and align the
project baseline with the approved Manager-only FE-10 scope.

- [ ] T001 Implement and evidence the approved `REPORTING_EXECUTIVE_VIEW` capability plus active MySQL `account_location_assignments` enforcement in the authorization owner; deny by default when either is missing, and do not grant the capability automatically to Admin parameter authority.
- [ ] T002 Implement and evidence the approved immutable `locationId: Long` across active `account_location_assignments`, retail orders, workshop sessions/final invoices, and check-ins; current workshop location text is display-only and must not be used for authorization or aggregation.
- [x] T003 [P] Correct the `Executive dashboard` row and related footnotes in `documents/docs/report-3-software-requirement-specification/sections/03-i-overall-requirements/04-user-requirements/04-permission-matrix.md` to `No` for Guest, Member, Staff, and Admin and `Restricted` for branch-scoped Manager access, without removing FE-09 Admin parameter-impact evidence.
- [x] T004 [P] Register the Manager-only `GET /api/v1/executive-reports` contract and its current implementation maturity in `ppswbs-spec/APIs.md`, referencing `ppswbs-spec/features/010-manager-dashboard/contracts/manager-dashboard.openapi.yaml` and distinguishing FE-10 from FE-09 parameter-impact reporting.

**Checkpoint**: Role/capability mapping and stable branch identity are approved,
and the baseline no longer grants Admin or Staff FE-10 revenue access.

---

## Phase 2: Foundational source and module boundaries

**Purpose**: Establish the closed reporting module, minimal source-facade
contracts, authorization boundary, shared clock, and safe error contract needed
by every user story.

**CRITICAL**: No user-story production implementation should start until this
phase and its source dependencies are complete. Test doubles may be used only
for contract/unit work and must not be treated as production reporting data.

- [ ] T005 Create the closed reporting module declaration with allowed dependencies limited to `accountmanagement::facade`, `readyringsales::facade`, `workshopbooking::facade`, and `operations::facade` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/package-info.java`.
- [ ] T006 [P] Define `ManagerReportingAccessFacade` and immutable `ManagerReportingScope` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/accountmanagement/facade/ManagerReportingAccessFacade.java` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/accountmanagement/facade/ManagerReportingScope.java`, exposing only active Manager capability and authorized `locationId: Long` values for a verified UID.
- [ ] T007 [P] Define `RetailReportingFacade` and immutable `RetailBranchAggregate` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/facade/RetailReportingFacade.java` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/facade/RetailBranchAggregate.java`; the contract must return product value after allocated discounts, exclude shipping, count each fully paid branch order once, and select by verified full-payment time.
- [ ] T008 [P] Define `WorkshopReportingFacade`, `WorkshopRevenueAggregate`, and `WorkshopCapacityAggregate` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/facade/WorkshopReportingFacade.java`, `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/facade/WorkshopRevenueAggregate.java`, and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/facade/WorkshopCapacityAggregate.java`; count the final invoice total once at `fullyPaidAt`, never add the deposit again, and include only completed-session capacity.
- [ ] T009 [P] Define `AttendanceReportingFacade` and immutable `AttendanceAggregate` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/operations/facade/AttendanceReportingFacade.java` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/operations/facade/AttendanceAggregate.java`, exposing accepted idempotent check-ins for completed sessions rather than bookings or reservations.
- [ ] T010 [P] Add an injectable UTC `Clock` and the fixed business zone `Asia/Ho_Chi_Minh` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/infrastructure/ReportingTimeConfiguration.java` without reading the server-default timezone.
- [ ] T011 [P] Add reporting-specific typed exceptions and contract-compatible safe error mapping in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/web/ReportingExceptionHandler.java` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/web/ExecutiveReportErrorResponse.java`, preserving correlation IDs and exposing no stack trace, SQL detail, UID, branch assignment, or source payload.
- [ ] T012 Implement `ManagerReportingAccessService` from the approved T001/T002 decisions in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/accountmanagement/application/ManagerReportingAccessService.java`; resolve active account, Manager capability, and authorized location IDs from MySQL after Firebase identity verification and never from Firebase/email/provider claims.
- [ ] T013 Implement the FE-08-owned aggregate adapter behind `RetailReportingFacade` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/application/RetailReportingService.java` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/readyringsales/infrastructure/RetailReportingRepository.java` only after paid branch-order facts exist; use JPA projections and do not infer payment validity inside FE-10.
- [ ] T014 Implement the workshop-owned aggregate adapter behind `WorkshopReportingFacade` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/application/WorkshopReportingService.java` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking/infrastructure/WorkshopReportingRepository.java` only after the approved final-invoice/`fullyPaidAt` and stable-branch facts exist; do not reconstruct final revenue from deposit attempts.
- [ ] T015 Implement the operations-owned aggregate adapter behind `AttendanceReportingFacade` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/operations/application/AttendanceReportingService.java` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/operations/infrastructure/AttendanceReportingRepository.java` only after accepted check-in facts exist; keep raw participant records inside `operations`.
- [ ] T016 Extend module-boundary coverage for the new reporting dependencies and source named interfaces in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/ModulithArchitectureTests.java`, proving `reporting` cannot access source controllers, entities, repositories, or internal DTOs.

**Checkpoint**: FE-10 can authorize a Manager and consume minimized aggregate
contracts without cross-module repository access or guessed source facts.

---

## Phase 3: User Story 1 - View Executive Manager Dashboard (Priority: P1)

**Goal**: An authenticated authorized Manager can open one read-only dashboard
with the five required KPI cards, active period/branch scope, and no
operational-list navigation or unrelated business metrics.

**Independent Test**: Open the page as Manager, Member, Staff, Admin, a locked
account, and a Manager with limited branches; verify only the valid Manager
sees the dashboard and its five KPI cards while no business record changes.

### Tests for User Story 1

- [ ] T017 [P] [US1] Write failing authorization and branch-scope tests for Manager allow, unauthenticated `401`, Member/Staff/Admin/locked-account `403`, and FE-09 parameter-impact permission denial in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/reporting/ManagerDashboardAccessTest.java`.
- [ ] T018 [P] [US1] Write React UI tests for exactly five KPI cards, scope notices, filter entry, loading state, and the absence of operational-list navigation in `ppswbs_frontend/src/features/manager-dashboard/pages/ManagerDashboardPage.test.tsx`.

### Implementation for User Story 1

- [ ] T019 [P] [US1] Create immutable `AuthorizedBranchScope` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/domain/AuthorizedBranchScope.java`, containing only the approved capability and stable authorized branch identifiers; KPI values remain in the dashboard projection from T027.
- [ ] T020 [US1] Implement early capability and complete-set branch authorization in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/application/ManagerDashboardAccessService.java`, rejecting any explicitly requested unauthorized branch instead of silently removing it and calling no metric facade before authorization succeeds.
- [ ] T021 [US1] Add authenticated protection for `/api/v1/executive-reports` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/configuration/SecurityConfiguration.java` and implement no rendered application route; business authorization must still be enforced through T020 rather than URL rules alone, and the API route's authentication entry point must emit the T011 contract-compatible safe `401` response.
- [ ] T022 [US1] Implement the React dashboard shell in `ppswbs_frontend/src/features/manager-dashboard/pages/ManagerDashboardPage.tsx` and `components/ExecutiveKpiCards.tsx`, showing the five required KPI-card placeholders, Manager-only access, active branch scope, and no sample values or operational-list links.
- [ ] T023 [US1] Add Manager-dashboard labels and safe access/scope messages in `ppswbs_frontend/src/features/manager-dashboard/` using the approved frontend localization approach, then complete T017-T018 with assertions that viewing/filtering leaves source records unchanged.

**Checkpoint**: The secure read-only shell proves the one-dashboard layout and
authorization boundary. It does not claim KPI values until the source gates and
the following P1 composition slice are complete.

---

## Phase 4: User Story 1 continuation - Compose KPI data and filters (Priority: P1)

**Goal**: A Manager can select an allowed day, Monday-through-Sunday week,
month, or year and permitted branches, then view the five required verified
KPIs as consolidated totals and branch rows.

**Independent Test**: Given fixed retail, final workshop invoice, completed
session capacity, and check-in aggregates, compare all five KPI values and
period boundaries with the source facts, including discounts, shipping
exclusion, deposit non-duplication, weighted utilization, and branch scope.

### Tests for User Story 1 continuation

- [ ] T024 [P] [US1] Write failing fixed-clock tests for day/week/month/year ranges, Monday week start, adjacent half-open boundaries, current-period truncation, and default current-month-through-now behavior in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/reporting/ReportingPeriodResolverTest.java`.
- [ ] T025 [P] [US1] Write failing KPI composition tests for retail net revenue, shipping exclusion, final workshop invoice/deposit non-duplication, VND-only revenue, non-VND partial results, paid branch-order count, weighted `sum(check-ins) / sum(capacity) * 100`, and zero capacity in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/reporting/KpiComposerTest.java`.
- [ ] T026 [P] [US1] Write failing source-facade integration tests for verified full-payment time, branch attribution after allocated discounts, final workshop `fullyPaidAt`, completed-session/cancelled-session filtering, and accepted check-ins in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/readyringsales/RetailReportingFacadeTest.java`, `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/workshopbooking/WorkshopReportingFacadeTest.java`, and `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/operations/AttendanceReportingFacadeTest.java`.

### Implementation for User Story 1 continuation

- [ ] T027 [P] [US1] Create `PeriodType`, `ReportingPeriod`, `KpiValues`, `BranchKpi`, and `DashboardSnapshot` immutable value objects in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/domain/PeriodType.java`, `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/domain/ReportingPeriod.java`, `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/domain/KpiValues.java`, `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/domain/BranchKpi.java`, and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/domain/DashboardSnapshot.java`; monetary/count values are non-negative, API revenue fields serialize as decimal strings, unknown values remain nullable, and branch display names are never authorization keys.
- [ ] T028 [US1] Implement `ReportingPeriodResolver` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/application/ReportingPeriodResolver.java`, converting `Asia/Ho_Chi_Minh` calendar periods to `[startInclusive, endExclusive)` instants and rejecting custom ranges, malformed dates, and future-only periods with `INVALID_REPORTING_PERIOD`.
- [ ] T029 [US1] Implement source orchestration and KPI composition in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/application/ExecutiveReportQueryService.java` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/application/KpiComposer.java`; pass one authorized branch set and interval to every facade, accept VND monetary facts only, mark a non-VND source partial without conversion, and compute consolidated utilization from summed numerator/denominator rather than branch percentages.
- [ ] T030 [P] [US1] Create explicit request/response DTOs and mapping for the OpenAPI fields in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/web/ExecutiveReportQuery.java`, `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/web/ExecutiveReportResponse.java`, and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/web/ExecutiveReportMapper.java`, including decimal-string revenue values, and returning no source entity, customer/order/payment detail, or raw attendance data.
- [ ] T031 [US1] Implement the Manager-only `GET /api/v1/executive-reports` endpoint from `ppswbs-spec/features/010-manager-dashboard/contracts/manager-dashboard.openapi.yaml` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/web/ExecutiveReportApiController.java`, with Bean Validation for `periodType`, `anchorDate`, and repeated unique `locationId` values.
- [ ] T032 [US1] Implement the React period/branch filter and executive report components in `ppswbs_frontend/src/features/manager-dashboard/pages/ManagerDashboardPage.tsx`, `components/ExecutiveReportFilters.tsx`, and `components/ExecutiveReportView.tsx`, showing the active period, exact branch scope, currency, exactly five KPI cards, and separate branch rows.
- [ ] T033 [US1] Complete contract/authorization tests for complete and empty `200`, invalid period `400`, unauthenticated `401`, Manager/branch `403`, response DTO shape, and correlation-linked errors in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/reporting/ExecutiveReportApiTest.java`.
- [ ] T034 [US1] Complete end-to-end formula and boundary scenarios QS-05 through QS-11 from `quickstart.md` in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/reporting/ExecutiveRevenueJourneyTest.java`, including 120,000,000 + 45,000,000 = 165,000,000 VND, 12,000,000 final workshop revenue without adding its 5,000,000 deposit, 8,000,000 retail revenue after discount with shipping excluded, and weighted 28/50 = 56% utilization.
- [ ] T035 [US1] Complete React filter/UI tests for all four period types, authorized branch subsets, active-scope display, exactly five KPI cards, absence of a custom date-range control, and loading/empty/validation/error states in `ppswbs_frontend/src/features/manager-dashboard/pages/ManagerDashboardPage.test.tsx`.

**Checkpoint**: US1 provides the first business-value release: an authorized
Manager can obtain complete or valid-empty executive KPIs for an exact calendar
period and branch scope.

---

## Phase 5: User Story 3 - Understand data quality and refresh results (Priority: P2)

**Goal**: A Manager can distinguish complete, empty, unavailable, and partial
data, see the last successful update time, retry explicitly, and retain
the last valid rendered view when a filter or refresh fails.

**Independent Test**: Inject each source-quality/failure combination and verify
known zero versus unknown null, partial totals, all-source `503`, safe notices,
stable last-updated time without interaction, explicit refresh, no timer, and
no business-data mutation.

### Tests for User Story 3

- [ ] T036 [P] [US3] Write failing unit tests for `COMPLETE`, `EMPTY`, and `UNAVAILABLE` source combinations, non-VND source facts, zero denominator, nullable affected values, partial total suppression, verified-payment refresh visibility, and all-required-source failure in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/reporting/ReportSourceFailurePolicyTest.java`.
- [ ] T037 [P] [US3] Write React/API-state tests for last-valid-view preservation, explicit retry/refresh, unchanged last-updated time without interaction, rejected/unavailable response handling, and no timer-triggered endpoint call in `ppswbs_frontend/src/features/manager-dashboard/pages/ManagerDashboardRefresh.test.tsx`.

### Implementation for User Story 3

- [ ] T038 [P] [US3] Create `SourceName`, `SourceStatus`, and `SourceQuality` value objects in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/domain/SourceName.java`, `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/domain/SourceStatus.java`, and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/domain/SourceQuality.java`; `EMPTY` means successfully evaluated with no facts, while `UNAVAILABLE` means unknown and must never become zero; dashboard refreshes read only committed verified source facts.
- [ ] T039 [US3] Implement `ReportSourceFailurePolicy` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/application/ReportSourceFailurePolicy.java` and integrate it into `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/application/ExecutiveReportQueryService.java`, producing `PARTIAL` with null affected metrics and null consolidated revenue when a required revenue component is unknown, and throwing typed `REPORTING_SOURCES_UNAVAILABLE` only when all required sources are unavailable.
- [ ] T040 [US3] Extend `ExecutiveReportResponse` and mapping with overall quality, four source-quality entries, safe reason codes, source `asOf`, and `lastUpdatedAt` in `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/web/ExecutiveReportResponse.java` and `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/web/ExecutiveReportMapper.java`, rendering update time in Vietnam time while preserving an instant in the REST contract.
- [ ] T041 [US3] Implement safe quality notices, explicit Refresh, stable non-polling behavior, and failed-response preservation in `ppswbs_frontend/src/features/manager-dashboard/pages/ManagerDashboardPage.tsx` and `components/SourceQualityNotice.tsx`; every reload uses the REST response and re-authorizes any previous scope server-side.
- [ ] T042 [US3] Complete partial/empty/unavailable/VND-quality API and UI scenarios QS-11 through QS-19 in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/reporting/ExecutiveReportDataQualityTest.java`, asserting that safe errors contain correlation IDs but no stack trace, SQL, UID, PII, or internal source payload.
- [ ] T043 [US3] Verify read-only and refresh behavior across all source modules in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/reporting/ExecutiveReportReadOnlyTest.java`, proving initial load, filter, rejected filter, retry, and explicit refresh create or update no order, payment, workshop, check-in, configuration, or accounting record.

**Checkpoint**: All three stories are independently testable, and Managers can
distinguish trustworthy zero values from incomplete or unavailable data.

---

## Phase 6: Polish and cross-cutting verification

**Purpose**: Complete contract, security, architecture, bounded-query, and
end-to-end evidence without expanding FE-10 into accounting, exports, polling,
scheduled aggregation, or Admin analytics.

- [ ] T044 [P] Validate and synchronize the implemented DTOs, status codes, nullable quality behavior, and security responses against `ppswbs-spec/features/010-manager-dashboard/contracts/manager-dashboard.openapi.yaml` and the catalog entry in `ppswbs-spec/APIs.md` without weakening existing API error compatibility.
- [ ] T045 [P] Add complete English and Vietnamese labels for period types, KPI names, empty/partial/unavailable notices, last-updated time, validation, and retry guidance using the approved frontend localization approach in `ppswbs_frontend/src/features/manager-dashboard/`.
- [ ] T046 Verify bounded source aggregation and absence of raw transaction transfer/N+1 branch reads in `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/reporting/ReportingQueryBoundaryTest.java`; record measured query/timing evidence without inventing an unapproved latency target.
- [ ] T047 Run and resolve Spring Modulith `ApplicationModules.verify()` through `ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/ModulithArchitectureTests.java` without broadening named interfaces or moving business logic into `common`.
- [ ] T048 Perform a final security/privacy review of `ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/reporting/` and `ppswbs_frontend/src/features/manager-dashboard/`, confirming Admin parameter-impact permission cannot reach FE-10 and no customer identity, recipient detail, order line, payment detail, raw attendance record, token, or provider payload is returned or logged.
- [ ] T049 Run `mvnw.cmd test` and `mvnw.cmd verify` from `ppswbs_backend/`, execute every applicable scenario in `ppswbs-spec/features/010-manager-dashboard/quickstart.md`, and record actual commands, results, source-gate status, and any verification exception in `ppswbs-spec/features/010-manager-dashboard/verification.md`.
- [ ] T050 Re-run cross-artifact consistency review for `ppswbs-spec/features/010-manager-dashboard/spec.md`, `ppswbs-spec/features/010-manager-dashboard/plan.md`, and `ppswbs-spec/features/010-manager-dashboard/tasks.md`, then record unresolved dependencies and production-enable readiness in `ppswbs-spec/features/010-manager-dashboard/verification.md` without marking missing source gates complete.

---

## Dependencies and execution order

### Phase dependencies

- **Phase 1**: Starts immediately. T001 and T002 are decision gates; T003 and
  T004 can proceed in parallel as documentation alignment.
- **Phase 2**: Depends on approved T001/T002 and blocks production story work.
  Facade interface/unit work may use test doubles, but T013-T015 need real
  source-owned facts before production completion.
- **US1**: Its shell depends on T005-T012. Complete KPI behavior depends on
  T007-T015 and must not use guessed deposits, booking counts, free-text
  locations, or unverified retail payments. Test doubles are permitted only for
  contract/unit work, never as production KPI evidence.
- **US3**: Depends on the US1 snapshot and API/view paths; its tests can start
  once those interfaces are frozen.
- **Polish**: Depends on every story selected for the release and all applicable
  source gates.

### User story dependencies

- **US1 (P1)**: Requires the authorization foundation and all four source
  facades before it can claim complete business-value KPI results.
- **US3 (P2)**: Requires the US1 query/response path but remains independently
  testable by substituting source quality and failure states.

### Within each user story

- Write the listed tests first and confirm they fail for the intended missing
  behavior before implementation.
- Create immutable domain/request values before application services.
- Complete authorization and application services before controllers/views.
- Complete source-owned aggregate tests before trusting KPI composition.
- Do not treat mocked source data as evidence that a production source gate is
  complete.

### Parallel opportunities

- T003 and T004 can run in parallel after the Manager-only scope is confirmed.
- T006-T011 define independent files and can run in parallel after T001/T002.
- T013-T015 can be implemented by the three source owners in parallel once
  their respective approved facts exist.
- T017 and T018 can be written in parallel; T019 and T022 affect separate files.
- T024-T026 can be written in parallel, and T027/T030 can proceed in parallel
  after facade contracts are frozen.
- T036 and T037 can be written in parallel; T038 can proceed separately from
  the UI work in T041.
- T044-T046 can run in parallel after the feature behavior stabilizes.

## Parallel examples

### User Story 1

```text
T017 Manager/API authorization tests
T018 React navigation/UI tests
T019 authorized-scope/summary value objects
```

### User Story 1 KPI composition

```text
T024 period-boundary tests
T025 KPI-composition tests
T026 source-facade aggregate tests
T027 reporting domain values
T030 REST DTOs and mapper
```

### User Story 3

```text
T036 data-quality policy tests
T037 refresh/view-preservation tests
T038 source-quality value objects
```

## Implementation strategy

### MVP first

1. Complete and approve Phase 1 decisions.
2. Complete the Phase 2 authorization/module foundation.
3. Implement the US1 shell and validate Manager-only, read-only behavior with
   no operational-list navigation.
4. Keep the shell in a loading/unavailable state until source gates permit
   verified KPI output; do not demo it as a complete dashboard.

### First business-value release

1. Complete all production source gates and T013-T015.
2. Complete the remaining US1 period, formula, API, and view behavior.
3. Validate each source formula and the consolidated branch/total results.
4. Add US3 data-quality and refresh behavior before using the dashboard for
   business decisions.

### Parallel team strategy

1. One owner completes Manager capability/branch authorization and the
   `accountmanagement` facade.
2. Retail, workshop, and operations owners complete T013-T015 in parallel after
   their source designs are approved.
3. A reporting owner implements period/KPI composition and REST contracts.
4. A frontend/test owner implements React dashboard states, approved localization,
   and independent journey tests
   after response contracts are stable.

## Notes

- Every task uses the required checkbox, sequential ID, `[P]` marker only where
  work can proceed in separate files, a story label only in story phases, and
  explicit paths.
- FE-10 owns composition only; source lifecycle/schema work stays with its
  owning feature and must be approved there before its FE-10 facade adapter is
  marked complete.
- Run `$speckit-analyze` after this task list is reviewed and before any
  `$speckit-implement` request.
