## 3. Database Design

{{r4-db width=100% align=center}}

The ERD is intentionally divided into three focused diagrams so that table names, keys and relationships remain readable in the published report.

### 3.0a Core Catalogue and Workshop ERD

(Diagram pending upload to Drive: r4-db-01-core-catalogue-workshop)

### 3.0b Design and Customer Interaction ERD

(Diagram pending upload to Drive: r4-db-02-design-customer-interaction)

### 3.0c Commerce and Operations ERD

(Diagram pending upload to Drive: r4-db-03-commerce-operations)

Each diagram uses orthogonal relationship routing. A relationship must not pass through an entity or label, and no more than two relationships may cross the same relationship line. Cross-context relationships are represented by a labelled external-reference node instead of repeated long crossing lines.

### Table Inventory

::: {.docx-table width="100%" columns="6%,26%,34%,34%" repeat-header="false"}

|No.|Feature|Table|Description|
|---|---|---|---|
|1|001 Account Management|accounts|Stores every system account linked to Firebase Authentication, with its role and status.|
|2|001 Account Management|account_profiles|Stores the full name and phone number of an account.|
|3|001 Account Management|account_location_assignments|Assigns Staff and Manager accounts to the branches they work at.|
|4|001 Account Management|member_addresses|Stores the shipping addresses of a Member.|
|5|002 Product Management|catalogue_categories|Stores product categories used to group ready rings.|
|6|002 Product Management|catalogue_products|Stores ready-ring products and their publish state.|
|7|002 Product Management|catalogue_product_images|Stores the ordered images of a product.|
|8|002 Product Management|catalogue_variants|Stores the ring-size variants of a product with SKU, applied price and total stock.|
|9|002 Product Management|catalogue_variant_branch_stock|Stores the stock of each variant at each branch.|
|10|002 Product Management|catalogue_price_proposals|Stores price proposals for a variant and the Manager decision.|
|11|003.1 Workshop Booking|booking_groups|Stores a direct booking group with its buyer, status, payment mode and hold expiry.|
|12|003.1 Workshop Booking|group_payment_attempts|Stores the VNPay payment attempts of a booking group.|
|13|003.1 Workshop Booking|booking_group_lines|Stores the package lines of a booking group.|
|14|003.1 Workshop Booking|booking_group_line_sessions|Stores the sessions chosen for a booking line and their capacity hold.|
|15|003.1 Workshop Booking|ticket_bookings|Stores one ticket (one participant) in a booking group.|
|16|003.2 Workshop Operations|workshop_locations|Stores the branches where workshops run and ready rings are sold.|
|17|003.2 Workshop Operations|workshop_slots|Stores the reusable time slots of a branch.|
|18|003.2 Workshop Operations|workshop_sessions|Stores workshop sessions with date, capacity and lifecycle state.|
|19|003.2 Workshop Operations|workshop_packages|Stores workshop packages with price, participant rules and lifecycle state.|
|20|003.2 Workshop Operations|workshop_addons|Stores the fixed add-ons sold for a package at a branch.|
|21|003.2 Workshop Operations|session_staff_assignments|Assigns Staff to a session; the assignment grants access to that session.|
|22|003.2 Workshop Operations|attendance_tickets|Stores the operations ticket used for QR check-in of one participant.|
|23|003.3 Workshop Settlement and Changes|settlement_final_bills|Stores the folio of a ticket with remaining amount, handover and debt state.|
|24|003.3 Workshop Settlement and Changes|settlement_charges|Stores accepted add-on charges posted to a folio.|
|25|003.3 Workshop Settlement and Changes|settlement_folio_collections|Stores payments collected on a folio by VNPay, cash or bank transfer.|
|26|003.3 Workshop Settlement and Changes|settlement_ticket_cancellations|Stores ticket cancellations and their benefit outcome.|
|27|003.3 Workshop Settlement and Changes|settlement_ticket_changes|Stores session changes applied to a ticket.|
|28|003.3 Workshop Settlement and Changes|settlement_refund_requests|Stores refund requests and their approval, transfer and confirmation steps.|
|29|003.4 Workshop OTA Connectivity|ota_channel_connections|Stores the connection configuration of each OTA provider.|
|30|003.4 Workshop OTA Connectivity|ota_channel_mappings|Links Mland sessions to OTA provider products.|
|31|003.4 Workshop OTA Connectivity|ota_channel_bookings|Stores bookings received from an OTA provider.|
|32|004 Design Request and Custom Order|design_requests|Stores custom design requests submitted by an active Member and their human-review status. Guest users have no private request record.|
|33|004 Design Request and Custom Order|design_request_images|Stores the reference images of a design request with their quality result.|
|34|004 Design Request and Custom Order|custom_orders|Stores custom ring orders created from an accepted design request.|
|35|004 Design Request and Custom Order|custom_order_quotes|Stores the quotes of a custom order and the Manager approval.|
|36|006 Loyalty and Promotion|loyalty_wallet|Stores the point balance and held points of a Member.|
|37|006 Loyalty and Promotion|loyalty_point_ledger|Stores every point movement (earn, hold, consume, release, reverse) of a wallet.|
|38|006 Loyalty and Promotion|loyalty_policy|Stores immutable revisions of the earn and redeem policy.|
|39|006 Loyalty and Promotion|promotion_program|Stores voucher and automatic-discount programs.|
|40|006 Loyalty and Promotion|promotion_member_program|Stores vouchers saved by a Member and their usage counts.|
|41|007 Notification Management|notification_template_definition|Stores the email templates the system can send.|
|42|007 Notification Management|notification_request|Stores email notification requests and their delivery state.|
|43|007 Notification Management|notification_delivery_attempt|Stores each attempt to send a notification email.|
|44|008 Retail Order and Fulfillment|retail_carts|Stores the shopping cart of a Member.|
|45|008 Retail Order and Fulfillment|retail_cart_lines|Stores the variants, branch and quantity in a cart.|
|46|008 Retail Order and Fulfillment|retail_grouped_checkouts|Stores one Member payment that covers one or more branch orders.|
|47|008 Retail Order and Fulfillment|retail_orders|Stores a retail order fulfilled by one branch.|
|48|008 Retail Order and Fulfillment|retail_order_items|Stores the frozen variant, quantity and price of an order line.|
|49|008 Retail Order and Fulfillment|retail_shipments|Stores the carrier shipment of a delivery order.|
|50|011 Review and 013 Consultation Chat|reviews|Stores customer reviews of a workshop or product.|
|51|011 Review and 013 Consultation Chat|review_replies|Stores the Manager reply to a review.|
|52|011 Review and 013 Consultation Chat|consultation_conversations|Stores consultation conversations between a Member and Staff.|
|53|011 Review and 013 Consultation Chat|consultation_messages|Stores the immutable messages of a conversation.|
|54|012 Workshop Ring Design Configuration|ring_designs|Stores immutable ring design versions created in the configurator.|
|55|012 Workshop Ring Design Configuration|ring_materials|Stores the ring material options.|
|56|012 Workshop Ring Design Configuration|ring_shapes|Stores the ring shape options.|
|57|012 Workshop Ring Design Configuration|ring_gemstones|Stores the gemstone options; selected gemstones are kept in ring_designs.component_snapshot.|
|58|012 Workshop Ring Design Configuration|ring_attachments|Stores the attachment options; selected attachments are kept in ring_designs.component_snapshot.|

:::

### 3.1 accounts

Stores every system account linked to one Firebase UID, with one fixed role and its lifecycle status. A Staff or Manager remains PENDING_SETUP until the current invitation is consumed, the intended email is verified and current active location scope is valid. Invitation versions require a separate durable record of Account ID, intended email, secret digest, key version, created/expiry times and use state; the raw secret is never stored. The column-level invitation design remains to be finalized before implementation.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT UNSIGNED|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|firebase_uid|VARCHAR(128)|NOT NULL, UNIQUE|Unique Firebase Authentication user identifier.|
|login_email|VARCHAR(320)||Contact or authentication email address.|
|email_verified_at|DATETIME(6)||Timestamp recording when email verified at.|
|role|ENUM('MEMBER','STAFF','MANAGER','CARRIER','ADMIN')|NOT NULL, DEFAULT MEMBER|One fixed platform role; no configurable role hierarchy.|
|status|ENUM('ACTIVE','PENDING_SETUP','SUSPENDED')|NOT NULL, DEFAULT ACTIVE|Operational lifecycle state (enum('active','pending_setup','suspended')).|
|resume_status|ENUM('ACTIVE','PENDING_SETUP')||Stores resume status value.|
|revocation_pending|TINYINT(1)|NOT NULL, DEFAULT 0|Stores revocation pending value.|
|version|BIGINT|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|session_revocation_confirmed_at|DATETIME(6)||Timestamp recording when session revocation confirmed at.|
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record creation timestamp (UTC).|
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record last update timestamp (UTC).|

