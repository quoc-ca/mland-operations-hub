package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.LoyaltyAccountDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PromotionUsageDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyAccountQueryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.PromotionUsageQueryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.RealWorkshopBookings;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingOutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.DepositPaymentEvidenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopLoyaltyOutcomeProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * T062/T065 on MySQL 8.4 with FE-03's REAL sources (develop 8d69792): the booking is accepted through
 * the single-booking rows seeded by {@code LegacyDirectBookings}; deposit, unpaid expiry, customer cancellation, no-show and completion
 * are decided by FE-03's own services (verified deposit through {@code WorkshopBookingFacade}, expiry through
 * {@code LegacyDirectBookings},
 * {@link WorkshopLoyaltyOutcomeProcessor}) under their source guards.
 * Only Workshop Operations (003.2) and the Member identity check (001) are replaced. A valid cancellation after the
 * deposit gives back the points and the use exactly once and the usage report returns to zero while the history stays;
 * a case that is not a customer cancellation, a no-show, an unpaid remainder or a late deposit never restores or earns.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
@Import({RealWorkshopBookings.class, edu.fpt.sep490_g22.ppswbs_backend.support.legacybooking.LegacyDirectBookings.class, edu.fpt.sep490_g22.ppswbs_backend.support.legacybooking.LegacyBookingLoyaltyOutcomes.class})
class LoyaltyRecoverySourceIntegrationIT {

    @Autowired private RealWorkshopBookings workshop;
    @Autowired private WorkshopLoyaltyOutcomeProcessor finalOutcomes;
    @Autowired private SettlementFinalBillRepository bills;
    @Autowired private LoyaltyFacade loyalty;
    @Autowired private PromotionUsageQueryService usage;
    @Autowired private LoyaltyAccountQueryService accounts;
    @Autowired private PromotionProgramRepository programs;
    @Autowired private TransactionTemplate transaction;

    @MockitoBean private WorkshopOperationsAdapter operations;
    @MockitoBean private WorkshopBookingAuthorizationService authorization;
    @MockitoBean private WorkshopOperationsSettlementAdapter settlementOperations;

    /** 2 x 500.000 with a 100.000 voucher and 900 points: post 810.000, deposit 405.000, remaining 405.000. */
    private Booked bookAndPayDeposit() {
        UUID session = workshop.openSession(500_000L);
        long member = workshop.member(2_000);
        String code = workshop.voucher(100_000);
        String quoteRef = workshop.quote(member, session, 2, "VOUCHER", code, "900").quoteRef();
        WorkshopBooking booking = workshop.accept(member, quoteRef);
        assertThat(workshop.payDeposit(booking).status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        return new Booked(member, code, booking.getBookingCode());
    }

    @Test
    void aLateDepositAfterAConfirmedUnpaidExpiryChangesNothing() {
        UUID session = workshop.openSession(500_000L);
        long member = workshop.member(2_000);
        String code = workshop.voucher(100_000);
        WorkshopBooking booking = workshop.accept(member, workshop.quote(member, session, 2, "VOUCHER", code, "900").quoteRef());
        workshop.expireUnpaid(booking);
        assertThat(workshop.reload(booking.getBookingCode()).getBookingStatus()).isNotEqualTo(BookingStatus.PENDING_PAYMENT);

        DepositPaymentEvidenceOutcome late = workshop.payDeposit(workshop.reload(booking.getBookingCode()));
        assertThat(late.status()).as("a single booking deposit is refused after the expiry").isEqualTo(BookingOutcomeStatus.REJECTED);

        BenefitResult result = result(booking.getBookingCode());
        assertThat(result.pointsState()).isEqualTo(ComponentState.RELEASED);
        assertThat(result.voucherState()).isEqualTo(ComponentState.RELEASED);
        assertThat(result.earnState()).isEqualTo(EarnState.INELIGIBLE);
        assertThat(workshop.wallet(member).getBalancePoints()).isEqualTo(BigInteger.valueOf(2_000));
        assertThat(workshop.wallet(member).getHeldPoints()).isZero();
        assertThat(report(code).consumedCount()).isEqualTo("0");
    }

    @Test
    void aPausedProgrammeKeepsTheUsageOfUncancelledBookings() {
        Booked booked = bookAndPayDeposit();
        transaction.executeWithoutResult(status -> programs.findByCodeKey(booked.code()).orElseThrow().pause());

        PromotionUsageDtos.ProgramUsage report = report(booked.code());
        assertThat(report.consumedCount()).isEqualTo("1");
        assertThat(report.committedDiscountAmount()).isEqualTo("100000");
    }

    // ---- helpers ----

    private record Booked(long member, String code, String bookingCode) {
    }

    private BenefitResult result(String bookingCode) {
        return loyalty.findBenefitResult(SourceKind.WORKSHOP, bookingCode);
    }

    private PromotionUsageDtos.ProgramUsage report(String code) {
        return (PromotionUsageDtos.ProgramUsage) usage.usage(workshop.program(code).getId());
    }

    private static SettlementCase customerCancellation(String bookingCode, String caseType) {
        return SettlementCase.builder().caseReference("CASE-" + UUID.randomUUID()).bookingReference(bookingCode)
                .caseType(caseType).lifecycleStatus("COMPLETED").correlationReference("corr-" + UUID.randomUUID()).build();
    }

    /**
     * Since 003.3 T087 a confirmed deposit opens the booking's single folio asynchronously (Modulith listener),
     * so the test sets the folio to the state it needs instead of inserting a second one.
     */
    private void finalBill(String bookingCode, String status, long remaining) throws InterruptedException {
        SettlementFinalBill folio = null;
        for (int i = 0; i < 50 && folio == null; i++) {
            folio = bills.findByBookingReference(bookingCode).orElse(null);
            if (folio == null) Thread.sleep(100);
        }
        if (folio == null) {
            folio = SettlementFinalBill.builder().billReference("BILL-" + UUID.randomUUID()).bookingReference(bookingCode)
                    .postPromotionAmountVnd(810_000L).acceptedDepositAmountVnd(405_000L).currencyCode("VND").build();
        }
        folio.setRemainingAmountVnd(remaining);
        folio.setLifecycleStatus(status);
        bills.saveAndFlush(folio);
    }
}
