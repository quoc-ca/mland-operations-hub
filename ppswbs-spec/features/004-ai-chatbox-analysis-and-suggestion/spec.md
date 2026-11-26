# Feature-004 — Custom Design Request and Human Review

Date created: 2026-11-20
Date updated: 2026-11-24
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

Status: `MEMBER-ONLY REBASELINE — IMPLEMENTATION PLAN REQUIRED`

## 1. Purpose and boundary

Feature-004 lets an authenticated, active Member submit and follow a custom
manufacturing design request. The request may contain a text description, up to
five reference images, and/or an immutable snapshot of a Member-owned
Feature-012 configured design.

Guest users may see the feature entry point, its explanation and a sign-in CTA.
They must not create, upload, track, revise, or read a private design request.
When a Guest attempts a protected action, the frontend shows the sign-in flow
and preserves a safe return route; the backend remains the final authority.

Feature-004 performs human review and quote handoff. It does not use AI to
assess images, decide feasibility, set a price, approve a request, or reject a
request. It does not own Feature-012 validation, workshop booking, payment
settlement, production, or fulfilment.

Before a Member uploads or submits any reference image, the system must record
affirmative, versioned consent for temporary storage and sharing the image with
authorized Staff/Manager reviewers. The consent message describes human design-
request review only and must not claim that an AI provider analyzes the image.
Without consent, the image cannot be stored or submitted.

## 2. Actors and permissions

| Capability | Guest | Member | Staff | Manager | Admin |
|---|---|---|---|---|---|
| See feature entry/description | Allow | Allow | Allow | Allow | Allow |
| Start custom request | Deny; prompt sign-in | Allow for own account | Deny as customer | Deny as customer | Deny |
| Upload/read request images | Deny | Own request only | Authorized review projection | Authorized review projection | Technical projection only |
| View own request/status/feedback | Deny | Own requests only | Review scope | Review scope | Full view (Admin oversight) |
| Submit revision after clarification | Deny | Own request only | Deny | Deny | Deny |
| Review request | Deny | Deny | Allow | Allow | Deny |
| Write decision explanation | Deny | Deny | Mandatory explanation | Mandatory explanation | Deny |
| Prepare/approve quote | Deny | View own quote/payment state | Prepare quote (all Staff in V1) | Approve business quote | Deny |
| Pay custom order | Deny | Own order, full payment | Deny | Deny | Deny |

All Member actions require Firebase identity, an active Mland Member account,
resource ownership and current request/version state. Client-supplied role,
owner, status, quote, or reviewer fields are ignored.

> [!NOTE]
> **Legacy Route Removal (26/11/2026):** Tuyến đường riêng lẻ `/price-proposals/:proposalId/ai-suggestions` là dead route/thử nghiệm prototype cũ không có liên kết từ giao diện người dùng và đã được loại bỏ hoàn toàn. Toàn bộ thao tác soạn và duyệt báo giá được tích hợp trực tiếp trên trang quản lý yêu cầu thiết kế (`/design-review-requests/:id`) và danh sách đơn chế tác (`/staff/custom-orders`, `/manager/custom-orders`).


## 3. Customer flow

```text
Guest sees feature entry
  → selects protected action
  → sign-in prompt
  → Member authentication
  → request form
  → optional description and/or up to five images
  → immediate image-quality feedback
  → submit with Idempotency-Key
  → server validation and persistence
  → NEED_REVIEW
  → Member views status and staff/manager explanation
```

At least one description or image is required. Each image is validated in the
browser for type, size and readable quality, then revalidated by the backend.
`TOO_BLURRY` blocks submission. The backend owns all quality thresholds.

Feature-012 design attachment is optional. If supplied, it must belong to the
current Member and be in any non-`ARCHIVED` state. Feature-004 stores its own
immutable snapshot at submission time; later edits to the source design do not
alter the request. An archived design cannot be attached.

## 4. Human review lifecycle

```text
NEED_REVIEW → CLARIFICATION_REQUIRED → NEED_REVIEW
NEED_REVIEW → ACCEPTED
NEED_REVIEW → REJECTED
```

