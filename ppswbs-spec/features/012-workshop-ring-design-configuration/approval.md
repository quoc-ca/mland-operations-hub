# Feature-012 Approval Record

Date created: 2026-11-08
Date updated: 2026-11-09
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

## Status

`APPROVED FOR BACKEND IMPLEMENTATION — PHYSICAL MIGRATION V109 & BACKEND CODE AUTHORIZED`

Formal approval granted by Tech Lead for Feature-012 backend persistence, Modulith architecture,
REST API implementation, and Migration `V109__workshop_ring_design_configuration.sql`.
Figma visual revision and frontend screens remain pending frontend phase.

## Pending approval scope

- `research.md` (Approved for backend)
- `spec.md` (Approved for backend)
- `data-model.md` (Approved for backend)
- `contracts/workshop-ring-design.openapi.yaml` (Approved with DesignOptionsResponse addition)
- `design/architecture.md` (Approved for backend)
- `design/workflows.md` (Approved for backend)
- `design/ui-flow.md` (Frontend baseline only)
- `frontend-handoff.md` (Frontend baseline only)

## Required approval fields

| Field | Value |
| --- | --- |
| Approved revision | Rev 1.0 (Backend) |
| Approved scope | Backend implementation, Flyway V109 migration, OpenAPI synchronization, unit/integration test suite |
| Date approved | 2026-11-09 |
| Tech Lead | Ngô Tiến Đạt |
| Physical migration approved | Yes (V109__workshop_ring_design_configuration.sql) |
| Production code approved | Yes (Backend Spring Modulith module) |

## Conditions before implementation

1. [x] Approve physical mapping of configured-design versions and P2.9 snapshot persistence. (Row-per-version `ring_designs`, `source_design_id` lineage, JSON snapshot).
2. [x] Approve the Feature-012-owned Guest session table and cleanup/replay rules. (`guest_design_sessions` with 20-min inactivity expiry, SHA-256 digest, on-read touch and `@Scheduled` cleanup).
3. [x] Approve the Manager-owned option/rule facade and its versioned evaluation input/output contract. (`DesignOptionRulesPort`, advisory estimate, difficulty score, `DesignOptionsResponse`).
4. [x] Approve the Workshop Booking attachment facade and transaction boundary. (`attachAndLockDesignForBooking(...)` and REST attachment endpoint).
5. [x] Verify Flyway version/history; repository baseline verified at `V108`. Next append-only migration is confirmed as `V109__workshop_ring_design_configuration.sql`.

## Figma evidence pending approval

The Feature-012 visual baseline has been created in the existing Figma file:

```text
Section: 457:1553
Desktop configurator: 457:1554
Mobile configurator: 457:1655
Model/blank selection: 457:1698
Validation/estimate: 457:1749
Branch recovery: 457:1795
```

The Figma revision identifier and explicit visual approval remain TBD. These
node IDs provide traceability but are not approval evidence by themselves.
