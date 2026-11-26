-- Feature-004 Custom Manufacturing: Method 1B dedicated table for custom order shipments (Mock GHTK)
CREATE TABLE IF NOT EXISTS custom_order_shipments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    custom_order_id VARCHAR(36) NOT NULL,
    custom_order_reference VARCHAR(64) NOT NULL,
    tracking_number VARCHAR(64) NOT NULL UNIQUE,
    carrier_service VARCHAR(32) NOT NULL DEFAULT 'GHTK',
    shipping_address VARCHAR(500) NOT NULL,
    recipient_name VARCHAR(128) NULL,
    recipient_phone VARCHAR(32) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PREPARING',
    version INT NOT NULL DEFAULT 1,
    picked_up_at TIMESTAMP NULL,
    delivered_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cos_order_ref (custom_order_reference),
    INDEX idx_cos_tracking (tracking_number),
    INDEX idx_cos_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