Staff and Manager must provide a non-empty explanation for every decision.
Every decision records actor, role, request version, timestamp and audit data.
Admin has technical/audit access only and cannot make a business decision.

Member can read the safe status, explanation and next action of an own request.
A terminal request cannot receive another decision; stale or concurrent commands
return a conflict.

## 5. Member-only API baseline

```text
POST /api/v1/design-requests
GET  /api/v1/design-requests
GET  /api/v1/design-requests/{designRequestId}
POST /api/v1/custom-design-requests/{id}
GET  /api/v1/design-review-requests
GET  /api/v1/design-review-requests/{reviewRequestId}
POST /api/v1/design-review-requests/{reviewRequestId}/decisions
POST /api/v1/custom-design-requests/{id}/quote
GET  /api/v1/members/me/custom-orders
GET  /api/v1/members/me/custom-orders/{customOrderId}
POST /api/v1/members/me/custom-orders/{customOrderId}/payment
POST /api/v1/members/me/custom-orders/{customOrderId}/request-new-quote
```

`POST`, `PATCH` and other mutations require `Idempotency-Key`; versioned
commands require `If-Match-Version`. No Guest token, Guest design session,
tracking link, or Guest daily quota is part of the current contract. Any legacy
tracking endpoint must be removed or return a documented authentication error;
it must not reveal old request data.

The old Basic AI Support, AI image analysis and AI price-suggestion routes are
not Feature-004 capabilities. If legacy routes remain in the repository, they
must be explicitly deprecated or separately protected and cannot be used as
acceptance evidence for this feature.

## 6. Quote and custom-order handoff

`ACCEPTED` moves a Member request to quote preparation. A quote has a configured
expiry. The Member must pay the complete quoted amount in one payment; there is
no deposit, cart line, or indefinite unpaid hold. An expired unpaid quote is
cancelled and requires a new quote where policy permits. Custom Manufacturing
owns production, fulfilment and closure after verified full payment.

## 7. Privacy, security and retention

- Public/Member/reviewer/audit projections are separate DTOs.
- No raw image, storage key, Firebase token, private contact data or internal
  database ID is returned to a Guest or another Member.
- Images are retained according to the approved manufacturing-history policy.
- Request, revision, decision and quote audit records are append-only.
- Feature-004 never directly accesses Feature-012 repositories.
- All authorization is enforced server-side; frontend gating is only UX.

## 8. Explicit non-goals

- Guest request creation or private request tracking.
- AI analysis, AI pricing or automated approval/rejection.
- Workshop booking or ticket mutation.
- Catalogue pricing publication.
- Deposit or cart support for custom orders.
- Direct access to Feature-012 persistence.

## 9. Acceptance scenarios

1. Guest can see the feature entry and sign-in CTA but cannot submit a request.
2. Guest interaction with the form/upload/status action opens sign-in and sends
   no protected request API call.
3. Active Member submits description-only, image-only, or mixed input.
4. A request with more than five images or a blurry image is rejected before
   persistence and before review creation.
5. A valid request starts at `NEED_REVIEW`.
6. Staff/Manager decisions without an explanation are rejected.
7. Member sees the latest decision, explanation and next action for own request.
8. Member cannot read or revise another Member's request.
9. Admin cannot accept, reject, clarify, quote, or pay through Feature-004.
10. Quote payment is full amount only; expired unpaid quote cannot be paid.
11. A Feature-012 reference is copied as an immutable snapshot and never changes
    when the original design changes.
12. Duplicate commands and stale versions return typed `409` responses.

## 10. Frontend redesign handoff — pending approval

The custom-design request page must use the existing Mland shell, typography,
paper/white/gold palette, cards, field styling and bilingual copy patterns.
The contextual consultation entry point must use the same outer chat width,
spacing and responsive breakpoint as the general consultation chat. Context
attachment may change header content, not chat geometry.

The draft Figma frames are pending user approval, not an approved baseline:

- F04 desktop: 588:43
- F04 mobile: 590:43

The draft preserves the current Member-only submission, optional Feature-012
reference, five-image limit, immediate quality feedback and human-review flow.
