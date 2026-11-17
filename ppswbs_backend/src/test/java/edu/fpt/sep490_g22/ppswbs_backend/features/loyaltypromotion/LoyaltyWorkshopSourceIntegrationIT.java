package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyWalletRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.MemberWorkshopBookingQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.support.legacybooking.LegacyBookingLoyaltyOutcomes;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
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
@org.springframework.context.annotation.Import(LegacyBookingLoyaltyOutcomes.class)
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class LoyaltyWorkshopSourceIntegrationIT {

    @Autowired private LoyaltyFacade loyalty;
    @Autowired private LoyaltyWalletRepository wallets;
    @Autowired private WorkshopBookingRepository bookings;
    @Autowired private MemberWorkshopBookingQuoteRepository quotes;
    @Autowired private SettlementFinalBillRepository bills;
    @Autowired private SettlementCaseRepository cases;
    @Autowired private LegacyBookingLoyaltyOutcomes bookingOutcomes;
    @Autowired private WorkshopLoyaltyOutcomeProcessor finalOutcomes;
    @Autowired private TransactionTemplate transaction;
    @MockBean private WorkshopOperationsSettlementAdapter operations;

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
