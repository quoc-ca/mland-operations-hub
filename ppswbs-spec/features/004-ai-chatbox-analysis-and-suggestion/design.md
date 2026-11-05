# Feature-004 Frontend Design — Independent AI Design Workspace

**Date created**: 2026-10-24
**Date updated**: 2026-11-05
**Author**: Ngô Tiến Đạt
**Last updated by**: Ngô Tiến Đạt

**Feature**: `004-ai-chatbox-analysis-and-suggestion`
**Status**: Frontend behavior synchronized with the current React/Vite implementation; backend contract blockers remain documented
**Design baseline**: [`ppswbs-spec/DESIGN.md`](../../DESIGN.md) and [`FRONTEND-ARCHITECTURE.md`](../../FRONTEND-ARCHITECTURE.md)

## 1. Objective and boundary

Feature-004 exposes three independent capabilities:

1. Guest or Member Basic AI Support.
2. Member-owned Independent Design Request with optional description and/or reference image.
3. Manager advisory price suggestion for a new workshop package or retail product proposal.

The Design Request flow is independent from booking. It must not render or require
package, location, session, booking, payment, deposit, ticket, capacity or settlement
data. The frontend never decides authorization or business state; the backend remains
the authority.

## 2. Confirmed actor and input policy

| Actor | Frontend capability |
| --- | --- |
| Guest | Basic AI Support only. The Design Request entry is protected by `RequireSession`. |
| Member | Basic AI Support, create an own Design Request and view its own result. |
| Staff | Open review queue/detail when backend grants review permission. |
| Manager | Review requests and use the price-suggestion screen when backend grants authority. |
| Admin | View only the technical/audit projection returned by the backend. |

An Independent Design Request is valid when it contains a description, an image, or
both. A description-only request does not show image consent. An image request must
record the configured consent before submission. The frontend sends the current
backend field names and does not convert them to a different OpenAPI shape.

## 3. Frontend structure

### 3.1 Current implementation

The current slice is:

```text
src/features/designreview/
  api/designReviewApi.js
  api/useDesignReviewApi.js
  public/AiSupportPage.jsx
  workspace/DesignWorkspacePage.jsx
  workspace/DesignDetailPage.jsx
  review/ReviewQueuePage.jsx
  review/ReviewDetailPage.jsx
  pricing/PriceProposalAiPage.jsx
  rules.js
  messages.js
  designreview.css
  test/
```

Routes are registered by `src/features/designreview/routes.jsx` and composed in
`src/app/router.jsx`. Shared HTTP, Firebase session, i18n and UI primitives are
used instead of feature-local replacements.

### 3.2 Normalization direction

Future maintenance may map the current slice to the project architecture as:

```text
ui/             pages and feature components
application/    use-case hooks and command/resource lifecycle
domain/         pure validation and state rules
infrastructure/ REST adapter and DTO mapping
routes/         feature-owned route declarations
tests/          feature tests
```

This is a maintenance direction, not a requirement to refactor the current
prototype slice during documentation synchronization.

## 4. Route and screen inventory

| ID | Route | Actor | Required states |
| --- | --- | --- | --- |
| `F04-AI-CHAT` | `/ai-support` | Guest/Member | Empty, pending, validation, provider failure, retry, advisory scope label. |
| `F04-DESIGN-WORKSPACE` | `/design-requests/new` | Member | Description-only, image-only, combined input, validation, consent, pending and recoverable failure. |
| `F04-CONSENT` | Inline workspace state | Member | Purpose/version display, affirmative consent, consent-required error. |
| `F04-IMAGE-UPLOAD` | Inline workspace state | Member | JPEG/PNG/WEBP, 10 MB, 4096×4096, rate-limit and file validation states. |
| `F04-ANALYSIS-STATUS` | `/design-requests/:designRequestId` | Member owner | `PROCESSING`, `SUCCEEDED`, `FAILED`, retry and safe failure evidence. |
| `F04-RESULT` | Same detail route | Member owner | Candidate features, image quality, confidence/uncertainty and review status; never raw provider payload. |
| `F04-REVIEW-QUEUE` | `/design-review-requests` | Staff/Manager | Loading, empty, forbidden and API failure states. |
| `F04-REVIEW-DETAIL` | `/design-review-requests/:reviewRequestId` | Staff/Manager | Decision form, reason validation, stale version and conflict recovery. |
| `F04-PRICE-PROPOSAL` | `/price-proposals/:proposalId/ai-suggestions` | Manager/Admin projection | Advisory suggestion, manual fallback, decision and technical-audit view. |

## 5. Screen and API mapping

| API | Screen | Frontend behavior |
| --- | --- | --- |
| `POST /api/v1/ai-support/messages` | `F04-AI-CHAT` | Guest uses public scope; Member may send Firebase identity. Invalid authenticated identity is not downgraded to Guest. |
| `POST /api/v1/design-requests` | Workspace/Consent/Upload | Multipart `description`, `catalogueReference`, consent fields, `designVersion`, optional `image`, and one `Idempotency-Key`. |
| `GET /api/v1/design-requests/{id}` | Status/Result | Owner-safe projection; cross-owner access is displayed as typed forbidden/not-found behavior. |
| `GET /api/v1/design-review-requests` | Review Queue | Display the returned queue only; do not invent branch/session filters. |
| `POST /api/v1/design-review-requests/{id}/decisions` | Review Detail | Send `designVersion`, decision and reason; preserve form state on recoverable error. |
| Price suggestion endpoints | Price Proposal/Admin Audit | Manager sees commercial advisory data; Admin sees only the technical projection returned by the backend. |

The current backend returns `message` for Basic AI Support, uses `designVersion`
for review decisions, accepts raw price context with `targetType`, and currently
returns `200` for price suggestion. These shapes are implementation compatibility
facts; OpenAPI synchronization remains a backend task.

## 6. Data and command lifecycle

```text
Route
  → feature page
  → application hook/command
  → shared HTTP client
  → Firebase token when required
  → backend controller/service
  → safe DTO
  → local feature state
  → loading/success/error UI
```

Every write action creates one idempotency key. A retry of the same command reuses
the key. The client does not auto-retry commands or infer a successful mutation
from a timeout.

## 7. Required UI behavior

- Customer screens are mobile-first; operational screens are desktop-first and responsive.
- Use bilingual messages from the feature message catalog.
- Keep recoverable input on validation, network, `409`, `429` and `503` errors.
- Disable duplicate submission while a command is pending.
- Treat `401` through the shared auth refresh path; show access denied for `403` without signing out.
- Show text labels with semantic colors; color alone cannot communicate status.
- Provide keyboard-accessible controls, visible focus, labels and live status for asynchronous changes.
- Never render raw image payload, provider payload, Firebase token, private identity or internal price suggestion to an unauthorized actor.

## 8. Acceptance traceability

The frontend handoff is complete only when the implementation can trace each screen
to `spec.md`, the API contract, backend response fields and tests. The current
implementation is a frontend delivery slice, not proof that live backend integration,
OpenAPI synchronization or production provider integration has passed.

## 9. Figma visual mapping (2026-11-05)

The visual handoff is pending reviewer approval in Figma file
`m5Cyk5rit6lKaX3TPprSow`. The created sections are `413:2` for Feature-004
customer screens, `414:1083` for Staff operations and `414:2983` for Manager
operations. The frames include desktop/mobile variants for AI chat, independent
design workspace, consent, image upload, analysis/result, review decisions and
Manager price proposal. Visual frames do not alter the independent flow or any
backend authority rule.
