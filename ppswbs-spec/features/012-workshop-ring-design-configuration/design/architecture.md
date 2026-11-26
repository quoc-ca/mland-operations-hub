# Feature-012 Architecture Design — Workshop Ring Design Configuration

**Date created**: 2026-11-08
**Date updated**: 2026-11-26
**Author**: Ngô Tiến Đạt
**Last updated by**: Phan Sơn

**Status:** 2026-11-22 lifecycle change and proposed 2026-11-26 Admin policy documented; implementation approval pending.

Current boundary: Feature-012 owns configuration, saved-design persistence,
recommendation and safe consultation/reference snapshots. The former booking
attachment facade and booking snapshot are historical only and must not be
implemented in the current baseline.

The former booking attachment facade is no longer a current dependency. Feature-
012 exposes owner-safe design snapshots to Feature-004 and a read-only workshop
recommendation projection resolved through the catalogue boundary.

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
├── dto/                 # proposed: public, owner and Admin DTOs
├── service/             # proposed: use cases and transactions
├── facade/              # proposed: public cross-feature boundary
├── dao/                 # proposed: persistence boundary
├── entity/              # proposed: internal JPA model
├── rules/               # proposed: option compatibility/evaluation ports
└── audit/               # proposed: minimized audit boundary
```

Expected public boundaries:

- `WorkshopRingDesignFacade`
  - `getAvailableOptions(optionalPackageReference)`
  - `validateDesign(...)`
  - `getDesignSnapshot(...)`
- `DesignDraftApplicationService`
  - `createDraft(...)`
  - `saveVersion(...)`
  - `evaluate(...)`
  - `archive(...)`
- `DesignOptionRulesPort`
  - `loadOptions(optionalPackageReference)`
  - `validateCompatibility(...)`
  - `evaluate(...)`
- No Guest design-session or booking attachment port; Guest preview is local-only.

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
│   └── SignInRequiredPanel.jsx
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
                         WorkshopRingDesignFacade → Feature-013 consultation / Feature-004 reference
```

Feature-003 may depend on the public facade only. Feature-012 must not depend
on Feature-003 repositories or private DTOs.

## 5. Data flow

```text
General design catalogue or compatible package page
→ design entry
→ options API
→ local form state
→ Save/Evaluate command
→ shared HTTP/auth transport
→ controller
→ identity + ownership
→ option/rule facades
→ version transaction
→ safe DesignResponse
→ summary UI
→ material-compatible workshop advice or Feature-013 consultation
```

The server is authoritative for permission, option availability, compatibility,
estimate, difficulty and version.

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
│   ├── PackageCompatibilityPanel.jsx
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

Read requests use the project's resource pattern. Save, evaluate and archive
commands use the project's command pattern, generate one
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
- Guest preview state must remain local and non-authoritative; no Guest session
  storage or expiry workflow is part of Feature-012.
- Historical Manager-owned rule gate is superseded for difficulty by Section 8;
  non-difficulty catalogue facts still need a stable read boundary.
- General catalogue and package-filtered models must be consistent; Copper/Steel
  model records require an append-only correction of their Silver package association.
- Policy preview/publish and catalogue model/option writes must preserve every
  active published model's complete default score and eligibility.
- Module verification must confirm no repository cycle.

The logical design is ready for review. Implementation remains gated by the
physical `ring_designs` mapping, the Member ownership boundary, the
proposed Admin policy boundary, the approved WorkshopRingDesignFacade and
formal approval.
The `V102` migration baseline is historical. Repository migration files were
observed through `V203` on 2026-11-26; the actual applied database baseline
must be verified before an append-only policy migration is numbered.

## 8. Proposed Admin difficulty-policy architecture — 2026-11-26

