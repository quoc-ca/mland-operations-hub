-- Feature-004 Custom Manufacturing: CUSTOM_ORDER_FULL_PAYMENT is 25 characters.
-- V32 created this column at VARCHAR(24); V190 added the enum check value but
-- did not widen the column. Do not edit either already-applied migration.
ALTER TABLE payment_owner_registrations
    MODIFY payment_kind VARCHAR(64) NOT NULL;
