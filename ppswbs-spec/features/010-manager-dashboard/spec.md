# Feature Specification: Manager Dashboard

**Feature Branch**: `010-manager-dashboard`

**Created**: 2026-10-24

**Status**: Draft

**Input**: User description: "Create FE-10 Manager Dashboard with a read-only Executive Revenue Dashboard for authorized Managers; Admin may view only purpose-limited parameter-impact information in FE-09, not FE-10 executive revenue."

## Scope and source alignment

FE-10 provides a Manager-facing dashboard for consolidated business analytics. Its primary journey is `View Executive Revenue Dashboard`, which includes verified retail revenue, workshop utilization, branch KPIs, permitted filters, and data-quality notices.

The feature is read-only. It does not create or modify orders, payments, workshop records, products, promotions, delivery records, or accounting records. Manager Dashboard summary cards may link to authorized operational lists, but a link must not grant additional permissions.

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
- Q: Which reporting-period filters must FE-10 V1 support? → A: Calendar day, week, month, and year in Vietnam time (UTC+7), without a custom date range.
- Q: When must FE-10 refresh its dashboard data? → A: Reload on initial page load, filter application, or explicit Manager refresh; do not refresh automatically on a timer.

## User Scenarios & Testing

### User Story 1 - View Manager Dashboard (Priority: P1)

As an authorized Manager, I want to open a dashboard that summarizes the business areas I am responsible for so that I can identify performance and pending work quickly.

**Why this priority**: The dashboard is the entry point for the Manager's operational overview and is the minimum useful outcome of FE-10.

**Independent Test**: An authorized Manager opens the dashboard and verifies that summary cards, revenue access, permitted navigation, and scope notices are displayed without changing business records.

**Acceptance Scenarios**:

1. **Given** the user is an authenticated authorized Manager, **When** the user opens the Manager Dashboard, **Then** the system displays read-only summary cards for the permitted business scope.
2. **Given** the user is not authorized as a Manager, **When** the user attempts to open Manager revenue content, **Then** the system denies access and does not reveal revenue values.
3. **Given** a summary card links to an operational list, **When** the Manager opens the link, **Then** the system applies the Manager's existing authorization and does not grant a new permission.

---

### User Story 2 - View Executive Revenue Dashboard (Priority: P1)

As an authorized Manager, I want to view verified revenue and business KPIs for a permitted period and branch scope so that I can evaluate business performance.

**Why this priority**: Consolidated revenue and branch performance are the core business value of FE-10.

**Independent Test**: A Manager selects a supported calendar period and permitted branch, then compares the displayed metrics with the corresponding verified business records.

**Acceptance Scenarios**:

1. **Given** the Manager opens the Executive Revenue Dashboard, **When** the default view loads, **Then** the system displays consolidated metrics from the first day of the current calendar month through the current time in Vietnam time (UTC+7) across all branches within the Manager's authorized scope.
2. **Given** the Manager selects a supported calendar period and permitted branch scope, **When** the Manager applies the filters, **Then** the system displays verified retail revenue, verified workshop revenue, their total, paid retail order count, and workshop utilization for exactly that scope.
3. **Given** the selected period contains no verified records, **When** the dashboard loads, **Then** the system displays an explicit empty state and does not infer revenue from missing transactions.
4. **Given** a workshop has a verified deposit and later receives a final invoice containing actual surcharges, **When** the final invoice becomes fully paid, **Then** the dashboard recognizes the final invoice total once in the reporting period containing the fully-paid date and does not add the deposit again.
5. **Given** one verified grouped payment contains branch orders from multiple branches, **When** branch revenue is calculated, **Then** each branch receives its product value after allocated discounts and no delivery fee is counted as store revenue.
6. **Given** completed workshop sessions in the selected scope have recorded capacity and check-ins, **When** workshop utilization is calculated, **Then** the system divides their total checked-in participants by their total configured capacity and excludes cancelled or not-yet-completed sessions.
7. **Given** a verified event occurs near a calendar-day boundary, **When** the dashboard assigns the event to a reporting period, **Then** the event date is determined using Vietnam time (UTC+7).
8. **Given** the Manager selects a calendar day, week, month, or year, **When** the period is applied, **Then** the dashboard uses the complete selected calendar period, or the elapsed portion through the current time when the selected period is still in progress, in Vietnam time (UTC+7).

