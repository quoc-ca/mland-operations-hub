-- FE-08 now uses the canonical FE-02 numeric product-variant identifier.
-- Existing local/test demo rows must first be cleared with scripts/local-reset-retail-demo.sql.
DELIMITER $$
CREATE PROCEDURE assert_retail_variant_migration_safe()
BEGIN
    IF EXISTS (SELECT 1 FROM retail_order_items)
       OR EXISTS (SELECT 1 FROM retail_checkout_holds)
       OR EXISTS (SELECT 1 FROM retail_orders)
       OR EXISTS (SELECT 1 FROM retail_grouped_checkouts)
       OR EXISTS (SELECT 1 FROM retail_cart_lines)
       OR EXISTS (SELECT 1 FROM retail_carts) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Clear pre-FE-02 local/test retail rows before V24';
    END IF;
END$$
CALL assert_retail_variant_migration_safe()$$
DROP PROCEDURE assert_retail_variant_migration_safe$$
DELIMITER ;

ALTER TABLE retail_cart_lines
    MODIFY COLUMN variant_id BIGINT UNSIGNED NOT NULL;

ALTER TABLE retail_order_items
    MODIFY COLUMN variant_id BIGINT UNSIGNED NOT NULL;
