# Feature-012 Data Model — Workshop Ring Design Configuration

**Date created**: 2026-11-08
**Date updated**: 2026-11-26
**Author**: Ngô Tiến Đạt
**Last updated by**: Phan Sơn

**Status:** Historical implemented baseline plus proposed 2026-11-26 Admin difficulty model; lifecycle and policy changes pending approval/implementation.

## 1. Ownership boundary

Feature-012 owns configured-design lifecycle, option selections, evaluation
snapshots and archive/audit evidence. Workshop Booking owns booking, session,
capacity, payment and ticket records. Catalogue and Manager administration own
non-difficulty option facts. Feature-012 owns the Admin-published difficulty
policy and the evaluation snapshots that consume it.

Feature-012 must not create direct repository dependencies on Workshop Booking,
Workshop Operations or Product Management. Cross-feature validation uses public
facades and opaque references.

## 2. Logical entities

### 2.1 Configured design aggregate

Logical name: `configured_design`

| Field | Required | Meaning |
| --- | --- | --- |
| `design_reference` | Yes | Opaque external reference; never a raw database ID in a public DTO. |
| `owner_type` | Yes | `MEMBER`. |
| `owner_reference` | Yes | Member account reference. |
| `package_reference` | No | Opaque workshop package reference only when the Member explicitly starts from or selects a compatible package; null for general discovery. |
| `source_model_reference` | No | Published catalogue model used as the starting point. |
| `design_type` | Yes | `configured_design`; source catalogue models remain `catalogue_model`. |
| `current_version` | Yes | Latest successful Save/Evaluate version. |
| `lifecycle_status` | Yes | New writes use `DRAFT`, `SAVED`, `ARCHIVED`. `VALIDATED`, `ATTACHED_TO_BOOKING`, and `LOCKED` remain legacy-readable values only. |
| `created_at` / `updated_at` | Yes | Audit timestamps. |

The existing `ring_designs` proposal may represent this aggregate or each
immutable version. That physical choice is not approved here.

### 2.2 Design version

Logical name: `configured_design_version`

| Field | Required | Meaning |
| --- | --- | --- |
| `design_reference` | Yes | Parent configured-design reference. |
| `design_version` | Yes | Monotonic version within the design aggregate. |
| `package_reference` | No | Explicit package context used for compatibility validation when present; null does not imply a Silver package. |
| `material_reference` | Yes | Selected base material (`MAT-SILVER-925`, `MAT-COPPER`, `MAT-STAINLESS-STEEL`). |
| `shape_reference` | Yes | Selected base shape (`SHP-CLASSIC-ROUND`, `SHP-BEVELED`, `SHP-SINGLE-TWIST`, `SHP-WAVE`, `SHP-BALL-PEEN`, `SHP-FLAT-BAND`). |
| `ring_size` | Yes | Validated size value (`6` through `29`); millimeter perimeter = `size + 40mm`. |
| `component_snapshot` | Yes | Closed JSON snapshot containing component list, attachments, engraving, dimensions (`thicknessMm`, `bandWidthMm`), and polar `gemstonePositions` (angle, coordinates). |
| `evaluation_snapshot` | Yes after evaluate | Estimate, difficulty score and level, recommendation outcome, currency, rules version and validation result, all bound to the same effective difficulty policy. |
| `lifecycle_status` | Yes | Current version state; booking attach/lock values are historical-read only. |
| `created_at` | Yes | Immutable creation timestamp. |

Each successful Save/Evaluate creates a new immutable version. A version cannot
be rewritten after persistence.

### 2.3 Gemstone/component selection

Logical relation: `configured_design_gemstone`

- Parent design/version reference.
- Opaque gemstone/component reference (e.g., `CMP-CZ-WHITE-2MM`, `CMP-CZ-BLUE-2MM`, `CMP-MOISSANITE-1CT`).
- Positive quantity validated against the component's server-provided `maxQuantity`.
- Optional validated variant, semantic placement (`CENTER`, `ACCENT_LEFT`, `ACCENT_RIGHT`, `HALO`), and visual polar coordinates ($\theta \in [0, 360)$ degrees, radius $R = 85\text{px}$).
- Source catalogue version.

Aggregate invariant: the sum of all positive component quantities in one design
version is at most 10. Component-specific `maxQuantity` remains a separate
constraint, and the persisted snapshot must make the aggregate reproducible.

