# Entities and Physical Database Mapping: Feature 004 (AI Chatbox Analysis and Suggestion)

**Feature**: `004-ai-chatbox-analysis-and-suggestion`  
**Date**: 2026-10-24  
**Author**: Antigravity Senior Backend Engineer  
**Status**: Proposal & Schema Specification  

---

## 1. Context & Reconciliation with `DB.md`

### 1.1 Separation from Legacy `custom_design_requests`
In `ppswbs-spec/DB.md` (lines 41–42, 1927–1974), the tables `custom_design_requests` and `custom_design_reviews` are defined with:
- `status IN ('need_review', 'accepted', 'rejected')`
- A single permanent `img_url`
- Explicit rule: *"This table has no relationship to ring_designs, orders, pricing, payment, fulfilment, guest requests, or AI analysis."*
- Dedicated to the **Custom Manufacturing** journey (Feature 008).

In contrast, **Feature 004** (`spec.md`, `data-model.md`, `spec-general.md`) specifically owns:
1. **Basic AI text support** (temporary/30-day retention).
2. **Workshop booking-design image analysis and human review**:
   - Linked to a confirmed/active Workshop Booking (`booking_code` / `booking_id`).
   - Requires explicit, recorded Member consent (`ImageConsent`).
   - Uses temporary image storage, deleted immediately after processing (`TemporaryImageReference`).
   - Two distinct statuses:
     - `analysis_status`: `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`.
     - `review_status`: `NEED_REVIEW`, `ACCEPTED`, `REJECTED`, `CLARIFICATION_REQUIRED`.
   - Immutable `design_version` increments on clarification/revision.
   - Structured candidate features (`AnalysisResult`) and value estimation snapshot.
3. **Manager advisory price suggestions** for new workshop packages or retail products.

### 1.2 Decision
To avoid schema conflicts, breaking changes, or violating the `DB.md` invariant on `custom_design_requests`, Feature 004 introduces **distinct, dedicated physical tables**:
1. `ai_text_interactions`
2. `booking_design_requests`
3. `image_consents`
4. `temporary_image_references`
5. `design_analysis_results`
6. `human_review_decisions`
7. `price_suggestions`

---

## 2. Detailed Table Specifications

### 2.1 `ai_text_interactions`
Stores basic AI chat support interactions with a 30-day retention policy. No raw tokens, private customer data, or passwords.

| Column | Type | Constraints / Description |
| --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | Primary key. |
| `actor_scope` | `VARCHAR(16)` | `NOT NULL`, `CHECK (actor_scope IN ('GUEST', 'MEMBER'))`. |
| `member_id` | `BIGINT` | `NULLABLE`, FK to `members(id)` when actor is authenticated Member. |
| `prompt_text` | `VARCHAR(2000)` | `NOT NULL`, sanitized input message (max 2000 chars). |
| `response_text` | `TEXT` | `NULLABLE`, validated safe advisory response text. |
| `scope` | `VARCHAR(32)` | `NOT NULL`, `CHECK (scope IN ('PRODUCT', 'WORKSHOP', 'STORE', 'FAQ', 'OUT_OF_SCOPE'))`. |
| `provider_correlation_id` | `VARCHAR(128)` | `NULLABLE`, correlation ID from AI provider. |
| `safe_failure_code` | `VARCHAR(64)` | `NULLABLE`, error code when provider fails or input rejected. |
| `created_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP`. |
| `expires_at` | `TIMESTAMP` | `NOT NULL`, retention deadline (created_at + 30 days). |

**Indexes**:
- `INDEX idx_ati_expires (expires_at)` for background retention cleanup.
- `INDEX idx_ati_member (member_id, created_at)` for Member audit/history.

---

### 2.2 `booking_design_requests`
Represents an image-based booking-design analysis request for a workshop booking.

