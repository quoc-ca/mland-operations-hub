# Version 1.3 — Feature 004: AI Chatbox Analysis and Suggestion

- **Feature**: 004 — AI Chatbox Analysis and Suggestion
- **Author**: Ngô Tiến Đạt
- **Last updated by**: Ngô Tiến Đạt
- **Date created**: 2026-10-24
- **Date updated**: 2026-11-01
- **Source of truth**: Feature-004 `spec.md`, `design.md`, OpenAPI contract and `FE-004-DESIGN-INDEPENDENT-HANDOFF.md`

> This file is an implementation status index. Historical audit details are
> condensed here; detailed requirements and design remain in the feature
> artifacts. Phase labels are retained for tracing and audit.

---

## 1. Completed

### Phase A — Contract and boundary

- Feature-004 was rebased from a booking-dependent design flow to an independent Design Workspace.
- The Feature-004 artifacts, `ppswbs-spec/APIs.md`, `spec-general.md`, relevant Report 3 sections and `documents/PROJECT-STATUS.md` were aligned with the independent boundary.
- The independent flow has no booking, location, session, payment, deposit, capacity, ticket or check-in prerequisite or side effect.
- `FE-003` does not call `FE-004` and does not store `designApprovalReference`.

### Phase B — Independent aggregate and persistence

- Replaced the booking-specific request model with `DesignRequest` and `design_requests`.
- Removed booking fields and booking foreign-key assumptions from Feature-004 persistence.
- Refactored consent, analysis, review and image-reference records to use independent Design Request ownership/version data. The current V12 migration still evolves the pre-existing `image_consents` table; this must be verified during Flyway upgrade testing.
- Added append-only migration [`V60__independent_design_request.sql`](../../../ppswbs_backend/src/main/resources/db/migration/V60__independent_design_request.sql).
- Preserved the separation between technical analysis state (`PENDING → PROCESSING → SUCCEEDED / FAILED`) and business review state (`NEED_REVIEW → ACCEPTED / REJECTED / CLARIFICATION_REQUIRED`).

### Phase C — Application services, security and authorization

- Removed Feature-004 calls to `WorkshopBookingFacade` and `getBookingEligibilityForDesign(...)`.
- Preserved the required validation and security rules: 2,000-character Basic AI prompt limit, current 4,000-character Design Request description limit, 10 MB/4096×4096 image limit, prompt-injection screening, structured provider validation, atomic three-attempts-in-five-minutes image rate limiting, idempotent retry and temporary image deletion.
- Preserved the authorization boundary:
  - Guest: Basic AI Support only until Guest ownership policy is approved;
  - Member: own Design Request data under the approved ownership policy;
  - Staff/Manager: review only within approved scope;
  - Manager: business price decision;
  - Admin: technical/audit evidence only;
  - AI Provider and Cloud Storage: no Mland business authority.

### Phase D — API and backend implementation

- Implemented independent REST endpoints for AI support, Design Request creation/read, review queue/decision and price suggestion.
- Removed `bookingCode` from the Design Request controller contract.
- Retired `BookingDesignRequest` and its booking-specific repository.
- Added safe projections; raw images, provider payloads, credentials and unnecessary PII are not returned.
- Replaced the legacy booking review action with the independent `DESIGN_ACCEPTED` action.

### Phase E — Verification evidence

The following results are historical evidence recorded from the implementation run on 2026-10-29. They must be reproduced after the final commit before release. A rerun during this documentation update was attempted but the Maven PowerShell wrapper failed before Maven started (`Cannot index into a null array`), so no new pass claim is made.

- Spring Modulith verification: **2 tests passed**.
- Feature-004 integration suite: **44/44 tests passed**.
- Coverage included Guest/Member access, invalid tokens, prompt injection, consent, image validation, idempotency, rate limiting, ownership isolation, human review conflicts and price-suggestion permissions.
- No Feature-004 production code imports the legacy Workshop Booking package or calls the booking eligibility facade.

