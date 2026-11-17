# Implementation Plan: Manager Dashboard

**Branch**: `010-manager-dashboard` | **Date**: 2026-10-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `features/010-manager-dashboard/spec.md`

## Summary

Implement FE-10 as a read-only `reporting` Spring Modulith module. It authorizes
an Mland Manager and resolves the Manager's branch scope, converts the selected
calendar period to UTC instants using `Asia/Ho_Chi_Minh`, requests already
verified aggregate facts from the owning retail, workshop, and attendance
modules through named facades, and composes one executive dashboard response.
The first view covers the current calendar month and every authorized branch;
day, Monday-through-Sunday week, month, and year filters are supported. Data is
reloaded only on initial load, successful filter application, or explicit
refresh. FE-10 creates no accounting ledger, revenue snapshot table, scheduler,
or Admin analytics surface.

## Technical Context

**Language/Version**: Java 21 backend; TypeScript with React + Vite frontend

**Primary Dependencies**: Spring Boot 3.4.1, Spring Modulith 1.3.1, Spring
Security, Spring MVC, Bean Validation, Spring Data JPA for source modules, and
Flyway for any source-owned schema evolution; React + Vite for the separate
frontend. Frontend dependency versions and test tooling remain a foundation gate.

**Storage**: No FE-10 persistence or migration. Verified retail, workshop,
attendance, branch, and authorization facts remain in their owning MySQL/JPA
modules; the dashboard composes request-time projections only.

**Testing**: JUnit 5, Spring Boot Test, MockMvc authorization/contract tests,
facade-boundary integration tests, calendar-boundary tests using a fixed clock,
Spring Modulith `ApplicationModules.verify()`, and frontend build/type/UI/API
state verification using the approved frontend foundation.

**Target Platform**: Separate React + Vite client-side application consuming a
versioned Spring Boot REST read contract

**Project Type**: Spring Modulith modular monolith

**Performance Goals**: No approved numeric latency, throughput, or branch/data
volume target exists. V1 performs bounded on-demand aggregate reads, never
polls automatically, and records safe timing/quality evidence so later targets
can be based on measurements rather than invented values.

**Constraints**: No raw SQL in application code; no cross-module entity,
repository, controller, or internal DTO access; Firebase proves identity only;
MySQL authorization grants the Manager capability and branch scope; V1 accepts
only VND monetary facts and treats another currency as partial rather than
converting it; do not count pending/failed payments, delivery fees, or
the workshop deposit twice; do not expose executive revenue to Admin through
FE-09 parameter-impact permission; no cache, materialized analytics table,
scheduled aggregation, export, comparison report, or timer refresh in V1.

**Scale/Scope**: Three user stories, five required KPI values, one consolidated
total plus per-branch rows, four calendar period types, and four local source
boundaries. Exact production branch count and historical record volume remain
unbaselined; source facades must aggregate within the requested scope rather
than return raw transactions to `reporting`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle/gate | Result | Plan evidence |
| --- | --- | --- |
| Module integrity and data ownership | PASS with source-facade prerequisites | New `reporting` module owns composition only. Retail, workshop, attendance, branch, and authorization facts stay with their owning modules and cross boundaries through named facades. |
| JPA/Flyway persistence | PASS | FE-10 adds no table and performs no direct repository access. Any missing source fact is added by its owner through an append-only migration, not by `reporting`. |
| Identity and sensitive-data boundary | PASS | Firebase UID is verified, then Mland authorization requires `REPORTING_EXECUTIVE_VIEW` and resolves active assigned branches. Dashboard DTOs contain aggregate facts and stable branch references, not customer/order/payment details. |
| Versioned REST and safe errors | PASS | [manager-dashboard.openapi.yaml](contracts/manager-dashboard.openapi.yaml) defines a versioned plural kebab-case GET endpoint, typed validation, safe errors, and no JPA entities. |
| Separate frontend baseline | PASS with foundation gate | The dashboard is a React + Vite route consuming REST only. The backend exposes no rendered application page; frontend dependencies, routing, token transport, and test tools must be approved before scaffold. |
| Evidence-based verification | PASS | [quickstart.md](quickstart.md) covers KPI formulas, authorization, scope, period boundaries, data-quality states, refresh behavior, contract behavior, and Modulith verification. |
| Simplicity and explicit decisions | PASS | Request-time composition is selected over snapshots, cache, background jobs, or a separate analytics service. Unapproved role-code mapping and missing source facts remain explicit implementation gates. |

No constitution violation requires a complexity exception. Planning may finish,
but production implementation cannot enable FE-10 until the source-fact and
role/scope prerequisites listed below are approved and available.

