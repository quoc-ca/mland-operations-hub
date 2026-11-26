# Feature-004 — Custom Design Request and Human Review

Date created: 2026-11-20
Date updated: 2026-11-26
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

| Capability | Guest | Member | Staff | Manager | Admin | Carrier |
|---|---|---|---|---|---|---|
| See feature entry/description | Allow | Allow | Allow | Allow | Allow | Deny |
| Start custom request | Deny; prompt sign-in | Allow for own account | Deny as customer | Deny as customer | Deny | Deny |
| Upload/read request images | Deny | Own request only | Authorized review projection | Authorized review projection | Technical projection only | Deny |
| View own request/status/feedback | Deny | Own requests only | Review scope | Review scope | Full view (Admin oversight) | Deny |
| Submit revision after clarification | Deny | Own request only | Deny | Deny | Deny | Deny |
| Review request | Deny | Deny | Allow | Allow | Deny | Deny |
| Write decision explanation | Deny | Deny | Mandatory explanation | Mandatory explanation | Deny | Deny |
| Prepare/approve quote | Deny | View own quote/payment state | Prepare quote (all Staff in V1) | Approve business quote | Deny | Deny |
| Pay custom order | Deny | Own order, full payment (10-min VNPay window) | Deny | Deny | Deny | Deny |
| Pack & advance custom shipment | Deny | Deny | Advance to `READY_FOR_CARRIER` | Advance to `READY_FOR_CARRIER` | Deny | Deny |
| Courier pickup & delivery (GHTK) | Deny | Deny | Deny | Deny | Deny | Advance `HANDED_TO_CARRIER` & `DELIVERED` |

All Member actions require Firebase identity, an active Mland Member account,
resource ownership and current request/version state. Carrier actions require an
active account with role `CARRIER`. Client-supplied role, owner, status, quote, or
reviewer fields are ignored.

### 2.1 Access and state rules (2026-11-26)

Approved in session by Hồ Hoàng Quốc and Feature owner Ngô Tiến Đạt on 2026-11-26 for implementation:

- **Quote read:** only the request owner, Staff and Manager may read a request's quote. Everyone else, Admin included, receives `404`, whether or not a quote exists. The owner receives `204` while no quote exists.
- **Feature-012 reference:** submission and clarification revision accept a design reference only when the design belongs to the current Member and its latest version is not `ARCHIVED`. A missing, foreign, legacy Guest or archived design is refused with `422 DESIGN_REFERENCE_INVALID`, the same answer for each case, and nothing is saved.
- **Quote lock:** once an order is `PAID`, `IN_PRODUCTION`, `READY_FOR_FULFILMENT` or `CLOSED`, preparing, approving or re-requesting a quote is refused with `409 CUSTOM_ORDER_QUOTE_LOCKED` and nothing changes. Approval applies only to the latest quote of that order; an older or foreign quote is refused with `409 QUOTE_ALREADY_DECIDED`. Re-quoting stays allowed in `QUOTE_PENDING`, `FULL_PAYMENT_PENDING` and `QUOTE_EXPIRED`.
- **VNPay 10-Minute Gateway Session vs. 24–72h Quote Validity:**
  - Manager-approved quote validity is business-oriented, lasting **24 to 72 hours** (`quote.expiresAt`).
  - VNPay payment URL generation (`vnp_ExpireDate`) is strictly capped at **10 minutes** from creation:
    $$\text{vnp\_ExpireDate} = \min(\text{now} + 10\text{ minutes}, \text{quote.expiresAt})$$
  - If a Member closes the bank tab or the 10-minute window elapses, the payment attempt expires without touching the quote. The Member can re-open `/account/my-custom-orders/:id` and initiate a fresh 10-minute VNPay attempt, provided the 24–72h quote validity has not expired.
  - When `now >= quote.expiresAt`, the quote and order transition to `QUOTE_EXPIRED`. Payment initiation is rejected with `422 QUOTE_EXPIRED`, requiring the Member to request a new quote.
- **Mock GHTK Carrier Handoff (Method 1B):**
  - Managed via dedicated `custom_order_shipments` records and exposed through `CarrierFacade` without mutating Feature 008 tables.
  - Upon reaching `READY_FOR_FULFILMENT`, a mock GHTK tracking code `S...` is generated in `PREPARING` status.
  - Workshop Staff packs the parcel and advances shipment to `READY_FOR_CARRIER` ("Chờ bưu tá lấy"). Staff cannot advance beyond this step.
  - Couriers with role `CARRIER` access `/api/v1/carrier/shipments` to execute `PICKED_UP` ($\rightarrow$ `HANDED_TO_CARRIER`) and `DELIVERED` ($\rightarrow$ `DELIVERED`). Marking `DELIVERED` automatically synchronizes `CustomOrder.status` to `CLOSED` and `fulfilment_status = 'DELIVERED'`.
