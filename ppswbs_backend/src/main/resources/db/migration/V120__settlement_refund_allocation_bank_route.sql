-- V120: route a refund allocation to the bank flow instead of VNPay Refund (003.3 T098, FR-038).
-- The allocation keeps recording which original VNPay payment the money came from — that evidence is still
-- what proves Mland holds it. What changes is how it goes back: a new lifecycle value hands the allocation
-- to the T097 request, and the allocation is only VERIFIED once a Manager confirms the bank transfer.
-- A legacy allocation already sitting at the gateway is left exactly as it is.

ALTER TABLE settlement_refund_allocations
    ADD COLUMN bank_refund_reference VARCHAR(128) NULL DEFAULT NULL AFTER lifecycle_status,
    ADD KEY idx_sra_bank_refund (bank_refund_reference);