---

## 2. Incomplete / Next steps

### Giai đoạn 1: Frontend React + Vite

- Build the standalone `F04-DESIGN-WORKSPACE`; do not place it inside the booking flow.
- Implement Basic AI Chat, Design Request description/image submission, consent and validation states, processing/result screens, owner-safe result view, Staff/Manager review queue, Manager price suggestion and Admin technical/audit projection.
- Cover loading, empty, denied, validation, timeout, rate-limit, stale-version, conflict and retry states.
- Do not add package, location/session, booking summary, payment or ticket screens to Feature-004.

### Giai đoạn 2: Gemini production integration and prompt evaluation

- Confirm the production Gemini model, quota, billing, timeout, retry and circuit-breaker policy.
- Provide production credentials only through the approved secret/configuration channel.
- Evaluate representative ring images and Vietnamese prompt-injection cases.
- Keep provider output advisory and schema-validated; it must never publish prices or make business decisions.

### Giai đoạn 3: Operations, retention and notifications

- Record the implemented policy explicitly: Guest Design Request creation is currently denied; only an active Member may submit; at least a description or image is required; text-only requests are accepted; image requests require consent; every created request enters human review. Revision/withdrawal/approval expiry remain incomplete because no corresponding endpoint/state transition is implemented.
- Confirm whether the current 4,000-character Design Request description limit and deterministic text-only estimate are approved product behavior or must be changed to the spec baseline.
- Decide whether review-result delivery uses polling, email, SSE, WebSocket or another approved notification boundary.
- Verify the scheduled retention cleanup in the target deployment environment.

### Giai đoạn 4: E2E, migration and release readiness

- Add React E2E coverage for the standalone Design Workspace.
- Resolve the repository-wide Flyway numbering conflict: duplicate V6, V7 and V9 ownership.
- Run clean-install and upgrade-path Flyway tests on MySQL.
- Reproduce compile, Modulith and Feature-004 test results from the final commit.
- Review and commit the complete backend/frontend change set together.
- Add operational handoff documentation for Staff and Manager users.

### Phase A — Remaining contract gate

- Update Feature-004 artifacts after the five policy decisions above are approved.
- Do not infer Guest access, review thresholds, retention or revision behavior from the old booking implementation.

### Phase B — Remaining migration gate

- Linearize all repository Flyway migrations without editing already-applied migrations.
- Verify upgrade compatibility and preserve existing data before claiming database readiness.

### Phase C — Remaining implementation gate

- Implement only behavior approved by the final ownership, review and retention policy.
- Keep Account Management access facade-only and preserve separation of duties.

### Phase D — Remaining frontend gate

- Implement the screens and states in `design.md` against the independent API contract.
- Do not reconnect Feature-004 to Workshop Booking or Payment.

### Phase E — Remaining verification gate

- Re-run all backend and frontend checks after the final policy and migration changes.
- Store exact commands, commit hash, environment and results in the next version entry.

---

## 3. Active blockers and audit notes

1. **Policy blocker:** Guest ownership, request-content, review, revision/withdrawal and image-retention policies are not fully approved.
2. **Migration blocker:** Repository-wide Flyway version collisions must be resolved before clean-install/upgrade readiness can be claimed.
3. **Evidence status:** The 44/44 and Modulith results above are historical execution evidence, not fresh verification of this documentation-only update.
4. **Historical baseline:** Earlier booking-coupled implementation notes and the previous `53/53` result are retained only as audit history in Git, not as the current Feature-004 contract.

---

## 4. Traceability index

