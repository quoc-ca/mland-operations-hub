# PPSWBS Database Design

## Tables

### Identity and policy

~~~sql
CREATE TABLE accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    system_code VARCHAR(64) NULL UNIQUE,
    firebase_uid VARCHAR(128) NULL UNIQUE,
    email VARCHAR(255) NULL,
    role VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_accounts_role CHECK (
        role IN ('GUEST', 'MEMBER', 'STAFF', 'MANAGER', 'ADMIN_TECHNICAL')
    ),
    CONSTRAINT chk_accounts_status CHECK (
        status IN ('ACTIVE', 'SUSPENDED', 'PENDING_POLICY_ACCEPTANCE', 'SYSTEM')
    ),
    CONSTRAINT chk_accounts_guest_system CHECK (
        (role = 'GUEST' AND status = 'SYSTEM' AND system_code = 'GUEST_SYSTEM' AND firebase_uid IS NULL)
        OR (role <> 'GUEST' AND system_code IS NULL)
    )
);

CREATE TABLE member_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL UNIQUE,
    full_name VARCHAR(255) NULL,
    phone VARCHAR(32) NULL,
    address_text VARCHAR(512) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_member_profiles_account
        FOREIGN KEY (account_id) REFERENCES accounts(id)
);

CREATE TABLE guest_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(255) NULL,
    email VARCHAR(255) NOT NULL,
    canonical_email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(32) NULL,
    email_verified_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE policy_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    policy_type VARCHAR(64) NOT NULL,
    version VARCHAR(64) NOT NULL,
    content_uri VARCHAR(512) NOT NULL,
    effective_at TIMESTAMP NOT NULL,
    state VARCHAR(64) NOT NULL,
    effective_policy_type VARCHAR(64)
        GENERATED ALWAYS AS (
            CASE WHEN state = 'EFFECTIVE' THEN policy_type ELSE NULL END
        ) STORED,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_policy_type_version (policy_type, version),
    UNIQUE KEY uk_effective_policy_type (effective_policy_type),
    CONSTRAINT chk_policy_documents_state CHECK (
        state IN ('DRAFT', 'EFFECTIVE', 'ARCHIVED')
    )
);

CREATE TABLE member_policy_acceptances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    policy_document_id BIGINT NOT NULL,
    accepted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locale VARCHAR(16) NOT NULL DEFAULT 'vi',
    correlation_id VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_member_policy_acceptance (account_id, policy_document_id),
    CONSTRAINT fk_member_policy_acceptances_account
        FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_member_policy_acceptances_policy
        FOREIGN KEY (policy_document_id) REFERENCES policy_documents(id)
);

CREATE TABLE member_auth_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NULL,
    event_type VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NULL,
    safe_metadata JSON NULL,
    INDEX idx_member_auth_audit_account_occurred (account_id, occurred_at),
    CONSTRAINT fk_member_auth_audit_events_account
        FOREIGN KEY (account_id) REFERENCES accounts(id)
);

INSERT INTO accounts (system_code, firebase_uid, email, role, status)
VALUES ('GUEST_SYSTEM', NULL, NULL, 'GUEST', 'SYSTEM');
~~~

### Catalogue

~~~sql
CREATE TABLE locations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    address_text VARCHAR(512) NOT NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE workshop_packages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    duration_minutes INT NOT NULL,
    base_price_vnd BIGINT NOT NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_workshop_packages_duration CHECK (duration_minutes > 0),
    CONSTRAINT chk_workshop_packages_price CHECK (base_price_vnd >= 0)
);

CREATE TABLE workshop_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    location_id BIGINT NOT NULL,
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    capacity INT NOT NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_workshop_sessions_location_start (location_id, starts_at),
    CONSTRAINT fk_workshop_sessions_location
        FOREIGN KEY (location_id) REFERENCES locations(id),
    CONSTRAINT chk_workshop_sessions_capacity CHECK (capacity > 0),
    CONSTRAINT chk_workshop_sessions_time CHECK (ends_at > starts_at)
);

CREATE TABLE workshop_session_packages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workshop_session_id BIGINT NOT NULL,
    workshop_package_id BIGINT NOT NULL,
    price_vnd BIGINT NOT NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_workshop_session_package (workshop_session_id, workshop_package_id),
    CONSTRAINT fk_workshop_session_packages_session
        FOREIGN KEY (workshop_session_id) REFERENCES workshop_sessions(id),
    CONSTRAINT fk_workshop_session_packages_package
        FOREIGN KEY (workshop_package_id) REFERENCES workshop_packages(id),
    CONSTRAINT chk_workshop_session_packages_price CHECK (price_vnd >= 0)
);

