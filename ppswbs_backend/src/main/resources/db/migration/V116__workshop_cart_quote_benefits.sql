-- V116: benefits on a cart quote (003.1 T092 with the 006 cart contract of 2026-11-12, D-010).
-- 006 decides the group discount, the points and which tickets the voucher covers; 003.1 stores that answer
-- with the quote and allocates it per ticket. The ticket order is the cart order of lines_json, so accept
-- rebuilds exactly the cart that was previewed and the terms digest still matches.

ALTER TABLE workshop_cart_quotes
    ADD COLUMN benefit_selection_json JSON NULL DEFAULT NULL AFTER lines_json,
    -- 0-based positions, in cart order, of the tickets the voucher covers (BenefitPreview.eligibleLineIndexes).
    ADD COLUMN eligible_ticket_indexes_json JSON NULL DEFAULT NULL AFTER benefit_selection_json,
    ADD COLUMN point_value_vnd BIGINT NOT NULL DEFAULT 0 AFTER point_discount_vnd,
    ADD COLUMN terms_digest VARCHAR(128) NULL DEFAULT NULL,
    ADD COLUMN loyalty_policy_revision BIGINT NULL DEFAULT NULL;

-- A quote that used points must carry the point value it used, so the per-ticket split is reproducible.
ALTER TABLE workshop_cart_quotes
    ADD CONSTRAINT ck_wcq_point_value CHECK (points_used = 0 OR point_value_vnd > 0);