| Phase | Current evidence | Remaining evidence |
| --- | --- | --- |
| Phase A | Independent contract and boundary artifacts | Approved ownership/review/retention decisions |
| Phase B | Independent entity model and V12 migration | Repository-wide migration linearization and MySQL upgrade test |
| Phase C | Services, security and authorization rebase | Final policy-driven behavior |
| Phase D | Independent REST backend contract | React + Vite Design Workspace |
| Phase E | Historical Modulith and 44/44 integration results | Fresh final-commit verification and E2E evidence |
| Giai đoạn 1 | Backend API available | Frontend implementation |
| Giai đoạn 2 | Provider port and validation boundary | Production Gemini configuration/evaluation |
| Giai đoạn 3 | Retention and review mechanisms exist | Policy and notification decisions |
| Giai đoạn 4 | Test plan exists | Migration, E2E and release evidence |

---

## 5. Current backend flow and data flow

This section describes the code flow currently present in commit `79a770f` (`[UPDATE] Sửa lại code backend luồng 004 độc lập khỏi quá trình booking`). It is an implementation trace, not a replacement for the Feature-004 contract. The flow has no call to Workshop Booking, Payment, Operations, Ticketing or Capacity.

### 5.1. Module and dependency flow

```text
HTTP request
  → designreview.web controller
  → designreview.application service
  → designreview.infrastructure repository / adapter
  → designreview.domain entity / port

designreview
  → accountmanagement.facade only
  → AI provider port implemented by GeminiRestClientAdapter
  → temporary image port implemented by LocalStorageAdapter
```

`designreview/package-info.java` allows only `accountmanagement :: facade`. Controllers do not access repositories directly. Services own the use-case transaction and map entities to safe DTO projections.

### 5.2. Basic AI Support flow

```text
POST /api/v1/ai-support/messages
  → AiSupportController
  → optional CurrentAccountActor extraction for an active Member
  → AiSupportService.askSupport(message, memberId, correlationId)
  → validate blank/length ≤ 2,000 characters
  → detect prompt injection and forbidden private/business context
  → AiTextSupportPort
  → GeminiRestClientAdapter.generateSupportResponse(...)
  → AiTextInteractionRepository.save(...)
  → AiSupportResponse
```

Guest requests are represented with `memberId = null` and `ActorScope.GUEST`. Member requests store the resolved account ID. Successful and rejected interactions are retained with a 30-day expiry. `DataRetentionCleanupJob` later removes expired text records.

### 5.3. Independent Design Request submission flow

```text
POST /api/v1/design-requests (multipart/form-data)
  → DesignRequestController
  → SecurityIdentityExtractor.requireUid(authentication)
  → AccountFacade.resolveAccount(callerUid)
  → require ACTIVE MEMBER
  → DesignRequestRepository idempotency lookup
  → validate description/image presence
  → validate description and prompt-injection patterns
  → if image: consent → ImageRateLimiter → MIME/size/dimensions validation
  → create DesignRequest with PROCESSING + NEED_REVIEW
  → image branch or text-only branch
  → return DesignRequestViewDto with HTTP 202
```

Image branch:

```text
ImageConsentRepository.save
  → ReferenceImageStoragePort.storeTemporary
  → TemporaryImageReferenceRepository.save
  → ImageFeatureAnalysisPort.analyzeRingImage
  → GeminiRestClientAdapter.analyzeRingImage
  → finally: ReferenceImageStoragePort.deleteTemporary
  → TemporaryImageReference marked DELETED_AFTER_PROCESSING
  → DesignAnalysisResultRepository.save(safe JSON projection)
  → DesignRequest becomes SUCCEEDED / AWAITING_HUMAN_REVIEW
```

If image analysis fails, the image is still deleted in `finally`; the request becomes `FAILED` with a safe failure code and `SAFE_RETRY_AVAILABLE`. The current adapter is deterministic/mock-like and returns candidate features, image quality, confidence, explanation and an estimated value; it does not currently call a live Gemini HTTP endpoint.

Text-only branch:

```text
no image
  → no image consent, storage or image rate-limit slot
  → DesignRequest becomes SUCCEEDED / AWAITING_HUMAN_REVIEW
  → DesignAnalysisResultRepository.save(TEXT_DESCRIPTION_ONLY projection)
  → current code assigns estimatedValue = 1,800,000 VND
```

