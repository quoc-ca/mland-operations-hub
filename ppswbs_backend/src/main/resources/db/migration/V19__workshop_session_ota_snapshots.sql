CREATE TABLE workshop_session_ota_snapshots (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_reference VARCHAR(36) NOT NULL,
    location_reference VARCHAR(64) NOT NULL,
    scheduled_start_at DATETIME(6) NOT NULL,
    scheduled_end_at DATETIME(6) NOT NULL,
    sellable_availability INT NOT NULL,
    sale_state VARCHAR(32) NOT NULL,
    source_version BIGINT NOT NULL,
    change_reference VARCHAR(64) NOT NULL,
    effective_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ota_snapshot_session_version (session_reference, source_version),
    KEY idx_ota_snapshot_session_current (session_reference, source_version DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
