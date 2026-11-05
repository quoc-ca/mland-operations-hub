# Feature-011 Frontend Architecture Design — Review Management

**Date created**: 2026-11-03
**Date updated**: 2026-11-05
**Author**: Ngô Tiến Đạt
**Last updated by**: Ngô Tiến Đạt

## 1. Boundary and source of truth

Feature-011 owns review presentation and review commands. Its frontend follows
React/Vite project conventions and consumes the approved OpenAPI contract. The
backend remains authoritative for eligibility, ownership, status, moderation
authority, optimistic version and privacy filtering.

The frontend must not access Workshop or Retail repositories. Cross-feature facts
arrive through Feature-011 API projections backed by the approved eligibility ports.

## 2. Proposed frontend structure

```text
src/features/reviewmanagement/
  routes/
    reviewManagementRoutes.jsx
  ui/
    public/PublicReviewListPage.jsx
    public/PublicReviewDetailPage.jsx
    customer/EligibleReviewSourcesPage.jsx
    customer/GuestReviewLookupPage.jsx
    customer/CreateReviewPage.jsx
    customer/EditReviewPage.jsx
    moderation/ModerationQueuePage.jsx
    moderation/ModerationDetailPage.jsx
    moderation/ReplyEditor.jsx
    components/ReviewCard.jsx
    components/RatingInput.jsx
  application/
    useReviewApi.js
    useReviewResources.js
    useReviewCommands.js
  domain/
    reviewRules.js
    reviewState.js
  infrastructure/
    reviewApi.js
  tests/
```

This is the target structure for the Feature-011 frontend implementation. It does
not require creating placeholder layers before the implementation task is started.
No new state library, UI kit or form library is permitted.

## 3. Dependency direction

```text
app/router
  → reviewmanagement/routes
    → reviewmanagement/ui
      → reviewmanagement/application
        → reviewmanagement/domain
        → reviewmanagement/infrastructure
          → shared/api/http
          → shared/auth
          → shared/i18n
```

UI pages do not call `fetch`, Firebase or backend repositories directly. Domain
rules do not import React, router or browser APIs. The API adapter maps OpenAPI DTOs
to view data and never exposes persistence entities.

## 4. API and auth boundary

The client uses:

- public calls without Firebase identity for public listing/detail;
- Guest lookup and create according to the approved opaque eligibility-handle contract;
- Firebase Bearer identity for Member, Staff and Manager operations;
- `Idempotency-Key` for every write command;
- `If-Match-Version` for owner edit, moderation and reply edit where required.

The route guard may show a sign-in requirement, but it cannot grant a role. A `401`
uses the shared session recovery path; a `403` shows access denied without signing
the user out. Raw contact, transaction source references and private audit evidence
must not enter public UI state.

## 5. Data flow

```text
Route
  → page
  → application resource/command hook
  → reviewApi adapter
  → shared HTTP client and Firebase token
  → Feature-011 controller/service
  → safe DTO projection
  → application state
  → UI state
```

For commands, one idempotency key belongs to one user action. A retry reuses the
same key. A stale version or duplicate-source conflict triggers reload/reconciliation,
not an automatic retry with a new key.

## 6. Backend contract blockers

Frontend implementation must target the approved OpenAPI/spec, not silently follow
the current backend mismatches. Before live integration, resolve or explicitly version:

- idempotency transport currently differing between implementation and OpenAPI;
- Guest retail-product eligibility/create path;
- owner-safe detail projection and cross-user existence behavior;
- Admin technical-only permission boundary;
- DTO/status/header differences;
- migration/history verification and API contract synchronization.
