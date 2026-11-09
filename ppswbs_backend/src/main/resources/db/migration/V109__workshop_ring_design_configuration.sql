-- V109__workshop_ring_design_configuration.sql
-- Feature 012 Workshop Ring Design Configuration physical schema and initial catalog seed

CREATE TABLE guest_design_sessions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_reference VARCHAR(128) NOT NULL,
    session_digest VARCHAR(128) NOT NULL,
    design_reference VARCHAR(64) NULL,
    status ENUM('ACTIVE', 'EXPIRED', 'REVOKED') NOT NULL DEFAULT 'ACTIVE',
    issued_at DATETIME(6) NOT NULL,
    last_activity_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    correlation_id VARCHAR(128) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_gds_session_reference (session_reference),
    KEY idx_gds_session_digest (session_digest),
    KEY idx_gds_status_expires (status, expires_at),
    KEY idx_gds_design_ref (design_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ring_designs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    design_reference VARCHAR(64) NOT NULL,
    design_version INT NOT NULL DEFAULT 1,
    design_name VARCHAR(255) NULL,
    design_type VARCHAR(30) NOT NULL DEFAULT 'configured_design',
    owner_type VARCHAR(30) NOT NULL, -- 'GUEST_SESSION' or 'MEMBER'
    owner_reference VARCHAR(128) NOT NULL,
    package_reference VARCHAR(128) NOT NULL,
    source_model_reference VARCHAR(128) NULL,
    source_design_id BIGINT UNSIGNED NULL,
    material_reference VARCHAR(64) NULL,
    shape_reference VARCHAR(64) NULL,
    ring_size VARCHAR(30) NULL,
    component_snapshot JSON NULL,
    estimated_price DECIMAL(15,2) NULL,
    currency CHAR(3) NULL DEFAULT 'VND',
    difficulty_score DECIMAL(10,4) NULL,
    rules_version VARCHAR(64) NULL,
    status ENUM('DRAFT', 'VALIDATED', 'ATTACHED_TO_BOOKING', 'LOCKED', 'ARCHIVED') NOT NULL DEFAULT 'DRAFT',
    booking_reference VARCHAR(128) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ring_designs_ref_version (design_reference, design_version),
    KEY idx_ring_designs_owner (owner_type, owner_reference, status),
    KEY idx_ring_designs_package (package_reference),
    KEY idx_ring_designs_booking (booking_reference),
    CONSTRAINT fk_ring_designs_source FOREIGN KEY (source_design_id) REFERENCES ring_designs (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ring_design_idempotency_records (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    idempotency_key VARCHAR(128) NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    actor_scope VARCHAR(128) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_status INT NOT NULL,
    response_body JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_rdir_key_command_actor (idempotency_key, command_type, actor_scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ring_materials (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    material_code VARCHAR(64) NOT NULL,
    material_name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    purity VARCHAR(50) NULL,
    unit_price DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    difficulty_score DECIMAL(10,4) NOT NULL DEFAULT 1.0000,
    img_url TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ring_materials_code (material_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ring_shapes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    shape_code VARCHAR(64) NOT NULL,
    shape_name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    price_adjustment DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    difficulty_score DECIMAL(10,4) NOT NULL DEFAULT 1.0000,
    img_url TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ring_shapes_code (shape_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ring_gemstones (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    gemstone_code VARCHAR(64) NOT NULL,
    gemstone_name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    gemstone_type VARCHAR(100) NULL,
    color VARCHAR(50) NULL,
    cut_name VARCHAR(100) NULL,
    unit_price DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    difficulty_score DECIMAL(10,4) NOT NULL DEFAULT 1.0000,
    img_url TEXT NULL,
    max_quantity INT NOT NULL DEFAULT 10,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ring_gemstones_code (gemstone_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ring_attachments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    attachment_code VARCHAR(64) NOT NULL,
    attachment_name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    attachment_type VARCHAR(100) NULL,
    unit_price DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    difficulty_score DECIMAL(10,4) NOT NULL DEFAULT 1.0000,
    img_url TEXT NULL,
    max_quantity INT NOT NULL DEFAULT 10,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ring_attachments_code (attachment_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ring_catalogue_models (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    model_code VARCHAR(64) NOT NULL,
    package_reference VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    material_reference VARCHAR(64) NOT NULL,
    shape_reference VARCHAR(64) NOT NULL,
    default_components JSON NULL,
    img_url TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ring_catalogue_models_code (model_code),
    KEY idx_rcm_package (package_reference, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Initial Catalog Seeds
INSERT INTO ring_materials (material_code, material_name, description, purity, unit_price, currency, difficulty_score, is_active) VALUES
('MAT-SILVER-925', 'Bạc Ý 925 (Sterling Silver)', 'Bạc chuẩn quốc tế 92.5%, sáng bóng, dễ uốn nắn chế tác workshop', '92.5%', 250000.00, 'VND', 1.0000, TRUE),
('MAT-GOLD-14K', 'Vàng 14K (Yellow Gold)', 'Vàng tây 14K độ cứng cao, ánh vàng sang trọng bền lâu', '58.5%', 1800000.00, 'VND', 2.5000, TRUE),
('MAT-ROSE-GOLD-14K', 'Vàng Hồng 14K (Rose Gold)', 'Vàng hồng thời thượng, phù hợp nhẫn đôi và kỷ niệm', '58.5%', 1900000.00, 'VND', 2.5000, TRUE),
('MAT-WHITE-GOLD-14K', 'Vàng Trắng 14K (White Gold)', 'Vàng trắng tinh tế, phủ rhodium cao cấp', '58.5%', 1950000.00, 'VND', 2.8000, TRUE);

INSERT INTO ring_shapes (shape_code, shape_name, description, price_adjustment, currency, difficulty_score, is_active) VALUES
('SHP-CLASSIC-ROUND', 'Dáng tròn cổ điển (Classic Round)', 'Đai nhẫn tròn đều truyền thống, mang lại cảm giác đeo êm ái hàng ngày', 0.00, 'VND', 1.0000, TRUE),
('SHP-COMFORT-FIT', 'Vát cong lòng trong (Comfort Fit)', 'Lòng trong bo tròn giảm cấn tay, bề mặt ngoài phẳng hiện đại', 50000.00, 'VND', 1.2000, TRUE),
('SHP-TWISTED', 'Dáng xoắn vô cực (Twist Infinity)', 'Đai nhẫn xoắn lượn sóng tượng trưng cho sự gắn kết vĩnh cửu', 120000.00, 'VND', 2.0000, TRUE),
('SHP-HAMMERED', 'Bề mặt búa gõ (Hammered Texture)', 'Hiệu ứng giác cắt thủ công mộc mạc, đậm chất workshop thủ công', 80000.00, 'VND', 1.8000, TRUE);

INSERT INTO ring_gemstones (gemstone_code, gemstone_name, description, gemstone_type, color, cut_name, unit_price, currency, difficulty_score, max_quantity, is_active) VALUES
('GEM-CZ-WHITE-2MM', 'Đá CZ Trắng 2mm (Cubic Zirconia)', 'Đá nhân tạo lấp lánh giác cắt tròn rực rỡ', 'CZ', 'Trắng trong', 'Brilliant Round', 80000.00, 'VND', 0.8000, 10, TRUE),
('GEM-CZ-BLUE-2MM', 'Đá CZ Xanh Lam 2mm (Sapphire Blue)', 'Đá CZ xanh sapphire biểu tượng hy vọng', 'CZ', 'Xanh lam', 'Round Cut', 90000.00, 'VND', 0.8000, 10, TRUE),
('GEM-CZ-RED-2MM', 'Đá CZ Đỏ Ruby 2mm (Ruby Red)', 'Đá CZ đỏ nồng nàn đại diện cho tình yêu son sắt', 'CZ', 'Đỏ ruby', 'Round Cut', 90000.00, 'VND', 0.8000, 10, TRUE),
('GEM-MOISSANITE-3MM', 'Đá Moissanite 3mm', 'Đá moissanite độ tán sắc vượt trội, độ cứng 9.25 Mohs', 'Moissanite', 'D Color VVS1', 'Round Brilliant', 450000.00, 'VND', 1.5000, 4, TRUE);

INSERT INTO ring_attachments (attachment_code, attachment_name, description, attachment_type, unit_price, currency, difficulty_score, max_quantity, is_active) VALUES
('ATT-BEAD-EDGE', 'Viền hạt bi Milgrain (Beaded Edge)', 'Viền hạt tròn li ti phong cách cổ điển vintage', 'ENGRAVING_TEXTURE', 100000.00, 'VND', 1.2000, 2, TRUE),
('ATT-SATIN-FINISH', 'Xử lý phay xước mờ (Satin Matte Finish)', 'Lớp hoàn thiện mờ nhám chống trầy xước, phong cách tối giản', 'FINISH', 60000.00, 'VND', 0.8000, 1, TRUE),
('ATT-HEART-EMBOSS', 'Dập nổi trái tim nhỏ (Mini Heart Emboss)', 'Chi tiết tim chìm tinh tế ở mặt ngoài hoặc mép nhẫn', 'STAMP', 70000.00, 'VND', 1.0000, 2, TRUE);

INSERT INTO ring_catalogue_models (model_code, package_reference, name, description, material_reference, shape_reference, default_components, img_url, is_active) VALUES
('MOD-WS-SILVER-CLASSIC', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Bạc Tròn Cơ Bản', 'Thiết kế nhẫn tròn cổ điển thanh lịch, hoàn hảo cho buổi trải nghiệm chế tác đầu tiên', 'MAT-SILVER-925', 'SHP-CLASSIC-ROUND', '{"components": [], "attachments": []}', 'https://images.unsplash.com/photo-1605100804763-247f67b3557e', TRUE),
('MOD-WS-SILVER-TWIST', 'PKG-COUPLE-SILVER', 'Mẫu nhẫn Bạc Xoắn Vô Cực', 'Thiết kế nhẫn đai xoắn mềm mại, điểm xuyết một viên đá CZ trắng tinh tế', 'MAT-SILVER-925', 'SHP-TWISTED', '{"components": [{"reference": "GEM-CZ-WHITE-2MM", "quantity": 1, "placement": "CENTER"}], "attachments": []}', 'https://images.unsplash.com/photo-1603561591411-07134e71a2a9', TRUE),
('MOD-WS-GOLD-COMFORT', 'PKG-PREMIUM-GOLD', 'Mẫu nhẫn Vàng 14K Hiện Đại', 'Thiết kế phẳng vát êm tay, hoàn thiện phay mờ kết hợp khắc chữ cá nhân hóa', 'MAT-GOLD-14K', 'SHP-COMFORT-FIT', '{"components": [], "attachments": [{"reference": "ATT-SATIN-FINISH", "quantity": 1, "placement": "OUTER_SURFACE"}]}', 'https://images.unsplash.com/photo-1598560917505-59a3ad559071', TRUE);

