# Feature-011 — Review Management Specification

Date created: 2026-11-03  
Date updated: 2026-11-05
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

Display name (decision 2026-11-06): a Member's public review shows the account name partly masked (first letter of the family name and the last word, for example "H*** Quốc"), or an anonymous label when the reviewer ticks the anonymous option at creation. The internal account number is never shown, not even partly; a Member whose account has no name is shown as anonymous. The choice is stored as the display text, fixed at creation, and an edit does not change it. The same option applies to a Guest, whose masked contact is replaced by the anonymous label.

## 7. Public listing and aggregates

Public listing supports target filtering, package filtering, rating filtering and stable pagination. A workshop review carries the package of the attended booking (id and the name at that time, decision 2026-11-06), so the list shows which package each review is about and a package page can show its reviews across all of that package's sessions; the review's target stays the session. A retail review has no package. Homepage, Product Details and Workshop Details may feature the newest five-star reviews first, while the general list remains filterable. Aggregate average/count is calculated or projected from `PUBLIC` rows only. Hidden and removed reviews are excluded from public output and aggregates.

## 8. Moderation and reply

Staff may transition any `PUBLIC` or `HIDDEN` review to `HIDDEN` within the moderation operation permitted to Staff. Staff may not remove or reply. Manager may hide, remove, restore where allowed by the approved transition policy, create one reply and edit that reply. Each decision records actor, role, reason, time, previous status, new status and correlation/idempotency data. Admin has no business moderation authority.

## 9. Security, privacy, audit and retention

Guest contact input is used only for eligibility matching and must be normalized, protected in logs and excluded from public responses. Member access is checked against authenticated ownership. The feature uses source facades rather than repositories of Workshop or Retail. Business-history policy governs review, reply, revision and audit retention; there is no routine hard delete. Error responses must avoid source enumeration and sensitive transaction disclosure.

## 10. Proposed error behavior

`REVIEW_INPUT_INVALID` → 400; `REVIEW_SOURCE_NOT_ELIGIBLE` → 422; `REVIEW_GUEST_HANDLE_INVALID` → 401/422; `REVIEW_GUEST_HANDLE_EXPIRED` → 401; `REVIEW_NOT_AUTHORIZED` → 403; `REVIEW_MODERATION_ACTION_NOT_ALLOWED` → 403; `REVIEW_NOT_FOUND` → 404; `REVIEW_ALREADY_EXISTS` → 409; `REVIEW_STATE_CONFLICT` → 409; `REVIEW_TARGET_MISMATCH` → 422; `REVIEW_REPLY_NOT_AUTHORIZED` → 403; `REVIEW_IDEMPOTENCY_CONFLICT` → 409. This mapping is the approved Feature-011 API baseline.

## 11. Backend implementation contract

### 11.1 Module boundary

The backend module is `reviewmanagement`. Its implementation boundary contains
the controller/API layer, application services, domain validation, review,
revision, reply, moderation and idempotency repositories, eligibility ports,
and the public-read facade. It may depend on approved Workshop Operations,
Fulfilment and Account identity facades only. It must not import or query the
Workshop/Retail repositories directly.

### 11.2 Eligibility ports

`WorkshopAttendanceReviewPort` accepts an attendance-ticket reference and
returns an opaque source reference, workshop-session target reference, owner
evidence, attendance state and source timestamp. It returns eligible only for
`CHECKED_IN`; `NO_SHOW`, cancelled, not-checked-in and unavailable sources are
not eligible.

`RetailFulfilmentReviewPort` accepts an order-line reference and returns an
opaque source reference, retail-product target reference, owner evidence,
payment/fulfilment evidence and source timestamp. It returns eligible only when
the parent order is `PICKED_UP` or `DELIVERED`; unpaid, cancelled and other
unfulfilled states are not eligible.

### 11.3 Guest eligibility handle

Guest flow is:

```text
email/phone lookup
→ normalize contact
→ verify sources through eligibility ports
→ issue opaque eligibility handle
→ return eligible sources and handle
→ create review with handle
→ resolve handle and revalidate source/ownership
→ create PUBLIC review
```

