-- Feature 011 §7.1 / 002 FR-063: a retail review is about the product, so its target_reference is the product id the
-- product page queries. Reviews written before this fix stored the ring variant id instead; point them at the product
-- of the variant bought on their order line. Rows already holding the product id are left unchanged.
-- CAST(... AS CHAR) takes the connection collation (utf8mb4_0900_ai_ci on MySQL 8), while reviews uses
-- utf8mb4_unicode_ci, so the product id is converted to the column's collation before it is compared.
UPDATE reviews r
    JOIN retail_order_items roi ON roi.id = r.source_reference
    JOIN catalogue_variants cv ON cv.id = roi.variant_id
SET r.target_reference = CAST(cv.product_id AS CHAR) COLLATE utf8mb4_unicode_ci
WHERE r.source_type = 'RETAIL_ORDER_LINE'
  AND r.target_type = 'RETAIL_PRODUCT'
  AND r.target_reference <> CAST(cv.product_id AS CHAR) COLLATE utf8mb4_unicode_ci;
