# Feature-011 Workflows

Date created: 2026-11-03  
Date updated: 2026-11-03  
Author: Ngô Tiến Đạt  
Last updated by: Ngô Tiến Đạt

## W1 — Guest eligibility and creation

1. Guest submits normalized email/phone to `POST /review-eligibility/lookup`.
2. Controller validates format and calls `ReviewEligibilityService.lookupGuest`.
3. The service calls source ports and returns only checked-in or fulfilled matching sources plus an opaque handle bound to that source set.
4. Guest selects one source and submits rating/text plus the handle to `POST /reviews` with `Idempotency-Key`; a raw source ID without a valid handle is rejected.
5. Service resolves the handle, rechecks source eligibility and identity match, rejects target mismatch, then inserts review, first revision and audit atomically.
6. On duplicate source, return the existing idempotent result or `REVIEW_ALREADY_EXISTS`; do not create a second row.

## W2 — Member eligibility and creation

1. Authenticated Member opens `GET /reviews/eligible-sources`.
2. Backend derives Member identity from Firebase/session claims and ignores client-supplied owner identity.
3. Member selects an own eligible source and submits the same create command.
4. Source validation and transaction behavior are identical to W1.

## W3 — Owner edit

1. Owner opens an own review and submits rating/text with `If-Match-Version` and idempotency key.
2. Service checks owner, immutable source/target, input limits and current version.
3. On success, update current projection and append revision/audit in one transaction; status remains `PUBLIC`.
4. On stale version, return `REVIEW_STATE_CONFLICT` and leave persisted content unchanged.

## W4 — Public listing

1. Public client calls `GET /reviews` with target/rating/cursor filters.
2. Public read facade applies `status = PUBLIC` before pagination and aggregate calculation/projection.
3. Response contains masked display name and optional Manager reply only; no contact or source transaction data.

## W5 — Staff moderation

1. Staff opens `GET /review-moderation`.
2. Backend verifies Staff status and the Feature-011 global-hide permission.
3. Staff submits `HIDDEN` with reason and version.
4. Service rejects remove/reply commands from Staff, then atomically changes visibility and writes moderation audit.

## W6 — Manager moderation and reply

1. Manager opens queue/detail and submits `HIDDEN` or `REMOVED` with reason.
2. Service verifies Manager authority, conflict-of-interest rule and current version.
3. Manager creates one reply through `POST /reviews/{id}/reply` or edits it through `PATCH`.
4. Reply operation and audit are atomic; a second current reply is rejected.

## Failure and recovery

- Invalid input: no source call requiring mutation and no review row.
- Source unavailable: return a dependency error; do not guess eligibility.
- Duplicate/concurrent create: database uniqueness and idempotency yield one committed review.
- Stale edit/moderation/reply: return conflict and require reload; no partial audit.
- Aggregate projection failure, if projections are selected: transaction must either fail safely or enqueue a repairable rebuild; never count hidden/removed rows as public.

## Backend command contract details

### W7 — Guest handle protection

1. Normalize email/phone without retaining the raw value in logs.
2. Call both eligibility ports and build a server-side source-set binding.
3. Issue an opaque handle whose visible value cannot be decoded into contact or
   source data.
4. On create, resolve the handle, verify expiry/status/source binding and call
   the relevant source port again before persistence.
5. Reject expired, consumed, mismatched or malformed handles without revealing
   whether a different source exists.

### W8 — Idempotent create

1. Canonicalize command fields and compute a request digest.
2. Atomically claim `(actor-or-handle-scope, idempotency-key, command-type)`.
3. Replay an identical completed command; reject a changed payload.
4. Insert review, first revision and audit under the same transaction as the
   idempotency outcome.
5. Let the unique source constraint resolve concurrent commands that pass the
   initial read at the same time.
