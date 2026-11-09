# Feature-012 Architecture Design — Workshop Ring Design Configuration

Date created: 2026-11-08
Date updated: 2026-11-09
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

**Status:** Frontend design handoff ready for review; implementation requires approval of the spec, data
model, API contract, Guest session boundary and Workshop Booking facade.

## 1. Design objective and boundaries

Feature-012 owns customer-facing workshop ring configuration and the safe
configured-design facade. It does not own package catalogue administration,
branch/session capacity, payment or booking persistence.

`ringdesignconfiguration` is the proposed backend logical module name. It must
be verified with Spring Modulith before implementation.

## 2. Proposed backend structure

```text
ppswbs_backend/src/main/java/.../features/ringdesignconfiguration/
├── controller/          # proposed: transport and validation
├── dto/                 # proposed: public, owner and booking DTOs
├── service/             # proposed: use cases and transactions
├── facade/              # proposed: public cross-feature boundary
├── dao/                 # proposed: persistence boundary
├── entity/              # proposed: internal JPA model
├── rules/               # proposed: option compatibility/evaluation ports
└── audit/               # proposed: minimized audit boundary
```

Expected public boundaries:

- `WorkshopRingDesignFacade`
  - `getAvailableOptions(...)`
  - `validateDesign(...)`
  - `getDesignSnapshot(...)`
  - `attachDesignToBooking(...)`
- `DesignDraftApplicationService`
  - `createDraft(...)`
  - `saveVersion(...)`
  - `evaluate(...)`
  - `archive(...)`
- `DesignOptionRulesPort`
  - `loadPackageOptions(...)`
  - `validateCompatibility(...)`
  - `evaluate(...)`
- `GuestDesignSessionPort`
  - `issue(...)`
  - `resolve(...)`
  - `touch(...)`
  - `expire(...)`

These names are proposed logical units, not approved Java API names.

## 3. Proposed frontend structure

```text
ppswbs_frontend/src/features/ringdesignconfiguration/
├── routes/
├── ui/
│   ├── RingDesignEntryPage.jsx
│   ├── RingModelSelectionPage.jsx
│   ├── RingParameterConfigurationPage.jsx
│   ├── ComponentSelectionPage.jsx
│   ├── AttachmentSelectionPage.jsx
│   ├── EngravingConfigurationPage.jsx
│   ├── DesignEvaluationPage.jsx
│   ├── DesignSummaryPage.jsx
│   ├── MemberDesignHistoryPage.jsx
│   └── GuestSessionExpiredPage.jsx
├── application/
├── domain/
├── infrastructure/
├── messages/
└── tests/
```

The frontend uses existing shared HTTP, Firebase auth, i18n and UI modules. It
does not add a state library, call `fetch` directly, or access booking/catalogue
repositories.

## 4. Dependency direction

```text
app/router
  → ringdesignconfiguration/routes
  → ringdesignconfiguration/ui
  → ringdesignconfiguration/application
  → ringdesignconfiguration/domain
  → ringdesignconfiguration/infrastructure
  → shared/http + shared/auth + shared/i18n + shared/ui
```

Backend dependency direction:

```text
controller → application service → domain/rules → DAO/entity
                                      ↓
                         catalogue/operations facades
                                      ↓
                         WorkshopRingDesignFacade → Feature-003 Booking
```

Feature-003 may depend on the public facade only. Feature-012 must not depend
on Feature-003 repositories or private DTOs.

## 5. Data flow

```text
Package page
→ design entry
→ options API
→ local form state
→ Save/Evaluate command
→ shared HTTP/auth/session transport
→ controller
→ identity/session + ownership
→ option/rule facades
→ version transaction
→ safe DesignResponse
→ summary UI
→ Booking facade attachment
→ Booking-owned snapshot
```

The server is authoritative for permission, option availability, compatibility,
estimate, difficulty, version and lock state.

## 6. Frontend handoff from the Figma baseline

The current repository uses React 19, Vite and `react-router` 7. Feature-012
should follow the existing feature conventions visible in
`ppswbs_frontend/src/features/reviewmanagement/` and
`ppswbs_frontend/src/features/designreview/` rather than adding a state library
or UI framework.

