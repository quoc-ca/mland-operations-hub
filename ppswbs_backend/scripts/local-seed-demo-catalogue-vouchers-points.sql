-- Local-only demo data for FE-08 (retail) and FE-06 (vouchers, points).
-- Adds 5 published rings (sizes 6, 7, 8, 10 pieces per size at every active branch), 3 product vouchers saved
-- for every Member account, and 100,000 loyalty points for every Member account.
-- Safe to re-run: existing rows are reused, points are granted once per Member.
-- Never run against a shared, staging or production database.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @now = UTC_TIMESTAMP(6);

-- The demo branch from local-seed-fe08-demo.sql, so there is at least one selling branch.
INSERT INTO workshop_locations (code, display_name, address_snapshot, active, created_at, updated_at)
VALUES ('FE08-DEMO-HN-01', 'MLAND Hoan Kiem', '83 Ma May, Hoan Kiem, Ha Noi', 1, @now, @now)
ON DUPLICATE KEY UPDATE active = 1, updated_at = @now;

INSERT INTO catalogue_categories (name, name_key, description, active, version, created_at, updated_at)
VALUES ('FE-08 Demo Rings', 0x666530382d64656d6f2d72696e6773, 'Local test data for the retail sales flow.', 1, 0, @now, @now)
ON DUPLICATE KEY UPDATE active = 1, updated_at = @now;
SELECT id INTO @category_id FROM catalogue_categories WHERE name_key = 0x666530382d64656d6f2d72696e6773 LIMIT 1;

DROP PROCEDURE IF EXISTS seed_demo_ring;
DELIMITER //
CREATE PROCEDURE seed_demo_ring(IN p_name VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, IN p_description TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                                IN p_sku VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, IN p_price DECIMAL(19,0),
                                IN p_media_id CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci)
