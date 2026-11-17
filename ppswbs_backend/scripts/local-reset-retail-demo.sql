-- Local/test-only reset for FE-08 rows created before product variants adopted numeric IDs.
-- Do not run against a shared, staging, or production database.
DELETE FROM retail_order_items;
DELETE FROM retail_checkout_holds;
DELETE FROM retail_orders;
DELETE FROM retail_grouped_checkouts;
DELETE FROM retail_cart_lines;
DELETE FROM retail_carts;
