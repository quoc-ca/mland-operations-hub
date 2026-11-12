-- FE-06 (006) T100, FR-022 / US5.7 (decision 2026-11-12): a workshop whose fee after voucher and points is exactly
-- 0 VND is accepted; FE-03 confirms it without the payment gateway and settles it like a deposit. V11 still required
-- post_promotion_amount >= 1, so such a booking was refused by the database at accept. Only a negative amount stays
-- invalid (the calculator cannot produce one). Retail rows keep no post-promotion amount, as before.

ALTER TABLE loyalty_accepted_benefit DROP CHECK chk_loyalty_benefit_workshop;

ALTER TABLE loyalty_accepted_benefit
    ADD CONSTRAINT chk_loyalty_benefit_workshop CHECK (
        (source_kind = 'RETAIL' AND post_promotion_amount IS NULL)
        OR (source_kind = 'WORKSHOP' AND post_promotion_amount IS NOT NULL AND post_promotion_amount >= 0));
