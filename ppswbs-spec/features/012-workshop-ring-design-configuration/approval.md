# Feature-012 Approval Record

Date created: 2026-11-08
Date updated: 2026-11-22
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

## Status

**Current approval status:** `VISUAL BASELINE APPROVED — SUPERSEDING BUSINESS/IMPLEMENTATION GATES REMAIN`

> The approval below is historical for the former booking-attachment baseline.
> The 2026-11-22 change request is not approved for implementation until the
> saved-design recommendation, consultation handoff and retired booking flow
> have been reviewed again.

## Visual baseline approval — 2026-11-22

**Status:** `APPROVED FOR FRONTEND VISUAL BASELINE ONLY`

The user approved the latest in-place Figma updates for the saved-design
recommendation, consultation handoff and existing configurator screens. This
approval covers visual direction and frame reuse only. It does not approve a
change to Feature-012 business behavior, API contracts, persistence, or
implementation readiness.

Approved existing-frame references:

- Member configurator/saved-design section: `457:1553`.
- Saved-design recommendation frame: `457:1749`.
- Recommendation panel: `579:1408`.
- Consultation action: `579:1415`.
- UI redesign configurator desktop/mobile: `498:482`, `498:581`.
- Existing homepage custom-request entry points: `498:14`, `498:130`, `498:131`.

The existing-style-first rule in `ppswbs-spec/DESIGN.md` is mandatory for
subsequent frontend implementation.

`APPROVED FOR FULL-STACK IMPLEMENTATION — BACKEND & FRONTEND AUTHORIZED`

Formal approval granted by Tech Lead on 2026-11-09 for Feature-012 full-stack implementation:
Backend persistence, Modulith architecture, REST API, Migration `V110__workshop_ring_design_configuration.sql`,
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
| Approved scope | Backend API, Flyway V110, OpenAPI DTOs, Frontend React 19 + Vite configurator, Vitest suite |
| Date approved | 2026-11-09 |
| Tech Lead | Ngô Tiến Đạt |
| Physical migration approved | Yes (V110__workshop_ring_design_configuration.sql) |
| Production code approved | Yes (Spring Modulith module & React 19 Frontend) |

## Conditions before implementation

1. [x] Approve physical mapping of configured-design versions and P2.9 snapshot persistence. (Row-per-version `ring_designs`, `source_design_id` lineage, JSON snapshot).
2. [x] Retire the Feature-012-owned Guest session table and cleanup/replay rules. Guest preview is local-only and no Guest design state is persisted.
3. [x] Approve the Manager-owned option/rule facade and its versioned evaluation input/output contract. (`DesignOptionRulesPort`, advisory estimate, difficulty score, `DesignOptionsResponse`).
4. [ ] Approve the superseding removal of Workshop Booking attachment. The former `attachAndLockDesignForBooking(...)` and REST attachment endpoint are retired and must not be implemented in the current baseline.
5. [x] Verify Flyway version/history; repository baseline verified at `V109__catalogue_variant_branch_stock.sql`. Next append-only migration is confirmed as `V110__workshop_ring_design_configuration.sql`.
6. [x] Approve Frontend visual baseline and Figma node IDs (`457:1553`, `457:1554`, `457:1655`, `457:1698`, `457:1749`, `457:1795`), local-only Guest preview state, and 2D SVG Live Preview.

## Figma evidence — historical baseline pending superseding review

The Feature-012 visual baseline is confirmed from the Figma design frames:

```text
Section: 457:1553
Desktop configurator: 457:1554
Mobile configurator: 457:1655
Model/blank selection: 457:1698
Validation/estimate: 457:1749
Branch recovery: 457:1795
```
