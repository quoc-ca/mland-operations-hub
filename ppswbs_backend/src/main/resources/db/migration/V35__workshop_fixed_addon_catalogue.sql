CREATE TABLE workshop_addons (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reference CHAR(36) NOT NULL,
    package_id BIGINT NOT NULL,
    location_id BIGINT UNSIGNED NOT NULL,
    description VARCHAR(200) NOT NULL,
    price_vnd BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_workshop_addons_reference (reference),
    KEY ix_workshop_addons_scope_state (package_id, location_id, state),
    CONSTRAINT fk_workshop_addons_package FOREIGN KEY (package_id) REFERENCES workshop_packages (id),
    CONSTRAINT fk_workshop_addons_location FOREIGN KEY (location_id) REFERENCES workshop_locations (id),
    CONSTRAINT chk_workshop_addons_price CHECK (price_vnd > 0),
    CONSTRAINT chk_workshop_addons_state CHECK (state IN ('DRAFT', 'ACTIVE', 'RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
