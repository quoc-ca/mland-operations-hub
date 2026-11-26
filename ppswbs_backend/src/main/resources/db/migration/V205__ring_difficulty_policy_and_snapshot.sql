-- V205__ring_difficulty_policy_and_snapshot.sql
-- Feature-012 / FE-09: Admin-owned immutable ring difficulty policy, preview proofs,
-- publication audit, general-design nullable package_reference, and evaluation snapshots.

CREATE TABLE ring_difficulty_policy_versions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    rules_version VARCHAR(64) NOT NULL,
    engraving_bonus DECIMAL(18,4) NOT NULL,
    custom_image_bonus_per_image DECIMAL(18,4) NOT NULL,
    easy_max DECIMAL(18,4) NOT NULL,
    medium_max DECIMAL(18,4) NOT NULL,
    hard_max DECIMAL(18,4) NOT NULL,
    very_hard_max DECIMAL(18,4) NOT NULL,
    wax_above DECIMAL(18,4) NOT NULL,
    silver_clay_gemstones_above INT NOT NULL,
    published_by VARCHAR(128) NOT NULL,
    published_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_rdpv_rules_version (rules_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ring_difficulty_option_scores (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    policy_version_id BIGINT UNSIGNED NOT NULL,
    option_kind VARCHAR(32) NOT NULL,
    option_reference VARCHAR(128) NOT NULL,
    score DECIMAL(18,4) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_rdos_policy_kind_ref (policy_version_id, option_kind, option_reference),
    CONSTRAINT fk_rdos_policy_version FOREIGN KEY (policy_version_id) REFERENCES ring_difficulty_policy_versions (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ring_difficulty_effective_policy (
    policy_scope VARCHAR(64) NOT NULL,
    policy_version_id BIGINT UNSIGNED NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (policy_scope),
    CONSTRAINT fk_rdep_policy_version FOREIGN KEY (policy_version_id) REFERENCES ring_difficulty_policy_versions (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ring_difficulty_catalogue_revision (
    revision_scope VARCHAR(64) NOT NULL,
    revision_number BIGINT UNSIGNED NOT NULL DEFAULT 1,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (revision_scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ring_difficulty_preview_proofs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    proof_reference_hash VARCHAR(128) NOT NULL,
    admin_account_reference VARCHAR(128) NOT NULL,
    candidate_digest VARCHAR(128) NOT NULL,
    expected_policy_version_id BIGINT UNSIGNED NOT NULL,
    expected_catalogue_revision BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_rdpp_proof_hash (proof_reference_hash),
    KEY idx_rdpp_expires_at (expires_at),
    CONSTRAINT fk_rdpp_expected_policy FOREIGN KEY (expected_policy_version_id) REFERENCES ring_difficulty_policy_versions (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ring_difficulty_publication_audit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_reference VARCHAR(128) NOT NULL,
    previous_policy_version_id BIGINT UNSIGNED NULL,
    new_policy_version_id BIGINT UNSIGNED NOT NULL,
    correlation_id VARCHAR(128) NULL,
    reason VARCHAR(500) NULL,
    old_values_snapshot JSON NULL,
    new_values_snapshot JSON NOT NULL,
    published_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_rdpa_published_at (published_at),
    CONSTRAINT fk_rdpa_prev_policy FOREIGN KEY (previous_policy_version_id) REFERENCES ring_difficulty_policy_versions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_rdpa_new_policy FOREIGN KEY (new_policy_version_id) REFERENCES ring_difficulty_policy_versions (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE ring_designs
    MODIFY COLUMN difficulty_score DECIMAL(18,4) NULL,
    MODIFY COLUMN package_reference VARCHAR(128) NULL,
    ADD COLUMN policy_version_id BIGINT UNSIGNED NULL,
    ADD COLUMN difficulty_level VARCHAR(32) NULL,
    ADD COLUMN recommendation_code VARCHAR(64) NULL,
    ADD COLUMN recommended_package_reference VARCHAR(128) NULL,
    ADD COLUMN material_disclosure VARCHAR(500) NULL,
    ADD COLUMN evaluated_at DATETIME(6) NULL,
    ADD CONSTRAINT fk_ring_designs_policy_version FOREIGN KEY (policy_version_id) REFERENCES ring_difficulty_policy_versions (id) ON DELETE RESTRICT;

ALTER TABLE ring_catalogue_models
    MODIFY COLUMN package_reference VARCHAR(128) NULL;

UPDATE ring_catalogue_models
SET package_reference = NULL
WHERE material_reference IN ('MAT-COPPER', 'MAT-STAINLESS-STEEL');

-- Seed initial effective difficulty policy from active catalogue scores and baseline coefficients/cutoffs
INSERT INTO ring_difficulty_policy_versions (
    rules_version,
    engraving_bonus,
    custom_image_bonus_per_image,
    easy_max,
    medium_max,
    hard_max,
    very_hard_max,
    wax_above,
    silver_clay_gemstones_above,
    published_by,
    published_at
) VALUES (
    '2026.11-v1.0',
    0.5000,
    0.3000,
    3.0000,
    6.0000,
    9.0000,
    11.0000,
    9.0000,
    5,
    'SYSTEM_SEED',
    CURRENT_TIMESTAMP(6)
);

INSERT INTO ring_difficulty_option_scores (policy_version_id, option_kind, option_reference, score)
SELECT p.id, 'MATERIAL', m.material_code, m.difficulty_score
FROM ring_difficulty_policy_versions p
JOIN ring_materials m ON m.is_active = TRUE
WHERE p.rules_version = '2026.11-v1.0';

INSERT INTO ring_difficulty_option_scores (policy_version_id, option_kind, option_reference, score)
SELECT p.id, 'SHAPE', s.shape_code, s.difficulty_score
FROM ring_difficulty_policy_versions p
JOIN ring_shapes s ON s.is_active = TRUE
WHERE p.rules_version = '2026.11-v1.0';

INSERT INTO ring_difficulty_option_scores (policy_version_id, option_kind, option_reference, score)
SELECT p.id, 'GEMSTONE', g.gemstone_code, g.difficulty_score
FROM ring_difficulty_policy_versions p
JOIN ring_gemstones g ON g.is_active = TRUE
WHERE p.rules_version = '2026.11-v1.0';

INSERT INTO ring_difficulty_option_scores (policy_version_id, option_kind, option_reference, score)
SELECT p.id, 'ATTACHMENT', a.attachment_code, a.difficulty_score
FROM ring_difficulty_policy_versions p
JOIN ring_attachments a ON a.is_active = TRUE
WHERE p.rules_version = '2026.11-v1.0';

INSERT INTO ring_difficulty_effective_policy (policy_scope, policy_version_id, updated_at)
SELECT 'WORKSHOP_RING_DIFFICULTY', p.id, CURRENT_TIMESTAMP(6)
FROM ring_difficulty_policy_versions p
WHERE p.rules_version = '2026.11-v1.0';

INSERT INTO ring_difficulty_catalogue_revision (revision_scope, revision_number, updated_at)
VALUES ('WORKSHOP_RING_CATALOGUE', 1, CURRENT_TIMESTAMP(6));

INSERT INTO ring_difficulty_publication_audit (
    actor_reference,
    previous_policy_version_id,
    new_policy_version_id,
    correlation_id,
    reason,
    old_values_snapshot,
    new_values_snapshot,
    published_at
)
SELECT
    'SYSTEM_SEED',
    NULL,
    p.id,
    'MIGRATION-V205',
    'Initial policy seed from active catalogue scores and baseline thresholds',
    NULL,
    '{"rulesVersion":"2026.11-v1.0"}',
    p.published_at
FROM ring_difficulty_policy_versions p
WHERE p.rules_version = '2026.11-v1.0';
