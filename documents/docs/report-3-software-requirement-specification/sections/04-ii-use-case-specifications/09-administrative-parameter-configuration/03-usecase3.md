### 5.1 Administrative Parameter Configuration

#### 5.1.3 View Executive Revenue Dashboard

<table>
<tr><td>Primary Actors</td><td>Manager</td><td>Secondary Actors</td><td>None</td></tr>
<tr><td>Description</td><td colspan="3">Allows the Manager to view consolidated business analytics, workshop utilization, retail revenue, and branch key performance indicators for an authorized reporting period.</td></tr>
<tr><td>Preconditions</td><td colspan="3">1. The Manager is authenticated and authorized to view executive analytics.<br>2. The platform is available.<br>3. The selected reporting period and branch scope are valid.</td></tr>
<tr><td>Postconditions</td><td colspan="3">• The system displays the dashboard metrics and visualizations for the selected scope and period.<br>• No booking, order, payment, or operational master data is changed.<br>• Any unavailable or delayed metric is identified rather than presented as a confirmed value.</td></tr>
<tr><td>Normal<br>Sequence/Flow</td><td colspan="3"><em>View Executive Revenue Dashboard</em><br>1. The Manager opens the executive revenue dashboard.<br>2. The system loads the default reporting period and authorized branch scope.<br>3. The Manager selects a period, branch, or supported KPI view.<br>4. The system retrieves and aggregates verified revenue, workshop, order, and branch data.<br>5. The system displays revenue totals, workshop utilization, retail performance, and branch KPIs with the applicable period and scope.<br>6. The Manager reviews the dashboard or changes the filters.</td></tr>
<tr><td>Alternative<br>Sequences/Flows</td><td colspan="3"><em>Step 2 — No data is available</em><br>The system displays a zero/empty state with the selected period and does not infer missing transactions.<br><br><em>Step 3 — Invalid period or unauthorized branch scope</em><br>The system rejects the filter and keeps the last valid dashboard view.<br><br><em>Step 4 — Aggregation fails or data is delayed</em><br>The system displays an error or data-unavailable indicator and allows the Manager to retry.<br><br><em>Step 5 — Partial data source is unavailable</em><br>The system marks the affected KPI as incomplete and does not represent it as fully consolidated.</td></tr>
<tr><td>Business Rule</td><td colspan="3">BR-56-01, BR-56-02, BR-56-03</td></tr>
</table>

<table><tr style="background-color:#f4cccc"><th>ID</th><th>Rule Definition</th></tr><tr><td>BR-56-01</td><td>Only an authorized Manager may view executive revenue and branch KPI data.</td></tr><tr><td>BR-56-02</td><td>Dashboard metrics must be calculated from verified recorded business data for the selected period and scope.</td></tr><tr><td>BR-56-03</td><td>Missing, delayed, or partial data must be clearly identified and must not be silently presented as complete analytics.</td></tr></table>
