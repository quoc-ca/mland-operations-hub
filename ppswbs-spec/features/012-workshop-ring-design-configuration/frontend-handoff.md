# Feature-012 Frontend Handoff

**Date created**: 2026-11-09
**Date updated**: 2026-11-26
**Author**: Ngô Tiến Đạt
**Last updated by**: Phan Sơn

**Status:** Historical frontend baseline completed; the 2026-11-26 Admin difficulty-policy and disabled-option display below are proposed, unimplemented changes.

The new customer options contract returns `selectable` and
`unavailableReason: DIFFICULTY_SCORE_PENDING` for an active option without a
score in the effective policy. Show that option, explain its disabled state,
and prevent selection or Save/Evaluate with it. No active published model may
reference such an option; catalogue/policy publication guards that invariant.
Future difficulty
labels and workshop recommendations must use the server evaluation snapshot;
the older local thresholds and `/10` display in this historical handoff are
not the proposed policy baseline.

## 1. Handoff objective

Build the customer-facing React/Vite flow for workshop ring configuration from
the revised Feature-012 contract after Tech Lead approval and the customer
Figma visual baseline. The experience
must feel spacious and easy to scan: controls are grouped by purpose, every
adjustment explains its effect, and the preview changes immediately as the user
edits the local draft.

The frontend exposes the configurator entry point and local preview to Guest and
Member, but only an authenticated Member may create, evaluate, save, archive or
consult with a saved design. Guest parameter changes are local preview state and
are discarded when the preview is abandoned. Member ownership, option
compatibility, evaluation, versioning and recommendation are backend decisions.
Booking attachment is retired from the current Feature-012 flow.

## 2. Figma baseline

| Node ID | Frame | Required implementation meaning |
| --- | --- | --- |
| `457:1553` | `F12 · Workshop Ring Design Configuration` | Feature section in the existing Member page. |
| `457:1554` | Desktop configurator, 1440×1080 | Controls panel on the left; preview and summary panel on the right. |
| `457:1655` | Mobile configurator, 390×844 | Preview first, then vertically spaced controls and primary action. |
| `457:1698` | Model/blank selection | Published model, alternate model and blank-design cards. |
| `457:1749` | Saved design and recommendation | Saved preview, advisory workshop recommendation and consultation action. |
| `457:1795` | Historical branch recovery | Retained for audit only; not part of the current flow. |

The existing in-place Figma frames are approved as the visual baseline on
2026-11-22. This approval covers visual direction and frame reuse only; it does
not approve new business behavior or API changes.

The Figma component references are:

- Material 3 Button component set:
  `ab924dce8b851fd820bd0d56da24a9c489311ae6`.
- Material 3 Standard slider component set:
  `4851d51e7cd3b6f9988e35f1ea59cbd4d382627b`.

Production code must use shared repository UI primitives and CSS, not import
Figma assets or add a UI kit dependency solely because Figma uses Material 3.

## 3. Routes and screen responsibilities

Use the existing browser-router convention. Proposed routes are:

```text
/design/rings
/design/rings/models
/design/rings/blank
/design/rings/configure
/design/rings/:designId
/workshops/:packageReference/design
/workshops/:packageReference/design/models
/workshops/:packageReference/design/blank
/workshops/:packageReference/design/configure
/workshops/:packageReference/design/:designId  # legacy detail redirect to /design/rings/:designId
/my/designs
```

Route behavior:

| Route/screen | Responsibility |
| --- | --- |
| General entry | Show all active eligible published models or blank design without choosing a workshop package. |
| Package entry | Explain the selected workshop and start compatible model/blank selection. |
| Models | Render all eligible published models in general discovery or only compatible models in package context. Model score includes full defaults. |
| Blank | Initialize an empty draft from server-returned defaults. |
| Configure | Render dynamic controls and the live `RingPreview`. |
| Design detail/evaluation | Show the latest safe server version, validation, evaluation and next action. |
| Member history | Show only the authenticated Member's designs and versions; support archive for eligible designs. |
| Guest sign-in required | Safe recovery state when Guest attempts a protected command; do not create or reveal persisted design data. |
| Package recovery | Explain explicit package incompatibility and preserve the current design for correction/retry. |

The booking flow remains owned by Feature-003. Feature-012 does not supply a
current booking-attachment adapter; it supplies saved-design recommendation and
owner-safe consultation/reference projections.