### 5.4. Design Request read flow

```text
GET /api/v1/design-requests/{designRequestId}
  → DesignRequestController
  → SecurityIdentityExtractor.requireUid
  → DesignRequestRepository.findById
  → DesignReviewPermissions.canViewDesignRequest(authorities)
     ├─ internal role: reviewer/admin projection allowed
     └─ otherwise: AccountFacade ownership check against ownerAccountId
  → DesignAnalysisResultRepository.findByDesignRequestId
  → parse safe JSON fields
  → DesignRequestViewDto
```

The response contains request state, safe candidate features, image quality, confidence and explanation. It does not return the raw image or raw provider payload.

### 5.5. Human review flow

```text
GET /api/v1/design-review-requests
  → DesignReviewController
  → SecurityIdentityExtractor.requireUid
  → HumanReviewService.getReviewQueue
  → DesignRequestRepository.findByReviewStatus(NEED_REVIEW)
  → Staff filter estimatedValue ≤ 3,000,000 VND
    or Manager/Owner sees all queued requests
  → map safe DesignRequestViewDto list
```

```text
POST /api/v1/design-review-requests/{id}/decisions
  → parse ACCEPTED / REJECTED / CLARIFICATION_REQUIRED
  → HumanReviewService.recordDecision
  → authorize Staff or Manager
  → load DesignRequest
  → reject self-review
  → reject terminal ACCEPTED/REJECTED state
  → validate design version and Staff value threshold
  → HumanReviewDecisionRepository.save
  → update DesignRequest.reviewStatus and nextAction
  → return safe projection
```

Current implementation keeps the former 3,000,000 VND Staff/Manager routing rule. Branch/delegation scope and explicit conflict-of-interest checks beyond self-review are not implemented in this flow and remain verification gaps.

### 5.6. AI price suggestion flow

```text
POST /api/v1/price-proposals/{proposalId}/ai-suggestions
  → PriceProposalController
  → SecurityIdentityExtractor.requireUid
  → PriceSuggestionService.requestSuggestion
  → Manager/Owner authority check
  → validate proposalId and WORKSHOP_PACKAGE/RETAIL_PRODUCT target
  → AiPriceSuggestionPort
  → GeminiRestClientAdapter.suggestPrice
  → PriceSuggestionRepository.save(advisory=true)
  → Manager-facing PriceSuggestionViewDto
```

```text
GET /api/v1/price-proposals/{proposalId}/ai-suggestions
  → PriceSuggestionService.getSuggestion
  → Manager sees business pricing fields
  → Admin sees technical/audit projection with pricing fields removed
  → Staff/Guest/Member are denied
```

```text
POST /api/v1/price-proposals/{proposalId}/ai-suggestions/decisions
  → Manager authority check
  → validate ACCEPTED / ADJUSTED / REJECTED / MANUAL_FALLBACK
  → validate finalPrice when required
  → PriceSuggestionRepository.save
  → return updated Manager projection
```

The current Feature-004 code stores the Manager decision in `PriceSuggestion`; applying or publishing the final catalogue price remains outside this module.

### 5.7. Persistence data flow

| Runtime data | Producer | Persistence | Consumer/output |
| --- | --- | --- | --- |
| Basic AI prompt/response | `AiSupportService` | `AiTextInteractionRepository` → `ai_text_interactions` | Guest/Member safe response; retention job |
| Independent request metadata | `DesignAnalysisService` | `DesignRequestRepository` → `design_requests` | Owner/reviewer safe projection; facade summary |
| Image consent | `DesignAnalysisService` | `ImageConsentRepository` → `image_consents` | Consent/audit evidence |
| Temporary image reference | image branch | `TemporaryImageReferenceRepository` + `LocalStorageAdapter` | Provider input, then deletion evidence |
| AI image result | image/text branch | `DesignAnalysisResultRepository` → analysis result record | Candidate features, quality, confidence and explanation |
| Human decision | `HumanReviewService` | `HumanReviewDecisionRepository` + `DesignRequestRepository` | Review queue state and owner-safe result |
| Advisory price | `PriceSuggestionService` | `PriceSuggestionRepository` → `price_suggestions` | Manager view or Admin technical projection |

