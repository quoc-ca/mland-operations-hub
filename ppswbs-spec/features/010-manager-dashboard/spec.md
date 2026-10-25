# Feature Specification: Manager Dashboard

**Feature Branch**: `010-manager-dashboard`

**Created**: 2026-10-24

**Status**: Draft

**Input**: User description: "Create FE-10 Manager Dashboard with a read-only Executive Revenue Dashboard for authorized Managers; Admin may view only purpose-limited parameter-impact information in FE-09, not FE-10 executive revenue."

## Scope and source alignment

FE-10 provides one Manager-facing dashboard for consolidated business analytics.
Its primary journey is `View Executive Manager Dashboard`, which includes five
read-only KPI cards, permitted filters, per-branch KPI rows, and data-quality
notices.

The feature is read-only. It does not create or modify orders, payments,
workshop records, products, promotions, delivery records, or accounting records.
V1 does not include operational-list navigation cards, pending-work cards, or
metrics owned by another feature.

All monetary values in FE-10 V1 use Vietnamese dong (`VND`). A source fact in a
different currency is not converted or aggregated; the affected source and KPI
are reported as partial with a safe reason code.

The current requirements baseline describes the dashboard as Manager-only. The permission matrix contains an inconsistent Admin `Full` entry; this feature follows the Manager-only direction and does not grant Admin revenue access.

An Admin may receive a separate, purpose-limited `Parameter Impact Summary` for parameters that the Admin is authorized to configure. That capability belongs to FE-09 Administrative Parameter Configuration and does not grant access to FE-10, total executive revenue, branch revenue rankings, customer details, individual order or payment details, or business-price approval. FE-10 must keep its authorization boundary independent from that Admin capability.

## Clarifications

### Session 2026-10-24

- Q: How is workshop revenue recognized when a booking begins with a 50% deposit and the final amount may change because of actual surcharges? → A: Count the final invoice total only after it has been fully paid; do not count the deposit separately again.
- Q: Which date determines the reporting period for a fully paid final workshop invoice? → A: The date on which the final invoice becomes fully paid.
- Q: How is retail revenue attributed when one grouped payment contains branch orders from multiple branches? → A: Attribute each branch's product value after its allocated discounts to that branch and exclude delivery fees from store revenue.
- Q: How is workshop utilization calculated for sessions that have taken place? → A: Divide the total number of checked-in participants by the total configured capacity of completed sessions.
- Q: Which timezone determines payment dates and dashboard reporting periods? → A: Vietnam time (UTC+7).
- Q: Which KPI group must every branch show on FE-10? → A: Verified retail revenue, verified workshop revenue, total verified revenue, paid retail order count, and workshop utilization.
- Q: Which reporting period loads by default when a Manager first opens the dashboard? → A: The current calendar month, from the first day through the current time in Vietnam time (UTC+7).
- Q: Which branch scope loads by default when a Manager first opens the dashboard? → A: All branches within the Manager's authorized scope.
- Q: What is the authoritative source of a Manager's permitted branch scope? →
  A: MySQL `staff_branch_assignment` records. An active Manager may view only
  branch IDs in active assignments; no active assignment means no FE-10 access.
  Firebase claims and frontend state do not grant or widen that scope.
- Q: Which authorization capability permits executive dashboard access? → A:
  `REPORTING_EXECUTIVE_VIEW`. Only an active account with that capability and
  at least one active `staff_branch_assignment` may access FE-10; Admin does
  not receive it automatically from parameter-configuration authority.
- Q: Which key joins a Manager assignment with retail and workshop reporting
  facts? → A: One immutable `branchId`. `staff_branch_assignment`, retail
  branch orders, workshop sessions/final invoices, and check-ins reference the
  same ID. A branch name is display-only and may change without changing scope
  or aggregation.
- Q: What format does the immutable common `branchId` use? → A: UUID. The UUID
  is generated once for the branch and reused by assignment, retail, workshop,
  and attendance records; it is never derived from the branch display name.
- Q: Which reporting-period filters must FE-10 V1 support? → A: Calendar day, week, month, and year in Vietnam time (UTC+7), without a custom date range.
- Q: When must FE-10 refresh its dashboard data? → A: Reload on initial page load, filter application, or explicit Manager refresh; do not refresh automatically on a timer.

## User Scenarios & Testing

### User Story 1 - View Executive Manager Dashboard (Priority: P1)

As an authorized Manager, I want to open one dashboard containing verified
revenue and operational KPI cards for my permitted period and branches so that I
can evaluate business performance.