## 4. Frontend module structure

Create the feature in the existing frontend source tree:

```text
ppswbs_frontend/src/features/ringdesignconfiguration/
├── routes.jsx
├── api/workshopRingDesignApi.js
├── application/
│   ├── useWorkshopDesignOptions.js
│   ├── useWorkshopDesignDraft.js
│   └── useWorkshopDesignCommand.js
├── domain/
│   ├── designDraft.js
│   ├── designPreview.js
│   └── designErrors.js
├── ui/
│   ├── RingDesignEntryPage.jsx
│   ├── RingModelSelectionPage.jsx
│   ├── RingConfigurationPage.jsx
│   ├── RingPreview.jsx
│   ├── DesignControlField.jsx
│   ├── DesignEvaluationPage.jsx
│   ├── MemberDesignHistoryPage.jsx
│   └── BranchCompatibilityPage.jsx
├── messages.js
├── ring-design.css
└── tests/
```

Reuse:

```text
src/shared/api/http.js
src/shared/ui/components.js
src/shared/auth/AuthProvider.jsx
src/shared/auth/RequireSession.jsx
src/shared/i18n/
```

Do not add a state library, form library, UI kit, direct `fetch` calls or a
second auth/session implementation.

## 5. Data and state model

Keep three distinct client states:

1. `serverDesign`: the latest authoritative DTO returned by the backend.
2. `draft`: normalized local selections currently shown in the controls.
3. `previewModel`: a derived visual-only model used by `RingPreview`.

Example visual-only shape:

```text
PreviewModel {
  modelReference?
  shapeReference?
  materialReference?
  ringSize?
  perimeterMm?
  innerDiameterMm?
  thicknessMm: number (1.0 - 3.0)
  bandWidthMm: number (1.5 - 6.0)
  estimatedWeightGrams: number
  excessWeightGrams: number
  excessMaterialCost: number
  gemstones: [] // 1-to-1 component summary
  stones: [{ id, stoneIndex, reference, color, angleDeg, cx, cy }] // individual stone instances
  attachmentReferences[]
  engravingPreview?
}
```

`previewModel` must be derived locally and must never be sent as a substitute
for the API request. It must not contain or display internal database IDs.
Unknown optional values render a neutral/incomplete preview. The preview must
not calculate price, difficulty or compatibility.

Control descriptors must include enough safe data for the UI to render:

```text
ControlDescriptor {
  key
  label
  explanation
  type: CARD | PILLS | SLIDER | SELECT | TEXT
  options[]
  value
  min?
  max?
  step?
  unit?
  required
  disabledReason?
}
```

This is a frontend mapping type, not a new backend contract. The adapter maps
the actual OpenAPI response into it.

## 6. API and command rules

Use the contract in `contracts/workshop-ring-design.openapi.yaml` and the
adapter boundary in `design/architecture.md`.

```text
GET  /api/v1/workshop-design-options
POST /api/v1/workshop-designs
GET  /api/v1/workshop-designs/{designId}
POST /api/v1/workshop-designs/{designId}/versions
POST /api/v1/workshop-designs/{designId}/evaluate
POST /api/v1/workshop-designs/{designId}/archive
GET  /api/v1/workshop-designs/{designId}/recommendation
```

Rules:

- Options are loaded by package context and rendered only after successful
  response.
- The saved-design screen renders the advisory workshop recommendation and
  keeps the general workshop discovery action visible.
- The consultation action hands off an owner-verified design snapshot to
  Feature-013; it does not create a booking or payment mutation.
- Guest requests carry the opaque design-session reference through the approved
  transport; Member requests use the shared Firebase-authenticated client.
- Every write action gets one `Idempotency-Key`; a retry reuses the same key.
- Versioned commands send `If-Match-Version` from the latest server response.
- `401` session/identity failures, `403` access failures, `409` stale or
  idempotency conflicts, `422` compatibility failures and `503` rule-service
  failures map to explicit UI states.
- Do not send client estimate, difficulty, `rulesVersion`, owner, status or
  compatibility claims as authoritative values.
- Do not expose raw DTOs to presentational components; map to safe view models.

## 7. Detailed interaction behavior

### 7.1 Model or blank selection

- Show three spacious cards as in node `457:1698`.
- Each card has a title, one-sentence explanation and selected/unselected state.
- Selecting a published model creates a configured-design lineage; it does not
  mutate the catalogue model. Populate all default gemstones and attachments
  before Save/Evaluate; display the server's complete default score and level.
