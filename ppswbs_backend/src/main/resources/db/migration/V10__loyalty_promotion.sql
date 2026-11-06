-- V10__loyalty_promotion.sql
-- Schema migration for Feature FE-06 Loyalty & Promotion (T016).
-- Renumbered from V7 on 2026-10-27 before it was committed or shared: origin/develop already uses V6–V8.
-- Source: ppswbs-spec/features/006-loyalty-promotion/data-model.md (10 tables).
-- Append-only: never edit after it has been applied; changes go into a new migration.
-- IDs are positive signed BIGINT; money and points are DECIMAL(19,0); percentages DECIMAL(9,6);
-- timestamps are UTC DATETIME(6). Foreign keys are RESTRICT: history is never cascaded away.
-- Member and actor ids are logical references to the identity module (no foreign key to account tables).

-- Immutable points policy revisions. The registry points to the current one.
CREATE TABLE loyalty_policy (
    id BIGINT NOT NULL AUTO_INCREMENT,
    revision_no BIGINT NOT NULL,
    earn_unit_amount DECIMAL(19,0) NOT NULL,
    redeem_point_value DECIMAL(19,0) NOT NULL,
    min_redeem_points DECIMAL(19,0) NOT NULL,
    max_redeem_percent DECIMAL(9,6) NOT NULL,
    rounding_mode VARCHAR(10) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_loyalty_policy_revision (revision_no),
    CONSTRAINT chk_loyalty_policy_values CHECK (
        revision_no >= 1 AND earn_unit_amount >= 1 AND redeem_point_value >= 1 AND min_redeem_points >= 1
        AND max_redeem_percent > 0 AND max_redeem_percent <= 100),
    CONSTRAINT chk_loyalty_policy_rounding CHECK (rounding_mode = 'FLOOR')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Singleton gate row (id = 1): READ-locked on accept, WRITE-locked when a programme or the policy changes.
CREATE TABLE loyalty_registry (
    id BIGINT NOT NULL,
    current_policy_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_loyalty_registry_policy FOREIGN KEY (current_policy_id)
        REFERENCES loyalty_policy (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_loyalty_registry_singleton CHECK (id = 1 AND version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Initial configuration decided on 2026-10-24: 10.000d per point earned, 100d per point redeemed,
-- at least 10 points per use, redemption capped at 10% of the amount after programmes (floor rounding).
INSERT INTO loyalty_policy (revision_no, earn_unit_amount, redeem_point_value, min_redeem_points,
                            max_redeem_percent, rounding_mode, created_at, created_by)
VALUES (1, 10000, 100, 10, 10.000000, 'FLOOR', UTC_TIMESTAMP(6), NULL);

INSERT INTO loyalty_registry (id, current_policy_id, version)
VALUES (1, (SELECT id FROM loyalty_policy WHERE revision_no = 1), 0);

-- Voucher or automatic discount. Kind, scope and code never change. The code key is unique across all
-- history (binary collation: no case or accent folding in the database; the application upper-cases).
-- Quota counters exist for vouchers only and change under the programme lock.
-- current_revision_id gets its foreign key below, after promotion_revision exists (circular reference).
CREATE TABLE promotion_program (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kind VARCHAR(20) NOT NULL,
    scope VARCHAR(20) NOT NULL,
    code_original VARCHAR(128) NULL DEFAULT NULL,
    code_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL,
    status VARCHAR(10) NOT NULL,
    current_revision_id BIGINT NULL DEFAULT NULL,
    total_limit BIGINT NULL DEFAULT NULL,
    member_limit BIGINT NULL DEFAULT NULL,
    held_count BIGINT NULL DEFAULT NULL,
    consumed_count BIGINT NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_promotion_program_code_key (code_key),
    KEY idx_promotion_program_scope_status (scope, status, id),
    CONSTRAINT chk_promotion_program_enums CHECK (
        kind IN ('VOUCHER', 'AUTO_DISCOUNT') AND scope IN ('PRODUCT', 'WORKSHOP')
        AND status IN ('ENABLED', 'PAUSED') AND version >= 0),
    CONSTRAINT chk_promotion_program_auto CHECK (
        kind <> 'AUTO_DISCOUNT' OR (scope = 'PRODUCT' AND code_original IS NULL AND code_key IS NULL
            AND total_limit IS NULL AND member_limit IS NULL AND held_count IS NULL AND consumed_count IS NULL)),
    CONSTRAINT chk_promotion_program_voucher CHECK (
        kind <> 'VOUCHER' OR (code_original IS NOT NULL AND code_key IS NOT NULL AND total_limit >= 1
            AND member_limit >= 1 AND held_count >= 0 AND consumed_count >= 0
            AND held_count + consumed_count <= total_limit))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Immutable programme terms; editing a programme inserts a new revision.
CREATE TABLE promotion_revision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    program_id BIGINT NOT NULL,
    revision_no BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    discount_kind VARCHAR(10) NOT NULL,
    percent DECIMAL(9,6) NULL DEFAULT NULL,
    fixed_amount DECIMAL(19,0) NULL DEFAULT NULL,
    max_discount_amount DECIMAL(19,0) NOT NULL,
    minimum_eligible_amount DECIMAL(19,0) NOT NULL,
    allow_points TINYINT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_promotion_revision_program_no (program_id, revision_no),
    KEY idx_promotion_revision_window (start_at, end_at),
    CONSTRAINT fk_promotion_revision_program FOREIGN KEY (program_id)
        REFERENCES promotion_program (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_promotion_revision_window CHECK (revision_no >= 1 AND start_at < end_at),
    CONSTRAINT chk_promotion_revision_discount CHECK (
        (discount_kind = 'PERCENT' AND percent IS NOT NULL AND fixed_amount IS NULL
            AND percent > 0 AND percent <= 100)
        OR (discount_kind = 'FIXED' AND fixed_amount IS NOT NULL AND percent IS NULL AND fixed_amount >= 1)),
    CONSTRAINT chk_promotion_revision_amounts CHECK (max_discount_amount >= 1 AND minimum_eligible_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE promotion_program
    ADD CONSTRAINT fk_promotion_program_current_revision FOREIGN KEY (current_revision_id)
        REFERENCES promotion_revision (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

-- Scope values of a revision. Different dimensions combine with AND, values inside one with OR.
CREATE TABLE promotion_revision_target (
    id BIGINT NOT NULL AUTO_INCREMENT,
    revision_id BIGINT NOT NULL,
    dimension VARCHAR(20) NOT NULL,
    target_key VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_promotion_target (revision_id, dimension, target_key),
    KEY idx_promotion_target_lookup (dimension, target_key, revision_id),
    CONSTRAINT fk_promotion_target_revision FOREIGN KEY (revision_id)
        REFERENCES promotion_revision (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_promotion_target_dimension CHECK (
        dimension IN ('PRODUCT', 'VARIANT', 'PACKAGE', 'BRANCH', 'WORKSHOP_DATE', 'SESSION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Per-member quota counters and the voucher a Member saved. Saving holds no quota (counters stay 0).
CREATE TABLE promotion_member_program (
    id BIGINT NOT NULL AUTO_INCREMENT,
    program_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    saved_at DATETIME(6) NULL DEFAULT NULL,
    held_count BIGINT NOT NULL DEFAULT 0,
    consumed_count BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_promotion_member_program (program_id, member_id),
    KEY idx_promotion_member_program_saved (member_id, saved_at),
    CONSTRAINT fk_promotion_member_program_program FOREIGN KEY (program_id)
        REFERENCES promotion_program (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_promotion_member_program_counts CHECK (held_count >= 0 AND consumed_count >= 0 AND version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One shared points wallet per Member: no tiers, no per-service split, no expiry.
CREATE TABLE loyalty_wallet (
    member_id BIGINT NOT NULL,
    balance_points DECIMAL(19,0) NOT NULL,
    held_points DECIMAL(19,0) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (member_id),
    CONSTRAINT chk_loyalty_wallet_points CHECK (
        balance_points >= 0 AND held_points >= 0 AND held_points <= balance_points AND version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Snapshot of what was accepted with one order or booking plus the state of each component.
-- Amounts and revisions never change; states, outcome keys and timestamps move forward only.
-- Workshop: deposit and remaining are recorded once by the booking owner and add up to the
-- post-promotion amount; they stay NULL until then.
CREATE TABLE loyalty_accepted_benefit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_kind VARCHAR(10) NOT NULL,
    source_ref VARCHAR(128) NOT NULL,
    member_id BIGINT NOT NULL,
    member_origin_eligible TINYINT(1) NOT NULL,
    accepted_at DATETIME(6) NOT NULL,
    original_deadline DATETIME(6) NOT NULL,
    source_revision BIGINT NOT NULL,
    accepted_terms_digest VARCHAR(128) NOT NULL,
    policy_id BIGINT NOT NULL,
    program_id BIGINT NULL DEFAULT NULL,
    revision_id BIGINT NULL DEFAULT NULL,
    gross_amount DECIMAL(19,0) NOT NULL,
    program_discount DECIMAL(19,0) NOT NULL,
    requested_points DECIMAL(19,0) NOT NULL,
    point_discount DECIMAL(19,0) NOT NULL,
    earn_base DECIMAL(19,0) NOT NULL,
    post_promotion_amount DECIMAL(19,0) NULL DEFAULT NULL,
    deposit_amount DECIMAL(19,0) NULL DEFAULT NULL,
    remaining_amount DECIMAL(19,0) NULL DEFAULT NULL,
    points_state VARCHAR(10) NOT NULL,
    voucher_state VARCHAR(10) NOT NULL,
    earn_state VARCHAR(12) NOT NULL,
    earned_points DECIMAL(19,0) NULL DEFAULT NULL,
    settled_at DATETIME(6) NULL DEFAULT NULL,
    settlement_outcome_key VARCHAR(128) NULL DEFAULT NULL,
    release_outcome_key VARCHAR(128) NULL DEFAULT NULL,
    restore_outcome_key VARCHAR(128) NULL DEFAULT NULL,
    earn_outcome_key VARCHAR(128) NULL DEFAULT NULL,
    source_outcome_version BIGINT NOT NULL DEFAULT 0,
    voucher_consumed_at DATETIME(6) NULL DEFAULT NULL,
    voucher_restored_at DATETIME(6) NULL DEFAULT NULL,
    source_terminal_at DATETIME(6) NULL DEFAULT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_loyalty_benefit_source (source_kind, source_ref),
    KEY idx_loyalty_benefit_member (member_id, accepted_at, id),
    KEY idx_loyalty_benefit_program (program_id, voucher_state),
    CONSTRAINT fk_loyalty_benefit_policy FOREIGN KEY (policy_id)
        REFERENCES loyalty_policy (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_loyalty_benefit_program FOREIGN KEY (program_id)
        REFERENCES promotion_program (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_loyalty_benefit_revision FOREIGN KEY (revision_id)
        REFERENCES promotion_revision (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_loyalty_benefit_enums CHECK (
        source_kind IN ('RETAIL', 'WORKSHOP')
        AND points_state IN ('NONE', 'HELD', 'CONSUMED', 'RELEASED', 'RESTORED')
        AND voucher_state IN ('NONE', 'HELD', 'CONSUMED', 'RELEASED', 'RESTORED')
        AND earn_state IN ('PENDING', 'GRANTED', 'INELIGIBLE')),
    CONSTRAINT chk_loyalty_benefit_origin CHECK (member_origin_eligible = 1),
    CONSTRAINT chk_loyalty_benefit_amounts CHECK (
        gross_amount >= 0 AND program_discount >= 0 AND requested_points >= 0 AND point_discount >= 0
        AND earn_base >= 0 AND source_outcome_version >= 0 AND source_revision >= 0 AND version >= 0),
    CONSTRAINT chk_loyalty_benefit_workshop CHECK (
        (source_kind = 'RETAIL' AND post_promotion_amount IS NULL AND deposit_amount IS NULL
            AND remaining_amount IS NULL)
        OR (source_kind = 'WORKSHOP' AND post_promotion_amount IS NOT NULL
            AND ((deposit_amount IS NULL AND remaining_amount IS NULL)
                OR (deposit_amount IS NOT NULL AND remaining_amount IS NOT NULL
                    AND deposit_amount >= 0 AND remaining_amount >= 0
                    AND deposit_amount + remaining_amount = post_promotion_amount)))),
    CONSTRAINT chk_loyalty_benefit_earn CHECK (earn_state <> 'GRANTED' OR earned_points IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Append-only point ledger. Unique per benefit and kind, so a hold/consume/release/restore/earn can never
-- be posted twice. A zero earn is recorded on the benefit only, never as a ledger row.
CREATE TABLE loyalty_point_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    accepted_benefit_id BIGINT NOT NULL,
    entry_kind VARCHAR(10) NOT NULL,
    delta_balance DECIMAL(19,0) NOT NULL,
    delta_held DECIMAL(19,0) NOT NULL,
    balance_after DECIMAL(19,0) NOT NULL,
    held_after DECIMAL(19,0) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    original_debit_id BIGINT NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_loyalty_ledger_benefit_kind (accepted_benefit_id, entry_kind),
    KEY idx_loyalty_ledger_member (member_id, occurred_at, id),
    CONSTRAINT fk_loyalty_ledger_benefit FOREIGN KEY (accepted_benefit_id)
        REFERENCES loyalty_accepted_benefit (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_loyalty_ledger_original_debit FOREIGN KEY (original_debit_id)
        REFERENCES loyalty_point_ledger (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_loyalty_ledger_after CHECK (balance_after >= 0 AND held_after >= 0 AND held_after <= balance_after),
    CONSTRAINT chk_loyalty_ledger_deltas CHECK (
        (entry_kind = 'HOLD' AND delta_balance = 0 AND delta_held > 0 AND original_debit_id IS NULL)
        OR (entry_kind = 'CONSUME' AND delta_balance < 0 AND delta_held = delta_balance AND original_debit_id IS NULL)
        OR (entry_kind = 'RELEASE' AND delta_balance = 0 AND delta_held < 0 AND original_debit_id IS NULL)
        OR (entry_kind = 'RESTORE' AND delta_balance > 0 AND delta_held = 0 AND original_debit_id IS NOT NULL)
        OR (entry_kind = 'EARN' AND delta_balance > 0 AND delta_held = 0 AND original_debit_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Append-only audit trail that is also the receipt of idempotent commands: the command columns are set
-- together or not at all, and (actor_key, action, command_id) is unique when a command id is present
-- (MySQL allows several NULLs in a unique index). Never holds a full request, token, PII or secret.
CREATE TABLE loyalty_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_ref VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL,
    object_ref VARCHAR(128) NOT NULL,
    detail VARCHAR(2000) NULL DEFAULT NULL,
    correlation_id VARCHAR(64) NULL DEFAULT NULL,
    occurred_at DATETIME(6) NOT NULL,
    actor_key VARCHAR(128) NULL DEFAULT NULL,
    command_id VARCHAR(36) NULL DEFAULT NULL,
    canonical_digest VARCHAR(64) NULL DEFAULT NULL,
    minimal_result VARCHAR(2000) NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_loyalty_audit_command (actor_key, action, command_id),
    KEY idx_loyalty_audit_object (object_ref, occurred_at, id),
    CONSTRAINT chk_loyalty_audit_command CHECK (
        (actor_key IS NULL AND command_id IS NULL AND canonical_digest IS NULL AND minimal_result IS NULL)
        OR (actor_key IS NOT NULL AND command_id IS NOT NULL AND canonical_digest IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