CREATE TABLE ring_models (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    base_price_vnd BIGINT NOT NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_ring_models_price CHECK (base_price_vnd >= 0)
);

CREATE TABLE ring_components (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    component_type VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    price_delta_vnd BIGINT NOT NULL DEFAULT 0,
    state VARCHAR(64) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE ring_model_components (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ring_model_id BIGINT NOT NULL,
    ring_component_id BIGINT NOT NULL,
    min_quantity INT NOT NULL DEFAULT 0,
    max_quantity INT NOT NULL DEFAULT 1,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ring_model_component (ring_model_id, ring_component_id),
    CONSTRAINT fk_ring_model_components_model
        FOREIGN KEY (ring_model_id) REFERENCES ring_models(id),
    CONSTRAINT fk_ring_model_components_component
        FOREIGN KEY (ring_component_id) REFERENCES ring_components(id),
    CONSTRAINT chk_ring_model_components_quantity CHECK (
        min_quantity >= 0 AND max_quantity >= min_quantity
    )
);

CREATE TABLE ready_ring_products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    list_price_vnd BIGINT NOT NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_ready_ring_products_price CHECK (list_price_vnd >= 0)
);

CREATE TABLE ready_ring_units (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ready_ring_product_id BIGINT NOT NULL,
    unit_code VARCHAR(64) NOT NULL UNIQUE,
    availability_status VARCHAR(64) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ready_ring_units_product
        FOREIGN KEY (ready_ring_product_id) REFERENCES ready_ring_products(id)
);
~~~

### Design review

~~~sql
CREATE TABLE design_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    guest_profile_id BIGINT NULL,
    booking_id BIGINT NULL,
    request_type VARCHAR(64) NOT NULL,
    consented_at TIMESTAMP NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'SUBMITTED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_design_requests_account
        FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_design_requests_guest_profile
        FOREIGN KEY (guest_profile_id) REFERENCES guest_profiles(id)
);

CREATE TABLE design_reference_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    design_request_id BIGINT NOT NULL,
    storage_uri VARCHAR(512) NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    media_type VARCHAR(128) NOT NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_design_reference_asset_hash (design_request_id, content_sha256),
    CONSTRAINT fk_design_reference_assets_request
        FOREIGN KEY (design_request_id) REFERENCES design_requests(id)
);

CREATE TABLE design_request_components (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    design_request_id BIGINT NOT NULL,
    ring_component_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_design_request_component (design_request_id, ring_component_id),
    CONSTRAINT fk_design_request_components_request
        FOREIGN KEY (design_request_id) REFERENCES design_requests(id),
    CONSTRAINT fk_design_request_components_component
        FOREIGN KEY (ring_component_id) REFERENCES ring_components(id),
    CONSTRAINT chk_design_request_components_quantity CHECK (quantity > 0)
);

CREATE TABLE feasibility_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_code VARCHAR(64) NOT NULL,
    rule_version VARCHAR(64) NOT NULL,
    rule_definition JSON NOT NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'DRAFT',
    published_by_account_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_feasibility_rule_version (rule_code, rule_version),
    CONSTRAINT fk_feasibility_rules_manager
        FOREIGN KEY (published_by_account_id) REFERENCES accounts(id)
);

CREATE TABLE feasibility_evaluations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    design_request_id BIGINT NOT NULL UNIQUE,
    feasibility_rule_id BIGINT NOT NULL,
    route VARCHAR(64) NOT NULL,
    decision VARCHAR(64) NOT NULL,
    candidate_features JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_feasibility_evaluations_request
        FOREIGN KEY (design_request_id) REFERENCES design_requests(id),
    CONSTRAINT fk_feasibility_evaluations_rule
        FOREIGN KEY (feasibility_rule_id) REFERENCES feasibility_rules(id)
);

CREATE TABLE design_review_decisions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    design_request_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    decision VARCHAR(64) NOT NULL,
    reason TEXT NOT NULL,
    decision_context JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_design_review_decisions_request
        FOREIGN KEY (design_request_id) REFERENCES design_requests(id),
    CONSTRAINT fk_design_review_decisions_account
        FOREIGN KEY (account_id) REFERENCES accounts(id)
);

