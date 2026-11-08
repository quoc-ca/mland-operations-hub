# Feature-012 Architecture Design — Workshop Ring Design Configuration

Date created: 2026-11-08  
Date updated: 2026-11-08  
Author: Ngô Tiến Đạt  
Last updated by: Ngô Tiến Đạt  

**Status:** Proposed design; implementation requires approval of the spec, data
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

## 6. Technical gates

- Existing `ring_designs` mapping must be reconciled before JPA entities are
  implemented.
- Guest session storage must use an approved boundary and enforce 20-minute
  inactivity expiry.
- Manager-owned rule/catalogue data must expose a stable read facade.
- Booking must expose an approved attach/snapshot boundary.
- Module verification must confirm no repository cycle.
