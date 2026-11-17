-- Session 2026-11-14, 006 FR-044 (D-013): an automatic discount may also have the WORKSHOP scope.
-- V10 wrote chk_promotion_program_auto with "scope = 'PRODUCT'" because automatic discounts were product-only.
-- The rest of that rule stays exactly as it was: an automatic discount still has no code and no quota counters.
-- Append-only: V10 is left untouched, the old constraint is dropped and the new one added under the same name.
ALTER TABLE promotion_program
    DROP CHECK chk_promotion_program_auto;

ALTER TABLE promotion_program
    ADD CONSTRAINT chk_promotion_program_auto CHECK (
        kind <> 'AUTO_DISCOUNT' OR (code_original IS NULL AND code_key IS NULL
            AND total_limit IS NULL AND member_limit IS NULL AND held_count IS NULL AND consumed_count IS NULL));
