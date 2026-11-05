CREATE TABLE ota_readiness_assessments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assessment_reference CHAR(36) NOT NULL,
    mapping_id BIGINT NOT NULL,
    provider_code VARCHAR(32) NOT NULL,
    environment VARCHAR(16) NOT NULL,
    mapping_fingerprint CHAR(64) NOT NULL,
    readiness_record_reference VARCHAR(255) NOT NULL,
    decision_status VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    reviewer_account_reference VARCHAR(128) NOT NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ota_readiness_assessment_reference (assessment_reference),
    KEY idx_ota_readiness_mapping_recorded (mapping_id, recorded_at),
    CONSTRAINT chk_ota_readiness_provider CHECK (provider_code IN ('KLOOK', 'GETYOURGUIDE')),
    CONSTRAINT chk_ota_readiness_environment CHECK (environment IN ('TEST', 'PRODUCTION')),
    CONSTRAINT chk_ota_readiness_decision CHECK (decision_status IN ('APPROVED', 'REJECTED')),
    CONSTRAINT fk_ota_readiness_mapping FOREIGN KEY (mapping_id)
        REFERENCES ota_channel_mappings (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ota_readiness_check_evidence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assessment_id BIGINT NOT NULL,
    check_code VARCHAR(48) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    evidence_reference VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ota_readiness_check (assessment_id, check_code),
    CONSTRAINT chk_ota_readiness_check_outcome CHECK (outcome IN ('VERIFIED', 'FAILED', 'MISSING')),
    CONSTRAINT fk_ota_readiness_check_assessment FOREIGN KEY (assessment_id)
        REFERENCES ota_readiness_assessments (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
