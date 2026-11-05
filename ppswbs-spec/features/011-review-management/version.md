# Version 1.0 — Feature-011: Review Management (Backend Implementation)

- **Feature**: 011 — Review Management
- **Author**: Ngô Tiến Đạt
- **Last updated by**: Ngô Tiến Đạt
- **Date created**: 2026-11-03
- **Date updated**: 2026-11-05
- **Source of truth**: Feature-011 `spec.md`, `data-model.md`, backend implementation, `V91__review_management.sql`, OpenAPI contract and synchronized project specifications

> This file is an implementation status index. Detailed behavior remains in
> the Feature-011 artifacts and source code. Phase labels are retained for
> tracing and audit. “Implemented” means code is present; it does not mean the
> implementation has passed final contract, migration or release verification.

---

## 1. Completed

### Phase A — Backend boundary and module structure

- Added the `features.reviewmanagement` backend module.
- Added controller, DTO, domain, service, eligibility and DAO layers.
- Added `WorkshopAttendanceReviewPort` and `RetailFulfilmentReviewPort`.
- Added internal adapters for Workshop attendance and Retail fulfilment checks.
- Review Management owns review data and does not directly use Workshop/Retail repositories through the feature service boundary.
- Added security integration through `IdentityGateFilter` and `SecurityConfiguration`.

### Phase B — Persistence and migration

- Added [`V91__review_management.sql`](../../../ppswbs_backend/src/main/resources/db/migration/V91__review_management.sql) after merge resolution. `develop` introduced separate `V89` and `V90` migrations, so retaining Feature-011 as `V89` would leave duplicate Flyway versions.
- Added logical persistence for `reviews`, `review_revisions`, `review_replies`, `review_moderation_events`, `guest_eligibility_handles` and `review_idempotency_records`.
- Added unique source protection through `(source_type, source_reference)`.
- Added rating constraint `1..5`, service-level source/target validation and `PUBLIC/HIDDEN/REMOVED` status model.
- Added realtime aggregate query support through `ReviewAggregateProjection`.

### Phase C — Eligibility and Guest handle

- Workshop eligibility verifies `CHECKED_IN` attendance through `InternalWorkshopAttendanceAdapter`.
- Retail eligibility verifies `PICKED_UP`/`DELIVERED` fulfilment through `InternalRetailFulfilmentAdapter`.
- Member eligible-source lookup uses authenticated account ownership.
- Guest lookup accepts email/phone and persists a SHA-256-backed opaque handle.
- Guest handle TTL is currently 30 minutes.
- Handle records retain the eligible source-reference set and masked contact value.
- Handle validation checks existence, expiry and source binding before Guest creation/edit.

### Phase D — Review application, moderation and reply

- `ReviewApplicationService` implements Member/Guest creation, duplicate-source prevention, revision creation and owner edit.
- Valid review creation starts at `PUBLIC`.
- Owner edits use optimistic `expectedVersion` validation and append a revision.
- `ReviewModerationService` implements visibility actions and audit event persistence.
- Staff is restricted to `HIDE` in the current service implementation.
- Manager supports `HIDE`, `RESTORE` and `REMOVE` in the current service implementation.
- `ReviewReplyService` implements one current reply and audited reply edits.
- `ReviewPublicReadServiceImpl` returns public reviews and realtime count/average data.
- `ReviewMaskingService` masks Member display names and Guest contact values.
- `ReviewIdempotencyService` implements SHA-256 request hashing and replay records for the create-review path.

### Phase E — REST API and exception handling

Implemented endpoint operations:

```text
POST  /api/v1/review-eligibility/lookup
GET   /api/v1/reviews/eligible-sources
POST  /api/v1/reviews
GET   /api/v1/reviews
GET   /api/v1/reviews/{reviewId}
PATCH /api/v1/reviews/{reviewId}
PATCH /api/v1/reviews/{reviewId}/visibility
GET   /api/v1/review-moderation
POST  /api/v1/reviews/{reviewId}/reply
PATCH /api/v1/reviews/{reviewId}/reply
```

- Added structured `ReviewManagementExceptionHandler` responses.
- Added domain error codes for invalid input, ineligible source, duplicate source, authorization, target mismatch, Guest handle failure and state conflict.
- Public listing filters by target and returns aggregate data.
- Moderation queue supports status/target filters and pagination.

### Phase F — Test artifacts present

The repository contains the following Feature-011 test suites:

- `ReviewApplicationServiceTest` — 6 test cases.
- `ReviewEligibilityServiceTest` — 3 test cases.
- `ReviewModerationServiceTest` — 3 test cases.
- `ReviewPublicReadServiceTest` — 2 test cases.
- `ReviewReplyServiceTest` — 3 test cases.
- `ReviewControllerMockMvcTest` — 4 test cases.

Reported total: **21 test cases**. A fresh execution was not claimed in this update because the current environment has Java 17 while the backend requires Java 21.

---

## 2. Incomplete / Next steps

### Giai đoạn 1 — Backend contract alignment

