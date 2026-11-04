# Feature-011 Implementation Plan

Date created: 2026-11-03  
Date updated: 2026-11-03  
Author: Ngô Tiến Đạt  
Last updated by: Ngô Tiến Đạt

## Boundary and dependencies

Feature module `reviewmanagement` owns the review use cases and its persistence. Proposed ports (not yet production code) are `ReviewEligibilityPort`, `WorkshopAttendanceReviewPort`, `RetailOrderReviewPort`, `ReviewPublicReadFacade` and `ReviewModerationFacade`. Workshop and Retail modules expose verification facades; Review Management does not access their repositories directly.

## Delivery order

1. Verify AGENT, general spec, Report 3 and current APIs/DB baseline.
2. Approve this specification, source-facade contracts and unresolved physical mappings.
3. Define schema and an unused append-only migration version after repository/database history checks.
4. Implement source verification adapters and review application services.
5. Implement create/edit/list/aggregate/moderation/reply API endpoints from the approved contract.
6. Implement public and operational screens after API synchronization.
7. Add unit, integration, authorization, concurrency, idempotency, migration and privacy tests.
8. Run cross-artifact and architecture checks before code review.

## Transaction and concurrency design

Create validates input and source eligibility, then inserts review, first revision and audit atomically. The unique source key is the final concurrency guard. The idempotency key stores/replays the command outcome and rejects a changed payload under the same key. Owner edit, visibility change and reply edit use optimistic version checks; stale writes return `REVIEW_STATE_CONFLICT`. Aggregate projection updates must be committed with the state change or rebuilt from `PUBLIC` reviews.

## Resolved decisions (Ratified 2026-11-04)

- Source eligibility: Implemented via WorkshopAttendanceReviewPort (checking CHECKED_IN attendance tickets) and RetailFulfilmentReviewPort (checking PICKED_UP/DELIVERED retail order lines) with internal adapters.
- Aggregate strategy: Realtime SQL query over reviews where status = 'PUBLIC'.
- Staff global-hide permission: Confirmed Staff may hide any review globally.
- Migration version: V89__review_management.sql.
- Guest handle: Persisted in guest_eligibility_handles table, 30-minute TTL, valid for sources in that lookup session. Direct source IDs without valid handle are rejected.
- Guest no-OTP risk acknowledged for V1 per design decision.

## Backend delivery slice

### Proposed backend package boundary

```text
features/reviewmanagement/
  controller/
  dto/
  application/
  domain/
  facade/
  eligibility/
  persistence/
  security/
```

The names are logical design labels until the project’s code persona confirms
the physical package convention. The boundary must expose only the review
public-read and eligibility contracts required by other modules.

### Required ports and services

- `WorkshopAttendanceReviewPort`: verify checked-in attendance and map the
  workshop-session target.
- `RetailFulfilmentReviewPort`: verify picked-up/delivered order-line
  fulfilment and map the retail-product target.
- `GuestEligibilityHandlePort`: issue, resolve, expire and protect opaque Guest
  handles without exposing raw contact/source data.
- `ReviewApplicationService`: create and edit reviews with validation,
  ownership, idempotency and transaction boundaries.
- `ReviewModerationService`: Staff/Manager visibility actions and audit.
- `ReviewReplyService`: one current Manager reply with audited edits.
- `ReviewPublicReadFacade`: public listing, detail and realtime PUBLIC aggregate.

### Required implementation order

1. Ratify Guest handle TTL/replay/storage against the security baseline.
2. Ratify Workshop Operations and Fulfilment facade DTOs and ownership evidence.
3. Define physical schema and idempotency tables after the migration gate.
4. Implement source adapters and application services.
5. Implement controllers and error mapping from the OpenAPI contract.
6. Implement authorization, audit and optimistic version checks.
7. Add unit/integration/concurrency/privacy/migration tests.
