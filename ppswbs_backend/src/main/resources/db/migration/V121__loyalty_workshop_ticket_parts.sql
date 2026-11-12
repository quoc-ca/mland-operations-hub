-- FE-06 (006) Session 2026-11-12, D-012 (T103): a workshop cart keeps one settlement part per ticket so 003.3 can earn
-- per checked-out ticket (FR-030) and give back a cancelled ticket's used points (FR-042, FR-043). A ticket's points
-- can now be RESTORED after the group deposit consumed them, and the part records how the ticket ended.

ALTER TABLE loyalty_accepted_benefit_part DROP CHECK chk_loyalty_benefit_part_enums;

ALTER TABLE loyalty_accepted_benefit_part
    ADD COLUMN closure VARCHAR(20) NULL DEFAULT NULL AFTER earn_state;

ALTER TABLE loyalty_accepted_benefit_part
    ADD CONSTRAINT chk_loyalty_benefit_part_enums CHECK (
        points_state IN ('NONE', 'HELD', 'CONSUMED', 'RELEASED', 'RESTORED')
        AND earn_state IN ('PENDING', 'GRANTED', 'INELIGIBLE')
        AND (closure IS NULL
             OR closure IN ('COMPLETED', 'VALID_CANCELLATION', 'MLAND_CANCELLATION', 'NO_SHOW')));