| Column | Type | Constraints / Description |
| --- | --- | --- |
| `id` | `VARCHAR(36)` | Primary key (UUID string representation). |
| `member_id` | `BIGINT` | `NOT NULL`, FK to `members(id)`. |
| `booking_code` | `VARCHAR(64)` | `NOT NULL`, references `workshop_bookings(booking_code)`. |
| `design_version` | `INT` | `NOT NULL DEFAULT 1`, increments when clarification requested and revised. |
| `idempotency_key` | `VARCHAR(128)` | `NOT NULL`, unique per Member and design version. |
| `analysis_status` | `VARCHAR(32)` | `NOT NULL`, `CHECK (analysis_status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED'))`. |
| `review_status` | `VARCHAR(32)` | `NOT NULL`, `CHECK (review_status IN ('NEED_REVIEW', 'ACCEPTED', 'REJECTED', 'CLARIFICATION_REQUIRED'))`. |
| `estimated_value` | `BIGINT` | `NULLABLE`, estimated value snapshot in VND (routing threshold: 3,000,000 VND). |
| `next_action` | `VARCHAR(64)` | `NOT NULL`, e.g. `AWAITING_ANALYSIS`, `AWAITING_REVIEW`, `SUBMIT_NEW_VERSION`, `PROCEED_TO_BOOKING`. |
| `safe_failure_code` | `VARCHAR(64)` | `NULLABLE`, safe error code on analysis failure. |
| `created_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP`. |
| `updated_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`. |

**Constraints & Indexes**:
- `UNIQUE KEY uk_bdr_member_version_idemp (member_id, booking_code, design_version, idempotency_key)`
- `INDEX idx_bdr_review_queue (review_status, analysis_status, created_at)` for Staff/Manager review queue.
- `INDEX idx_bdr_member (member_id, created_at)` for Member lookups.
- `CONSTRAINT fk_bdr_member FOREIGN KEY (member_id) REFERENCES members (id)`

---

### 2.3 `image_consents`
Records affirmative Member consent for AI image analysis prior to temporary upload or Gemini processing.

| Column | Type | Constraints / Description |
| --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | Primary key. |
| `member_id` | `BIGINT` | `NOT NULL`, FK to `members(id)`. |
| `booking_code` | `VARCHAR(64)` | `NOT NULL`. |
| `purpose` | `VARCHAR(64)` | `NOT NULL`, fixed to `'WORKSHOP_BOOKING_DESIGN_ANALYSIS'`. |
| `consent_version` | `VARCHAR(64)` | `NOT NULL`, e.g. `'v1.0.0'`. |
| `affirmative_action` | `VARCHAR(64)` | `NOT NULL DEFAULT 'CHECKBOX_CONFIRMED'`. |
| `accepted_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP`. |

**Indexes**:
- `INDEX idx_ic_member_booking (member_id, booking_code, consent_version)`
- `CONSTRAINT fk_ic_member FOREIGN KEY (member_id) REFERENCES members (id)`

---

### 2.4 `temporary_image_references`
Tracks temporary image storage lifecycle to enforce immediate deletion after processing.

| Column | Type | Constraints / Description |
| --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | Primary key. |
| `design_request_id` | `VARCHAR(36)` | `NOT NULL`, FK to `booking_design_requests(id)`. |
| `storage_object_key` | `VARCHAR(512)` | `NOT NULL`, object key/path in temporary storage. |
| `file_name` | `VARCHAR(255)` | `NOT NULL`, original file name (sanitized). |
| `content_type` | `VARCHAR(64)` | `NOT NULL`, MIME type (image/jpeg, image/png, image/webp). |
| `file_size_bytes` | `BIGINT` | `NOT NULL`, limited to <= 10 MB (10,485,760 bytes). |
| `deleted` | `BOOLEAN` | `NOT NULL DEFAULT FALSE`. |
| `deleted_at` | `TIMESTAMP` | `NULLABLE`, timestamp of successful deletion. |
| `deletion_outcome` | `VARCHAR(64)` | `NULLABLE`, e.g. `'DELETED_AFTER_SUCCESS'`, `'DELETED_AFTER_FAILURE'`. |
| `created_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP`. |

**Indexes**:
- `INDEX idx_tir_pending_deletion (deleted, created_at)` for cleanup jobs.
- `CONSTRAINT fk_tir_request FOREIGN KEY (design_request_id) REFERENCES booking_design_requests (id)`

---

### 2.5 `design_analysis_results`
Stores candidate ring features and image quality classifications returned by Gemini. Raw provider payloads are never persisted.

