-- Local/test-only seed data for exercising the FE-08 retail flow.
-- Safe to re-run against a disposable local database. Do not use in shared/staging/production databases.

SET @now = UTC_TIMESTAMP(6);

-- A selling location published through Workshop Operations.
INSERT INTO workshop_locations (code, display_name, address_snapshot, active, created_at, updated_at)
VALUES ('FE08-DEMO-HN-01', 'MLAND Hoan Kiem', '83 Ma May, Hoan Kiem, Ha Noi', 1, @now, @now)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    address_snapshot = VALUES(address_snapshot),
    active = 1,
    updated_at = @now;

-- A public catalogue category.
INSERT INTO catalogue_categories (name, name_key, description, active, version, created_at, updated_at)
VALUES ('FE-08 Demo Rings', 0x666530382d64656d6f2d72696e6773,
        'Local test data for the retail sales flow.', 1, 0, @now, @now)
ON DUPLICATE KEY UPDATE active = 1, updated_at = @now;

SELECT id INTO @category_id
FROM catalogue_categories
WHERE name_key = 0x666530382d64656d6f2d72696e6773
LIMIT 1;

-- A published product with one sellable size.
SELECT id INTO @product_id
FROM catalogue_products
WHERE name = 'Moonlight Silver Ring - FE-08 Demo' AND deleted_at IS NULL
ORDER BY id
LIMIT 1;

INSERT INTO catalogue_products (name, description, category_id, published, first_published_at,
                                deleted_at, deleted_by, content_version, version, created_at, updated_at)
SELECT 'Moonlight Silver Ring - FE-08 Demo',
       'A local test product for the FE-08 cart and checkout flow.',
       @category_id, 1, @now, NULL, NULL, 1, 0, @now, @now
FROM DUAL
WHERE @product_id IS NULL;

SELECT id INTO @product_id
FROM catalogue_products
WHERE name = 'Moonlight Silver Ring - FE-08 Demo' AND deleted_at IS NULL
ORDER BY id
LIMIT 1;

SELECT id INTO @variant_id
FROM catalogue_variants
WHERE product_id = @product_id AND ring_size = 7
LIMIT 1;

INSERT INTO catalogue_variants (product_id, ring_size, sku, remaining_unsold, sale_enabled,
                                applied_price, applied_proposal_id, applied_sequence,
                                pending_proposal_id, version)
SELECT @product_id, 7, 'FE08-MOON-S7', 12, 1, NULL, NULL, 0, NULL, 0
FROM DUAL
WHERE @variant_id IS NULL;

SELECT id INTO @variant_id
FROM catalogue_variants
WHERE product_id = @product_id AND ring_size = 7
LIMIT 1;

-- The approved price is intentionally recorded through the same proposal/application tables as real data.
INSERT INTO catalogue_price_proposals (variant_id, review_id, revision, version, state, effective,
                                       proposed_amount, approved_amount, currency, base_applied_sequence,
                                       terms_digest, submitted_by, submitted_at, decided_by, decided_at,
                                       reason, supersedes_id)
SELECT @variant_id, NULL, 1, 0, 'APPROVED', 1, 150000, 150000, 'VND', 0,
       REPEAT('0', 64), 'local-demo', @now, 'local-demo', @now, NULL, NULL
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM catalogue_price_proposals
    WHERE variant_id = @variant_id AND state = 'APPROVED' AND approved_amount = 150000
);

SELECT id INTO @proposal_id
FROM catalogue_price_proposals
WHERE variant_id = @variant_id AND state = 'APPROVED' AND approved_amount = 150000
ORDER BY id DESC
LIMIT 1;

UPDATE catalogue_variants
SET remaining_unsold = GREATEST(remaining_unsold, 12),
    sale_enabled = 1,
    applied_price = 150000,
    applied_proposal_id = @proposal_id,
    applied_sequence = GREATEST(applied_sequence, 1)
WHERE id = @variant_id;

INSERT INTO catalogue_price_applications (variant_id, proposal_id, applied_sequence, old_amount,
                                          new_amount, actor_id, applied_at)
SELECT @variant_id, @proposal_id, 1, NULL, 150000, 'local-demo', @now
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM catalogue_price_applications WHERE proposal_id = @proposal_id
);

-- Public catalogue queries require at least one READY product image. The API will gracefully use its
-- normal image fallback locally because this fixture has no Cloudinary asset behind it.
SET @media_id = '00000000-0000-4000-8000-000000000808';
INSERT INTO catalogue_media_objects (id, storage_key, provider_asset_id, provider_version, provider_format,
                                     checksum, content_type, byte_count, state, uploaded_by, command_id,
                                     created_at, last_verified_at, first_attached_at, last_unreferenced_at,
                                     delete_requested_at, deleted_at)
VALUES (@media_id, 'local/demo/fe08-moonlight', 'local-demo-fe08-moonlight', '1', 'png',
        REPEAT('0', 64), 'image/png', 1, 'READY', 'local-demo',
        '00000000-0000-4000-8000-000000000809', @now, @now, @now, NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE state = 'READY', last_verified_at = @now;

INSERT INTO catalogue_product_images (product_id, media_object_id, alt_text, display_position, version)
SELECT @product_id, @media_id, 'Moonlight Silver Ring', 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM catalogue_product_images
    WHERE product_id = @product_id
      AND media_object_id = CAST(@media_id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci
);

UPDATE catalogue_product_images
SET alt_text = 'Moonlight Silver Ring'
WHERE product_id = @product_id
  AND media_object_id = CAST(@media_id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci;

INSERT INTO catalogue_media_references (media_object_id, owner_module, owner_record_ref, linked_at, released_at)
SELECT @media_id, 'productmanagement', CONCAT('product:', @product_id), @now, NULL
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM catalogue_media_references
    WHERE media_object_id = CAST(@media_id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci
      AND CAST(owner_module AS BINARY) = CAST('productmanagement' AS BINARY)
      AND CAST(owner_record_ref AS BINARY) = CAST(CONCAT('product:', @product_id) AS BINARY)
);

SELECT @product_id AS product_id, @variant_id AS variant_id,
       (SELECT id FROM workshop_locations WHERE code = 'FE08-DEMO-HN-01') AS location_id;
