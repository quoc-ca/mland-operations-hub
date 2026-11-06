# Feature-011 Workflows

**Date created**: 2026-11-03
**Date updated**: 2026-11-05
**Author**: Ngô Tiến Đạt
**Last updated by**: Ngô Tiến Đạt

## W1 — Guest eligibility and creation

1. Guest submits normalized email/phone to `POST /review-eligibility/lookup`.
2. The API adapter sends the lookup command without Firebase identity.
3. The backend returns only eligible sources and an opaque handle.
4. Guest selects one source and submits rating/text plus the handle to `POST /reviews` with `Idempotency-Key`.
5. The backend resolves the handle, revalidates source/target ownership and creates `PUBLIC` review data atomically.
6. Duplicate source or changed idempotent payload is shown as a safe conflict; no second review is created.

## W2 — Member eligibility and creation

1. Member opens `GET /reviews/eligible-sources` with Firebase identity.
2. Backend derives Member identity and ignores client-supplied owner identity.
3. Member selects an own source and submits rating/text with `Idempotency-Key`.
4. The API adapter maps the safe response to the published review screen.

## W3 — Owner edit

1. Owner opens an own review and edits rating/text.
2. Application command sends `PATCH /reviews/{reviewId}` with `Idempotency-Key` and `If-Match-Version`.
3. Success keeps status `PUBLIC` and refreshes the projection.
4. `409 REVIEW_STATE_CONFLICT` keeps local input and requires reload/reconciliation.

## W4 — Public listing

1. Public route calls `GET /reviews` with target/rating/cursor filters.
2. The backend filters `PUBLIC` before pagination and aggregate calculation.
3. UI displays masked name, rating, text, approved current reply and aggregate only.

## W5 — Staff moderation

1. Staff opens `GET /review-moderation`.
2. Staff selects a review and submits `HIDDEN` with reason, version and idempotency key.
3. Staff remove/reply controls are not rendered as available business actions.
4. On success, queue/detail reloads; on `403` the page remains signed in but denied.

## W6 — Manager moderation and reply

1. Manager opens queue/detail and submits `HIDDEN` or `REMOVED` with reason/version.
2. Manager creates one reply with `POST /reviews/{id}/reply` or edits it with `PATCH`.
3. Commands use idempotency and optimistic version headers according to OpenAPI.
4. `409` requires latest-data reload; no new-key automatic retry is allowed.

## W7 — Frontend data-flow boundary

```text
Route
  → page
  → application resource/command hook
  → reviewApi adapter
  → shared HTTP client
  → Firebase token when required
  → Feature-011 controller/service
  → safe DTO projection
  → local feature state
  → UI state
```

The UI never calls source repositories, creates eligibility evidence locally,
calculates authorization, or exposes contact/transaction data. A retry of a write
reuses the original `Idempotency-Key`.

## W8 — Backend guest handle protection

1. Normalize contact without retaining raw value in logs.
2. Build a server-side source-set binding and issue an opaque handle.
3. Resolve expiry, status, source binding and ownership again during create.
4. Reject malformed, expired, replayed or mismatched handles without revealing source existence.

## W9 — Backend idempotent create

1. Canonicalize command fields and compute the request digest.
2. Atomically claim the actor/handle scope and idempotency key.
3. Replay an identical completed command; reject changed payload.
4. Insert review, first revision and audit transactionally.
5. Let the unique source constraint resolve concurrent creates.

## Failure and recovery

- Invalid input: no review row and no mutation.
- Source unavailable: typed dependency error; frontend preserves input and offers explicit retry.
- Duplicate/concurrent create: one committed review and safe conflict/result.
- Stale edit/moderation/reply: `REVIEW_STATE_CONFLICT`; reload without partial audit.
- `401`: shared token-refresh path; `403`: access denied without sign-out.
- Backend/OpenAPI mismatch: integration remains blocked; frontend does not invent a compatibility shape.
