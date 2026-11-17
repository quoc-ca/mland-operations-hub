CREATE TABLE ota_channel_connections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider_code VARCHAR(32) NOT NULL,
    environment VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    credential_reference VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ota_connection_provider_environment (provider_code, environment),
    KEY idx_ota_connection_status (status),
    CONSTRAINT chk_ota_connection_environment CHECK (environment IN ('TEST', 'PRODUCTION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ota_channel_mappings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    connection_id BIGINT NOT NULL,
    offer_reference VARCHAR(128) NOT NULL,
    session_reference VARCHAR(128) NOT NULL,
    location_reference VARCHAR(128) NOT NULL,
    external_product_id VARCHAR(255) NOT NULL,
    external_option_id VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ota_mapping_external_option (connection_id, external_product_id, external_option_id),
    UNIQUE KEY uq_ota_mapping_session (connection_id, session_reference),
    KEY idx_ota_mapping_status (status),
    KEY idx_ota_mapping_location (location_reference),
    CONSTRAINT fk_ota_mapping_connection FOREIGN KEY (connection_id)
        REFERENCES ota_channel_connections (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ota_channel_price_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    mapping_id BIGINT NOT NULL,
    retail_amount_minor BIGINT NOT NULL,
    target_net_amount_minor BIGINT NOT NULL,
    commission_amount_minor BIGINT NULL,
    currency_code CHAR(3) NOT NULL,
    terms_reference VARCHAR(255) NOT NULL,
    effective_from DATETIME(6) NOT NULL,
    effective_to DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_ota_price_snapshot_mapping_effective (mapping_id, effective_from),
    CONSTRAINT chk_ota_price_retail_nonnegative CHECK (retail_amount_minor >= 0),
    CONSTRAINT chk_ota_price_net_nonnegative CHECK (target_net_amount_minor >= 0),
    CONSTRAINT chk_ota_price_commission_nonnegative CHECK (commission_amount_minor IS NULL OR commission_amount_minor >= 0),
    CONSTRAINT chk_ota_price_effective_window CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT fk_ota_price_snapshot_mapping FOREIGN KEY (mapping_id)
        REFERENCES ota_channel_mappings (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ota_outbound_changes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    mapping_id BIGINT NOT NULL,
    source_version BIGINT NOT NULL,
    source_change_reference VARCHAR(128) NOT NULL,
    change_kind VARCHAR(32) NOT NULL,
    canonical_snapshot JSON NOT NULL,
    status VARCHAR(24) NOT NULL,
    accepted_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ota_outbound_mapping_version_kind (mapping_id, source_version, change_kind),
    KEY idx_ota_outbound_status_created (status, created_at),
    CONSTRAINT chk_ota_outbound_source_version_positive CHECK (source_version > 0),
    CONSTRAINT fk_ota_outbound_mapping FOREIGN KEY (mapping_id)
        REFERENCES ota_channel_mappings (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ota_delivery_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    outbound_change_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    outcome VARCHAR(24) NOT NULL,
    provider_correlation_reference VARCHAR(255) NULL,
    failure_category VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ota_delivery_attempt_number (outbound_change_id, attempt_number),
    KEY idx_ota_delivery_attempt_outcome (outcome, started_at),
    CONSTRAINT chk_ota_delivery_attempt_positive CHECK (attempt_number > 0),
    CONSTRAINT chk_ota_delivery_attempt_time CHECK (completed_at IS NULL OR completed_at >= started_at),
    CONSTRAINT fk_ota_delivery_attempt_change FOREIGN KEY (outbound_change_id)
        REFERENCES ota_outbound_changes (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ota_inbound_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    connection_id BIGINT NOT NULL,
    external_event_reference VARCHAR(255) NULL,
    payload_fingerprint CHAR(64) NOT NULL,
    dedupe_key CHAR(64) NOT NULL,
    event_kind VARCHAR(32) NOT NULL,
    authentication_result VARCHAR(24) NOT NULL,
    processing_status VARCHAR(24) NOT NULL,
    outcome_reference VARCHAR(128) NULL,
    received_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ota_inbound_connection_dedupe (connection_id, dedupe_key),
    KEY idx_ota_inbound_processing_received (processing_status, received_at),
    KEY idx_ota_inbound_outcome_reference (outcome_reference),
    CONSTRAINT chk_ota_inbound_processed_time CHECK (processed_at IS NULL OR processed_at >= received_at),
    CONSTRAINT fk_ota_inbound_connection FOREIGN KEY (connection_id)
        REFERENCES ota_channel_connections (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ota_channel_bookings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    mapping_id BIGINT NOT NULL,
    external_booking_reference VARCHAR(255) NOT NULL,
    provider_status VARCHAR(32) NOT NULL,
    participant_count INT NOT NULL,
    mland_booking_reference VARCHAR(128) NULL,
    sales_channel_snapshot VARCHAR(32) NOT NULL,
    displayed_policy_reference VARCHAR(128) NOT NULL,
    displayed_policy_version VARCHAR(64) NOT NULL,
    booking_cutoff_at DATETIME(6) NULL,
    booking_cutoff_timezone VARCHAR(64) NULL,
    retail_amount_minor BIGINT NOT NULL,
    target_net_amount_minor BIGINT NOT NULL,
    commission_amount_minor BIGINT NULL,
    fee_amount_minor BIGINT NULL,
    accepted_amount_minor BIGINT NULL,
    currency_code CHAR(3) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ota_channel_booking_external (mapping_id, external_booking_reference),
    KEY idx_ota_channel_booking_status (provider_status),
    KEY idx_ota_channel_booking_mland_reference (mland_booking_reference),
    CONSTRAINT chk_ota_channel_booking_participants CHECK (participant_count > 0),
    CONSTRAINT chk_ota_channel_booking_retail_nonnegative CHECK (retail_amount_minor >= 0),
    CONSTRAINT chk_ota_channel_booking_net_nonnegative CHECK (target_net_amount_minor >= 0),
    CONSTRAINT chk_ota_channel_booking_commission_nonnegative CHECK (commission_amount_minor IS NULL OR commission_amount_minor >= 0),
    CONSTRAINT chk_ota_channel_booking_fee_nonnegative CHECK (fee_amount_minor IS NULL OR fee_amount_minor >= 0),
    CONSTRAINT chk_ota_channel_booking_accepted_nonnegative CHECK (accepted_amount_minor IS NULL OR accepted_amount_minor >= 0),
    CONSTRAINT fk_ota_channel_booking_mapping FOREIGN KEY (mapping_id)
        REFERENCES ota_channel_mappings (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ota_booking_ticket_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    channel_booking_id BIGINT NOT NULL,
    participant_ordinal INT NOT NULL,
    operations_ticket_reference VARCHAR(128) NOT NULL,
    linked_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ota_ticket_link_participant (channel_booking_id, participant_ordinal),
    UNIQUE KEY uq_ota_ticket_link_operations_reference (operations_ticket_reference),
    CONSTRAINT chk_ota_ticket_link_ordinal_positive CHECK (participant_ordinal > 0),
    CONSTRAINT fk_ota_ticket_link_booking FOREIGN KEY (channel_booking_id)
        REFERENCES ota_channel_bookings (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ota_customer_payment_evidence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    channel_booking_id BIGINT NOT NULL,
    external_evidence_reference VARCHAR(255) NOT NULL,
    evidence_kind VARCHAR(24) NOT NULL,
    evidence_status VARCHAR(24) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    correlation_reference VARCHAR(128) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ota_payment_evidence_external (channel_booking_id, external_evidence_reference),
    KEY idx_ota_payment_evidence_kind_status (evidence_kind, evidence_status),
    CONSTRAINT fk_ota_payment_evidence_booking FOREIGN KEY (channel_booking_id)
        REFERENCES ota_channel_bookings (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ota_payout_positions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    channel_booking_id BIGINT NOT NULL,
    price_snapshot_id BIGINT NULL,
    expected_amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_ota_payout_position_booking (channel_booking_id),
    KEY idx_ota_payout_position_status (status),
    CONSTRAINT fk_ota_payout_position_booking FOREIGN KEY (channel_booking_id)
        REFERENCES ota_channel_bookings (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ota_payout_position_price_snapshot FOREIGN KEY (price_snapshot_id)
        REFERENCES ota_channel_price_snapshots (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ota_payout_ledger_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payout_position_id BIGINT NOT NULL,
    entry_kind VARCHAR(24) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    external_evidence_reference VARCHAR(255) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    correlation_reference VARCHAR(128) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ota_payout_ledger_evidence (payout_position_id, entry_kind, external_evidence_reference),
    KEY idx_ota_payout_ledger_occurred (occurred_at),
    CONSTRAINT fk_ota_payout_ledger_position FOREIGN KEY (payout_position_id)
        REFERENCES ota_payout_positions (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ota_addon_bill_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    channel_booking_id BIGINT NOT NULL,
    settlement_quote_reference VARCHAR(128) NOT NULL,
    settlement_bill_reference VARCHAR(128) NULL,
    amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ota_addon_booking_quote (channel_booking_id, settlement_quote_reference),
    KEY idx_ota_addon_bill_reference (settlement_bill_reference),
    CONSTRAINT chk_ota_addon_amount_nonnegative CHECK (amount_minor >= 0),
    CONSTRAINT fk_ota_addon_booking FOREIGN KEY (channel_booking_id)
        REFERENCES ota_channel_bookings (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ota_reconciliation_work_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    mapping_id BIGINT NULL,
    inbound_event_id BIGINT NULL,
    channel_booking_id BIGINT NULL,
    payout_position_id BIGINT NULL,
    location_reference VARCHAR(128) NOT NULL,
    category VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    assigned_manager_reference VARCHAR(128) NULL,
    decision VARCHAR(64) NULL,
    resolution_reference VARCHAR(255) NULL,
    audit_reference VARCHAR(128) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_ota_reconciliation_location_status (location_reference, status),
    KEY idx_ota_reconciliation_assignee_status (assigned_manager_reference, status),
    CONSTRAINT chk_ota_reconciliation_has_subject CHECK (
        mapping_id IS NOT NULL OR inbound_event_id IS NOT NULL
        OR channel_booking_id IS NOT NULL OR payout_position_id IS NOT NULL
    ),
    CONSTRAINT fk_ota_reconciliation_mapping FOREIGN KEY (mapping_id)
        REFERENCES ota_channel_mappings (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ota_reconciliation_inbound_event FOREIGN KEY (inbound_event_id)
        REFERENCES ota_inbound_events (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ota_reconciliation_booking FOREIGN KEY (channel_booking_id)
        REFERENCES ota_channel_bookings (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ota_reconciliation_payout_position FOREIGN KEY (payout_position_id)
        REFERENCES ota_payout_positions (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
