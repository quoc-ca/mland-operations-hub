# Feature-012 Data Model — Workshop Ring Design Configuration

Date created: 2026-11-08
Date updated: 2026-11-14
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

**Status:** IMPLEMENTED AND VERIFIED; Flyway migrations `V110` and `V120` applied and tested.

## 1. Ownership boundary

Feature-012 owns configured-design lifecycle, option selections, evaluation
snapshots, Guest design-session binding and archive/audit evidence. Workshop
Booking owns booking, session, capacity, payment and ticket records. Catalogue
and Manager administration own the source option/rule facts consumed by this
feature.

Feature-012 must not create direct repository dependencies on Workshop Booking,
Workshop Operations or Product Management. Cross-feature validation uses public
facades and opaque references.

## 2. Logical entities

### 2.1 Configured design aggregate

Logical name: `configured_design`

| Field | Required | Meaning |
| --- | --- | --- |
| `design_reference` | Yes | Opaque external reference; never a raw database ID in a public DTO. |
| `owner_type` | Yes | `GUEST_SESSION` or `MEMBER`. |
| `owner_reference` | Yes | Member account reference or feature-owned Guest-session binding. |
| `package_reference` | Yes | Opaque selected workshop package reference. |
| `source_model_reference` | No | Published catalogue model used as the starting point. |
| `design_type` | Yes | `configured_design`; source catalogue models remain `catalogue_model`. |
| `current_version` | Yes | Latest successful Save/Evaluate version. |
| `lifecycle_status` | Yes | `DRAFT`, `VALIDATED`, `ATTACHED_TO_BOOKING`, `LOCKED`, `ARCHIVED`. |
| `created_at` / `updated_at` | Yes | Audit timestamps. |

The existing `ring_designs` proposal may represent this aggregate or each
immutable version. That physical choice is not approved here.

### 2.2 Design version

Logical name: `configured_design_version`

| Field | Required | Meaning |
| --- | --- | --- |
| `design_reference` | Yes | Parent configured-design reference. |
| `design_version` | Yes | Monotonic version within the design aggregate. |
| `package_reference` | Yes | Package context used for validation. |
| `material_reference` | Yes | Selected base material (`MAT-SILVER-925`, `MAT-COPPER`, `MAT-STAINLESS-STEEL`). |
| `shape_reference` | Yes | Selected base shape (`SHP-CLASSIC-ROUND`, `SHP-BEVELED`, `SHP-SINGLE-TWIST`, `SHP-WAVE`, `SHP-BALL-PEEN`, `SHP-FLAT-BAND`). |
| `ring_size` | Yes | Validated size value (`6` through `29`); millimeter perimeter = `size + 40mm`. |
| `component_snapshot` | Yes | Closed JSON snapshot containing component list, attachments, engraving, dimensions (`thicknessMm`, `bandWidthMm`), and polar `gemstonePositions` (angle, coordinates). |
| `evaluation_snapshot` | Yes after evaluate | Estimate, difficulty, currency, rules version and validation result. |
| `lifecycle_status` | Yes | Version state and attach/lock state. |
| `created_at` | Yes | Immutable creation timestamp. |

Each successful Save/Evaluate creates a new immutable version. A version cannot
be rewritten after persistence.

### 2.3 Gemstone/component selection

Logical relation: `configured_design_gemstone`

- Parent design/version reference.
- Opaque gemstone/component reference (e.g., `CMP-CZ-WHITE-2MM`, `CMP-CZ-BLUE-2MM`, `CMP-MOISSANITE-1CT`).
- Positive quantity (clamped to `maxQuantity`: 10 for CZ, 4 for Moissanite).
- Optional validated variant, semantic placement (`CENTER`, `ACCENT_LEFT`, `ACCENT_RIGHT`, `HALO`), and visual polar coordinates ($\theta \in [0, 360)$ degrees, radius $R = 85\text{px}$).
- Source catalogue version.

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
- `custom_images`: List of uploaded custom images `[{ id, placement, image_url, crop_x, crop_y, crop_width, crop_height, price }]`.
  - Placement: `INNER_BAND` or `OUTER_BAND`.
  - Frame constraint: Exactly 160px x 160px crop window with thresholded monochrome black & white laser simulation.
  - Pricing: 100.000 ₫ per uploaded image (`100,000 VND / image`).
- Text/placement backward compatibility: `text` and `placement` fields are maintained for legacy consumers.
- Surface Texture Warning Invariant (Invariant 22): When any engraving item or custom image is placed on `OUTER_BAND` of a textured/hammered shape (`SHP-HAMMERED`, `SHP-BALL-PEEN`, `SHP-SINGLE-TWIST`, `SHP-TWISTED`, `SHP-WAVE`), the evaluation snapshot must record an advisory technical warning.

### 2.6 Guest design session

Logical name: `guest_design_session`

| Field | Meaning |
| --- | --- |
| `session_reference` | Opaque client-visible reference. |
| `session_digest` | Protected server-side lookup value; raw session value is not logged. |
| `design_reference` | Design set bound to the session. |
| `issued_at` | Session creation time. |
| `last_activity_at` | Used for 20-minute inactivity expiry. |
| `expires_at` | Server-calculated expiry boundary. |
| `status` | `ACTIVE`, `EXPIRED`, `REVOKED`. |
| `correlation_id` | Safe operational trace reference. |