Recommended feature boundary:

```text
ppswbs_frontend/src/features/ringdesignconfiguration/
├── routes.jsx
├── api/
│   └── workshopRingDesignApi.js
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
│   ├── BranchCompatibilityPage.jsx
│   └── ring-design.css
├── messages.js
└── tests/
```

These are implementation units to be confirmed by the frontend agent; they do
not create a new backend module or a second booking flow. Use the existing
shared modules:

```text
shared/api/http.js
shared/ui/components.js
shared/auth/AuthProvider.jsx
shared/auth/RequireSession.jsx
shared/i18n/
```

Read requests use the project's resource pattern. Save, evaluate, archive and
attach commands use the project's command pattern, generate one
`Idempotency-Key` per user action, and reuse that key for a retry. Versioned
commands send `If-Match-Version` from the last server response. The frontend
must not retry a write with a new key or bypass the shared HTTP/auth client.

### 6.1 Figma source and component mapping

| Figma node | Purpose | Frontend implication |
| --- | --- | --- |
| `457:1553` | `F12 · Workshop Ring Design Configuration` section | Canonical Feature-012 visual section for this handoff. |
| `457:1554` | Desktop configurator, 1440×1080 | Two-column layout: controls left, live preview and summary right. |
| `457:1655` | Mobile configurator, 390×844 | Stacked preview-first layout with compact controls. |
| `457:1698` | Model or blank selection | Three starting-point cards and package-aware explanation. |
| `457:1749` | Validation and estimate | Safe summary, compatibility state and advisory estimate. |
| `457:1795` | Branch compatibility recovery | Correction actions without losing the saved design. |

The Figma Button component set key is
`ab924dce8b851fd820bd0d56da24a9c489311ae6`; the Standard slider component set
key is `4851d51e7cd3b6f9988e35f1ea59cbd4d382627b`. These keys document the
visual source only; production code must use the repository's shared UI
primitives and CSS conventions.

### 6.2 Preview architecture

`RingPreview` receives a normalized local preview model, not an API entity:

```text
PreviewModel = {
  modelReference?, shapeReference?, materialReference?,
  ringSize?, bandWidth?, gemstoneReference?,
  attachmentReferences[], engravingPreview?
}
```

The preview is derived locally whenever a control changes. It must visibly
reflect selected material, gemstone, ring size and band width. It must tolerate
missing optional values and show a neutral incomplete state rather than invent a
catalogue option. The Figma ring illustration is a 2D visual baseline; no 3D,
WebGL or external image service is implied by this design.

`RingPreview` must never display the estimate as a payable price, expose raw
provider/database IDs, or mutate the draft. Save/Evaluate sends the normalized
server contract and replaces the local authoritative version only after success.

### 6.3 Responsive and accessibility rules

- Desktop keeps the controls and preview in separate readable panels with a
  persistent summary; avoid compressing controls into a dense table.
- Mobile places the preview before controls and keeps the primary action visible
  after the controls; controls are full-width and vertically spaced.
- Every slider has a visible label, current value, unit/range explanation and a
  non-slider fallback such as numeric/select input when the returned option
  requires it.
- Every selectable pill/card has selected, focus, disabled and unavailable
  semantics. Color alone must not indicate the active choice.
- Error/help text is adjacent to the affected field and announced through the
  existing accessibility pattern.
- Vietnamese and English labels must be supplied through shared i18n messages;
  no literal UI copy is hidden in the API adapter.

## 7. Technical gates

- Existing `ring_designs` mapping must be reconciled before JPA entities are
  implemented.
- Guest session storage must use an approved boundary and enforce 20-minute
  inactivity expiry.
- Manager-owned rule/catalogue data must expose a stable read facade.
- Booking must expose an approved attach/snapshot boundary.
- Module verification must confirm no repository cycle.

The logical design is ready for review. Implementation remains gated by the
physical `ring_designs` mapping, the Feature-012-owned Guest session table, the
Manager rule facade, the approved WorkshopRingDesignFacade and formal approval.
The repository migration baseline is `V102`; no migration is created by this
design package.