CREATE TABLE design_review_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    design_request_id BIGINT NOT NULL,
    account_id BIGINT NULL,
    event_type VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    safe_metadata JSON NULL,
    CONSTRAINT fk_design_review_audit_request
        FOREIGN KEY (design_request_id) REFERENCES design_requests(id),
    CONSTRAINT fk_design_review_audit_account
        FOREIGN KEY (account_id) REFERENCES accounts(id)
);
~~~

### Workshop booking

~~~sql
CREATE TABLE workshop_bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_code VARCHAR(64) NOT NULL UNIQUE,
    account_id BIGINT NOT NULL,
    guest_profile_id BIGINT NULL,
    workshop_session_id BIGINT NOT NULL,
    workshop_package_id BIGINT NOT NULL,
    parent_booking_id BIGINT NULL,
    contact_name VARCHAR(255) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    canonical_email VARCHAR(255) NOT NULL,
    participant_count INT NOT NULL DEFAULT 1,
    confirmation_state VARCHAR(64) NOT NULL DEFAULT 'EMAIL_CONFIRMATION_PENDING',
    booking_status VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_workshop_bookings_canonical_email (canonical_email),
    CONSTRAINT fk_workshop_bookings_account
        FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_workshop_bookings_guest_profile
        FOREIGN KEY (guest_profile_id) REFERENCES guest_profiles(id),
    CONSTRAINT fk_workshop_bookings_session
        FOREIGN KEY (workshop_session_id) REFERENCES workshop_sessions(id),
    CONSTRAINT fk_workshop_bookings_package
        FOREIGN KEY (workshop_package_id) REFERENCES workshop_packages(id),
    CONSTRAINT fk_workshop_bookings_parent
        FOREIGN KEY (parent_booking_id) REFERENCES workshop_bookings(id),
    CONSTRAINT chk_workshop_bookings_participants CHECK (participant_count > 0)
);

CREATE TABLE booking_email_confirmations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    confirmed_at TIMESTAMP NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_booking_email_confirmations_booking
        FOREIGN KEY (booking_id) REFERENCES workshop_bookings(id)
);

CREATE TABLE booking_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    account_id BIGINT NULL,
    event_type VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    safe_metadata JSON NULL,
    CONSTRAINT fk_booking_audit_events_booking
        FOREIGN KEY (booking_id) REFERENCES workshop_bookings(id),
    CONSTRAINT fk_booking_audit_events_account
        FOREIGN KEY (account_id) REFERENCES accounts(id)
);

ALTER TABLE design_requests
    ADD CONSTRAINT fk_design_requests_booking
    FOREIGN KEY (booking_id) REFERENCES workshop_bookings(id);
~~~

### Ready-ring sales, billing, and payments

~~~sql
CREATE TABLE ready_ring_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_code VARCHAR(64) NOT NULL UNIQUE,
    account_id BIGINT NOT NULL,
    ready_ring_unit_id BIGINT NOT NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'PENDING_PAYMENT',
    fulfilment_choice VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ready_ring_orders_account
        FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_ready_ring_orders_unit
        FOREIGN KEY (ready_ring_unit_id) REFERENCES ready_ring_units(id)
);

CREATE TABLE ready_ring_sales_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ready_ring_order_id BIGINT NOT NULL,
    account_id BIGINT NULL,
    event_type VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    safe_metadata JSON NULL,
    CONSTRAINT fk_ready_ring_sales_audit_order
        FOREIGN KEY (ready_ring_order_id) REFERENCES ready_ring_orders(id),
    CONSTRAINT fk_ready_ring_sales_audit_account
        FOREIGN KEY (account_id) REFERENCES accounts(id)
);

CREATE TABLE invoices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_code VARCHAR(64) NOT NULL UNIQUE,
    workshop_booking_id BIGINT NULL,
    ready_ring_order_id BIGINT NULL,
    currency_code CHAR(3) NOT NULL DEFAULT 'VND',
    total_amount_vnd BIGINT NOT NULL,
    deposit_due_vnd BIGINT NOT NULL DEFAULT 0,
    amount_paid_vnd BIGINT NOT NULL DEFAULT 0,
    state VARCHAR(64) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_invoices_booking
        FOREIGN KEY (workshop_booking_id) REFERENCES workshop_bookings(id),
    CONSTRAINT fk_invoices_order
        FOREIGN KEY (ready_ring_order_id) REFERENCES ready_ring_orders(id),
    CONSTRAINT chk_invoices_subject CHECK (
        (workshop_booking_id IS NOT NULL AND ready_ring_order_id IS NULL)
        OR (workshop_booking_id IS NULL AND ready_ring_order_id IS NOT NULL)
    ),
    CONSTRAINT chk_invoices_amounts CHECK (
        total_amount_vnd >= 0 AND deposit_due_vnd >= 0 AND amount_paid_vnd >= 0
    )
);

