-- FE-08: discount share of each branch order, decided when the quote is previewed (Session 2026-11-13).
-- The voucher share goes only to branches holding the lines the voucher discounts (FE-06 eligibleLineIndexes);
-- the points share follows each branch's amount after the voucher. NULL for quotes created before this column.
ALTER TABLE retail_checkout_quote_branches
    ADD COLUMN discount_amount DECIMAL(19,0) NULL DEFAULT NULL AFTER subtotal;
