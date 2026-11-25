### 8.3 View Dashboard

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

Allows the Manager to view consolidated business analytics, workshop utilization, retail revenue, and branch key performance indicators for an authorized reporting period, together with today's operations and recent events on the same page.

#### Preconditions

1. The Manager is authenticated and authorized to view executive analytics.
2. The platform is available.
3. The selected reporting period and branch scope are valid.

#### Postconditions

- The system displays dashboard metrics for the selected period and scope.
- No booking, order, payment, or operational master data is changed.
- Missing or delayed metrics are clearly identified.

#### Normal Flow

**View Executive Revenue Dashboard**

1. The Manager opens Dashboard, the Manager's home page and the first group of the navigation rail.
2. The system shows today's operations and recent events, and loads the revenue report for the default reporting period (the current month) and all authorized branches.
3. The Manager selects a day, week, month or year (Vietnam time) and all branches or one branch with a single click.
4. The system retrieves and aggregates verified revenue, workshop, order, and branch data: finalized workshop folios, workshop tickets and check-ins, and retail orders.
5. The system displays revenue totals, workshop utilization, retail performance, and branch KPIs with the applicable period and scope, each compared with the previous period of the same length, and trend charts by hour for a day, by day for a week or month and by month for a year: workshop revenue, registered places, check-ins, no-shows and cancellations; retail orders placed, paid and cancelled, and retail revenue. Each figure keeps one colour, every chart writes its values, and all labels follow the VI/EN switch.
6. For the same period and branches, the system lists the best sellers: the top 5 products by units in paid retail orders and the top 5 workshop packages by confirmed tickets, each row with its rank, name, count and a bar; hovering or focusing a row shows its amount before discounts and its share of the five.
7. The Manager reviews the dashboard, changes the filters or switches the language.

#### Alternative Flows

**Step 2 — No data is available**

The system displays a zero/empty state with the selected period and does not infer missing transactions.

**Step 3 — Invalid period or unauthorized branch scope**

The system rejects the filter and keeps the last valid dashboard view.

**Step 4 — Aggregation fails or data is delayed**

The system displays an error or data-unavailable indicator and allows the Manager to retry.

**Step 5 — Partial data source is unavailable**

The system marks the affected KPI as incomplete and does not represent it as fully consolidated.

**Step 6 — Best sellers cannot be loaded or have no data**

The system shows Không tải được danh sách bán chạy with Thử lại and keeps the rest of the dashboard; a list whose source cannot be read shows Tạm thời không đọc được dữ liệu này, and a period with no sale or confirmed ticket shows an empty-list notice.

#### Business Rules

BR-64-01, BR-64-02, BR-64-03, BR-64-04

#### Business Rule Definitions

| ID       | Rule Definition                                                                                                        |
| -------- | ---------------------------------------------------------------------------------------------------------------------- |
| BR-64-01 | Only an authorized Manager may view executive revenue and branch KPI data.                                             |
| BR-64-02 | Dashboard metrics must be calculated from verified recorded business data for the selected period and scope.           |
| BR-64-03 | Missing, delayed, or partial data must be clearly identified and must not be silently presented as complete analytics. |
| BR-64-04 | Best sellers count products by units in VND branch orders paid in the period, and workshop packages by tickets confirmed, completed or marked no-show in booking groups placed in the period at the branch of the first session; ties go to the higher amount before discounts. |
