# FE-10 Manager Dashboard — Brainstorm Report

**Date**: 2026-10-24  
**Status**: Approved design direction  
**Approved approach**: A — one Manager Dashboard feature with one primary executive analytics journey

## Problem statement

Managers need one read-only view of verified business performance instead of opening several operational areas separately. The feature must show the business picture within the Manager's permitted reporting period and branch scope without changing orders, payments, workshop records, or other operational data.

## Requirements and boundaries

The feature must:

- Provide one Manager Dashboard with the Executive Revenue view as its only V1
  journey.
- Show exactly five KPI cards: verified retail revenue, verified workshop
  revenue, total verified revenue, paid retail order count, and workshop
  utilization, plus branch KPI rows.
- Allow filtering by a valid reporting period and permitted branch scope.
- Clearly identify empty, unavailable, or partial data.
- Keep every dashboard action read-only.
- Hide revenue values from Member, Staff, and Admin users.

The feature does not include order creation or editing, payment processing, product or workshop management, promotion management, GHTK tracking, accounting reconciliation, scheduled reports, or file export.

## Evaluated approaches

| Approach | Description | Benefits | Trade-offs |
| --- | --- | --- | --- |
| A — Single dashboard feature | Keep Manager Dashboard and Executive Revenue Dashboard in one FE-10 feature with one primary journey. | Smallest scope, one authorization boundary, less duplicated metric logic. | The dashboard will contain several KPI views inside one feature. |
| B — Separate dashboard and reporting features | Split the landing dashboard from revenue reporting. | Each area can evolve independently. | Duplicates scope, filters, permission checks, and data-quality behavior for a small V1. |
| C — Dashboard plus scheduled reports | Add export and periodic report delivery. | Useful for offline management review. | Adds report formats, delivery, scheduling, retention, and failure handling not requested for V1. |

## Recommended solution

Use Approach A. Treat `View Executive Revenue Dashboard` as the primary use case. Summary cards and KPI views are independently testable parts of the same read-only journey, not separate business features.

The dashboard should:

1. Authenticate and authorize the Manager.
2. Load the default reporting period and permitted branch scope.
3. Accept a valid period and branch filter.
4. Aggregate verified records from retail and workshop domains.
5. Display revenue, utilization, and branch KPI results.
6. Mark empty, unavailable, or partial data instead of presenting it as complete.
7. Preserve the last valid view when a new filter is rejected.

## Approved Admin parameter-impact boundary

The Admin does not receive access to the Executive Revenue Dashboard. Instead, the Administrative Parameter Configuration feature should provide a purpose-limited `Parameter Impact Summary` beside the parameters that the Admin is authorized to configure.

The summary may show only information needed to assess the selected parameter, within the same scope as that parameter:

- Current value, proposed value, effective date, and affected workflow.
- A before/after calculation using an illustrative order or configuration example.
- Aggregated usage counts related to the parameter, such as orders using loyalty points or discounts.
- Aggregated value affected by the parameter, such as total point redemption or discount amount.
- Operational effect counts for non-price parameters, such as requests affected by a queue limit, minimum deadline, or unavailable date.
- Previous parameter values, actor, change time, and audit history.

The Admin view must not expose:

- Total executive revenue or complete branch revenue.
- Branch revenue rankings or unrelated business KPIs.
- Customer identity, individual order details, or payment details.
- Product or workshop price approval actions.
- Any ability to confirm payment or override a business decision.

This separation lets an Admin validate configuration consequences without turning the technical/operational role into a business-management role. FE-10 remains Manager-only; the impact summary belongs to FE-09 and should be formalized in that feature's specification.

## Implementation considerations and risks

- FE-08 owns retail order and payment completion data; FE-10 consumes verified results rather than re-deciding payment status.
- Workshop booking owns workshop sessions, attendance, capacity, and utilization data; FE-10 consumes those results.
- Authorization must be evaluated against the Manager's allowed branch scope on every dashboard request.
- Revenue definition must remain tied to verified recorded business data; the feature must not silently infer pending or failed payments as revenue.
- The Report 3 Permission Matrix still lists Admin as `Full` for the executive dashboard, while the screen specification and approved direction make the dashboard Manager-only. FE-10 follows the Manager-only direction; the source matrix needs a later documentation synchronization.
- Admin parameter-impact data must be purpose-limited and aggregated. Reusing the executive dashboard endpoint or permission would grant a broader data scope than the approved need.
- The exact KPI formula, timezone, and refresh target are not defined by the current baseline. The spec uses business-readable defaults and leaves implementation-specific targets to the plan or a later decision.

## Success metrics and validation criteria

- An authorized Manager can open the dashboard and see the default scoped metrics in one journey.
- A Manager can change the reporting period and branch scope without seeing data outside that scope.
- Every displayed revenue value can be traced to verified recorded business data.
- Empty, unavailable, and partial data states are visibly distinguishable from complete data.
- Unauthorized roles cannot view executive revenue values.
- Dashboard actions do not change order, payment, workshop, or master-data records.
- An authorized Admin can understand the before/after effect and aggregated operational impact of a configurable parameter without seeing executive revenue, customer details, or unrelated branch KPIs.

## Next steps and dependencies

1. Formalize the approved scope in `spec.md`.
2. Add the approved `Parameter Impact Summary` to the FE-09 Administrative Parameter Configuration specification rather than FE-10.
3. Synchronize the Report 3 permission matrix so Admin no longer has full Executive Dashboard access.
4. Create a plan for the read-only data dependencies and authorization boundaries.
5. Generate dependency-ordered tasks and analyze the resulting artifacts before implementation.