CREATE TABLE invoice_lines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    line_type VARCHAR(64) NOT NULL,
    description VARCHAR(512) NOT NULL,
    quantity INT NOT NULL,
    unit_amount_vnd BIGINT NOT NULL,
    line_amount_vnd BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invoice_lines_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    CONSTRAINT chk_invoice_lines_quantity CHECK (quantity > 0)
);

CREATE TABLE invoice_adjustments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    staff_account_id BIGINT NOT NULL,
    manager_account_id BIGINT NULL,
    adjustment_type VARCHAR(64) NOT NULL,
    amount_vnd BIGINT NOT NULL,
    reason TEXT NOT NULL,
    customer_consented_at TIMESTAMP NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_invoice_adjustments_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    CONSTRAINT fk_invoice_adjustments_staff
        FOREIGN KEY (staff_account_id) REFERENCES accounts(id),
    CONSTRAINT fk_invoice_adjustments_manager
        FOREIGN KEY (manager_account_id) REFERENCES accounts(id)
);

CREATE TABLE billing_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    account_id BIGINT NULL,
    event_type VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    safe_metadata JSON NULL,
    CONSTRAINT fk_billing_audit_events_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    CONSTRAINT fk_billing_audit_events_account
        FOREIGN KEY (account_id) REFERENCES accounts(id)
);

CREATE TABLE payment_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    provider VARCHAR(64) NOT NULL,
    provider_reference VARCHAR(255) NULL,
    requested_amount_vnd BIGINT NOT NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_attempts_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    CONSTRAINT chk_payment_attempts_amount CHECK (requested_amount_vnd > 0)
);

CREATE TABLE payment_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_attempt_id BIGINT NOT NULL,
    provider VARCHAR(64) NOT NULL,
    provider_transaction_id VARCHAR(255) NOT NULL,
    amount_vnd BIGINT NOT NULL,
    state VARCHAR(64) NOT NULL,
    confirmed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payment_provider_transaction (provider, provider_transaction_id),
    CONSTRAINT fk_payment_transactions_attempt
        FOREIGN KEY (payment_attempt_id) REFERENCES payment_attempts(id),
    CONSTRAINT chk_payment_transactions_amount CHECK (amount_vnd > 0)
);

CREATE TABLE payment_provider_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_transaction_id BIGINT NULL,
    provider VARCHAR(64) NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    safe_metadata JSON NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'RECEIVED',
    UNIQUE KEY uk_payment_provider_event (provider, provider_event_id),
    CONSTRAINT fk_payment_provider_events_transaction
        FOREIGN KEY (payment_transaction_id) REFERENCES payment_transactions(id)
);
~~~

### Operations and fulfilment

~~~sql
CREATE TABLE workshop_check_ins (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workshop_booking_id BIGINT NOT NULL UNIQUE,
    staff_account_id BIGINT NOT NULL,
    checked_in_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actual_participant_count INT NOT NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'CHECKED_IN',
    CONSTRAINT fk_workshop_check_ins_booking
        FOREIGN KEY (workshop_booking_id) REFERENCES workshop_bookings(id),
    CONSTRAINT fk_workshop_check_ins_staff
        FOREIGN KEY (staff_account_id) REFERENCES accounts(id),
    CONSTRAINT chk_workshop_check_ins_participants CHECK (actual_participant_count >= 0)
);

CREATE TABLE custody_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workshop_booking_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    staff_account_id BIGINT NOT NULL,
    work_item_description TEXT NOT NULL,
    intake_photo_uri VARCHAR(512) NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'IN_CUSTODY',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_custody_records_booking
        FOREIGN KEY (workshop_booking_id) REFERENCES workshop_bookings(id),
    CONSTRAINT fk_custody_records_location
        FOREIGN KEY (location_id) REFERENCES locations(id),
    CONSTRAINT fk_custody_records_staff
        FOREIGN KEY (staff_account_id) REFERENCES accounts(id)
);

CREATE TABLE custody_releases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    custody_record_id BIGINT NOT NULL UNIQUE,
    staff_account_id BIGINT NOT NULL,
    released_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    state VARCHAR(64) NOT NULL DEFAULT 'RELEASED',
    CONSTRAINT fk_custody_releases_record
        FOREIGN KEY (custody_record_id) REFERENCES custody_records(id),
    CONSTRAINT fk_custody_releases_staff
        FOREIGN KEY (staff_account_id) REFERENCES accounts(id)
);