---

### User Story 3 - Understand Data Quality and Refresh Results (Priority: P2)

As an authorized Manager, I want to know when dashboard data is delayed, incomplete, or unavailable so that I do not make decisions from misleading totals.

**Why this priority**: A visible data-quality state protects business decisions while allowing the dashboard to remain useful when one source is unavailable.

**Independent Test**: A dashboard test provides delayed, partial, failed, and complete source states and verifies that each state is represented distinctly.

**Acceptance Scenarios**:

1. **Given** a metric source is delayed, **When** the dashboard is displayed, **Then** the affected metric is marked as delayed and is not presented as fully current.
2. **Given** only some metric sources are available, **When** the dashboard is displayed, **Then** the affected KPI is marked incomplete and the overall view does not claim to be fully consolidated.
3. **Given** a filter is invalid or outside the Manager's permitted scope, **When** the Manager applies it, **Then** the system rejects it and preserves the last valid dashboard view.
4. **Given** a metric retrieval attempt fails, **When** the Manager requests the dashboard, **Then** the system shows a safe unavailable state and allows an eligible retry without changing business data.
5. **Given** the dashboard is open and newer verified records become available, **When** no filter is changed and the Manager does not request a refresh, **Then** the current view remains stable and continues to display its last-updated time.
6. **Given** the Manager opens the dashboard, applies a supported filter, or explicitly requests a refresh, **When** metric retrieval succeeds, **Then** the system reloads the latest verified records and displays the new last-updated time.

### Edge Cases

- The selected period is valid but contains no verified business records.
- The selected branch is valid but has no records for the selected period.
- The Manager attempts to select a branch outside the permitted scope.
- A source contains records for only part of the selected period.
- A source is delayed while other sources are current.
- A source is unavailable during aggregation.
- New verified records arrive while the dashboard remains open without a filter change or explicit refresh.
- A user session expires or the user's Manager permission is removed while viewing the dashboard.
- A linked operational list contains records outside the Manager's current scope.
- An Admin authorized to view a parameter-impact summary attempts to use that permission to access FE-10 executive revenue or branch KPIs.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST allow an authenticated authorized Manager to open the Manager Dashboard.
- **FR-002**: The system MUST display read-only summary cards for business areas within the Manager's permitted scope.
- **FR-003**: The system MUST restrict executive revenue values and branch KPI values to authorized Managers.
- **FR-004**: When the Executive Revenue Dashboard opens, the system MUST load a default reporting period beginning at 00:00:00 on the first day of the current calendar month and ending at the current time, interpreted in Vietnam time (UTC+7), across all branches within the Manager's authorized scope.
- **FR-005**: The system MUST allow the Manager to select a calendar day, Monday-through-Sunday calendar week, calendar month, or calendar year in Vietnam time (UTC+7); V1 MUST NOT provide a custom date-range filter.
- **FR-006**: The system MUST allow the Manager to select only branches within the Manager's permitted scope.
- **FR-007**: The system MUST calculate dashboard metrics from verified recorded business data for the selected period and scope.
- **FR-008**: The system MUST display verified retail revenue and the count of fully paid retail branch orders supplied by the retail order domain.
- **FR-009**: The system MUST calculate workshop utilization for completed sessions as total checked-in participants divided by total configured capacity within the selected reporting scope; cancelled and not-yet-completed sessions MUST be excluded.
- **FR-010**: For every selected permitted branch scope, the system MUST display verified retail revenue, verified workshop revenue, total verified revenue calculated as their sum, paid retail order count, and workshop utilization.
- **FR-011**: The system MUST preserve the last valid dashboard view when a period or branch filter is rejected.
- **FR-012**: The system MUST identify empty, delayed, unavailable, and partial data states separately from complete data.
- **FR-013**: The system MUST NOT present missing or delayed records as verified revenue.
- **FR-014**: The system MUST allow an eligible Manager to retry an unavailable or delayed dashboard view.
- **FR-015**: The system MUST keep dashboard viewing and filtering read-only; these actions MUST NOT change orders, payments, workshop records, products, promotions, or other operational master data.
- **FR-016**: The system MUST apply the user's authorization again when the user opens a linked operational list.
- **FR-017**: The system MUST deny access without revealing executive revenue values when the user is unauthenticated, locked, no longer authorized, or outside the Manager role boundary.
- **FR-018**: The system MUST provide a clear safe state when the session expires or authorization changes during dashboard use.
- **FR-019**: The system MUST recognize workshop revenue from the final invoice total only after that invoice is fully paid, including recorded actual surcharges and excluding a second count of the earlier 50% deposit; the fully-paid date MUST determine the reporting period.
- **FR-020**: For a verified grouped retail payment containing orders from multiple branches, the system MUST attribute each branch's product value after allocated discounts to that branch and MUST exclude delivery fees from store revenue.
- **FR-021**: The system MUST interpret payment dates, workshop dates, filter boundaries, and reporting periods using Vietnam time (UTC+7).
- **FR-022**: The system MUST NOT treat permission to view an Admin parameter-impact summary as permission to view FE-10 executive revenue or branch KPIs.
- **FR-023**: The system MUST reload dashboard data on initial page load, successful filter application, and explicit Manager refresh; V1 MUST NOT refresh dashboard data automatically on a timer.
- **FR-024**: The system MUST display the last successful dashboard update time in Vietnam time (UTC+7).