The storage mechanism must use an approved shared session boundary or an
explicitly approved feature-owned store. The data model does not authorize a
new global authentication/session system.

### 2.7 Evaluation snapshot

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

### 2.8 Booking attachment snapshot

Logical name: `booking_design_attachment`

- Opaque booking reference supplied through the Booking facade.
- Design reference/version.
- Immutable package and configuration snapshot.
- Attach actor/scope and timestamp.
- Lock state and correlation ID.

Feature-012 returns the snapshot; Feature-003 owns the booking-side persistence
and transaction boundary.

### 2.9 Archive/audit evidence

Append-only evidence includes actor scope, action, design/version reference,
previous/new lifecycle state, outcome, reason where required, timestamp and
correlation ID. It must not contain raw session values, credentials, payment
data or unnecessary personal data.

## 3. Invariants

1. `design_type` distinguishes catalogue models from configured designs.
2. Every validated design uses active options compatible with its package.
3. A configured version has one base material and one base shape in V1.
4. Gemstone/attachment quantities are positive and placement values are validated.
5. Estimate, difficulty and `rules_version` are stored as one evaluation snapshot.
6. Client-supplied estimate, difficulty or rules version is never authoritative.
7. Every successful Save/Evaluate creates one immutable version.
8. Only the latest editable version can receive a new Save/Evaluate command.
9. A version attached to a booking cannot be edited or archived.
10. A failed booking submission cannot delete or invalidate the design.
11. Archive is soft-delete; design history and audit are not hard-deleted.
12. Guest sessions expire after 20 minutes of inactivity and cannot be reused after expiry.
13. Member reads and writes are restricted to the authenticated Member's own designs.
14. Booking validates and attaches only a current `VALIDATED` version.
15. Feature-003 never queries Feature-012 repositories directly.
16. Configured designs have no direct FK to Feature-004 image requests.
17. Ring size must be a recognized standard size in `["6", ..., "29"]` with millimeter perimeter $P = \text{size} + 40\text{mm}$.
18. Workshop materials are restricted to Silver 925 (`MAT-SILVER-925`), Copper (`MAT-COPPER`), and Stainless Steel 316L (`MAT-STAINLESS-STEEL`); Gold variants are rejected as inactive options.
19. Physical dimensions must stay within limits: $1.0\text{mm} \le \text{thickness} \le 3.0\text{mm}$ and $1.5\text{mm} \le \text{band width} \le 6.0\text{mm}$.
20. Component quantities must satisfy $1 \le \text{quantity} \le \text{maxQuantity}$ per type. On the 2D Top View preview, individual gemstone coordinates are mathematically locked to circle radius $R = 85\text{px}$ from center $(150, 135)$ and cannot be dragged outside or away from the ring band.
21. Material cost calculates mass $M = \frac{(P + \pi T) \cdot T \cdot W}{1000} \cdot D$. Rings with $M \le 3.0\text{g}$ use base unit price; every 1 gram exceeding 3.0g incurs an excess surcharge (120.000 ₫/g for Silver).

## 4. Keys, uniqueness and concurrency

Proposed logical constraints:

- Unique public `design_reference`.
- Unique `(design_reference, design_version)`.
- At most one current editable version per design aggregate.
- Unique idempotency key within actor/session scope and command type.
- No duplicate component selection where the approved variant/placement rule
  says the selection is unique.
- Optimistic version check on Save/Evaluate, archive and attach commands.
- Booking attachment is idempotent by booking/design/version command scope.

The exact physical indexes, FK actions, version column and idempotency table must
be reconciled with repository Flyway history before migration.

## 5. Retention and deletion

- Guest session and unsubmitted draft data follows the 20-minute inactivity
  expiry plus approved cleanup process.
- Member design history is retained for three years.
- Archived design content is not hard-deleted by normal feature operations.
- Booking snapshots remain under the Booking/business-record retention policy.
- No raw image or AI provider payload is owned by this feature.

## 6. Resolved logical model and remaining gates

The V1 logical model uses one immutable `ring_designs` row per design version.
`source_design_id` preserves lineage between model-derived versions. A separate
version table is not required. `design_version` is a logical sequence whose
physical representation still requires schema approval.

`guest_design_sessions` is owned by Feature-012 and stores only an opaque
reference/digest, bound design reference, `last_activity_at`, `expires_at` and
`ACTIVE/EXPIRED/REVOKED` state. It must not store raw identity in the client;
cleanup and replay protection are implementation gates.

The booking boundary is `attachAndLockDesignForBooking(...)`. Feature-012
returns an immutable snapshot; Feature-003 owns booking-side persistence. The
operation is synchronous in the booking transaction and does not create payment,
capacity or ticket side effects.

Evaluation reads catalogue data through the Manager-owned rule facade. It stores
safe input/output, currency, timestamp and `rules_version`; Feature-012 does not
own a fixed numerical formula. P2.9 snapshot envelope and decimal-string money
representation remain mandatory.

Remaining gates are physical schema/migration approval after checking versions
through `V102`, session-store implementation, rule-facade implementation,
booking-facade implementation and formal approval.
