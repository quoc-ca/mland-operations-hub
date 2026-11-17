-- V11__loyalty_drop_workshop_payable.sql
-- Schema migration for Feature FE-06 (follow-up to V10, decided 2026-10-26; renumbered from V8 on 2026-10-27 to avoid the develop V6–V8).
-- Decision: the workshop deposit and the remaining amount belong to FE-03/003.1, which derives them from the
-- post-promotion amount that loyalty returns. Loyalty keeps only post_promotion_amount, so the two columns
-- and the check that tied them to it are removed. Append-only: V10 (formerly V7) is not edited.
-- A workshop snapshot must carry a post-promotion amount greater than zero (no free booking, FR-022); a retail
-- order never carries one.

ALTER TABLE loyalty_accepted_benefit DROP CHECK chk_loyalty_benefit_workshop;

ALTER TABLE loyalty_accepted_benefit
    DROP COLUMN deposit_amount,
    DROP COLUMN remaining_amount;

ALTER TABLE loyalty_accepted_benefit
    ADD CONSTRAINT chk_loyalty_benefit_workshop CHECK (
        (source_kind = 'RETAIL' AND post_promotion_amount IS NULL)
        OR (source_kind = 'WORKSHOP' AND post_promotion_amount IS NOT NULL AND post_promotion_amount >= 1));
