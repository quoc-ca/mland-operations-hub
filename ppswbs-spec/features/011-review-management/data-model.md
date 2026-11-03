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

Either a query over `review` filtered by `status = PUBLIC` or a projection owned by this feature. If a projection is selected, updates must be transactional or repairable from the append-only review history. The realtime/projection choice is TBD for implementation planning.

## Cross-feature and migration rules

Do not create direct foreign keys into Workshop or Retail tables until their physical ownership contracts are approved. Opaque references must be validated through the corresponding facade. A migration is only a proposal at this stage: first scan all Flyway versions and target `flyway_schema_history`, choose an unused append-only version, test clean and upgrade paths, and define recovery before implementation.

No cascade delete may remove review, revision, reply or moderation history. Sensitive Guest lookup inputs are not stored as raw public fields; if a forensic hash is needed, its purpose and protection must be approved separately.