- Align `bodyText` validation with the approved 2,000-character rule. Current DTOs accept up to 4,000 characters in create/edit/reply paths.
- Align idempotency transport. Current implementation reads `idempotencyKey` from request bodies; the approved API contract expects `Idempotency-Key` request-header behavior.
- Align idempotency and optimistic-version enforcement consistently for moderation and reply commands; current DTOs carry fields, but services do not use the complete shared idempotency lifecycle.
- Decide and enforce the final Guest handle replay/consumption policy. The migration has `consumed_at`, but current validation does not consistently consume or replay-bind the handle.
- Align the OpenAPI contract with current DTO names, status codes and body/header shapes.

### Giai đoạn 2 — Eligibility and authorization gaps

- Current Guest creation explicitly allows only `WORKSHOP_ATTENDANCE`; Guest retail-product review remains unimplemented although Feature-011 scope includes Guest retail eligibility.
- Verify the exact `DELIVERED` source state and ownership evidence returned by the Fulfilment adapter.
- Remove or explicitly approve Admin business authority. Current controllers permit Admin to access moderation queue and create/edit Manager replies, while the approved baseline grants Admin technical/audit access only.
- Add server-side ownership checks to `GET /api/v1/reviews/{reviewId}`. Current service maps a review detail without an explicit public/owner/authorized-scope decision at this method boundary.
- Add conflict-of-interest/self-action checks for Staff and Manager moderation/reply operations if required by the approved invariant.

### Giai đoạn 3 — Persistence and migration safety

- Review `ON DELETE CASCADE` on `review_revisions`, `review_replies` and `review_moderation_events`. The approved business-history policy requires audit/revision/reply history to survive normal review removal; migration behavior must be changed or explicitly approved before production.
- Run Flyway validation against a clean MySQL database and an existing database containing migrations through `V88`.
- Verify `V91` upgrade, rerun behavior, schema constraints, indexes and `flyway_schema_history`.
- Add a recovery plan for partial migration failure; do not use checksum repair, `baseline` or `outOfOrder` to conceal version conflicts.

### Giai đoạn 4 — Verification and release readiness

- Install/use JDK 21 and reproduce the 21 Feature-011 tests.
- Add tests for Guest retail review, Guest handle expiry/replay, raw source-ID rejection, concurrent idempotent commands and Admin denial.
- Add tests for 2,001-character text rejection and header-based idempotency.
- Add persistence tests proving audit/revision/reply history is retained after `REMOVED`.
- Add API contract validation against the updated OpenAPI document.
- Add architecture/Spring Modulith verification for the new module.
- Add frontend integration after the backend contract is finalized.

### Giai đoạn 5 — Frontend handoff

- Implement Guest eligibility lookup and handle storage with the agreed security policy.
- Implement Member eligible-source list.
- Implement create/edit review forms with 1–5 stars and 2,000-character text limit.
- Implement public review cards, filters and realtime aggregate display.
- Implement Staff moderation and Manager moderation/reply screens.
- Do not expose Guest handle, source ownership evidence, raw contact data or moderation audit details in public UI.

---

## 3. Active blockers and audit notes

1. **Environment blocker:** Current Java runtime is 17.0.10; Maven project requires Java 21. Test execution cannot be treated as fresh evidence until JDK 21 is active.
2. **Contract blocker:** DTOs currently use 4,000-character text limits and body-level idempotency fields, which differ from the approved backend contract.
3. **Business-permission blocker:** Admin is currently accepted by some moderation/reply controller branches despite the approved technical/audit-only Admin boundary.
4. **Eligibility blocker:** Guest retail review is not currently supported by `ReviewApplicationService`.
5. **Privacy/ownership blocker:** Review detail access and public projection boundaries require a final audit to prevent source/owner metadata leakage.
6. **Retention blocker:** Migration foreign keys currently cascade-delete revision, reply and moderation records; this conflicts with the business-history retention rule.
7. **Evidence status:** The 21 test cases are present and previously reported, but no new pass result is claimed from the current Java 17 environment.
8. **Migration status:** Feature-011 was renamed from `V89` to `V91` after merging `develop`, which already contains `V89` and `V90` migrations. Clean-install and existing-database validation remain outstanding.

---

## 4. Traceability index

| Phase | Current evidence | Remaining evidence |
| --- | --- | --- |
| Phase A | `features/reviewmanagement` module, ports, adapters and security integration | Final Modulith dependency verification |
| Phase B | `V91__review_management.sql`, six logical tables and unique source constraint | Retention-safe FK policy and MySQL upgrade evidence |
| Phase C | Guest handle, Member lookup, Workshop/Retail adapters | Guest retail path, handle replay/consumption and final ownership evidence |
| Phase D | Application, moderation, reply, masking and idempotency services | Admin boundary, conflict-of-interest and complete idempotency enforcement |
| Phase E | Controllers and exception handler for ten endpoint operations | OpenAPI/DTO/header alignment and endpoint authorization tests |
| Phase F | 21 Feature-011 test cases exist | Fresh execution under JDK 21 and expanded edge-case coverage |
| Phase G | No Feature-011 frontend implementation recorded here | React/Vite implementation and integration verification |

---

## 6. Merge resolution log — `develop` into `dat`

The merge completed without textual conflict markers, but migration analysis
found semantic Flyway version collisions:

