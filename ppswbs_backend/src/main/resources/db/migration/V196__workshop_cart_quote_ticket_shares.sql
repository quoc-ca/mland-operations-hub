-- 003.1 FE-03: 006 now splits the programme discount per ticket into an automatic and a voucher share
-- (Session 2026-11-22). The quote keeps that split so the group is created from the same numbers that were shown.
-- voucher_discount_vnd keeps meaning the whole programme discount (automatic + voucher).
ALTER TABLE workshop_cart_quotes
    ADD COLUMN ticket_shares_json JSON NULL;
