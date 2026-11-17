# Research: Manager Dashboard

**Feature**: `010-manager-dashboard`  
**Date**: 2026-10-24

This research resolves the design choices required to plan FE-10. It does not
claim that the source modules already expose every required reporting fact or
that production revenue reporting may be enabled before the listed gates pass.

## Decision 1: Use a dedicated read-only reporting module

- **Decision**: Add a closed Spring Modulith module named `reporting`. It owns
  authorization orchestration, reporting-period resolution, KPI composition,
  data-quality metadata, and the Manager-facing response. It creates no
  reporting table, cache, scheduler, or accounting record.
- **Rationale**: Executive reporting combines facts owned by retail sales,
  workshop booking, operations, and technical administration. A dedicated
  composition boundary keeps those concerns out of any one source module while
  preserving source ownership.
- **Alternatives considered**: Putting the dashboard in `readyringsales` was
  rejected because workshop and attendance facts are not retail data. A
  separate analytics service or warehouse was rejected because the approved
  scope does not justify another deployment or data pipeline.

## Decision 2: Authorize capability and branch scope before reading metrics

- **Decision**: Verify identity, require the Mland
  `REPORTING_EXECUTIVE_VIEW` capability, and load
  stable authorized branch identifiers from active MySQL
  `account_location_assignments` records before calling any metric facade. An
  explicitly requested branch must belong to the complete authorized set; no
  active assignment denies FE-10 access. Admin parameter-configuration
  authority does not imply this capability; missing capability evidence is
  denied by default.

The same immutable `locationId: Long` must be carried by assignment, retail, workshop,
and attendance facts. Reporting never authorizes or aggregates by display name
or free-text workshop location.
- **Rationale**: Firebase proves identity only; MySQL remains authoritative for
  Mland roles and branch assignments. Early authorization also prevents timing
  and error behavior from leaking the existence of another branch's data.
- **Alternatives considered**: Trusting a Firebase role claim or UI-hidden
  controls was rejected. Mapping business `Manager` to implementation `OWNER`
  without approval was rejected because the project documents leave that
  mapping unresolved.

## Decision 3: Read minimized aggregate projections through source facades

- **Decision**: The source modules expose immutable aggregate read contracts by
  branch, `VND` monetary facts, and half-open time interval. `reporting` does not access
  their repositories, entities, internal DTOs, or raw transaction lists.
- **Rationale**: Source modules own recognition rules and can aggregate close to
  their data. This respects Modulith boundaries and avoids transferring
  customer, order, payment, or attendance details into the dashboard.
- **Alternatives considered**: Cross-module repository joins and a common
  reporting repository were rejected because they violate ownership. Returning
  raw transactions was rejected because it increases coupling and PII exposure.

## Decision 4: Resolve calendar periods in Vietnam time

- **Decision**: Interpret day, Monday-through-Sunday week, month, and year in
  `Asia/Ho_Chi_Minh`, then convert the result to a half-open UTC instant range
  `[startInclusive, endExclusive)`. The default is the current calendar month
  through the current instant using an injected clock.
- **Rationale**: Business reports need stable local calendar boundaries while
  source timestamps are compared as instants. A half-open range prevents double
  counting records exactly at adjacent period boundaries.
- **Alternatives considered**: Server-default timezone and inclusive end-of-day
  timestamps were rejected because they are environment-dependent and prone to
  boundary duplication.

## Decision 5: Recognize verified retail revenue without delivery charges

- **Decision**: Retail revenue is the product value of fully and successfully
  paid branch orders after allocated discounts. Shipping fees are excluded.
  Paid retail order count counts each qualifying branch order once. The
  reporting date is the source module's verified full-payment time.
- **Rationale**: This matches the approved KPI definition and FE-08's
  marketplace-style branch-order ownership while avoiding inflation from
  pass-through delivery charges or failed/pending payment attempts.
- **Alternatives considered**: Gross catalogue price, grouped checkout count,
  and payable amount including shipping were rejected because each misstates
  branch sales performance.

## Decision 6: Recognize workshop revenue from the final fully paid invoice

