-- 003.2 Phase 18 (FR-037, WF-09): which Package is sold at which location.
-- A missing row means INACTIVE; no hard delete. Rollback = drop the table (only the matrix is lost).
CREATE TABLE workshop_package_location_availability (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    package_id BIGINT NOT NULL,
    location_id BIGINT UNSIGNED NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_by_account_reference VARCHAR(64) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wpla_package_location (package_id, location_id),
    KEY idx_wpla_location (location_id),
    CONSTRAINT fk_wpla_package FOREIGN KEY (package_id) REFERENCES workshop_packages (id),
    CONSTRAINT fk_wpla_location FOREIGN KEY (location_id) REFERENCES workshop_locations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Initial matrix: Hoan Kiem x 5 Packages; Tay Ho x 3 Packages; Da Nang none.
INSERT INTO workshop_package_location_availability
    (package_id, location_id, active, version, created_at, updated_at)
SELECT p.id, l.id, 1, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM workshop_packages p
JOIN workshop_locations l ON l.code = 'HN-HOANKIEM'
WHERE p.name IN ('Signature Workshop - Silver 925', 'Signature Workshop - Brass',
                 'Silver Clay Workshop - 5g', 'Silver Clay Workshop - 7g', 'Wax Casting Workshop');

INSERT INTO workshop_package_location_availability
    (package_id, location_id, active, version, created_at, updated_at)
SELECT p.id, l.id, 1, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM workshop_packages p
JOIN workshop_locations l ON l.code = 'HN-TAYHO'
WHERE p.name IN ('Signature Workshop - Brass', 'Signature Workshop - Silver 925', 'Wax Casting Workshop');

-- An unmatched Package name or location code must not be skipped silently: if the seed did not produce
-- exactly 8 rows, this insert violates NOT NULL / the FK and the migration fails.
INSERT INTO workshop_package_location_availability
    (package_id, location_id, active, version, created_at, updated_at)
SELECT NULL, 0, 0, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM (SELECT COUNT(*) AS seeded FROM workshop_package_location_availability) AS seed_check
WHERE seed_check.seeded <> 8;
