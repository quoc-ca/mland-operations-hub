ALTER TABLE payment_owner_registrations DROP CHECK ck_payment_owner_kind;
ALTER TABLE payment_owner_registrations
    ADD CONSTRAINT ck_payment_owner_kind CHECK (payment_kind IN ('DEPOSIT', 'FINAL_PAYMENT', 'REFUND', 'RETAIL'));