BEGIN
    DECLARE v_product BIGINT DEFAULT NULL;
    DECLARE v_variant BIGINT DEFAULT NULL;
    DECLARE v_proposal BIGINT DEFAULT NULL;
    DECLARE v_size SMALLINT DEFAULT 6;

    SELECT id INTO v_product FROM catalogue_products WHERE name = p_name COLLATE utf8mb4_unicode_ci AND deleted_at IS NULL ORDER BY id LIMIT 1;
    IF v_product IS NULL THEN
        INSERT INTO catalogue_products (name, description, category_id, published, first_published_at,
                                        deleted_at, deleted_by, content_version, version, created_at, updated_at)
        VALUES (p_name, p_description, @category_id, 1, @now, NULL, NULL, 1, 0, @now, @now);
        SET v_product = LAST_INSERT_ID();
    END IF;
    UPDATE catalogue_products SET published = 1, updated_at = @now WHERE id = v_product;

    WHILE v_size <= 8 DO
        SET v_variant = NULL;
        SET v_proposal = NULL;
        SELECT id INTO v_variant FROM catalogue_variants WHERE product_id = v_product AND ring_size = v_size LIMIT 1;
        IF v_variant IS NULL THEN
            INSERT INTO catalogue_variants (product_id, ring_size, sku, remaining_unsold, sale_enabled,
                                            applied_price, applied_proposal_id, applied_sequence, pending_proposal_id, version)
            VALUES (v_product, v_size, CONCAT(p_sku, '-S', v_size), 0, 1, NULL, NULL, 0, NULL, 0);
            SET v_variant = LAST_INSERT_ID();
        END IF;

        SELECT id INTO v_proposal FROM catalogue_price_proposals
        WHERE variant_id = v_variant AND state = 'APPROVED' AND approved_amount = p_price ORDER BY id DESC LIMIT 1;
        IF v_proposal IS NULL THEN
            INSERT INTO catalogue_price_proposals (variant_id, review_id, revision, version, state, effective,
                                                   proposed_amount, approved_amount, currency, base_applied_sequence,
                                                   terms_digest, submitted_by, submitted_at, decided_by, decided_at,
                                                   reason, supersedes_id)
            VALUES (v_variant, NULL, 1, 0, 'APPROVED', 1, p_price, p_price, 'VND', 0,
                    REPEAT('0', 64), 'local-demo', @now, 'local-demo', @now, NULL, NULL);
            SET v_proposal = LAST_INSERT_ID();
            INSERT INTO catalogue_price_applications (variant_id, proposal_id, applied_sequence, old_amount,
                                                      new_amount, actor_id, applied_at)
            VALUES (v_variant, v_proposal, 1, NULL, p_price, 'local-demo', @now);
        END IF;

        -- 10 pieces of this size at every active branch; the size total equals the sum of its branches.
        INSERT INTO catalogue_variant_branch_stock (variant_id, location_id, remaining_unsold, sale_enabled, version)
        SELECT v_variant, l.id, 10, 1, 0 FROM workshop_locations l WHERE l.active = 1
        ON DUPLICATE KEY UPDATE remaining_unsold = GREATEST(remaining_unsold, 10), sale_enabled = 1;

        UPDATE catalogue_variants v
        SET v.sale_enabled = 1,
            v.applied_price = p_price,
            v.applied_proposal_id = v_proposal,
            v.applied_sequence = GREATEST(v.applied_sequence, 1),
            v.remaining_unsold = (SELECT COALESCE(SUM(s.remaining_unsold), 0) FROM catalogue_variant_branch_stock s
                                  WHERE s.variant_id = v_variant)
        WHERE v.id = v_variant;

        SET v_size = v_size + 1;
    END WHILE;

    -- Public catalogue queries need one READY image; locally the API falls back to its placeholder image.
    INSERT INTO catalogue_media_objects (id, storage_key, provider_asset_id, provider_version, provider_format,
                                         checksum, content_type, byte_count, state, uploaded_by, command_id,
                                         created_at, last_verified_at, first_attached_at, last_unreferenced_at,
                                         delete_requested_at, deleted_at)
    VALUES (p_media_id, CONCAT('local/demo/', LOWER(p_sku)), CONCAT('local-demo-', LOWER(p_sku)), '1', 'png',
            REPEAT('0', 64), 'image/png', 1, 'READY', 'local-demo', p_media_id, @now, @now, @now, NULL, NULL, NULL)
    ON DUPLICATE KEY UPDATE state = 'READY', last_verified_at = @now;

    IF NOT EXISTS (SELECT 1 FROM catalogue_product_images WHERE product_id = v_product) THEN
        INSERT INTO catalogue_product_images (product_id, media_object_id, alt_text, display_position, version)
        VALUES (v_product, p_media_id, p_name, 0, 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM catalogue_media_references
                   WHERE media_object_id = p_media_id COLLATE utf8mb4_unicode_ci AND owner_module = 'productmanagement'
                     AND owner_record_ref = CONCAT('product:', v_product)) THEN
        INSERT INTO catalogue_media_references (media_object_id, owner_module, owner_record_ref, linked_at, released_at)
        VALUES (p_media_id, 'productmanagement', CONCAT('product:', v_product), @now, NULL);
    END IF;
END //
DELIMITER ;

CALL seed_demo_ring('Nhẫn bạc Hoa Mai - Demo', 'Nhẫn bạc 925 khắc hoa mai, phù hợp đeo hằng ngày.', 'DEMO-HOAMAI', 120000, '00000000-0000-4000-8000-000000000901');
CALL seed_demo_ring('Nhẫn đôi Bình Minh - Demo', 'Nhẫn đôi bạc mạ vàng hồng, có thể khắc tên.', 'DEMO-BINHMINH', 250000, '00000000-0000-4000-8000-000000000902');
CALL seed_demo_ring('Nhẫn Sóng Biển - Demo', 'Nhẫn bạc vân sóng, thiết kế thủ công tại xưởng MLAND.', 'DEMO-SONGBIEN', 480000, '00000000-0000-4000-8000-000000000903');
CALL seed_demo_ring('Nhẫn đá Thạch Anh Tím - Demo', 'Nhẫn bạc gắn đá thạch anh tím tự nhiên.', 'DEMO-THACHANH', 890000, '00000000-0000-4000-8000-000000000904');
CALL seed_demo_ring('Nhẫn vàng 10K Tinh Tú - Demo', 'Nhẫn vàng 10K đính đá CZ hình ngôi sao.', 'DEMO-TINHTU', 1450000, '00000000-0000-4000-8000-000000000905');
DROP PROCEDURE IF EXISTS seed_demo_ring;