**Why this priority**: The five KPI cards and branch breakdown are the complete
business value of FE-10 V1; no separate Manager landing page is needed.

**Independent Test**: A Manager opens the dashboard, chooses a supported period
and permitted branch set, and compares all five KPI cards and branch rows with
the corresponding verified source facts without changing business records.

**Acceptance Scenarios**:

1. **Given** the user is an authenticated authorized Manager, **When** the user opens the dashboard, **Then** the system displays the five read-only KPI cards for the permitted business scope.
2. **Given** the user is not authorized as a Manager, **When** the user attempts to open the dashboard, **Then** the system denies access and does not reveal KPI or revenue values.

3. **Given** the Manager opens the dashboard, **When** the default view loads, **Then** the system displays consolidated metrics from the first day of the current calendar month through the current time in Vietnam time (UTC+7) across all branches within the Manager's authorized scope.
4. **Given** the Manager selects a supported calendar period and permitted branch scope, **When** the Manager applies the filters, **Then** the system displays verified retail revenue, verified workshop revenue, their total, paid retail order count, and workshop utilization for exactly that scope.
5. **Given** the selected period contains no verified records, **When** the dashboard loads, **Then** the system displays an explicit empty state and does not infer revenue from missing transactions.
6. **Given** a workshop has a verified deposit and later receives a final invoice containing actual surcharges, **When** the final invoice becomes fully paid, **Then** the dashboard recognizes the final invoice total once in the reporting period containing the fully-paid date and does not add the deposit again.
7. **Given** one verified grouped payment contains branch orders from multiple branches, **When** branch revenue is calculated, **Then** each branch receives its product value after allocated discounts and no delivery fee is counted as store revenue.
8. **Given** completed workshop sessions in the selected scope have recorded capacity and check-ins, **When** workshop utilization is calculated, **Then** the system divides their total checked-in participants by their total configured capacity and excludes cancelled or not-yet-completed sessions.
9. **Given** a verified event occurs near a calendar-day boundary, **When** the dashboard assigns the event to a reporting period, **Then** the event date is determined using Vietnam time (UTC+7).
10. **Given** the Manager selects a calendar day, week, month, or year, **When** the period is applied, **Then** the dashboard uses the complete selected calendar period, or the elapsed portion through the current time when the selected period is still in progress, in Vietnam time (UTC+7).

---

### User Story 3 - Understand Data Quality and Refresh Results (Priority: P2)

As an authorized Manager, I want to know when dashboard data is empty,
incomplete, or unavailable so that I do not make decisions from misleading
totals.

**Why this priority**: A visible data-quality state protects business decisions while allowing the dashboard to remain useful when one source is unavailable.

**Independent Test**: A dashboard test provides empty, partial, unavailable,
and complete source states and verifies that each state is represented distinctly.

**Acceptance Scenarios**:

1. **Given** only some metric sources are available, **When** the dashboard is displayed, **Then** the affected KPI is marked incomplete and the overall view does not claim to be fully consolidated.
2. **Given** a filter is invalid or outside the Manager's permitted scope, **When** the Manager applies it, **Then** the system rejects it and preserves the last valid dashboard view.
3. **Given** a metric retrieval attempt fails, **When** the Manager requests the dashboard, **Then** the system shows a safe unavailable state and allows an eligible retry without changing business data.
4. **Given** the dashboard is open and newer verified records become available, **When** no filter is changed and the Manager does not request a refresh, **Then** the current view remains stable and continues to display its last-updated time.
5. **Given** the Manager opens the dashboard, applies a supported filter, or explicitly requests a refresh, **When** metric retrieval succeeds, **Then** the system reloads the latest committed verified records and displays the new last-updated time.

### Edge Cases

- The selected period is valid but contains no verified business records.
- The selected branch is valid but has no records for the selected period.
- The Manager attempts to select a branch outside the permitted scope.
- A source contains records for only part of the selected period.
- A source is unavailable during aggregation.
- New verified records arrive while the dashboard remains open without a filter change or explicit refresh.
- A user session expires or the user's Manager permission is removed while viewing the dashboard.
- An Admin authorized to view a parameter-impact summary attempts to use that permission to access FE-10 executive revenue or branch KPIs.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST allow an authenticated active account with
  `REPORTING_EXECUTIVE_VIEW` and at least one active
  `staff_branch_assignment` to open the Manager Dashboard.
