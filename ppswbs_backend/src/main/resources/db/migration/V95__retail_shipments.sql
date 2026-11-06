-- FE-08 GHTK shipment: fictional shipment + lifecycle for Staff-managed fulfilment.
-- This is a stand-alone module linked only by grouped_checkout_id / retail_orders foreign keys.
-- It never touches product stock or loyalty ledger; cancellation/failure handling is out of scope.

-- retail_orders never persisted the recipient address used for its GHTK quote (quoteShipping/createCheckout
-- only validated it in-memory). Add the snapshot now so a mock shipment has a real recipient to show Staff.
ALTER TABLE retail_orders
    ADD COLUMN recipient_name     VARCHAR(255) NULL AFTER payable_amount,
    ADD COLUMN recipient_phone    VARCHAR(32)  NULL AFTER recipient_name,
    ADD COLUMN recipient_address  VARCHAR(512) NULL AFTER recipient_phone,
    ADD COLUMN recipient_province VARCHAR(128) NULL AFTER recipient_address,
    ADD COLUMN recipient_district VARCHAR(128) NULL AFTER recipient_province,
    ADD COLUMN recipient_ward     VARCHAR(128) NULL AFTER recipient_district;

ALTER TABLE retail_checkout_quote_branches
    ADD COLUMN recipient_name VARCHAR(255) NULL,
    ADD COLUMN recipient_phone VARCHAR(32) NULL,
    ADD COLUMN recipient_address VARCHAR(512) NULL,
    ADD COLUMN recipient_province VARCHAR(128) NULL,
    ADD COLUMN recipient_district VARCHAR(128) NULL,
    ADD COLUMN recipient_ward VARCHAR(128) NULL;

CREATE TABLE retail_shipping_quotes (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    member_account_id BIGINT NOT NULL,
    cart_id VARCHAR(36) NOT NULL,
    cart_version BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    binding_digest VARCHAR(64) NOT NULL,
    charge DECIMAL(19,0) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    KEY idx_shipping_quote_owner (member_account_id, expires_at),
    CONSTRAINT chk_shipping_quote_charge CHECK (charge >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE retail_shipments (
    id                      VARCHAR(36)        NOT NULL,
    grouped_checkout_id     VARCHAR(36)        NOT NULL,
    branch_order_id         VARCHAR(36)        NOT NULL,
    branch_order_identifier VARCHAR(36)        NOT NULL,
    location_id             BIGINT          NOT NULL,
    fulfilment_method       VARCHAR(16)     NOT NULL,
    mock_tracking_number    VARCHAR(32)     NOT NULL,
    status                  VARCHAR(32)     NOT NULL,
    recipient_name          VARCHAR(255),
    recipient_phone         VARCHAR(32),
    recipient_address       VARCHAR(512),
    province_name           VARCHAR(128),
    district_name           VARCHAR(128),
    ward_name               VARCHAR(128),
    created_at              DATETIME(6)     NOT NULL,
    updated_at              DATETIME(6)     NOT NULL,
    version                 INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_retail_shipments_branch_order (branch_order_id),
    UNIQUE KEY uk_retail_shipments_mock_tracking (mock_tracking_number),
    CONSTRAINT fk_retail_shipments_checkout
        FOREIGN KEY (grouped_checkout_id) REFERENCES retail_grouped_checkouts (id)
        ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT fk_retail_shipments_branch_order
        FOREIGN KEY (branch_order_id) REFERENCES retail_orders (id)
        ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE retail_shipment_status_history (
    id              VARCHAR(36)        NOT NULL,
    shipment_id     VARCHAR(36)        NOT NULL,
    status          VARCHAR(32)      NOT NULL,
    actor_id        BIGINT          NULL,
    actor_label     VARCHAR(128)    NOT NULL,
    note            VARCHAR(512)    NULL,
    created_at      DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_retail_shipment_status_history_shipment (shipment_id, created_at),
    CONSTRAINT fk_retail_shipment_history_shipment
        FOREIGN KEY (shipment_id) REFERENCES retail_shipments (id)
        ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