In physical storage, individual gemstone instances and their visual angles are preserved in the JSON `component_snapshot` (`gemstonePositions: [{ id, stoneIndex, reference, angleDeg }]`).

### 2.4 Attachment selection

Logical relation: `configured_design_attachment`

- Parent design/version reference.
- Opaque attachment reference.
- Positive quantity.
- Optional validated variant and placement.
- Source catalogue version.

### 2.4.1 Physical dimensions

- `thickness_mm`: Thickness of ring band, constrained between 1.0mm and 3.0mm (step 0.1mm, default 1.8mm).
- `band_width_mm`: Width of ring band surface, constrained between 1.5mm and 6.0mm (step 0.1mm, default 3.5mm).
- Stored directly in `component_snapshot.dimensions` and evaluated by the rules adapter.

### 2.5 Engraving & Custom Image Upload Model

Logical fields:

- `enabled`: Boolean toggle for standard text/symbol engraving.
- `items`: List of standard engraving items `[{ id, type, content, placement }]`.
  - Type: `TEXT` or `SYMBOL`.
  - Placement: `INNER_BAND` (Mặt trong) or `OUTER_BAND` (Mặt ngoài).
  - Pricing: Flat one-time standard engraving fee (80.000 ₫) regardless of item count or placement.
- `custom_images`: List of uploaded custom images `[{ id, placement, image_url, crop_x, crop_y, crop_width, crop_height, contrast, sharpness, processed_preview_reference, price }]`.
- `contrast` and `sharpness` are integers in `0..100`, defaulting to `50`.
- A safe source or processed-preview reference must be resolvable after reload;
  a browser-only data URL is not authoritative.
- Cardinality is independent: at most 2 `TEXT`, 2 `SYMBOL` and 2 custom image
  items per design version.
  - Placement: `INNER_BAND` or `OUTER_BAND`.
  - Frame constraint: Exactly 160px x 160px crop window with thresholded monochrome black & white laser simulation.
  - Pricing: 100.000 ₫ per uploaded image (`100,000 VND / image`).
- Text/placement backward compatibility: `text` and `placement` fields are maintained for legacy consumers.
- Surface Texture Warning Invariant (Invariant 22): When any engraving item or custom image is placed on `OUTER_BAND` of a textured/hammered shape (`SHP-HAMMERED`, `SHP-BALL-PEEN`, `SHP-SINGLE-TWIST`, `SHP-TWISTED`, `SHP-WAVE`), the evaluation snapshot must record an advisory technical warning.

### 2.6 Evaluation snapshot

Logical name: `design_evaluation_snapshot`

- `design_reference` and `design_version`.
- Server-derived `estimated_price` and `currency`.
- Server-derived `difficulty_score`.
- Volumetric mass `estimated_weight_grams`, `base_weight_limit_grams` (3.0g), `material_cost`, and `excess_material_cost`.
- `rules_version`.
- Validation result and safe reason codes.
- Calculation timestamp.
- Correlation ID.

The snapshot is advisory and is not a payment, invoice or final custom-order
amount.

### 2.7 Booking attachment snapshot — historical only

Logical name: `booking_design_attachment`

- Opaque booking reference supplied through the Booking facade.
- Design reference/version.
- Immutable package and configuration snapshot.
- Attach actor/scope and timestamp.
- Lock state and correlation ID.

This logical structure is retained for migration/audit interpretation only.
The current Feature-012 flow creates no booking attachment, booking snapshot,
booking lock or booking-side persistence. Current consumers use the saved-design
recommendation or an owner-safe Feature-013 consultation/reference projection.

### 2.8 Archive/audit evidence

Append-only evidence includes actor scope, action, design/version reference,
previous/new lifecycle state, outcome, reason where required, timestamp and
correlation ID. It must not contain raw session values, credentials, payment
data or unnecessary personal data.

## 3. Invariants

1. `design_type` distinguishes catalogue models from configured designs.
2. Every validated design uses active options; when a package is explicitly selected, those options and the source model must also be compatible with that package.
3. A configured version has one base material and one base shape in V1.
4. Gemstone/attachment quantities are positive and placement values are validated.
5. Estimate, difficulty and `rules_version` are stored as one evaluation snapshot.
6. Client-supplied estimate, difficulty or rules version is never authoritative.
7. Every successful Save/Evaluate creates one immutable version.
8. Only the latest editable version can receive a new Save/Evaluate command.
9. Legacy booking-attached versions are read-only compatibility data; current
   flow does not create new booking attachments.
