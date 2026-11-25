# Feature-004 Data Model — Design Request and Human Review

Date created: 2026-11-20
Date updated: 2026-11-21
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

## Ownership boundary

Feature-004 owns independent design requests, image-quality evidence, human decisions
and audit. It does not own Guest proof, daily quota, booking, payment, configured
design, catalogue or AI data.

## Logical aggregates

### `design_requests`

- Opaque public `request_reference`.
- `ownership_type`: `MEMBER`.
- Member owner reference. A protected Guest attempt never creates a request row.
- Optional description, maximum configured length.
- `status`: `NEED_REVIEW`, `ACCEPTED`, `REJECTED`.
- Monotonic request version and timestamps.
- No booking, location, package, payment, ticket or AI fields.

### `design_request_images`

- Parent request and opaque image reference.
- Maximum five rows per request.
- Validated MIME/signature, size, dimensions and checksum.
- `quality_status`: `PROCESSING`, `PASS`, `TOO_BLURRY`, `QUALITY_CHECK_FAILED`.
- Safe quality reason/score projection and deletion outcome audit.

### `design_review_decisions`

- Request/version reference.
- Reviewer account reference and role `STAFF` or `MANAGER`.
- `decision`: `ACCEPTED` or `REJECTED`.
- Mandatory bounded explanation, timestamp and optimistic version.

### `design_request_idempotency_records`

- Scoped command key, actor/proof scope, request fingerprint, response reference,
  status and expiry.
- Changed payload with the same key is a conflict.

### `design_request_audit_events`

Append-only events for submission, image quality result, decision, token use, quota
rejection, conflict and deletion. Raw images, raw tokens and unnecessary PII are excluded.

## Invariants

1. A request contains at least a description or one image and no more than five images.
2. `TOO_BLURRY` or unavailable quality status cannot be submitted.
3. New requests start at `NEED_REVIEW`.
4. One request version has at most one terminal decision.
5. A decision always has an explanation.
6. Staff and Manager have equal decision authority; Admin has none.
7. Every persisted request has an active Member owner.
8. Idempotent retries cannot create duplicates.
9. Review history and audit are append-only and not hard-deleted.
11. No entity or foreign key references Workshop Booking, Payment or Feature-012 internals.

## Custom-order intake additions (V190 Schema Baseline)

The request aggregate is also the intake boundary for a Member custom order. The
physical model defines `custom_design_reference_snapshots`, `custom_design_request_revisions`,
`custom_orders`, `custom_order_quotes`, `custom_order_payment_context`, and
`custom_order_idempotency_records`:

### `custom_design_reference_snapshots`
- `snapshot_reference`, `design_reference`, `design_version`.
- `design_snapshot_json`: Immutable JSON serialization captured from Feature-012 facade.
- Created when a request references a persisted Feature-012 ring design.

### `custom_design_request_revisions`
- `revision_reference`, `request_id`, `revision_number`.
- `description`, `design_reference`, `design_version`, `created_at`.
- Created when Member submits a revision in response to `CLARIFICATION_REQUIRED`.

### `custom_orders`
- `custom_order_reference`, `request_reference`, `member_account_id`.
- `quote_reference`, `quote_generation`.
- `status`: `QUOTE_PENDING`, `FULL_PAYMENT_PENDING`, `PAID`, `IN_PRODUCTION`, `FULFILLED`, `CLOSED`, `EXPIRED`, `CANCELLED`.
- `production_status`, `fulfilment_status`, `payment_deadline_at`, `closed_at`.

### `custom_order_quotes`
- `quote_reference`, `custom_order_reference`, `quote_generation`.
- `quoted_amount`, `currency` (VND), `status`: `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `EXPIRED`.
- `quote_expires_at`, `approved_by_account_id`, `approved_at`, `rejection_reason`.

### `custom_order_payment_context`
- `payment_reference`, `custom_order_reference`, `quote_reference`, `amount`.
- `payment_owner_kind`: `CUSTOM_ORDER_FULL_PAYMENT`.
- `status`: `INITIATED`, `VERIFIED`, `FAILED`, `EXPIRED`.

- A reference snapshot is immutable and created from a Feature-012 `DRAFT` or
  `VALIDATED` version through a public facade.
- `CLARIFICATION_REQUIRED` creates a new revision and returns the request to
  `NEED_REVIEW`; the previous decision remains audit history.
- `ACCEPTED` creates a custom-order quote handoff but no cart line. The order is
  payable only as one complete quoted amount (100% full payment).
- `quote_expires_at` is Manager-policy controlled. The payment context and any
  temporary queue hold expire together at the configured deadline.
- A custom order retains the immutable quoted total, currency, quote generation,
  payment deadline, payment status and expiry/cancellation reason.
- There is no deposit amount, remaining balance, final-balance proposal or
  partial-payment state in the custom-order model.
- Feature-004 does not own production, fulfilment rows or payment settlement;
  Custom Manufacturing owns the full-payment handoff through the payment
  facade.
