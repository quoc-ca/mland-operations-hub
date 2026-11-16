-- V162__repair_v161_member_seed.sql
-- Repairs section 8 of V161 (member vouchers and loyalty points) on databases that already held member data
-- when V161 ran. V161 itself is not edited: it is applied on several machines and Flyway checks its checksum.
--
-- 1. Wallets. V161 set balance_points = 100000 on every MEMBER wallet, erasing the points held before.
--    Every wallet change (HOLD, CONSUME, RELEASE, RESTORE, EARN) writes one ledger entry, so the ledger is the
--    source of truth: balance = SUM(delta_balance), held = SUM(delta_held).
-- 2. Ledger running totals. V161 wrote its EARN entry with balance_after = 100000 whatever came before, and
--    later entries were computed from that wrong wallet. Running totals are recomputed from the V161 entry on,
--    for the members of the V161 grant only; earlier entries are not touched.
-- 3. Voucher wallet. V161 saved every ENABLED voucher to every Member. Only the sample vouchers of V161 stay
--    saved; any other voucher saved by V161 is unsaved again (saved_at back to NULL, counters kept).
--
-- The 100000-point grant and the sample vouchers of V161 are kept. On a database where V161 ran on empty
-- member data nothing is wrong and every statement below changes no row.

-- Time V161 ran (its @now): the accepted_at of its grant, also the saved_at of the vouchers it saved.
SET @seed_at = (SELECT MIN(accepted_at) FROM loyalty_accepted_benefit
                WHERE source_kind = 'RETAIL' AND source_ref LIKE 'seed-points-member-%');

-- ---------------------------------------------------------------------------
-- 1. Rebuild the wallets of the V161 grant from the ledger
-- ---------------------------------------------------------------------------
UPDATE loyalty_wallet w
JOIN (SELECT l.member_id, SUM(l.delta_balance) AS balance_points, SUM(l.delta_held) AS held_points
      FROM loyalty_point_ledger l
      WHERE l.member_id IN (SELECT b.member_id FROM loyalty_accepted_benefit b
                            WHERE b.source_kind = 'RETAIL' AND b.source_ref LIKE 'seed-points-member-%')
      GROUP BY l.member_id) s ON s.member_id = w.member_id
SET w.balance_points = s.balance_points,
    w.held_points = s.held_points,
    w.version = w.version + 1
WHERE w.balance_points <> s.balance_points OR w.held_points <> s.held_points;

-- ---------------------------------------------------------------------------
-- 2. Recompute ledger running totals from the V161 EARN entry on
-- ---------------------------------------------------------------------------
UPDATE loyalty_point_ledger l
JOIN (SELECT x.id,
             SUM(x.delta_balance) OVER (PARTITION BY x.member_id ORDER BY x.id) AS balance_after,
             SUM(x.delta_held) OVER (PARTITION BY x.member_id ORDER BY x.id) AS held_after
      FROM loyalty_point_ledger x
      WHERE x.member_id IN (SELECT b.member_id FROM loyalty_accepted_benefit b
                            WHERE b.source_kind = 'RETAIL' AND b.source_ref LIKE 'seed-points-member-%')) r
  ON r.id = l.id
JOIN (SELECT e.member_id, MIN(e.id) AS first_id
      FROM loyalty_point_ledger e
      JOIN loyalty_accepted_benefit b ON b.id = e.accepted_benefit_id
      WHERE b.source_kind = 'RETAIL' AND b.source_ref LIKE 'seed-points-member-%' AND e.entry_kind = 'EARN'
      GROUP BY e.member_id) g ON g.member_id = l.member_id AND l.id >= g.first_id
SET l.balance_after = r.balance_after,
    l.held_after = r.held_after
WHERE l.balance_after <> r.balance_after OR l.held_after <> r.held_after;

-- ---------------------------------------------------------------------------
-- 3. Unsave the vouchers V161 saved that are not its sample vouchers
-- ---------------------------------------------------------------------------
UPDATE promotion_member_program m
JOIN promotion_program p ON p.id = m.program_id
SET m.saved_at = NULL,
    m.version = m.version + 1
WHERE @seed_at IS NOT NULL
  AND m.saved_at = @seed_at
  AND p.kind = 'VOUCHER'
  AND p.code_key NOT IN ('MLCHAO10', 'MLGIAM50K', 'MLMANAO15', 'MLTAYHO80', 'MLHK12',
                         'MLWS100', 'MLWS15', 'MLWSHK50');