10. A failed recommendation or consultation handoff cannot delete or invalidate
    the design. Former booking-submission behavior is historical only.
11. Archive is soft-delete; design history and audit are not hard-deleted.
12. Guest local preview state is never persisted or treated as a design lifecycle state.
13. Member reads and writes are restricted to the authenticated Member's own designs.
14. Feature-004 may snapshot any owner-visible state after server ownership
    validation; Feature-003 does not attach a design through Feature-012.
15. Feature-003 never queries Feature-012 repositories directly.
16. Configured designs have no direct FK to Feature-004 image requests.
17. Ring size must be a recognized standard size in `["6", ..., "29"]` with millimeter perimeter $P = \text{size} + 40\text{mm}$.
18. Workshop materials are restricted to Silver 925 (`MAT-SILVER-925`), Copper (`MAT-COPPER`), and Stainless Steel 316L (`MAT-STAINLESS-STEEL`); Gold variants are rejected as inactive options.
19. Physical dimensions must stay within limits: $1.0\text{mm} \le \text{thickness} \le 3.0\text{mm}$ and $1.5\text{mm} \le \text{band width} \le 6.0\text{mm}$.
20. Component quantities must satisfy $1 \le \text{quantity} \le \text{maxQuantity}$ per type. On the 2D Top View preview, individual gemstone coordinates are mathematically locked to circle radius $R = 85\text{px}$ from center $(150, 135)$ and cannot be dragged outside or away from the ring band.
21. Aggregate gemstone quantity across all component types must be at most 10.
22. Engraving cardinality is independently limited to 2 `TEXT`, 2 `SYMBOL` and 2 custom image items.
23. Custom-image `contrast` and `sharpness` are persisted integers in `0..100`, default 50.
24. Saved previews must be reconstructible from persisted safe image/reference data.
25. Feature-012 messages and preview labels use one active locale and valid UTF-8 without mojibake.
26. Material cost calculates mass $M = \frac{(P + \pi T) \cdot T \cdot W}{1000} \cdot D$. Signature Silver 925 includes 3g; mass above 3g produces an advisory excess-material estimate (120.000 ₫/g for Silver), not an automatic payable workshop charge.

## 4. Keys, uniqueness and concurrency

Proposed logical constraints:

- Unique public `design_reference`.
- Unique `(design_reference, design_version)`.
- At most one current editable version per design aggregate.
- Unique idempotency key within actor/session scope and command type.
- No duplicate component selection where the approved variant/placement rule
  says the selection is unique.
- Optimistic version check applies to Save/Evaluate and archive commands.
- Recommendation reads are non-mutating and require no booking command scope.

The exact physical indexes, FK actions, version column and idempotency table must
be reconciled with repository Flyway history before migration.

### 2.10 Custom-order reference snapshot

Feature-004 receives a server-built immutable snapshot through the public facade.
The snapshot records the selected design version, optional explicit package context, material,
shape, size, components, attachments, engraving, dimensions and evaluation
metadata where available. It does not expose the Guest session value or private
Member data.

The current Member flow may project an owner-visible `SAVED` version after
ownership and status validation. Legacy booking-attached/locked rows require an
approved read-only compatibility projection. Changes to the source design never
mutate an existing custom-order snapshot.

## 5. Retention and deletion

- Guest local preview data is discarded when the preview is abandoned.
- Member design history is retained for three years.
- Archived design content is not hard-deleted by normal feature operations.
- Former booking snapshots remain under the Booking/business-record retention
  policy; the current Feature-012 flow creates no new booking snapshot.
- No raw image or AI provider payload is owned by this feature.

## 6. Resolved logical model and remaining gates

The V1 logical model uses one immutable `ring_designs` row per design version.
`source_design_id` preserves lineage between model-derived versions. A separate
version table is not required. `design_version` is a logical sequence whose
physical representation still requires schema approval.

No Guest session table is owned by Feature-012. Guest preview state is local and
non-authoritative; only authenticated Member design data is persisted.