-- Three product vouchers valid from yesterday for 60 days at every branch. FE-06 product vouchers apply only to
-- the products they target, so each one targets every published product.
SELECT COALESCE((SELECT id FROM accounts WHERE role IN ('MANAGER', 'ADMIN') ORDER BY id LIMIT 1),
                (SELECT id FROM accounts ORDER BY id LIMIT 1), 1) INTO @creator_id;
SET @start_at = DATE_SUB(@now, INTERVAL 1 DAY);
SET @end_at = DATE_ADD(@now, INTERVAL 60 DAY);

DROP PROCEDURE IF EXISTS seed_demo_voucher;
DELIMITER //
CREATE PROCEDURE seed_demo_voucher(IN p_code VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, IN p_name VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                                   IN p_kind VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                                   IN p_percent DECIMAL(9,6), IN p_fixed DECIMAL(19,0), IN p_max DECIMAL(19,0),
                                   IN p_minimum DECIMAL(19,0))
BEGIN
    DECLARE v_program BIGINT DEFAULT NULL;
    DECLARE v_revision BIGINT DEFAULT NULL;
    SELECT id INTO v_program FROM promotion_program
    WHERE code_key = CONVERT(UPPER(p_code) USING utf8mb4) COLLATE utf8mb4_bin LIMIT 1;
    IF v_program IS NULL THEN
        INSERT INTO promotion_program (kind, scope, code_original, code_key, status, current_revision_id,
                                       total_limit, member_limit, held_count, consumed_count, created_at, created_by,
                                       config_version, version)
        VALUES ('VOUCHER', 'PRODUCT', p_code, UPPER(p_code), 'ENABLED', NULL, 1000, 5, 0, 0, @now, @creator_id, 0, 0);
        SET v_program = LAST_INSERT_ID();
        INSERT INTO promotion_revision (program_id, revision_no, name, start_at, end_at, discount_kind, percent,
                                        fixed_amount, max_discount_amount, minimum_eligible_amount, allow_points,
                                        created_at, created_by)
        VALUES (v_program, 1, p_name, @start_at, @end_at, p_kind, p_percent, p_fixed, p_max, p_minimum, 1,
                @now, @creator_id);
        SET v_revision = LAST_INSERT_ID();
        UPDATE promotion_program SET current_revision_id = v_revision WHERE id = v_program;
    ELSE
        UPDATE promotion_program SET status = 'ENABLED' WHERE id = v_program;
        UPDATE promotion_revision r JOIN promotion_program p ON p.current_revision_id = r.id
        SET r.end_at = GREATEST(r.end_at, @end_at) WHERE p.id = v_program;
    END IF;
    -- Saved to every Member's voucher wallet (saving holds no quota).
    INSERT INTO promotion_member_program (program_id, member_id, saved_at, held_count, consumed_count, version)
    SELECT v_program, a.id, @now, 0, 0, 0 FROM accounts a WHERE a.role = 'MEMBER'
    ON DUPLICATE KEY UPDATE saved_at = COALESCE(saved_at, @now);
END //
DELIMITER ;

