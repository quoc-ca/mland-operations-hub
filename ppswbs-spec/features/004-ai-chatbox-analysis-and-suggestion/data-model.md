# Feature-04 Data Model: Independent Design Request

**Date created**: 2026-10-21  
**Date updated**: 2026-11-05
**Author**: Ngô Tiến Đạt  
**Last updated by**: Ngô Tiến Đạt  

## 1. Ownership boundary

Feature-04 stores independent design-analysis data only. It has no foreign key or
runtime dependency on Workshop Booking, workshop sessions, locations, payments,
capacity holds, tickets or check-in. If a design needs catalogue context, it stores
an opaque reference and reads approved data through `catalogue::facade` only.

The current implementation baseline is Member-only ownership. Guest Design Request
creation is out of scope; Guest uses Basic AI Support only. Description-only,
image-only and combined requests are valid. Withdrawal/revision endpoints and
approval expiry are not exposed by the current frontend contract.

## 2. Logical aggregates

### 2.1 `design_requests`

| Field | Constraint / meaning |
| --- | --- |
| `id` | UUID primary key. |
| `request_reference` | Public-safe unique reference; never exposes internal IDs. |
| `owner_account_id` | Required Member identity from the Account facade; never a foreign entity. |
| `ownership_type` | `MEMBER`. |
| `description` | Optional normalized design description, maximum 4,000 characters. |
| `catalogue_reference` | Nullable opaque package/product/model reference; no booking reference. |
| `request_version` | Positive integer, starts at 1 and increments for material revisions. |
| `request_status` | Current frontend displays backend state; withdrawal/completion commands are not exposed. |
| `analysis_status` | `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`. |
| `review_status` | `NEED_REVIEW`, `ACCEPTED`, `REJECTED`, `CLARIFICATION_REQUIRED` when supplied by backend. |
| `next_action` | Server-derived action; never trusted from client input. |
| `created_at`, `updated_at` | Audit timestamps. |

Forbidden fields: `booking_id`, `booking_code`, `booking_status`, `package_id`
as booking context, `session_id`, `location_id`, `hold_id`, `ticket_id`,
`payment_id`, `deposit_id` and `designApprovalReference`.

### 2.2 `image_consents`

Stores explicit consent only when a request contains an image.

| Field | Constraint / meaning |
| --- | --- |
| `id` | Primary key. |
| `design_request_id` | Required reference to `design_requests`. |
| `consent_version` | Required published policy version. |
| `purpose` | Fixed purpose such as `INDEPENDENT_DESIGN_IMAGE_ANALYSIS`. |
| `actor_account_id` | Member account or approved guest proof subject. |
| `affirmative_action` | Recorded checkbox/explicit action. |
| `accepted_at` | Server timestamp. |

Consent must be committed before temporary image storage or provider processing.

### 2.3 `temporary_image_references`

Stores only the lifecycle of a temporary image object.

| Field | Constraint / meaning |
| --- | --- |
| `id` | Primary key. |
| `design_request_id` | Required reference to `design_requests`. |
| `storage_object_key` | Sanitized temporary object key. |
| `content_type`, `file_size_bytes` | Validated MIME and size metadata. |
| `processing_status` | `STORED`, `PROCESSING`, `DELETED`, `DELETE_FAILED`. |
| `deleted_at`, `deletion_outcome` | Cleanup audit evidence. |
| `created_at` | Creation timestamp. |

Independent design images are temporary processing data and must be deleted after
processing on success and failure. The frontend does not cache raw image content.

### 2.4 `design_analysis_results`

| Field | Constraint / meaning |
| --- | --- |
| `id` | Primary key. |
| `design_request_id` | Required reference; one result per request version. |
| `schema_version` | Versioned safe result schema. |
| `image_quality_json` | Structured quality evidence, nullable for text-only requests. |
| `candidate_features_json` | Candidate features only; never a final design decision. |
| `confidence_json` | Confidence/uncertainty metadata. |
| `safe_explanation` | Policy-filtered explanation; no raw provider payload. |
| `provider_correlation_id` | Safe technical correlation value. |
| `processed_at` | Server timestamp. |

### 2.5 `human_review_decisions`

Append-only review evidence for the independent request.

| Field | Constraint / meaning |
| --- | --- |
| `id` | Primary key. |
| `design_request_id`, `request_version` | Decision target and version. |
| `reviewer_account_id` | Account identity from the Account facade. |
| `reviewer_role` | `STAFF` or `MANAGER`. |
| `decision` | `ACCEPTED`, `REJECTED`, `CLARIFICATION_REQUIRED`. |
| `reason` | Required bounded rationale. |
| `decided_at` | Server timestamp. |

At most one terminal decision may exist for a request version. A material revision
creates a new version and cannot reuse an earlier decision.

### 2.6 `price_suggestions`

This aggregate remains independent of Design Requests and applies only to new
workshop-package or retail-product proposals.

| Field | Constraint / meaning |
| --- | --- |
| `proposal_id`, `target_type` | Opaque catalogue proposal reference and `WORKSHOP_PACKAGE`/`RETAIL_PRODUCT`. |
| `advisory` | Always `TRUE`. |
| `suggestion_status` | `SUCCEEDED`, `INVALID`, `FAILED`. |
| `suggested_price`, `explanation` | Internal Manager-visible advisory output. |
| `manager_decision`, `final_price`, `decided_at` | Manager decision evidence; never auto-published. |
| `requested_by_account_id` | Manager account identity. |
| `provider_correlation_id`, `created_at` | Technical/audit evidence. |

## 3. Invariants

- No table in this model has a foreign key to a Workshop Booking table.
- Owner identity is resolved through the Account facade; no account entity or repository is imported.
- Raw image content, raw provider payload, tokens and credentials are not persisted.
- Text interactions expire within 30 days.
- Image deletion is attempted on success and failure paths; deletion outcome is observable.
- AI output cannot mutate booking, payment, catalogue price, custom-order or review state directly.
- Idempotency is scoped to the approved owner identity, request version and command key.
- Any guest proof mechanism must be one-way, revocable/expirable and excluded from logs.
