# Feature-004 Data Model — Design Request and Human Review

Date created: 2026-11-20
Date updated: 2026-11-20
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

## Ownership boundary

Feature-004 owns independent design requests, image-quality evidence, Guest proof,
daily quota, human decisions and audit. It does not own booking, payment, configured
design, catalogue or AI data.

## Logical aggregates

### `design_requests`

- Opaque public `request_reference`.
- `ownership_type`: `GUEST` or `MEMBER`.
- Member owner reference or Guest session binding.
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

### `guest_design_sessions`

- Opaque digest/reference, request binding, expiry and revocation state.
- No raw email, phone, token or database identifier in client-visible data.
- One request binding prevents cross-request token reuse.

### `guest_request_quota`

- Opaque Guest identity/session digest and calendar date in system timezone.
- Atomic count with maximum five create attempts per day.
- Same idempotency command cannot increment twice.

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
7. Guest quota is five requests per calendar day and is atomic.
8. Idempotent retries cannot create duplicates or consume quota twice.
9. Guest tokens are opaque, scoped, expirable and never raw-logged.
10. Review history and audit are append-only and not hard-deleted.
11. No entity or foreign key references Workshop Booking, Payment or Feature-012 internals.

## Custom-order intake additions

The request aggregate is also the intake boundary for a Member custom order. The
logical model adds `custom_design_request_revisions`, `custom_design_decisions`,
`custom_design_reference_snapshots` and `custom_order_quote_handoffs`.

- A reference snapshot is immutable and created from a Feature-012 `DRAFT` or
  `VALIDATED` version through a public facade.
- `CLARIFICATION_REQUIRED` creates a new revision and returns the request to
  `NEED_REVIEW`; the previous decision remains audit history.
- `ACCEPTED` creates a custom-order quote handoff but no cart line. The order is
  payable only as one complete quoted amount.
- `quote_expires_at` is Manager-policy controlled. The payment context and any
  temporary queue hold expire together at the configured deadline.
- A custom order retains the immutable quoted total, currency, quote generation,
  payment deadline, payment status and expiry/cancellation reason.
- There is no deposit amount, remaining balance, final-balance proposal or
  partial-payment state in the custom-order model.
- Feature-004 does not own production, fulfilment rows or payment settlement;
  Custom Manufacturing owns the full-payment handoff through the payment
  facade.