- **Legacy Basic AI Support:** while the legacy route remains, all Guest prompts together are capped per day (default 100, configurable) in addition to the per-session limit; exceeding it returns the existing `429` quota answer. Member limits are unchanged.

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
GET  /api/v1/staff/custom-orders
PATCH /api/v1/staff/custom-orders/{customOrderId}/status
POST /api/v1/staff/custom-orders/{customOrderId}/shipment/transitions
GET  /api/v1/carrier/shipments
POST /api/v1/carrier/shipments/{shipmentId}/transitions
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

## 6. Quote, payment and custom-order fulfillment handoff

### 6.1 Full Payment & 10-Minute VNPay Session Lifecycle
- `ACCEPTED` moves a Member request to quote preparation. A quote has a configured business expiry approved by Manager, ranging from **24 to 72 hours** (`quote.expiresAt`).
- The Member must pay the complete quoted amount in one payment; there is no deposit, cart line, or indefinite unpaid hold.
- **VNPay 10-Minute Attempt Window**: When the Member initiates full payment (`POST /api/v1/members/me/custom-orders/{id}/payment`), the system generates a distinct Payment Attempt with:
  $$\text{vnp\_ExpireDate} = \min(\text{now} + 10\text{ minutes}, \text{quote.expiresAt})$$
  This 10-minute window matches the VNPay gateway session standard and is identical to Feature 008 (Retail hold) and Feature 003 (Workshop booking).
- **Payment Abort & Re-Attempt**: If the Member abandons the payment gateway, closes the browser, or the 10-minute transaction window expires at the bank, the individual payment attempt transitions to `EXPIRED`. The quote itself remains valid in `FULL_PAYMENT_PENDING` (provided `now < quote.expiresAt`). The Member can revisit `/account/my-custom-orders/:id` and click "Thanh toán lại" to generate a new 10-minute payment URL with a fresh `merchantReference`.
- **Quote Expiration (Terminal Expiry)**: If the 24–72h quote validity elapses without verified payment, both the quote and the order transition to `QUOTE_EXPIRED`. Payment initiation is rejected with `422 QUOTE_EXPIRED`. The Member must submit a new quote request (`/request-new-quote`) where policy permits.

### 6.2 Mock GHTK Fulfillment & Carrier Lifecycle (Method 1B)
- **Bounded Context & Schema Isolation**: Custom manufacturing fulfillment uses an independent entity `custom_order_shipments` and integrates into the courier portal via `CarrierFacade`, preserving zero regression and isolation from Feature 008 (`retail_shipments`).
- **Shipment Creation at Completion of Production**:
  - When Staff moves a paid order from `IN_PRODUCTION` to `READY_FOR_FULFILMENT` (manufacturing finished), the system automatically generates a GHTK-compliant tracking number (`mockTrackingNumber` with prefix `S` + 10 digits, e.g. `S1450382716`) and stores the shipment in `PREPARING` status with the Member's shipping address.
- **Role-Gated Courier Transitions**:
  - **Workshop Staff**: Packs the crafted ring and advances shipment to `READY_FOR_CARRIER` ("Chờ bưu tá lấy"). Staff cannot advance shipment beyond this step.
  - **Carrier (Courier role `CARRIER`)**:
    - Queries `/api/v1/carrier/shipments` (CarrierFacade returns unified queue of both retail and custom shipments).
    - Courier picks up parcel: transitions to `HANDED_TO_CARRIER` ("Đã lấy hàng" / "Đang giao").
    - Courier delivers parcel to recipient: transitions to `DELIVERED` ("Giao thành công").
  - **Terminal Custom Order Synchronization**: Marking the custom shipment `DELIVERED` automatically synchronizes `CustomOrder.status` to `CLOSED` and `fulfilment_status = 'DELIVERED'`.

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
13. VNPay payment URL generation sets `vnp_ExpireDate` to exactly 10 minutes (`now + 10m`), strictly decoupled from the Manager-approved 24–72h quote validity.
14. An abandoned or expired 10-minute VNPay attempt allows the Member to generate a fresh 10-minute payment attempt while quote validity is active.
15. An unpaid quote reaching the 24–72h deadline transitions to `QUOTE_EXPIRED` and rejects further payment attempts with `422 QUOTE_EXPIRED`.
16. Transitioning a custom order to `READY_FOR_FULFILMENT` creates a `custom_order_shipments` record in `PREPARING` status with a unique GHTK `S...` tracking number.
17. Staff advances custom shipment to `READY_FOR_CARRIER`; advancing past this step by Staff is rejected.
18. Courier with role `CARRIER` advances custom shipment from `READY_FOR_CARRIER` to `HANDED_TO_CARRIER` and then to `DELIVERED`, automatically closing the custom order (`CLOSED`).

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
