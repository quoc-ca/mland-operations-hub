-- V143__two_level_administrative_divisions_and_shipping_addresses.sql
-- Two-level administrative directory (Province -> Commune) and updated member shipping address model for FE-001 Account Management

CREATE TABLE IF NOT EXISTS administrative_directory_versions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    version VARCHAR(64) NOT NULL,
    source_url VARCHAR(255) NULL,
    effective_from DATETIME(6) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_adv_version (version),
    KEY idx_adv_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS administrative_provinces (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    directory_version VARCHAR(64) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ap_version_code (directory_version, code),
    KEY idx_ap_directory_version (directory_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS administrative_communes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    directory_version VARCHAR(64) NOT NULL,
    province_code VARCHAR(64) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ac_version_code (directory_version, code),
    KEY idx_ac_version_province (directory_version, province_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Recreate member_addresses with 2-level administrative division schema and optimistic-locking version
DROP TABLE IF EXISTS member_addresses;

CREATE TABLE member_addresses (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    recipient_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(15) NOT NULL,
    province_code VARCHAR(64) NOT NULL,
    province_name VARCHAR(128) NOT NULL,
    commune_code VARCHAR(64) NOT NULL,
    commune_name VARCHAR(128) NOT NULL,
    commune_type VARCHAR(32) NOT NULL,
    detail_address VARCHAR(255) NOT NULL,
    directory_version VARCHAR(64) NOT NULL,
    address_type VARCHAR(16) NOT NULL DEFAULT 'HOME',
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_member_addresses_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE,
    KEY idx_member_addresses_account (account_id),
    KEY idx_member_addresses_account_default (account_id, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed initial active directory version VN-2026-V1
INSERT INTO administrative_directory_versions (version, source_url, effective_from, status)
VALUES ('VN-2026-V1', 'https://danhmuchanhchinh.gso.gov.vn', '2026-01-01 00:00:00.000000', 'ACTIVE')
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- Seed provinces for VN-2026-V1
INSERT INTO administrative_provinces (directory_version, code, name) VALUES
('VN-2026-V1', 'HN', 'Thành phố Hà Nội'),
('VN-2026-V1', 'HCM', 'Thành phố Hồ Chí Minh'),
('VN-2026-V1', 'DN', 'Thành phố Đà Nẵng'),
('VN-2026-V1', 'BDG', 'Tỉnh Bình Dương'),
('VN-2026-V1', 'DNA', 'Tỉnh Đồng Nai'),
('VN-2026-V1', 'HP', 'Thành phố Hải Phòng'),
('VN-2026-V1', 'CT', 'Thành phố Cần Thơ')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Seed communes for Hà Nội (HN)
INSERT INTO administrative_communes (directory_version, province_code, code, name, type) VALUES
('VN-2026-V1', 'HN', 'PHTR', 'Phường Hàng Trống', 'WARD'),
('VN-2026-V1', 'HN', 'PHB', 'Phường Hàng Bạc', 'WARD'),
('VN-2026-V1', 'HN', 'PHG', 'Phường Hàng Gai', 'WARD'),
('VN-2026-V1', 'HN', 'PTT', 'Phường Tràng Tiền', 'WARD'),
('VN-2026-V1', 'HN', 'PDB', 'Phường Điện Bàn', 'WARD'),
('VN-2026-V1', 'HN', 'PDC', 'Phường Đội Cấn', 'WARD'),
('VN-2026-V1', 'HN', 'PGV', 'Phường Giảng Võ', 'WARD'),
('VN-2026-V1', 'HN', 'PDV', 'Phường Dịch Vọng', 'WARD'),
('VN-2026-V1', 'HN', 'PYH', 'Phường Yên Hòa', 'WARD'),
('VN-2026-V1', 'HN', 'PTH', 'Phường Trung Hòa', 'WARD'),
('VN-2026-V1', 'HN', 'PCL', 'Phường Cát Linh', 'WARD'),
('VN-2026-V1', 'HN', 'PLH', 'Phường Láng Hạ', 'WARD'),
('VN-2026-V1', 'HN', 'POCD', 'Phường Ô Chợ Dừa', 'WARD')
ON DUPLICATE KEY UPDATE name = VALUES(name), type = VALUES(type);

-- Seed communes for Hồ Chí Minh (HCM)
INSERT INTO administrative_communes (directory_version, province_code, code, name, type) VALUES
('VN-2026-V1', 'HCM', 'PBN', 'Phường Bến Nghé', 'WARD'),
('VN-2026-V1', 'HCM', 'PBT', 'Phường Bến Thành', 'WARD'),
('VN-2026-V1', 'HCM', 'PDK', 'Phường Đa Kao', 'WARD'),
('VN-2026-V1', 'HCM', 'PTD', 'Phường Tân Định', 'WARD'),
('VN-2026-V1', 'HCM', 'PCOL', 'Phường Cầu Ông Lãnh', 'WARD'),
('VN-2026-V1', 'HCM', 'PVTS', 'Phường Võ Thị Sáu', 'WARD'),
('VN-2026-V1', 'HCM', 'PSG', 'Phường Sài Gòn', 'WARD'),
('VN-2026-V1', 'HCM', 'PTP', 'Phường Tân Phong', 'WARD'),
('VN-2026-V1', 'HCM', 'PTPH', 'Phường Tân Phú', 'WARD'),
('VN-2026-V1', 'HCM', 'PPM', 'Phường Phú Mỹ', 'WARD'),
('VN-2026-V1', 'HCM', 'PTD1', 'Phường Thảo Điền', 'WARD'),
('VN-2026-V1', 'HCM', 'PAP', 'Phường An Phú', 'WARD'),
('VN-2026-V1', 'HCM', 'PLT', 'Phường Linh Trung', 'WARD'),
('VN-2026-V1', 'HCM', 'PHP', 'Phường Hiệp Phú', 'WARD')
ON DUPLICATE KEY UPDATE name = VALUES(name), type = VALUES(type);

-- Seed communes for Đà Nẵng (DN)
INSERT INTO administrative_communes (directory_version, province_code, code, name, type) VALUES
('VN-2026-V1', 'DN', 'PTT2', 'Phường Thạch Thang', 'WARD'),
('VN-2026-V1', 'DN', 'PHC1', 'Phường Hải Châu 1', 'WARD'),
('VN-2026-V1', 'DN', 'PBH', 'Phường Bình Hiên', 'WARD'),
('VN-2026-V1', 'DN', 'PVT', 'Phường Vĩnh Trung', 'WARD'),
('VN-2026-V1', 'DN', 'PTC', 'Phường Tân Chính', 'WARD'),
('VN-2026-V1', 'DN', 'PAHB', 'Phường An Hải Bắc', 'WARD'),
('VN-2026-V1', 'DN', 'PPM2', 'Phường Phước Mỹ', 'WARD')
ON DUPLICATE KEY UPDATE name = VALUES(name), type = VALUES(type);

-- Seed communes for Bình Dương (BDG)
INSERT INTO administrative_communes (directory_version, province_code, code, name, type) VALUES
('VN-2026-V1', 'BDG', 'PPC', 'Phường Phú Cường', 'WARD'),
('VN-2026-V1', 'BDG', 'PHT', 'Phường Hiệp Thành', 'WARD'),
('VN-2026-V1', 'BDG', 'PLTH', 'Phường Lái Thiêu', 'WARD'),
('VN-2026-V1', 'BDG', 'PAP2', 'Phường An Phú', 'WARD')
ON DUPLICATE KEY UPDATE name = VALUES(name), type = VALUES(type);

-- Seed communes for Đồng Nai (DNA)
INSERT INTO administrative_communes (directory_version, province_code, code, name, type) VALUES
('VN-2026-V1', 'DNA', 'PTD3', 'Phường Trảng Dài', 'WARD'),
('VN-2026-V1', 'DNA', 'PTP2', 'Phường Tân Phong', 'WARD'),
('VN-2026-V1', 'DNA', 'PQT', 'Phường Quyết Thắng', 'WARD')
ON DUPLICATE KEY UPDATE name = VALUES(name), type = VALUES(type);

-- Seed communes for Hải Phòng (HP)
INSERT INTO administrative_communes (directory_version, province_code, code, name, type) VALUES
('VN-2026-V1', 'HP', 'PHVT', 'Phường Hoàng Văn Thụ', 'WARD'),
('VN-2026-V1', 'HP', 'PPBC', 'Phường Phan Bội Châu', 'WARD'),
('VN-2026-V1', 'HP', 'PMT', 'Phường Máy Tơ', 'WARD'),
('VN-2026-V1', 'HP', 'PCD', 'Phường Cầu Đất', 'WARD')
ON DUPLICATE KEY UPDATE name = VALUES(name), type = VALUES(type);

-- Seed communes for Cần Thơ (CT)
INSERT INTO administrative_communes (directory_version, province_code, code, name, type) VALUES
('VN-2026-V1', 'CT', 'PTA', 'Phường Tân An', 'WARD'),
('VN-2026-V1', 'CT', 'PAC', 'Phường An Cư', 'WARD'),
('VN-2026-V1', 'CT', 'PXK', 'Phường Xuân Khánh', 'WARD')
ON DUPLICATE KEY UPDATE name = VALUES(name), type = VALUES(type);