| Column | Type | Constraints / Description |
| --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | Primary key. |
| `design_request_id` | `VARCHAR(36)` | `NOT NULL UNIQUE`, FK to `booking_design_requests(id)`. |
| `schema_version` | `VARCHAR(32)` | `NOT NULL`, e.g. `'v1.0'`. |
| `image_quality_json` | `TEXT` | `NULLABLE`, structured image quality (resolution, clarity, angle). |
| `candidate_features_json` | `TEXT` | `NULLABLE`, structured candidate features (ring type, band width, gemstone settings, metal). |
| `confidence_json` | `TEXT` | `NULLABLE`, confidence/uncertainty metadata. |
| `safe_explanation` | `TEXT` | `NULLABLE`, policy-filtered explanation. |
| `provider_correlation_id` | `VARCHAR(128)` | `NULLABLE`. |
| `processed_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP`. |

**Indexes**:
- `CONSTRAINT fk_dar_request FOREIGN KEY (design_request_id) REFERENCES booking_design_requests (id)`

---

### 2.6 `human_review_decisions`
Append-only timeline of Staff/Manager decisions on a booking design request.

| Column | Type | Constraints / Description |
| --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | Primary key. |
| `design_request_id` | `VARCHAR(36)` | `NOT NULL`, FK to `booking_design_requests(id)`. |
| `reviewer_user_id` | `VARCHAR(128)` | `NOT NULL`, reviewer UID/identifier. |
| `reviewer_role` | `VARCHAR(32)` | `NOT NULL`, `CHECK (reviewer_role IN ('STAFF', 'OWNER', 'MANAGER'))`. |
| `design_version` | `INT` | `NOT NULL`, version being reviewed. |
| `decision` | `VARCHAR(32)` | `NOT NULL`, `CHECK (decision IN ('ACCEPTED', 'REJECTED', 'CLARIFICATION_REQUIRED'))`. |
| `reason` | `VARCHAR(2000)` | `NOT NULL`, mandatory decision rationale. |
| `estimated_value_snapshot`| `BIGINT` | `NULLABLE`, snapshot of value used for authority check. |
| `decided_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP`. |

**Indexes**:
- `INDEX idx_hrd_request_timeline (design_request_id, decided_at)` for chronological history.
- `CONSTRAINT fk_hrd_request FOREIGN KEY (design_request_id) REFERENCES booking_design_requests (id)`

---

### 2.7 `price_suggestions`
Stores advisory price suggestions for new workshop packages or retail products requested by a Manager.

| Column | Type | Constraints / Description |
| --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | Primary key. |
| `proposal_id` | `VARCHAR(36)` | `NOT NULL`, target package/product proposal UUID. |
| `target_type` | `VARCHAR(32)` | `NOT NULL`, `CHECK (target_type IN ('WORKSHOP_PACKAGE', 'RETAIL_PRODUCT'))`. |
| `advisory` | `BOOLEAN` | `NOT NULL DEFAULT TRUE`, always TRUE. |
| `suggestion_status` | `VARCHAR(32)` | `NOT NULL`, `CHECK (suggestion_status IN ('SUCCEEDED', 'INVALID', 'FAILED'))`. |
| `suggested_price` | `DOUBLE` | `NULLABLE`, suggested advisory price in VND. |
| `explanation` | `TEXT` | `NULLABLE`, pricing rationale/breakdown. |
| `provider_correlation_id` | `VARCHAR(128)` | `NULLABLE`. |
| `manager_decision` | `VARCHAR(32)` | `NULLABLE`, `CHECK (manager_decision IS NULL OR manager_decision IN ('ACCEPTED', 'ADJUSTED', 'REJECTED', 'MANUAL_FALLBACK'))`. |
| `final_price` | `DOUBLE` | `NULLABLE`, final price confirmed by Manager. |
| `requested_by` | `VARCHAR(128)` | `NOT NULL`, Manager UID. |
| `created_at` | `TIMESTAMP` | `NOT NULL DEFAULT CURRENT_TIMESTAMP`. |
| `decided_at` | `TIMESTAMP` | `NULLABLE`. |

**Indexes**:
- `INDEX idx_ps_proposal (proposal_id, created_at)`