This section supersedes Manager-owned difficulty assumptions above. Feature-012
owns policy storage, evaluator and Admin API; FE-09 owns only the Admin UI. The
[Admin OpenAPI](../contracts/admin-ring-difficulty.openapi.yaml) owns transport
fields and errors; [data model](../data-model.md#7-proposed-difficulty-policy-model--2026-11-26)
owns schema and snapshot invariants. React/Vite, Java/Spring Boot, JPA and
Flyway are existing stack choices; no new library or generic parameter store
is proposed.

| Production unit and location | Status/source | Responsibility and proposed use-case boundary |
| --- | --- | --- |
| `rules/ManagerDesignRuleAdapter.java` → `RingDesignRuleEvaluator` in `ringdesignconfiguration` | Existing adapter; rename/refactor proposed by FE-012 spec §4.1 | `evaluate(request, effectivePolicy)` uses one version for score, level and recommendation; no literal coefficients or frontend-owned thresholds. |
| `controller/AdminRingDifficultyPolicyController.java` | Proposed by Admin OpenAPI | `getActive`, `listVersions`, `preview`, `publish`; transport validation and active Admin enforcement. |
| `service/RingDifficultyPolicyService.java` | Proposed by FE-012 spec §4.1 | `readActive`, `readHistory`, `preview`, `publish`; whole-candidate validation, transaction, concurrency and idempotency. |
| `dao/RingDifficultyPolicyRepository.java`, `dao/RingDifficultyOptionScoreRepository.java` and `dao/RingDifficultyPreviewProofRepository.java` | Proposed by data model §7 | Read immutable versions/entries, retain short-lived preview proof and atomically switch the effective version through JPA. |
| `domain/RingDifficultyPolicyVersion.java`, `domain/RingDifficultyOptionScore.java`, preview-proof and publication audit entities | Proposed by data model §7 | Persist complete versions, option scores, 15-minute proof metadata and minimized old/new publication evidence. |
| `dto/AdminRingDifficulty*Dto.java` and evaluation response DTO | Proposed by Admin OpenAPI and customer OpenAPI | Separate Admin candidate/preview/history transport from safe public score/level/recommendation fields; no JPA entity exposure. |
| `service/WorkshopRingDesignApplicationService.java` | Existing; change proposed by FE-012 spec §4.1 | Bind save/evaluate to one effective version and return stored historical snapshot on reads. |
| `controller/WorkshopDesignOptionsController.java` and customer design controller | Existing; response change proposed | Return versioned option scores and server-derived evaluation metadata without exposing Admin audit. |
| `dao/RingCatalogueModelRepository.java` and model publication boundary | Existing repository; guard proposed by FE-012 spec §4.1 | Read all active models for general discovery, filter package-compatible models, validate full defaults before model publication and option retirement. |
| `ui/DesignEvaluationPage.jsx` and `ui/RingModelSelectionPage.jsx` | Existing; display change proposed by FE-012 spec §6 | Render returned score/level/recommendation without `/10`, with a too-risky no-workshop state. |
| `domain/designPreview.js` and customer API adapter | Existing; refactor proposed by customer OpenAPI | Stop authoritative local classification/package selection; map server metadata into view models while keeping visual preview local. |

The Admin flow is FE-09 page → FE-09 API client → proposed Admin controller →
policy service → JPA repositories. Preview creates short-lived server proof;
publish checks its candidate digest, actor, policy/catalogue version and expiry before the
effective-pointer transaction. The customer flow is design controller →
application service → evaluator + one active policy → immutable evaluation
snapshot. An unauthorized Admin call stops at the controller/security boundary;
invalid or stale publication stops before effective-pointer mutation. There is
no cross-module repository access or Manager difficulty write path. New active
options remain visible but disabled for customer selection until an Admin
publishes scores for their references, provided no published model uses them.
The existing create path currently scores only material and shape while saving
model default components separately; the proposed evaluator must score the
complete defaults. Published-model impact is exhaustive and checked again
inside the publication transaction.

## 9. Current gaps and approval boundary

The current code still uses catalogue score columns, frontend thresholds and
model scoring that omits default components. A new append-only migration,
current DB baseline verification, revised customer contract, general catalogue
entry and written Admin-screen visual approval are dependencies. This section is
`PROPOSED`, not implementation authority. Historical Feature-012 approval does
not approve these units, API fields, authorization or schema.
