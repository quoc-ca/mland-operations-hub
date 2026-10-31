# Data Model: Manager Dashboard

FE-10 uses logical request and projection value objects only. It does not own a
database table or authorize a Flyway migration. Source facts remain in their
owning modules and cross into `reporting` only as minimized aggregates.

## Ownership and projections

| Logical value | Owner | Purpose |
| --- | --- | --- |
| Manager capability and location assignments | `accountmanagement` authorization facade; approved `REPORTING_EXECUTIVE_VIEW` capability and active MySQL `account_location_assignments` records | Establish authorized reporting scope |
| Retail paid aggregate | `readyringsales` | Net product revenue and paid branch-order count |
| Workshop final-revenue aggregate | `workshopbooking` | Final fully paid workshop invoice revenue |
| Completed-session capacity aggregate | `workshopbooking` | Capacity denominator for completed sessions |
| Actual attendance aggregate | `operations` | Checked-in participant numerator |
| Reporting period, KPI composition and quality summary | `reporting` | Manager dashboard response |

## ReportingPeriod

- Period type: `DAY`, `WEEK`, `MONTH`, or `YEAR`
- Anchor local date
- Start instant, inclusive
- End instant, exclusive
- Timezone: `Asia/Ho_Chi_Minh`

The week starts Monday and ends at the following Monday boundary. The default
period starts at the first instant of the current local month and ends at the
current instant. A fixed/injected clock is required for deterministic tests.

## AuthorizedBranchScope

- Authenticated principal reference
- Required business capability: `REPORTING_EXECUTIVE_VIEW`
- Complete set of active `locationId: Long` values from MySQL
  `account_location_assignments` records
- Effective requested location identifiers

If no branches are requested, the effective set is all active assigned branches.
No active assignment denies FE-10 access. If
any explicitly requested branch is outside the authorized set, the complete
request is rejected; unauthorized branches are not silently removed.

`locationId` is one immutable `Long` key referenced by
`account_location_assignments`, retail orders, workshop sessions/final invoices,
and check-ins. The location display name is not an authorization or aggregation key.

## RetailBranchAggregate

- Stable `locationId: Long`
- Currency code: `VND` only
- Net product revenue after allocated discounts
- Fully paid branch-order count
- Source `asOf` instant
- Source quality

Shipping charges, pending or failed payments, and duplicated payment evidence
are excluded. The aggregate is selected by verified full-payment time within
the reporting interval.

## WorkshopRevenueAggregate

- Stable `locationId: Long`
- Currency code: `VND` only
- Final fully paid invoice revenue
- Qualifying final-invoice count, for validation evidence only
- Source `asOf` instant
- Source quality

Each workshop contributes its final invoice total exactly once after the final
invoice becomes fully paid. Its deposit is an installment and is not added as a
separate revenue fact.

## WorkshopCapacityAggregate

- Stable `locationId: Long`
- Completed session count
- Sum of configured capacity for qualifying completed sessions
- Source `asOf` instant
- Source quality

The source owner applies the completed-session lifecycle rule. Invalid or
missing capacity is surfaced through quality metadata instead of being silently
converted to zero.

## AttendanceAggregate

- Stable `locationId: Long`
- Sum of actual checked-in participants for qualifying completed sessions
- Source `asOf` instant
- Source quality

Only accepted, idempotent check-in evidence is counted. Booking or reservation
count is not a substitute for actual attendance.

## SourceQuality

- Source: `RETAIL`, `WORKSHOP_REVENUE`, `WORKSHOP_CAPACITY`, or `ATTENDANCE`
- Status: `COMPLETE`, `EMPTY`, or `UNAVAILABLE`
- Optional `asOf` instant identifying the aggregate-read time
- Optional safe reason code

`EMPTY` means the source was successfully evaluated and no qualifying facts
exist. `UNAVAILABLE` means the value is unknown and must not be represented as
zero. A successfully verified payment is committed by its owning source before
it can appear in an aggregate; pending or failed payments are excluded. Safe
reason codes must not reveal SQL, stack traces, provider payloads, secrets, or
customer information.

## BranchKpi

- Stable `locationId: Long` and display name
- Currency code: `VND` only
- Retail revenue, nullable when unknown
- Workshop revenue, nullable when unknown
- Total verified revenue, nullable when a required revenue component is unknown
- Paid retail order count, nullable when the retail source is unknown
- Checked-in participant count, nullable when attendance is unknown
- Completed-session capacity, nullable when capacity is unknown
- Workshop utilization percentage, nullable when either input is unknown or the
  denominator is zero
- Relevant source-quality references

The branch display name is presentation data only. It is never used as a join
key or authorization key. Revenue values are serialized to the React client as
non-negative base-10 decimal strings; count and percentage fields retain their
respective numeric types.

## DashboardSnapshot

- Resolved reporting period
- Effective authorized branch scope
- Currency code: `VND`
- Consolidated KPI values
- Ordered collection of `BranchKpi`
- Overall status: `COMPLETE`, `EMPTY`, or `PARTIAL`
- Source-quality collection
- Response generation/last-updated instant

The snapshot is an in-memory response projection. It is neither an accounting
record nor a persisted analytics snapshot.

## Composition invariants

1. Authorization and branch scope are resolved before any business aggregate
   is requested.
2. Every source query uses the same location identifiers and half-open reporting
   interval.
3. Retail revenue equals product value after allocated discounts and excludes
   shipping charges.
4. Workshop revenue uses the final invoice total once at `fullyPaidAt`; the
   deposit is never added separately.
5. Total revenue equals retail revenue plus workshop revenue only when both
   required values are known and both source facts use `VND`.
6. A non-`VND` source fact is never converted or summed. The affected source
   and KPI are incomplete and receive safe quality evidence.
7. Consolidated utilization is weighted:
   `sum(checkedInParticipants) / sum(completedSessionCapacity) * 100`.
   It is not the average of branch percentages.
8. A known empty source may contribute zero. An unavailable source contributes
   null, never a fabricated zero.
9. No dashboard request creates or updates source records.
10. No dashboard DTO contains customer identity, recipient data, order lines,
    payment details, raw attendance records, or provider payloads.

## Request lifecycle

```text
Authenticated request
        |
        v
Resolve Manager capability and branch scope
        |
        v
Resolve Vietnam calendar period to UTC interval
        |
        v
Read source-owned branch aggregates
        |
        v
Validate currency and source quality
        |
        v
Compose branch and consolidated KPIs
        |
        v
Return transient DashboardSnapshot
```

There is no persisted FE-10 state transition. Reload occurs only on initial
view, successful filter submission, or explicit refresh.