- **FR-002**: The system MUST display exactly five read-only KPI summary cards
  for the selected permitted scope: verified retail revenue, verified workshop
  revenue, total verified revenue, paid retail order count, and workshop
  utilization.
- **FR-003**: The system MUST restrict executive revenue values and branch KPI
  values to accounts with `REPORTING_EXECUTIVE_VIEW`; Admin parameter-
  configuration authority MUST NOT imply that capability.
- **FR-004**: When the Executive Revenue Dashboard opens, the system MUST load a default reporting period beginning at 00:00:00 on the first day of the current calendar month and ending at the current time, interpreted in Vietnam time (UTC+7), across all branches within the Manager's authorized scope.
- **FR-005**: The system MUST allow the Manager to select a calendar day, Monday-through-Sunday calendar week, calendar month, or calendar year in Vietnam time (UTC+7); V1 MUST NOT provide a custom date-range filter.
- **FR-006**: The system MUST allow the Manager to select only stable branch
  identifiers within the Manager's active `staff_branch_assignment` scope. The
  identifier MUST be the immutable common `branchId`, never a branch name or
  free-text workshop location.
- **FR-007**: The system MUST calculate dashboard metrics from verified recorded business data for the selected period and scope.
- **FR-008**: The system MUST display verified retail revenue and the count of fully paid retail branch orders supplied by the retail order domain.
- **FR-009**: The system MUST calculate workshop utilization for completed sessions as total checked-in participants divided by total configured capacity within the selected reporting scope; cancelled and not-yet-completed sessions MUST be excluded.
- **FR-010**: For every selected permitted branch scope, the system MUST display verified retail revenue, verified workshop revenue, total verified revenue calculated as their sum, paid retail order count, and workshop utilization.
- **FR-011**: The system MUST preserve the last valid dashboard view when a period or branch filter is rejected.
- **FR-012**: The system MUST identify empty, unavailable, and partial data
  states separately from complete data.
- **FR-013**: The system MUST NOT present an unavailable source or missing
  source records as zero or as complete verified revenue.
- **FR-014**: The system MUST allow an eligible Manager to retry an unavailable dashboard view.
- **FR-015**: The system MUST keep dashboard viewing and filtering read-only; these actions MUST NOT change orders, payments, workshop records, products, promotions, or other operational master data.
- **FR-016**: The V1 dashboard MUST contain only its period/branch filters,
  five KPI cards, per-branch KPI rows, data-quality notices, and explicit
  refresh control; it MUST NOT include operational-list navigation cards,
  pending-work cards, or metrics owned by another feature.
- **FR-017**: The system MUST deny access without revealing executive revenue values when the user is unauthenticated, locked, no longer authorized, or outside the Manager role boundary.
- **FR-018**: The system MUST provide a clear safe state when the session expires or authorization changes during dashboard use.
- **FR-019**: The system MUST recognize workshop revenue from the final invoice total only after that invoice is fully paid, including recorded actual surcharges and excluding a second count of the earlier 50% deposit; the fully-paid date MUST determine the reporting period.
- **FR-020**: For a verified grouped retail payment containing orders from multiple branches, the system MUST attribute each branch's product value after allocated discounts to that branch and MUST exclude delivery fees from store revenue.
- **FR-021**: The system MUST interpret payment dates, workshop dates, filter boundaries, and reporting periods using Vietnam time (UTC+7).
- **FR-022**: The system MUST NOT treat permission to view an Admin parameter-impact summary as permission to view FE-10 executive revenue or branch KPIs.
- **FR-023**: The system MUST reload dashboard data on initial page load, successful filter application, and explicit Manager refresh; V1 MUST NOT refresh dashboard data automatically on a timer.
- **FR-024**: The system MUST display the last successful dashboard update time in Vietnam time (UTC+7).
- **FR-025**: The system MUST accept only `VND` monetary facts for FE-10 V1.
  A non-`VND` source fact MUST NOT be converted or included in a revenue total;
  the affected result MUST be partial with safe quality metadata.
- **FR-026**: The system MUST include a retail order in dashboard revenue and
  paid-order count only after its payment is successfully verified and the
  verified outcome is committed by the retail domain. A Manager refresh MUST
  read the latest committed verified facts; pending or failed payment attempts
  MUST NOT contribute to any KPI.

### Key Entities