CREATE TABLE carrier_handoffs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ready_ring_order_id BIGINT NOT NULL UNIQUE,
    staff_account_id BIGINT NOT NULL,
    carrier_name VARCHAR(255) NOT NULL,
    handoff_reference VARCHAR(255) NOT NULL,
    handed_off_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_carrier_handoffs_order
        FOREIGN KEY (ready_ring_order_id) REFERENCES ready_ring_orders(id),
    CONSTRAINT fk_carrier_handoffs_staff
        FOREIGN KEY (staff_account_id) REFERENCES accounts(id)
);

CREATE TABLE fulfilment_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    custody_record_id BIGINT NULL,
    ready_ring_order_id BIGINT NULL,
    account_id BIGINT NULL,
    event_type VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    safe_metadata JSON NULL,
    CONSTRAINT fk_fulfilment_audit_custody
        FOREIGN KEY (custody_record_id) REFERENCES custody_records(id),
    CONSTRAINT fk_fulfilment_audit_order
        FOREIGN KEY (ready_ring_order_id) REFERENCES ready_ring_orders(id),
    CONSTRAINT fk_fulfilment_audit_account
        FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT chk_fulfilment_audit_subject CHECK (
        (custody_record_id IS NOT NULL AND ready_ring_order_id IS NULL)
        OR (custody_record_id IS NULL AND ready_ring_order_id IS NOT NULL)
    )
);
~~~

### Technical administration

~~~sql
CREATE TABLE technical_configurations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    configuration_key VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    safe_value JSON NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    updated_by_account_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_technical_configurations_account
        FOREIGN KEY (updated_by_account_id) REFERENCES accounts(id)
);

CREATE TABLE integration_health_checks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    integration_key VARCHAR(128) NOT NULL,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    state VARCHAR(64) NOT NULL,
    safe_detail TEXT NULL,
    INDEX idx_integration_health_checks_key_time (integration_key, checked_at)
);

CREATE TABLE technical_admin_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    safe_metadata JSON NULL,
    CONSTRAINT fk_technical_admin_audit_account
        FOREIGN KEY (account_id) REFERENCES accounts(id)
);
~~~

## Relationships

### Identity and policy

