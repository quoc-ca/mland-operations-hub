-- FE-06 (006) Session 2026-11-12, D-011: a retail checkout accepted with settlement parts (FE-08 branch orders paid
-- in cash at the counter one by one). Each part keeps its share of the checkout benefit, fixed at acceptance, and its
-- own points/earn state. The voucher use stays on loyalty_accepted_benefit (one use per checkout).

CREATE TABLE loyalty_accepted_benefit_part (
    id BIGINT NOT NULL AUTO_INCREMENT,
    accepted_benefit_id BIGINT NOT NULL,
    part_seq INT NOT NULL,
    part_ref VARCHAR(64) NOT NULL,
    gross_amount DECIMAL(19,0) NOT NULL,
    program_discount DECIMAL(19,0) NOT NULL,
    points DECIMAL(19,0) NOT NULL,
    point_discount DECIMAL(19,0) NOT NULL,
    earn_base DECIMAL(19,0) NOT NULL,
    earn_points DECIMAL(19,0) NOT NULL,
    points_state VARCHAR(10) NOT NULL,
    earn_state VARCHAR(12) NOT NULL,
    earned_points DECIMAL(19,0) NULL DEFAULT NULL,
    settlement_outcome_key VARCHAR(128) NULL DEFAULT NULL,
    release_outcome_key VARCHAR(128) NULL DEFAULT NULL,
    earn_outcome_key VARCHAR(128) NULL DEFAULT NULL,
    settled_at DATETIME(6) NULL DEFAULT NULL,
    terminal_at DATETIME(6) NULL DEFAULT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_loyalty_benefit_part_seq (accepted_benefit_id, part_seq),
    UNIQUE KEY uk_loyalty_benefit_part_ref (accepted_benefit_id, part_ref),
    CONSTRAINT fk_loyalty_benefit_part_benefit FOREIGN KEY (accepted_benefit_id)
        REFERENCES loyalty_accepted_benefit (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_loyalty_benefit_part_seq CHECK (part_seq >= 1),
    CONSTRAINT chk_loyalty_benefit_part_enums CHECK (
        points_state IN ('NONE', 'HELD', 'CONSUMED', 'RELEASED')
        AND earn_state IN ('PENDING', 'GRANTED', 'INELIGIBLE')),
    CONSTRAINT chk_loyalty_benefit_part_amounts CHECK (
        gross_amount >= 0 AND program_discount >= 0 AND points >= 0 AND point_discount >= 0
        AND earn_base >= 0 AND earn_points >= 0
        AND gross_amount - program_discount - point_discount = earn_base),
    CONSTRAINT chk_loyalty_benefit_part_earn CHECK (earn_state <> 'GRANTED' OR earned_points IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A part posts its own CONSUME/RELEASE/EARN rows. part_seq 0 is the whole transaction (every existing row), so the
-- "once per benefit and kind" rule still holds for transactions without parts and becomes "once per part and kind".
ALTER TABLE loyalty_point_ledger
    ADD COLUMN part_seq INT NOT NULL DEFAULT 0 AFTER accepted_benefit_id;

ALTER TABLE loyalty_point_ledger
    ADD UNIQUE KEY uk_loyalty_ledger_benefit_kind_part (accepted_benefit_id, entry_kind, part_seq);

ALTER TABLE loyalty_point_ledger
    DROP INDEX uk_loyalty_ledger_benefit_kind;
