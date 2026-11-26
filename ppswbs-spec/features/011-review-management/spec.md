# Feature-011 — Review Management

Date created: 2026-11-03
Date updated: 2026-11-24
Author: Ngô Tiến Đạt
Last updated by: Ngô Tiến Đạt

Status: `PUBLIC-READ / MEMBER-WRITE REBASELINE — IMPLEMENTATION PLAN REQUIRED`

## 1. Purpose and boundary

Feature-011 stores reviews for a workshop session or retail product. A valid
review is created from a verified workshop attendance or fulfilled retail order
line, is published immediately after validation, and may later be hidden or
removed by authorized operations staff.

Guest users may browse public reviews and aggregates. Guest users may see the
review entry point and a sign-in CTA, but they cannot perform eligibility lookup,
create a review, edit a review, or access a private source. All write actions
require an authenticated active Member account.

The feature never stores or writes back Google Places reviews, branch reviews,
or transaction data in public responses.

## 2. Actors and permissions

| Action | Guest | Member | Staff | Manager | Admin |
|---|---|---|---|---|---|
| Read public list/detail/aggregate | Allow | Allow | Allow | Allow | Allow |
| See review entry point | Allow | Allow | Allow | Allow | Allow |
| Eligibility lookup | Deny; prompt sign-in | Own sources only | Deny | Deny | Technical support only |
| Create review | Deny; prompt sign-in | Own eligible source | Deny | Deny | Deny |
| Edit own review | Deny; prompt sign-in | Own review only | Deny | Deny as owner | Deny |
| Hide review | Deny | Deny | Allow | Allow | Deny |
| Remove review | Deny | Deny | Deny | Allow | Deny |
| Create/edit reply | Deny | Deny | Deny | Allow | Deny |
| Technical/audit projection | Deny | Deny | Operational scope | Operational scope | Allow if separately authorized |

Backend checks role, account status, ownership, source scope and review state.
Frontend route guards only improve navigation and must not be treated as
authorization.

## 3. Eligible sources for Members

- Workshop source: attendance ticket is `CHECKED_IN` and maps to exactly one
  `WORKSHOP_SESSION` owned by the Member.
- Retail source: order line belongs to the Member and its parent order is
  `PICKED_UP` or `DELIVERED`.
- `NO_SHOW`, cancelled, unpaid and unfulfilled sources are not eligible.
- One source can create at most one review, enforced by a database unique
  constraint and idempotent command.

The former Guest email/phone lookup and opaque eligibility-handle flow is
retired. The old endpoint must not create or disclose a private eligibility
source. A Guest attempting it receives a documented authentication response.

## 4. Review lifecycle and invariants

```text
CREATE_VALIDATED → PUBLIC
PUBLIC ──owner edit──> PUBLIC
PUBLIC ──Staff/Manager hide──> HIDDEN
PUBLIC ──Manager remove──> REMOVED
HIDDEN ──authorized restore──> PUBLIC
HIDDEN ──Manager remove──> REMOVED
REMOVED ──terminal──> REMOVED
```

- Rating is an integer from 1 to 5; text is optional and at most 2,000 chars.
- Photo rules remain those already approved by the backend contract; no new
  Guest path is created by this rebaseline.
- Owner edit never changes `PUBLIC` to a pending state.
- `REMOVED` is a soft state; history is never hard-deleted.
- Only `PUBLIC` reviews contribute to lists and aggregates.
- Public DTOs expose only masked/anonymous display name and safe review content.
- Staff/Manager cannot moderate a request where conflict-of-interest rules fail.
- A review has at most one current Manager reply; edits append audit history.

## 5. API baseline

```text
GET   /api/v1/reviews
GET   /api/v1/reviews/{reviewId}
GET   /api/v1/reviews/eligible-sources       # Member only
POST  /api/v1/reviews                       # Member only
PATCH /api/v1/reviews/{reviewId}             # Member owner only
PATCH /api/v1/reviews/{reviewId}/visibility  # Staff/Manager only
GET   /api/v1/review-moderation              # Staff/Manager only
POST  /api/v1/reviews/{reviewId}/reply       # Manager only
PATCH /api/v1/reviews/{reviewId}/reply       # Manager only
```

