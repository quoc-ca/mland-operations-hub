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

## Known blockers

- No approved unified source facade yet.
- Physical attendance/order-line mappings are unknown.
- Realtime versus projection aggregate strategy is TBD.
- Staff global-hide permission must be represented explicitly, not inferred from branch scope.
- Migration version and upgrade/recovery plan are not selected.
- Guest no-OTP/no-rate-limit choice is a known trust risk and must be visible in security review.
- Guest lookup-to-create proof/handle field, lifetime and replay policy are unresolved; direct client-submitted source IDs must not be trusted.
