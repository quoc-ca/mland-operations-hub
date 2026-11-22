# Feature-012 UI Flow — Workshop Ring Design Configuration

Date created: 2026-11-08
Date updated: 2026-11-22
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

**Status:** Visual baseline approved by the user on 2026-11-22; implementation
remains subject to the feature specification, API and engineering gates.

## Existing-style-first rule

All Feature-012 screens must extend the existing customer journey and shared
frontend visual language before introducing new UI patterns. Reuse the current
page shell, typography, spacing, controls, cards, responsive behavior and
bilingual content conventions. The approved Figma frames are a visual baseline,
not permission to change business rules, API behavior or design lifecycle.

## 1. Routes and screens

| Screen | Proposed route | Actor | Required behavior |
| --- | --- | --- | --- |
| Ring Design Entry | `/workshops/:packageReference/design` | Guest/Member | Explain workshop-only scope and choose model (12 models) or blank canvas. |
| Model Selection | `/workshops/:packageReference/design/models` | Guest/Member | Show published models only (minimum 10 models); selecting one creates a new lineage. |
| Blank Design Setup | `/workshops/:packageReference/design/blank` | Guest/Member | Load package-scoped defaults from server. |
| Parameter Configuration | `/workshops/:packageReference/design/configure` | Guest/Member | Edit shape (6 profiles/textures), material (Silver, Copper, Steel), size (6-29 with mm perimeter), thickness slider (1.0-3.0mm), and band width slider (1.5-6.0mm). Synchronously updates Dual View SVG (Top View and Side View). |
| Component Selection | Same design context | Guest/Member | Select gemstone types with steppers `[-] {count} [+]` up to `maxQuantity`, subtotal calculations, and interactive drag-and-drop placement along the ring circle ($R = 85\text{px}$) on the Top View SVG without escaping. |
| Attachment Selection | Same design context | Guest/Member | Select supported attachments and placement. |
| Engraving Configuration | Same design context | Guest/Member | Enter only server-supported engraving content/placement. |
| Evaluation and Summary | `/workshops/:packageReference/design/:designId` | Guest/Member | Show validation, estimate/difficulty as advisory, summary of stones `${quantity}x ${label}`, and current version. |
| Member Design History | `/my/designs` | Member | Show own current and historical versions; archive unsubmitted designs. |
| Guest Session Expired | Same design context | Guest | Explain expiry without exposing internal design data. |
| Workshop Recommendation | `/workshops/:packageReference/design/:designId` | Guest/Member | Show advisory recommendation, reason, Wax Casting two-session note when applicable, and always keep Explore workshops visible. |
| Design Consultation Launch | `/workshops/:packageReference/design/:designId` | Guest/Member | Open Feature-013 chat with the saved design context; do not expose raw internal identifiers. |

The primary Figma frames are `457:1553` (section), `457:1554` (desktop
configuration), `457:1655` (mobile configuration), `457:1698` (model/blank),
`457:1749` (validation/estimate) and `457:1795` (branch recovery).

## 2. Customer flow

The former Branch Compatibility Recovery and Booking Attachment steps are legacy
documentation. The current flow ends at a saved design and recommendation:

```text
Configure design
→ Save design
→ SAVED summary
→ Show recommendation
→ Edit, archive, consult, or explore any workshop
```

```mermaid
flowchart TD
  A[Workshop Package Context] --> B[Design Entry]
  B --> C{Starting point}
  C -->|Published model| D[Model Selection]
  C -->|Blank| E[Blank Design Setup]
  D --> F[Parameter Configuration]
  E --> F
  F --> G[Components / Gemstones]
  G --> H[Attachments]
  H --> I[Engraving]
  I --> J[Save / Evaluate]
  J --> K{Backend result}
  K -->|Invalid| F
  K -->|Saved| L[Saved Design Summary]
  L --> M[Workshop Recommendation]
  L --> N[Edit Design]
  L --> O[Archive Design]
  L --> P[Feature-013 Consultation]
  M --> Q[Explore All Workshops]
```

## 3. UI state requirements

- Loading options, design draft and version history.
- Empty published-model catalogue.
- Invalid or incomplete parameter input.
- Inactive/unsupported option returned by backend.
- Saving/evaluating pending state with duplicate command disabled.
- Advisory estimate/difficulty clearly separated from package price/deposit.
- Stale version conflict while preserving unsaved input.
- Guest session expired after 20 minutes inactivity.
- Branch incompatibility with a clear correction action.
- Booking submit failure with design-preserved retry.
- Forbidden/not-found state without leaking design existence.
- Service unavailable state with explicit retry.

### 3.1 Screen-to-API mapping