:::

### 3.2 account_profiles

Stores the full name and phone number of an account.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|account_id|BIGINT UNSIGNED|PRIMARY KEY, FK → accounts(id)|Foreign key referencing accounts(id).|
|full_name|VARCHAR(400)||Human-readable name or title.|
|phone|VARCHAR(30)||Contact telephone number.|
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record last update timestamp (UTC).|

:::

### 3.3 account_location_assignments

Assigns Staff and Manager accounts to the branches they work at.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT UNSIGNED|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|account_id|BIGINT UNSIGNED|NOT NULL, FK → accounts(id)|Foreign key referencing accounts(id).|
|location_id|BIGINT UNSIGNED|NOT NULL, FK → workshop_locations(id) (logical)|Logical reference to workshop_locations(id); no database constraint.|
|assigned_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Timestamp recording when assigned at.|
|active|TINYINT(1)|NOT NULL, DEFAULT 1|Active status indicator (1=active, 0=inactive).|

:::

### 3.4 member_addresses

Stores the shipping addresses of a Member.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT UNSIGNED|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|account_id|BIGINT UNSIGNED|NOT NULL, FK → accounts(id)|References accounts(id).|
|recipient_name|VARCHAR(255)|NOT NULL||
|phone_number|VARCHAR(15)|NOT NULL||
|province_code|VARCHAR(64)|NOT NULL||
|province_name|VARCHAR(128)|NOT NULL||
|commune_code|VARCHAR(64)|NOT NULL||
|commune_name|VARCHAR(128)|NOT NULL||
|commune_type|VARCHAR(32)|NOT NULL||
|detail_address|VARCHAR(255)|NOT NULL||
|directory_version|VARCHAR(64)|NOT NULL||
|address_type|VARCHAR(16)|NOT NULL, DEFAULT HOME||
|is_default|TINYINT(1)|NOT NULL, DEFAULT 0||
|version|BIGINT|NOT NULL, DEFAULT 0||
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||

:::

### 3.5 catalogue_categories

Stores product categories used to group ready rings.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|name|VARCHAR(200)|NOT NULL|Human-readable name or title.|
|name_key|VARBINARY(1600)|NOT NULL, UNIQUE|Stores name key value.|
|description|TEXT||Descriptive details, business remarks, or explanatory notes.|
|active|TINYINT(1)|NOT NULL|Active status indicator (1=active, 0=inactive).|
|version|BIGINT|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|created_at|DATETIME(6)|NOT NULL|Record creation timestamp (UTC).|
|updated_at|DATETIME(6)|NOT NULL|Record last update timestamp (UTC).|

:::

### 3.6 catalogue_products

Stores ready-ring products and their publish state.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|name|VARCHAR(200)|NOT NULL|Human-readable name or title.|
|description|TEXT|NOT NULL|Descriptive details, business remarks, or explanatory notes.|
|category_id|BIGINT|NOT NULL, FK → catalogue_categories(id)|Foreign key referencing catalogue_categories(id).|
|published|TINYINT(1)|NOT NULL|Stores published value.|
|first_published_at|DATETIME(6)||Timestamp recording when first published at.|
|deleted_at|DATETIME(6)||Timestamp recording when deleted at.|
|deleted_by|VARCHAR(128)||Stores deleted by value.|
|content_version|BIGINT|NOT NULL, DEFAULT 0|Stores content version value.|
|version|BIGINT|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|created_at|DATETIME(6)|NOT NULL|Record creation timestamp (UTC).|
|updated_at|DATETIME(6)|NOT NULL|Record last update timestamp (UTC).|

:::

### 3.7 catalogue_product_images

Stores the ordered images of a product.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|product_id|BIGINT|NOT NULL, FK → catalogue_products(id)|Foreign key referencing catalogue_products(id).|
|media_object_id|CHAR(36)|NOT NULL, FK → catalogue_media_objects(id)|Foreign key referencing catalogue_media_objects(id).|
|alt_text|VARCHAR(200)|NOT NULL|Stores alt text value.|
|display_position|INT|NOT NULL|Stores display position value.|
|version|BIGINT|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|

:::

### 3.8 catalogue_variants

Stores the ring-size variants of a product with SKU, applied price and total stock.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|product_id|BIGINT|NOT NULL, FK → catalogue_products(id)|Foreign key referencing catalogue_products(id).|
|ring_size|SMALLINT|NOT NULL|Stores ring size value.|
|sku|VARCHAR(25)|NOT NULL, UNIQUE|Stores sku value.|
|remaining_unsold|DECIMAL(19,0)|NOT NULL|Stores remaining unsold value.|
|sale_enabled|TINYINT(1)|NOT NULL|Stores sale enabled value.|
|applied_price|DECIMAL(19,0)||Monetary value or calculated fee (applied price).|
|applied_proposal_id|BIGINT|FK → catalogue_price_proposals(id)|Foreign key referencing catalogue_price_proposals(id).|
|applied_sequence|BIGINT|NOT NULL, DEFAULT 0|Stores applied sequence value.|
|pending_proposal_id|BIGINT|FK → catalogue_price_proposals(id)|Foreign key referencing catalogue_price_proposals(id).|
|version|BIGINT|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|

:::

### 3.9 catalogue_variant_branch_stock

Stores the stock of each variant at each branch.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|variant_id|BIGINT|NOT NULL, FK → catalogue_variants(id)|References catalogue_variants(id).|
|location_id|BIGINT|NOT NULL, FK → workshop_locations(id) (logical)|Logical reference to workshop_locations(id); no database constraint.|
|remaining_unsold|DECIMAL(19,0)|NOT NULL||
|sale_enabled|TINYINT(1)|NOT NULL, DEFAULT 1||
|version|BIGINT|NOT NULL, DEFAULT 0||
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||

:::

### 3.10 catalogue_price_proposals

