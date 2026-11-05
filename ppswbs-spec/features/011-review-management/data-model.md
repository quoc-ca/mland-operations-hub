# Feature-011 Data Model

Date created: 2026-11-03  
Date updated: 2026-11-03  
Author: Ngô Tiến Đạt  
Last updated by: Ngô Tiến Đạt

## Ownership

Feature-011 owns review content, revisions, replies, moderation events and the review-source reference used for uniqueness. Workshop attendance and retail order fulfilment remain owned by their modules. Feature-011 verifies them through source facades; it must not query their repositories directly.

## Logical entities

### `review`

`id`, `source_type`, `source_reference`, `target_type`, `target_reference`, `owner_type`, `owner_reference`, `masked_display_name`, `rating`, `body_text`, `status`, `created_at`, `updated_at`, `version`.

Allowed values: `source_type = WORKSHOP_ATTENDANCE | RETAIL_ORDER_LINE`; `target_type = WORKSHOP_SESSION | RETAIL_PRODUCT`; `status = PUBLIC | HIDDEN | REMOVED`. `rating` is an integer from 1 through 5. `body_text` is nullable and at most 2,000 characters.

Constraints:

- Unique `(source_type, source_reference)`.
- Source verification must prove the owner/source relationship before insert.
- Source target mapping is immutable after creation.
- `REMOVED` is never physically deleted by normal feature operations.

### `review_revision`

Append-only snapshot of owner edits and moderation-visible content changes: `id`, `review_id`, `revision_number`, `rating`, `body_text`, `changed_by_type`, `changed_by_reference`, `changed_at`, `reason`, `correlation_id`. Unique `(review_id, revision_number)`.

### `review_reply`

One current reply per review: `id`, `review_id` unique, `body_text`, `created_by_manager_id`, `updated_at`, `version`. Reply edits append audit evidence; they do not create reply threads.

### `review_moderation_event`

Append-only business audit: `id`, `review_id`, `actor_id`, `actor_role`, `action`, `previous_status`, `new_status`, `reason`, `created_at`, `correlation_id`, `idempotency_key`.

### `review_eligibility_source`

A response/projection boundary, not necessarily a persisted table: `source_type`, opaque `source_reference`, `target_type`, opaque `target_reference`, eligibility state, owner match result, fulfilment/attendance evidence and source timestamp. It must not expose contact data in public DTOs.

### Aggregate

V1 uses a realtime query over `review` filtered by immutable target and `status = PUBLIC`. No aggregate projection table is required for this implementation slice.

## Cross-feature and migration rules

Do not create direct foreign keys into Workshop or Retail tables until their physical ownership contracts are approved. Opaque references must be validated through the corresponding facade. A migration is only a proposal at this stage: first scan all Flyway versions and target `flyway_schema_history`, choose an unused append-only version, test clean and upgrade paths, and define recovery before implementation.

No cascade delete may remove review, revision, reply or moderation history. Sensitive Guest lookup inputs are not stored as raw public fields; if a forensic hash is needed, its purpose and protection must be approved separately.

## Backend implementation additions

### `guest_eligibility_handle` (Ratified 2026-11-04)

The handle is an opaque, non-reversible value issued after a successful Guest
lookup. Logical fields are `handle_id`, protected handle digest, source-set
binding, normalized-contact proof reference, issued time, expiry time, status,
consumed/replay metadata and correlation ID. Raw email, phone and source IDs
must not be encoded into the client-visible value or written to application
logs. The handle is valid only for its bound source set and is resolved again
at create time. Per user ratification on 2026-11-04: TTL is 30 minutes, stored in MySQL `guest_eligibility_handles`, and valid for all sources in that lookup session.
Replay with the same Idempotency-Key returns the existing review idempotently.


### `review_idempotency_record`

Feature-owned idempotency boundary for create, owner edit, visibility and reply
commands: actor/guest-proof scope, idempotency key, canonical request digest,
command type, lifecycle, response reference, created/expiry timestamps and
correlation ID. A key reused with a different canonical request is a conflict;
an identical completed request replays the original result without another
mutation.

## Realtime aggregate query

The V1 aggregate is calculated from `reviews` using the immutable target and
`status = PUBLIC`. It must run in the same authorization/public projection
boundary as public listing, and must not use `review_revisions` or hidden rows
as an alternate source of truth.

## Migration gate

The merged repository contains `develop` migrations through `V90`, while the
Feature-011 migration is allocated as `V91`. Before applying it, re-scan the
repository and `flyway_schema_history`, verify that `V91` is unused, and test
clean install, upgrade, rerun and recovery behavior. Do not use
`baseline`, `outOfOrder`, ignore patterns or checksum repair to conceal a
duplicate version.