## Project Structure

### Documentation (this feature)

```text
features/010-manager-dashboard/
├── spec.md
├── brainstorm_report.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── manager-dashboard.openapi.yaml
└── checklists/
    └── requirements.md
```

### Source Code (repository root)

The legacy tree below is superseded where it shows a backend root-level
`reporting` package or server templates. Under Constitution v3, implementation
uses `features/reporting/{controller,dto,dao,entity,service,facade}` in the
backend and `ppswbs_frontend/src/features/manager-dashboard/{api,components,pages,model}`
in the separate React + Vite client. Source facades likewise live under their
own `features/<feature>/facade` package. No Thymeleaf template or htmx fragment
is part of FE-10.

```text
../ppswbs_backend/src/main/java/edu/fpt/sep490_g22/ppswbs_backend/
├── reporting/
│   ├── web/             # Manager page, REST DTOs, GET filters and safe errors
│   ├── application/     # authorization, period resolution and KPI composition
│   ├── domain/          # reporting period, KPI and data-quality value objects
│   ├── infrastructure/  # clock/config adapters only; no business repository
│   └── facade/          # optional named read boundary for internal consumers
├── accountmanagement/facade/ # Manager capability and authorized-location projection
├── readyringsales/facade/ # verified paid retail aggregate projection
├── workshopbooking/facade/# final paid workshop revenue/session-capacity projection
├── operations/facade/     # actual checked-in participant projection
└── common/                 # existing technical kernel and typed error handling

../ppswbs_backend/src/main/resources/
├── templates/manager-dashboard/
│   ├── index.html
│   └── fragments/executive-report.html
└── i18n/                  # Vietnamese/English labels and safe messages

../ppswbs_backend/src/test/java/edu/fpt/sep490_g22/ppswbs_backend/
├── reporting/             # unit, MVC, authorization and facade-composition tests
└── ModulithArchitectureTests.java
```

**Structure Decision**: Add one closed `features.reporting` module because executive
analytics is a distinct read/composition responsibility and must not be owned
by retail, workshop, operations, or technical administration. Its declared
dependencies are only `accountmanagement::facade`, `readyringsales::facade`,
`workshopbooking::facade`, and `operations::facade`. The source modules perform
their own aggregation and expose minimized immutable projections; `reporting`
does not import their entities or repositories.

## Phase 0: Research Outputs

[research.md](research.md) records these decisions:

1. Use a dedicated request-time `reporting` module and no FE-10 persistence.
2. Resolve identity, Manager capability, and all authorized branches before any
   business metric request; deny by default when role-code mapping is absent.
3. Use source-owned aggregate facade contracts rather than raw record transfer.
4. Use half-open UTC instant ranges derived from `Asia/Ho_Chi_Minh` calendar
   day/week/month/year boundaries.
5. Recognize retail and workshop revenue only from approved verified-payment
   facts, preserve branch ownership, and accept VND monetary facts only.
6. Calculate consolidated workshop utilization as total checked-in people
   divided by total configured capacity, never as an average of percentages.
7. Return partial results only with explicit per-source quality metadata; do not
   fabricate a consolidated total when a required revenue source is unavailable.
8. Use React + Vite client-side reads through the versioned REST contract; no
   timer polling or background aggregation.

## Phase 1: Design Outputs

- [data-model.md](data-model.md) defines request/projection value objects,
  invariants, formula ownership, and the no-persistence decision.
- [manager-dashboard.openapi.yaml](contracts/manager-dashboard.openapi.yaml)
  defines the Manager-only read contract, period/branch validation, partial
  quality response, and typed errors.
- [quickstart.md](quickstart.md) defines end-to-end validation scenarios and
  expected results without implementation code.

## Delivery Slices

1. Synchronize the Report 3 permission matrix to Manager-only executive
   revenue and keep FE-09 `Parameter Impact Summary` separate.
2. Approve the business-role-to-implementation capability mapping and branch
   assignment source; implement the deny-by-default authorization facade.
3. Complete source-owned stable branch identifiers and aggregate facades for
   paid retail facts, final paid workshop invoices, completed-session capacity,
   and actual check-ins. Do not infer them from location names or current draft
   records that lack the required facts.
4. Add the `reporting` module, period resolver with a fixed/injected clock, KPI
   composition, VND-only/incomplete-data safeguards, and safe typed errors.
5. Implement the Manager REST endpoint and React dashboard route with explicit
   Refresh, loading/empty/validation/error/recovery states, and last-updated/
   data-quality notices.
6. Add authorization, formula, boundary, partial-failure, contract, i18n, and
   Modulith verification; record fresh evidence from [quickstart.md](quickstart.md).