- **Decision**: Workshop revenue is recognized once, using the final invoice
  total after approved surcharges when that invoice becomes fully paid. The
  50-percent booking deposit is not added separately. The reporting date is the
  final invoice's `fullyPaidAt` instant.
- **Rationale**: A deposit is an installment of the final obligation, not extra
  revenue. Using the final invoice captures workshop adjustments without
  double counting.
- **Alternatives considered**: Summing deposit and final invoice payments was
  rejected as double counting. Reconstructing final revenue from current
  deposit-era records was rejected because the required lifecycle does not yet
  exist in the implementation.

## Decision 7: Calculate utilization as a weighted ratio

- **Decision**: For completed sessions in scope, workshop utilization equals
  `sum(actual checked-in participants) / sum(configured capacity) * 100`.
  Sessions with invalid capacity are not converted into a fabricated percent;
  the source reports a quality issue. A zero total denominator yields no
  utilization value rather than an invented zero-percent result.
- **Rationale**: The weighted formula represents actual seat usage across
  differently sized sessions. Averaging branch or session percentages gives
  small sessions disproportionate influence.
- **Alternatives considered**: Booking count was rejected because utilization
  is based on actual check-in. Averaging percentages was rejected because it is
  mathematically misleading.

## Decision 8: Make incomplete data explicit

- **Decision**: Every source projection includes `COMPLETE`, `EMPTY`, or
  `UNAVAILABLE`, an optional aggregate-read `asOf` instant, and a safe reason
  code. An order/payment becomes visible only after its owning source commits a
  successfully verified outcome; an explicit dashboard refresh reads those
  latest committed facts. One unavailable source returns HTTP 200 with dashboard
  status `PARTIAL` and null affected values. If a required revenue source is
  unavailable, consolidated revenue is null. If all required sources are
  unavailable, return a typed 503 and preserve the previously rendered valid
  view.
- **Rationale**: Zero is a valid business value and must not be confused with a
  failed source. Explicit quality state lets a Manager interpret the dashboard
  without exposing stack traces or internal payloads.
- **Alternatives considered**: Replacing failures with zero and failing the
  whole dashboard for any one source were rejected as respectively misleading
  and unnecessarily fragile.

## Decision 9: Use request-time REST and React client reads

- **Decision**: Expose a versioned Manager-only GET contract consumed by the
  separate React + Vite client. React reloads after an initial route load,
  successful filter application, or explicit refresh and renders distinct
  loading, empty, validation, partial, unavailable, and recovery states. There
  is no timer polling, mutation endpoint, export, or background aggregation in
  V1.
- **Rationale**: Request-time composition is the smallest design that satisfies
  the approved separate-frontend baseline. It also avoids stale cache semantics
  before scale and latency targets are established.
- **Alternatives considered**: Scheduled snapshots, WebSockets, automatic
  polling, and a new client state library were rejected as unsupported
  complexity.

## Decision 10: Keep Admin parameter-impact evidence outside FE-10

- **Decision**: FE-10 remains Manager-only. FE-09 may show an Admin only the
  current/proposed parameter, before/after examples, aggregate usage or affected
  values, queue/deadline effects, and audit history needed to configure that
  parameter. That permission never grants FE-10 totals, rankings, customer,
  order, or payment details.
- **Rationale**: Admin needs evidence for safe parameter configuration but does
  not need unrestricted executive revenue access. Separate capabilities reduce
  privilege and keep ownership clear.
- **Alternatives considered**: Granting Admin full dashboard access was
  rejected because it exceeds the approved purpose. Hiding all parameter impact
  evidence was rejected because it makes configuration harder to evaluate.

## Decision 11: Treat missing source facts as implementation gates

- **Decision**: Planning and contract work may proceed, but production FE-10 is
  not enabled until the project approves Manager-role mapping, stable branch
  identity, FE-08 paid-retail aggregates, final workshop invoice/fully-paid
  facts, and actual check-in projections.
- **Rationale**: The current backend does not yet implement all facts needed to
  calculate trustworthy KPIs. Explicit gates prevent the dashboard from
  guessing from display names, deposits, or booked participants.
- **Alternatives considered**: Temporary joins by workshop location text and
  inferred revenue from payment attempts were rejected because they can produce
  incorrect or unauthorized reporting.