- Blank design starts with server defaults and highlights required fields.
- Disable the continue action while options are loading or the create command is
  pending.

### 7.2 Parameter configuration

- Group controls into material, shape/size, dimensions, gemstone/component, attachments and
  engraving sections according to the returned schema.
- **Material Choices**: Render cards for active Silver 925 (`MAT-SILVER-925`), Copper (`MAT-COPPER`), and Stainless Steel 316L (`MAT-STAINLESS-STEEL`); under the proposed policy, disable a returned card when `selectable` is false and explain `DIFFICULTY_SCORE_PENDING`. Gold options are deactivated and not displayed.
- **Ring Size & Millimeter Perimeter**: Render accessible pills for sizes `6` through `29`. Each pill displays its number and corresponding perimeter: e.g., `Size 10 (50 mm)` and `Size 29 (69 mm)` ($P = \text{size} + 40\text{mm}$).
- **Interactive Dimension Sliders**:
  - Ring thickness slider: min 1.0mm, max 3.0mm, step 0.1mm, default 1.8mm.
  - Ring band width slider: min 1.5mm, max 6.0mm, step 0.1mm, default 3.5mm.
  - Numeric values and units ("mm") update synchronously beside each slider.
- **Multi-Gemstone Quantities**:
  - Each gemstone card provides stepper buttons `[-] {count} [+]` permitting selection of multiple stones per type up to `maxQuantity` (10 for CZ, 4 for Moissanite).
  - Displays unit price, current count, and calculated subtotal.
  - Header badge indicates total stones selected across all types.
- Put the explanation directly beneath the control label.
- Preserve unrelated selections when one field becomes invalid.
- Update the preview immediately on every local change.
- Show an unsaved indicator after local edits; do not claim `VALIDATED` until
  Save/Evaluate succeeds.

### 7.3 Preview and estimate

- **Dual View SVG Preview**:
  - **Top View (Mặt chính diện)**: Dynamic circular ring band with stroke thickness scaling to `thicknessMm`, rendering surface textures (hammered ball-peen indentations, single twist, wavy curves, comfort beveled), size watermark, and draggable gemstones.
  - **Side View (Mặt cạnh / Tiết diện ngang)**: Dynamic cylindrical profile with height scaling to `bandWidthMm` (1.5mm - 6.0mm) and wall thickness scaling to `thicknessMm` (1.0mm - 3.0mm), displaying prong mounts and stone silhouettes.
- **Constrained Circular Drag-and-Drop**:
  - Individual gemstones rendered on Top View can be clicked and dragged along the ring circle band ($R = 85\text{px}$, center $(150, 135)$).
  - Pointer movement is projected onto the polar angle $\theta = \text{atan2}(y - 135, x - 150)$ so stones are mathematically constrained strictly to the ring circumference and **cannot be dragged outside or away from the ring band**.
  - Visual feedback: dashed guide track and pulsing drag halo appear during dragging.
- Desktop: controls occupy the left panel and preview/summary occupy the right.
- Mobile: preview is above controls, with the primary action below the controls.
- Preview labels show selected material, gemstone and size in plain language.
- Use `Indicative estimate`/`Tham khảo` and the warning that package price and
  deposit are unchanged.
- A missing optional value shows an incomplete neutral preview, not a guessed
  ring model.

### 7.4 Validation, conflict and recovery

- Field errors appear beside the control and focus moves to the first invalid
  field after a failed command.
- While saving/evaluating, preserve the draft and disable duplicate submission.
- On `DESIGN_VERSION_CONFLICT`, keep unsaved values in memory, load the latest
  version, and give the user an explicit reconcile/retry action.
- On `DESIGN_PACKAGE_INCOMPATIBLE`, preserve local input and offer a compatible
  model or general design entry. Node `457:1795` is historical branch recovery.
- On a Guest protected action, offer Member sign-in without sending a design
  command or creating server-side session state.

### 7.5 Multi-Item Engraving, 160x160 Laser Cropper & Surface Texture Alert