The handle must not encode raw contact data or source identifiers, must be
bound to the returned workshop and retail source set, expire after 30 minutes,
and never be logged in raw form. A create command using a raw or unbound source
identifier is rejected. Replay is controlled by the handle lifecycle and
`Idempotency-Key`; one handle may be used for different eligible sources in its
source set, while the same command is replay-safe and a source can still be
reviewed only once.


### 11.4 Create transaction and concurrency

The create command validates transport/schema input, resolves the Member
identity or Guest handle, verifies source eligibility and target mapping,
checks idempotency, then atomically inserts the review, first revision and
creation audit before committing `PUBLIC`. The unique
`(source_type, source_reference)` constraint is the final concurrent-create
guard. A duplicate or changed payload under an existing idempotency key is
mapped to a deterministic `409` response without a second review.

Owner edit, visibility change and reply edit require optimistic version
validation. A stale version returns `REVIEW_STATE_CONFLICT` and commits no
content or audit mutation. Every successful moderation/reply/edit operation
writes its audit event in the same transaction as the business change.

### 11.5 Realtime public aggregate

The V1 aggregate is a realtime query over `reviews` with `status = PUBLIC`.
The query returns count and average rating for the selected immutable target.
`HIDDEN` and `REMOVED` rows are never included. No aggregate projection table
is required for this implementation slice.

### 11.6 Backend error mapping

The implementation must expose stable codes for invalid input, ineligible
source, invalid/expired Guest handle, unauthorized owner/moderation action,
duplicate source, target mismatch, idempotency conflict, stale version and
reply conflict. Raw contact, source lookup evidence and cross-user existence
must not be disclosed in error responses.

## 12. Acceptance scenarios

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
- Guest create with an expired, malformed, replayed or source-mismatched handle is rejected without disclosing source existence.
- Guest create with only a raw source identifier is rejected.
- Identical idempotent retry returns the original result; changed payload under the same key returns `409`.
- Concurrent create commands for one source commit at most one review.
- Realtime aggregate returns only `PUBLIC` rows and excludes `HIDDEN`/`REMOVED` rows.
- Migration verification covers clean database, upgrade database, rerun and duplicate-version detection through the current repository baseline `V92`–`V99` and Feature-011 retention correction `V100`.

## 13. Frontend boundary and implementation handoff

The frontend consumes the approved Feature-011 OpenAPI contract. It does not infer
eligibility, ownership, moderation authority or public privacy rules from client
state. Backend responses are the only source for eligible sources, review status,
masked identity, aggregate values and moderation permissions.

### 13.1 Entry points

- Public users enter through product/workshop detail or the public review list.
- Guests use contact lookup and then an opaque eligibility handle.
- Members use the authenticated eligible-source list.
- Staff and Managers use the moderation queue.
- Admin receives only a technical/audit projection when the backend exposes one.

### 13.2 Frontend route baseline

```text
/reviews
/reviews/:reviewId
/reviews/guest/lookup
/reviews/eligible-sources
/reviews/new
/reviews/:reviewId/edit
/review-moderation
/review-moderation/:reviewId
```

The exact embedding route for public reviews may be owned by Product or Workshop
pages, but the data must come from the Feature-011 public projection. No route may
display Google review write-back, branch review, image/video upload or notification
center behavior.

### 13.3 Required frontend states

The UI must define loading, empty, validation, pending, success, forbidden,
dependency failure, duplicate, stale-version conflict and retry-preserves-input
states. Customer pages are mobile-first; moderation pages are desktop-first and
responsive. Rating controls, status labels and reply forms must be keyboard
accessible and bilingual.

### 13.4 Contract implementation gate

The frontend target is the approved OpenAPI/spec. Backend integration must use
header-based idempotency/version concurrency, Guest workshop and retail
eligibility, owner-safe detail projection, Admin technical-only authority and
the DTO/status/header shapes defined by the contract.