~~~mermaid
erDiagram
    ACCOUNTS ||--o| MEMBER_PROFILES : account_id
    ACCOUNTS ||--o{ MEMBER_POLICY_ACCEPTANCES : account_id
    POLICY_DOCUMENTS ||--o{ MEMBER_POLICY_ACCEPTANCES : policy_document_id
    ACCOUNTS o|--o{ MEMBER_AUTH_AUDIT_EVENTS : account_id
~~~

### Catalogue

~~~mermaid
erDiagram
    LOCATIONS ||--o{ WORKSHOP_SESSIONS : location_id
    WORKSHOP_SESSIONS ||--o{ WORKSHOP_SESSION_PACKAGES : workshop_session_id
    WORKSHOP_PACKAGES ||--o{ WORKSHOP_SESSION_PACKAGES : workshop_package_id
    RING_MODELS ||--o{ RING_MODEL_COMPONENTS : ring_model_id
    RING_COMPONENTS ||--o{ RING_MODEL_COMPONENTS : ring_component_id
    READY_RING_PRODUCTS ||--o{ READY_RING_UNITS : ready_ring_product_id
~~~

### Booking

~~~mermaid
erDiagram
    ACCOUNTS ||--o{ WORKSHOP_BOOKINGS : account_id
    GUEST_PROFILES o|--o{ WORKSHOP_BOOKINGS : guest_profile_id
    WORKSHOP_SESSIONS ||--o{ WORKSHOP_BOOKINGS : workshop_session_id
    WORKSHOP_PACKAGES ||--o{ WORKSHOP_BOOKINGS : workshop_package_id
    WORKSHOP_BOOKINGS o|--o{ WORKSHOP_BOOKINGS : parent_booking_id
    WORKSHOP_BOOKINGS ||--|| BOOKING_EMAIL_CONFIRMATIONS : booking_id
    WORKSHOP_BOOKINGS ||--o{ BOOKING_AUDIT_EVENTS : booking_id
    ACCOUNTS o|--o{ BOOKING_AUDIT_EVENTS : account_id
~~~

### Design review

~~~mermaid
erDiagram
    ACCOUNTS ||--o{ DESIGN_REQUESTS : account_id
    GUEST_PROFILES o|--o{ DESIGN_REQUESTS : guest_profile_id
    WORKSHOP_BOOKINGS o|--o{ DESIGN_REQUESTS : booking_id
    DESIGN_REQUESTS ||--o{ DESIGN_REFERENCE_ASSETS : design_request_id
    DESIGN_REQUESTS ||--o{ DESIGN_REQUEST_COMPONENTS : design_request_id
    RING_COMPONENTS ||--o{ DESIGN_REQUEST_COMPONENTS : ring_component_id
    FEASIBILITY_RULES ||--o{ FEASIBILITY_EVALUATIONS : feasibility_rule_id
    DESIGN_REQUESTS ||--o| FEASIBILITY_EVALUATIONS : design_request_id
    DESIGN_REQUESTS ||--o{ DESIGN_REVIEW_DECISIONS : design_request_id
    ACCOUNTS ||--o{ DESIGN_REVIEW_DECISIONS : account_id
    DESIGN_REQUESTS ||--o{ DESIGN_REVIEW_AUDIT_EVENTS : design_request_id
    ACCOUNTS o|--o{ DESIGN_REVIEW_AUDIT_EVENTS : account_id
~~~

### Ready-ring, billing, and payments

~~~mermaid
erDiagram
    READY_RING_UNITS ||--o| READY_RING_ORDERS : ready_ring_unit_id
    ACCOUNTS ||--o{ READY_RING_ORDERS : account_id
    WORKSHOP_BOOKINGS o|--o| INVOICES : workshop_booking_id
    READY_RING_ORDERS o|--o| INVOICES : ready_ring_order_id
    INVOICES ||--o{ INVOICE_LINES : invoice_id
    INVOICES ||--o{ INVOICE_ADJUSTMENTS : invoice_id
    ACCOUNTS ||--o{ INVOICE_ADJUSTMENTS : staff_account_id
    ACCOUNTS o|--o{ INVOICE_ADJUSTMENTS : manager_account_id
    INVOICES ||--o{ BILLING_AUDIT_EVENTS : invoice_id
    ACCOUNTS o|--o{ BILLING_AUDIT_EVENTS : account_id
    INVOICES ||--o{ PAYMENT_ATTEMPTS : invoice_id
    PAYMENT_ATTEMPTS ||--o{ PAYMENT_TRANSACTIONS : payment_attempt_id
    PAYMENT_TRANSACTIONS o|--o{ PAYMENT_PROVIDER_EVENTS : payment_transaction_id
    READY_RING_ORDERS ||--o{ READY_RING_SALES_AUDIT_EVENTS : ready_ring_order_id
    ACCOUNTS o|--o{ READY_RING_SALES_AUDIT_EVENTS : account_id
~~~

### Operations and fulfilment

~~~mermaid
erDiagram
    WORKSHOP_BOOKINGS ||--o| WORKSHOP_CHECK_INS : workshop_booking_id
    ACCOUNTS ||--o{ WORKSHOP_CHECK_INS : staff_account_id
    WORKSHOP_BOOKINGS ||--o{ CUSTODY_RECORDS : workshop_booking_id
    LOCATIONS ||--o{ CUSTODY_RECORDS : location_id
    ACCOUNTS ||--o{ CUSTODY_RECORDS : staff_account_id
    CUSTODY_RECORDS ||--|| CUSTODY_RELEASES : custody_record_id
    ACCOUNTS ||--o{ CUSTODY_RELEASES : staff_account_id
    READY_RING_ORDERS ||--o| CARRIER_HANDOFFS : ready_ring_order_id
    ACCOUNTS ||--o{ CARRIER_HANDOFFS : staff_account_id
    CUSTODY_RECORDS o|--o{ FULFILMENT_AUDIT_EVENTS : custody_record_id
    READY_RING_ORDERS o|--o{ FULFILMENT_AUDIT_EVENTS : ready_ring_order_id
    ACCOUNTS o|--o{ FULFILMENT_AUDIT_EVENTS : account_id
~~~

### Technical administration

~~~mermaid
erDiagram
    ACCOUNTS ||--o{ TECHNICAL_CONFIGURATIONS : updated_by_account_id
    ACCOUNTS ||--o{ TECHNICAL_ADMIN_AUDIT_EVENTS : account_id
    INTEGRATION_HEALTH_CHECKS {
        bigint id PK
        varchar integration_key
        varchar state
    }
~~~