### 5.8. Current implementation caveats

- The Maven wrapper could not be started in the current environment, so this update does not claim fresh test execution.
- Flyway still contains duplicate V6, V7 and V9 filenames; V12 cannot be considered release-ready until repository-wide migration ordering is resolved.
- `V60__independent_design_request.sql` still modifies the legacy `image_consents` schema and requires clean-install/upgrade verification against the actual baseline.
- `DesignAnalysisService` currently requires an active Member even though the entity contains Guest-proof fields.
- `consentPurpose` is accepted by the controller but the current service does not independently validate it against `INDEPENDENT_DESIGN_IMAGE_ANALYSIS`.
- The current in-memory `LocalStorageAdapter` is suitable for deterministic local/test processing, not multi-instance production deployment.

---

## 6. Frontend implementation specification for the next agent

**Status**: Frontend specification plan; implementation not started for Feature-004
**Decision**: Do not use Figma MCP. Reuse the existing React/Vite foundation and conventions already used by Features 002 and 006.

### 6.1. Frontend baseline and rules

The implementation MUST use the existing frontend baseline:

- React 19, Vite 7 and `react-router` 7.
- Feature boundary: `ppswbs_frontend/src/features/designreview/`.
- Shared modules:
  - `src/shared/api/http.js`;
  - `src/shared/auth/AuthProvider.jsx` and `RequireSession.jsx`;
  - `src/shared/i18n/`;
  - `src/shared/ui/`.
- Add no state library, UI kit, form library or new dependency.
- Use real React Router paths for Feature-004; do not add Feature-004 screens to the hash-based prototype route.
- Use `useResource` for reads and `useCommand` for writes.
- Do not auto-retry requests. A retry of a write reuses the same `Idempotency-Key`.
- Use Firebase Bearer authentication through the shared HTTP client. Never store tokens in local/session storage, URLs or logs.
- Route guards and hidden buttons are UX only. Backend authorization remains authoritative.
- Do not store raw images, raw provider payloads or unnecessary PII in long-lived frontend state.
- Do not use mock success responses as evidence of backend integration.
- Reuse shared loading, empty, error, retry, field-error, status, dialog, bilingual and accessibility conventions from Features 002/006.

### 6.2. Route and screen inventory

The first frontend delivery uses these routes:

```text
/ai-support
/design-requests/new
/design-requests/:designRequestId
/design-review-requests
/design-review-requests/:reviewRequestId
/price-proposals/:proposalId/ai-suggestions
```

| Screen ID | Route | Actor | Required behavior |
| --- | --- | --- | --- |
| `F04-AI-CHAT` | `/ai-support` | Guest/Member | Basic public-scope AI support, 2,000-character limit, advisory label, loading, validation, provider error and retry. |
| `F04-DESIGN-WORKSPACE` | `/design-requests/new` | Active Member | Description up to 4,000 characters and optional catalogue reference/image. No package, location, session, booking, payment or ticket fields. |
| `F04-CONSENT` | Part of new-request flow | Active Member with image | Display consent purpose/version and require affirmative confirmation before submit. |
| `F04-IMAGE-UPLOAD` | Part of new-request flow | Active Member with image | Validate JPEG/PNG/WEBP, 10 MB, 4096×4096 and display typed validation/rate-limit errors. |
| `F04-ANALYSIS-STATUS` | `/design-requests/:designRequestId` | Request owner | Display actual `PROCESSING`, `SUCCEEDED` or `FAILED` state returned by the backend. Do not assume asynchronous polling is available. |
| `F04-RESULT` | `/design-requests/:designRequestId` | Request owner | Display safe candidate features, image quality, confidence, explanation, review state and next action. Never display raw provider payload/image. |
| `F04-REVIEW-QUEUE` | `/design-review-requests` | Staff/Manager | Load `NEED_REVIEW` requests. Do not add branch/session filtering until backend scope data exists. |
| `F04-REVIEW-DETAIL` | `/design-review-requests/:reviewRequestId` | Staff/Manager | Display safe result, version, estimated value and reason form; submit human decision and recover from 403/409. |
| `F04-PRICE-PROPOSAL` | `/price-proposals/:proposalId/ai-suggestions` | Manager/Admin | Manager sees advisory business data and decides; Admin sees technical/audit projection only. No catalogue publication action is owned by Feature-004. |