1. Feature-011 `V89__review_management.sql` conflicted with `develop`’s occupied `V89` versions. It was renamed to `V91__review_management.sql`.
2. `develop` contained two separate `V89` scripts: `V89__member_shipping_addresses.sql` and `V89__workshop_package_foundry_working_day_rule.sql`. The account migration remains `V89` because its feature artifacts and verification reference that version; the workshop package migration was renamed to `V92__workshop_package_foundry_working_day_rule.sql` and its header was updated.
3. `V90__workshop_operations_global_idempotency_key.sql` remains unchanged.

The repository must still be validated against `flyway_schema_history` before
any database application. No `baseline`, checksum repair or out-of-order
execution was used to hide the duplicate-version problem.

---

## 5. Current backend flow and data flow

### 5.1 Guest eligibility flow

```text
POST /api/v1/review-eligibility/lookup
  → ReviewEligibilityController
  → ReviewEligibilityService.lookupGuestEligibility
  → normalize contact input
  → InternalWorkshopAttendanceAdapter.findEligibleByContact
  → ReviewRepository excludes already-reviewed source references
  → generate random Guest handle
  → SHA-256 handle hash
  → GuestEligibilityHandleRepository.save
  → return masked contact, eligible sources, handle and expiry
```

Current implementation creates Guest handles from Workshop attendance results.
The Guest retail lookup path is not yet wired into Guest creation.

### 5.2 Member eligibility flow

```text
GET /api/v1/reviews/eligible-sources
  → ActorContext.require current Member actor
  → ReviewEligibilityService.getMemberEligibleSources
  → WorkshopAttendanceReviewPort.findEligibleByMemberId
  → RetailFulfilmentReviewPort.findEligibleByMemberId
  → ReviewRepository checks existing source reviews
  → return eligible source list with masked Member display name
```

### 5.3 Review creation flow

```text
POST /api/v1/reviews
  → ReviewController
  → ActorContext resolves authenticated Member or Guest path
  → ReviewApplicationService.createReview
  → ReviewIdempotencyService lookup/record pending
  → ReviewRepository duplicate-source check
  → Member: verify source through Workshop/Retail port
  → Guest: validate handle + source binding + Workshop attendance port
  → verify target type/reference
  → create Review(status = PUBLIC)
  → create ReviewRevision(revision = 1)
  → save review and revision transactionally
  → record idempotency success
  → return ReviewDto
```

### 5.4 Public read flow

```text
GET /api/v1/reviews?targetType=...&targetReference=...
  → ReviewController
  → ReviewPublicReadFacade
  → ReviewPublicReadServiceImpl
  → query status = PUBLIC
  → calculate totalCount and averageRating in realtime
  → map masked ReviewDto and current reply
  → return PublicReviewPageDto
```

### 5.5 Moderation and reply flow

```text
PATCH /api/v1/reviews/{id}/visibility
  → ReviewModerationController
  → ActorContext current Staff/Manager/Admin
  → ReviewModerationService.changeVisibility
  → role/action validation
  → ReviewRepository.load
  → update status
  → ReviewModerationEventRepository.save
  → return ModerationItemDto

POST/PATCH /api/v1/reviews/{id}/reply
  → ReviewModerationController
  → role validation
  → ReviewReplyService.createReply/editReply
  → ReviewReplyRepository
  → ReviewModerationEventRepository audit
  → return ReplyDto
```

The flows above describe the current code, including known contract gaps. They
must not be treated as proof that the remaining blockers are approved behavior.

## 7. Frontend specification synchronization (2026-11-05)

Feature-011 now has a frontend implementation handoff aligned with the approved
OpenAPI/spec rather than the current backend mismatches.

### 7.1 Frontend target

- React 19 + Vite 7 with real `react-router` routes.
- Feature-owned `routes`, `ui`, `application`, `domain`, `infrastructure` and `tests` layers.
- Shared HTTP, Firebase auth, i18n and UI primitives; no new state library or UI kit.
- Backend authority for eligibility, ownership, permission, status, aggregate and privacy filtering.
- One `Idempotency-Key` per write command; retries reuse the same key.
- `If-Match-Version` for owner edit, moderation and reply edit where defined by OpenAPI.

### 7.2 Frontend delivery phases

1. Foundation: routes, API adapter, shared auth/i18n/UI integration.
2. Customer flow: Guest lookup, Member eligible sources, create/edit review and public listing/detail.
3. Operations flow: Staff hide, Manager hide/remove/reply/edit-reply and Admin technical projection.
4. Verification: API mapping, permission, ownership, privacy, validation, conflict and recovery tests, then `npm test` and `npm run build`.

### 7.3 Current blockers for live frontend integration

- Backend idempotency transport does not yet match the approved header contract.
- Guest retail-product eligibility/create is incomplete in the current implementation.
- Review detail ownership and cross-user enumeration behavior require final audit.
- Admin technical-only authority must be synchronized with backend security.
- DTO names, status codes, headers and migration/API verification remain to be aligned.

The frontend agent must stop at these contract mismatches and must not add a
client-side compatibility layer without a separately approved versioned contract.
