## 3. Database Design

(*DB Diagrams Images*)

### 3.1 accounts

Stores core user accounts and credentials, bridging Firebase Authentication with system roles and status lifecycles.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|firebase_uid|varchar(128)|UNIQUE, NOT NULL|Unique Firebase Authentication user identifier.|
|login_email|varchar(320)|NULL|Contact or authentication email address.|
|email_verified_at|datetime(6)|NULL|Timestamp recording when email verified at.|
|role|enum('MEMBER','STAFF','MANAGER','ADMIN')|NOT NULL, DEFAULT MEMBER|Assigned access control role in RBAC hierarchy.|
|status|enum('ACTIVE','PENDING_SETUP','SUSPENDED')|NOT NULL, DEFAULT ACTIVE|Operational lifecycle state (enum('active','pending_setup','suspended')).|
|resume_status|enum('ACTIVE','PENDING_SETUP')|NULL|Stores resume status value.|
|revocation_pending|tinyint(1)|NOT NULL, DEFAULT 0|Stores revocation pending value.|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|session_revocation_confirmed_at|datetime(6)|NULL|Timestamp recording when session revocation confirmed at.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.2 account_profiles

Stores personal profile and contact information for user accounts.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|account_id|bigint unsigned|PK, NOT NULL, FK (accounts.id)|Foreign key referencing accounts(id).|
|full_name|varchar(400)|NULL|Human-readable name or title.|
|phone|varchar(30)|NULL|Contact telephone number.|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.3 account_location_assignments

Associates staff and manager accounts with physical workshop locations for branch-scoped permissions.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|account_id|bigint unsigned|FK (accounts.id), UNIQUE, NOT NULL|Foreign key referencing accounts(id).|
|location_id|bigint unsigned|UNIQUE, NOT NULL|Stores location id value.|
|assigned_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Timestamp recording when assigned at.|
|active|tinyint(1)|UNIQUE, NOT NULL, DEFAULT 1|Active status indicator (1=active, 0=inactive).|

:::

### 3.4 account_audit_events

Audits administrative account lifecycle actions such as role changes, suspensions, and password resets.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|actor_account_id|bigint unsigned|NULL|Numerical count, quota, or capacity (actor account id).|
|target_account_id|bigint unsigned|NULL|Numerical count, quota, or capacity (target account id).|
|action|varchar(80)|NOT NULL|Stores action value.|
|outcome|varchar(16)|NOT NULL|Stores outcome value.|
|correlation_id|varchar(64)|NOT NULL|Stores correlation id value.|
|occurred_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Timestamp recording when occurred at.|
|metadata|json|NULL|Serialized JSON or snapshot data for metadata.|

:::

### 3.5 internal_account_provisionings

Tracks administrative account invitations, setup token links, and initial onboarding state.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|account_id|bigint unsigned|FK (accounts.id), NOT NULL|Foreign key referencing accounts(id).|
|idempotency_key|varchar(128)|UNIQUE, NOT NULL|Unique idempotency key for safe retry execution.|
|request_digest|varchar(64)|NULL|Cryptographic signature or hash digest for request digest.|
|intended_email|varchar(320)|NULL|Stores intended email value.|
|intended_role|varchar(32)|NULL|Stores intended role value.|
|intended_locations|json|NULL|Stores intended locations value.|
|status|enum('INVITATION_SENT','SETUP_COMPLETE','INVITATION_FAILED')|NOT NULL, DEFAULT INVITATION_SENT|Operational lifecycle state (enum('invitation_sent','setup_complete','invitation_failed')).|
|invitation_last_sent_at|datetime(6)|NULL|Timestamp recording when invitation last sent at.|
|created_by_admin_id|bigint unsigned|FK (accounts.id), NOT NULL|Foreign key referencing accounts(id).|
|firebase_setup_link_issued_at|datetime(6)|NULL|Timestamp recording when firebase setup link issued at.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|

:::

### 3.6 ai_text_interactions

Logs customer and staff AI chat interactions for prompt tracing and recommendation context.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|actor_scope|varchar(16)|NOT NULL|Stores actor scope value.|
|member_id|bigint|NULL|Stores member id value.|
|prompt_text|varchar(2000)|NOT NULL|Stores prompt text value.|
|response_text|text|NULL|Stores response text value.|
|scope|varchar(32)|NOT NULL|Stores scope value.|
|provider_correlation_id|varchar(128)|NULL|Stores provider correlation id value.|
|safe_failure_code|varchar(64)|NULL|Stores safe failure code value.|
|created_at|timestamp|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|expires_at|timestamp|NOT NULL|Timestamp recording when expires at.|

:::

### 3.7 booking_design_requests

Captures customer-submitted custom ring design requests associated with workshop booking sessions.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|varchar(36)|PK, NOT NULL|Primary key unique identifier.|
|member_id|bigint|UNIQUE, NOT NULL|Stores member id value.|
|booking_code|varchar(64)|UNIQUE, NOT NULL|Stores booking code value.|
|design_version|int|UNIQUE, NOT NULL, DEFAULT 1|Stores design version value.|
|idempotency_key|varchar(128)|UNIQUE, NOT NULL|Unique idempotency key for safe retry execution.|
|analysis_status|varchar(32)|NOT NULL|Stores analysis status value.|
|review_status|varchar(32)|NOT NULL|Stores review status value.|
|estimated_value|bigint|NULL|Stores estimated value value.|
|next_action|varchar(64)|NOT NULL|Stores next action value.|
|safe_failure_code|varchar(64)|NULL|Stores safe failure code value.|
|created_at|timestamp|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|timestamp|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.8 design_requests

Stores independent custom ring design requests submitted by members outside standard bookings.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|varchar(36)|PK, NOT NULL|Primary key unique identifier.|
|request_reference|varchar(64)|UNIQUE, NOT NULL|Stores request reference value.|
|owner_account_id|bigint|UNIQUE, NOT NULL|Numerical count, quota, or capacity (owner account id).|
|guest_proof_hash|varchar(128)|NULL|Cryptographic signature or hash digest for guest proof hash.|
|ownership_type|varchar(16)|NOT NULL, DEFAULT MEMBER|Stores ownership type value.|
|description|varchar(4000)|NULL|Descriptive details, business remarks, or explanatory notes.|
|catalogue_reference|varchar(128)|NULL|Stores catalogue reference value.|
|request_version|int|UNIQUE, NOT NULL, DEFAULT 1|Stores request version value.|
|idempotency_key|varchar(128)|UNIQUE, NOT NULL|Unique idempotency key for safe retry execution.|
|request_status|varchar(32)|NOT NULL, DEFAULT ACTIVE|Stores request status value.|
|analysis_status|varchar(32)|NOT NULL, DEFAULT PENDING|Stores analysis status value.|
|review_status|varchar(32)|NOT NULL, DEFAULT NEED_REVIEW|Stores review status value.|
|estimated_value|bigint|NULL|Stores estimated value value.|
|next_action|varchar(64)|NOT NULL|Stores next action value.|
|safe_failure_code|varchar(64)|NULL|Stores safe failure code value.|
|created_at|timestamp|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|timestamp|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.9 temporary_image_references

Maintains transient image upload references and storage keys before final design confirmation.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|design_request_id|varchar(36)|FK (booking_design_requests.id), NOT NULL|Foreign key referencing booking_design_requests(id).|
|storage_object_key|varchar(512)|NOT NULL|Stores storage object key value.|
|file_name|varchar(255)|NOT NULL|Stores file name value.|
|content_type|varchar(64)|NOT NULL|Stores content type value.|
|file_size_bytes|bigint|NOT NULL|Stores file size bytes value.|
|deleted|tinyint(1)|NOT NULL, DEFAULT 0|Stores deleted value.|
|deleted_at|timestamp|NULL|Timestamp recording when deleted at.|
|deletion_outcome|varchar(64)|NULL|Stores deletion outcome value.|
|created_at|timestamp|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|

:::

### 3.10 image_consents

Records customer privacy and usage consents for design reference images and CAD analysis.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|member_id|bigint|NULL|Stores member id value.|
|booking_code|varchar(64)|NULL|Stores booking code value.|
|purpose|varchar(64)|NOT NULL|Stores purpose value.|
|consent_version|varchar(64)|NOT NULL|Stores consent version value.|
|affirmative_action|varchar(64)|NOT NULL, DEFAULT CHECKBOX_CONFIRMED|Stores affirmative action value.|
|accepted_at|timestamp|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Timestamp recording when accepted at.|
|actor_account_id|bigint|NULL|Numerical count, quota, or capacity (actor account id).|
|design_request_id|varchar(36)|NULL|Stores design request id value.|

:::

### 3.11 design_analysis_results

Stores AI/LLM gemstone, ring dimension, and manufacturing feasibility analysis results.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|design_request_id|varchar(36)|FK (booking_design_requests.id), UNIQUE, NOT NULL|Foreign key referencing booking_design_requests(id).|
|schema_version|varchar(32)|NOT NULL|Stores schema version value.|
|image_quality_json|text|NULL|Serialized JSON or snapshot data for image quality json.|
|candidate_features_json|text|NULL|Calendar date for candidate features json.|
|confidence_json|text|NULL|Serialized JSON or snapshot data for confidence json.|
|safe_explanation|text|NULL|Stores safe explanation value.|
|provider_correlation_id|varchar(128)|NULL|Stores provider correlation id value.|
|processed_at|timestamp|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Timestamp recording when processed at.|

:::

### 3.12 price_suggestions

Stores algorithmic component-based pricing estimates for analyzed custom ring designs.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|proposal_id|varchar(36)|NOT NULL|Stores proposal id value.|
|target_type|varchar(32)|NOT NULL|Stores target type value.|
|advisory|tinyint(1)|NOT NULL, DEFAULT 1|Stores advisory value.|
|suggestion_status|varchar(32)|NOT NULL|Stores suggestion status value.|
|suggested_price|double|NULL|Monetary value or calculated fee (suggested price).|
|explanation|text|NULL|Stores explanation value.|
|provider_correlation_id|varchar(128)|NULL|Stores provider correlation id value.|
|manager_decision|varchar(32)|NULL|Stores manager decision value.|
|final_price|double|NULL|Monetary value or calculated fee (final price).|
|requested_by|varchar(128)|NOT NULL|Stores requested by value.|
|created_at|timestamp|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|decided_at|timestamp|NULL|Timestamp recording when decided at.|

:::

### 3.13 human_review_decisions

Records staff goldsmith and manager approval or rejection decisions on custom design requests.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|design_request_id|varchar(36)|FK (booking_design_requests.id), NOT NULL|Foreign key referencing booking_design_requests(id).|
|reviewer_user_id|varchar(128)|NOT NULL|Stores reviewer user id value.|
|reviewer_role|varchar(32)|NOT NULL|Stores reviewer role value.|
|design_version|int|NOT NULL|Stores design version value.|
|decision|varchar(32)|NOT NULL|Stores decision value.|
|reason|varchar(2000)|NOT NULL|Documented justification or reason for reason.|
|estimated_value_snapshot|bigint|NULL|Serialized JSON or snapshot data for estimated value snapshot.|
|decided_at|timestamp|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Timestamp recording when decided at.|

:::

### 3.14 workshop_locations

Defines physical workshop branch locations, addresses, and operational availability flags.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|code|varchar(64)|UNIQUE, NOT NULL|Unique human-readable business identifier code.|
|display_name|varchar(255)|NOT NULL|Human-readable name or title.|
|address_snapshot|varchar(1000)|NOT NULL|Serialized JSON or snapshot data for address snapshot.|
|active|tinyint(1)|NOT NULL, DEFAULT 1|Active status indicator (1=active, 0=inactive).|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.15 workshop_slots

Defines recurring daily time slots and baseline capacity for workshop session scheduling.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|reference|varchar(36)|UNIQUE, NOT NULL|Unique external public reference identifier.|
|location_id|bigint unsigned|FK (workshop_locations.id), NOT NULL|Foreign key referencing workshop_locations(id).|
|label|varchar(100)|NOT NULL|Stores label value.|
|start_time|time|NOT NULL|Stores start time value.|
|end_time|time|NOT NULL|Stores end time value.|
|default_capacity|int|NOT NULL|Numerical count, quota, or capacity (default capacity).|
|active|tinyint(1)|NOT NULL, DEFAULT 1|Active status indicator (1=active, 0=inactive).|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.16 workshop_packages

Maintains workshop tier packages (e.g. Standard, Premium) and base pricing definitions.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|name|longtext|NOT NULL|Human-readable name or title.|
|description|longtext|NOT NULL|Descriptive details, business remarks, or explanatory notes.|
|public_price|bigint|NOT NULL|Monetary value or calculated fee (public price).|
|min_participants|int|NOT NULL|Stores min participants value.|
|max_participants|int|NOT NULL|Stores max participants value.|
|required_session_count|int|NOT NULL|Numerical count, quota, or capacity (required session count).|
|same_location_required|tinyint(1)|NOT NULL|Stores same location required value.|
|minimum_same_day_slot_gap|int|NOT NULL|Stores minimum same day slot gap value.|
|state|varchar(16)|NOT NULL|Operational lifecycle state (varchar(16)).|
|created_at|datetime(6)|NOT NULL|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL|Record last update timestamp (UTC).|

:::

### 3.17 workshop_sessions

