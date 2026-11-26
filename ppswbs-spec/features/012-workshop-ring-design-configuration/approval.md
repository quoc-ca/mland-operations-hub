# Feature-012 Approval Record

**Date created**: 2026-11-08
**Date updated**: 2026-11-26
**Author**: Ngô Tiến Đạt
**Last updated by**: Phan Sơn

## Status

**Current approval status:** `APPROVED — 2026-11-26 FULL-SCOPE REBASELINE APPROVED`

> Tech Lead Ngô Tiến Đạt approved the 2026-11-26 full-scope rebaseline via
> `review-for-approval.md` (covering Member-only flow, retired Guest/booking
> attachment, updated `V204` base pricing & `120,000 VND/g` excess formula,
> Admin-owned difficulty policy, FE-09 editor, `data-model.md` §7–8,
> customer/Admin OpenAPI contracts, and C7 Technical Projection).

## Approved 2026-11-26 full-scope rebaseline

| Field | Approved value |
| --- | --- |
| Decision | APPROVED |
| Approved scope | `spec.md`, `plan.md`, `formula.md`, `data-model.md`, `contracts/{workshop-ring-design,admin-ring-difficulty}.openapi.yaml`, `design/{architecture,workflows,ui-flow}.md`, `frontend-handoff.md`, `tasks.md`, and `review-for-approval.md` |
| Date approved | 2026-11-26 |
| Tech Lead | Ngô Tiến Đạt |

The Member-only general/package-filtered design flow, retired Guest/booking
commands, saved-design recommendation/consultation boundary, new Admin-only
difficulty policy, FE-09 editor, immutable version schema, customer/Admin APIs,
server-side level/recommendation fields and historical read behavior form one
proposed revision. The earlier 2026-11-09 full-stack approval and 2026-11-22
visual approval below do not cover this revision. No checkbox below is
reinterpreted as approval. Before code handoff, Ngô Tiến Đạt must approve the
exact updated spec, plan, design, data-model, customer/Admin contracts,
migration design and written FE-09 Admin visual baseline; the decision record
must identify scope, date and exact content revision. Phan Sơn is the
responsible updater, not the approver.

The 2026-11-26 user clarifications narrow FE-09 delivery to ring difficulty,
keep new unscored options visible but disabled, require standard and optional
non-personal preview examples, require server-verified preview proof before
publication, and retain audit for at least five years with referenced policy
versions kept longer. These clarify the proposed revision; they do not approve
the Admin API, schema, visual design or implementation.

Further 2026-11-26 decisions in the same proposed revision: Signature Silver
925 includes 3g; excess mass remains an advisory design estimate. Every active
published model must keep a full-default score and remain at or below the
too-risky cutoff across policy/catalogue publication. General discovery keeps
Copper/Steel models without attaching them to a Silver workshop; package pages
filter compatible models. Copper/Steel have no incompatible package advice;
S925-to-S999 Silver Clay advice discloses the material difference. The
publication preview must show exhaustive model impact, and publish must reject
an unsafe candidate atomically. The static seed check found 12/12 models
scoreable with maximum 4.7000 under current values; this is not runtime proof.

The review-ready proposed revision is summarized in
`review-for-approval.md` with candidate fingerprint
`0fcb4666b9631b5226f42d2eef79f5a2f82f6bcad13775eb6c236865e9cdb246`.
This fingerprint identifies the review candidate, not an approval.
Feature-013's current Member-only consultation spec
postdates its approval and must be revalidated for this handoff. FE-09 has a
separate pending `approval.md`. None of these statements records approval.

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
4. [x] Approve the superseding removal of Workshop Booking attachment. The former `attachAndLockDesignForBooking(...)` and REST attachment endpoint are retired and must not be implemented in the current baseline.
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
