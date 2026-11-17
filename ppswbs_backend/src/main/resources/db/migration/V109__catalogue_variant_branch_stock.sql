-- Feature 002 US10 (Session 2026-11-08): quantity of each size per branch.
-- The branch is a workshop_locations row (owner 003.2); no cross-module foreign key is declared.
-- catalogue_variants.remaining_unsold stays and is kept equal to the sum of the size's branch rows by the
-- catalogue (FE-08 still reads and deducts that total until it sends the branch, FR-053).
CREATE TABLE catalogue_variant_branch_stock (
    id BIGINT NOT NULL AUTO_INCREMENT,
    variant_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    remaining_unsold DECIMAL(19,0) NOT NULL,
    sale_enabled TINYINT(1) NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_catalogue_branch_stock (variant_id, location_id),
    KEY idx_catalogue_branch_stock_location (location_id, variant_id),
    CONSTRAINT fk_catalogue_branch_stock_variant FOREIGN KEY (variant_id) REFERENCES catalogue_variants (id),
    CONSTRAINT chk_catalogue_branch_stock_quantity CHECK (remaining_unsold >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- FR-055 / Q-B1: existing quantities move to the default branch, chosen by code (ids differ between machines).
-- Branch codes are created in the UI, so a database may not have this code: then nothing is moved and the
-- existing products stay "not assigned to a branch" until a Manager adds a branch (the old quantity is
-- offered for it). Re-running inserts nothing twice.
INSERT INTO catalogue_variant_branch_stock (variant_id, location_id, remaining_unsold, sale_enabled, version)
SELECT v.id, l.id, v.remaining_unsold, 1, 0
FROM catalogue_variants v
JOIN workshop_locations l ON l.code = 'HN-HOANKIEM'
WHERE NOT EXISTS (
    SELECT 1 FROM catalogue_variant_branch_stock s WHERE s.variant_id = v.id AND s.location_id = l.id
);