Represents specific calendar date and time instances of scheduled workshop sessions.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|reference|varchar(36)|UNIQUE, NOT NULL|Unique external public reference identifier.|
|location_id|bigint unsigned|FK (workshop_locations.id), UNIQUE, NOT NULL|Foreign key referencing workshop_locations(id).|
|slot_id|bigint unsigned|FK (workshop_slots.id), UNIQUE, NOT NULL|Foreign key referencing workshop_slots(id).|
|workshop_date|date|UNIQUE, NOT NULL|Calendar date for workshop date.|
|scheduled_start_at|datetime(6)|NOT NULL|Timestamp recording when scheduled start at.|
|scheduled_end_at|datetime(6)|NOT NULL|Timestamp recording when scheduled end at.|
|check_in_opens_at|datetime(6)|NOT NULL|Timestamp recording when check in opens at.|
|check_in_opening_source|enum('DEFAULT','MANAGER_SET')|NOT NULL, DEFAULT DEFAULT|Stores check in opening source value.|
|capacity|int|NOT NULL|Numerical count, quota, or capacity (capacity).|
|state|enum('DRAFT','PUBLISHED','SALES_PAUSED','IN_PROGRESS','ATTENDANCE_FINALIZED','CANCELLATION_REQUESTED','CANCELLED')|NOT NULL, DEFAULT DRAFT|Operational lifecycle state (enum('draft','published','sales_paused','in_progress','attendance_finalized','cancellation_requested','cancelled')).|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|cancellation_handoff_reference|varchar(128)|NULL|Stores cancellation handoff reference value.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|
|attendance_finalized_at|datetime(6)|NULL|Timestamp recording when attendance finalized at.|

:::

### 3.18 session_operational_leads

Designates the primary lead staff member responsible for an operational workshop session.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|session_id|bigint unsigned|FK (workshop_sessions.id), NOT NULL|Foreign key referencing workshop_sessions(id).|
|manager_account_id|bigint unsigned|NOT NULL|Numerical count, quota, or capacity (manager account id).|
|active|tinyint(1)|NOT NULL, DEFAULT 1|Active status indicator (1=active, 0=inactive).|
|reason_code|varchar(80)|NOT NULL|Documented justification or reason for reason code.|
|reason_note|varchar(500)|NULL|Documented justification or reason for reason note.|
|evidence_reference|varchar(255)|NULL|Stores evidence reference value.|
|assigned_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Timestamp recording when assigned at.|
|revoked_at|datetime(6)|NULL|Timestamp recording when revoked at.|

:::

### 3.19 session_staff_assignments

Tracks instructors and support staff assigned to assist in a workshop session.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|session_id|bigint unsigned|FK (workshop_sessions.id), NOT NULL|Foreign key referencing workshop_sessions(id).|
|account_id|bigint unsigned|NOT NULL|Numerical count, quota, or capacity (account id).|
|assigned_by_id|bigint unsigned|NOT NULL|Stores assigned by id value.|
|active|tinyint(1)|NOT NULL, DEFAULT 1|Active status indicator (1=active, 0=inactive).|
|assigned_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Timestamp recording when assigned at.|
|revoked_at|datetime(6)|NULL|Timestamp recording when revoked at.|

:::

### 3.20 workshop_addons

Catalogs available workshop add-ons (extra gemstone, engraving, plating) selectable during booking.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|reference|varchar(36)|UNIQUE, NOT NULL|Unique external public reference identifier.|
|package_id|bigint|FK (workshop_packages.id), NOT NULL|Foreign key referencing workshop_packages(id).|
|location_id|bigint unsigned|FK (workshop_locations.id), NOT NULL|Foreign key referencing workshop_locations(id).|
|description|varchar(200)|NOT NULL|Descriptive details, business remarks, or explanatory notes.|
|price_vnd|bigint|NOT NULL|Monetary value or calculated fee (price vnd).|
|state|varchar(16)|NOT NULL|Operational lifecycle state (varchar(16)).|
|created_at|datetime(6)|NOT NULL|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL|Record last update timestamp (UTC).|

:::

### 3.21 capacity_holds

Temporarily reserves workshop bench seats during checkout to prevent overbooking.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|reference|varchar(36)|UNIQUE, NOT NULL|Unique external public reference identifier.|
|session_id|bigint unsigned|FK (workshop_sessions.id), NOT NULL|Foreign key referencing workshop_sessions(id).|
|booking_reference|varchar(64)|NOT NULL|Unique external public reference identifier.|
|requested_ticket_count|int|NOT NULL|Numerical count, quota, or capacity (requested ticket count).|
|state|enum('ACTIVE','CONFIRMED','RELEASED','EXPIRED')|NOT NULL, DEFAULT ACTIVE|Operational lifecycle state (enum('active','confirmed','released','expired')).|
|expires_at|datetime(6)|NOT NULL|Timestamp recording when expires at.|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.22 attendance_tickets

Stores issued workshop admittance tickets and access passes for participants.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|reference|varchar(36)|UNIQUE, NOT NULL|Unique external public reference identifier.|
|booking_reference|varchar(64)|NOT NULL|Unique external public reference identifier.|
|ticket_sequence|int|NOT NULL|Stores ticket sequence value.|
|ticket_code|varchar(32)|UNIQUE, NOT NULL|Stores ticket code value.|
|qr_reference|varchar(64)|UNIQUE, NOT NULL|Stores qr reference value.|
|validity_state|enum('ACTIVE','CANCELLED','REVOKED','REFUNDED')|NOT NULL, DEFAULT ACTIVE|Stores validity state value.|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.23 ticket_session_entitlements

Links individual workshop tickets to specific session seat allocations.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|ticket_id|bigint unsigned|FK (attendance_tickets.id), UNIQUE, NOT NULL|Foreign key referencing attendance_tickets(id).|
|session_id|bigint unsigned|FK (workshop_sessions.id), UNIQUE, NOT NULL|Foreign key referencing workshop_sessions(id).|
|attendance_state|enum('NOT_CHECKED_IN','CHECKED_IN','NO_SHOW')|NOT NULL, DEFAULT NOT_CHECKED_IN|Stores attendance state value.|
|checked_in_at|datetime(6)|NULL|Timestamp recording when checked in at.|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.24 attendance_actions

Records real-time attendee check-in and completion actions taken by workshop staff.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|ticket_id|bigint unsigned|FK (attendance_tickets.id), NOT NULL|Foreign key referencing attendance_tickets(id).|
|session_id|bigint unsigned|FK (workshop_sessions.id), NOT NULL|Foreign key referencing workshop_sessions(id).|
|action_type|enum('CHECK_IN','CHECK_IN_OVERRIDE','REVERSAL','FINALIZATION','CORRECTION')|NOT NULL|Stores action type value.|
|actor_id|bigint unsigned|NOT NULL|Stores actor id value.|
|channel|varchar(32)|NOT NULL, DEFAULT ONSITE_SCAN|Stores channel value.|
|reason_code|varchar(80)|NULL|Documented justification or reason for reason code.|
|reason_note|varchar(500)|NULL|Documented justification or reason for reason note.|
|evidence_reference|varchar(255)|NULL|Stores evidence reference value.|
|occurred_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Timestamp recording when occurred at.|

:::

### 3.25 ticket_resend_throttles

Rate-limits ticket email and SMS re-delivery requests to prevent notification abuse.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|booking_reference|varchar(64)|UNIQUE, NOT NULL|Unique external public reference identifier.|
|window_started_at|timestamp(6)|NOT NULL|Timestamp recording when window started at.|
|request_count|int|NOT NULL|Numerical count, quota, or capacity (request count).|
|updated_at|timestamp(6)|NOT NULL|Record last update timestamp (UTC).|

:::

### 3.26 operations_audit_events

Logs operational interventions, schedule shifts, and emergency capacity adjustments.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|session_id|bigint unsigned|NULL|Stores session id value.|
|actor_id|bigint unsigned|NULL|Stores actor id value.|
|action|varchar(80)|NOT NULL|Stores action value.|
|outcome|varchar(32)|NOT NULL|Stores outcome value.|
|correlation_id|varchar(64)|NOT NULL|Stores correlation id value.|
|metadata|json|NULL|Serialized JSON or snapshot data for metadata.|
|occurred_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Timestamp recording when occurred at.|

:::

### 3.27 workshop_bookings

Core aggregate storing workshop reservation header records, customer references, and lifecycle states.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|booking_code|varchar(32)|UNIQUE, NOT NULL|Stores booking code value.|
|package_id|bigint unsigned|NOT NULL|Stores package id value.|
|package_name_snapshot|varchar(255)|NOT NULL|Serialized JSON or snapshot data for package name snapshot.|
|contact_email|varchar(254)|NOT NULL|Stores contact email value.|
|contact_phone|varchar(32)|NOT NULL|Stores contact phone value.|
|member_account_id|bigint unsigned|NULL|Numerical count, quota, or capacity (member account id).|
|participant_count|int|NOT NULL|Numerical count, quota, or capacity (participant count).|
|booking_status|enum('PENDING_PAYMENT','CONFIRMED','EXPIRED','CANCELLED')|NOT NULL, DEFAULT PENDING_PAYMENT|Stores booking status value.|
|payment_status|enum('UNPAID','DEPOSIT_PAID','FULLY_PAID')|NOT NULL, DEFAULT UNPAID|Stores payment status value.|
|hold_expires_at|datetime(6)|NULL|Timestamp recording when hold expires at.|
|confirmed_at|datetime(6)|NULL|Timestamp recording when confirmed at.|
|cancelled_at|datetime(6)|NULL|Timestamp recording when cancelled at.|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.28 booking_capacity_holds

Links active booking sessions to capacity hold reservations during payment pending windows.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|booking_id|bigint unsigned|FK (workshop_bookings.id), NOT NULL|Foreign key referencing workshop_bookings(id).|
|session_commitment_id|bigint unsigned|FK (booking_session_commitments.id), NOT NULL|Foreign key referencing booking_session_commitments(id).|
|hold_reference|varchar(64)|UNIQUE, NOT NULL|Stores hold reference value.|
|hold_status|varchar(32)|NOT NULL, DEFAULT RESERVED|Stores hold status value.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.29 booking_session_commitments

Formalizes confirmed bench commitments for a customer booking within a session.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|booking_id|bigint unsigned|FK (workshop_bookings.id), UNIQUE, NOT NULL|Foreign key referencing workshop_bookings(id).|
|session_reference|varchar(36)|UNIQUE, NOT NULL|Stores session reference value.|
|location_id|bigint unsigned|NOT NULL|Stores location id value.|
|ordinal|int|UNIQUE, NOT NULL|Stores ordinal value.|
|seat_count|int|NOT NULL|Numerical count, quota, or capacity (seat count).|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.30 booking_tickets

Associates generated customer attendance tickets with the parent workshop booking.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|booking_id|bigint unsigned|FK (workshop_bookings.id), NOT NULL|Foreign key referencing workshop_bookings(id).|
|ticket_code|varchar(64)|UNIQUE, NOT NULL|Stores ticket code value.|
|qr_reference|varchar(128)|UNIQUE, NOT NULL|Stores qr reference value.|
|delivery_status|enum('PENDING','DELIVERED','REDELIVERY_REQUESTED')|NOT NULL, DEFAULT PENDING|Stores delivery status value.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.31 booking_ticket_session_entitlements

Maps booking tickets to session entitlements and attendee identity credentials.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|booking_ticket_id|bigint unsigned|FK (booking_tickets.id), UNIQUE, NOT NULL|Foreign key referencing booking_tickets(id).|
|session_commitment_id|bigint unsigned|FK (booking_session_commitments.id), UNIQUE, NOT NULL|Foreign key referencing booking_session_commitments(id).|
|session_reference|varchar(36)|NOT NULL|Stores session reference value.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|

:::

### 3.32 member_workshop_booking_quotes

Maintains time-limited price quotes and discount calculations for member bookings.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|quote_ref|varchar(36)|UNIQUE, NOT NULL|Stores quote ref value.|
|member_account_id|bigint unsigned|NOT NULL|Numerical count, quota, or capacity (member account id).|
|package_id|bigint unsigned|NOT NULL|Stores package id value.|
|package_name_snapshot|longtext|NOT NULL|Serialized JSON or snapshot data for package name snapshot.|
|price_per_participant|bigint|NOT NULL|Monetary value or calculated fee (price per participant).|
|participant_count|int|NOT NULL|Numerical count, quota, or capacity (participant count).|
|session_facts_json|json|NOT NULL|Serialized JSON or snapshot data for session facts json.|
|benefit_selection_json|json|NOT NULL|Serialized JSON or snapshot data for benefit selection json.|
|gross_amount|bigint|NOT NULL|Monetary value or calculated fee (gross amount).|
|program_discount|bigint|NOT NULL|Numerical count, quota, or capacity (program discount).|
|points_used|bigint|NOT NULL|Numerical count, quota, or capacity (points used).|
|point_discount|bigint|NOT NULL|Numerical count, quota, or capacity (point discount).|
|post_promotion_amount|bigint|NOT NULL|Monetary value or calculated fee (post promotion amount).|
|earn_base|bigint|NOT NULL|Stores earn base value.|
|terms_digest|varchar(128)|NOT NULL|Cryptographic signature or hash digest for terms digest.|
|quote_state|varchar(16)|NOT NULL, DEFAULT OPEN|Stores quote state value.|
|accepted_booking_code|varchar(32)|NULL|Stores accepted booking code value.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|expires_at|datetime(6)|NOT NULL|Timestamp recording when expires at.|

:::

### 3.33 booking_payment_attempts

