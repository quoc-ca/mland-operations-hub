package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyRegistryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.MemberProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PromotionUsageDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.MemberProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyRestorationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.PromotionUsageQueryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T042: current usage report (FR-008, US2.1, US2.9). Holds and previews never count; a validly cancelled booking
 * leaves the totals once its restore is applied, a repeated cancellation never subtracts twice, history is kept, and
 * expiry or pause never removes uncancelled settled usage. An automatic discount counts settled transactions only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class PromotionUsageIT {

    private static final Instant AT = Instant.parse("2026-10-27T01:00:00Z");

    @Autowired private TransactionTemplate transaction;
    @Autowired private PromotionUsageQueryService usage;
    @Autowired private LoyaltyRestorationService restore;
    @Autowired private PromotionProgramRepository programs;
    @Autowired private PromotionRevisionRepository revisions;
    @Autowired private MemberProgramRepository memberPrograms;
    @Autowired private AcceptedBenefitRepository benefits;
    @Autowired private LoyaltyRegistryRepository registry;

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    private long policyId() {
        return registry.findById(LoyaltyRegistry.SINGLETON_ID).orElseThrow().getCurrentPolicyId();
    }

    private long program(ProgramKind kind) {
        return transaction.execute(status -> {
            String code = "UG" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            PromotionProgram program = programs.saveAndFlush(kind == ProgramKind.VOUCHER
                    ? PromotionProgram.voucher(TargetScope.WORKSHOP, code, code, 10, 5, AT, 1L)
                    : PromotionProgram.autoDiscount(AT, 1L));
            PromotionRevision revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1, kind, "U",
                    AT, AT.plusSeconds(86_400), DiscountTerms.fixed(n(100_000), n(100_000), BigInteger.ZERO), true, AT, 1L));
            program.pointToRevision(revision.getId());
            return program.getId();
        });
    }

    /** One accepted booking (voucher programme: one use held), optionally settled by the verified deposit. */
    private String booking(long programId, boolean voucher, boolean settled) {
        String sourceRef = "usage-" + UUID.randomUUID();
        long memberId = ThreadLocalRandom.current().nextLong(100_000_000L, 900_000_000L);
        SourceKind kind = voucher ? SourceKind.WORKSHOP : SourceKind.RETAIL;
        transaction.executeWithoutResult(status -> {
            PromotionProgram program = programs.findById(programId).orElseThrow();
            MemberProgram counters = null;
            if (voucher) {
                program.hold();
                counters = MemberProgram.create(programId, memberId);
                counters.hold(5);
                counters = memberPrograms.saveAndFlush(counters);
            }
            AcceptedBenefit benefit = benefits.saveAndFlush(AcceptedBenefit.accept(new AcceptedBenefit.Snapshot(kind,
                    sourceRef, memberId, AT, AT.plusSeconds(1800), 1L, "d", policyId(), programId,
                    program.getCurrentRevisionId(), n(1_000_000), n(100_000), BigInteger.ZERO, BigInteger.ZERO,
                    n(900_000), voucher ? n(900_000) : null, voucher), true));
            if (settled) {
                benefit.settle("dep-" + sourceRef, 1, AT);
                if (voucher) {
                    program.consume();
                    counters.consume();
                }
            }
        });
        return sourceRef;
    }

    private void cancel(String sourceRef) {
        transaction.executeWithoutResult(status -> restore.restoreCancelledWorkshopBenefits(new AuthoritativeOutcome(
                SourceKind.WORKSHOP, sourceRef, "workshopbooking", "cancel-" + sourceRef, 2,
                OutcomeKind.VALID_WORKSHOP_CANCELLATION, AT.plusSeconds(60), AT.plusSeconds(1800), "s", true, "p")));
    }

    @Test
    void voucherUsageCountsSettledUsesOnlyAndDropsAValidCancellationOnce() {
        long programId = program(ProgramKind.VOUCHER);
        String cancelled = booking(programId, true, true);
        booking(programId, true, true);
        booking(programId, true, false);

        PromotionUsageDtos.ProgramUsage before = (PromotionUsageDtos.ProgramUsage) usage.usage(programId);
        assertThat(before.consumedCount()).isEqualTo("2");
        assertThat(before.heldCount()).isEqualTo("1");
        assertThat(before.committedDiscountAmount()).isEqualTo("200000");
        assertThat(before.totalLimit()).isEqualTo("10");
        assertThat(before.perMemberLimit()).isEqualTo("5");

        cancel(cancelled);
        cancel(cancelled);
        PromotionUsageDtos.ProgramUsage after = (PromotionUsageDtos.ProgramUsage) usage.usage(programId);
        assertThat(after.consumedCount()).isEqualTo("1");
        assertThat(after.committedDiscountAmount()).isEqualTo("100000");
        AcceptedBenefit history = benefits.findBySourceKindAndSourceRef(SourceKind.WORKSHOP, cancelled).orElseThrow();
        assertThat(history.getVoucherConsumedAt()).as("history kept").isNotNull();
        assertThat(history.getVoucherRestoredAt()).isNotNull();
    }

    @Test
    void aSingleCancelledBookingLeavesZeroUsedAndZeroDiscount() {
        // US2.9
        long programId = program(ProgramKind.VOUCHER);
        String only = booking(programId, true, true);
        cancel(only);
        PromotionUsageDtos.ProgramUsage report = (PromotionUsageDtos.ProgramUsage) usage.usage(programId);
        assertThat(report.consumedCount()).isEqualTo("0");
        assertThat(report.committedDiscountAmount()).isEqualTo("0");
    }

    @Test
    void pausingOrExpiringNeverRemovesUncancelledSettledUsage() {
        long programId = program(ProgramKind.VOUCHER);
        booking(programId, true, true);
        transaction.executeWithoutResult(status -> programs.findById(programId).orElseThrow().pause());
        PromotionUsageDtos.ProgramUsage report = (PromotionUsageDtos.ProgramUsage) usage.usage(programId);
        assertThat(report.consumedCount()).isEqualTo("1");
        assertThat(report.committedDiscountAmount()).isEqualTo("100000");
    }

    @Test
    void automaticDiscountUsageCountsSettledTransactionsOnly() {
        long programId = program(ProgramKind.AUTO_DISCOUNT);
        booking(programId, false, true);
        booking(programId, false, true);
        booking(programId, false, false);
        PromotionUsageDtos.AutoUsage report = (PromotionUsageDtos.AutoUsage) usage.usage(programId);
        assertThat(report.acceptedCount()).isEqualTo("2");
        assertThat(report.committedDiscountAmount()).isEqualTo("200000");
    }
}
