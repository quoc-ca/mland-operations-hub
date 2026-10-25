# Quickstart: FE-10 Verification Walkthrough

This guide defines validation evidence for the planned Manager Dashboard. It
does not claim that FE-10 or all required source projections are implemented.

## Prerequisites and implementation gates

- Java 21 and the repository Maven toolchain.
- Approved business `Manager` capability mapping and MySQL-backed authorized
  branch assignment source.
- Stable branch identifiers shared across authorization, retail, workshop, and
  attendance projections; display names must not be used as join keys.
- FE-08 source facade for verified paid retail product revenue after discounts,
  excluding shipping, plus paid branch-order count.
- Workshop source facade for final invoice totals and `fullyPaidAt`, and for
  completed-session capacity.
- Operations source facade for actual idempotent check-in counts.
- Fixed/injected clock and deterministic source-facade test doubles.

If a gate is absent, contract/unit work may continue with test doubles, but the
dashboard must remain disabled for production revenue decisions.

## Validation scenarios

| ID | Scenario | Expected result |
| --- | --- | --- |
| QS-01 | Authorized Manager opens the dashboard without filters | Current calendar month through current time in `Asia/Ho_Chi_Minh` and all authorized branches are loaded. |
| QS-02 | Member, Staff, or unauthenticated user requests FE-10 | Request is denied with typed `401` or `403`; no source metric is disclosed. |
| QS-03 | Admin has FE-09 parameter-impact permission but requests FE-10 | Request is denied; parameter-impact permission does not grant executive revenue access. |
| QS-04 | Manager explicitly requests a branch outside their assignment | Complete request is rejected with `BRANCH_SCOPE_FORBIDDEN`; the branch is not silently removed. |
| QS-05 | Manager requests an authorized subset of branches | Totals and rows include exactly that subset and use stable branch identifiers. |
| QS-06 | Retail revenue is 120,000,000 VND, workshop revenue is 45,000,000 VND, paid retail orders are 80, check-ins are 60, and capacity is 100 | Total revenue is 165,000,000 VND and utilization is 60%; source values remain separately visible. |
| QS-07 | Workshop booking has a 5,000,000 VND deposit and a final fully paid invoice of 12,000,000 VND after surcharges | Workshop revenue contributes 12,000,000 VND once on `fullyPaidAt`, not 17,000,000 VND and not on deposit time. |
| QS-08 | Paid retail branch order has 9,000,000 VND product value, 1,000,000 VND allocated discount, and 300,000 VND shipping | Retail revenue contributes 8,000,000 VND; shipping is excluded. |
| QS-09 | Two completed sessions have 8/10 and 20/40 actual attendance | Consolidated utilization is 28/50 = 56%, not the 65% average of the two percentages. |
| QS-10 | Day/week/month/year boundaries are tested at Vietnam midnight, Monday, month end, and year end | The resolver emits non-overlapping half-open UTC ranges; week is Monday through the following Monday. |
| QS-11 | All sources respond successfully but have no qualifying facts | HTTP 200 with dashboard status `EMPTY` and known zero values, distinct from source failure. |
| QS-12 | Attendance is unavailable while both revenue sources are complete | HTTP 200 with status `PARTIAL`; utilization and attendance-dependent values are null, while verified revenue remains visible. |
| QS-13 | One required revenue source is unavailable | HTTP 200 `PARTIAL`; the unavailable component and total revenue are null rather than zero. |
| QS-14 | All required reporting sources are unavailable | Typed HTTP 503 `REPORTING_SOURCES_UNAVAILABLE`; the React dashboard keeps the previous valid view and shows safe retry guidance. |
| QS-15 | A source returns a non-`VND` monetary fact | The fact is not converted or summed; the affected source and KPI are `PARTIAL` with safe quality evidence. |
| QS-16 | Manager changes a filter, explicitly refreshes, and then waits without interaction | Data reloads on successful filter and explicit refresh only; no timer-triggered request occurs. |
| QS-17 | The dashboard request is completed | No order, payment, workshop, check-in, configuration, or accounting record is created or updated. |
| QS-18 | React dashboard loads, filters, or refreshes | The client calls only the authorized REST endpoint, renders an explicit loading/empty/validation/partial/unavailable state, and makes no timer-triggered request. |
| QS-19 | A retail payment is pending at 10:00, is successfully verified and committed at 10:03, then the Manager refreshes | The pending payment is absent at 10:00. After the refresh at 10:03, its branch revenue and paid-order contribution appear exactly once. |

## Contract and architecture checks

- Validate `contracts/manager-dashboard.openapi.yaml` as OpenAPI 3.0.3.
- Verify DTOs, Bean Validation, typed errors, and authorization behavior for
  `GET /api/v1/executive-reports`.
- Verify dashboard responses contain no customer identity, recipient details,
  order lines, payment details, raw attendance records, or provider payloads.
- Verify `reporting` imports only named facades and does not access source
  entities or repositories.
- Run Spring Modulith `ApplicationModules.verify()` after the module and source
  facades exist.

## Required commands after implementation

Run from `ppswbs_backend` on Windows:

```text
.\mvnw.cmd test
.\mvnw.cmd verify
```

On Unix-compatible environments, use `./mvnw test` and `./mvnw verify`.
Record actual command output and any approved manual evidence; this planning
artifact is not runtime proof.