Records individual payment transactions and gateway checkout attempts for a booking.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|booking_id|bigint unsigned|FK (workshop_bookings.id), NOT NULL|Foreign key referencing workshop_bookings(id).|
|invoice_id|bigint unsigned|FK (booking_invoices.id), NOT NULL|Foreign key referencing booking_invoices(id).|
|attempt_reference|varchar(64)|UNIQUE, NOT NULL|Stores attempt reference value.|
|merchant_reference|varchar(128)|NOT NULL|Stores merchant reference value.|
|attempt_amount|bigint|NOT NULL|Monetary value or calculated fee (attempt amount).|
|status|enum('INITIATED','PENDING','SUCCESS','FAILED','EXPIRED')|NOT NULL, DEFAULT INITIATED|Operational lifecycle state (enum('initiated','pending','success','failed','expired')).|
|payment_url_snapshot|varchar(2048)|NULL|Serialized JSON or snapshot data for payment url snapshot.|
|expires_at|datetime(6)|NOT NULL|Timestamp recording when expires at.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|
|provider_create_date|varchar(14)|NULL|Calendar date for provider create date.|

:::

### 3.34 booking_payment_receipts

Stores immutable proof of payment receipts issued for booking deposits and balances.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|booking_id|bigint unsigned|FK (workshop_bookings.id), NOT NULL|Foreign key referencing workshop_bookings(id).|
|payment_attempt_id|bigint unsigned|FK (booking_payment_attempts.id)|Foreign key referencing booking_payment_attempts(id).|
|provider_event_id|varchar(128)|UNIQUE, NOT NULL|Stores provider event id value.|
|provider_name|varchar(32)|NOT NULL, DEFAULT VNPAY|Stores provider name value.|
|amount_received|bigint|NOT NULL|Monetary value or calculated fee (amount received).|
|received_at|datetime(6)|NOT NULL|Timestamp recording when received at.|
|safe_payload_hash|varchar(64)|NOT NULL|Serialized JSON or snapshot data for safe payload hash.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|

:::

### 3.35 booking_invoices

Stores formal financial invoice metadata and VAT records for workshop bookings.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|booking_id|bigint unsigned|FK (workshop_bookings.id), UNIQUE, NOT NULL|Foreign key referencing workshop_bookings(id).|
|invoice_number|varchar(64)|UNIQUE, NOT NULL|Stores invoice number value.|
|price_per_participant|bigint|NOT NULL|Monetary value or calculated fee (price per participant).|
|package_total|bigint|NOT NULL|Monetary value or calculated fee (package total).|
|post_promotion_amount|bigint|NOT NULL|Monetary value or calculated fee (post promotion amount).|
|deposit_amount|bigint|NOT NULL|Monetary value or calculated fee (deposit amount).|
|remaining_amount|bigint|NOT NULL|Monetary value or calculated fee (remaining amount).|
|currency|varchar(3)|NOT NULL, DEFAULT VND|ISO-4217 standard currency code (e.g., VND).|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.36 booking_late_payment_evidence