Stores price proposals for a variant and the Manager decision.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|variant_id|BIGINT|NOT NULL, FK → catalogue_variants(id)|Foreign key referencing catalogue_variants(id).|
|review_id|BIGINT|FK → catalogue_product_reviews(id)|Foreign key referencing catalogue_product_reviews(id).|
|revision|BIGINT|NOT NULL|Stores revision value.|
|version|BIGINT|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|state|VARCHAR(20)|NOT NULL|Operational lifecycle state (varchar(20)).|
|effective|TINYINT(1)|NOT NULL|Stores effective value.|
|proposed_amount|DECIMAL(19,0)|NOT NULL|Monetary value or calculated fee (proposed amount).|
|approved_amount|DECIMAL(19,0)||Monetary value or calculated fee (approved amount).|
|currency|CHAR(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|base_applied_sequence|BIGINT|NOT NULL|Stores base applied sequence value.|
|terms_digest|VARCHAR(64)|NOT NULL|Cryptographic signature or hash digest for terms digest.|
|submitted_by|VARCHAR(128)||Stores submitted by value.|
|submitted_at|DATETIME(6)||Timestamp recording when submitted at.|
|decided_by|VARCHAR(128)||Stores decided by value.|
|decided_at|DATETIME(6)||Timestamp recording when decided at.|
|reason|VARCHAR(2000)||Documented justification or reason for reason.|
|supersedes_id|BIGINT|FK → catalogue_price_proposals(id)|Foreign key referencing catalogue_price_proposals(id).|
|submitter_branches|JSON|||

:::

### 3.11 booking_groups

Stores a direct booking group with its buyer, status, payment mode and hold expiry.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT UNSIGNED|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|group_code|VARCHAR(32)|NOT NULL, UNIQUE||
|buyer_member_account_id|BIGINT UNSIGNED|FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|contact_email|VARCHAR(254)|NOT NULL||
|contact_phone|VARCHAR(32)|NOT NULL||
|group_status|ENUM('PENDING_PAYMENT','CONFIRMED','EXPIRED','CANCELLED')|NOT NULL, DEFAULT PENDING_PAYMENT||
|payment_mode|ENUM('VNPAY','INTERNAL_FREE','INTERNAL_DEFERRED')|NOT NULL||
|group_collection_vnd|BIGINT|NOT NULL||
|hold_expires_at|DATETIME(6)|||
|payment_deadline_at|DATETIME(6)|||
|voucher_use_reference|VARCHAR(128)|||
|loyalty_policy_revision|BIGINT|||
|confirmed_at|DATETIME(6)|||
|version|BIGINT|NOT NULL, DEFAULT 0||
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||

:::

### 3.12 group_payment_attempts

Stores the VNPay payment attempts of a booking group.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT UNSIGNED|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|group_id|BIGINT UNSIGNED|NOT NULL, FK → booking_groups(id)|References booking_groups(id).|
|attempt_reference|VARCHAR(64)|NOT NULL, UNIQUE||
|merchant_reference|VARCHAR(64)|NOT NULL, UNIQUE||
|amount_vnd|BIGINT|NOT NULL||
|provider_create_date|VARCHAR(14)|||
|payment_url_snapshot|VARCHAR(2048)|||
|attempt_status|ENUM('PENDING','SUCCESS','FAILED','EXPIRED')|NOT NULL, DEFAULT PENDING||
|expires_at|DATETIME(6)|NOT NULL||
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||

:::

### 3.13 booking_group_lines

Stores the package lines of a booking group.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT UNSIGNED|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|group_id|BIGINT UNSIGNED|NOT NULL, FK → booking_groups(id)|References booking_groups(id).|
|line_index|INT|NOT NULL||
|line_reference|VARCHAR(48)|NOT NULL, UNIQUE||
|package_id|BIGINT UNSIGNED|NOT NULL, FK → workshop_packages(id) (logical)|Logical reference to workshop_packages(id); no database constraint.|
|package_name_snapshot|VARCHAR(255)|NOT NULL||
|price_per_participant_vnd|BIGINT|NOT NULL||
|ticket_count|INT|NOT NULL||
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||

:::

### 3.14 booking_group_line_sessions

Stores the sessions chosen for a booking line and their capacity hold.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT UNSIGNED|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|line_id|BIGINT UNSIGNED|NOT NULL, FK → booking_group_lines(id)|References booking_group_lines(id).|
|ordinal|INT|NOT NULL||
|session_reference|VARCHAR(36)|NOT NULL, FK → workshop_sessions(reference) (logical)|Logical reference to workshop_sessions(reference); no database constraint.|
|location_reference|BIGINT UNSIGNED|FK → workshop_locations(id) (logical)|Logical reference to workshop_locations(id); no database constraint.|
|scheduled_start|DATETIME(6)|||
|hold_reference|VARCHAR(36)|||
|hold_status|ENUM('RESERVED','COMMITTED','RELEASED')|NOT NULL, DEFAULT RESERVED||

:::

### 3.15 ticket_bookings

Stores one ticket (one participant) in a booking group.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT UNSIGNED|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|ticket_code|VARCHAR(40)|NOT NULL, UNIQUE||
|group_id|BIGINT UNSIGNED|NOT NULL, FK → booking_groups(id)|References booking_groups(id).|
|line_index|INT|NOT NULL||
|participant_ordinal|INT|NOT NULL||
|package_id|BIGINT UNSIGNED|NOT NULL, FK → workshop_packages(id) (logical)|Logical reference to workshop_packages(id); no database constraint.|
|package_name_snapshot|VARCHAR(255)|NOT NULL||
|operations_ticket_reference|VARCHAR(36)|UNIQUE||
|qr_reference|VARCHAR(128)|UNIQUE||
|ticket_status|ENUM('PENDING_PAYMENT','CONFIRMED','EXPIRED','CANCELLED','COMPLETED','NO_SHOW')|NOT NULL, DEFAULT PENDING_PAYMENT||
|financial_status|ENUM('UNPAID','DEPOSIT_PAID','FULLY_PAID','REFUND_PENDING','REFUNDED')|NOT NULL, DEFAULT UNPAID||
|confirmed_at|DATETIME(6)|||
|customer_changes_used|INT|NOT NULL, DEFAULT 0||
|version|BIGINT|NOT NULL, DEFAULT 0||
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||

:::

### 3.16 workshop_locations

Stores the branches where workshops run and ready rings are sold.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT UNSIGNED|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|code|VARCHAR(64)|NOT NULL, UNIQUE|Unique human-readable business identifier code.|
|display_name|VARCHAR(255)|NOT NULL|Human-readable name or title.|
|address_snapshot|VARCHAR(1000)|NOT NULL|Serialized JSON or snapshot data for address snapshot.|
|active|TINYINT(1)|NOT NULL, DEFAULT 1|Active status indicator (1=active, 0=inactive).|
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record creation timestamp (UTC).|
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record last update timestamp (UTC).|

:::

### 3.17 workshop_slots

Stores the reusable time slots of a branch.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT UNSIGNED|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|reference|VARCHAR(36)|NOT NULL, UNIQUE|Unique external public reference identifier.|
|location_id|BIGINT UNSIGNED|NOT NULL, FK → workshop_locations(id)|Foreign key referencing workshop_locations(id).|
|label|VARCHAR(100)|NOT NULL|Stores label value.|
|start_time|TIME|NOT NULL|Stores start time value.|
|end_time|TIME|NOT NULL|Stores end time value.|
|active|TINYINT(1)|NOT NULL, DEFAULT 1|Active status indicator (1=active, 0=inactive).|
|version|BIGINT|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record creation timestamp (UTC).|
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record last update timestamp (UTC).|

:::

### 3.18 workshop_sessions

Stores workshop sessions with date, capacity and lifecycle state.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT UNSIGNED|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|reference|VARCHAR(36)|NOT NULL, UNIQUE|Unique external public reference identifier.|
|location_id|BIGINT UNSIGNED|NOT NULL, FK → workshop_locations(id)|Foreign key referencing workshop_locations(id).|
|slot_id|BIGINT UNSIGNED|NOT NULL, FK → workshop_slots(id)|Foreign key referencing workshop_slots(id).|
|workshop_date|DATE|NOT NULL|Calendar date for workshop date.|
|scheduled_start_at|DATETIME(6)|NOT NULL|Timestamp recording when scheduled start at.|
|scheduled_end_at|DATETIME(6)|NOT NULL|Timestamp recording when scheduled end at.|
|check_in_opens_at|DATETIME(6)|NOT NULL|Timestamp recording when check in opens at.|
|check_in_opening_source|ENUM('DEFAULT','MANAGER_SET')|NOT NULL, DEFAULT DEFAULT|Stores check in opening source value.|
|capacity|INT|NOT NULL|Numerical count, quota, or capacity (capacity).|
|state|ENUM('DRAFT','PUBLISHED','SALES_PAUSED','IN_PROGRESS','ATTENDANCE_FINALIZED','CANCELLATION_REQUESTED','CANCELLED')|NOT NULL, DEFAULT DRAFT|Operational lifecycle state (enum('draft','published','sales_paused','in_progress','attendance_finalized','cancellation_requested','cancelled')).|
|version|BIGINT|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|cancellation_handoff_reference|VARCHAR(128)||Stores cancellation handoff reference value.|
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record creation timestamp (UTC).|
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record last update timestamp (UTC).|
|attendance_finalized_at|DATETIME(6)||Timestamp recording when attendance finalized at.|
|reschedule_quota_override|INT|||
|reschedule_quota_used|INT|NOT NULL, DEFAULT 0||

:::

### 3.19 workshop_packages

Stores workshop packages with price, participant rules and lifecycle state.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|name|LONGTEXT|NOT NULL|Human-readable name or title.|
|description|LONGTEXT|NOT NULL|Descriptive details, business remarks, or explanatory notes.|
|public_price|BIGINT|NOT NULL|Monetary value or calculated fee (public price).|
|min_participants|INT|NOT NULL|Stores min participants value.|
|max_participants|INT|NOT NULL|Stores max participants value.|
|required_session_count|INT|NOT NULL|Numerical count, quota, or capacity (required session count).|
|same_location_required|TINYINT(1)|NOT NULL|Stores same location required value.|
|minimum_same_day_slot_gap|INT|NOT NULL|Stores minimum same day slot gap value.|
|state|VARCHAR(16)|NOT NULL|Operational lifecycle state (varchar(16)).|
|created_at|DATETIME(6)|NOT NULL|Record creation timestamp (UTC).|
|updated_at|DATETIME(6)|NOT NULL|Record last update timestamp (UTC).|
|required_foundry_working_days_between_sessions|INT|NOT NULL, DEFAULT 0||

:::

### 3.20 workshop_addons

Stores the fixed add-ons sold for a package at a branch.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|reference|VARCHAR(36)|NOT NULL, UNIQUE|Unique external public reference identifier.|
|package_id|BIGINT|NOT NULL, FK → workshop_packages(id)|Foreign key referencing workshop_packages(id).|
|location_id|BIGINT UNSIGNED|NOT NULL, FK → workshop_locations(id)|Foreign key referencing workshop_locations(id).|
|description|VARCHAR(200)|NOT NULL|Descriptive details, business remarks, or explanatory notes.|
|price_vnd|BIGINT|NOT NULL|Monetary value or calculated fee (price vnd).|
|state|VARCHAR(16)|NOT NULL|Operational lifecycle state (varchar(16)).|
|created_at|DATETIME(6)|NOT NULL|Record creation timestamp (UTC).|
|updated_at|DATETIME(6)|NOT NULL|Record last update timestamp (UTC).|

:::

### 3.21 session_staff_assignments

Assigns Staff to a session; the assignment grants access to that session.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT UNSIGNED|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|session_id|BIGINT UNSIGNED|NOT NULL, FK → workshop_sessions(id)|Foreign key referencing workshop_sessions(id).|
|account_id|BIGINT UNSIGNED|NOT NULL, FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|assigned_by_id|BIGINT UNSIGNED|NOT NULL, FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|active|TINYINT(1)|NOT NULL, DEFAULT 1|Active status indicator (1=active, 0=inactive).|
|assigned_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Timestamp recording when assigned at.|
|revoked_at|DATETIME(6)||Timestamp recording when revoked at.|

:::

### 3.22 attendance_tickets

Stores the operations ticket used for QR check-in of one participant.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT UNSIGNED|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|reference|VARCHAR(36)|NOT NULL, UNIQUE|Unique external public reference identifier.|
|booking_reference|VARCHAR(64)|NOT NULL|Unique external public reference identifier.|
|ticket_sequence|INT|NOT NULL|Stores ticket sequence value.|
|ticket_code|VARCHAR(32)|NOT NULL, UNIQUE, FK → ticket_bookings(ticket_code) (logical)|Logical reference to ticket_bookings(ticket_code); no database constraint.|
|qr_reference|VARCHAR(64)|NOT NULL, UNIQUE|Stores qr reference value.|
|validity_state|ENUM('ACTIVE','CANCELLED','REVOKED','REFUNDED')|NOT NULL, DEFAULT ACTIVE|Stores validity state value.|
|version|BIGINT|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record creation timestamp (UTC).|
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record last update timestamp (UTC).|
|qr_generation|INT|NOT NULL, DEFAULT 1||
|sales_channel|VARCHAR(16)|NOT NULL, DEFAULT DIRECT||

:::

### 3.23 settlement_final_bills

Stores the folio of a ticket with remaining amount, handover and debt state.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|bill_reference|VARCHAR(128)|NOT NULL, UNIQUE|Stores bill reference value.|
|booking_reference|VARCHAR(128)|NOT NULL, UNIQUE, FK → ticket_bookings(ticket_code) (logical)|Logical reference to ticket_bookings(ticket_code); no database constraint.|
|group_reference|VARCHAR(128)|FK → booking_groups(group_code) (logical)|Logical reference to booking_groups(group_code); no database constraint.|
|post_promotion_amount_vnd|BIGINT|NOT NULL|Monetary value or calculated fee (post promotion amount vnd).|
|accepted_deposit_amount_vnd|BIGINT|NOT NULL|Monetary value or calculated fee (accepted deposit amount vnd).|
|remaining_amount_vnd|BIGINT|NOT NULL|Monetary value or calculated fee (remaining amount vnd).|
|currency_code|VARCHAR(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|lifecycle_status|VARCHAR(24)|NOT NULL|Stores lifecycle status value.|
|frozen_at|TIMESTAMP(6)|NOT NULL|Timestamp recording when frozen at.|
|loyalty_processed_at|TIMESTAMP(6)|||
|created_at|TIMESTAMP(6)|NOT NULL|Record creation timestamp (UTC).|
|handover_approved_by_account_id|BIGINT|FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|handover_reason|VARCHAR(1000)|||
|handover_approved_at|DATETIME(6)|||
|debt_amount_vnd|BIGINT|||
|debt_reason|VARCHAR(1000)|||
|debt_recorded_by_account_id|BIGINT|FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|debt_recorded_at|DATETIME(6)|||
|debt_closed_by_account_id|BIGINT|FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|debt_closed_at|DATETIME(6)|||

:::

### 3.24 settlement_charges

Stores accepted add-on charges posted to a folio.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|charge_reference|VARCHAR(128)|NOT NULL, UNIQUE|Stores charge reference value.|
|bill_reference|VARCHAR(128)|NOT NULL, FK → settlement_final_bills(bill_reference)|Foreign key referencing settlement_final_bills(bill_reference).|
|source_category|VARCHAR(32)|NOT NULL|Stores source category value.|
|amount_vnd|BIGINT|NOT NULL|Monetary value or calculated fee (amount vnd).|
|currency_code|VARCHAR(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|lifecycle_status|VARCHAR(24)|NOT NULL|Stores lifecycle status value.|
|reason_reference|VARCHAR(255)||Documented justification or reason for reason reference.|
|created_at|TIMESTAMP(6)|NOT NULL|Record creation timestamp (UTC).|
|description|VARCHAR(200)||Descriptive details, business remarks, or explanatory notes.|
|consent_reference|VARCHAR(64)|UNIQUE||
|consent_recorded_by|BIGINT|||
|consent_recorded_at|TIMESTAMP(6)|||

:::

### 3.25 settlement_folio_collections

Stores payments collected on a folio by VNPay, cash or bank transfer.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|collection_reference|VARCHAR(128)|NOT NULL, UNIQUE||
|bill_reference|VARCHAR(128)|NOT NULL, FK → settlement_final_bills(bill_reference) (logical)|Logical reference to settlement_final_bills(bill_reference); no database constraint.|
|booking_reference|VARCHAR(128)|NOT NULL||
|method|ENUM('VNPAY','CASH','BANK_TRANSFER')|NOT NULL||
|amount_vnd|BIGINT|NOT NULL||
|cash_received_vnd|BIGINT|||
|cash_change_vnd|BIGINT|||
|excess_vnd|BIGINT|NOT NULL, DEFAULT 0||
|status|ENUM('PENDING_VERIFICATION','CONFIRMED','FAILED','VOIDED')|NOT NULL, DEFAULT PENDING_VERIFICATION||
|external_reference|VARCHAR(128)|||
|payer_label|VARCHAR(200)|||
|branch_id|BIGINT|FK → workshop_locations(id) (logical)|Logical reference to workshop_locations(id); no database constraint.|
|cash_business_day|DATE|||
|recorded_by_account_id|BIGINT|FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|confirmed_by_account_id|BIGINT|FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|confirmed_at|DATETIME(6)|||
|currency_code|VARCHAR(3)|NOT NULL, DEFAULT VND||
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|payment_url_snapshot|VARCHAR(2048)|||
|payment_expires_at|DATETIME(6)|||
|provider_create_date|VARCHAR(14)|||

:::

### 3.26 settlement_ticket_cancellations

Stores ticket cancellations and their benefit outcome.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|cancellation_reference|VARCHAR(64)|NOT NULL, UNIQUE||
|group_request_reference|VARCHAR(64)|NOT NULL||
|ticket_code|VARCHAR(64)|NOT NULL, UNIQUE, FK → ticket_bookings(ticket_code) (logical)|Logical reference to ticket_bookings(ticket_code); no database constraint.|
|group_code|VARCHAR(64)|NOT NULL||
|requester_account_id|BIGINT|FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|requester_role|VARCHAR(32)|NOT NULL||
|channel|VARCHAR(32)|NOT NULL||
|buyer_proof|VARCHAR(128)|||
|reason|VARCHAR(1000)|NOT NULL||
|requested_at|DATETIME(6)|NOT NULL||
|first_session_start|DATETIME(6)|NOT NULL||
|benefit_outcome|VARCHAR(32)|NOT NULL||
|points_returned|BIGINT|NOT NULL, DEFAULT 0||
|voucher_restored|TINYINT(1)|NOT NULL, DEFAULT 0||
|created_at|DATETIME(6)|NOT NULL||

:::

### 3.27 settlement_ticket_changes

Stores session changes applied to a ticket.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|change_reference|VARCHAR(64)|NOT NULL, UNIQUE||
|idempotency_key|VARCHAR(128)|NOT NULL||
|ticket_code|VARCHAR(64)|NOT NULL, FK → ticket_bookings(ticket_code) (logical)|Logical reference to ticket_bookings(ticket_code); no database constraint.|
|group_code|VARCHAR(64)|NOT NULL||
|change_type|VARCHAR(32)|NOT NULL||
|from_session_references|VARCHAR(512)|||
|to_session_references|VARCHAR(512)|||
|counted|TINYINT(1)|NOT NULL, DEFAULT 1||
|price_increase_amount_vnd|BIGINT|NOT NULL, DEFAULT 0||
|buyer_confirmed_increase|TINYINT(1)|NOT NULL, DEFAULT 0||
|applied|TINYINT(1)|NOT NULL, DEFAULT 1||
|requester_account_id|BIGINT|FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|requester_role|VARCHAR(32)|||
|channel|VARCHAR(32)|||
|buyer_proof|VARCHAR(128)|||
|reason|VARCHAR(1000)|||
|balance_due_snapshot|BIGINT|NOT NULL, DEFAULT 0||
|created_at|DATETIME(6)|NOT NULL||

:::

### 3.28 settlement_refund_requests

Stores refund requests and their approval, transfer and confirmation steps.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|request_reference|VARCHAR(128)|NOT NULL, UNIQUE||
|booking_reference|VARCHAR(128)|NOT NULL||
|group_reference|VARCHAR(128)|FK → booking_groups(group_code) (logical)|Logical reference to booking_groups(group_code); no database constraint.|
|reason|ENUM('MLAND_CANCELLATION','LATE_PAYMENT_NOT_RECONFIRMED','OVERCHARGE_AFTER_FINALIZED','EXCESS_OR_DUPLICATE_MONEY','OTA_ADDON_OVERCHARGE')|NOT NULL||
|source_reference|VARCHAR(128)|NOT NULL||
|amount_vnd|BIGINT|NOT NULL||
|currency_code|VARCHAR(3)|NOT NULL, DEFAULT VND||
|status|ENUM('PENDING_ACCOUNT','PENDING_APPROVAL','PENDING_TRANSFER','PENDING_RECONCILIATION','REFUNDED','CLOSED_UNREACHABLE')|NOT NULL, DEFAULT PENDING_ACCOUNT||
|recipient_bank_code|VARCHAR(32)|||
|recipient_account_encrypted|VARCHAR(512)|||
|recipient_account_masked|VARCHAR(16)|||
|recipient_holder_name|VARCHAR(200)|||
|account_entry_channel|ENUM('LINK','COUNTER')|||
|account_entered_by_account_id|BIGINT|FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|account_entered_at|DATETIME(6)|||
|approved_by_account_id|BIGINT|FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|approved_at|DATETIME(6)|||
|approval_reason|VARCHAR(1000)|||
|transferred_by_account_id|BIGINT|FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|transferred_at|DATETIME(6)|||
|bank_transfer_reference|VARCHAR(128)|||
|transfer_proof_reference|VARCHAR(255)|||
|confirmed_by_account_id|BIGINT|FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|confirmed_at|DATETIME(6)|||
|closed_by_account_id|BIGINT|FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|closed_at|DATETIME(6)|||
|closure_reason|VARCHAR(1000)|||
|reopened_at|DATETIME(6)|||
|reminder_day3_at|DATETIME(6)|||
|reminder_day7_at|DATETIME(6)|||
|version|BIGINT|NOT NULL, DEFAULT 0||
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||

:::

### 3.29 ota_channel_connections

Stores the connection configuration of each OTA provider.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|provider_code|VARCHAR(32)|NOT NULL|Stores provider code value.|
|environment|VARCHAR(16)|NOT NULL|Stores environment value.|
|status|VARCHAR(24)|NOT NULL|Operational lifecycle state (varchar(24)).|
|credential_reference|VARCHAR(255)||Stores credential reference value.|
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record creation timestamp (UTC).|
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record last update timestamp (UTC).|
|public_reference|VARCHAR(36)|NOT NULL, UNIQUE|Unique external public reference identifier.|

:::

### 3.30 ota_channel_mappings

Links Mland sessions to OTA provider products.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|connection_id|BIGINT|NOT NULL, FK → ota_channel_connections(id)|Foreign key referencing ota_channel_connections(id).|
|offer_reference|VARCHAR(128)|NOT NULL|Stores offer reference value.|
|session_reference|VARCHAR(128)|NOT NULL, FK → workshop_sessions(reference) (logical)|Logical reference to workshop_sessions(reference); no database constraint.|
|location_reference|VARCHAR(128)|NOT NULL|Stores location reference value.|
|external_product_id|VARCHAR(255)|NOT NULL|Stores external product id value.|
|external_option_id|VARCHAR(255)|NOT NULL|Stores external option id value.|
|status|VARCHAR(32)|NOT NULL|Operational lifecycle state (varchar(32)).|
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record creation timestamp (UTC).|
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record last update timestamp (UTC).|
|public_reference|VARCHAR(36)|NOT NULL, UNIQUE|Unique external public reference identifier.|
|version|BIGINT|NOT NULL, DEFAULT 1|Optimistic concurrency locking version counter.|

:::

### 3.31 ota_channel_bookings

Stores bookings received from an OTA provider.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|mapping_id|BIGINT|NOT NULL, FK → ota_channel_mappings(id)|Foreign key referencing ota_channel_mappings(id).|
|external_booking_reference|VARCHAR(255)|NOT NULL|Stores external booking reference value.|
|provider_status|VARCHAR(32)|NOT NULL|Stores provider status value.|
|participant_count|INT|NOT NULL|Numerical count, quota, or capacity (participant count).|
|mland_booking_reference|VARCHAR(128)||Stores mland booking reference value.|
|sales_channel_snapshot|VARCHAR(32)|NOT NULL|Serialized JSON or snapshot data for sales channel snapshot.|
|displayed_policy_reference|VARCHAR(128)|NOT NULL|Stores displayed policy reference value.|
|displayed_policy_version|VARCHAR(64)|NOT NULL|Stores displayed policy version value.|
|booking_cutoff_at|DATETIME(6)||Timestamp recording when booking cutoff at.|
|booking_cutoff_timezone|VARCHAR(64)||Stores booking cutoff timezone value.|
|retail_amount_minor|BIGINT|NOT NULL|Monetary value or calculated fee (retail amount minor).|
|target_net_amount_minor|BIGINT|NOT NULL|Monetary value or calculated fee (target net amount minor).|
|commission_amount_minor|BIGINT||Monetary value or calculated fee (commission amount minor).|
|fee_amount_minor|BIGINT||Monetary value or calculated fee (fee amount minor).|
|accepted_amount_minor|BIGINT||Monetary value or calculated fee (accepted amount minor).|
|currency_code|VARCHAR(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record creation timestamp (UTC).|
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record last update timestamp (UTC).|
|public_reference|VARCHAR(36)|NOT NULL, UNIQUE|Unique external public reference identifier.|

:::

### 3.32 design_requests

Stores design requests submitted by an active Member and their human-review status. A Guest may see the protected entry point but does not create a private request record.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|VARCHAR(36)|PRIMARY KEY|Primary key unique identifier.|
|request_reference|VARCHAR(64)|NOT NULL, UNIQUE|Stores request reference value.|
|owner_account_id|BIGINT|FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|guest_proof_hash|VARCHAR(128)||Cryptographic signature or hash digest for guest proof hash.|
|ownership_type|VARCHAR(16)|NOT NULL, DEFAULT MEMBER|Stores ownership type value.|
|description|VARCHAR(4000)||Descriptive details, business remarks, or explanatory notes.|
|catalogue_reference|VARCHAR(128)||Stores catalogue reference value.|
|request_version|INT|NOT NULL, DEFAULT 1|Stores request version value.|
|idempotency_key|VARCHAR(128)|NOT NULL|Unique idempotency key for safe retry execution.|
|request_status|VARCHAR(32)|NOT NULL, DEFAULT ACTIVE|Stores request status value.|
|analysis_status|VARCHAR(32)|NOT NULL, DEFAULT PENDING|Stores analysis status value.|
|review_status|VARCHAR(32)|NOT NULL, DEFAULT NEED_REVIEW|Stores review status value.|
|estimated_value|BIGINT||Stores estimated value value.|
|next_action|VARCHAR(64)|NOT NULL|Stores next action value.|
|safe_failure_code|VARCHAR(64)||Stores safe failure code value.|
|created_at|TIMESTAMP|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record creation timestamp (UTC).|
|updated_at|TIMESTAMP|NOT NULL, DEFAULT CURRENT_TIMESTAMP|Record last update timestamp (UTC).|
|guest_session_id|VARCHAR(128)|||
|tracking_token|VARCHAR(256)|||
|tracking_token_hash|VARCHAR(128)|||
|tracking_token_expires_at|TIMESTAMP|||
|decision_explanation|VARCHAR(2000)|||
|design_reference|VARCHAR(64)|||
|design_version|INT|||
|quote_reference|VARCHAR(64)|||

:::

### 3.33 design_request_images

Stores the reference images of a design request with their quality result.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|design_request_id|VARCHAR(36)|NOT NULL, FK → design_requests(id)|References design_requests(id).|
|image_reference|VARCHAR(64)|NOT NULL, UNIQUE||
|storage_path|VARCHAR(512)|NOT NULL||
|original_filename|VARCHAR(255)|NOT NULL||
|content_type|VARCHAR(64)|NOT NULL||
|file_size_bytes|BIGINT|NOT NULL||
|quality_status|VARCHAR(32)|NOT NULL, DEFAULT PASS||
|quality_reason|VARCHAR(255)|||
|quality_score|DOUBLE|||
|created_at|TIMESTAMP|NOT NULL, DEFAULT CURRENT_TIMESTAMP||

:::

### 3.34 custom_orders

Stores custom ring orders created from an accepted design request.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|VARCHAR(36)|PRIMARY KEY|Primary key.|
|custom_order_reference|VARCHAR(64)|NOT NULL, UNIQUE||
|member_account_id|BIGINT|NOT NULL, FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|design_request_id|VARCHAR(36)|NOT NULL, FK → design_requests(id) (logical)|Logical reference to design_requests(id); no database constraint.|
|status|VARCHAR(32)|NOT NULL, DEFAULT QUOTE_PENDING||
|production_status|VARCHAR(32)|||
|fulfilment_status|VARCHAR(32)|||
|shipping_address|VARCHAR(500)|||
|created_at|TIMESTAMP|NOT NULL, DEFAULT CURRENT_TIMESTAMP||
|updated_at|TIMESTAMP|NOT NULL, DEFAULT CURRENT_TIMESTAMP||

:::

### 3.35 custom_order_quotes

Stores the quotes of a custom order and the Manager approval.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|VARCHAR(36)|PRIMARY KEY|Primary key.|
|custom_order_id|VARCHAR(36)|NOT NULL, FK → custom_orders(id) (logical)|Logical reference to custom_orders(id); no database constraint.|
|quote_reference|VARCHAR(64)|NOT NULL, UNIQUE||
|quote_generation|INT|NOT NULL, DEFAULT 1||
|total_amount|BIGINT|NOT NULL||
|currency|VARCHAR(3)|NOT NULL, DEFAULT VND||
|expires_at|TIMESTAMP|NOT NULL||
|status|VARCHAR(32)|NOT NULL, DEFAULT QUOTE_PENDING||
|approved_by_manager|TINYINT(1)|NOT NULL, DEFAULT 0||
|manager_account_id|BIGINT|FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|manager_reason|VARCHAR(2000)|||
|payment_status|VARCHAR(32)|NOT NULL, DEFAULT UNPAID||
|payment_mode|VARCHAR(32)|NOT NULL, DEFAULT FULL_CUSTOM_ORDER_PAYMENT||
|created_at|TIMESTAMP|NOT NULL, DEFAULT CURRENT_TIMESTAMP||
|updated_at|TIMESTAMP|NOT NULL, DEFAULT CURRENT_TIMESTAMP||

:::

### 3.36 loyalty_wallet

Stores the point balance and held points of a Member.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|member_id|BIGINT|PRIMARY KEY, FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|balance_points|DECIMAL(19,0)|NOT NULL|Numerical count, quota, or capacity (balance points).|
|held_points|DECIMAL(19,0)|NOT NULL|Numerical count, quota, or capacity (held points).|
|version|BIGINT|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|

:::

### 3.37 loyalty_point_ledger

Stores every point movement (earn, hold, consume, release, reverse) of a wallet.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|member_id|BIGINT|NOT NULL, FK → loyalty_wallet(member_id) (logical)|Logical reference to loyalty_wallet(member_id); no database constraint.|
|accepted_benefit_id|BIGINT|NOT NULL, FK → loyalty_accepted_benefit(id)|Foreign key referencing loyalty_accepted_benefit(id).|
|part_seq|INT|NOT NULL, DEFAULT 0||
|entry_kind|VARCHAR(10)|NOT NULL|Stores entry kind value.|
|delta_balance|DECIMAL(19,0)|NOT NULL|Stores delta balance value.|
|delta_held|DECIMAL(19,0)|NOT NULL|Stores delta held value.|
|balance_after|DECIMAL(19,0)|NOT NULL|Stores balance after value.|
|held_after|DECIMAL(19,0)|NOT NULL|Stores held after value.|
|occurred_at|DATETIME(6)|NOT NULL|Timestamp recording when occurred at.|
|original_debit_id|BIGINT|FK → loyalty_point_ledger(id)|Foreign key referencing loyalty_point_ledger(id).|

:::

### 3.38 loyalty_policy

Stores immutable revisions of the earn and redeem policy.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|revision_no|BIGINT|NOT NULL, UNIQUE|Stores revision no value.|
|earn_unit_amount|DECIMAL(19,0)|NOT NULL|Monetary value or calculated fee (earn unit amount).|
|redeem_point_value|DECIMAL(19,0)|NOT NULL|Stores redeem point value value.|
|min_redeem_points|DECIMAL(19,0)|NOT NULL|Numerical count, quota, or capacity (min redeem points).|
|max_redeem_percent|DECIMAL(9,6)|NOT NULL|Stores max redeem percent value.|
|rounding_mode|VARCHAR(10)|NOT NULL|Stores rounding mode value.|
|created_at|DATETIME(6)|NOT NULL|Record creation timestamp (UTC).|
|created_by|BIGINT||Identifier of the actor or administrator who created the record.|

:::

### 3.39 promotion_program

Stores voucher and automatic-discount programs.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|kind|VARCHAR(20)|NOT NULL|Stores kind value.|
|scope|VARCHAR(20)|NOT NULL|Stores scope value.|
|code_original|VARCHAR(128)||Stores code original value.|
|code_key|VARCHAR(128)|UNIQUE|Stores code key value.|
|status|VARCHAR(10)|NOT NULL|Operational lifecycle state (varchar(10)).|
|distribution|VARCHAR(16)|||
|current_revision_id|BIGINT|FK → promotion_revision(id)|Foreign key referencing promotion_revision(id).|
|total_limit|BIGINT||Monetary value or calculated fee (total limit).|
|member_limit|BIGINT||Stores member limit value.|
|held_count|BIGINT||Numerical count, quota, or capacity (held count).|
|consumed_count|BIGINT||Numerical count, quota, or capacity (consumed count).|
|claimed_count|BIGINT|||
|created_at|DATETIME(6)|NOT NULL|Record creation timestamp (UTC).|
|created_by|BIGINT|NOT NULL|Identifier of the actor or administrator who created the record.|
|config_version|BIGINT|NOT NULL, DEFAULT 0|Stores config version value.|
|version|BIGINT|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|

:::

### 3.40 promotion_member_program

Stores vouchers saved by a Member and their usage counts.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|program_id|BIGINT|NOT NULL, FK → promotion_program(id)|Foreign key referencing promotion_program(id).|
|member_id|BIGINT|NOT NULL, FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|saved_at|DATETIME(6)||Timestamp recording when saved at.|
|held_count|BIGINT|NOT NULL, DEFAULT 0|Numerical count, quota, or capacity (held count).|
|consumed_count|BIGINT|NOT NULL, DEFAULT 0|Numerical count, quota, or capacity (consumed count).|
|version|BIGINT|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|

:::

### 3.41 notification_template_definition

Stores the email templates the system can send, including the Admin-initiated internal invitation. An approved version is pinned to each delivery request. The version storage referred to below is not yet described column by column in this section and must be completed before the database design is implemented.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|code|VARCHAR(64)|NOT NULL, UNIQUE||
|name|VARCHAR(128)|NOT NULL||
|description|VARCHAR(255)|||
|owning_module|VARCHAR(64)|NOT NULL||
|lifecycle_status|VARCHAR(32)|NOT NULL, DEFAULT DRAFT||
|allowed_placeholders|TEXT|NOT NULL||
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||

:::

### 3.42 notification_request

Stores email notification requests and their delivery state. The invitation request keeps only an Account-owned invitation ID/version and safe recipient snapshot. Its stored rendition uses a placeholder for the setup URL. Account provides the current URL transiently for each SMTP attempt; no request, rendition or audit row stores the expanded link. Invitation requests allow at most two definite attempts and reject generic resend or cancel.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|business_event_reference|VARCHAR(128)|NOT NULL||
|template_code|VARCHAR(64)|NOT NULL, FK → notification_template_definition(code) (logical)|Logical reference to notification_template_definition(code); no database constraint.|
|template_version_id|BIGINT|NOT NULL, logical reference → notification_template_version(id)|Pins the approved template version; version-table column details remain TBD in this section.|
|recipient_role|VARCHAR(32)|NOT NULL, DEFAULT CUSTOMER||
|recipient_address_fingerprint|VARCHAR(64)|NOT NULL||
|recipient_address_masked|VARCHAR(128)|NOT NULL||
|recipient_snapshot|TEXT|NOT NULL||
|business_data_snapshot|MEDIUMTEXT|NOT NULL||
|artifact_reference|VARCHAR(255)|||
|credential_reference|VARCHAR(255)|||
|credential_expires_at|DATETIME(6)|||
|delivery_state|VARCHAR(32)|NOT NULL, DEFAULT PENDING||
|attempt_count|INT|NOT NULL, DEFAULT 0||
|next_attempt_at|DATETIME(6)|||
|processing_started_at|DATETIME(6)|||
|smtp_invocation_started_at|DATETIME(6)|||
|terminal_at|DATETIME(6)|||
|original_request_id|BIGINT|FK → notification_request(id)|References notification_request(id).|
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|original_idempotency_scope|TINYINT|||

:::

### 3.43 notification_delivery_attempt

Stores each attempt to send a notification email.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|notification_request_id|BIGINT|NOT NULL, FK → notification_request(id)|References notification_request(id).|
|attempt_number|INT|NOT NULL||
|started_at|DATETIME(6)|NOT NULL||
|completed_at|DATETIME(6)|NOT NULL||
|outcome_category|VARCHAR(32)|NOT NULL||
|provider_message_id|VARCHAR(128)|||
|safe_error_summary|VARCHAR(512)|||

:::

### 3.44 retail_carts

Stores the shopping cart of a Member.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|VARCHAR(36)|PRIMARY KEY|Primary key unique identifier.|
|member_account_id|BIGINT UNSIGNED|NOT NULL, UNIQUE, FK → accounts(id)|Foreign key referencing accounts(id).|
|version|BIGINT|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record creation timestamp (UTC).|
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record last update timestamp (UTC).|

:::

### 3.45 retail_cart_lines

Stores the variants, branch and quantity in a cart.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|VARCHAR(36)|PRIMARY KEY|Primary key unique identifier.|
|cart_id|VARCHAR(36)|NOT NULL, FK → retail_carts(id)|Foreign key referencing retail_carts(id).|
|variant_id|BIGINT UNSIGNED|NOT NULL, FK → catalogue_variants(id) (logical)|Logical reference to catalogue_variants(id); no database constraint.|
|location_id|BIGINT UNSIGNED|NOT NULL, FK → workshop_locations(id) (logical)|Logical reference to workshop_locations(id); no database constraint.|
|quantity|INT|NOT NULL|Numerical count, quota, or capacity (quantity).|
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record creation timestamp (UTC).|
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record last update timestamp (UTC).|

:::

### 3.46 retail_grouped_checkouts

Stores one Member payment that covers one or more branch orders.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|VARCHAR(36)|PRIMARY KEY|Primary key unique identifier.|
|member_account_id|BIGINT UNSIGNED|NOT NULL, FK → accounts(id)|Foreign key referencing accounts(id).|
|idempotency_key|VARCHAR(128)|NOT NULL|Unique idempotency key for safe retry execution.|
|cart_version|BIGINT|NOT NULL|Stores cart version value.|
|currency|VARCHAR(3)|NOT NULL|ISO-4217 standard currency code (e.g., VND).|
|merchandise_subtotal|DECIMAL(19,2)|NOT NULL|Monetary value or calculated fee (merchandise subtotal).|
|shipping_charge|DECIMAL(19,2)|NOT NULL|Stores shipping charge value.|
|discount_amount|DECIMAL(19,2)|NOT NULL|Monetary value or calculated fee (discount amount).|
|payable_amount|DECIMAL(19,2)|NOT NULL|Monetary value or calculated fee (payable amount).|
|payment_state|VARCHAR(32)|NOT NULL|Stores payment state value.|
|payment_initiated_at|DATETIME(6)||Timestamp recording when payment initiated at.|
|payment_url|VARCHAR(2048)|||
|payment_url_expires_at|DATETIME(6)|||
|version|BIGINT|NOT NULL, DEFAULT 0|Optimistic concurrency locking version counter.|
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record creation timestamp (UTC).|
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record last update timestamp (UTC).|
|cancellation_unrestored_quantity|INT||Numerical count, quota, or capacity (cancellation unrestored quantity).|
|payment_proof_ref|VARCHAR(128)||Stores payment proof ref value.|
|payment_method|VARCHAR(24)|NOT NULL, DEFAULT VNPAY||
|counter_payment_deadline|DATETIME(6)|||

:::

### 3.47 retail_orders

Stores a retail order fulfilled by one branch.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|VARCHAR(36)|PRIMARY KEY|Primary key unique identifier.|
|grouped_checkout_id|VARCHAR(36)|NOT NULL, FK → retail_grouped_checkouts(id)|Foreign key referencing retail_grouped_checkouts(id).|
|location_id|BIGINT UNSIGNED|NOT NULL, FK → workshop_locations(id) (logical)|Logical reference to workshop_locations(id); no database constraint.|
|fulfilment_method|VARCHAR(16)|NOT NULL|Stores fulfilment method value.|
|payment_state|VARCHAR(32)|NOT NULL|Stores payment state value.|
|paid_at|DATETIME(6)|||
|completed_at|DATETIME(6)|||
|fulfilment_state|VARCHAR(32)|NOT NULL|Stores fulfilment state value.|
|subtotal|DECIMAL(19,2)|NOT NULL|Monetary value or calculated fee (subtotal).|
|shipping_charge|DECIMAL(19,2)|NOT NULL|Stores shipping charge value.|
|payable_amount|DECIMAL(19,2)|NOT NULL|Monetary value or calculated fee (payable amount).|
|recipient_name|VARCHAR(255)|||
|recipient_phone|VARCHAR(32)|||
|recipient_address|VARCHAR(512)|||
|recipient_province|VARCHAR(128)|||
|recipient_district|VARCHAR(128)|||
|recipient_ward|VARCHAR(128)|||
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)|Record creation timestamp (UTC).|
|pickup_ready_confirmed|TINYINT(1)|NOT NULL, DEFAULT 0|Stores pickup ready confirmed value.|

:::

### 3.48 retail_order_items

Stores the frozen variant, quantity and price of an order line.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|VARCHAR(36)|PRIMARY KEY|Primary key unique identifier.|
|retail_order_id|VARCHAR(36)|NOT NULL, FK → retail_orders(id)|Foreign key referencing retail_orders(id).|
|variant_id|BIGINT UNSIGNED|NOT NULL, FK → catalogue_variants(id) (logical)|Logical reference to catalogue_variants(id); no database constraint.|
|quantity|INT|NOT NULL|Numerical count, quota, or capacity (quantity).|
|unit_price|DECIMAL(19,2)|NOT NULL|Monetary value or calculated fee (unit price).|
|line_total|DECIMAL(19,2)|NOT NULL|Monetary value or calculated fee (line total).|

:::

### 3.49 retail_shipments

Stores the carrier shipment of a delivery order.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|VARCHAR(36)|PRIMARY KEY|Primary key.|
|grouped_checkout_id|VARCHAR(36)|NOT NULL, FK → retail_grouped_checkouts(id)|References retail_grouped_checkouts(id).|
|branch_order_id|VARCHAR(36)|NOT NULL, UNIQUE, FK → retail_orders(id)|References retail_orders(id).|
|branch_order_identifier|VARCHAR(36)|NOT NULL||
|location_id|BIGINT|NOT NULL, FK → workshop_locations(id) (logical)|Logical reference to workshop_locations(id); no database constraint.|
|fulfilment_method|VARCHAR(16)|NOT NULL||
|mock_tracking_number|VARCHAR(32)|NOT NULL, UNIQUE||
|status|VARCHAR(32)|NOT NULL||
|recipient_name|VARCHAR(255)|||
|recipient_phone|VARCHAR(32)|||
|recipient_address|VARCHAR(512)|||
|province_name|VARCHAR(128)|||
|district_name|VARCHAR(128)|||
|ward_name|VARCHAR(128)|||
|created_at|DATETIME(6)|NOT NULL||
|updated_at|DATETIME(6)|NOT NULL||
|version|INT|NOT NULL, DEFAULT 0||

:::

### 3.50 reviews

Stores customer reviews of a workshop or product.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|VARCHAR(36)|PRIMARY KEY|Primary key.|
|source_type|VARCHAR(32)|NOT NULL||
|source_reference|VARCHAR(128)|NOT NULL||
|target_type|VARCHAR(32)|NOT NULL||
|target_reference|VARCHAR(128)|NOT NULL||
|owner_type|VARCHAR(16)|NOT NULL||
|owner_reference|VARCHAR(128)|NOT NULL||
|masked_display_name|VARCHAR(128)|NOT NULL||
|rating|INT|NOT NULL||
|body_text|TEXT|||
|status|VARCHAR(16)|NOT NULL, DEFAULT PUBLIC||
|version|BIGINT|NOT NULL, DEFAULT 0||
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|package_id|BIGINT|FK → workshop_packages(id) (logical)|Logical reference to workshop_packages(id); no database constraint.|
|package_name|VARCHAR(255)|||
|image_urls|TEXT|||

:::

### 3.51 review_replies

Stores the Manager reply to a review.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|VARCHAR(36)|PRIMARY KEY|Primary key.|
|review_id|VARCHAR(36)|NOT NULL, UNIQUE, FK → reviews(id)|References reviews(id).|
|body_text|TEXT|NOT NULL||
|created_by_manager_id|BIGINT UNSIGNED|NOT NULL, FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|version|BIGINT|NOT NULL, DEFAULT 0||
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||

:::

### 3.52 consultation_conversations

Stores consultation conversations between a Member and Staff.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|conversation_reference|VARCHAR(64)|NOT NULL, UNIQUE||
|member_user_id|BIGINT|NOT NULL, FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|context_type|VARCHAR(32)|||
|context_reference|VARCHAR(64)|||
|context_version|INT|||
|context_snapshot|JSON|||
|assigned_location_id|BIGINT|FK → workshop_locations(id) (logical)|Logical reference to workshop_locations(id); no database constraint.|
|claimed_by_user_id|BIGINT|FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|status|VARCHAR(16)|NOT NULL, DEFAULT OPEN||
|aggregate_version|BIGINT|NOT NULL, DEFAULT 1||
|created_at|TIMESTAMP(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|updated_at|TIMESTAMP(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|closed_at|TIMESTAMP(6)|||
|member_hidden_at|TIMESTAMP(6)|||
|member_hidden_by_user_id|BIGINT|FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|

:::

### 3.53 consultation_messages

Stores the immutable messages of a conversation.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|conversation_id|BIGINT|NOT NULL, FK → consultation_conversations(id)|References consultation_conversations(id).|
|sender_user_id|BIGINT|NOT NULL, FK → accounts(id) (logical)|Logical reference to accounts(id); no database constraint.|
|sender_role|VARCHAR(16)|NOT NULL||
|client_message_reference|VARCHAR(128)|NOT NULL||
|message_text|TEXT|NOT NULL||
|correlation_id|VARCHAR(64)|||
|created_at|TIMESTAMP(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||

:::

### 3.54 ring_designs

Stores immutable ring design versions created in the configurator.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT UNSIGNED|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|design_reference|VARCHAR(64)|NOT NULL||
|design_version|INT|NOT NULL, DEFAULT 1||
|design_name|VARCHAR(255)|||
|design_type|VARCHAR(30)|NOT NULL, DEFAULT configured_design||
|owner_type|VARCHAR(30)|NOT NULL||
|owner_reference|VARCHAR(128)|NOT NULL||
|package_reference|VARCHAR(128)|NOT NULL||
|source_model_reference|VARCHAR(128)|||
|source_design_id|BIGINT UNSIGNED|FK → ring_designs(id)|References ring_designs(id).|
|material_reference|VARCHAR(64)|FK → ring_materials(material_code) (logical)|Logical reference to ring_materials(material_code); no database constraint.|
|shape_reference|VARCHAR(64)|FK → ring_shapes(shape_code) (logical)|Logical reference to ring_shapes(shape_code); no database constraint.|
|ring_size|VARCHAR(30)|||
|component_snapshot|JSON|||
|estimated_price|DECIMAL(15,2)|||
|currency|VARCHAR(3)|||
|difficulty_score|DECIMAL(10,4)|||
|rules_version|VARCHAR(64)|||
|status|ENUM('DRAFT','SAVED','VALIDATED','ARCHIVED')|NOT NULL, DEFAULT DRAFT|Customer-facing lifecycle for a persisted Member design. Feature-012 no longer attaches designs to workshop bookings; recommendation is informational only.|
|booking_reference|VARCHAR(128)|||
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||

:::

### 3.55 ring_materials

Stores the ring material options.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT UNSIGNED|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|material_code|VARCHAR(64)|NOT NULL, UNIQUE||
|material_name|VARCHAR(100)|NOT NULL||
|description|TEXT|||
|purity|VARCHAR(50)|||
|unit_price|DECIMAL(15,2)|NOT NULL, DEFAULT 0.00||
|currency|VARCHAR(3)|NOT NULL||
|difficulty_score|DECIMAL(10,4)|NOT NULL, DEFAULT 1.0000||
|img_url|TEXT|||
|is_active|TINYINT(1)|NOT NULL, DEFAULT 1||
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||

:::

### 3.56 ring_shapes

Stores the ring shape options.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT UNSIGNED|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|shape_code|VARCHAR(64)|NOT NULL, UNIQUE||
|shape_name|VARCHAR(100)|NOT NULL||
|description|TEXT|||
|price_adjustment|DECIMAL(15,2)|NOT NULL, DEFAULT 0.00||
|currency|VARCHAR(3)|NOT NULL||
|difficulty_score|DECIMAL(10,4)|NOT NULL, DEFAULT 1.0000||
|img_url|TEXT|||
|is_active|TINYINT(1)|NOT NULL, DEFAULT 1||
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||

:::

### 3.57 ring_gemstones

Stores the gemstone options; selected gemstones are kept in ring_designs.component_snapshot.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT UNSIGNED|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|gemstone_code|VARCHAR(64)|NOT NULL, UNIQUE||
|gemstone_name|VARCHAR(100)|NOT NULL||
|description|TEXT|||
|gemstone_type|VARCHAR(100)|||
|color|VARCHAR(50)|||
|cut_name|VARCHAR(100)|||
|unit_price|DECIMAL(15,2)|NOT NULL, DEFAULT 0.00||
|currency|VARCHAR(3)|NOT NULL||
|difficulty_score|DECIMAL(10,4)|NOT NULL, DEFAULT 1.0000||
|img_url|TEXT|||
|max_quantity|INT|NOT NULL, DEFAULT 10||
|is_active|TINYINT(1)|NOT NULL, DEFAULT 1||
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||

:::

### 3.58 ring_attachments

Stores the attachment options; selected attachments are kept in ring_designs.component_snapshot.

::: {.docx-table width="100%" columns="22%,22%,26%,30%" repeat-header="false"}

|Column|Type|Constraints|Notes|
|---|---|---|---|
|id|BIGINT UNSIGNED|PRIMARY KEY, AUTO_INCREMENT|Primary key, auto-increment identifier.|
|attachment_code|VARCHAR(64)|NOT NULL, UNIQUE||
|attachment_name|VARCHAR(100)|NOT NULL||
|description|TEXT|||
|attachment_type|VARCHAR(100)|||
|unit_price|DECIMAL(15,2)|NOT NULL, DEFAULT 0.00||
|currency|VARCHAR(3)|NOT NULL||
|difficulty_score|DECIMAL(10,4)|NOT NULL, DEFAULT 1.0000||
|img_url|TEXT|||
|max_quantity|INT|NOT NULL, DEFAULT 10||
|is_active|TINYINT(1)|NOT NULL, DEFAULT 1||
|created_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||
|updated_at|DATETIME(6)|NOT NULL, DEFAULT CURRENT_TIMESTAMP(6)||

:::
