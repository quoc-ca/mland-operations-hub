-- 003.3 FR-040 (owner decision 2026-11-13): the system has no trustworthy payer name or source account to compare
-- with the refund account (VNPay returns neither; counter collections do not link to refunds), and comparing names
-- misjudges namesakes and spelling. The payer-name check is removed; the approving Manager and the transferring
-- actor check the holder name shown by the bank instead.
ALTER TABLE settlement_refund_requests
    DROP CHECK ck_srr_other_recipient;

ALTER TABLE settlement_refund_requests
    DROP COLUMN payer_label,
    DROP COLUMN recipient_is_payer,
    DROP COLUMN payer_confirmed_other_recipient;
