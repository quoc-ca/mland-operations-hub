# Feature-011 — Review Management Specification

Date created: 2026-11-03  
Date updated: 2026-11-03  
Author: Ngô Tiến Đạt  
Last updated by: Ngô Tiến Đạt

Status: `SPEC/DESIGN READY FOR REVIEW`  
Source of truth: this feature specification, `data-model.md`, the contract under `contracts/`, and the approved project documents after synchronization.

## 1. Context and objective

Feature-011 lets a Guest or Member submit feedback for a workshop session they actually attended or a retail product line they actually received. It makes eligible sources explicit, prevents duplicate source reviews, publishes technically valid reviews immediately, and gives Staff/Manager a controlled post-publication moderation path.

The feature is independent of Google Places. Google data remains read-only external information and is never copied into Mland or written back.

## 2. Scope and non-goals

In scope: eligibility lookup, review creation/editing, public listing/filtering, public aggregates, Staff hiding, Manager removal and one current Manager reply with audit history.

Out of scope: branch/location reviews, Google review ingestion/write-back, AI moderation, notifications, image/video uploads, threaded replies, OTP, Guest rate limiting and hard deletion of review history.

## 3. Actors and permission matrix

| Action | Guest | Member | Staff | Manager | Admin |
|---|---|---|---|---|---|
| Find eligible source | Email/phone lookup only | Own sources | No business lookup requirement | No business lookup requirement | Technical support only |
| Create review | Eligible matched source | Own eligible source | Deny | Deny | Deny |
| Edit own review | Own review | Own review | Deny as owner | Deny as owner unless separately entitled | Deny |
| Read public reviews | Allow | Allow | Allow | Allow | Allow |
| Read own non-public review | Own source result only | Own review only | Deny by default | Authorized operational scope | Technical projection only |
| Hide review | Deny | Deny | Any review | Any review | Deny |
| Remove review | Deny | Deny | Deny | Any review | Deny |
| Create/edit reply | Deny | Deny | Deny | One current reply | Deny |
| Technical/audit evidence | Deny | Deny | Operational evidence only | Operational evidence only | Technical/audit projection only |

Every allow requires server-side authentication or source verification, resource ownership/scope and current status. The client cannot submit a different owner, source, target or actor to gain access. Staff’s ability to hide every review is intentional and must be separately represented in permission configuration.

## 4. Eligibility

### 4.1 Workshop

The source is an attendance ticket. The source is eligible only when its attendance state is `CHECKED_IN`, it belongs to the supplied Guest identity match or authenticated Member, and it maps to one `WORKSHOP_SESSION`. A source already reviewed is not returned as creatable, although its existing review may remain readable according to ownership rules.

### 4.2 Retail

The source is one retail order line. The parent order must be fulfilled as `PICKED_UP` or `DELIVERED`, and the line must belong to the supplied Guest identity match or authenticated Member. It maps to exactly one `RETAIL_PRODUCT` and can have at most one review.

### 4.3 Guest lookup

Guest submits an email or phone number, then receives only the eligible sources that match. The response must not disclose whether unrelated transactions exist. Guest selects a specific source before creating a review. No OTP and no rate limiting are applied in V1 by decision; source matching and audit remain mandatory.

## 5. Review lifecycle and invariants

```text
CREATE_VALIDATED → PUBLIC
PUBLIC ──owner edit──> PUBLIC
PUBLIC ──Staff/Manager hide──> HIDDEN
PUBLIC ──Manager remove──> REMOVED
HIDDEN ──authorized restore──> PUBLIC
HIDDEN ──Manager remove──> REMOVED
REMOVED ──terminal──> REMOVED
```

Required invariants:

1. Only a verified eligible attendance or order line can create a review.
2. A source has at most one review, protected by a database unique constraint and an idempotent command.
3. A review’s source and target cannot be changed to another source or target.
4. `REMOVED` is soft-remove; review, revision, reply and audit history are retained.
5. Only `PUBLIC` reviews contribute to count and average aggregates or public listings.
6. Only the owner edits the review body; editing never returns it to `PENDING`.
7. Staff/Manager cannot act on a review where the actor is the review creator or has a recorded conflict of interest.
8. A review has at most one current reply. Reply edits append audit history and do not create a thread.
9. Public responses contain a masked display name only; never email, phone, source identifiers or raw transaction data.
10. Feature-011 never persists or writes back Google Places reviews.

## 6. Submission and edit behavior

Input is validated before eligibility and persistence: rating is an integer `1..5`; text is optional and at most 2,000 characters; no media fields are accepted. A valid create commits the review as `PUBLIC` and an audit/revision record atomically. A duplicate source returns a stable conflict response and does not create another review. An owner edit validates ownership and the same content limits, keeps `PUBLIC`, and appends a revision/audit record atomically.

## 7. Public listing and aggregates

Public listing supports target filtering, rating filtering and stable pagination. Homepage, Product Details and Workshop Details may feature the newest five-star reviews first, while the general list remains filterable. Aggregate average/count is calculated or projected from `PUBLIC` rows only. Hidden and removed reviews are excluded from public output and aggregates.

## 8. Moderation and reply

Staff may transition any `PUBLIC` or `HIDDEN` review to `HIDDEN` within the moderation operation permitted to Staff. Staff may not remove or reply. Manager may hide, remove, restore where allowed by the approved transition policy, create one reply and edit that reply. Each decision records actor, role, reason, time, previous status, new status and correlation/idempotency data. Admin has no business moderation authority.

## 9. Security, privacy, audit and retention

Guest contact input is used only for eligibility matching and must be normalized, protected in logs and excluded from public responses. Member access is checked against authenticated ownership. The feature uses source facades rather than repositories of Workshop or Retail. Business-history policy governs review, reply, revision and audit retention; there is no routine hard delete. Error responses must avoid source enumeration and sensitive transaction disclosure.

## 10. Proposed error behavior

`REVIEW_INPUT_INVALID` → 400/422; `REVIEW_SOURCE_NOT_ELIGIBLE` → 422; `REVIEW_NOT_AUTHORIZED` → 403; `REVIEW_NOT_FOUND` → 404; `REVIEW_ALREADY_EXISTS` → 409; `REVIEW_STATE_CONFLICT` → 409; `REVIEW_TARGET_MISMATCH` → 422; `REVIEW_REPLY_NOT_AUTHORIZED` → 403; `REVIEW_IDEMPOTENCY_CONFLICT` → 409. The final codes become baseline only after API synchronization.

## 11. Acceptance scenarios

- A Guest lookup returns only eligible checked-in workshop attendance and picked-up/delivered order lines.
- A Member cannot see or submit against another Member’s source.
- `NO_SHOW`, cancelled, unpaid and unfulfilled sources cannot be reviewed.
- A valid review is `PUBLIC` immediately and appears in public listing.
- A second or concurrent create for one source produces one row and a conflict/duplicate result.
- Rating outside 1–5 or text over 2,000 characters is rejected before persistence.
- Owner can edit only their own review; edit keeps `PUBLIC` and creates revision/audit evidence.
- Staff can hide any review but cannot remove or reply.
- Manager can hide/remove and create/edit the single current reply.
- Admin cannot perform business moderation.
- Removed reviews remain auditable and never appear publicly.
- Public payload never exposes contact details, transaction details or Google review data.
