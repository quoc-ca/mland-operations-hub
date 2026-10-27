package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit.Snapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LedgerEntryKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyAudit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyPolicy;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.MemberProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PointLedger;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.PolicyTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T015 evidence on the external MySQL 8.4 test database: Flyway applies 006's V10 and V11 after 001's V5 and 002's V9
 * (upgrade path, nothing of them is touched), the ten loyalty tables exist with their seed, every entity
 * maps to its table, and the database itself enforces the unique keys, checks and restrict foreign keys of
 * data-model section 2. Every data-changing test runs inside a transaction that is rolled back, so reruns
 * on the disposable database never collide and no other feature's rows are touched.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class LoyaltyMySqlMigrationIT {

    private static final Instant NOW = Instant.parse("2026-10-26T06:00:00.123456Z");
    private static final Instant LATER = NOW.plusSeconds(3600);

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transaction;
    @Autowired private EntityManager entityManager;

    /** Runs the work in one transaction and always rolls it back. */
    private void rolledBack(Consumer<JdbcTemplate> work) {
        transaction.executeWithoutResult(status -> {
            try {
                work.accept(jdbc);
            } finally {
                status.setRollbackOnly();
            }
        });
    }

    private void assertRejected(Consumer<JdbcTemplate> work) {
        assertThatThrownBy(() -> rolledBack(work)).satisfies(LoyaltyMySqlMigrationIT::requireConstraintViolation);
    }

    /** MySQL error codes: 1062 duplicate key, 1264 out of range, 1451/1452 foreign key, 3819 check constraint. A missing table (1146) does not count. */
    private static void requireConstraintViolation(Throwable failure) {
        Throwable cause = failure;
        while (cause != null && !(cause instanceof java.sql.SQLException)) {
            cause = cause.getCause();
        }
        assertThat(cause).as("expected a database constraint failure but got %s", failure).isNotNull();
        assertThat(((java.sql.SQLException) cause).getErrorCode()).isIn(1062, 1264, 1451, 1452, 3819);
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    // ----- helpers that insert minimal valid rows through plain SQL -----

    private long insertVoucher(JdbcTemplate sql, String codeKey, long total, long perMember, long held, long consumed) {
        sql.update("INSERT INTO promotion_program (kind, scope, code_original, code_key, status, total_limit, "
                + "member_limit, held_count, consumed_count, created_at, created_by, version) "
                + "VALUES ('VOUCHER','PRODUCT',?,?,'ENABLED',?,?,?,?,?,1,0)",
                codeKey, codeKey, total, perMember, held, consumed, java.sql.Timestamp.from(NOW));
        return sql.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertRevision(JdbcTemplate sql, long programId, long revisionNo, String kind, String percent,
                                String fixed, Instant start, Instant end) {
        sql.update("INSERT INTO promotion_revision (program_id, revision_no, name, start_at, end_at, discount_kind, "
                + "percent, fixed_amount, max_discount_amount, minimum_eligible_amount, allow_points, created_at, created_by) "
                + "VALUES (?,?,?,?,?,?,?,?,50000,0,1,?,1)", programId, revisionNo, "Giảm Tết",
                java.sql.Timestamp.from(start), java.sql.Timestamp.from(end), kind,
                percent == null ? null : new BigDecimal(percent), fixed == null ? null : new BigInteger(fixed),
                java.sql.Timestamp.from(NOW));
        return sql.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long currentPolicyId(JdbcTemplate sql) {
        return sql.queryForObject("SELECT current_policy_id FROM loyalty_registry WHERE id = 1", Long.class);
    }

    private long insertBenefit(JdbcTemplate sql, String sourceKind, String sourceRef, String post,
                               int memberOrigin, String earnState, String earned) {
        sql.update("INSERT INTO loyalty_accepted_benefit (source_kind, source_ref, member_id, member_origin_eligible, "
                + "accepted_at, original_deadline, source_revision, accepted_terms_digest, policy_id, gross_amount, "
                + "program_discount, requested_points, point_discount, earn_base, post_promotion_amount, "
                + "points_state, voucher_state, earn_state, earned_points, source_outcome_version, version) "
                + "VALUES (?,?,?,?,?,?,1,'digest',?,1000000,0,0,0,1000000,?,'NONE','NONE',?,?,0,0)",
                sourceKind, sourceRef, LoyaltyTestSupport.MEMBER_ID, memberOrigin, java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(LATER), currentPolicyId(sql),
                post == null ? null : new BigInteger(post), earnState,
                earned == null ? null : new BigInteger(earned));
        return sql.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertLedger(JdbcTemplate sql, long benefitId, String kind, long deltaBalance, long deltaHeld,
                              Long originalDebitId) {
        sql.update("INSERT INTO loyalty_point_ledger (member_id, accepted_benefit_id, entry_kind, delta_balance, "
                + "delta_held, balance_after, held_after, occurred_at, original_debit_id) VALUES (?,?,?,?,?,500,0,?,?)",
                LoyaltyTestSupport.MEMBER_ID, benefitId, kind, deltaBalance, deltaHeld, java.sql.Timestamp.from(NOW),
                originalDebitId);
    }

    // ----- migration, tables and seed -----

    @Test
    void migrationsAreAppliedInOrderWithTenTables() {
        LoyaltyTestSupport.requireMySql84(dataSource);
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class);
        assertThat(versions).containsSubsequence("5", "9", "10", "11");
        List<String> tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND (table_name LIKE 'loyalty\\_%' OR table_name LIKE 'promotion\\_%') "
                + "ORDER BY table_name", String.class);
        assertThat(tables).containsExactlyInAnyOrder("loyalty_accepted_benefit", "loyalty_audit", "loyalty_policy",
                "loyalty_point_ledger", "loyalty_registry", "loyalty_wallet", "promotion_member_program",
                "promotion_program", "promotion_revision", "promotion_revision_target");
    }

    @Test
    void earlierFeaturesAreStillPresent() {
        List<String> tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name IN ('accounts','catalogue_products')", String.class);
        assertThat(tables).containsExactlyInAnyOrder("accounts", "catalogue_products");
    }

    @Test
    void seedHasOneRegistryRowPointingToTheDefaultPolicy() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loyalty_registry", Integer.class)).isEqualTo(1);
        Map<String, Object> policy = jdbc.queryForMap("SELECT p.revision_no, p.earn_unit_amount, p.redeem_point_value, "
                + "p.min_redeem_points, p.max_redeem_percent, p.rounding_mode FROM loyalty_registry r "
                + "JOIN loyalty_policy p ON p.id = r.current_policy_id WHERE r.id = 1");
        assertThat(((Number) policy.get("revision_no")).longValue()).isEqualTo(1L);
        assertThat(new BigInteger(policy.get("earn_unit_amount").toString())).isEqualTo(BigInteger.valueOf(10_000));
        assertThat(new BigInteger(policy.get("redeem_point_value").toString())).isEqualTo(BigInteger.valueOf(100));
        assertThat(new BigInteger(policy.get("min_redeem_points").toString())).isEqualTo(BigInteger.TEN);
        assertThat((BigDecimal) policy.get("max_redeem_percent")).isEqualByComparingTo("10");
        assertThat(policy.get("rounding_mode")).isEqualTo("FLOOR");
    }

    @Test
    void registryAcceptsOnlyTheSingletonRow() {
        assertRejected(sql -> sql.update("INSERT INTO loyalty_registry (id, current_policy_id, version) VALUES (2, ?, 0)",
                currentPolicyId(sql)));
    }

    // ----- entity mapping -----

    @Test
    void everyEntityMapsToItsTableWithExactValues() {
        transaction.executeWithoutResult(status -> {
            try {
                PolicyTerms terms = new PolicyTerms(BigInteger.valueOf(20_000), BigInteger.valueOf(50),
                        BigInteger.valueOf(5), new BigDecimal("12.500000"));
                LoyaltyPolicy policy = LoyaltyPolicy.create(System.nanoTime(), terms, NOW, 1L);
                entityManager.persist(policy);
                entityManager.flush();
                assertThat(entityManager.find(LoyaltyRegistry.class, LoyaltyRegistry.SINGLETON_ID).getCurrentPolicyId())
                        .isNotNull();

                String code = unique("RT");
                PromotionProgram program = PromotionProgram.voucher(TargetScope.PRODUCT, code, code, 100, 3, NOW, 1L);
                entityManager.persist(program);
                entityManager.flush();
                PromotionRevision revision = PromotionRevision.create(program.getId(), 1, program.getKind(), "Giảm Tết",
                        NOW, LATER, DiscountTerms.percent(new BigDecimal("7.250000"), BigInteger.valueOf(50_000),
                                BigInteger.valueOf(500_000)), true, NOW, 1L);
                entityManager.persist(revision);
                entityManager.flush();
                program.pointToRevision(revision.getId());
                entityManager.persist(PromotionTarget.create(revision.getId(), TargetScope.PRODUCT,
                        TargetDimension.VARIANT, "42"));
                MemberProgram memberProgram = MemberProgram.create(program.getId(), LoyaltyTestSupport.MEMBER_ID);
                memberProgram.save(NOW);
                memberProgram.hold(3);
                entityManager.persist(memberProgram);
                LoyaltyWallet wallet = LoyaltyWallet.open(LoyaltyTestSupport.MEMBER_ID);
                wallet.earn(new BigInteger("9999999999999999999"));
                wallet.hold(BigInteger.valueOf(200));
                entityManager.persist(wallet);

                AcceptedBenefit benefit = AcceptedBenefit.accept(new Snapshot(SourceKind.WORKSHOP, unique("bk-"),
                        LoyaltyTestSupport.MEMBER_ID, NOW, LATER, 1L, "digest", policy.getId(), program.getId(),
                        revision.getId(), BigInteger.valueOf(1_000_000), BigInteger.valueOf(100_000),
                        BigInteger.valueOf(200), BigInteger.valueOf(20_000), BigInteger.valueOf(880_000),
                        BigInteger.valueOf(880_000), true), true);
                entityManager.persist(benefit);
                entityManager.flush();
                PointLedger ledger = PointLedger.entry(LedgerEntryKind.HOLD, LoyaltyTestSupport.MEMBER_ID,
                        benefit.getId(), BigInteger.valueOf(200), wallet.getBalancePoints(), wallet.getHeldPoints(),
                        NOW, null);
                entityManager.persist(ledger);
                entityManager.persist(LoyaltyAudit.command("manager:1", "promotion.create", "program:" + program.getId(),
                        "{\"revision\":1}", "corr-1", NOW, "manager:1", UUID.randomUUID().toString(), "a".repeat(64),
                        "{\"id\":1}"));
                entityManager.flush();
                entityManager.clear();

                PromotionProgram loaded = entityManager.find(PromotionProgram.class, program.getId());
                assertThat(loaded.getCodeKey()).isEqualTo(code);
                assertThat(loaded.getCurrentRevisionId()).isEqualTo(revision.getId());
                assertThat(loaded.getHeldCount()).isZero();
                PromotionRevision loadedRevision = entityManager.find(PromotionRevision.class, revision.getId());
                assertThat(loadedRevision.getPercent()).isEqualByComparingTo("7.25");
                assertThat(loadedRevision.getStartAt()).isEqualTo(NOW);
                assertThat(loadedRevision.isAllowPoints()).isTrue();
                LoyaltyWallet loadedWallet = entityManager.find(LoyaltyWallet.class, LoyaltyTestSupport.MEMBER_ID);
                assertThat(loadedWallet.getBalancePoints()).isEqualTo(new BigInteger("9999999999999999999"));
                assertThat(loadedWallet.getHeldPoints()).isEqualTo(BigInteger.valueOf(200));
                AcceptedBenefit loadedBenefit = entityManager.find(AcceptedBenefit.class, benefit.getId());
                assertThat(loadedBenefit.getPostPromotionAmount()).isEqualTo(BigInteger.valueOf(880_000));
                assertThat(loadedBenefit.getPointsState()).isEqualTo(ComponentState.HELD);
                assertThat(loadedBenefit.getEarnState()).isEqualTo(EarnState.PENDING);
                assertThat(entityManager.find(LoyaltyPolicy.class, policy.getId()).getMaxRedeemPercent())
                        .isEqualByComparingTo("12.5");
            } finally {
                status.setRollbackOnly();
            }
        });
    }

    // ----- programme keys and checks -----

    @Test
    void codeKeyIsUniqueBinaryAndDoesNotFoldCaseOrAccents() {
        rolledBack(sql -> {
            String key = unique("TET");
            insertVoucher(sql, key, 10, 1, 0, 0);
            insertVoucher(sql, key.replace("TET", "TÉT"), 10, 1, 0, 0);
            insertVoucher(sql, key.toLowerCase(java.util.Locale.ROOT), 10, 1, 0, 0);
            assertThatThrownBy(() -> insertVoucher(sql, key, 10, 1, 0, 0)).satisfies(LoyaltyMySqlMigrationIT::requireConstraintViolation);
        });
    }

    @Test
    void automaticDiscountCannotHaveCodeQuotaOrWorkshopScope() {
        assertRejected(sql -> sql.update("INSERT INTO promotion_program (kind, scope, code_original, code_key, status, "
                + "created_at, created_by, version) VALUES ('AUTO_DISCOUNT','PRODUCT','X','X','ENABLED',?,1,0)",
                java.sql.Timestamp.from(NOW)));
        assertRejected(sql -> sql.update("INSERT INTO promotion_program (kind, scope, status, total_limit, member_limit, "
                + "held_count, consumed_count, created_at, created_by, version) "
                + "VALUES ('AUTO_DISCOUNT','PRODUCT','ENABLED',5,1,0,0,?,1,0)", java.sql.Timestamp.from(NOW)));
        assertRejected(sql -> sql.update("INSERT INTO promotion_program (kind, scope, status, created_at, created_by, version) "
                + "VALUES ('AUTO_DISCOUNT','WORKSHOP','ENABLED',?,1,0)", java.sql.Timestamp.from(NOW)));
        rolledBack(sql -> sql.update("INSERT INTO promotion_program (kind, scope, status, created_at, created_by, version) "
                + "VALUES ('AUTO_DISCOUNT','PRODUCT','ENABLED',?,1,0)", java.sql.Timestamp.from(NOW)));
    }

    @Test
    void voucherNeedsCodeAndQuotaAndCannotOverbook() {
        assertRejected(sql -> sql.update("INSERT INTO promotion_program (kind, scope, status, created_at, created_by, version) "
                + "VALUES ('VOUCHER','PRODUCT','ENABLED',?,1,0)", java.sql.Timestamp.from(NOW)));
        assertRejected(sql -> insertVoucher(sql, unique("OVER"), 8, 1, 6, 3));
        assertRejected(sql -> insertVoucher(sql, unique("ZERO"), 0, 1, 0, 0));
        assertRejected(sql -> insertVoucher(sql, unique("NEG"), 8, 1, -1, 0));
        rolledBack(sql -> insertVoucher(sql, unique("FULL"), 8, 1, 6, 2));
    }

    @Test
    void revisionChecksEnforceTheDiscountRules() {
        rolledBack(sql -> {
            long program = insertVoucher(sql, unique("REV"), 10, 1, 0, 0);
            insertRevision(sql, program, 1, "PERCENT", "100", null, NOW, LATER);
            insertRevision(sql, program, 2, "PERCENT", "0.000001", null, NOW, LATER);
            insertRevision(sql, program, 3, "FIXED", null, "50000", NOW, LATER);
            assertThatThrownBy(() -> insertRevision(sql, program, 1, "FIXED", null, "5", NOW, LATER))
                    .as("unique (program, revision)").satisfies(LoyaltyMySqlMigrationIT::requireConstraintViolation);
        });
        for (String[] bad : new String[][] {
                {"PERCENT", "0", null}, {"PERCENT", "100.000001", null}, {"PERCENT", "10", "5000"},
                {"FIXED", null, "0"}, {"FIXED", "10", "5000"}, {"FIXED", null, null}, {"PERCENT", null, null}}) {
            assertRejected(sql -> {
                long program = insertVoucher(sql, unique("BAD"), 10, 1, 0, 0);
                insertRevision(sql, program, 1, bad[0], bad[1], bad[2], NOW, LATER);
            });
        }
        assertRejected(sql -> insertRevision(sql, insertVoucher(sql, unique("WIN"), 10, 1, 0, 0), 1, "PERCENT", "10",
                null, LATER, NOW));
        assertRejected(sql -> insertRevision(sql, insertVoucher(sql, unique("EQ"), 10, 1, 0, 0), 1, "PERCENT", "10",
                null, NOW, NOW));
    }

    @Test
    void targetsAreUniquePerRevisionAndDimension() {
        rolledBack(sql -> {
            long program = insertVoucher(sql, unique("TGT"), 10, 1, 0, 0);
            long revision = insertRevision(sql, program, 1, "PERCENT", "10", null, NOW, LATER);
            sql.update("INSERT INTO promotion_revision_target (revision_id, dimension, target_key) VALUES (?, 'PRODUCT', '1')", revision);
            sql.update("INSERT INTO promotion_revision_target (revision_id, dimension, target_key) VALUES (?, 'VARIANT', '1')", revision);
            assertThatThrownBy(() -> sql.update("INSERT INTO promotion_revision_target (revision_id, dimension, target_key) "
                    + "VALUES (?, 'PRODUCT', '1')", revision)).satisfies(LoyaltyMySqlMigrationIT::requireConstraintViolation);
        });
        assertRejected(sql -> {
            long program = insertVoucher(sql, unique("DIM"), 10, 1, 0, 0);
            long revision = insertRevision(sql, program, 1, "PERCENT", "10", null, NOW, LATER);
            sql.update("INSERT INTO promotion_revision_target (revision_id, dimension, target_key) VALUES (?, 'COLOR', '1')", revision);
        });
    }

    @Test
    void restrictForeignKeysKeepHistory() {
        assertRejected(sql -> {
            long program = insertVoucher(sql, unique("FK"), 10, 1, 0, 0);
            insertRevision(sql, program, 1, "PERCENT", "10", null, NOW, LATER);
            sql.update("DELETE FROM promotion_program WHERE id = ?", program);
        });
        assertRejected(sql -> sql.update("DELETE FROM loyalty_policy WHERE id = ?", currentPolicyId(sql)));
        assertRejected(sql -> insertRevision(sql, Long.MAX_VALUE, 1, "PERCENT", "10", null, NOW, LATER));
    }

    @Test
    void memberProgramIsUniquePerMemberAndProgramAndKeepsCountersNonNegative() {
        rolledBack(sql -> {
            long program = insertVoucher(sql, unique("MP"), 10, 2, 0, 0);
            sql.update("INSERT INTO promotion_member_program (program_id, member_id, held_count, consumed_count, version) "
                    + "VALUES (?,?,0,0,0)", program, LoyaltyTestSupport.MEMBER_ID);
            sql.update("INSERT INTO promotion_member_program (program_id, member_id, saved_at, held_count, consumed_count, version) "
                    + "VALUES (?,?,?,0,0,0)", program, LoyaltyTestSupport.OTHER_MEMBER_ID, java.sql.Timestamp.from(NOW));
            assertThatThrownBy(() -> sql.update("INSERT INTO promotion_member_program (program_id, member_id, held_count, "
                    + "consumed_count, version) VALUES (?,?,0,0,0)", program, LoyaltyTestSupport.MEMBER_ID))
                    .satisfies(LoyaltyMySqlMigrationIT::requireConstraintViolation);
        });
        assertRejected(sql -> {
            long program = insertVoucher(sql, unique("MPN"), 10, 2, 0, 0);
            sql.update("INSERT INTO promotion_member_program (program_id, member_id, held_count, consumed_count, version) "
                    + "VALUES (?,?,-1,0,0)", program, LoyaltyTestSupport.MEMBER_ID);
        });
    }

    // ----- wallet, accepted benefit, ledger, audit -----

    @Test
    void walletKeepsHeldWithinBalance() {
        rolledBack(sql -> sql.update("INSERT INTO loyalty_wallet (member_id, balance_points, held_points, version) "
                + "VALUES (9100001, 200, 200, 0)"));
        assertRejected(sql -> sql.update("INSERT INTO loyalty_wallet (member_id, balance_points, held_points, version) "
                + "VALUES (9100002, 200, 201, 0)"));
        assertRejected(sql -> sql.update("INSERT INTO loyalty_wallet (member_id, balance_points, held_points, version) "
                + "VALUES (9100003, -1, 0, 0)"));
        assertRejected(sql -> sql.update("INSERT INTO loyalty_wallet (member_id, balance_points, held_points, version) "
                + "VALUES (9100004, 10000000000000000000, 0, 0)"));
    }

    @Test
    void acceptedBenefitIsUniquePerSourceAndNeverFromGuestOrigin() {
        rolledBack(sql -> {
            String ref = unique("src-");
            insertBenefit(sql, "RETAIL", ref, null, 1, "PENDING", null);
            insertBenefit(sql, "WORKSHOP", ref, "880000", 1, "PENDING", null);
            assertThatThrownBy(() -> insertBenefit(sql, "RETAIL", ref, null, 1, "PENDING", null))
                    .satisfies(LoyaltyMySqlMigrationIT::requireConstraintViolation);
        });
        assertRejected(sql -> insertBenefit(sql, "RETAIL", unique("guest-"), null, 0, "PENDING", null));
    }

    @Test
    void onlyAWorkshopStoresAPositivePostPromotionAmount() {
        // A workshop must carry the fee after voucher and points; a retail order never does.
        assertRejected(sql -> insertBenefit(sql, "WORKSHOP", unique("w1-"), null, 1, "PENDING", null));
        assertRejected(sql -> insertBenefit(sql, "WORKSHOP", unique("w2-"), "0", 1, "PENDING", null));
        assertRejected(sql -> insertBenefit(sql, "RETAIL", unique("w3-"), "880000", 1, "PENDING", null));
        // An odd amount stays exact: deposit and remaining are FE-03's business and are not stored here.
        rolledBack(sql -> insertBenefit(sql, "WORKSHOP", unique("w4-"), "984999", 1, "PENDING", null));
        rolledBack(sql -> insertBenefit(sql, "WORKSHOP", unique("w5-"), "1", 1, "PENDING", null));
    }

    @Test
    void depositAndRemainingColumnsAreNotStoredByLoyalty() {
        List<String> columns = jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'loyalty_accepted_benefit'", String.class);
        assertThat(columns).contains("post_promotion_amount").doesNotContain("deposit_amount", "remaining_amount");
    }

    @Test
    void grantedEarnNeedsARecordedAmount() {
        assertRejected(sql -> insertBenefit(sql, "RETAIL", unique("g1-"), null, 1, "GRANTED", null));
        rolledBack(sql -> insertBenefit(sql, "RETAIL", unique("g2-"), null, 1, "GRANTED", "0"));
        assertRejected(sql -> insertBenefit(sql, "RETAIL", unique("g3-"), null, 1, "DONE", null));
    }

    @Test
    void ledgerIsUniquePerBenefitAndKindWithFixedDeltaPatterns() {
        rolledBack(sql -> {
            long benefit = insertBenefit(sql, "RETAIL", unique("l1-"), null, 1, "PENDING", null);
            insertLedger(sql, benefit, "HOLD", 0, 200, null);
            insertLedger(sql, benefit, "CONSUME", -200, -200, null);
            assertThatThrownBy(() -> insertLedger(sql, benefit, "HOLD", 0, 5, null)).satisfies(LoyaltyMySqlMigrationIT::requireConstraintViolation);
        });
        assertRejected(sql -> insertLedger(sql, insertBenefit(sql, "RETAIL", unique("l2-"), null, 1, "PENDING", null),
                "HOLD", 5, 200, null));
        assertRejected(sql -> insertLedger(sql, insertBenefit(sql, "RETAIL", unique("l3-"), null, 1, "PENDING", null),
                "CONSUME", -200, -100, null));
        assertRejected(sql -> insertLedger(sql, insertBenefit(sql, "RETAIL", unique("l4-"), null, 1, "PENDING", null),
                "RESTORE", 200, 0, null));
        assertRejected(sql -> insertLedger(sql, insertBenefit(sql, "RETAIL", unique("l5-"), null, 1, "PENDING", null),
                "EARN", 0, 0, null));
        rolledBack(sql -> {
            long benefit = insertBenefit(sql, "WORKSHOP", unique("l6-"), "880000", 1, "PENDING", null);
            insertLedger(sql, benefit, "CONSUME", -200, -200, null);
            long debit = sql.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            insertLedger(sql, benefit, "RESTORE", 200, 0, debit);
        });
    }

    @Test
    void auditAllowsManyEventsButOneCommandPerActorActionAndKey() {
        rolledBack(sql -> {
            for (int i = 0; i < 2; i++) {
                sql.update("INSERT INTO loyalty_audit (actor_ref, action, object_ref, occurred_at) VALUES ('m','a','o',?)",
                        java.sql.Timestamp.from(NOW));
            }
            String commandId = UUID.randomUUID().toString();
            String insert = "INSERT INTO loyalty_audit (actor_ref, action, object_ref, occurred_at, actor_key, command_id, "
                    + "canonical_digest, minimal_result) VALUES ('m','promotion.create','o',?,?,?,?,'{}')";
            sql.update(insert, java.sql.Timestamp.from(NOW), "manager:1", commandId, "d".repeat(64));
            sql.update(insert, java.sql.Timestamp.from(NOW), "manager:2", commandId, "d".repeat(64));
            assertThatThrownBy(() -> sql.update(insert, java.sql.Timestamp.from(NOW), "manager:1", commandId, "e".repeat(64)))
                    .satisfies(LoyaltyMySqlMigrationIT::requireConstraintViolation);
        });
        assertRejected(sql -> sql.update("INSERT INTO loyalty_audit (actor_ref, action, object_ref, occurred_at, command_id) "
                + "VALUES ('m','a','o',?,?)", java.sql.Timestamp.from(NOW), UUID.randomUUID().toString()));
    }
}