- **Flat Standard Engraving Fee**: Standard engraving is charged once (+80.000 ₫) regardless of how many text/symbol items the customer adds.
- **Multiple Engraving Placements**: Customers can add multiple text lines or symbol shortcuts (♥, ∞, ★, ✿, 💍), configuring each item's placement as `INNER_BAND` (Mặt trong) or `OUTER_BAND` (Mặt ngoài).
- **Surface Texture Warning Banner**: If any engraving item or uploaded image is placed on `OUTER_BAND` while a textured ring shape (`SHP-HAMMERED`, `SHP-BALL-PEEN`, `SHP-SINGLE-TWIST`, `SHP-TWISTED`, `SHP-WAVE`) is active, the UI displays a prominent warning card alerting that outer engraving may appear distorted or lack definition.
- **Custom Laser Image Cropper (`LaserEngravingImageCropper`)**:
  - Lets customers upload artwork/signatures (+100.000 ₫ per image).
  - Provides a draggable red framing box constrained to 160px × 160px.
  - Generates a live monochrome (B&W) laser simulation preview via HTML5 Canvas grayscale thresholding.
  - Updates technical configuration summary and estimated price in real time.
- On read failure, keep navigation context and offer retry.
- On write retry, reuse the original idempotency key and preserve form data.

## 8. Accessibility and responsive acceptance

- All controls have accessible names, visible focus and keyboard operation.
- Slider value, unit, minimum/maximum and explanation are available to assistive
  technology; provide a non-slider input when the option contract requires it.
- Selected material/card states are not conveyed by color alone.
- Error/help text is associated with the relevant field.
- The 390px mobile layout must not require horizontal scrolling.
- The 1440px desktop layout keeps preview and controls readable without dense
  tables or overlapping helper text.
- Vietnamese and English strings come from shared i18n resources.

## 9. Implementation phases and tests

### Phase 1 — Foundation

- Add route registration and feature API adapter.
- Add bilingual messages and feature CSS.
- Add DTO-to-view-model mapping and local draft/preview utilities.

### Phase 2 — Customer screens

- Implement model/blank selection.
- Implement dynamic configuration controls.
- Implement live preview and responsive layouts.
- Implement Save/Evaluate and evaluation summary.

### Phase 3 — Persistence and recommendation boundary

- Implement Member history/archive UI.
- Implement Guest sign-in recovery without a protected design command.
- Implement general catalogue, compatible package filtering and saved-design
  recommendation/consultation states without booking attachment.

### Phase 4 — Tests

- Route and permission behavior for Guest and Member.
- Server-driven controls and no hard-coded catalogue options.
- Immediate preview updates without network calls per slider movement.
- Idempotency-key reuse on write retry.
- `If-Match-Version` and stale-version recovery.
- Loading, empty, validation, forbidden, sign-in-required, 422, 503 and retry
  states.
- Estimate advisory label and no package/deposit mutation in the UI.
- Desktop/mobile layout and keyboard/accessibility behavior.
- Run the existing frontend test and build commands after implementation.

## 10. Explicit blockers

The frontend agent must stop and report a blocker instead of inventing a
compatibility layer when:

- The OpenAPI response does not provide enough metadata to render a control.
- A published model is missing a complete default score/level or references a
  disabled option; the backend must enforce the catalogue/policy guard.
- Backend error codes or headers differ from the Feature-012 contract.
- The Figma revision is changed in a way that affects route, state or permission
  behavior and has not been approved.

## 11. Required change implementation

The next frontend increment must implement the following against the updated
contract:

- Enforce and display the aggregate gemstone limit of 10.
- Enforce separate quotas of 2 `TEXT`, 2 `SYMBOL` and 2 custom images.
- Add persisted contrast and sharpness controls, each integer 0..100 with
  default 50.
- Derive all engraving previews from complete item/image collections.
- Restore all engraving text and image previews from the saved response.
- Never use transient `previewDataUrl` as the only restoration source.
- Normalize all Feature-012 messages and labels to UTF-8 and the active locale.
- Show Copper/Steel models in general discovery without a false Silver package
  association; show only compatible models on a package page.
- Show no workshop CTA for Copper/Steel or `TOO_RISKY`; disclose S999 material
  when an S925 design receives Silver Clay advice.
- Show Silver 925 estimated mass above 3g as an advisory excess amount, not an
  invalid design or a payable workshop total.

The implementation agent must add stable test IDs for counters, disabled add
buttons, quota messages, both image sliders, every text preview and every image
preview. Stop and report a contract blocker if the backend does not return a
safe image reference that permits saved-preview restoration.