Captures audit evidence and staff notes when late payments are conditionally accepted.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|booking_id|bigint unsigned|FK (workshop_bookings.id), NOT NULL|Foreign key referencing workshop_bookings(id).|
|booking_code|varchar(32)|NOT NULL|Stores booking code value.|
|merchant_reference|varchar(128)|UNIQUE, NOT NULL|Stores merchant reference value.|
|evidence_reference|varchar(128)|UNIQUE, NOT NULL|Stores evidence reference value.|
|provider_transaction_reference|varchar(64)|NULL|Stores provider transaction reference value.|
|amount_vnd|bigint|NOT NULL|Monetary value or calculated fee (amount vnd).|
|currency_code|varchar(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|verified_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Timestamp recording when verified at.|

:::

### 3.37 booking_magic_links

Generates cryptographically signed magic links for guest booking lookup and ticket retrieval.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|booking_id|bigint unsigned|FK (workshop_bookings.id), NOT NULL|Foreign key referencing workshop_bookings(id).|
|token_hash|varchar(64)|UNIQUE, NOT NULL|Cryptographic signature or hash digest for token hash.|
|lifecycle_status|enum('ACTIVE','REVOKED','EXPIRED')|NOT NULL, DEFAULT ACTIVE|Stores lifecycle status value.|
|expires_at|datetime(6)|NOT NULL|Timestamp recording when expires at.|
|revoked_at|datetime(6)|NULL|Timestamp recording when revoked at.|
|revocation_reason|varchar(1000)|NULL|Documented justification or reason for revocation reason.|
|revoked_by_account_id|bigint unsigned|NULL|Numerical count, quota, or capacity (revoked by account id).|
|delivery_value_scrubbed_at|datetime(6)|NULL|Timestamp recording when delivery value scrubbed at.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.38 booking_notification_intents

Queues automated confirmation, reminder, and schedule change notifications.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|booking_id|bigint unsigned|FK (workshop_bookings.id), NOT NULL|Foreign key referencing workshop_bookings(id).|
|notification_reference|varchar(64)|UNIQUE, NOT NULL|Stores notification reference value.|
|notification_kind|varchar(64)|NOT NULL|Stores notification kind value.|
|correlation_reference|varchar(64)|NOT NULL|Stores correlation reference value.|
|delivery_snapshot|json|NOT NULL|Serialized JSON or snapshot data for delivery snapshot.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|

:::

### 3.39 booking_deposit_reconciliations

Reconciles incoming bank/VNPay deposit transactions against pending bookings.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|attempt_id|bigint unsigned|FK (booking_payment_attempts.id), UNIQUE, NOT NULL|Foreign key referencing booking_payment_attempts(id).|
|lifecycle_status|varchar(32)|NOT NULL|Stores lifecycle status value.|
|next_query_at|datetime(6)|NULL|Timestamp recording when next query at.|
|last_query_at|datetime(6)|NULL|Timestamp recording when last query at.|
|verified_evidence_reference|varchar(128)|NULL|Stores verified evidence reference value.|
|created_at|datetime(6)|NOT NULL|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL|Record last update timestamp (UTC).|

:::

### 3.40 booking_audit_evidence

Stores historical event logs and evidence snapshots for booking lifecycle transitions.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|booking_code|varchar(32)|NOT NULL|Stores booking code value.|
|location_id|bigint unsigned|NOT NULL|Stores location id value.|
|actor_type|varchar(32)|NOT NULL|Stores actor type value.|
|actor_id|bigint unsigned|NULL|Stores actor id value.|
|action|varchar(64)|NOT NULL|Stores action value.|
|outcome|varchar(32)|NOT NULL|Stores outcome value.|
|correlation_reference|varchar(64)|NOT NULL|Stores correlation reference value.|
|manager_reason|varchar(1000)|NULL|Documented justification or reason for manager reason.|
|action_reason|varchar(1000)|NULL|Documented justification or reason for action reason.|
|occurred_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Timestamp recording when occurred at.|

:::

### 3.41 booking_idempotency_records

Guarantees at-most-once processing for booking creation and modification requests.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|idempotency_key|varchar(128)|UNIQUE, NOT NULL|Unique idempotency key for safe retry execution.|
|command_action|varchar(80)|UNIQUE, NOT NULL|Stores command action value.|
|correlation_reference|varchar(64)|NOT NULL|Stores correlation reference value.|
|semantic_fingerprint|varchar(255)|NOT NULL|Cryptographic signature or hash digest for semantic fingerprint.|
|lifecycle_status|enum('IN_PROGRESS','COMPLETED')|NOT NULL, DEFAULT IN_PROGRESS|Stores lifecycle status value.|
|claim_version|bigint unsigned|NOT NULL, DEFAULT 1|Stores claim version value.|
|lease_expires_at|datetime(6)|NOT NULL|Timestamp recording when lease expires at.|
|claimed_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Timestamp recording when claimed at.|
|completed_at|datetime(6)|NULL|Timestamp recording when completed at.|
|status_code|int|NULL|Stores status code value.|
|response_payload|json|NULL|Serialized JSON or snapshot data for response payload.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.42 workshop_booking_package_policy_snapshots

Snapshots package terms and cancellation policies at the exact moment of booking.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|booking_reference|varchar(64)|UNIQUE, NOT NULL|Unique external public reference identifier.|
|package_reference|varchar(64)|NOT NULL|Stores package reference value.|
|required_session_count|int|NOT NULL|Numerical count, quota, or capacity (required session count).|
|min_participants|int|NOT NULL|Stores min participants value.|
|max_participants|int|NOT NULL|Stores max participants value.|
|same_location_required|tinyint(1)|NOT NULL|Stores same location required value.|
|minimum_same_day_slot_gap|int|NOT NULL|Stores minimum same day slot gap value.|
|created_at|timestamp(6)|NOT NULL|Record creation timestamp (UTC).|

:::

### 3.43 cancellation_handoffs

Coordinates cancellation requests transitioning from booking/operations into settlement.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|session_id|bigint unsigned|FK (workshop_sessions.id), NOT NULL|Foreign key referencing workshop_sessions(id).|
|requester_id|bigint unsigned|NOT NULL|Stores requester id value.|
|reason_code|varchar(80)|NOT NULL|Documented justification or reason for reason code.|
|reason_note|varchar(500)|NULL|Documented justification or reason for reason note.|
|evidence_reference|varchar(255)|NULL|Stores evidence reference value.|
|cancellation_case_reference|varchar(128)|NULL|Stores cancellation case reference value.|
|status|enum('REQUESTED','RESOLVED')|NOT NULL, DEFAULT REQUESTED|Operational lifecycle state (enum('requested','resolved')).|
|requested_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Timestamp recording when requested at.|
|resolved_at|datetime(6)|NULL|Timestamp recording when resolved at.|

:::

### 3.44 workshop_cancellation_outbox

Transactional outbox for reliable asynchronous delivery of workshop cancellation events.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|event_reference|varchar(128)|UNIQUE, NOT NULL|Stores event reference value.|
|session_reference|varchar(128)|NOT NULL|Stores session reference value.|
|affected_booking_references_json|json|NOT NULL|Serialized JSON or snapshot data for affected booking references json.|
|cancellation_reason_reference|varchar(1000)|NOT NULL|Documented justification or reason for cancellation reason reference.|
|operation_reference|varchar(128)|NOT NULL|Stores operation reference value.|
|correlation_reference|varchar(128)|NOT NULL|Stores correlation reference value.|
|cancellation_case_reference|varchar(128)|NOT NULL|Stores cancellation case reference value.|
|published_at|timestamp(6)|NULL|Timestamp recording when published at.|
|created_at|timestamp(6)|NOT NULL|Record creation timestamp (UTC).|

:::

### 3.45 settlement_cases

Master settlement dispute and financial reconciliation case management aggregate.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|case_reference|varchar(128)|UNIQUE, NOT NULL|Stores case reference value.|
|booking_reference|varchar(128)|NOT NULL|Unique external public reference identifier.|
|case_type|varchar(32)|NOT NULL|Stores case type value.|
|lifecycle_status|varchar(32)|NOT NULL|Stores lifecycle status value.|
|reason_reference|varchar(255)|NULL|Documented justification or reason for reason reference.|
|correlation_reference|varchar(128)|NOT NULL|Stores correlation reference value.|
|created_at|timestamp(6)|NOT NULL|Record creation timestamp (UTC).|
|updated_at|timestamp(6)|NOT NULL|Record last update timestamp (UTC).|

:::

### 3.46 settlement_cancellation_cases

Specialized settlement records handling booking cancellations and customer refunds.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|case_reference|varchar(128)|UNIQUE, NOT NULL|Stores case reference value.|
|session_reference|varchar(128)|NOT NULL|Stores session reference value.|
|affected_booking_references|json|NOT NULL|Stores affected booking references value.|
|cancellation_reason_reference|varchar(128)|NOT NULL|Documented justification or reason for cancellation reason reference.|
|operation_reference|varchar(128)|UNIQUE, NOT NULL|Stores operation reference value.|
|correlation_reference|varchar(128)|NOT NULL|Stores correlation reference value.|
|status|varchar(24)|NOT NULL|Operational lifecycle state (varchar(24)).|
|created_at|timestamp(6)|NOT NULL|Record creation timestamp (UTC).|
|updated_at|timestamp(6)|NOT NULL|Record last update timestamp (UTC).|

:::

### 3.47 settlement_charges

Records itemized service, material, and late cancellation charges assessed during settlement.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|charge_reference|varchar(128)|UNIQUE, NOT NULL|Stores charge reference value.|
|bill_reference|varchar(128)|FK (settlement_final_bills.bill_reference), NOT NULL|Foreign key referencing settlement_final_bills(bill_reference).|
|source_category|varchar(32)|NOT NULL|Stores source category value.|
|amount_vnd|bigint|NOT NULL|Monetary value or calculated fee (amount vnd).|
|currency_code|varchar(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|lifecycle_status|varchar(24)|NOT NULL|Stores lifecycle status value.|
|reason_reference|varchar(255)|NULL|Documented justification or reason for reason reference.|
|created_at|timestamp(6)|NOT NULL|Record creation timestamp (UTC).|
|description|varchar(200)|NULL|Descriptive details, business remarks, or explanatory notes.|

:::

### 3.48 settlement_addon_bills

Itemizes workshop add-on materials and tools consumed during a workshop session.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|bill_reference|varchar(128)|UNIQUE, NOT NULL|Stores bill reference value.|
|channel_booking_reference|varchar(128)|NOT NULL|Stores channel booking reference value.|
|quote_reference|varchar(128)|UNIQUE, NOT NULL|Stores quote reference value.|
|item_code|varchar(128)|NOT NULL|Stores item code value.|
|item_description|varchar(255)|NOT NULL|Stores item description value.|
|amount_minor|bigint|NOT NULL|Monetary value or calculated fee (amount minor).|
|currency_code|varchar(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|customer_consent_reference|varchar(128)|NOT NULL|Stores customer consent reference value.|
|staff_actor_reference|varchar(128)|NOT NULL|Stores staff actor reference value.|
|location_id|bigint|NULL|Stores location id value.|
|lifecycle_status|varchar(24)|NOT NULL|Stores lifecycle status value.|
|created_at|timestamp(6)|NOT NULL|Record creation timestamp (UTC).|

:::

### 3.49 settlement_final_bills

Summarizes final outstanding balance calculations required before customer release.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|bill_reference|varchar(128)|UNIQUE, NOT NULL|Stores bill reference value.|
|booking_reference|varchar(128)|UNIQUE, NOT NULL|Unique external public reference identifier.|
|post_promotion_amount_vnd|bigint|NOT NULL|Monetary value or calculated fee (post promotion amount vnd).|
|accepted_deposit_amount_vnd|bigint|NOT NULL|Monetary value or calculated fee (accepted deposit amount vnd).|
|remaining_amount_vnd|bigint|NOT NULL|Monetary value or calculated fee (remaining amount vnd).|
|currency_code|varchar(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|lifecycle_status|varchar(24)|NOT NULL|Stores lifecycle status value.|
|frozen_at|timestamp(6)|NOT NULL|Timestamp recording when frozen at.|
|created_at|timestamp(6)|NOT NULL|Record creation timestamp (UTC).|

:::

### 3.50 settlement_final_payment_attempts

Logs payment attempts for final workshop checkout balances and consumed addons.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|attempt_reference|varchar(128)|UNIQUE, NOT NULL|Stores attempt reference value.|
|bill_reference|varchar(128)|FK (settlement_final_bills.bill_reference), NOT NULL|Foreign key referencing settlement_final_bills(bill_reference).|
|booking_reference|varchar(128)|NOT NULL|Unique external public reference identifier.|
|operation_reference|varchar(128)|UNIQUE, NOT NULL|Stores operation reference value.|
|merchant_reference|varchar(128)|UNIQUE, NOT NULL|Stores merchant reference value.|
|amount_vnd|bigint|NOT NULL|Monetary value or calculated fee (amount vnd).|
|currency_code|varchar(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|provider_create_date|varchar(14)|NOT NULL|Calendar date for provider create date.|
|payment_url_snapshot|varchar(2048)|NULL|Serialized JSON or snapshot data for payment url snapshot.|
|lifecycle_status|varchar(24)|NOT NULL|Stores lifecycle status value.|
|created_at|timestamp(6)|NOT NULL|Record creation timestamp (UTC).|
|expires_at|timestamp(6)|NOT NULL|Timestamp recording when expires at.|

:::

### 3.51 settlement_refund_allocations

Tracks monetary refund disbursement breakdown between customer and shop accounts.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|allocation_reference|varchar(128)|UNIQUE, NOT NULL|Stores allocation reference value.|
|case_reference|varchar(128)|FK (settlement_cases.case_reference), UNIQUE, NOT NULL|Foreign key referencing settlement_cases(case_reference).|
|original_payment_reference|varchar(128)|UNIQUE, NOT NULL|Stores original payment reference value.|
|original_provider_transaction_number|varchar(15)|NOT NULL|Stores original provider transaction number value.|
|original_provider_create_date|varchar(14)|NOT NULL|Calendar date for original provider create date.|
|original_amount_vnd|bigint|NOT NULL|Monetary value or calculated fee (original amount vnd).|
|allocated_amount_vnd|bigint|NOT NULL|Monetary value or calculated fee (allocated amount vnd).|
|currency_code|varchar(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|operation_reference|varchar(128)|UNIQUE, NOT NULL|Stores operation reference value.|
|provider_refund_transaction_number|varchar(15)|NULL|Stores provider refund transaction number value.|
|lifecycle_status|varchar(24)|NOT NULL|Stores lifecycle status value.|
|provider_response_code|varchar(8)|NULL|Stores provider response code value.|
|provider_transaction_type|varchar(4)|NULL|Stores provider transaction type value.|
|provider_transaction_status|varchar(4)|NULL|Stores provider transaction status value.|
|provider_accepted_at|timestamp(6)|NULL|Timestamp recording when provider accepted at.|
|last_query_at|timestamp(6)|NULL|Timestamp recording when last query at.|
|created_at|timestamp(6)|NOT NULL|Record creation timestamp (UTC).|
|updated_at|timestamp(6)|NOT NULL|Record last update timestamp (UTC).|

:::

### 3.52 settlement_refund_policy_revisions

Maintains versioned business rules and percentage tiers for workshop refunds.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|policy_reference|varchar(128)|UNIQUE, NOT NULL|Stores policy reference value.|
|effective_at|timestamp(6)|UNIQUE, NOT NULL|Timestamp recording when effective at.|
|whole_booking_refund_percent|int|NOT NULL|Stores whole booking refund percent value.|
|created_by_account_id|bigint|NOT NULL|Identifier of the actor or administrator who created the record.|
|created_at|timestamp(6)|NOT NULL|Record creation timestamp (UTC).|

:::

### 3.53 settlement_refund_policy_snapshots

Snapshots applicable refund policy terms at the time of cancellation evaluation.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|snapshot_reference|varchar(128)|UNIQUE, NOT NULL|Serialized JSON or snapshot data for snapshot reference.|
|booking_reference|varchar(128)|NOT NULL|Unique external public reference identifier.|
|policy_revision|varchar(64)|NOT NULL|Stores policy revision value.|
|collected_amount_vnd|bigint|NOT NULL|Monetary value or calculated fee (collected amount vnd).|
|refundable_amount_vnd|bigint|NOT NULL|Monetary value or calculated fee (refundable amount vnd).|
|currency_code|varchar(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|captured_at|timestamp(6)|NOT NULL|Timestamp recording when captured at.|

:::

### 3.54 settlement_booking_mland_refund_policy_snapshots

Stores Mland-specific refund terms snapshot bound to the booking.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|snapshot_reference|varchar(128)|UNIQUE, NOT NULL|Serialized JSON or snapshot data for snapshot reference.|
|booking_reference|varchar(128)|UNIQUE, NOT NULL|Unique external public reference identifier.|
|policy_reference|varchar(128)|NOT NULL|Stores policy reference value.|
|refund_percent|smallint|NOT NULL|Stores refund percent value.|
|confirmed_at|timestamp(6)|NOT NULL|Timestamp recording when confirmed at.|
|captured_at|timestamp(6)|NOT NULL|Timestamp recording when captured at.|

:::

### 3.55 settlement_customer_cancellation_snapshots

Preserves customer-stated cancellation reason, timestamps, and request context.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|snapshot_reference|varchar(128)|UNIQUE, NOT NULL|Serialized JSON or snapshot data for snapshot reference.|
|case_reference|varchar(128)|FK (settlement_cases.case_reference), UNIQUE, NOT NULL|Foreign key referencing settlement_cases(case_reference).|
|booking_reference|varchar(128)|UNIQUE, NOT NULL|Unique external public reference identifier.|
|policy_revision|varchar(64)|NOT NULL|Stores policy revision value.|
|refund_percent|smallint|NOT NULL|Stores refund percent value.|
|earliest_session_reference|varchar(128)|NULL|Stores earliest session reference value.|
|earliest_session_start|timestamp(6)|NULL|Stores earliest session start value.|
|paid_deposit_amount_vnd|bigint|NOT NULL|Monetary value or calculated fee (paid deposit amount vnd).|
|refund_amount_vnd|bigint|NOT NULL|Monetary value or calculated fee (refund amount vnd).|
|currency_code|varchar(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|eligibility_outcome|varchar(16)|NOT NULL|Stores eligibility outcome value.|
|rejection_reason|varchar(64)|NULL|Documented justification or reason for rejection reason.|
|requested_at|timestamp(6)|NOT NULL|Timestamp recording when requested at.|

:::

### 3.56 settlement_mland_remedy_snapshots

Captures remedy offers (reschedule voucher, partial refund) presented to customer.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|snapshot_reference|varchar(128)|UNIQUE, NOT NULL|Serialized JSON or snapshot data for snapshot reference.|
|case_reference|varchar(128)|FK (settlement_cases.case_reference), UNIQUE, NOT NULL|Foreign key referencing settlement_cases(case_reference).|
|booking_reference|varchar(128)|UNIQUE, NOT NULL|Unique external public reference identifier.|
|policy_reference|varchar(128)|NOT NULL|Stores policy reference value.|
|cancellation_handoff_reference|varchar(128)|NOT NULL|Stores cancellation handoff reference value.|
|replacement_outcome|varchar(16)|NOT NULL|Stores replacement outcome value.|
|refund_percent|smallint|NOT NULL|Stores refund percent value.|
|confirmed_at|timestamp(6)|NOT NULL|Timestamp recording when confirmed at.|
|collected_amount_vnd|bigint|NOT NULL|Monetary value or calculated fee (collected amount vnd).|
|refundable_amount_vnd|bigint|NOT NULL|Monetary value or calculated fee (refundable amount vnd).|
|currency_code|varchar(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|captured_at|timestamp(6)|NOT NULL|Timestamp recording when captured at.|

:::

### 3.57 settlement_evidence_conclusions

Records staff investigative conclusions and evidence documentation for disputed claims.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|evidence_reference|varchar(128)|UNIQUE, NOT NULL|Stores evidence reference value.|
|booking_reference|varchar(128)|NOT NULL|Unique external public reference identifier.|
|provider_transaction_reference|varchar(255)|NULL|Stores provider transaction reference value.|
|provider_transaction_number|varchar(15)|NULL|Stores provider transaction number value.|
|original_provider_create_date|varchar(14)|NULL|Calendar date for original provider create date.|
|conclusion|varchar(32)|NOT NULL|Stores conclusion value.|
|amount_vnd|bigint|NULL|Monetary value or calculated fee (amount vnd).|
|currency_code|varchar(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|operation_reference|varchar(128)|UNIQUE, NOT NULL|Stores operation reference value.|
|created_at|timestamp(6)|NOT NULL|Record creation timestamp (UTC).|

:::

### 3.58 settlement_business_audits

Audits financial adjustments, fee waivers, and manual override decisions in settlement.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|audit_reference|varchar(128)|UNIQUE, NOT NULL|Stores audit reference value.|
|aggregate_type|varchar(64)|NOT NULL|Stores aggregate type value.|
|aggregate_reference|varchar(128)|NOT NULL|Stores aggregate reference value.|
|action_code|varchar(64)|NOT NULL|Stores action code value.|
|actor_reference|varchar(128)|NULL|Stores actor reference value.|
|correlation_reference|varchar(128)|NOT NULL|Stores correlation reference value.|
|evidence_reference|varchar(128)|NULL|Stores evidence reference value.|
|created_at|timestamp(6)|NOT NULL|Record creation timestamp (UTC).|
|reason_reference|varchar(500)|NULL|Documented justification or reason for reason reference.|

:::

### 3.59 settlement_notification_handoffs

Queues customer and accounting notification handoffs resulting from settlement.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|notification_reference|varchar(128)|UNIQUE, NOT NULL|Stores notification reference value.|
|settlement_reference|varchar(128)|NOT NULL|Stores settlement reference value.|
|notification_kind|varchar(64)|NOT NULL|Stores notification kind value.|
|correlation_reference|varchar(128)|NOT NULL|Stores correlation reference value.|
|stored_recipient|varchar(320)|NOT NULL|Stores stored recipient value.|
|rendered_settlement_content|text|NOT NULL|Stores rendered settlement content value.|
|payment_action_link|text|NULL|Network URL, endpoint path, or storage link for payment action link.|
|payment_action_link_expires_at|timestamp(6)|NULL|Timestamp recording when payment action link expires at.|
|payload_expires_at|timestamp(6)|NOT NULL|Timestamp recording when payload expires at.|
|created_at|timestamp(6)|NOT NULL|Record creation timestamp (UTC).|
|dispatched_at|timestamp(6)|NULL|Timestamp recording when dispatched at.|

:::

### 3.60 settlement_handoff_idempotency

Prevents duplicate handoff execution between operations and settlement aggregates.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|operation_reference|varchar(128)|UNIQUE, NOT NULL|Stores operation reference value.|
|request_fingerprint|varchar(64)|NOT NULL|Cryptographic signature or hash digest for request fingerprint.|
|lifecycle_status|varchar(24)|NOT NULL|Stores lifecycle status value.|
|cancellation_case_reference|varchar(128)|NULL|Stores cancellation case reference value.|
|rejection_reason|varchar(255)|NULL|Documented justification or reason for rejection reason.|
|correlation_reference|varchar(128)|NOT NULL|Stores correlation reference value.|
|created_at|timestamp(6)|NOT NULL|Record creation timestamp (UTC).|
|completed_at|timestamp(6)|NULL|Timestamp recording when completed at.|

:::

### 3.61 settlement_idempotency_outcomes

Persists cached results for repeated settlement transaction requests.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|operation_reference|varchar(128)|UNIQUE, NOT NULL|Stores operation reference value.|
|request_fingerprint|varchar(64)|NOT NULL|Cryptographic signature or hash digest for request fingerprint.|
|outcome_status|varchar(32)|NOT NULL|Stores outcome status value.|
|outcome_reference|varchar(128)|NULL|Stores outcome reference value.|
|correlation_reference|varchar(128)|NOT NULL|Stores correlation reference value.|
|created_at|timestamp(6)|NOT NULL|Record creation timestamp (UTC).|
|completed_at|timestamp(6)|NULL|Timestamp recording when completed at.|

:::

### 3.62 loyalty_registry

Singleton system gate controlling global loyalty reward policies and concurrent modifications.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, NOT NULL|Primary key unique identifier.|
|current_policy_id|bigint|FK (loyalty_policy.id), NOT NULL|Foreign key referencing loyalty_policy(id).|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|

:::

### 3.63 loyalty_policy

Defines versioned point accrual ratios, redemption rates, and qualification rules.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|revision_no|bigint|UNIQUE, NOT NULL|Stores revision no value.|
|earn_unit_amount|decimal(19,0)|NOT NULL|Monetary value or calculated fee (earn unit amount).|
|redeem_point_value|decimal(19,0)|NOT NULL|Stores redeem point value value.|
|min_redeem_points|decimal(19,0)|NOT NULL|Numerical count, quota, or capacity (min redeem points).|
|max_redeem_percent|decimal(9,6)|NOT NULL|Stores max redeem percent value.|
|rounding_mode|varchar(10)|NOT NULL|Stores rounding mode value.|
|created_at|datetime(6)|NOT NULL|Record creation timestamp (UTC).|
|created_by|bigint|NULL|Identifier of the actor or administrator who created the record.|

:::

### 3.64 loyalty_wallet

Maintains member reward point balances, tier qualifications, and active point reserves.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|member_id|bigint|PK, NOT NULL|Stores member id value.|
|balance_points|decimal(19,0)|NOT NULL|Numerical count, quota, or capacity (balance points).|
|held_points|decimal(19,0)|NOT NULL|Numerical count, quota, or capacity (held points).|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|

:::

### 3.65 loyalty_point_ledger

Append-only double-entry ledger recording all loyalty point earned, spent, and expired transactions.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|member_id|bigint|NOT NULL|Stores member id value.|
|accepted_benefit_id|bigint|FK (loyalty_accepted_benefit.id), UNIQUE, NOT NULL|Foreign key referencing loyalty_accepted_benefit(id).|
|entry_kind|varchar(10)|UNIQUE, NOT NULL|Stores entry kind value.|
|delta_balance|decimal(19,0)|NOT NULL|Stores delta balance value.|
|delta_held|decimal(19,0)|NOT NULL|Stores delta held value.|
|balance_after|decimal(19,0)|NOT NULL|Stores balance after value.|
|held_after|decimal(19,0)|NOT NULL|Stores held after value.|
|occurred_at|datetime(6)|NOT NULL|Timestamp recording when occurred at.|
|original_debit_id|bigint|FK (loyalty_point_ledger.id)|Foreign key referencing loyalty_point_ledger(id).|

:::

### 3.66 loyalty_accepted_benefit

Tracks specific discount benefits and loyalty rewards applied to orders and bookings.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|source_kind|varchar(10)|UNIQUE, NOT NULL|Stores source kind value.|
|source_ref|varchar(128)|UNIQUE, NOT NULL|Stores source ref value.|
|member_id|bigint|NOT NULL|Stores member id value.|
|member_origin_eligible|tinyint(1)|NOT NULL|Stores member origin eligible value.|
|accepted_at|datetime(6)|NOT NULL|Timestamp recording when accepted at.|
|original_deadline|datetime(6)|NOT NULL|Timestamp recording when original deadline.|
|source_revision|bigint|NOT NULL|Stores source revision value.|
|accepted_terms_digest|varchar(128)|NOT NULL|Cryptographic signature or hash digest for accepted terms digest.|
|policy_id|bigint|FK (loyalty_policy.id), NOT NULL|Foreign key referencing loyalty_policy(id).|
|program_id|bigint|FK (promotion_program.id)|Foreign key referencing promotion_program(id).|
|revision_id|bigint|FK (promotion_revision.id)|Foreign key referencing promotion_revision(id).|
|gross_amount|decimal(19,0)|NOT NULL|Monetary value or calculated fee (gross amount).|
|program_discount|decimal(19,0)|NOT NULL|Numerical count, quota, or capacity (program discount).|
|requested_points|decimal(19,0)|NOT NULL|Numerical count, quota, or capacity (requested points).|
|point_discount|decimal(19,0)|NOT NULL|Numerical count, quota, or capacity (point discount).|
|earn_base|decimal(19,0)|NOT NULL|Stores earn base value.|
|post_promotion_amount|decimal(19,0)|NULL|Monetary value or calculated fee (post promotion amount).|
|points_state|varchar(10)|NOT NULL|Numerical count, quota, or capacity (points state).|
|voucher_state|varchar(10)|NOT NULL|Stores voucher state value.|
|earn_state|varchar(12)|NOT NULL|Stores earn state value.|
|earned_points|decimal(19,0)|NULL|Numerical count, quota, or capacity (earned points).|
|settled_at|datetime(6)|NULL|Timestamp recording when settled at.|
|settlement_outcome_key|varchar(128)|NULL|Stores settlement outcome key value.|
|release_outcome_key|varchar(128)|NULL|Stores release outcome key value.|
|restore_outcome_key|varchar(128)|NULL|Stores restore outcome key value.|
|earn_outcome_key|varchar(128)|NULL|Stores earn outcome key value.|
|source_outcome_version|bigint|NOT NULL, DEFAULT 0|Stores source outcome version value.|
|voucher_consumed_at|datetime(6)|NULL|Timestamp recording when voucher consumed at.|
|voucher_restored_at|datetime(6)|NULL|Timestamp recording when voucher restored at.|
|source_terminal_at|datetime(6)|NULL|Timestamp recording when source terminal at.|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|

:::

### 3.67 loyalty_audit

Audits point adjustments, fraud investigations, and manual wallet credits.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|actor_ref|varchar(128)|NOT NULL|Stores actor ref value.|
|action|varchar(64)|UNIQUE, NOT NULL|Stores action value.|
|object_ref|varchar(128)|NOT NULL|Stores object ref value.|
|detail|varchar(2000)|NULL|Stores detail value.|
|correlation_id|varchar(64)|NULL|Stores correlation id value.|
|occurred_at|datetime(6)|NOT NULL|Timestamp recording when occurred at.|
|actor_key|varchar(128)|UNIQUE|Stores actor key value.|
|command_id|varchar(36)|UNIQUE|Stores command id value.|
|canonical_digest|varchar(64)|NULL|Cryptographic signature or hash digest for canonical digest.|
|minimal_result|varchar(2000)|NULL|Stores minimal result value.|

:::

### 3.68 promotion_program

Defines marketing promotional campaigns, valid date windows, and budget caps.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|kind|varchar(20)|NOT NULL|Stores kind value.|
|scope|varchar(20)|NOT NULL|Stores scope value.|
|code_original|varchar(128)|NULL|Stores code original value.|
|code_key|varchar(128)|UNIQUE|Stores code key value.|
|status|varchar(10)|NOT NULL|Operational lifecycle state (varchar(10)).|
|current_revision_id|bigint|FK (promotion_revision.id)|Foreign key referencing promotion_revision(id).|
|total_limit|bigint|NULL|Monetary value or calculated fee (total limit).|
|member_limit|bigint|NULL|Stores member limit value.|
|held_count|bigint|NULL|Numerical count, quota, or capacity (held count).|
|consumed_count|bigint|NULL|Numerical count, quota, or capacity (consumed count).|
|created_at|datetime(6)|NOT NULL|Record creation timestamp (UTC).|
|created_by|bigint|NOT NULL|Identifier of the actor or administrator who created the record.|
|config_version|bigint|NOT NULL, DEFAULT 0|Stores config version value.|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|

:::

### 3.69 promotion_revision

Stores immutable revisions of promotional rules and discount percentage configurations.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|program_id|bigint|FK (promotion_program.id), UNIQUE, NOT NULL|Foreign key referencing promotion_program(id).|
|revision_no|bigint|UNIQUE, NOT NULL|Stores revision no value.|
|name|varchar(200)|NOT NULL|Human-readable name or title.|
|start_at|datetime(6)|NOT NULL|Timestamp recording when start at.|
|end_at|datetime(6)|NOT NULL|Timestamp recording when end at.|
|discount_kind|varchar(10)|NOT NULL|Numerical count, quota, or capacity (discount kind).|
|percent|decimal(9,6)|NULL|Stores percent value.|
|fixed_amount|decimal(19,0)|NULL|Monetary value or calculated fee (fixed amount).|
|max_discount_amount|decimal(19,0)|NOT NULL|Monetary value or calculated fee (max discount amount).|
|minimum_eligible_amount|decimal(19,0)|NOT NULL|Monetary value or calculated fee (minimum eligible amount).|
|allow_points|tinyint(1)|NOT NULL|Numerical count, quota, or capacity (allow points).|
|created_at|datetime(6)|NOT NULL|Record creation timestamp (UTC).|
|created_by|bigint|NOT NULL|Identifier of the actor or administrator who created the record.|

:::

### 3.70 promotion_revision_target

Specifies target audience criteria, product categories, and branch filters for promotions.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|revision_id|bigint|FK (promotion_revision.id), UNIQUE, NOT NULL|Foreign key referencing promotion_revision(id).|
|dimension|varchar(20)|UNIQUE, NOT NULL|Stores dimension value.|
|target_key|varchar(64)|UNIQUE, NOT NULL|Stores target key value.|

:::

### 3.71 promotion_member_program

Tracks individual member voucher claims, redemption counters, and usage limits.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|program_id|bigint|FK (promotion_program.id), UNIQUE, NOT NULL|Foreign key referencing promotion_program(id).|
|member_id|bigint|UNIQUE, NOT NULL|Stores member id value.|
|saved_at|datetime(6)|NULL|Timestamp recording when saved at.|
|held_count|bigint|NOT NULL, DEFAULT 0|Numerical count, quota, or capacity (held count).|
|consumed_count|bigint|NOT NULL, DEFAULT 0|Numerical count, quota, or capacity (consumed count).|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|

:::

### 3.72 catalogue_categories

Maintains hierarchical merchandise and jewelry product categories.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|name|varchar(200)|NOT NULL|Human-readable name or title.|
|name_key|varbinary(1600)|UNIQUE, NOT NULL|Stores name key value.|
|description|text|NULL|Descriptive details, business remarks, or explanatory notes.|
|active|tinyint(1)|NOT NULL|Active status indicator (1=active, 0=inactive).|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|created_at|datetime(6)|NOT NULL|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL|Record last update timestamp (UTC).|

:::

### 3.73 catalogue_products

Stores primary product catalog definitions, brand metadata, and master specifications.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|name|varchar(200)|NOT NULL|Human-readable name or title.|
|description|text|NOT NULL|Descriptive details, business remarks, or explanatory notes.|
|category_id|bigint|FK (catalogue_categories.id), NOT NULL|Foreign key referencing catalogue_categories(id).|
|published|tinyint(1)|NOT NULL|Stores published value.|
|first_published_at|datetime(6)|NULL|Timestamp recording when first published at.|
|deleted_at|datetime(6)|NULL|Timestamp recording when deleted at.|
|deleted_by|varchar(128)|NULL|Stores deleted by value.|
|content_version|bigint|NOT NULL, DEFAULT 0|Stores content version value.|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|created_at|datetime(6)|NOT NULL|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL|Record last update timestamp (UTC).|

:::

### 3.74 catalogue_variants

Maintains specific purchasable SKU variants (ring size, metal alloy, gemstone color).

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|product_id|bigint|FK (catalogue_products.id), UNIQUE, NOT NULL|Foreign key referencing catalogue_products(id).|
|ring_size|smallint|UNIQUE, NOT NULL|Stores ring size value.|
|sku|varchar(25)|UNIQUE, NOT NULL|Stores sku value.|
|remaining_unsold|decimal(19,0)|NOT NULL|Stores remaining unsold value.|
|sale_enabled|tinyint(1)|NOT NULL|Stores sale enabled value.|
|applied_price|decimal(19,0)|NULL|Monetary value or calculated fee (applied price).|
|applied_proposal_id|bigint|FK (catalogue_price_proposals.id)|Foreign key referencing catalogue_price_proposals(id).|
|applied_sequence|bigint|NOT NULL, DEFAULT 0|Stores applied sequence value.|
|pending_proposal_id|bigint|FK (catalogue_price_proposals.id)|Foreign key referencing catalogue_price_proposals(id).|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|

:::

### 3.75 catalogue_media_objects

Catalogs digital media files, high-resolution imagery, and CAD render assets.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|char(36)|PK, NOT NULL|Primary key unique identifier.|
|storage_key|varchar(255)|UNIQUE, NOT NULL|Stores storage key value.|
|provider_asset_id|varchar(255)|NULL|Stores provider asset id value.|
|provider_version|varchar(64)|NULL|Stores provider version value.|
|provider_format|varchar(20)|NULL|Stores provider format value.|
|checksum|varchar(64)|NOT NULL|Stores checksum value.|
|content_type|varchar(20)|NOT NULL|Stores content type value.|
|byte_count|bigint|NOT NULL|Numerical count, quota, or capacity (byte count).|
|state|varchar(20)|NOT NULL|Operational lifecycle state (varchar(20)).|
|uploaded_by|varchar(128)|UNIQUE, NOT NULL|Stores uploaded by value.|
|command_id|char(36)|UNIQUE, NOT NULL|Stores command id value.|
|created_at|datetime(6)|NOT NULL|Record creation timestamp (UTC).|
|last_verified_at|datetime(6)|NULL|Timestamp recording when last verified at.|
|first_attached_at|datetime(6)|NULL|Timestamp recording when first attached at.|
|last_unreferenced_at|datetime(6)|NULL|Timestamp recording when last unreferenced at.|
|delete_requested_at|datetime(6)|NULL|Timestamp recording when delete requested at.|
|deleted_at|datetime(6)|NULL|Timestamp recording when deleted at.|

:::

### 3.76 catalogue_media_references

Links media objects to products and variant presentations.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|media_object_id|char(36)|FK (catalogue_media_objects.id), UNIQUE, NOT NULL|Foreign key referencing catalogue_media_objects(id).|
|owner_module|varchar(40)|UNIQUE, NOT NULL|Stores owner module value.|
|owner_record_ref|varchar(128)|UNIQUE, NOT NULL|Stores owner record ref value.|
|linked_at|datetime(6)|NOT NULL|Timestamp recording when linked at.|
|released_at|datetime(6)|NULL|Timestamp recording when released at.|

:::

### 3.77 catalogue_product_images

Stores product gallery images, display sequences, and thumbnail URLs.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|product_id|bigint|FK (catalogue_products.id), UNIQUE, NOT NULL|Foreign key referencing catalogue_products(id).|
|media_object_id|char(36)|FK (catalogue_media_objects.id), NOT NULL|Foreign key referencing catalogue_media_objects(id).|
|alt_text|varchar(200)|NOT NULL|Stores alt text value.|
|display_position|int|UNIQUE, NOT NULL|Stores display position value.|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|

:::

### 3.78 catalogue_price_proposals

Drafts proposed retail price changes submitted for managerial approval.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|variant_id|bigint|FK (catalogue_variants.id), NOT NULL|Foreign key referencing catalogue_variants(id).|
|review_id|bigint|FK (catalogue_product_reviews.id)|Foreign key referencing catalogue_product_reviews(id).|
|revision|bigint|NOT NULL|Stores revision value.|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|state|varchar(20)|NOT NULL|Operational lifecycle state (varchar(20)).|
|effective|tinyint(1)|NOT NULL|Stores effective value.|
|proposed_amount|decimal(19,0)|NOT NULL|Monetary value or calculated fee (proposed amount).|
|approved_amount|decimal(19,0)|NULL|Monetary value or calculated fee (approved amount).|
|currency|char(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|base_applied_sequence|bigint|NOT NULL|Stores base applied sequence value.|
|terms_digest|varchar(64)|NOT NULL|Cryptographic signature or hash digest for terms digest.|
|submitted_by|varchar(128)|NULL|Stores submitted by value.|
|submitted_at|datetime(6)|NULL|Timestamp recording when submitted at.|
|decided_by|varchar(128)|NULL|Stores decided by value.|
|decided_at|datetime(6)|NULL|Timestamp recording when decided at.|
|reason|varchar(2000)|NULL|Documented justification or reason for reason.|
|supersedes_id|bigint|FK (catalogue_price_proposals.id)|Foreign key referencing catalogue_price_proposals(id).|

:::

### 3.79 catalogue_price_applications

Records historical activations and application timestamps of approved price proposals.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|variant_id|bigint|FK (catalogue_variants.id), UNIQUE, NOT NULL|Foreign key referencing catalogue_variants(id).|
|proposal_id|bigint|FK (catalogue_price_proposals.id), UNIQUE, NOT NULL|Foreign key referencing catalogue_price_proposals(id).|
|applied_sequence|bigint|UNIQUE, NOT NULL|Stores applied sequence value.|
|old_amount|decimal(19,0)|NULL|Monetary value or calculated fee (old amount).|
|new_amount|decimal(19,0)|NOT NULL|Monetary value or calculated fee (new amount).|
|actor_id|varchar(128)|NOT NULL|Stores actor id value.|
|applied_at|datetime(6)|NOT NULL|Timestamp recording when applied at.|

:::

### 3.80 catalogue_product_reviews

Stores customer product ratings, written reviews, and verified buyer badges.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|product_id|bigint|FK (catalogue_products.id), UNIQUE, NOT NULL|Foreign key referencing catalogue_products(id).|
|revision|bigint|UNIQUE, NOT NULL|Stores revision value.|
|content_version|bigint|NOT NULL|Stores content version value.|
|state|varchar(20)|NOT NULL|Operational lifecycle state (varchar(20)).|
|effective|tinyint(1)|NOT NULL|Stores effective value.|
|submitted_by|varchar(128)|NULL|Stores submitted by value.|
|submitted_at|datetime(6)|NULL|Timestamp recording when submitted at.|
|decided_by|varchar(128)|NULL|Stores decided by value.|
|decided_at|datetime(6)|NULL|Timestamp recording when decided at.|
|rejection_reason|varchar(2000)|NULL|Documented justification or reason for rejection reason.|
|snapshot|json|NULL|Serialized JSON or snapshot data for snapshot.|

:::

### 3.81 catalogue_import_batches

Tracks bulk catalog import jobs, parsed spreadsheet rows, and execution status.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|char(36)|PK, NOT NULL|Primary key unique identifier.|
|actor_id|varchar(128)|UNIQUE, NOT NULL|Stores actor id value.|
|file_name|varchar(255)|NOT NULL|Stores file name value.|
|file_sha256|char(64)|NOT NULL|Stores file sha256 value.|
|state|varchar(20)|NOT NULL|Operational lifecycle state (varchar(20)).|
|row_count|int|NOT NULL|Numerical count, quota, or capacity (row count).|
|product_count|int|NOT NULL|Numerical count, quota, or capacity (product count).|
|variant_count|int|NOT NULL|Numerical count, quota, or capacity (variant count).|
|error_count|int|NOT NULL|Numerical count, quota, or capacity (error count).|
|errors|json|NULL|Stores errors value.|
|parsed_payload|json|NULL|Serialized JSON or snapshot data for parsed payload.|
|created_command_id|char(36)|UNIQUE, NOT NULL|Stores created command id value.|
|confirm_command_id|char(36)|UNIQUE|Stores confirm command id value.|
|created_product_ids|json|NULL|Stores created product ids value.|
|expires_at|datetime(6)|NOT NULL|Timestamp recording when expires at.|
|imported_at|datetime(6)|NULL|Timestamp recording when imported at.|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|created_at|datetime(6)|NOT NULL|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL|Record last update timestamp (UTC).|

:::

### 3.82 catalogue_command_receipts

Provides idempotency tracking for product catalog mutation commands.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|actor_id|varchar(128)|UNIQUE, NOT NULL|Stores actor id value.|
|action|varchar(80)|UNIQUE, NOT NULL|Stores action value.|
|command_id|char(36)|UNIQUE, NOT NULL|Stores command id value.|
|request_digest|varchar(64)|NOT NULL|Cryptographic signature or hash digest for request digest.|
|resource_type|varchar(40)|NOT NULL|Stores resource type value.|
|resource_id|varchar(64)|NULL|Stores resource id value.|
|result_status|int|NOT NULL|Stores result status value.|
|result_version|bigint|NULL|Stores result version value.|
|safe_result|json|NOT NULL|Stores safe result value.|
|committed_at|datetime(6)|NOT NULL|Timestamp recording when committed at.|

:::

### 3.83 catalogue_audit_events

Audits product creation, SKU decommissioning, and critical catalog modifications.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|actor_id|varchar(128)|NULL|Stores actor id value.|
|action|varchar(80)|NOT NULL|Stores action value.|
|resource_type|varchar(40)|NOT NULL|Stores resource type value.|
|resource_id|varchar(64)|NULL|Stores resource id value.|
|resource_version|bigint|NULL|Stores resource version value.|
|resource_revision|bigint|NULL|Stores resource revision value.|
|changed_fields|json|NULL|Stores changed fields value.|
|safe_changes|json|NULL|Stores safe changes value.|
|outcome|varchar(20)|NOT NULL|Stores outcome value.|
|correlation_id|varchar(64)|NOT NULL|Stores correlation id value.|
|occurred_at|datetime(6)|NOT NULL|Timestamp recording when occurred at.|

:::

### 3.84 retail_carts

Maintains active online shopping carts for guest and member retail sessions.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|varchar(36)|PK, NOT NULL|Primary key unique identifier.|
|member_account_id|bigint unsigned|FK (accounts.id), UNIQUE, NOT NULL|Foreign key referencing accounts(id).|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.85 retail_cart_lines

Stores line items, variant references, and quantities within a shopping cart.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|varchar(36)|PK, NOT NULL|Primary key unique identifier.|
|cart_id|varchar(36)|FK (retail_carts.id), UNIQUE, NOT NULL|Foreign key referencing retail_carts(id).|
|variant_id|bigint unsigned|UNIQUE, NOT NULL|Stores variant id value.|
|location_id|bigint unsigned|UNIQUE, NOT NULL|Stores location id value.|
|quantity|int|NOT NULL|Numerical count, quota, or capacity (quantity).|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.86 retail_checkout_holds

Temporarily holds inventory for items in checkout prior to payment completion.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|varchar(36)|PK, NOT NULL|Primary key unique identifier.|
|grouped_checkout_id|varchar(36)|FK (retail_grouped_checkouts.id), UNIQUE, NOT NULL|Foreign key referencing retail_grouped_checkouts(id).|
|status|varchar(32)|NOT NULL|Operational lifecycle state (varchar(32)).|
|expires_at|datetime(6)|NOT NULL|Timestamp recording when expires at.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|

:::

### 3.87 retail_checkout_quotes

Generates finalized purchase quotes including shipping, discounts, and taxes.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|varchar(36)|PK, NOT NULL|Primary key unique identifier.|
|member_account_id|bigint unsigned|FK (accounts.id), NOT NULL|Foreign key referencing accounts(id).|
|cart_id|varchar(36)|FK (retail_carts.id), NOT NULL|Foreign key referencing retail_carts(id).|
|cart_version|bigint|NOT NULL|Stores cart version value.|
|selection_choice|varchar(16)|NOT NULL|Stores selection choice value.|
|voucher_code|varchar(128)|NULL|Stores voucher code value.|
|requested_points|decimal(19,0)|NOT NULL|Numerical count, quota, or capacity (requested points).|
|terms_digest|varchar(128)|NOT NULL|Cryptographic signature or hash digest for terms digest.|
|currency|varchar(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|merchandise_subtotal|decimal(19,0)|NOT NULL|Monetary value or calculated fee (merchandise subtotal).|
|shipping_total|decimal(19,0)|NOT NULL|Monetary value or calculated fee (shipping total).|
|program_discount|decimal(19,0)|NOT NULL|Numerical count, quota, or capacity (program discount).|
|points_discount|decimal(19,0)|NOT NULL|Numerical count, quota, or capacity (points discount).|
|payable_total|decimal(19,0)|NOT NULL|Monetary value or calculated fee (payable total).|
|status|varchar(16)|NOT NULL|Operational lifecycle state (varchar(16)).|
|accepted_checkout_id|varchar(36)|FK (retail_grouped_checkouts.id), UNIQUE|Foreign key referencing retail_grouped_checkouts(id).|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|expires_at|datetime(6)|NOT NULL|Timestamp recording when expires at.|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|

:::

### 3.88 retail_checkout_quote_branches

Tracks branch fulfillment assignments for multi-location retail orders.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|varchar(36)|PK, NOT NULL|Primary key unique identifier.|
|quote_id|varchar(36)|FK (retail_checkout_quotes.id), UNIQUE, NOT NULL|Foreign key referencing retail_checkout_quotes(id).|
|location_id|bigint unsigned|UNIQUE, NOT NULL|Stores location id value.|
|fulfilment_method|varchar(16)|NOT NULL|Stores fulfilment method value.|
|shipping_quote_id|varchar(128)|NULL|Stores shipping quote id value.|
|shipping_charge|decimal(19,0)|NOT NULL|Stores shipping charge value.|
|subtotal|decimal(19,0)|NOT NULL|Monetary value or calculated fee (subtotal).|

:::

### 3.89 retail_checkout_quote_items

Itemizes products and applied discounts within a finalized checkout quote.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|varchar(36)|PK, NOT NULL|Primary key unique identifier.|
|quote_branch_id|varchar(36)|FK (retail_checkout_quote_branches.id), UNIQUE, NOT NULL|Foreign key referencing retail_checkout_quote_branches(id).|
|product_id|bigint unsigned|NOT NULL|Stores product id value.|
|variant_id|bigint unsigned|UNIQUE, NOT NULL|Stores variant id value.|
|quantity|int|NOT NULL|Numerical count, quota, or capacity (quantity).|
|unit_price|decimal(19,0)|NOT NULL|Monetary value or calculated fee (unit price).|
|line_total|decimal(19,0)|NOT NULL|Monetary value or calculated fee (line total).|

:::

### 3.90 retail_grouped_checkouts

Coordinates checkout grouping for orders containing multiple fulfillment streams.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|varchar(36)|PK, NOT NULL|Primary key unique identifier.|
|member_account_id|bigint unsigned|FK (accounts.id), UNIQUE, NOT NULL|Foreign key referencing accounts(id).|
|idempotency_key|varchar(128)|UNIQUE, NOT NULL|Unique idempotency key for safe retry execution.|
|cart_version|bigint|NOT NULL|Stores cart version value.|
|currency|varchar(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|merchandise_subtotal|decimal(19,2)|NOT NULL|Monetary value or calculated fee (merchandise subtotal).|
|shipping_charge|decimal(19,2)|NOT NULL|Stores shipping charge value.|
|discount_amount|decimal(19,2)|NOT NULL|Monetary value or calculated fee (discount amount).|
|payable_amount|decimal(19,2)|NOT NULL|Monetary value or calculated fee (payable amount).|
|payment_state|varchar(32)|NOT NULL|Stores payment state value.|
|payment_initiated_at|datetime(6)|NULL|Timestamp recording when payment initiated at.|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|
|cancellation_unrestored_quantity|int|NULL|Numerical count, quota, or capacity (cancellation unrestored quantity).|
|payment_proof_ref|varchar(128)|NULL|Stores payment proof ref value.|

:::

### 3.91 retail_orders

Master aggregate storing confirmed retail orders, customer details, and payment status.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|varchar(36)|PK, NOT NULL|Primary key unique identifier.|
|grouped_checkout_id|varchar(36)|FK (retail_grouped_checkouts.id), UNIQUE, NOT NULL|Foreign key referencing retail_grouped_checkouts(id).|
|location_id|bigint unsigned|UNIQUE, NOT NULL|Stores location id value.|
|fulfilment_method|varchar(16)|NOT NULL|Stores fulfilment method value.|
|payment_state|varchar(32)|NOT NULL|Stores payment state value.|
|fulfilment_state|varchar(32)|NOT NULL|Stores fulfilment state value.|
|subtotal|decimal(19,2)|NOT NULL|Monetary value or calculated fee (subtotal).|
|shipping_charge|decimal(19,2)|NOT NULL|Stores shipping charge value.|
|payable_amount|decimal(19,2)|NOT NULL|Monetary value or calculated fee (payable amount).|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|pickup_ready_confirmed|tinyint(1)|NOT NULL, DEFAULT 0|Stores pickup ready confirmed value.|

:::

### 3.92 retail_order_items

Itemizes purchased product variants, prices, and fulfillment states in a retail order.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|varchar(36)|PK, NOT NULL|Primary key unique identifier.|
|retail_order_id|varchar(36)|FK (retail_orders.id), NOT NULL|Foreign key referencing retail_orders(id).|
|variant_id|bigint unsigned|NOT NULL|Stores variant id value.|
|quantity|int|NOT NULL|Numerical count, quota, or capacity (quantity).|
|unit_price|decimal(19,2)|NOT NULL|Monetary value or calculated fee (unit price).|
|line_total|decimal(19,2)|NOT NULL|Monetary value or calculated fee (line total).|

:::

### 3.93 retail_variant_availability_guards

Provides optimistic and pessimistic locking guards for high-demand variant stock.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|variant_id|bigint unsigned|PK, NOT NULL|Stores variant id value.|
|version|bigint|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.94 retail_variant_holds

Tracks active physical stock holds on variants across store branches.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|varchar(36)|PK, NOT NULL|Primary key unique identifier.|
|grouped_checkout_id|varchar(36)|FK (retail_grouped_checkouts.id), UNIQUE, NOT NULL|Foreign key referencing retail_grouped_checkouts(id).|
|variant_id|bigint unsigned|UNIQUE, NOT NULL|Stores variant id value.|
|quantity|int|NOT NULL|Numerical count, quota, or capacity (quantity).|
|status|varchar(16)|NOT NULL|Operational lifecycle state (varchar(16)).|
|expires_at|datetime(6)|NOT NULL|Timestamp recording when expires at.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.95 retail_pickup_evidence

Stores digital signatures and photo evidence when items are picked up in-store.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|varchar(36)|PK, NOT NULL|Primary key unique identifier.|
|retail_order_id|varchar(36)|FK (retail_orders.id), UNIQUE, NOT NULL|Foreign key referencing retail_orders(id).|
|location_id|bigint unsigned|NOT NULL|Stores location id value.|
|actor_account_id|bigint unsigned|FK (accounts.id), NOT NULL|Foreign key referencing accounts(id).|
|milestone|varchar(16)|UNIQUE, NOT NULL|Stores milestone value.|
|occurred_at|datetime(6)|NOT NULL|Timestamp recording when occurred at.|

:::

### 3.96 payment_owner_registrations

Registers authorized merchant accounts and payment gateway API credentials.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|merchant_reference|varchar(128)|UNIQUE, NOT NULL|Stores merchant reference value.|
|booking_reference|varchar(128)|NOT NULL|Unique external public reference identifier.|
|payment_kind|varchar(24)|NOT NULL|Stores payment kind value.|
|amount_vnd|bigint|NOT NULL|Monetary value or calculated fee (amount vnd).|
|currency_code|varchar(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|provider_create_date|varchar(14)|NULL|Calendar date for provider create date.|
|lifecycle_status|varchar(24)|NOT NULL|Stores lifecycle status value.|
|created_at|timestamp(6)|NOT NULL|Record creation timestamp (UTC).|

:::

### 3.97 payment_ingress_receipts

Logs raw incoming IPN and webhook callbacks from payment gateways for audit.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|delivery_reference|varchar(256)|UNIQUE, NOT NULL|Stores delivery reference value.|
|merchant_reference|varchar(128)|NOT NULL|Stores merchant reference value.|
|created_at|timestamp(6)|NOT NULL|Record creation timestamp (UTC).|
|response_code|varchar(2)|NULL|Stores response code value.|
|transaction_status|varchar(2)|NULL|Stores transaction status value.|
|terminal_outcome|tinyint(1)|NOT NULL, DEFAULT 0|Stores terminal outcome value.|
|amount_vnd|bigint|NULL|Monetary value or calculated fee (amount vnd).|
|currency_code|varchar(3)|NULL|ISO-4217 standard currency code (e.g., VND).|
|provider_transaction_number|varchar(15)|NULL|Stores provider transaction number value.|

:::

### 3.98 vnpay_merchant_operations

Maintains transaction logs, signature digests, and queryDR logs for VNPay operations.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|operation_reference|varchar(128)|UNIQUE, NOT NULL|Stores operation reference value.|
|request_fingerprint|varchar(64)|NOT NULL|Cryptographic signature or hash digest for request fingerprint.|
|operation_kind|varchar(16)|NOT NULL|Stores operation kind value.|
|provider_request_id|varchar(32)|UNIQUE, NOT NULL|Stores provider request id value.|
|merchant_reference|varchar(128)|NOT NULL|Stores merchant reference value.|
|operation_status|varchar(16)|NOT NULL|Stores operation status value.|
|provider_response_code|varchar(2)|NULL|Stores provider response code value.|
|provider_transaction_type|varchar(2)|NULL|Stores provider transaction type value.|
|provider_transaction_status|varchar(2)|NULL|Stores provider transaction status value.|
|provider_transaction_number|varchar(15)|NULL|Stores provider transaction number value.|
|amount_vnd|bigint|NULL|Monetary value or calculated fee (amount vnd).|
|created_at|timestamp(6)|NOT NULL|Record creation timestamp (UTC).|
|updated_at|timestamp(6)|NOT NULL|Record last update timestamp (UTC).|

:::

### 3.99 ota_channel_connections

Maintains API integration configurations for Online Travel Agencies (Klook, KKday, etc.).

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|provider_code|varchar(32)|UNIQUE, NOT NULL|Stores provider code value.|
|environment|varchar(16)|UNIQUE, NOT NULL|Stores environment value.|
|status|varchar(24)|NOT NULL|Operational lifecycle state (varchar(24)).|
|credential_reference|varchar(255)|NULL|Stores credential reference value.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|
|public_reference|varchar(36)|UNIQUE, NOT NULL|Unique external public reference identifier.|

:::

### 3.100 ota_channel_mappings

Maps internal workshop packages and slots to external OTA listing IDs.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|connection_id|bigint|FK (ota_channel_connections.id), UNIQUE, NOT NULL|Foreign key referencing ota_channel_connections(id).|
|offer_reference|varchar(128)|NOT NULL|Stores offer reference value.|
|session_reference|varchar(128)|UNIQUE, NOT NULL|Stores session reference value.|
|location_reference|varchar(128)|NOT NULL|Stores location reference value.|
|external_product_id|varchar(255)|UNIQUE, NOT NULL|Stores external product id value.|
|external_option_id|varchar(255)|UNIQUE, NOT NULL|Stores external option id value.|
|status|varchar(32)|NOT NULL|Operational lifecycle state (varchar(32)).|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|
|public_reference|varchar(36)|UNIQUE, NOT NULL|Unique external public reference identifier.|
|version|bigint|NOT NULL, DEFAULT 1|Optimistic concurrency locking version counter.|

:::

### 3.101 ota_channel_price_snapshots

Tracks syndicated pricing and commission schedules agreed with OTA channels.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|mapping_id|bigint|FK (ota_channel_mappings.id), NOT NULL|Foreign key referencing ota_channel_mappings(id).|
|retail_amount_minor|bigint|NOT NULL|Monetary value or calculated fee (retail amount minor).|
|target_net_amount_minor|bigint|NOT NULL|Monetary value or calculated fee (target net amount minor).|
|commission_amount_minor|bigint|NULL|Monetary value or calculated fee (commission amount minor).|
|currency_code|varchar(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|terms_reference|varchar(255)|NOT NULL|Stores terms reference value.|
|effective_from|datetime(6)|NOT NULL|Stores effective from value.|
|effective_to|datetime(6)|NULL|Stores effective to value.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|public_reference|varchar(36)|UNIQUE, NOT NULL|Unique external public reference identifier.|

:::

### 3.102 ota_channel_bookings

Stores booking reservations ingested from external OTA partners.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|mapping_id|bigint|FK (ota_channel_mappings.id), UNIQUE, NOT NULL|Foreign key referencing ota_channel_mappings(id).|
|external_booking_reference|varchar(255)|UNIQUE, NOT NULL|Stores external booking reference value.|
|provider_status|varchar(32)|NOT NULL|Stores provider status value.|
|participant_count|int|NOT NULL|Numerical count, quota, or capacity (participant count).|
|mland_booking_reference|varchar(128)|NULL|Stores mland booking reference value.|
|sales_channel_snapshot|varchar(32)|NOT NULL|Serialized JSON or snapshot data for sales channel snapshot.|
|displayed_policy_reference|varchar(128)|NOT NULL|Stores displayed policy reference value.|
|displayed_policy_version|varchar(64)|NOT NULL|Stores displayed policy version value.|
|booking_cutoff_at|datetime(6)|NULL|Timestamp recording when booking cutoff at.|
|booking_cutoff_timezone|varchar(64)|NULL|Stores booking cutoff timezone value.|
|retail_amount_minor|bigint|NOT NULL|Monetary value or calculated fee (retail amount minor).|
|target_net_amount_minor|bigint|NOT NULL|Monetary value or calculated fee (target net amount minor).|
|commission_amount_minor|bigint|NULL|Monetary value or calculated fee (commission amount minor).|
|fee_amount_minor|bigint|NULL|Monetary value or calculated fee (fee amount minor).|
|accepted_amount_minor|bigint|NULL|Monetary value or calculated fee (accepted amount minor).|
|currency_code|varchar(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|
|public_reference|varchar(36)|UNIQUE, NOT NULL|Unique external public reference identifier.|

:::

### 3.103 ota_booking_ticket_links

Links ingested OTA bookings with internally issued admittance tickets.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|channel_booking_id|bigint|FK (ota_channel_bookings.id), UNIQUE, NOT NULL|Foreign key referencing ota_channel_bookings(id).|
|participant_ordinal|int|UNIQUE, NOT NULL|Stores participant ordinal value.|
|operations_ticket_reference|varchar(128)|UNIQUE, NOT NULL|Stores operations ticket reference value.|
|linked_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Timestamp recording when linked at.|

:::

### 3.104 ota_addon_bill_links

Associates in-workshop add-on charges with external OTA booking references.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|channel_booking_id|bigint|FK (ota_channel_bookings.id), UNIQUE, NOT NULL|Foreign key referencing ota_channel_bookings(id).|
|settlement_quote_reference|varchar(128)|UNIQUE, NOT NULL|Stores settlement quote reference value.|
|settlement_bill_reference|varchar(128)|NULL|Stores settlement bill reference value.|
|amount_minor|bigint|NOT NULL|Monetary value or calculated fee (amount minor).|
|currency_code|varchar(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|status|varchar(24)|NOT NULL|Operational lifecycle state (varchar(24)).|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|
|addon_type|varchar(32)|NULL|Stores addon type value.|
|item_code|varchar(100)|NULL|Stores item code value.|
|item_description|varchar(500)|NULL|Stores item description value.|
|customer_consent_reference|varchar(128)|NULL|Stores customer consent reference value.|
|accepted_at|datetime(6)|NULL|Timestamp recording when accepted at.|

:::

### 3.105 ota_customer_payment_evidence

Records payment confirmation vouchers and settlement tokens provided by OTAs.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|channel_booking_id|bigint|FK (ota_channel_bookings.id), UNIQUE, NOT NULL|Foreign key referencing ota_channel_bookings(id).|
|external_evidence_reference|varchar(255)|UNIQUE, NOT NULL|Stores external evidence reference value.|
|evidence_kind|varchar(24)|NOT NULL|Stores evidence kind value.|
|evidence_status|varchar(24)|NOT NULL|Stores evidence status value.|
|amount_minor|bigint|NOT NULL|Monetary value or calculated fee (amount minor).|
|currency_code|varchar(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|occurred_at|datetime(6)|NOT NULL|Timestamp recording when occurred at.|
|recorded_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Timestamp recording when recorded at.|
|correlation_reference|varchar(128)|NULL|Stores correlation reference value.|

:::

### 3.106 ota_delivery_attempts

Logs outgoing webhook transmissions and API sync calls to OTA partner endpoints.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|outbound_change_id|bigint|FK (ota_outbound_changes.id), UNIQUE, NOT NULL|Foreign key referencing ota_outbound_changes(id).|
|attempt_number|int|UNIQUE, NOT NULL|Stores attempt number value.|
|started_at|datetime(6)|NOT NULL|Timestamp recording when started at.|
|completed_at|datetime(6)|NULL|Timestamp recording when completed at.|
|outcome|varchar(24)|NOT NULL|Stores outcome value.|
|provider_correlation_reference|varchar(255)|NULL|Stores provider correlation reference value.|
|failure_category|varchar(64)|NULL|Stores failure category value.|

:::

### 3.107 ota_inbound_events

Ingests raw webhook events and payload messages received from OTA gateways.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|connection_id|bigint|FK (ota_channel_connections.id), UNIQUE, NOT NULL|Foreign key referencing ota_channel_connections(id).|
|external_event_reference|varchar(255)|NULL|Stores external event reference value.|
|payload_fingerprint|varchar(64)|NOT NULL|Serialized JSON or snapshot data for payload fingerprint.|
|dedupe_key|varchar(64)|UNIQUE, NOT NULL|Stores dedupe key value.|
|event_kind|varchar(32)|NOT NULL|Stores event kind value.|
|authentication_result|varchar(24)|NOT NULL|Stores authentication result value.|
|processing_status|varchar(24)|NOT NULL|Stores processing status value.|
|outcome_reference|varchar(128)|NULL|Stores outcome reference value.|
|received_at|datetime(6)|NOT NULL|Timestamp recording when received at.|
|processed_at|datetime(6)|NULL|Timestamp recording when processed at.|

:::

### 3.108 ota_outbound_changes

Queues schedule, capacity, and pricing changes to be published to OTA channels.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|mapping_id|bigint|FK (ota_channel_mappings.id), UNIQUE, NOT NULL|Foreign key referencing ota_channel_mappings(id).|
|source_version|bigint|UNIQUE, NOT NULL|Stores source version value.|
|source_change_reference|varchar(128)|NOT NULL|Stores source change reference value.|
|change_kind|varchar(32)|UNIQUE, NOT NULL|Stores change kind value.|
|canonical_snapshot|json|NOT NULL|Serialized JSON or snapshot data for canonical snapshot.|
|status|varchar(24)|NOT NULL|Operational lifecycle state (varchar(24)).|
|accepted_at|datetime(6)|NOT NULL|Timestamp recording when accepted at.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.109 ota_control_outbox

Transactional outbox for reliable dispatch of OTA management commands.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|operation_reference|varchar(36)|UNIQUE, NOT NULL|Stores operation reference value.|
|mapping_id|bigint|FK (ota_channel_mappings.id), NOT NULL|Foreign key referencing ota_channel_mappings(id).|
|command_kind|varchar(32)|NOT NULL|Stores command kind value.|
|actor_account_reference|varchar(128)|UNIQUE, NOT NULL|Numerical count, quota, or capacity (actor account reference).|
|reason|varchar(1000)|NOT NULL|Documented justification or reason for reason.|
|idempotency_key_hash|varchar(64)|UNIQUE, NOT NULL|Cryptographic signature or hash digest for idempotency key hash.|
|request_fingerprint|varchar(64)|NOT NULL|Cryptographic signature or hash digest for request fingerprint.|
|correlation_reference|varchar(36)|NOT NULL|Stores correlation reference value.|
|status|varchar(24)|NOT NULL|Operational lifecycle state (varchar(24)).|
|provider_correlation_reference|varchar(255)|NULL|Stores provider correlation reference value.|
|failure_category|varchar(64)|NULL|Stores failure category value.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|
|delivered_at|datetime(6)|NULL|Timestamp recording when delivered at.|

:::

### 3.110 ota_payout_ledger_entries

Tracks net payout receivables, partner commissions, and platform deductions.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|payout_position_id|bigint|FK (ota_payout_positions.id), UNIQUE, NOT NULL|Foreign key referencing ota_payout_positions(id).|
|entry_kind|varchar(24)|UNIQUE, NOT NULL|Stores entry kind value.|
|amount_minor|bigint|NOT NULL|Monetary value or calculated fee (amount minor).|
|currency_code|varchar(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|external_evidence_reference|varchar(255)|UNIQUE, NOT NULL|Stores external evidence reference value.|
|occurred_at|datetime(6)|NOT NULL|Timestamp recording when occurred at.|
|recorded_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Timestamp recording when recorded at.|
|correlation_reference|varchar(128)|NULL|Stores correlation reference value.|
|reason|varchar(1000)|NULL|Documented justification or reason for reason.|
|related_record_reference|varchar(128)|NULL|Stores related record reference value.|
|public_reference|varchar(36)|UNIQUE, NOT NULL|Unique external public reference identifier.|
|evidence_type|varchar(24)|NULL|Stores evidence type value.|
|allocation_reference|varchar(128)|UNIQUE|Stores allocation reference value.|
|payout_reference|varchar(200)|NULL|Stores payout reference value.|

:::

### 3.111 ota_payout_positions

Maintains aggregate financial balances receivable from each OTA channel.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|channel_booking_id|bigint|FK (ota_channel_bookings.id), NOT NULL|Foreign key referencing ota_channel_bookings(id).|
|price_snapshot_id|bigint|FK (ota_channel_price_snapshots.id)|Foreign key referencing ota_channel_price_snapshots(id).|
|expected_amount_minor|bigint|NOT NULL|Monetary value or calculated fee (expected amount minor).|
|currency_code|varchar(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|status|varchar(24)|NOT NULL|Operational lifecycle state (varchar(24)).|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::

### 3.112 ota_readiness_assessments

Evaluates channel readiness and automated health checks before enabling bookings.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|assessment_reference|varchar(36)|UNIQUE, NOT NULL|Stores assessment reference value.|
|mapping_id|bigint|FK (ota_channel_mappings.id), NOT NULL|Foreign key referencing ota_channel_mappings(id).|
|provider_code|varchar(32)|NOT NULL|Stores provider code value.|
|environment|varchar(16)|NOT NULL|Stores environment value.|
|mapping_fingerprint|varchar(64)|NOT NULL|Cryptographic signature or hash digest for mapping fingerprint.|
|readiness_record_reference|varchar(255)|NOT NULL|Stores readiness record reference value.|
|decision_status|varchar(16)|NOT NULL|Stores decision status value.|
|reason|varchar(500)|NOT NULL|Documented justification or reason for reason.|
|reviewer_account_reference|varchar(128)|NOT NULL|Numerical count, quota, or capacity (reviewer account reference).|
|recorded_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Timestamp recording when recorded at.|

:::

### 3.113 ota_readiness_check_evidence

Stores diagnostic test logs and verification responses for OTA readiness checks.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|assessment_id|bigint|FK (ota_readiness_assessments.id), UNIQUE, NOT NULL|Foreign key referencing ota_readiness_assessments(id).|
|check_code|varchar(48)|UNIQUE, NOT NULL|Stores check code value.|
|outcome|varchar(16)|NOT NULL|Stores outcome value.|
|evidence_reference|varchar(255)|NULL|Stores evidence reference value.|

:::

### 3.114 ota_reconciliation_decisions

Records automated and manual reconciliation resolutions for booking mismatches.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|decision_reference|varchar(36)|UNIQUE, NOT NULL|Stores decision reference value.|
|work_item_id|bigint|FK (ota_reconciliation_work_items.id)|Foreign key referencing ota_reconciliation_work_items(id).|
|mapping_id|bigint|FK (ota_channel_mappings.id)|Foreign key referencing ota_channel_mappings(id).|
|provider_code|varchar(32)|NOT NULL|Stores provider code value.|
|location_reference|varchar(128)|NOT NULL|Stores location reference value.|
|actor_account_reference|varchar(128)|UNIQUE, NOT NULL|Numerical count, quota, or capacity (actor account reference).|
|actor_role|varchar(16)|NOT NULL|Stores actor role value.|
|action_code|varchar(32)|NOT NULL|Stores action code value.|
|action_scope|varchar(32)|NOT NULL|Stores action scope value.|
|reason|varchar(1000)|NOT NULL|Documented justification or reason for reason.|
|outcome|varchar(32)|NOT NULL|Stores outcome value.|
|operation_reference|varchar(36)|UNIQUE, NOT NULL|Stores operation reference value.|
|correlation_reference|varchar(36)|NOT NULL|Stores correlation reference value.|
|idempotency_key_hash|varchar(64)|UNIQUE, NOT NULL|Cryptographic signature or hash digest for idempotency key hash.|
|request_fingerprint|varchar(64)|NOT NULL|Cryptographic signature or hash digest for request fingerprint.|
|source_version|bigint|NULL|Stores source version value.|
|recorded_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Timestamp recording when recorded at.|

:::

### 3.115 ota_reconciliation_work_items

Tracks unresolved discrepancies between internal attendance and OTA reports.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|mapping_id|bigint|FK (ota_channel_mappings.id)|Foreign key referencing ota_channel_mappings(id).|
|inbound_event_id|bigint|FK (ota_inbound_events.id)|Foreign key referencing ota_inbound_events(id).|
|channel_booking_id|bigint|FK (ota_channel_bookings.id)|Foreign key referencing ota_channel_bookings(id).|
|payout_position_id|bigint|FK (ota_payout_positions.id)|Foreign key referencing ota_payout_positions(id).|
|location_reference|varchar(128)|NOT NULL|Stores location reference value.|
|category|varchar(32)|NOT NULL|Stores category value.|
|status|varchar(24)|NOT NULL|Operational lifecycle state (varchar(24)).|
|assigned_manager_reference|varchar(128)|NULL|Stores assigned manager reference value.|
|decision|varchar(64)|NULL|Stores decision value.|
|resolution_reference|varchar(255)|NULL|Stores resolution reference value.|
|audit_reference|varchar(128)|NULL|Stores audit reference value.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|
|public_reference|varchar(36)|UNIQUE, NOT NULL|Unique external public reference identifier.|

:::

### 3.116 workshop_session_ota_snapshots

Maintains synchronized seat availability and lock state sent to OTA channels.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|session_reference|varchar(36)|UNIQUE, NOT NULL|Stores session reference value.|
|location_reference|varchar(64)|NOT NULL|Stores location reference value.|
|scheduled_start_at|datetime(6)|NOT NULL|Timestamp recording when scheduled start at.|
|scheduled_end_at|datetime(6)|NOT NULL|Timestamp recording when scheduled end at.|
|sellable_availability|int|NOT NULL|Stores sellable availability value.|
|sale_state|varchar(32)|NOT NULL|Stores sale state value.|
|source_version|bigint|UNIQUE, NOT NULL|Stores source version value.|
|change_reference|varchar(64)|NOT NULL|Stores change reference value.|
|effective_at|datetime(6)|NOT NULL|Timestamp recording when effective at.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|

:::

### 3.117 event_publication

Spring Modulith persistent event publication log guaranteeing at-least-once event delivery.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|binary(16)|PK, NOT NULL|Primary key unique identifier.|
|publication_date|datetime(6)|NOT NULL|Calendar date for publication date.|
|completion_date|datetime(6)|NULL|Calendar date for completion date.|
|event_type|varchar(255)|NOT NULL|Stores event type value.|
|listener_id|varchar(255)|NOT NULL|Stores listener id value.|
|serialized_event|longtext|NOT NULL|Stores serialized event value.|

:::

### 3.118 facade_command_idempotency_records

Global idempotency ledger for boundary API commands across architectural facades.

::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|bigint unsigned|PK, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|facade_action|varchar(80)|UNIQUE, NOT NULL|Stores facade action value.|
|operation_reference|varchar(64)|UNIQUE, NOT NULL|Stores operation reference value.|
|correlation_reference|varchar(64)|NOT NULL|Stores correlation reference value.|
|semantic_fingerprint|varchar(255)|NOT NULL|Cryptographic signature or hash digest for semantic fingerprint.|
|lifecycle_status|enum('IN_PROGRESS','COMPLETED')|NOT NULL, DEFAULT IN_PROGRESS|Stores lifecycle status value.|
|claim_version|bigint unsigned|NOT NULL, DEFAULT 1|Stores claim version value.|
|lease_expires_at|datetime(6)|NOT NULL|Timestamp recording when lease expires at.|
|claimed_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Timestamp recording when claimed at.|
|completed_at|datetime(6)|NULL|Timestamp recording when completed at.|
|outcome_status|enum('ACCEPTED','REJECTED')|NULL|Stores outcome status value.|
|rejection_reason|varchar(64)|NULL|Documented justification or reason for rejection reason.|
|response_payload|json|NULL|Serialized JSON or snapshot data for response payload.|
|created_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|datetime(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|

:::
