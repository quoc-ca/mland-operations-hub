## 10. Manager Dashboard

This design set covers feature FE-10 Manager Dashboard (spec `ppswbs-spec/features/010-manager-dashboard`). The backend module is `features.reporting`, a read-only Spring Modulith module layered as `controller → service → source`. It composes committed facts owned by FE-08 retail sales and FE-03 workshop operations through read-only aggregate SQL queries in `reporting.source`; it never changes them, and no order, ticket or customer row leaves the database, only per-branch aggregates and at most five ranked rows per best-seller list. Only an active Manager with at least one active branch assignment may view it. Every period is a half-open interval in Vietnam time; money is whole VND; a source that cannot be read gives `null` (shown as unknown, never as zero) and the report is marked `PARTIAL`. The frontend page `/manager/dashboard` is the Manager's home in the operations shell and follows the VI/EN switch.

### 10.1 Class Diagram

(Class diagram)

::: {.docx-table width="100%" columns="8%,32%,60%" repeat-header="false"}

|No|Class|Description|
|---|---|---|
|1|ExecutiveReportController|`GET /api/v1/executive-reports`: the KPIs for a period (day, week, month, year or a chosen day) and a branch set, per branch and in total.|
|2|ExecutiveTrendController|`GET /api/v1/executive-reports/trends`: the trend series with the same filters and scope rule.|
|3|ExecutiveTopSellerController|`GET /api/v1/executive-reports/top-sellers`: the best sellers with the same filters and scope rule.|
|4|ReportingExceptionHandler|Returns safe errors: a stable code, a short message and a correlation ID, never SQL, stack traces or source data.|
|5|ManagerReportingAccess|Checks `REPORTING_EXECUTIVE_VIEW`: an active Manager with at least one active branch assignment; a requested branch set must be wholly inside the assignment. Admin authority does not imply it.|
|6|ReportingPeriodResolver|Turns the period type and anchor date into `[from, to)` in Vietnam time, capped at now, and the previous period for comparison.|
|7|ExecutiveReportService|Composes the KPIs per branch and in total: retail revenue, workshop revenue, total revenue, paid retail orders, checked-in workshop participants and workshop utilization (checked-in places over completed session capacity). Each source is read on its own so one failing source only blanks its own values.|
|8|ExecutiveTrendService|Builds the trend series for the period and the previous period: retail orders placed, paid, cancelled and expired and retail revenue; workshop tickets booked, confirmed, cancelled and abandoned, places registered, checked in and no-show, and workshop revenue; folds hourly aggregates into hour, day or month buckets.|
|9|ExecutiveTopSellerService|Builds the best sellers for the period: the top 5 products by units and the top 5 workshop packages by confirmed tickets. Each list is read on its own; a list that cannot be read is `null` and named in `unavailable`, never shown as empty.|
|10|ReportingSourcePort / JdbcReportingSourceAdapter|Read-only KPI aggregates, one query per source, each grouped by branch in MySQL.|
|11|ReportingTrendPort / JdbcReportingTrendAdapter|Read-only trend aggregates grouped by whole hours since the period start, so they map exactly onto Vietnam days and months.|
|12|ReportingTopSellerPort / JdbcReportingTopSellerAdapter|Read-only rankings of at most five rows each: products by units in VND branch orders paid in the period, and workshop packages by tickets confirmed, completed or marked no-show in booking groups placed in the period at the branch of the first session. Ties go to the higher amount before discounts.|
|13|WorkshopFolioSql|Shared SQL for the workshop folios counted as revenue (finalized, fully paid, frozen post-promotion amount), so the KPI and the trend read the same facts.|
|14|ReportSourceQuality|Records the quality of each source (retail orders, retail revenue, workshop bookings, workshop revenue, attendance, capacity) with a safe reason code such as `SOURCE_UNAVAILABLE`; any unreadable source makes the report `PARTIAL`.|
|15|ReportingMoney|Whole-VND amounts as strings; `null` means unknown.|
|16|ReportingException|Typed failure with HTTP status and code (`MANAGER_REPORTING_FORBIDDEN`, `REPORTING_FILTER_INVALID`, `REPORTING_SOURCES_UNAVAILABLE`, `AUTHENTICATION_REQUIRED`).|
|17|ExecutiveReportDtos / ExecutiveTrendDtos / ExecutiveTopSellerDtos|Response records: report scope (period, interval, time zone, branches), KPI values in total and per branch, source quality, last update time, trend points for the current and previous period, and best sellers (ID, name, count and amount before discounts).|
|18|ManagerDashboardPage (frontend)|Filters (period, branch, day), the KPI cards, revenue report, trend charts and best sellers; reloads only on load, filter change or refresh.|
|19|TrendChart / RevenueReport (frontend)|Hand-drawn SVG charts sized to their container, with value labels, whole-number axes and a gap for unknown values.|
|20|TopSellers (frontend)|Two ranked lists, retail amber and workshop blue: rank, name, count and a bar per row; hovering or focusing a row shows its amount before discounts and its share of the five.|

:::

### 10.2 Sequence Diagram — View the Executive Dashboard

(Sequence diagram)

### 10.3 Sequence Diagram — View Trends

(Sequence diagram)

### 10.4 Sequence Diagram — View Best Sellers

(Sequence diagram)

