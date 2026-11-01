package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport.Outcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.RealWorkshopBookings;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingOutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.DepositPaymentEvidenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SettlementReadiness;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopCancellationLoyaltyService;
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
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * T063, source side, on MySQL 8.4 with two real transactions started together: FE-03's own services decide
 * (verified deposit through {@code WorkshopBookingFacade}, expiry through {@code BookingDepositReconciliationService},
 * {@link WorkshopCancellationLoyaltyService}, {@link WorkshopLoyaltyOutcomeProcessor}); only Workshop Operations
 * (003.2) and the Member identity check (001) are replaced. The same verified deposit delivered twice at once consumes
 * once; a cancellation racing the completion either restores or earns, never both; a deposit racing the expiry of its
 * hold leaves the unclear payment to reconciliation and never releases the benefits on its own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
@Import(RealWorkshopBookings.class)
class LoyaltyWorkshopSourceConcurrencyIT {

    private static final int ROUNDS = 3;

    @Autowired private RealWorkshopBookings workshop;
    @Autowired private WorkshopCancellationLoyaltyService cancellations;
    @Autowired private WorkshopLoyaltyOutcomeProcessor finalOutcomes;
    @Autowired private SettlementFinalBillRepository bills;
    @Autowired private LoyaltyFacade loyalty;
    @Autowired private TransactionTemplate transaction;

    @MockitoBean private WorkshopOperationsAdapter operations;
    @MockitoBean private WorkshopBookingAuthorizationService authorization;
    @MockitoBean private WorkshopOperationsSettlementAdapter settlementOperations;

    private record Booked(long member, String code, WorkshopBooking booking) {
    }

    /** 2 x 500.000, voucher 100.000, 900 points: post 810.000, deposit 405.000. Accepted, deposit not paid yet. */
    private Booked accepted() {
        UUID session = workshop.openSession(500_000L);
        long member = workshop.member(2_000);
        String code = workshop.voucher(100_000);
        WorkshopBooking booking = workshop.accept(member, workshop.quote(member, session, 2, "VOUCHER", code, "900").quoteRef());
        return new Booked(member, code, booking);
    }

    @Test
    void theSameVerifiedDepositDeliveredTwiceAtOnceConsumesOnce() {
        for (int round = 0; round < ROUNDS; round++) {
            Booked booked = accepted();
            Callable<DepositPaymentEvidenceOutcome> deposit = workshop.prepareDeposit(booked.booking());

            List<Outcome<DepositPaymentEvidenceOutcome>> results = LoyaltyTestSupport.concurrently(deposit, deposit);

            assertThat(results).anySatisfy(result -> assertThat(result.value().status()).isEqualTo(BookingOutcomeStatus.ACCEPTED));
            BenefitResult result = result(booked.booking().getBookingCode());
            assertThat(result.pointsState()).isEqualTo(ComponentState.CONSUMED);
            assertThat(result.voucherState()).isEqualTo(ComponentState.CONSUMED);
            assertThat(workshop.wallet(booked.member()).getBalancePoints()).isEqualTo(BigInteger.valueOf(1_100));
            assertThat(workshop.wallet(booked.member()).getHeldPoints()).isZero();
            assertThat(workshop.program(booked.code()).getConsumedCount()).isEqualTo(1);
            assertThat(workshop.reload(booked.booking().getBookingCode()).getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        }
    }

    @Test
    void aCancellationRacingTheCompletionEitherRestoresOrEarnsNeverBoth() {
        for (int round = 0; round < ROUNDS; round++) {
            Booked booked = accepted();
            assertThat(workshop.payDeposit(booked.booking()).status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
            String ref = booked.booking().getBookingCode();
            bills.saveAndFlush(SettlementFinalBill.builder().billReference("BILL-" + UUID.randomUUID()).bookingReference(ref)
                    .postPromotionAmountVnd(810_000L).acceptedDepositAmountVnd(405_000L).remainingAmountVnd(0L)
                    .currencyCode("VND").lifecycleStatus("FINALIZED").build());
            when(settlementOperations.readiness(ref)).thenReturn(new SettlementReadiness(true, true, false, Instant.now()));
            SettlementCase cancellation = SettlementCase.builder().caseReference("CASE-" + UUID.randomUUID()).bookingReference(ref)
                    .caseType("CUSTOMER_CANCELLATION").lifecycleStatus("COMPLETED").correlationReference("corr").build();

            LoyaltyTestSupport.concurrently(
                    () -> { transaction.executeWithoutResult(status -> cancellations.restoreVerifiedCancellation(cancellation, "refund")); return null; },
                    () -> { finalOutcomes.process(ref); return null; });

            BenefitResult result = result(ref);
            BigInteger balance = workshop.wallet(booked.member()).getBalancePoints();
            if (result.earnState() == EarnState.GRANTED) {
                assertThat(result.pointsState()).as("earned, so nothing was given back").isEqualTo(ComponentState.CONSUMED);
                assertThat(balance).isGreaterThan(BigInteger.valueOf(1_100));
            } else {
                assertThat(result.earnState()).isEqualTo(EarnState.INELIGIBLE);
                assertThat(result.pointsState()).isEqualTo(ComponentState.RESTORED);
                assertThat(balance).as("restored, nothing earned").isEqualTo(BigInteger.valueOf(2_000));
            }
            assertThat(workshop.wallet(booked.member()).getHeldPoints()).isZero();
        }
    }

    @Test
    void aDepositRacingTheExpiryOfItsHoldLeavesTheUnclearPaymentToReconciliation() {
        Booked booked = accepted();
        Callable<DepositPaymentEvidenceOutcome> deposit = workshop.prepareDeposit(booked.booking());
        workshop.holdRunsOut(booked.booking());

        List<Outcome<Object>> results = LoyaltyTestSupport.concurrently(
                () -> deposit.call(),
                () -> { workshop.runExpiryReconciliation(); return null; });

        assertThat(((DepositPaymentEvidenceOutcome) results.get(0).value()).status())
                .as("a deposit after the hold ran out is not applied").isEqualTo(BookingOutcomeStatus.REJECTED);
        BenefitResult result = result(booked.booking().getBookingCode());
        assertThat(result.pointsState()).as("an attempt exists, so FE-03 reconciles before any release").isEqualTo(ComponentState.HELD);
        assertThat(result.voucherState()).isEqualTo(ComponentState.HELD);
        assertThat(workshop.wallet(booked.member()).getHeldPoints()).isEqualTo(BigInteger.valueOf(900));
        assertThat(workshop.wallet(booked.member()).getBalancePoints()).isEqualTo(BigInteger.valueOf(2_000));
    }

    private BenefitResult result(String bookingCode) {
        return loyalty.findBenefitResult(SourceKind.WORKSHOP, bookingCode);
    }
}