## Internal Boundary and Data Flow

```text
Verified Firebase identity
        |
        v
accountmanagement::facade -- capability + authorized location IDs
        |
        v
reporting application service -- period/scope validation
        |                  |                   |
        v                  v                   v
readyringsales::facade  workshopbooking::facade  operations::facade
 paid retail aggregate   final invoice/capacity   checked-in aggregate
        \_____________________|___________________/
                              |
                              v
             KPI composition + quality metadata
                              |
                    REST response to React + Vite
```

- Authorization is evaluated before source metrics are requested.
- The requested branch set is intersected with, and must be wholly contained
  in, the authorized branch set; an unauthorized explicit branch is rejected.
- Facades accept a half-open `[startInclusive, endExclusive)` instant range and
  the authorized stable branch IDs, then return aggregates plus aggregate-read
  `asOf`/quality. Successfully verified payment outcomes are committed by their
  source owner before a refresh can read them; pending or failed attempts are
  excluded.
- A source exception is mapped to safe `UNAVAILABLE` metadata. One missing
  source yields a `PARTIAL` response; all required sources unavailable yields a
  typed service-unavailable response and the UI preserves its previous view.
- FE-10 performs no remote provider call and no business-data mutation.

## Source Prerequisites and Implementation Gates

| Gate | Current evidence | Required before the affected implementation slice |
| --- | --- | --- |
| Manager capability and location scope | `REPORTING_EXECUTIVE_VIEW` and active MySQL `account_location_assignments` are approved. Current backend authentication still grants only `ROLE_MEMBER`. | Implement a MySQL-authoritative facade that verifies the capability and returns active assigned `locationId: Long` values; no assignment denies access. Admin parameter authority does not imply the capability. |
| Stable location identity | One immutable `locationId: Long` is approved for `account_location_assignments`, retail orders, workshop sessions/final invoices, and check-ins. Current workshop free-text `location` is display-only. | Implement the source-owned common location-ID mapping. FE-10 must never join or authorize by display name/location text. |
| Retail paid facts | FE-08 design owns branch order snapshots and verified grouped payment but is not implemented in the current backend. | `readyringsales::facade` aggregate contract with net product revenue, paid-order count, paid time, branch and currency. |
| Workshop final revenue | Current workshop code records only deposit-era invoices; the clarified FE-10 rule requires the final invoice after surcharges and its fully-paid time. | Workshop owner provides a reviewed final-invoice lifecycle and aggregate facade; FE-10 must not reconstruct it from deposit attempts. |
| Actual utilization | Report 3 defines Staff check-in; current `operations` module is a package shell and current booking migration lacks actual participant fields. | Operations/workshop owners provide completed-session capacity and idempotent actual participant check-in projections by session and branch. |

These gates do not prevent documentation planning, contract design, or task
generation. They prevent claiming FE-10 implementation complete or enabling
production revenue output until each affected dependency is approved and
verified.

## Verification Strategy

- Unit-test period boundaries with a fixed clock, including midnight, month,
  year, and Monday/Sunday transitions in `Asia/Ho_Chi_Minh`.
- Unit-test money/count aggregation, discount/shipping exclusions, final-invoice
  recognition, weighted utilization, zero capacity, and non-VND source facts.
- MVC-test `401`, `403`, invalid period/branch `400`, partial `200`, empty `200`,
  complete `200`, and all-sources-unavailable `503` with correlation-linked
  safe errors.
- Verify that Admin parameter-impact permission cannot call the FE-10 endpoint.
- Verify no dashboard request changes source records and no timer-triggered
  request occurs.
- Run the full Maven test suite and `ApplicationModules.verify()` after source
  facades and `reporting` are present.

## Complexity Tracking

No constitution violation requires justification. A new `reporting` module is
the smallest design that keeps cross-domain analytics composition out of source
modules while avoiding a new service, database schema, cache, scheduler, or
message broker.

## Post-Design Constitution Check

PASS for Phase 1 design:

- Module ownership and named-facade dependencies are explicit.
- No source entity/repository leakage or raw SQL is proposed.
- Authentication, Manager capability, branch authorization, data minimization,
  and Admin denial are contract-visible.
- REST errors, validation, and React loading/empty/validation/error/recovery
  states are designed without a new constitution exception.
- No provider, role-code, branch mapping, source lifecycle, performance target,
  or scale assumption is silently invented; unresolved source prerequisites are
  implementation gates.
- Design artifacts and quickstart scenarios provide traceable evidence for
  `$speckit-tasks` and the required later `$speckit-analyze` run.

