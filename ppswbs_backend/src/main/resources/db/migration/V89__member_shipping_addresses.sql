-- V89__member_shipping_addresses.sql
-- Member shipping addresses (Shopee model) for Feature FE-001 Account Management

CREATE TABLE member_addresses (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    recipient_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(15) NOT NULL,
    province_id VARCHAR(64) NULL DEFAULT NULL,
    province_name VARCHAR(128) NOT NULL,
    district_id VARCHAR(64) NULL DEFAULT NULL,
    district_name VARCHAR(128) NOT NULL,
    ward_id VARCHAR(64) NULL DEFAULT NULL,
    ward_name VARCHAR(128) NOT NULL,
    hamlet_id VARCHAR(64) NULL DEFAULT NULL,
    hamlet_name VARCHAR(128) NULL DEFAULT NULL,
    detail_address VARCHAR(255) NOT NULL,
    address_type VARCHAR(16) NOT NULL DEFAULT 'HOME',
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_member_addresses_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE,
    KEY idx_member_addresses_account (account_id),
    KEY idx_member_addresses_account_default (account_id, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

