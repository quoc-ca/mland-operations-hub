package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyWalletRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.ComponentState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.EarnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingInvoiceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.MemberWorkshopBookingQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingSummaryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.CreateBookingRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.WorkshopBookingQuoteRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.WorkshopBookingQuoteResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingInvoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.MemberQuoteBookingAcceptanceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingLoyaltyOutcomeService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingQuoteService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityHoldOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * T059 on MySQL 8.4 through FE-03's REAL quote and acceptance services ({@link WorkshopBookingQuoteService},
 * {@link MemberQuoteBookingAcceptanceService}, develop 8d69792): the Member's voucher and points are reserved in the
 * same transaction as the capacity holds, the deposit is ceil(post/2) of the post-promotion amount, a capacity
 * refusal or a quote with reasons reserves nothing, and the verified deposit consumes the points once. Only the
 * boundaries owned by other features are replaced: Workshop Operations (003.2: package, sessions, capacity) and the
 * Member identity check (001). The quote, the acceptance and every loyalty call are FE-03's and FE-06's own code.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class LoyaltyWorkshopBookingAcceptanceIT {

    private static final long PACKAGE_ID = 9_771L;
    private static final long BRANCH_ID = 3L;
    private static final long PRICE = 500_000L;
    private static final LocalDate DAY = LocalDate.now().plusDays(20);

    @Autowired private WorkshopBookingQuoteService quoteService;
    @Autowired private MemberQuoteBookingAcceptanceService acceptance;
    @Autowired private WorkshopBookingLoyaltyOutcomeService outcomes;
    @Autowired private MemberWorkshopBookingQuoteRepository quotes;
    @Autowired private WorkshopBookingRepository bookings;
    @Autowired private BookingInvoiceRepository invoices;
    @Autowired private LoyaltyFacade loyalty;
    @Autowired private LoyaltyWalletRepository wallets;
    @Autowired private PromotionProgramRepository programs;
    @Autowired private PromotionRevisionRepository revisions;
    @Autowired private PromotionTargetRepository targets;
    @Autowired private TransactionTemplate transaction;

    @MockitoBean private WorkshopOperationsAdapter operations;
    @MockitoBean private WorkshopBookingAuthorizationService authorization;

    private UUID session;

    @BeforeEach
    void publishedPackageWithOneOpenSession() {
        session = UUID.randomUUID();
        when(operations.getPublishedPackageSelection(Long.toString(PACKAGE_ID))).thenReturn(new PublishedPackageSelectionSnapshot(
                Long.toString(PACKAGE_ID), PRICE, 1, 4, 1, false, 0, "Workshop nhẫn bạc"));
        when(operations.getSaleEligibility(session)).thenReturn(new SaleEligibilitySnapshot(session, true, "OPEN", BRANCH_ID,
                Instant.now().plus(20, ChronoUnit.DAYS), Instant.now().plus(20, ChronoUnit.DAYS).plusSeconds(7_200), 10, DAY));
        when(operations.reserveBookingCapacity(anyString(), eq(Long.toString(PACKAGE_ID)), anyList(), anyInt(), anyString(), any()))
                .thenAnswer(call -> CapacityHoldOutcome.accepted(List.of(UUID.randomUUID()), Instant.now().plusSeconds(900)));
    }

    @Test
    void acceptingReservesVoucherAndPointsWithTheCapacityAndTheDepositConsumesPointsOnce() {
        long member = member(2_000);
        String code = workshopVoucher();
        WorkshopBookingQuoteResponse quote = quote(member, "VOUCHER", code, "900");
        // 2 x 500.000 = 1.000.000; voucher 100.000; 900 points x 100 = 90.000 (policy: at most 10%) -> 810.000,
        // deposit ceil(810.000 / 2) = 405.000.
        assertThat(quote.reasons()).isEmpty();
        assertThat(quote.programDiscount()).isEqualTo("100000");
        assertThat(quote.pointDiscount()).isEqualTo("90000");
        assertThat(quote.postPromotionAmount()).isEqualTo("810000");
        assertThat(quote.depositAmount()).isEqualTo("405000");

        BookingSummaryResponse booking = accept(member, quote.quoteRef());

        BenefitResult held = loyalty.findBenefitResult(SourceKind.WORKSHOP, booking.getBookingCode());
        assertThat(held.voucherState()).isEqualTo(ComponentState.HELD);
        assertThat(held.pointsState()).isEqualTo(ComponentState.HELD);
        assertThat(wallet(member).getHeldPoints()).isEqualTo(BigInteger.valueOf(900));
        assertThat(program(code).getHeldCount()).isEqualTo(1);
        BookingInvoice invoice = invoice(booking.getBookingCode());
        assertThat(invoice.getPostPromotionAmount()).isEqualTo(810_000L);
        assertThat(invoice.getDepositAmount()).isEqualTo(405_000L);
        assertThat(invoice.getRemainingAmount()).isEqualTo(405_000L);
        assertThat(quotes.findByQuoteRefAndMemberAccountId(quote.quoteRef(), member).orElseThrow().getQuoteState()).isEqualTo("ACCEPTED");

        WorkshopBooking saved = bookings.findByBookingCode(booking.getBookingCode()).orElseThrow();
        transaction.executeWithoutResult(status -> outcomes.settleVerifiedDeposit(saved, "ipn-" + saved.getBookingCode()));
        transaction.executeWithoutResult(status -> outcomes.settleVerifiedDeposit(saved, "ipn-" + saved.getBookingCode()));

        BenefitResult settled = loyalty.findBenefitResult(SourceKind.WORKSHOP, booking.getBookingCode());
        assertThat(settled.voucherState()).isEqualTo(ComponentState.CONSUMED);
        assertThat(settled.pointsState()).isEqualTo(ComponentState.CONSUMED);
        assertThat(settled.earnState()).as("the deposit consumes, it never earns").isNotEqualTo(EarnState.GRANTED);
        assertThat(wallet(member).getHeldPoints()).isZero();
        assertThat(wallet(member).getBalancePoints()).as("points taken once at the deposit").isEqualTo(BigInteger.valueOf(1_100));
        assertThat(program(code).getConsumedCount()).isEqualTo(1);
        // The remaining amount is already net of the points: deposit + remaining = post-promotion amount.
        assertThat(invoice.getDepositAmount() + invoice.getRemainingAmount()).isEqualTo(invoice.getPostPromotionAmount());
    }

    @Test
    void anOddPostPromotionAmountRoundsTheDepositUp() {
        long member = member(100);
        when(operations.getPublishedPackageSelection(Long.toString(PACKAGE_ID))).thenReturn(new PublishedPackageSelectionSnapshot(
                Long.toString(PACKAGE_ID), 500_001L, 1, 4, 1, false, 0, "Workshop nhẫn bạc"));
        when(authorization.requireMemberOwnership(anyString(), isNull())).thenReturn(
                new AccountSummary(member, null, null, "member@example.test", Instant.now(), Instant.now()));
        WorkshopBookingQuoteResponse quote = quoteService.preview("Bearer test", new WorkshopBookingQuoteRequest(PACKAGE_ID,
                List.of(session.toString()), 1, new WorkshopBookingQuoteRequest.BenefitSelectionRequest(
                        WorkshopBookingQuoteRequest.BenefitSelectionRequest.Choice.NONE, null, "0")));
        assertThat(quote.postPromotionAmount()).isEqualTo("500001");
        assertThat(quote.depositAmount()).isEqualTo("250001");

        BookingInvoice invoice = invoice(accept(member, quote.quoteRef()).getBookingCode());
        assertThat(invoice.getDepositAmount()).as("ceil(500.001 / 2)").isEqualTo(250_001L);
        assertThat(invoice.getRemainingAmount()).isEqualTo(250_000L);
    }

    @Test
    void aCapacityRefusalReservesNothingAndKeepsTheQuoteOpen() {
        long member = member(2_000);
        String code = workshopVoucher();
        WorkshopBookingQuoteResponse quote = quote(member, "VOUCHER", code, "900");
        assertThat(quote.reasons()).isEmpty();
        when(operations.reserveBookingCapacity(anyString(), anyString(), anyList(), anyInt(), anyString(), any()))
                .thenReturn(CapacityHoldOutcome.rejected(RejectionReason.INSUFFICIENT_CAPACITY));

        assertThatThrownBy(() -> accept(member, quote.quoteRef())).hasMessageContaining("rejected");

        assertThat(wallet(member).getHeldPoints()).isZero();
        assertThat(program(code).getHeldCount()).isZero();
        assertThat(quotes.findByQuoteRefAndMemberAccountId(quote.quoteRef(), member).orElseThrow().getQuoteState()).isEqualTo("OPEN");
    }

    @Test
    void aQuoteWithReasonsCannotBeAcceptedAndHoldsNothing() {
        long member = member(5);
        WorkshopBookingQuoteResponse quote = quote(member, "NONE", null, "5");
        assertThat(quote.reasons()).as("5 points is below the minimum").isNotEmpty();

        assertThatThrownBy(() -> accept(member, quote.quoteRef())).hasMessageContaining("no longer eligible");

        assertThat(wallet(member).getHeldPoints()).isZero();
        assertThat(bookings.findByMemberAccountId(member)).isEmpty();
    }

    @Test
    void aGuestBookingHasNoLoyaltyEffect() {
        String unknown = "BK-NONE-" + UUID.randomUUID().toString().substring(0, 8);
        assertThatThrownBy(() -> loyalty.findBenefitResult(SourceKind.WORKSHOP, unknown)).isInstanceOf(LoyaltyException.class);
    }

    // ---- helpers ----

    private WorkshopBookingQuoteResponse quote(long member, String choice, String code, String points) {
        when(authorization.requireMemberOwnership(anyString(), isNull())).thenReturn(
                new AccountSummary(member, null, null, "member@example.test", Instant.now(), Instant.now()));
        return quoteService.preview("Bearer test", new WorkshopBookingQuoteRequest(PACKAGE_ID, List.of(session.toString()), 2,
                new WorkshopBookingQuoteRequest.BenefitSelectionRequest(
                        WorkshopBookingQuoteRequest.BenefitSelectionRequest.Choice.valueOf(choice), code, points)));
    }

    private BookingSummaryResponse accept(long member, String quoteRef) {
        CreateBookingRequest request = CreateBookingRequest.builder().quoteRef(quoteRef)
                .contactEmail("member@example.test").contactPhone("0900000000").build();
        return acceptance.accept(request, member, "corr-" + UUID.randomUUID(), null);
    }

    /** Fixed 100.000 off this package, combinable with points, 10 uses. */
    private String workshopVoucher() {
        String code = "WB" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        transaction.executeWithoutResult(status -> {
            PromotionProgram program = programs.saveAndFlush(PromotionProgram.voucher(TargetScope.WORKSHOP, code, code, 10, 1, now, 1L));
            PromotionRevision revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1, ProgramKind.VOUCHER,
                    "Workshop acceptance IT", now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS),
                    DiscountTerms.fixed(BigInteger.valueOf(100_000), BigInteger.valueOf(100_000), BigInteger.ZERO), true, now, 1L));
            targets.save(PromotionTarget.create(revision.getId(), TargetScope.WORKSHOP, TargetDimension.PACKAGE, Long.toString(PACKAGE_ID)));
            program.pointToRevision(revision.getId());
        });
        return code;
    }

    private long member(long points) {
        long id = ThreadLocalRandom.current().nextLong(100_000_000L, 900_000_000L);
        transaction.executeWithoutResult(status -> {
            LoyaltyWallet wallet = LoyaltyWallet.open(id);
            wallet.earn(BigInteger.valueOf(points));
            wallets.saveAndFlush(wallet);
        });
        return id;
    }

    private LoyaltyWallet wallet(long member) {
        return transaction.execute(status -> wallets.findById(member).orElseThrow());
    }

    private PromotionProgram program(String code) {
        return transaction.execute(status -> programs.findByCodeKey(code).orElseThrow());
    }

    private BookingInvoice invoice(String bookingCode) {
        return transaction.execute(status -> invoices.findByBookingId(bookings.findByBookingCode(bookingCode).orElseThrow().getId()).orElseThrow());
    }
}
