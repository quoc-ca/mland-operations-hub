### 5.3 View Executive Revenue Dashboard

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

Allows the Manager to view consolidated business analytics, workshop utilization, retail revenue, and branch key performance indicators for an authorized reporting period.

#### Preconditions

1. The Manager is authenticated and authorized to view executive analytics.
2. The platform is available.
3. The selected reporting period and branch scope are valid.

#### Normal Flow

**View Executive Revenue Dashboard**
1. The Manager opens the executive revenue dashboard.
2. The system loads the default reporting period and authorized branch scope.
3. The Manager selects a period, branch, or supported KPI view.
4. The system retrieves and aggregates verified revenue, workshop, order, and branch data.
5. The system displays revenue totals, workshop utilization, retail performance, and branch KPIs with the applicable period and scope.
6. The Manager reviews the dashboard or changes the filters.

#### Alternative Flows



**Step 2 — data is available**
The system displays a zero/empty state with the selected period and does not infer missing transactions.



**Step 3 — Invalid period or unauthorized branch scope**
The system rejects the filter and keeps the last valid dashboard view.



**Step 4 — Aggregation fails or data is delayed**
The system displays an error or data-unavailable indicator and allows the Manager to retry.



**Step 5 — Partial data source is unavailable**
The system marks the affected KPI as incomplete and does not represent it as fully consolidated.

#### Postconditions

• The system displays the dashboard metrics and visualizations for the selected scope and period.• No booking, order, payment, or operational master data is changed.• Any unavailable or delayed metric is identified rather than presented as a confirmed value.

#### Business Rules

BR-56-01, BR-56-02, BR-56-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-56-01 | Only an authorized Manager may view executive revenue and branch KPI data. |
| BR-56-02 | Dashboard metrics must be calculated from verified recorded business data for the selected period and scope. |
| BR-56-03 | Missing, delayed, or partial data must be clearly identified and must not be silently presented as complete analytics. |
