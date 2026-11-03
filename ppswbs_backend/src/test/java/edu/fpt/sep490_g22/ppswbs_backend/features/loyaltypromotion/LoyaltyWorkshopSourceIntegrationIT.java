package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyWalletRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.MemberWorkshopBookingQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingLoyaltyOutcomeService;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Datasource-backed proof that FE-03 owns and emits the Loyalty outcomes described by T050/T051. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class LoyaltyWorkshopSourceIntegrationIT {

    @Autowired private LoyaltyFacade loyalty;
    @Autowired private LoyaltyWalletRepository wallets;
    @Autowired private WorkshopBookingRepository bookings;
    @Autowired private MemberWorkshopBookingQuoteRepository quotes;
    @Autowired private SettlementFinalBillRepository bills;
    @Autowired private WorkshopBookingLoyaltyOutcomeService bookingOutcomes;
    @Autowired private WorkshopCancellationLoyaltyService cancellationOutcomes;
    @Autowired private WorkshopLoyaltyOutcomeProcessor finalOutcomes;
    @Autowired private TransactionTemplate transaction;
    @MockBean private WorkshopOperationsSettlementAdapter operations;

    @Test
    void verifiedDepositThenCompletedAndFullyPaidSettlesAndEarnsOnlyOnce() {
        Fixture fixture = acceptedMemberBooking(BookingStatus.CONFIRMED);
        transaction.executeWithoutResult(status -> bookingOutcomes.settleVerifiedDeposit(
                fixture.booking(), "ipn-" + fixture.booking().getBookingCode()));
        assertThat(loyalty.findBenefitResult(SourceKind.WORKSHOP, fixture.booking().getBookingCode()).pointsState())
                .isEqualTo(ComponentState.CONSUMED);

        bills.saveAndFlush(SettlementFinalBill.builder().billReference("BILL-" + fixture.booking().getBookingCode())
                .bookingReference(fixture.booking().getBookingCode()).postPromotionAmountVnd(900L)
                .acceptedDepositAmountVnd(450L).remainingAmountVnd(0L).currencyCode("VND")
                .lifecycleStatus("FINALIZED").build());
        when(operations.readiness(fixture.booking().getBookingCode()))
                .thenReturn(new SettlementReadiness(true, true, false, Instant.now()));
        finalOutcomes.process(fixture.booking().getBookingCode());
        finalOutcomes.process(fixture.booking().getBookingCode());

        assertThat(loyalty.findBenefitResult(SourceKind.WORKSHOP, fixture.booking().getBookingCode()).earnState())
                .isEqualTo(EarnState.GRANTED);
        assertThat(wallets.findById(fixture.memberId()).orElseThrow().getBalancePoints()).isEqualTo(BigInteger.valueOf(999));
    }

    @Test
    void confirmedUnpaidExpiryReleasesPointsAndClosesEarn() {
        Fixture fixture = acceptedMemberBooking(BookingStatus.EXPIRED);
        transaction.executeWithoutResult(status -> bookingOutcomes.finishConfirmedUnpaidExpiry(
                fixture.booking(), "verified-failure-" + fixture.booking().getBookingCode()));
        BenefitResult result = loyalty.findBenefitResult(SourceKind.WORKSHOP, fixture.booking().getBookingCode());
        assertThat(result.pointsState()).isEqualTo(ComponentState.RELEASED);
        assertThat(result.earnState()).isEqualTo(EarnState.INELIGIBLE);
        assertThat(wallets.findById(fixture.memberId()).orElseThrow().getHeldPoints()).isZero();
    }

    @Test
    void verifiedEligibleCancellationRestoresBenefitsAndFinalNoShowDoesNot() {
        Fixture cancelled = acceptedMemberBooking(BookingStatus.CONFIRMED);
        transaction.executeWithoutResult(status -> bookingOutcomes.settleVerifiedDeposit(cancelled.booking(), "deposit-cancel"));
        SettlementCase cancellation = SettlementCase.builder().caseReference("CASE-" + UUID.randomUUID())
                .bookingReference(cancelled.booking().getBookingCode()).caseType("CUSTOMER_CANCELLATION")
                .lifecycleStatus("COMPLETED").correlationReference("corr-" + UUID.randomUUID()).build();
        transaction.executeWithoutResult(status -> cancellationOutcomes.restoreVerifiedCancellation(cancellation, "refund-verified"));
        BenefitResult restored = loyalty.findBenefitResult(SourceKind.WORKSHOP, cancelled.booking().getBookingCode());
        assertThat(restored.pointsState()).isEqualTo(ComponentState.RESTORED);
        assertThat(restored.earnState()).isEqualTo(EarnState.INELIGIBLE);

        Fixture noShow = acceptedMemberBooking(BookingStatus.CONFIRMED);
        transaction.executeWithoutResult(status -> bookingOutcomes.settleVerifiedDeposit(noShow.booking(), "deposit-noshow"));
        bills.saveAndFlush(SettlementFinalBill.builder().billReference("BILL-" + noShow.booking().getBookingCode())
                .bookingReference(noShow.booking().getBookingCode()).postPromotionAmountVnd(900L)
                .acceptedDepositAmountVnd(450L).remainingAmountVnd(450L).currencyCode("VND")
                .lifecycleStatus("OPEN").build());
        when(operations.readiness(noShow.booking().getBookingCode()))
                .thenReturn(new SettlementReadiness(true, true, true, Instant.now()));
        finalOutcomes.process(noShow.booking().getBookingCode());
        BenefitResult forfeited = loyalty.findBenefitResult(SourceKind.WORKSHOP, noShow.booking().getBookingCode());
        assertThat(forfeited.pointsState()).isEqualTo(ComponentState.CONSUMED);
        assertThat(forfeited.earnState()).isEqualTo(EarnState.INELIGIBLE);
    }

    @Test
    void capacityFailureRollsBackLoyaltyAndGuestHasNoBenefit() {
        long memberId = member();
        String bookingRef = "BK-RB-" + UUID.randomUUID().toString().substring(0, 12);
        TrustedSourceContext source = source(memberId);
        BenefitSelection selection = new BenefitSelection(SelectionChoice.NONE, null, BigInteger.valueOf(100));
        BenefitPreview preview = loyalty.previewBenefits(source, selection);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            loyalty.reserveAcceptedTransaction(accepted(source, bookingRef, preview.termsDigest()), selection,
                    "create-" + bookingRef);
            throw new IllegalStateException("capacity persistence failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> loyalty.findBenefitResult(SourceKind.WORKSHOP, bookingRef))
                .isInstanceOf(LoyaltyException.class);
        assertThat(wallets.findById(memberId).orElseThrow().getHeldPoints()).isZero();

        WorkshopBooking guest = booking(null, BookingStatus.CONFIRMED, "BK-GUEST-" + UUID.randomUUID().toString().substring(0, 8));
        bookings.saveAndFlush(guest);
        transaction.executeWithoutResult(status -> bookingOutcomes.settleVerifiedDeposit(guest, "guest-ipn"));
        assertThatThrownBy(() -> loyalty.findBenefitResult(SourceKind.WORKSHOP, guest.getBookingCode()))
                .isInstanceOf(LoyaltyException.class);
    }

    private Fixture acceptedMemberBooking(BookingStatus status) {
        long memberId = member();
        String bookingRef = "BK-LY-" + UUID.randomUUID().toString().substring(0, 12);
        TrustedSourceContext source = source(memberId);
        BenefitSelection selection = new BenefitSelection(SelectionChoice.NONE, null, BigInteger.valueOf(100));
        BenefitPreview preview = loyalty.previewBenefits(source, selection);
        transaction.executeWithoutResult(tx -> loyalty.reserveAcceptedTransaction(
                accepted(source, bookingRef, preview.termsDigest()), selection, "create-" + bookingRef));

        WorkshopBooking booking = bookings.saveAndFlush(booking(memberId, status, bookingRef));
        quotes.saveAndFlush(MemberWorkshopBookingQuote.builder().quoteRef(UUID.randomUUID().toString())
                .memberAccountId(memberId).packageId(77L).packageNameSnapshot("Integration workshop")
                .pricePerParticipant(1_000_000L).participantCount(1).sessionFactsJson("[]")
                .benefitSelectionJson("{\"choice\":\"NONE\",\"pointsToUse\":100}")
                .grossAmount(1_000_000L).programDiscount(0L).pointsUsed(100L).pointDiscount(10_000L)
                .postPromotionAmount(990_000L).earnBase(990_000L).termsDigest(preview.termsDigest())
                .quoteState("ACCEPTED").acceptedBookingCode(bookingRef).createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600)).build());
        return new Fixture(memberId, booking);
    }

    private long member() {
        long id = ThreadLocalRandom.current().nextLong(100_000_000L, 900_000_000L);
        transaction.executeWithoutResult(status -> {
            LoyaltyWallet wallet = LoyaltyWallet.open(id);
            wallet.earn(BigInteger.valueOf(1_000));
            wallets.saveAndFlush(wallet);
        });
        return id;
    }

    private static TrustedSourceContext source(long memberId) {
        return new TrustedSourceContext(SourceKind.WORKSHOP, memberId, Instant.now(), List.of(),
                WorkshopFact.singleSession(77L, 2, BigInteger.valueOf(1_000_000),
                        WorkshopSessionFact.of(UUID.randomUUID().toString(), 2L, LocalDate.now().plusDays(30))),
                BigInteger.ZERO);
    }

    private static AcceptedSourceContext accepted(TrustedSourceContext source, String bookingRef, String digest) {
        Instant now = Instant.now();
        return new AcceptedSourceContext(source, bookingRef, 1L, true, now, now.plusSeconds(600),
                "Q-" + bookingRef, digest);
    }

    private static WorkshopBooking booking(Long memberId, BookingStatus status, String bookingRef) {
        return WorkshopBooking.builder().bookingCode(bookingRef).packageId(77L)
                .packageNameSnapshot("Integration workshop").contactEmail("integration@example.test")
                .contactPhone("0900000000").memberAccountId(memberId).participantCount(1)
                .bookingStatus(status).paymentStatus(status == BookingStatus.CONFIRMED
                        ? PaymentStatus.DEPOSIT_PAID : PaymentStatus.UNPAID)
                .holdExpiresAt(Instant.now().plusSeconds(600)).build();
    }

    private record Fixture(long memberId, WorkshopBooking booking) {}
}
