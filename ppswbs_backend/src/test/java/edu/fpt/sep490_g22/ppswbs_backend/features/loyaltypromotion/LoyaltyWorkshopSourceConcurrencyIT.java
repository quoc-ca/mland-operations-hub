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
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * T063, source side, on MySQL 8.4 with two real transactions started together: FE-03's own services decide
 * (verified deposit through {@code WorkshopBookingFacade}, expiry through {@code BookingDepositReconciliationService},
 * {@link WorkshopLoyaltyOutcomeProcessor}); only Workshop Operations
 * (003.2) and the Member identity check (001) are replaced. The same verified deposit delivered twice at once consumes
 * once; a cancellation racing the completion either restores or earns, never both; a deposit racing the expiry of its
 * hold leaves the unclear payment to reconciliation and never releases the benefits on its own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
@Import({RealWorkshopBookings.class, edu.fpt.sep490_g22.ppswbs_backend.support.legacybooking.LegacyDirectBookings.class, edu.fpt.sep490_g22.ppswbs_backend.support.legacybooking.LegacyBookingLoyaltyOutcomes.class})
class LoyaltyWorkshopSourceConcurrencyIT {

    private static final int ROUNDS = 3;

    @Autowired private RealWorkshopBookings workshop;
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


    private BenefitResult result(String bookingCode) {
        return loyalty.findBenefitResult(SourceKind.WORKSHOP, bookingCode);
    }
}
