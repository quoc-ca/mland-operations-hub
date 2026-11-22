# Feature Specification: Design Request and Human Review

Date created: 2026-11-20
Date updated: 2026-11-22
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

**Logical feature name:** `004-design-request-and-human-review`
**Legacy directory:** `004-ai-chatbox-analysis-and-suggestion`
**Status:** Implementation baseline; 2026-11-22 terminology, cross-feature reference and consultation changes documented; implementation pending.

## 1. Purpose and boundary

Feature-004 is rebaselined as the custom design request and order-intake boundary.
It lets a Member submit a custom-manufacturing request using text, images and/or
a Feature-012 configured-design snapshot. Guest access remains limited to public
design-request tracking and does not create a custom-manufacturing order.

Feature-004 has no AI provider, AI analysis, Basic AI Support, AI price suggestion,
candidate-feature extraction or automated design decision. Old AI artifacts and
test evidence are historical only and are not acceptance evidence for this baseline.

The feature does not own configured-design validation, workshop booking, ticket,
production, payment settlement or fulfilment. It consumes Feature-012 only through
an approved snapshot facade and hands accepted requests to Custom Manufacturing.

### Superseding change request — 2026-11-22

- The customer-facing tab is named `Yêu cầu chế tác`; the order area is named
  `Đơn hàng cá nhân`.
- The custom request copy explains that the customer describes a ring and the
  shop assesses feasibility and provides a suitable quote.
- Guest and Member may attach an owner-controlled Feature-012 design in any
  readable state, including `DRAFT`, `SAVED`, `ARCHIVED`, and legacy states.
  The server revalidates ownership and creates an immutable snapshot without
  mutating or unlocking the source design.
- AI analysis, AI pricing and automated design decisions remain out of scope.
- Saved-design consultation is launched through Feature-013 with a safe
  `WORKSHOP_RING_DESIGN` context. Guest and Member are supported; Staff and
  Manager may participate in the conversation.
- Quote terminology is `Phiên bản báo giá`; custom orders remain full-payment,
  no-cart and no-deposit flows.

## 2. Actors and permissions

### Route masking synchronization (2026-11-20)

`/design-review-requests*` are internal reviewer routes with browser allowlist
`STAFF` and `MANAGER`; a signed-in `MEMBER`, `ADMIN`, or `CARRIER` is replaced
to `/404` before page render/request. The former price-suggestion and Basic AI
Support routes are historical and are not part of the current Feature-004
contract. Public/Guest/Member design-request routes retain their current rules.

| Actor | Allowed capability |
| --- | --- |
| Guest | Create a request, track it with an opaque token/link, and read its safe status/result. |
| Member | Create requests and read only the Member's own requests. |
| Staff | View the authorized review queue, accept or reject, and write a mandatory explanation. |
| Manager | Same Feature-004 review authority as Staff. |
| Admin | Technical/audit access only; no accept/reject authority. |

Staff and Manager have equal business authority in this feature. The previous
3,000,000 VND threshold is retired because Feature-004 no longer produces an AI
estimate.

## 3. Customer submission flow

```text
Guest/Member opens request form
→ enters optional description
→ uploads 0..5 images
→ validates each image immediately
→ blocks images whose quality is TOO_BLURRY
→ validates all images again before submit
→ sends request with Idempotency-Key
→ backend revalidates proof, quota, files and quality
→ creates NEED_REVIEW request
→ returns safe request projection and Guest tracking link when applicable
```

At least one of description or image is required. Each request has at most five
images. No catalogue, booking or package field is part of the command.

### 3.1 Image quality

The browser displays quality feedback immediately after each upload. The backend
is authoritative and revalidates before persistence. Quality thresholds are supplied
by backend configuration; the frontend must not hard-code a blur threshold.

Allowed results are `PROCESSING`, `PASS`, `TOO_BLURRY` and `QUALITY_CHECK_FAILED`.
`TOO_BLURRY` blocks submission and requires replacement or removal. A quality-service
failure does not silently pass the image.

### 3.2 Guest proof and quota

The server issues an opaque Guest Design Session and, after successful creation, an
opaque tracking token/link. Tokens contain no raw PII or database identifier, have a
configured expiry, are not raw-logged, and are bound to one request.

Guest creation is limited to five requests per calendar day in the system timezone.
The sixth request returns HTTP 429 and does not create a request. Idempotent retry of
the same command returns the original result and is not counted twice.

## 4. Custom request and human review lifecycle

```text
NEED_REVIEW → CLARIFICATION_REQUIRED → NEED_REVIEW
NEED_REVIEW → ACCEPTED
NEED_REVIEW → REJECTED
```

`CLARIFICATION_REQUIRED` is non-terminal and creates a revision loop. Staff or
Manager must provide a non-empty bounded explanation for every clarification,
acceptance or rejection. The decision,
explanation, actor, role, version and timestamp are append-only audit evidence.