All write commands require `Idempotency-Key`; edits and moderation require
`If-Match-Version`. `401` means missing/invalid identity, `403` means role or
ownership failure, `404` means safe resource absence, `409` means duplicate or
stale state, and `422` means source/target eligibility failure.

The public detail/list projection must never return source ID, owner reference,
email, phone, order ID, attendance ticket, or raw audit data.

## 6. Frontend behavior

Guest may open `/reviews` and `/reviews/:reviewId`. Public cards may show a
`Write a review` CTA, but clicking it opens Member sign-in and does not call a
private endpoint. `/reviews/eligible-sources`, create and edit routes require
Member authentication. After sign-in, the original return target may be
restored safely.

Loading, empty, validation, forbidden, duplicate, stale-version, conflict and
retry states must be represented in the approved locale without mixed-language
or mojibake text.

## 7. Privacy and non-goals

Feature-011 does not review branches, import/write Google reviews, moderate with
AI, send notifications, upload videos, or create threaded replies. Business
history and audit retention follow the approved policy; no routine hard delete
is allowed.

## 8. Acceptance scenarios

1. Guest can browse public reviews and aggregates.
2. Guest clicking create/edit/eligible-source is asked to sign in and no private
   API call is made before authentication.
3. Member sees only own eligible sources and can create one review per source.
4. `CHECKED_IN`, `PICKED_UP`, and `DELIVERED` sources are accepted; invalid
   attendance/order states are rejected.
5. Duplicate and concurrent create commands produce one review and a typed
   conflict/idempotent result.
6. Owner edit keeps the review `PUBLIC` and appends revision/audit history.
7. Staff can hide but cannot remove or reply; Manager can hide/remove/reply.
8. Admin cannot make business moderation or reply decisions.
9. Public list and aggregates exclude `HIDDEN` and `REMOVED` reviews.
10. Public output never exposes private identity or transaction references.

## 9. Technical Projection for Admin & Audit (C7 Compliance)

> [!IMPORTANT]
> **Đồng bộ mã nguồn & Kiến trúc cho Agent/Lập trình viên tiếp theo:**
> - **Endpoint:** `GET /api/v1/reviews/{reviewId}/technical-projection`
> - **Quyền truy cập (Authorized Roles):** `ADMIN`, `MANAGER` (Guest, Member, Staff: `403 FORBIDDEN` / `401 UNAUTHORIZED`).
> - **DTO phía Backend:** `ReviewTechnicalProjectionDto` (`edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReviewTechnicalProjectionDto`).
> - **Nguyên lý Phân tách Trách nhiệm (Separation of Duties):**
>   - Admin có nhiệm vụ kiểm toán kỹ thuật, giám sát hệ thống và an ninh dữ liệu, nhưng **tuyệt đối không được xem** thông tin cá nhân (PII) của khách hàng hay nội dung bài đánh giá nhạy cảm.
>   - Do đó, Technical Projection **loại bỏ/mask** toàn bộ: mã tài khoản khách (`accountId`), tên thật, email, số điện thoại, và nội dung đánh giá (`bodyText`).
>   - Technical Projection **chỉ cung cấp** các thuộc tính kỹ thuật phục vụ kiểm toán: `id`, `targetType`, `targetReference`, `packageId`, `rating`, `status`, `imageCount`, `bodyTextLength`, `version`, `hasReply`, `createdAt`, `updatedAt`.
> - **Khi tích hợp Frontend Admin/Audit:** Khi phát triển màn hình kiểm toán đánh giá cho Admin, frontend gọi trực tiếp endpoint `GET /api/v1/reviews/{reviewId}/technical-projection`, không gọi endpoint thương mại `/api/v1/reviews/{reviewId}`.

