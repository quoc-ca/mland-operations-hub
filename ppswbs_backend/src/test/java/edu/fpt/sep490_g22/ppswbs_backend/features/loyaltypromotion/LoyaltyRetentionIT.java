package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyRegistryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PointLedgerRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.LoyaltyAccountDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LedgerEntryKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PointLedger;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyAccountQueryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * T074 (FR-041, US6.9): what keeps loyalty history for at least five years, checked so a later change cannot quietly
 * remove it, and the paging cost of a long point history measured on MySQL 8.4.
 *
 * <ul>
 *   <li>No loyalty code deletes rows: no repository {@code delete*} call and no {@code EntityManager.remove}. Holds are
 *       released only through {@code LoyaltyFacade} outcomes from the source owner; loyalty has no scheduled job of its
 *       own that could release, restore or purge anything, so disabling a route cannot change accepted holds.</li>
 *   <li>Codes are never reused and replay evidence is never lost: those are database keys covered by
 *       {@code LoyaltyMySqlMigrationIT} (code key, accepted benefit per source, ledger per benefit and kind, receipt per
 *       actor/action/command) and by the create/maintenance contract tests.</li>
 *   <li>A Member history of 1,000 rows pages through the keyset cursor with no gap or duplicate, and MySQL serves the
 *       page from {@code idx_loyalty_ledger_member}. The elapsed time is printed for the verification record; it is a
 *       measurement on a test fixture, not a production SLA.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class LoyaltyRetentionIT {

    private static final String LOYALTY = "edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion..";
    private static final int HISTORY_ROWS = 1_000;

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion");

    @Autowired private DataSource dataSource;
    @Autowired private TransactionTemplate transaction;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LoyaltyRegistryRepository registry;
    @Autowired private AcceptedBenefitRepository benefits;
    @Autowired private PointLedgerRepository ledger;
    @Autowired private LoyaltyAccountQueryService accounts;

    @Test
    void loyaltyCodeNeverDeletesRows() {
        noClasses().that().resideInAPackage(LOYALTY)
                .should().callMethodWhere(DescribedPredicate.<JavaMethodCall>describe("a repository delete method",
                        call -> call.getTarget().getName().startsWith("delete")
                                && call.getTargetOwner().isAssignableTo(Repository.class)))
                .because("loyalty history, receipts and audit are kept at least five years (FR-041)")
                .check(CLASSES);
        noClasses().that().resideInAPackage(LOYALTY)
                .should().callMethod(EntityManager.class, "remove", Object.class)
                .because("loyalty history, receipts and audit are kept at least five years (FR-041)")
                .check(CLASSES);
    }

    @Test
    void loyaltyHasNoScheduledJobThatCouldReleaseRestoreOrPurge() {
        noMethods().that().areDeclaredInClassesThat().resideInAPackage(LOYALTY)
                .should().beAnnotatedWith(Scheduled.class)
                .because("holds change only on an authoritative source outcome; disabling a route releases nothing")
                .check(CLASSES);
    }

    @Test
    void aLongHistoryPagesWithoutGapOrDuplicateFromTheMemberIndex() {
        LoyaltyTestSupport.requireMySql84(dataSource);
        long member = ThreadLocalRandom.current().nextLong(100_000_000L, 900_000_000L);
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        transaction.executeWithoutResult(status -> {
            long policyId = registry.findById(LoyaltyRegistry.SINGLETON_ID).orElseThrow().getCurrentPolicyId();
            List<AcceptedBenefit> created = new ArrayList<>();
            for (int i = 0; i < HISTORY_ROWS; i++) {
                created.add(AcceptedBenefit.accept(new AcceptedBenefit.Snapshot(SourceKind.RETAIL,
                        "retention-" + member + "-" + i, member, base, base.plusSeconds(900), 1L, "digest", policyId,
                        null, null, BigInteger.valueOf(10_000), BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO,
                        BigInteger.valueOf(10_000), null, false), true));
            }
            List<AcceptedBenefit> saved = benefits.saveAll(created);
            List<PointLedger> entries = new ArrayList<>();
            for (int i = 0; i < HISTORY_ROWS; i++) {
                // Pairs of rows share a timestamp, so the cursor must break ties by ID.
                entries.add(PointLedger.entry(LedgerEntryKind.EARN, member, saved.get(i).getId(), BigInteger.ONE,
                        BigInteger.valueOf(i + 1L), BigInteger.ZERO, base.plusSeconds(i / 2), null));
            }
            ledger.saveAll(entries);
        });

        long started = System.nanoTime();
        Set<String> seen = new HashSet<>();
        List<String> order = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            LoyaltyAccountDtos.TransactionPage page = accounts.transactions(member, 100, cursor);
            page.items().forEach(item -> {
                assertThat(seen.add(item.id())).as("no row on two pages").isTrue();
                order.add(item.occurredAt() + "|" + String.format("%019d", Long.parseLong(item.id())));
            });
            cursor = page.nextCursor();
            pages++;
        } while (cursor != null && pages < 50);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertThat(seen).hasSize(HISTORY_ROWS);
        assertThat(pages).isEqualTo(HISTORY_ROWS / 100);
        assertThat(order).isSortedAccordingTo((a, b) -> b.compareTo(a));
        System.out.printf("T074 measurement: %d ledger rows paged 100 at a time in %d pages, %d ms in total%n",
                HISTORY_ROWS, pages, elapsedMillis);

        List<Map<String, Object>> plan = jdbc.queryForList("""
                EXPLAIN SELECT id FROM loyalty_point_ledger
                WHERE member_id = ? AND (occurred_at < ? OR (occurred_at = ? AND id < ?))
                ORDER BY occurred_at DESC, id DESC LIMIT 101""",
                member, base.plusSeconds(400), base.plusSeconds(400), Long.MAX_VALUE);
        assertThat(plan).singleElement().satisfies(row -> assertThat(row.get("key")).isEqualTo("idx_loyalty_ledger_member"));
    }
}