### Key Entities

- **Dashboard View**: A read-only presentation of business metrics for a reporting period and permitted branch scope.
- **Reporting Scope**: A selected calendar day, Monday-through-Sunday week, month, or year interpreted using Vietnam time (UTC+7), together with the branch set used to constrain all displayed metrics. A current period ends at the current time; a completed period uses its full calendar boundaries.
- **Verified Business Record**: A recorded retail, payment, workshop, or branch fact eligible to contribute to a dashboard metric after its owning workflow has verified it. For workshop revenue, this is the fully paid final invoice rather than the deposit as a separate revenue record.
- **Branch Revenue Contribution**: The product value of a verified branch order after its allocated discounts, excluding delivery fees, attributed to the branch that owns that order.
- **KPI Snapshot**: The required indicators for a reporting scope: verified retail revenue, verified workshop revenue, total verified revenue, paid retail order count, and workshop utilization. Workshop utilization uses actual check-ins against configured capacity for completed sessions.
- **Data Quality Notice**: A visible status describing empty, delayed, unavailable, or partial source data and the time of the last successful dashboard update.

## Success Criteria

### Measurable Outcomes

- **SC-001**: In an authorized Manager test journey, the default dashboard view is displayed successfully in at least 95% of attempts when required source data is available.
- **SC-002**: In 100% of authorization tests, Member, Staff, and Admin users cannot view executive revenue values.
- **SC-003**: In 100% of scope tests, a Manager cannot view KPI or revenue data for a branch outside the Manager's permitted scope.
- **SC-004**: In 100% of data-quality tests, empty, delayed, unavailable, and partial data states are visibly distinguished from complete data.
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
- Mland's account and authorization data determines whether a user is an authorized Manager and which branches are in scope.
- Dashboard values are based on verified recorded business data; pending, failed, or missing transactions are not treated as revenue. A workshop deposit is not counted separately once its fully paid final invoice is recognized, and the fully-paid date assigns that revenue to a reporting period.
- For a grouped retail payment, each discount is allocated to the applicable branch order before revenue aggregation; delivery fees remain separate from branch and total store revenue.
- Workshop sessions expose their configured capacity, completion state, and recorded check-ins to FE-10; cancelled and future sessions do not contribute to actual utilization.
- The initial release does not include exports, scheduled reports, email delivery of reports, custom date ranges, or period-over-period comparison.
- The initial release does not include accounting reconciliation, profit calculation, tax reporting, or external carrier tracking.
- KPI formulas not explicitly defined in this specification will be finalized during planning if they affect implementation or acceptance tests.
- The permission-matrix conflict concerning Admin access is resolved for this feature by the approved Manager-only direction; the baseline matrix should be synchronized separately.
- FE-09 owns the Admin `Parameter Impact Summary`, which may show parameter values, before/after examples, aggregated usage or affected-value counts, and parameter audit history only within the parameter's authorized scope. FE-10 neither owns nor displays that summary.

