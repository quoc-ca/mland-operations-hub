-- V125 (renumbered from V124 to follow develop's V124): complete V123's bank-refund route by allowing its durable waiting state.
-- V123 added bank_refund_reference, but the CHECK constraint created by V45 still accepted only
-- the legacy gateway lifecycle values. Keep migrations append-only and replace that constraint here.

ALTER TABLE settlement_refund_allocations
    DROP CHECK ck_settlement_refund_lifecycle,
    ADD CONSTRAINT ck_settlement_refund_lifecycle CHECK (
        lifecycle_status IN (
            'PREPARED', 'ACCEPTED', 'UNKNOWN', 'VERIFIED', 'REJECTED', 'PENDING_BANK_REFUND'
        )
    );
