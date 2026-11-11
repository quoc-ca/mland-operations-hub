# Feature-012 Approval Record

Date created: 2026-11-08
Date updated: 2026-11-09
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

## Status

`APPROVED FOR FULL-STACK IMPLEMENTATION — BACKEND & FRONTEND AUTHORIZED`

Formal approval granted by Tech Lead on 2026-11-09 for Feature-012 full-stack implementation:
Backend persistence, Modulith architecture, REST API, Migration `V109__workshop_ring_design_configuration.sql`,
and Frontend React + Vite configurator using Figma frames (457:1553, 457:1554, 457:1655, 457:1698, 457:1749, 457:1795).

## Approved scope

- `research.md` (Approved)
- `spec.md` (Approved)
- `data-model.md` (Approved)
- `contracts/workshop-ring-design.openapi.yaml` (Approved)
- `design/architecture.md` (Approved)
- `design/workflows.md` (Approved)
- `design/ui-flow.md` (Approved)
- `frontend-handoff.md` (Approved)

## Required approval fields

| Field | Value |
| --- | --- |
| Approved revision | Rev 1.1 (Full-stack Backend & Frontend) |
| Approved scope | Backend API, Flyway V109, OpenAPI DTOs, Frontend React 19 + Vite configurator, Vitest suite |
| Date approved | 2026-11-09 |
| Tech Lead | Ngô Tiến Đạt |
| Physical migration approved | Yes (V109__workshop_ring_design_configuration.sql) |
| Production code approved | Yes (Spring Modulith module & React 19 Frontend) |

## Conditions before implementation

1. [x] Approve physical mapping of configured-design versions and P2.9 snapshot persistence. (Row-per-version `ring_designs`, `source_design_id` lineage, JSON snapshot).
2. [x] Approve the Feature-012-owned Guest session table and cleanup/replay rules. (`guest_design_sessions` with 20-min inactivity expiry, SHA-256 digest, on-read touch and `@Scheduled` cleanup).
3. [x] Approve the Manager-owned option/rule facade and its versioned evaluation input/output contract. (`DesignOptionRulesPort`, advisory estimate, difficulty score, `DesignOptionsResponse`).
4. [x] Approve the Workshop Booking attachment facade and transaction boundary. (`attachAndLockDesignForBooking(...)` and REST attachment endpoint).
5. [x] Verify Flyway version/history; repository baseline verified at `V108`. Next append-only migration is confirmed as `V109__workshop_ring_design_configuration.sql`.
6. [x] Approve Frontend visual baseline and Figma node IDs (`457:1553`, `457:1554`, `457:1655`, `457:1698`, `457:1749`, `457:1795`), `sessionStorage` for Guest session token, and 2D SVG Live Preview.

## Figma evidence approved

The Feature-012 visual baseline is confirmed from the Figma design frames:

```text
Section: 457:1553
Desktop configurator: 457:1554
Mobile configurator: 457:1655
Model/blank selection: 457:1698
Validation/estimate: 457:1749
Branch recovery: 457:1795
```
