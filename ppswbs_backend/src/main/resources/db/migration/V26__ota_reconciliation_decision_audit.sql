CREATE TABLE ota_reconciliation_decisions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    decision_reference CHAR(36) NOT NULL,
    work_item_id BIGINT NULL,
    mapping_id BIGINT NULL,
    provider_code VARCHAR(32) NOT NULL,
    location_reference VARCHAR(128) NOT NULL,
    actor_account_reference VARCHAR(128) NOT NULL,
    actor_role VARCHAR(16) NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    action_scope VARCHAR(32) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    operation_reference CHAR(36) NOT NULL,
    correlation_reference CHAR(36) NOT NULL,
    idempotency_key_hash CHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    source_version BIGINT NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ota_recon_decision_reference (decision_reference),
    UNIQUE KEY uq_ota_recon_operation_reference (operation_reference),
    UNIQUE KEY uq_ota_recon_actor_idempotency (actor_account_reference, idempotency_key_hash),
    KEY idx_ota_recon_decision_work_item (work_item_id, recorded_at),
    KEY idx_ota_recon_decision_mapping (mapping_id, recorded_at),
    CONSTRAINT chk_ota_recon_decision_subject CHECK (
        work_item_id IS NOT NULL OR mapping_id IS NOT NULL
    ),
    CONSTRAINT chk_ota_recon_decision_source_version CHECK (
        source_version IS NULL OR source_version > 0
    ),
    CONSTRAINT fk_ota_recon_decision_work_item FOREIGN KEY (work_item_id)
        REFERENCES ota_reconciliation_work_items (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ota_recon_decision_mapping FOREIGN KEY (mapping_id)
        REFERENCES ota_channel_mappings (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