Guest and Member can read the resulting status and explanation through their safe
projection. A terminal request cannot receive a second decision; stale or concurrent
commands return a conflict.

## 5. API baseline

```text
POST /api/v1/design-requests (dual-mapped with /api/v1/custom-design-requests)
GET  /api/v1/design-requests/{designRequestId} (dual-mapped with /api/v1/custom-design-requests/{id})
GET  /api/v1/design-requests
POST /api/v1/custom-design-requests/{id} (submit revision after CLARIFICATION_REQUIRED)
GET  /api/v1/design-review-requests
GET  /api/v1/design-review-requests/{reviewRequestId}
POST /api/v1/design-review-requests/{reviewRequestId}/decisions (CLARIFICATION_REQUIRED, ACCEPTED, REJECTED)
POST /api/v1/custom-design-requests/{id}/quote (Staff preparation / Manager handoff)

Member Custom Orders & Payment:
GET  /api/v1/members/me/custom-design-requests
GET  /api/v1/members/me/custom-orders
GET  /api/v1/members/me/custom-orders/{customOrderId}
POST /api/v1/members/me/custom-orders/{customOrderId}/payment (100% full payment only)
POST /api/v1/members/me/custom-orders/{customOrderId}/request-new-quote

Custom Manufacturing Operations:
POST /api/v1/custom-orders/{orderId}/approve-quote (Manager approval)
POST /api/v1/custom-orders/{orderId}/status (Staff/Manager production & fulfilment tracking)
```

All writes require `Idempotency-Key`; versioned review decisions require
`If-Match-Version`. Public/Guest/Member, reviewer and audit projections are separate
DTOs. Responses never expose raw storage keys, tokens, private contact data or IDs.

Error codes include:

```text
DESIGN_REQUEST_INVALID
DESIGN_REQUEST_IMAGE_LIMIT_EXCEEDED
DESIGN_REQUEST_IMAGE_TOO_BLURRY
DESIGN_REQUEST_IMAGE_QUALITY_UNAVAILABLE
DESIGN_REQUEST_GUEST_DAILY_LIMIT_EXCEEDED
DESIGN_TRACKING_TOKEN_INVALID
DESIGN_TRACKING_TOKEN_EXPIRED
DESIGN_REVIEW_NOT_AUTHORIZED
DESIGN_REVIEW_REASON_REQUIRED
DESIGN_REVIEW_STATE_CONFLICT
DESIGN_REVIEW_ALREADY_DECIDED
```

## 6. Custom design reference and quote handoff

- A Guest or Member may submit an owner-verified persisted Feature-012 design
  reference in any readable lifecycle state, including `DRAFT`, `SAVED` and
  `ARCHIVED`, subject to source ownership and privacy checks. The reference is
  copied into an immutable snapshot and is not treated as a booking attachment.
- Feature-004 receives an immutable `CustomDesignReferenceSnapshot`; later edits
  to the source design never change the request under review.
- `ACCEPTED` moves the request to quote preparation. It does not create a payment
  attempt or reserve a production queue slot.
- Quotes carry Manager-configured `quoteExpiresAt`. An expired quote cannot be
  paid and requires a new quote generation on the same custom order.
- A custom order has no deposit, cart line or deferred balance. After a quote is
  ready, the Member must pay the complete quoted amount in one payment attempt.
- The custom-order payment link and any temporary queue hold expire together at
  the configured deadline. If verified full payment is not received, the quote
  and custom order are expired/cancelled, the queue hold is released, and the
  Member must request a new quote where policy permits.
- No `Add to Cart` action exists for custom orders. A quote creates a
  single-order payment context directly from the Member's My Custom Orders
  detail page.
- Feature-004 owns intake, clarification, review and quote handoff; Custom
  Manufacturing owns full-payment settlement, production, fulfilment and
  closure.

## 7. UI and Figma approval gate

Required screens are customer entry/form, five-image gallery, per-image quality
feedback, submission confirmation, Guest tracking, Member history, request detail,
review queue, review detail, decision form, accepted/rejected result and access/error
states for Guest, Member, Staff, Manager and Admin.

Each Figma screen needs desktop/mobile variants, loading, empty, validation, blurry
image, quota, pending, accepted, rejected, token-expired and conflict states. One
active locale must be used per screen; all Vietnamese text must be valid UTF-8.
Figma revision and node IDs must be recorded in `version.md`. The current
in-place visual baseline is approved by the user; coding remains governed by
the separate contract, permission, data and engineering gates.

## 8. Invariants and non-goals

- A request has 0..5 images and at least one description/image input.
- Every submitted image has a server-confirmed quality result.
- Every new request starts at `NEED_REVIEW`.
- Every terminal decision has an explanation and audit event.
- Staff and Manager have equal decision authority; Admin does not.
- Guest tracking is opaque, scoped and expirable.
- History/audit is not hard-deleted.
- No AI, workshop booking mutation, payment settlement, catalogue publication or
  direct Feature-012 repository access exists.
