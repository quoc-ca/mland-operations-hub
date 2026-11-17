-- Session 2026-11-14, 006 D-014 (ERROR-LOG BE-DESIGN-0005, option a): a Guest who books a workshop directly gets the
-- workshop automatic discount (FR-044), and 006 keeps the accepted snapshot of it like any other, so the discount
-- given to a Guest can be reconciled. Such a snapshot has no Member: member_id is NULL and the snapshot is identified
-- by (source_kind, source_ref), already unique (uk_loyalty_benefit_source); for a workshop the source reference is
-- the booking group code.
-- A snapshot without a Member is a workshop one that never holds points or a voucher and never earns points (a zero
-- grant at most). Member snapshots keep every rule they had. Append-only: V10 is left untouched.
ALTER TABLE loyalty_accepted_benefit
    MODIFY member_id BIGINT NULL;

ALTER TABLE loyalty_accepted_benefit
    ADD CONSTRAINT chk_loyalty_benefit_guest CHECK (
        member_id IS NOT NULL
        OR (source_kind = 'WORKSHOP' AND requested_points = 0 AND point_discount = 0
            AND points_state = 'NONE' AND voucher_state = 'NONE'
            AND (earned_points IS NULL OR earned_points = 0)));
