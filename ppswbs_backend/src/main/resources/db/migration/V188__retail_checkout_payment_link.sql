-- FE-08 Session 2026-11-18: the VNPay link of an unpaid checkout is kept until the hold deadline, so the Member can
-- leave VNPay and pay later from "Chờ thanh toán" without a second VNPay transaction for the same checkout.
ALTER TABLE retail_grouped_checkouts
    ADD COLUMN payment_url VARCHAR(2048) NULL DEFAULT NULL AFTER payment_initiated_at,
    ADD COLUMN payment_url_expires_at DATETIME(6) NULL DEFAULT NULL AFTER payment_url;