Guest access is limited to `F04-AI-CHAT`. Design Request creation currently requires an active Member because that is what `DesignAnalysisService` enforces.

### 6.3. Backend-current API mapping

The feature API client MUST call the backend controller shape currently present in `ppswbs_backend`:

```text
POST /api/v1/ai-support/messages
POST /api/v1/design-requests
GET  /api/v1/design-requests/{id}
GET  /api/v1/design-review-requests
POST /api/v1/design-review-requests/{id}/decisions
POST /api/v1/price-proposals/{id}/ai-suggestions
GET  /api/v1/price-proposals/{id}/ai-suggestions
POST /api/v1/price-proposals/{id}/ai-suggestions/decisions
```

Current payload rules:

- Basic AI response fields are `message`, `scope` and `providerCorrelationId`.
- Design Request is multipart with `description`, `catalogueReference`, `consentVersion`, `consentPurpose`, `designVersion`, optional `image` and required `Idempotency-Key`.
- Design Request POST returns the current `DesignRequestViewDto` with HTTP `202`.
- Review decision uses `designVersion`, not OpenAPI's `requestVersion`.
- Price suggestion currently receives `targetType` as a request parameter and `bodyContext` as a raw request body.
- Current price suggestion controller returns HTTP `200`, not the `202` described in OpenAPI.
- The frontend MUST NOT silently transform these fields to the OpenAPI shape. Contract synchronization is a separate backend/spec task.

Known OpenAPI/backend mismatches to retain as blockers:

| Topic | OpenAPI | Current backend | Frontend rule |
| --- | --- | --- | --- |
| Basic AI answer | `answer` | `message` | Read `message` |
| Review version | `requestVersion` | `designVersion` | Send `designVersion` |
| Price context | JSON object | raw `bodyContext` plus `targetType` parameter | Follow controller until contract is synchronized |
| Price response status | `202` | `200` | Accept current `200` |

### 6.4. Frontend and backend data flow

```text
React Router route
  → Feature-004 page component
  → feature API hook/client
  → shared/api/http.js
  → Firebase getIdToken()
  → Authorization: Bearer <Firebase ID token>
  → designreview web controller
  → designreview application service
  → domain port/repository/adapter
  → safe JSON response
  → page-local state
  → loading/success/error/recovery UI
```

Write flow:

```text
User action
  → useCommand.run()
  → crypto.randomUUID() creates Idempotency-Key
  → API request
  → pending/disabled action state
  → safe response or typed ApiError
  → success state or translated error state
  → retry reuses the same key
```

Read flow:

```text
Page mount or route parameter change
  → useResource()
  → GET request
  → latest-request-wins protection
  → loading / empty / error / data state
```

Authorization flow:

```text
Route opens
  → AuthProvider observes Firebase session
  → UI applies only fail-safe navigation hints
  → backend verifies role/status/ownership
  → 401: shared client refreshes token once, then signs out if still rejected
  → 403: show access denied without signing out
```

Backend data flow by use case:

```text
Basic AI:
AiSupportController
  → AiSupportService
  → AiTextSupportPort / GeminiRestClientAdapter
  → AiTextInteractionRepository
  → AiSupportResponse

Design Request with image:
DesignRequestController
  → DesignAnalysisService
  → AccountFacade.resolveAccount
  → DesignRequestRepository
  → ImageConsentRepository
  → ImageRateLimiter
  → ReferenceImageStoragePort / LocalStorageAdapter
  → ImageFeatureAnalysisPort / GeminiRestClientAdapter
  → TemporaryImageReferenceRepository
  → DesignAnalysisResultRepository
  → DesignRequestViewDto

Human review:
DesignReviewController
  → HumanReviewService
  → DesignRequestRepository
  → HumanReviewDecisionRepository
  → DesignRequestViewDto

Price suggestion:
PriceProposalController
  → PriceSuggestionService
  → AiPriceSuggestionPort / GeminiRestClientAdapter
  → PriceSuggestionRepository
  → Manager or Admin-safe PriceSuggestionViewDto
```

### 6.5. Implementation tasks

#### Phase 1 — Frontend foundation

- Create `src/features/designreview/` with route, API, message and style modules.
- Register Feature-004 routes in `src/app/router.jsx`.
- Reuse `shared/api/http.js`, `useResource`, `useCommand`, `AuthProvider`, `RequireSession`, `ErrorNotice`, `Loading`, `Field`, `ConfirmDialog` and bilingual message conventions.
- Do not import internal components from Feature-002 or Feature-006.

#### Phase 2 — Customer flow

- Implement Basic AI Support.
- Implement Member Design Request form.
- Implement consent and image upload validation.
- Implement request submission with multipart data and idempotency.
- Implement status/result view using the POST/GET response shape.
- Implement owner-safe access, rate-limit errors and retry while preserving form data.

#### Phase 3 — Staff/Manager/Admin operations

- Implement review queue and review detail.
- Implement reason validation and decision submission.
- Implement stale-version, duplicate-decision and forbidden recovery.
- Implement Manager price suggestion request/view/decision.
- Implement Admin technical/audit projection without commercial pricing fields.

#### Phase 4 — Frontend verification

- API client tests: Bearer token, multipart payload, Idempotency-Key, 401 refresh, 403, 409, 429 `Retry-After`, 503 retry and current backend field names.
- Screen tests: loading, empty, validation, success, forbidden, rate limited, provider failure, stale version, conflict and retry preserving input.
- Permission tests: Guest cannot create Design Request; Member sees only own result; Staff/Manager review rules; Admin technical projection; Staff/Member price denial.
- Run `npm test` and `npm run build` from `ppswbs_frontend`.

### 6.6. Frontend blockers and unresolved gaps

1. Feature-004 has no approved frontend design pack (`architecture.md`, `workflows.md`, `ui-flow.md`).
2. OpenAPI does not match current backend DTOs/statuses.
3. No account bootstrap contract currently supplies role/status to the frontend; UI must remain fail-safe and rely on backend 401/403.
4. Backend retains compatibility with `ROLE_OWNER` although the current business baseline uses Manager.
5. Review queue has no real branch/delegation scope data.
6. Revision and withdrawal endpoints/state transitions are not implemented.
7. Image analysis executes synchronously inside the Design Request POST; no frontend polling contract is available.
8. `GeminiRestClientAdapter` currently returns deterministic/mock-like results rather than calling the live provider.
9. `LocalStorageAdapter` is in-memory and unsuitable for multi-instance production deployment.
10. Maven wrapper execution failed in the current environment before Maven started; no fresh backend integration evidence is available for this frontend handoff.
11. Repository Flyway migrations still contain duplicate V6, V7 and V9 filenames.
12. `consentPurpose` is accepted by the controller but is not independently validated by `DesignAnalysisService`; the frontend must send `INDEPENDENT_DESIGN_IMAGE_ANALYSIS` and the backend contract should be hardened separately.

