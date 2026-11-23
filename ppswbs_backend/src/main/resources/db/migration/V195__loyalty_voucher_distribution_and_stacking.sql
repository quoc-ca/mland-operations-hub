-- FE-06 (006) Session 2026-11-22, FR-046/FR-047, D-015: Shopee-style vouchers.
-- Append-only: V10/V119/V134 are left untouched; changed constraints are dropped and re-added under the same name.

-- 1. Distribution of a voucher (FR-046). PUBLIC = usable without saving (every existing voucher);
--    CLAIM_REQUIRED = usable only after the Member saved it, and saving holds one use of the total
--    (claimed_count = saved uses not yet held or consumed). Automatic discounts have neither column.
ALTER TABLE promotion_program
    ADD COLUMN distribution VARCHAR(16) NULL DEFAULT NULL AFTER status,
    ADD COLUMN claimed_count BIGINT NULL DEFAULT NULL AFTER consumed_count;

UPDATE promotion_program SET distribution = 'PUBLIC', claimed_count = 0 WHERE kind = 'VOUCHER';

ALTER TABLE promotion_program
    DROP CHECK chk_promotion_program_auto;

ALTER TABLE promotion_program
    ADD CONSTRAINT chk_promotion_program_auto CHECK (
        kind <> 'AUTO_DISCOUNT' OR (code_original IS NULL AND code_key IS NULL
            AND total_limit IS NULL AND member_limit IS NULL AND held_count IS NULL AND consumed_count IS NULL
            AND distribution IS NULL AND claimed_count IS NULL));

ALTER TABLE promotion_program
    DROP CHECK chk_promotion_program_voucher;

-- Same rule as V10, with the saved uses of a CLAIM_REQUIRED voucher counted against the total,
-- and one use per Member for that distribution. The V10 sum is kept as it was, and claimed_count is read through
-- COALESCE, so a row written without the new columns can still never overbook (a NULL sum would pass the CHECK).
ALTER TABLE promotion_program
    ADD CONSTRAINT chk_promotion_program_voucher CHECK (
        kind <> 'VOUCHER' OR (code_original IS NOT NULL AND code_key IS NOT NULL AND total_limit >= 1
            AND member_limit >= 1 AND held_count >= 0 AND consumed_count >= 0
            AND distribution IN ('PUBLIC', 'CLAIM_REQUIRED') AND claimed_count >= 0
            AND (distribution = 'CLAIM_REQUIRED' OR claimed_count = 0)
            AND (distribution = 'PUBLIC' OR member_limit = 1)
            AND held_count + consumed_count <= total_limit
            AND held_count + consumed_count + COALESCE(claimed_count, 0) <= total_limit));

-- 2. An accepted benefit may carry the best automatic discount AND one voucher (FR-015/FR-047).
--    program_id/revision_id keep their meaning for every reader: the voucher when there is one, otherwise the
--    automatic discount. auto_* name the automatic discount whenever one applied (also when it is alone), and
--    auto_discount is its part of program_discount; the voucher part is program_discount - auto_discount.
ALTER TABLE loyalty_accepted_benefit
    ADD COLUMN auto_program_id BIGINT NULL DEFAULT NULL AFTER revision_id,
    ADD COLUMN auto_revision_id BIGINT NULL DEFAULT NULL AFTER auto_program_id,
    ADD COLUMN auto_discount DECIMAL(19,0) NOT NULL DEFAULT 0 AFTER program_discount,
    ADD KEY idx_loyalty_benefit_auto_program (auto_program_id),
    ADD CONSTRAINT fk_loyalty_benefit_auto_program FOREIGN KEY (auto_program_id)
        REFERENCES promotion_program (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_loyalty_benefit_auto_revision FOREIGN KEY (auto_revision_id)
        REFERENCES promotion_revision (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

-- Existing rows held one programme: an automatic discount moves into auto_*, a voucher stays as it is.
UPDATE loyalty_accepted_benefit b
    JOIN promotion_program p ON p.id = b.program_id
SET b.auto_program_id = b.program_id, b.auto_revision_id = b.revision_id, b.auto_discount = b.program_discount
WHERE p.kind = 'AUTO_DISCOUNT';

ALTER TABLE loyalty_accepted_benefit
    ADD CONSTRAINT chk_loyalty_benefit_auto CHECK (
        auto_discount >= 0 AND auto_discount <= program_discount
        AND ((auto_program_id IS NULL AND auto_revision_id IS NULL AND auto_discount = 0)
            OR (auto_program_id IS NOT NULL AND auto_revision_id IS NOT NULL)));

-- 3. Each settlement part / ticket keeps its share of the automatic discount, so the voucher share
--    (program_discount - auto_discount) is exact for voucher scope checks and restoration.
ALTER TABLE loyalty_accepted_benefit_part
    ADD COLUMN auto_discount DECIMAL(19,0) NOT NULL DEFAULT 0 AFTER program_discount;

UPDATE loyalty_accepted_benefit_part pt
    JOIN loyalty_accepted_benefit b ON b.id = pt.accepted_benefit_id
SET pt.auto_discount = pt.program_discount
WHERE b.auto_program_id IS NOT NULL;

ALTER TABLE loyalty_accepted_benefit_part
    ADD CONSTRAINT chk_loyalty_benefit_part_auto CHECK (auto_discount >= 0 AND auto_discount <= program_discount);
