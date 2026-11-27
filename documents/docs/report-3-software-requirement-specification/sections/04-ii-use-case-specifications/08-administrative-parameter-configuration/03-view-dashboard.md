### 8.3 View Dashboard

| Use Case ID | UC-64 | Use Case Name | View Dashboard |
| --- | --- | --- | --- |
| Created By | ThinhBT | Created Date | 20/11/2026 |
| Primary Actors | Manager | Secondary Actors | None |

#### Description

As a Manager, I want to view consolidated business analytics, workshop utilization, retail revenue, and branch KPIs for an authorized period so that I can monitor performance alongside today's operations.

#### Trigger

A Manager opens Dashboard or changes a reporting period or branch filter.

#### Preconditions

1. The Manager is authenticated and authorized to view executive analytics.
2. The platform is available.
3. The selected reporting period and branch scope are valid.

#### Postconditions

1. The system displays dashboard metrics for the selected period and scope.
2. No booking, order, payment, or operational master data is changed.
3. Missing or delayed metrics are clearly identified.

#### Normal Flow

1. Manager opens Dashboard.
2. System displays today's operations, recent events, and the current-month revenue report for all authorized branches.
3. Manager selects a reporting period (day, week, month, year) and branch scope.
4. System retrieves and aggregates verified VND revenue, workshop, order, and branch KPI data.
5. System displays totals, comparisons, trend charts, and top-5 product and workshop best sellers.
6. Manager reviews the metrics and updates filters.

#### Alternative Flows

2a. No data is available for the selected period → System displays an explicit empty state without inferring transactions; unreadable sources appear as unknown, never zero.

6a. Manager switches VI/EN language → System updates all dashboard labels to the selected language without timer-based auto-refresh.

#### Exception Flows

3a. Invalid period or unauthorized branch scope → System rejects the filter and retains the last valid dashboard view.

4a. Non-VND amount encountered → System does not convert or add the amount and marks the affected figure incomplete.

4b. Aggregation fails or data is delayed → System displays an error or data-unavailable indicator and allows the Manager to retry.

5a. Partial data source is unavailable → System marks the affected KPI as incomplete and does not represent it as fully consolidated.

5b. Best sellers cannot be loaded or have no data → System displays Không tải được danh sách bán chạy with Thử lại (or Tạm thời không đọc được dữ liệu này / empty-list notice) while keeping the rest of the dashboard.

#### Business Rules

BR-64-01, BR-64-02, BR-64-03, BR-64-04

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

—

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| BR-64-01 | Only an authorized Manager may view executive revenue and branch KPI data. |
| BR-64-02 | Dashboard metrics must be calculated from verified recorded business data for the selected period and scope. |
| BR-64-03 | Missing, delayed, or partial data must be clearly identified and must not be silently presented as complete analytics. |
| BR-64-04 | Best sellers count products by units in VND branch orders paid in the period, and workshop packages by tickets confirmed, completed or marked no-show in booking groups placed in the period at the branch of the first session; ties go to the higher amount before discounts, then to the lower ID; a package that no longer exists keeps the name recorded on its booking line. |