CALL seed_demo_voucher('MLAND50K', 'Giảm 50.000đ cho đơn từ 300.000đ', 'FIXED', NULL, 50000, 50000, 300000);
CALL seed_demo_voucher('MLAND10', 'Giảm 10% tối đa 100.000đ cho đơn từ 200.000đ', 'PERCENT', 10.000000, NULL, 100000, 200000);
CALL seed_demo_voucher('MLAND150K', 'Giảm 150.000đ cho đơn từ 1.000.000đ', 'FIXED', NULL, 150000, 150000, 1000000);
DROP PROCEDURE IF EXISTS seed_demo_voucher;

INSERT IGNORE INTO promotion_revision_target (revision_id, dimension, target_key)
SELECT p.current_revision_id, 'PRODUCT', CAST(c.id AS CHAR)
FROM promotion_program p
JOIN catalogue_products c ON c.published = 1 AND c.deleted_at IS NULL
WHERE p.code_key IN ('MLAND50K', 'MLAND10', 'MLAND150K');

-- 100,000 points per Member, posted once as a granted earn with its ledger row so the wallet matches the ledger.
SELECT current_policy_id INTO @policy_id FROM loyalty_registry WHERE id = 1;

INSERT INTO loyalty_wallet (member_id, balance_points, held_points, version)
SELECT a.id, 0, 0, 0 FROM accounts a WHERE a.role = 'MEMBER'
ON DUPLICATE KEY UPDATE member_id = member_id;

INSERT INTO loyalty_accepted_benefit (source_kind, source_ref, member_id, member_origin_eligible, accepted_at,
                                      original_deadline, source_revision, accepted_terms_digest, policy_id,
                                      program_id, revision_id, gross_amount, program_discount, requested_points,
                                      point_discount, earn_base, post_promotion_amount, points_state, voucher_state,
                                      earn_state, earned_points, settled_at, source_outcome_version, version)
SELECT 'RETAIL', CONCAT('local-demo-points-', a.id), a.id, 1, @now, @now, 0, 'local-demo-seed', @policy_id,
       NULL, NULL, 0, 0, 0, 0, 0, NULL, 'NONE', 'NONE', 'GRANTED', 100000, @now, 0, 0
FROM accounts a
WHERE a.role = 'MEMBER'
  AND NOT EXISTS (SELECT 1 FROM loyalty_accepted_benefit b
                  WHERE b.source_kind = 'RETAIL' AND b.source_ref = CONCAT('local-demo-points-', a.id));

INSERT INTO loyalty_point_ledger (member_id, accepted_benefit_id, entry_kind, delta_balance, delta_held,
                                  balance_after, held_after, occurred_at, original_debit_id)
SELECT b.member_id, b.id, 'EARN', 100000, 0, w.balance_points + 100000, w.held_points, @now, NULL
FROM loyalty_accepted_benefit b
JOIN loyalty_wallet w ON w.member_id = b.member_id
WHERE b.source_kind = 'RETAIL' AND b.source_ref LIKE 'local-demo-points-%'
  AND NOT EXISTS (SELECT 1 FROM loyalty_point_ledger l WHERE l.accepted_benefit_id = b.id AND l.entry_kind = 'EARN');

UPDATE loyalty_wallet w
JOIN loyalty_point_ledger l ON l.member_id = w.member_id AND l.entry_kind = 'EARN' AND l.occurred_at = @now
JOIN loyalty_accepted_benefit b ON b.id = l.accepted_benefit_id AND b.source_ref LIKE 'local-demo-points-%'
SET w.balance_points = w.balance_points + 100000, w.version = w.version + 1;

-- Summary
SELECT p.id AS product_id, p.name, COUNT(v.id) AS sizes, MIN(v.applied_price) AS price
FROM catalogue_products p JOIN catalogue_variants v ON v.product_id = p.id
WHERE p.name LIKE '%- Demo' GROUP BY p.id, p.name ORDER BY price;
SELECT code_original AS voucher, status FROM promotion_program WHERE code_key IN ('MLAND50K', 'MLAND10', 'MLAND150K');
SELECT a.id AS member_id, a.login_email, w.balance_points FROM accounts a JOIN loyalty_wallet w ON w.member_id = a.id
WHERE a.role = 'MEMBER';