The former booking boundary `attachAndLockDesignForBooking(...)` is retired by
the 2026-11-22 change request. Feature-012 no longer attaches or locks a design
to a workshop ticket. Feature-004 may request an immutable owner-verified
snapshot from any readable design state; the custom-order module owns the
snapshot boundary and all later quote/order behavior.

Evaluation reads non-difficulty option facts from the catalogue boundary and
one immutable Admin-published difficulty policy owned by Feature-012. It stores
safe input/output, difficulty level, recommendation outcome, currency, timestamp
and `rules_version`; a historical read uses that snapshot rather than current
policy values. P2.9 snapshot envelope and decimal-string money representation
remain mandatory.

## 7. Proposed difficulty policy model — 2026-11-26

Feature-012 owns an immutable `ring_difficulty_policy_version` aggregate and
versioned `ring_difficulty_option_score` entries keyed by option kind and opaque
option reference. A version includes the standard-engraving and per-image
coefficients, ordered level cutoffs, Wax Casting cutoff, gemstone-count cutoff,
status, publish actor/time, and an immutable audit record of old/new values.
Exactly one version is effective, complete for the active options at its
publication time. Options activated later remain pending and unselectable until
the next complete publication. The physical table, key, uniqueness,
FK and migration contract are proposed here; they require Tech Lead approval.
An active published model is independently checked using its full default
material, shape, component and attachment snapshot. It must use active valid
references, have every contribution under the effective policy and score no
higher than that policy's too-risky cutoff. A model cannot be published, nor an
option it requires retired, when this invariant would fail. An explicit model
retirement is a separate catalogue operation; policy publication never hides
models as a side effect.

Publication validates every active option has exactly one non-negative score
with at most four decimal places, all required coefficients are present and
non-negative, level cutoffs strictly increase, Wax cutoff is below the too-risky
cutoff, and gemstone cutoff is an integer below the aggregate limit of 10.
Validation uses the whole candidate; no partial option update is visible.
An active option added by Manager without a score in the effective policy is
still returned to customer option reads, but with `selectable: false` and
`unavailableReason: DIFFICULTY_SCORE_PENDING`. It cannot be selected for a new
draft or evaluation, and it cannot be referenced by an active published model.
The Admin candidate must supply its score before the next
complete policy can be published. Manager catalogue DTOs exclude difficulty fields.

A preview with at least one evaluable standard example and no blocking
published model creates an
expiring proof record containing only an opaque reference, Admin account
reference, canonical candidate digest (option entries sorted by kind/reference
and decimal strings normalized to four places), expected active policy version,
catalogue revision,
creation time and expiry (15 minutes). Neither full candidate,
sample input/output nor Member data is retained in that record. Required
standard examples and optional Admin examples are evaluated transiently. A
publish checks the proof against the authenticated Admin, exact candidate,
still-effective version and unchanged catalogue revision. Inside the same
publication transaction it recomputes every active published model using its
complete defaults, rejecting the candidate with affected model codes if any
model becomes invalid, unscored or `TOO_RISKY`; a completed idempotent replay is
looked up first so it succeeds after proof expiry. Expired proof is deleted.

The publication audit is append-only for at least five years under Report 3
DBR-36. A superseded immutable policy version and its scores remain while any
retained design version references them; deleting an unreferenced version after
the applicable audit period requires a separate approved retention operation.

The migration design seeds the first version from existing catalogue
`difficulty_score` values, engraving coefficients `0.5000` and `0.3000`, level
cutoffs `3/6/9/11`, Wax cutoff `9` and gemstone cutoff `5`. Existing catalogue
score columns remain legacy compatibility data and cease to be evaluation
authority after cutover. No applied Flyway migration is edited. Existing
`ring_designs.difficulty_score` and `rules_version` are retained; new evaluation
snapshots additionally retain level and recommendation outcome. Records without
those fields do not acquire a fabricated historical outcome.

The existing `ring_designs.difficulty_score` column is `DECIMAL(10,4)`, while
the proposed per-option input range can produce a larger valid sum. The
append-only migration must widen the stored result to `DECIMAL(18,4)` and
reject a candidate whose maximum legally selectable configuration would exceed
that range. This is a storage-safety bound, not a customer-facing `/10` scale.
The physical design must permit nullable package context for new general
catalogue designs and general-catalogue models while preserving legacy package
references. Package-filtered customer reads return only compatible models;
unfiltered reads return all active eligible models. Existing seeded Copper/Steel
models currently point at the Silver package and require an append-only data
correction so no incompatible package relationship remains. Source-model
defaults must be projected and evaluated in full rather than reduced to
material and shape.