| Screen/action | Read/write boundary | Required frontend behavior |
| --- | --- | --- |
| Entry and model/blank selection | `GET /api/v1/workshop-design-options`, then `POST /api/v1/workshop-designs` | Render only server-returned models/options; create one opaque design context with an idempotency key. |
| Parameter configuration | Local draft state | Update preview immediately; do not call the backend for every slider movement. |
| Save/Evaluate | `POST /api/v1/workshop-designs/{designId}/versions` or `/evaluate` | Send complete normalized configuration, `Idempotency-Key` and `If-Match-Version`; disable duplicate submission while pending. |
| Evaluation result | Response from Save/Evaluate or `GET /api/v1/workshop-designs/{designId}` | Replace authoritative version and evaluation snapshot only after a successful response. |
| Archive | `POST /api/v1/workshop-designs/{designId}/archive` | Confirm intent, send version header and preserve history in the UI. |
| Workshop discovery | Recommendation projection from the saved-design response | Show the advisory package CTA and exact reason text; keep the Explore Workshops action visible and never force the recommendation. |
| Design consultation | Feature-013 consultation entry with a safe design snapshot | Open a new or existing consultation context with the selected design attached; do not expose internal design IDs or require a booking. |
| Former booking attachment | Retired endpoint/facade | Do not render or call `attach-to-booking`; historical booking-attachment screens are not part of the current flow. |

The exact request and response fields remain owned by
`contracts/workshop-ring-design.openapi.yaml`. The UI must use an adapter to
map DTOs into `PreviewModel` and `DesignViewModel`; components must not consume
raw API DTOs directly.

### 3.2 Visual state matrix

| State | Visual treatment from Figma | Behavior |
| --- | --- | --- |
| Initial loading | Skeleton/neutral preview panel | Disable Save/Evaluate and show no invented options. |
| Model selected | Selected card/pill and populated preview | Allow configuration with package-scoped options. |
| Blank design | Empty/neutral preview with required-field guidance | Do not allow server Save/Evaluate until required fields are valid. |
| Local changes | Live preview updates; summary may show unsaved indicator | Keep local draft even if a read request fails. |
| Saving/Evaluating | Pending label and disabled duplicate command | Keep the current preview and form data. |
| Validated | Green semantic status and evaluation summary | Show advisory estimate and `rulesVersion` only if returned by API. |
| Invalid | Field-level message beside the control | Preserve unrelated selections and focus the first invalid control. |
| Branch incompatible | Recovery panel matching node `457:1795` | Offer replace-option or change-branch action; do not discard design. |
| Session expired | Dedicated safe expiry state | Clear unusable session reference and offer restart/login without revealing data. |
| Stale version/conflict | Conflict notice with reload/reconcile action | Preserve local draft in memory; reload latest server version before retry. |
| Service failure | Error notice and retry | Retry reads normally; retry writes with the original idempotency key. |

## 4. UX and privacy rules

- Mobile-first customer flow; responsive desktop layout follows shared frontend
  baseline.
- Vietnamese/English labels use shared i18n messages.
- Do not show database IDs, raw Guest session values or private Member data.
- Do not label estimate as payable price or invoice amount.
- Use text and semantic status in addition to color.
- All controls require keyboard access, visible focus and announced validation.
- Parameter changes must show which fields are invalid without silently resetting
  unrelated selections.
- The UI does not infer branch compatibility or authorization locally.

## 5. Integration boundary

The booking feature passes only the approved package, branch, session and opaque
design context. It does not import Feature-012 repositories or internal state.
Feature-012 receives backend responses through its API adapter and does not
create booking/payment UI side effects.

## 6. Visual gate

Figma sections and exact node IDs must be created and approved before frontend
implementation. The Figma revision must be recorded in `version.md` and must not
override the API/spec behavior.

## 7. Review readiness

The screen flow is ready for design review. It must use opaque design/session
references, server-returned option data and advisory evaluation labels. It must
not display internal IDs, raw session values, private Member data or estimate as
payable price. Frontend implementation waits for the approved API contract and
Figma revision; no new UI business rule is introduced here.

## 8. Quota, preprocessing and encoding states

- Gemstone summary shows `current / 10`; the add control is disabled at 10 and
  shows a localized explanation.
- Text, symbol and image engraving controls show independent `0/2` counters.
- Each image card includes contrast and sharpness sliders from 0 to 100,
  default 50, with visible value and explanation.
- Configurator and saved-design previews render every collection member. They
  must not reduce the collection to the first text item or a blank image.
- Loading and error placeholders reserve the same preview dimensions as loaded
  content to avoid layout shift.
- All labels and messages use the active locale; mixed-language fallback is
  prohibited.