- **Dashboard View**: A read-only presentation of business metrics for a reporting period and permitted branch scope.
- **Reporting Scope**: A selected calendar day, Monday-through-Sunday week, month, or year interpreted using Vietnam time (UTC+7), together with the branch set used to constrain all displayed metrics. A current period ends at the current time; a completed period uses its full calendar boundaries.
- **Verified Business Record**: A recorded retail, payment, workshop, or branch fact eligible to contribute to a dashboard metric after its owning workflow has verified it. For workshop revenue, this is the fully paid final invoice rather than the deposit as a separate revenue record.
- **Branch Revenue Contribution**: The product value of a verified branch order after its allocated discounts, excluding delivery fees, attributed to the branch that owns that order.
- **KPI Snapshot**: The required indicators for a reporting scope: verified retail revenue, verified workshop revenue, total verified revenue, paid retail order count, and workshop utilization. Workshop utilization uses actual check-ins against configured capacity for completed sessions.
- **Data Quality Notice**: A visible status describing empty, unavailable, or
  partial source data and the time of the last successful dashboard update.

## Success Criteria

### Measurable Outcomes

- **SC-001**: In an authorized Manager test journey, the default dashboard view is displayed successfully in at least 95% of attempts when required source data is available.
- **SC-002**: In 100% of authorization tests, Member, Staff, and Admin users cannot view executive revenue values.
- **SC-003**: In 100% of scope tests, a Manager cannot view KPI or revenue data for a branch outside the Manager's permitted scope.
- **SC-004**: In 100% of data-quality tests, empty, unavailable, and partial data states are visibly distinguished from complete data.
- **SC-005**: In 100% of read-only behavior tests, viewing or filtering the dashboard leaves order, payment, workshop, product, promotion, and master-data records unchanged.
- **SC-006**: A Manager can change the reporting period and branch scope and identify the active scope within one dashboard journey.
- **SC-007**: In 100% of reporting-boundary tests, verified events are assigned to the correct calendar day and reporting period using Vietnam time (UTC+7).
- **SC-008**: In 100% of cross-feature authorization tests, an Admin's parameter-impact permission does not expose FE-10 executive revenue, branch rankings, customer details, or individual order and payment details.
- **SC-009**: In 100% of complete-data branch views, all five required KPI values are shown for the active reporting period and branch scope.
- **SC-010**: In 100% of default-view tests, the initial reporting period starts on the first day of the current calendar month and ends at the current time in Vietnam time (UTC+7).
- **SC-011**: In 100% of default-scope tests, the initial dashboard consolidates exactly all branches within the Manager's authorized scope and no unauthorized branch.
- **SC-012**: In 100% of period-filter tests, day, Monday-through-Sunday week, month, and year boundaries are applied correctly in Vietnam time (UTC+7), and no custom date-range control is offered.
- **SC-013**: In 100% of refresh-behavior tests, dashboard data reloads on initial load, successful filter application, or explicit refresh, remains stable without those actions, and shows the last successful update time.

## Assumptions

- FE-08 remains the owner of retail order and verified retail payment outcomes; FE-10 reads those outcomes and does not re-decide payment validity.
- The workshop feature remains the owner of workshop schedules, attendance, capacity, and utilization facts.
- Mland's MySQL authorization data determines whether a user is an authorized
  Manager. Active `staff_branch_assignment` records determine the branches in
  scope; Firebase claims and frontend state do not grant or widen that scope.
- Dashboard values are based on verified recorded business data; pending, failed, or missing transactions are not treated as revenue. A workshop deposit is not counted separately once its fully paid final invoice is recognized, and the fully-paid date assigns that revenue to a reporting period.
- For a grouped retail payment, each discount is allocated to the applicable branch order before revenue aggregation; delivery fees remain separate from branch and total store revenue.
- Workshop sessions expose their configured capacity, completion state, and recorded check-ins to FE-10; cancelled and future sessions do not contribute to actual utilization.
- The initial release does not include exports, scheduled reports, email delivery of reports, custom date ranges, or period-over-period comparison.
- The initial release does not include accounting reconciliation, profit calculation, tax reporting, or external carrier tracking.
- KPI formulas not explicitly defined in this specification will be finalized during planning if they affect implementation or acceptance tests.
- The permission-matrix conflict concerning Admin access is resolved for this feature by the approved Manager-only direction; the baseline matrix should be synchronized separately.
- FE-09 owns the Admin `Parameter Impact Summary`, which may show parameter values, before/after examples, aggregated usage or affected-value counts, and parameter audit history only within the parameter's authorized scope. FE-10 neither owns nor displays that summary.

