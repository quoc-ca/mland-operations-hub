-- Flyway Migration V2: Workshop Booking Redesign
-- Append-only migration

-- Drop legacy table (allowed since this is dev/test data reset as per spec)
DROP TABLE IF EXISTS booking_email_confirmations;
DROP TABLE IF EXISTS workshop_bookings;

-- Recreate workshop_bookings with new schema
CREATE TABLE workshop_bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_code VARCHAR(64) NOT NULL UNIQUE,
    member_id BIGINT NULL,
    package_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    participant_count INT NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    canonical_email VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(32) NOT NULL,
    canonical_phone VARCHAR(32) NOT NULL,
    design_reference VARCHAR(2000) NULL,
    package_name_snapshot VARCHAR(255) NOT NULL,
    price_snapshot BIGINT NOT NULL,
    currency_snapshot VARCHAR(3) NOT NULL,
    booking_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_status VARCHAR(32) NOT NULL DEFAULT 'UNPAID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_wb_code (booking_code),
    INDEX idx_wb_canonical_email (canonical_email),
    INDEX idx_wb_canonical_phone (canonical_phone),
    CONSTRAINT fk_wb_member FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE TABLE workshop_packages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(4000) NULL,
    price BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    deposit_percent INT NOT NULL DEFAULT 50,
    min_participants INT NOT NULL,
    max_participants INT NOT NULL,
    supported_options VARCHAR(2000) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE workshop_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    package_id BIGINT NOT NULL,
    session_date DATE NOT NULL,
    location VARCHAR(512) NULL,
    capacity INT NOT NULL,
    reserved_participants INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ws_package FOREIGN KEY (package_id) REFERENCES workshop_packages (id)
);

CREATE TABLE capacity_holds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE,
    session_id BIGINT NOT NULL,
    participant_count INT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    release_generation INT NOT NULL DEFAULT 0,
    released_at TIMESTAMP NULL,
    release_reason VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ch_booking FOREIGN KEY (booking_id) REFERENCES workshop_bookings (id)
);

CREATE TABLE workshop_invoices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE,
    total_amount BIGINT NOT NULL,
    deposit_percent INT NOT NULL,
    deposit_amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wi_booking FOREIGN KEY (booking_id) REFERENCES workshop_bookings (id)
);

CREATE TABLE payment_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    invoice_id BIGINT NOT NULL,
    provider_reference VARCHAR(128) NULL,
    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    gateway_expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pa_booking FOREIGN KEY (booking_id) REFERENCES workshop_bookings (id)
);

CREATE TABLE payment_event_receipts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    provider_event_id VARCHAR(128) NOT NULL UNIQUE,
    provider_reference VARCHAR(128) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_per_booking FOREIGN KEY (booking_id) REFERENCES workshop_bookings (id)
);

CREATE TABLE booking_tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE,
    qr_value VARCHAR(512) NOT NULL,
    valid_until TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bt_booking FOREIGN KEY (booking_id) REFERENCES workshop_bookings (id)
);

CREATE TABLE notification_intents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    intent_type VARCHAR(64) NOT NULL,
    recipient_email_snapshot VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP NULL,
    last_attempted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ni_booking FOREIGN KEY (booking_id) REFERENCES workshop_bookings (id)
);

CREATE TABLE booking_magic_links (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bml_booking FOREIGN KEY (booking_id) REFERENCES workshop_bookings (id)
);

CREATE TABLE external_registration_handoffs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    booking_code VARCHAR(64) NOT NULL,
    event_identity VARCHAR(128) NOT NULL,
    payload_reference VARCHAR(512) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_handoff_booking_event (booking_id, event_identity),
    CONSTRAINT fk_erh_booking FOREIGN KEY (booking_id) REFERENCES workshop_bookings (id)
);

CREATE TABLE IF NOT EXISTS event_publication (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    timestamp TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    event_type VARCHAR(512) NOT NULL,
    listener_id VARCHAR(512) NOT NULL,
    serialized_event VARCHAR(4000) NOT NULL,
    publication_date TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    completion_date TIMESTAMP(6) WITH TIME ZONE NULL
);

-- Note: The test environment database is allowed to be reset by this feature migration. 
-- Production must not execute this without careful data migration of existing legacy bookings.