One publish transaction checks the expected active version, persists the new
complete version and audit evidence, then changes the effective pointer. A
duplicate idempotent command returns its original result; stale concurrent
publish returns conflict and leaves the previous version effective. Recovery or
rollback publishes a new version copied from a prior one; it never rewrites
an immutable policy or saved design version.

The old `V102`, Guest-session and booking-facade gates above are historical;
repository migration files were observed through `V203` on 2026-11-26.
Remaining current gates are the applied-database baseline check, append-only
policy migration, Member-only customer-contract rebaseline, Admin API/visual
approval and formal Tech Lead approval.

## 8. Proposed physical mapping and append-only upgrade

This is a schema design for Tech Lead review, not an executable migration. The
implementation team must inspect the applied `flyway_schema_history` and
current schema before choosing the next migration number. Never edit V110,
V142, V194 or another applied migration.

| Physical object | Proposed columns and constraints |
| --- | --- |
| `ring_difficulty_policy_versions` | Unsigned bigint PK, unique opaque `rules_version`, non-negative `DECIMAL(18,4)` engraving and image coefficients, ordered `DECIMAL(18,4)` cutoffs, gemstone count cutoff, Admin actor reference and `DATETIME(6)` publication time. Rows are immutable. |
| `ring_difficulty_option_scores` | Unsigned bigint PK, FK to policy version with `ON DELETE RESTRICT`, kind/reference, non-negative `DECIMAL(18,4)` score, unique `(policy_version_id, kind, option_reference)`. References are validated against the catalogue at publication; no cross-table polymorphic FK is invented. |
| `ring_difficulty_effective_policy` | One row keyed by the ring-policy scope with FK to the currently effective policy. A locked update in the publication transaction atomically switches the pointer; old versions remain immutable. |
| `ring_difficulty_catalogue_revision` | One monotonic revision row for the active model/default and referenced-option set. Every model publication, default edit, active-state change and relevant option activation/retirement locks and advances it in the same transaction after preserving the model invariant. Preview proof binds this revision; publish compares it under lock. |
| `ring_difficulty_preview_proofs` | Unique hash of an opaque proof reference, Admin account reference, canonical candidate digest, expected effective policy FK, expected catalogue revision, `created_at` and `expires_at`. No candidate, examples, model payload or Member data is stored. Expired rows are purged; completed idempotent replay is checked before proof validation. |
| `ring_difficulty_publication_audit` | Append-only actor, timestamp, previous/new policy FK, reason/correlation and old/new values or stable policy references. Retain at least five years; access is Admin/audit-scoped. |
| Existing `ring_designs` | Widen `difficulty_score` to `DECIMAL(18,4)`; permit null `package_reference` for general designs; add nullable FK to policy version and versioned evaluation snapshot fields for level, recommendation code/package/material disclosure and evaluation time. Keep old `rules_version`, score, legacy status/booking fields and rows readable; new writes use only Member and current lifecycle values. |
| Existing `ring_catalogue_models` | Permit null `package_reference`. An append-only data correction sets the four active Copper/Steel model package references to null; Silver models retain the verified compatible association. Existing model codes and default JSON remain unchanged. |

Publication obtains the effective-policy and catalogue-revision locks in a
documented stable order, validates the complete candidate and active-model
defaults, inserts version/scores/audit, then switches the pointer in one
transaction. Catalogue writes use the catalogue-revision lock and the current
policy to reject a model publication/default edit/option retirement that would
leave an active model invalid, unscored or too risky. A concurrent change
invalidates an older preview and returns conflict without partial publication.
The customer options read uses one policy and catalogue snapshot; a general
read includes all active published models, and a package read filters by actual
package/material compatibility.

Seed policy values from active catalogue scores and existing coefficients and
cutoffs. Before the effective pointer is exposed, validate every active model
using its full defaults. The 12 rows seeded in V142 pass a static reference and
formula check (12/12, maximum 4.7000), but the migration must fail safely if
the applied database has different active models or scores. Preserve older
design results without mass recalculation. A failed schema or seed step leaves
the old runtime baseline until a separately approved deployment/cutover;
physical cleanup of unreferenced policy and audit rows remains a separate
retention operation.
