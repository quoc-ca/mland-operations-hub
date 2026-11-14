package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.CreateBookingGroupRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.WorkshopCartQuoteRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.WorkshopCartQuoteResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 003.1 T092: quoting a multi-ticket cart and confirming it into one booking group.
 * Loyalty is a mock: it answers the cart's programme discount, points and covered tickets, and 003.1 spreads them.
 * T105: every cart, Guest included, asks for the automatic workshop discount unless a Member names a voucher.
 */
class WorkshopCartBookingTest {

    /** The 12-minute group hold, relative to now so the Loyalty accept deadline is always still open. */
    private static final Instant HOLD_EXPIRY = Instant.now().plus(java.time.Duration.ofMinutes(12));

    private WorkshopOperationsAdapter operations;
    private WorkshopBookingAuthorizationService authorization;
    private WorkshopCartQuoteRepository quotes;
    private BookingGroupRepository groups;
    private BookingGroupLineRepository lines;
    private TicketBookingRepository tickets;
    private TicketPriceSnapshotRepository snapshots;
    private WorkshopCartQuoteService quoteService;
    private LoyaltyFacade loyalty;
    private GroupConfirmationService groupConfirmation;
    private BookingGroupCreationService creationService;
    private final List<WorkshopCartQuote> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        operations = mock(WorkshopOperationsAdapter.class);
        authorization = mock(WorkshopBookingAuthorizationService.class);
        quotes = mock(WorkshopCartQuoteRepository.class);
        groups = mock(BookingGroupRepository.class);
        lines = mock(BookingGroupLineRepository.class);
        tickets = mock(TicketBookingRepository.class);
        snapshots = mock(TicketPriceSnapshotRepository.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        when(quotes.save(any())).thenAnswer(invocation -> {
            WorkshopCartQuote quote = invocation.getArgument(0);
            if (quote.getCreatedAt() == null) {
                quote.setCreatedAt(Instant.now());
                quote.setExpiresAt(quote.getCreatedAt().plus(WorkshopCartQuote.QUOTE_TTL));
                quote.setQuoteState(CartQuoteState.OPEN);
            }
            saved.removeIf(existing -> existing.getQuoteRef().equals(quote.getQuoteRef()));
            saved.add(quote);
            return quote;
        });
        when(groups.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(lines.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tickets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        WorkshopCartPricingService pricing = new WorkshopCartPricingService(operations);
        loyalty = mock(LoyaltyFacade.class);
        // A Member cart previews even with nothing selected, so the quote can report the point limits (FR-034).
        when(loyalty.previewBenefits(any(), any())).thenReturn(preview(0, 0, 0, 0, List.of(), 0, 0, List.of()));
        WorkshopCartBenefitService benefits = new WorkshopCartBenefitService(loyalty);
        quoteService = new WorkshopCartQuoteService(authorization, pricing, benefits, quotes, mapper);
        groupConfirmation = mock(GroupConfirmationService.class);
        creationService = new BookingGroupCreationService(authorization, pricing, operations, quotes, groups, lines,
                tickets, snapshots, mock(BookingIdempotencyService.class), benefits, groupConfirmation, mapper);
    }

    // ---- quoting ----

    @Test
    void quotesEveryTicketOfEveryLineAndSumsOneGroupCollection() {
        UUID clay = publishedLine("7", 203_000, 1, 6, 1, "Clay Workshop");
        UUID wax = publishedLine("9", 500_000, 1, 4, 1, "Wax Casting");

        WorkshopCartQuoteResponse quote = quoteService.quote(null, cart(
                line(7L, clay, 2), line(9L, wax, 1)));

        assertThat(quote.lines()).hasSize(2);
        assertThat(quote.lines().get(0).tickets()).extracting(WorkshopCartQuoteResponse.QuotedTicket::finalPriceVnd)
                .containsExactly(203_000L, 203_000L);
        // min(final, 200,000) per ticket: 200,000 × 3 = 600,000 in one payment, 3,000 + 3,000 left at checkout.
        assertThat(quote.groupCollectionVnd()).isEqualTo(600_000);
        assertThat(quote.grossVnd()).isEqualTo(906_000);
        assertThat(quote.finalTotalVnd()).isEqualTo(906_000);
        assertThat(quote.remainingAtCheckoutVnd()).isEqualTo(306_000);
        assertThat(quote.paymentMode()).isEqualTo(GroupPaymentMode.VNPAY.name());
        assertThat(quote.lines().get(0).tickets()).extracting(WorkshopCartQuoteResponse.QuotedTicket::collectionKind)
                .containsOnly(TicketCollectionKind.DEPOSIT.name());
    }

    @Test
    void aGuestCartNeedsNoAuthenticationAndHasNoBuyer() {
        UUID session = publishedLine("7", 150_000, 1, 6, 1, "Clay Workshop");

        quoteService.quote(null, cart(line(7L, session, 1)));

        verifyNoInteractions(authorization);
        assertThat(saved.get(0).getBuyerMemberAccountId()).isNull();
        assertThat(saved.get(0).getVoucherDiscountVnd()).isZero();
        assertThat(saved.get(0).getPointsUsed()).isZero();
    }

    @Test
    void aMemberCartRecordsTheBuyer() {
        UUID session = publishedLine("7", 150_000, 1, 6, 1, "Clay Workshop");
        when(authorization.requireMemberOwnership("Bearer token", null))
                .thenReturn(new AccountSummary(42L, ActorRole.MEMBER, ActorStatus.ACTIVE, null, null, null));

        quoteService.quote("Bearer token", cart(line(7L, session, 1)));

        assertThat(saved.get(0).getBuyerMemberAccountId()).isEqualTo(42L);
    }

    @Test
    void aMemberCartReportsThePointLimitsEvenWhenNothingIsSelected() {
        UUID session = publishedLine("7", 150_000, 1, 6, 1, "Clay Workshop");
        givenMember(42L);
        givenLimits(1_400, 100, 100, List.of());

        WorkshopCartQuoteResponse quote = quoteService.quote("Bearer token", cart(line(7L, session, 1)));

        assertThat(quote.maxPoints()).isEqualTo(1_400);
        assertThat(quote.minRedeemPoints()).isEqualTo(100);
        assertThat(quote.pointValueVnd()).isEqualTo(100);
        assertThat(quote.pointsUsed()).isZero();
        assertThat(quote.voucherDiscountVnd()).isZero();
        // The read-only preview holds nothing, so the quote keeps an empty benefit snapshot.
        assertThat(saved.get(0).getBenefitSelectionJson()).isNull();
        assertThat(saved.get(0).getTermsDigest()).isNull();
        verify(loyalty, times(1)).previewBenefits(any(), any());
        verify(loyalty, never()).reserveAcceptedTransaction(any(), any(), anyString());
    }

    @Test
    void aRefusedPointAmountComesBackWithTheReasonAndTheCap() {
        UUID session = publishedLine("7", 150_000, 1, 6, 1, "Clay Workshop");
        givenMember(42L);
        // 006 refuses 5,000 points because the cart only allows 1,400; it applies nothing and says why.
        givenLimits(1_400, 100, 100, List.of("Points must be between 100 and 1400"));

        WorkshopCartQuoteResponse quote = quoteService.quote("Bearer token",
                cartWithPoints("5000", line(7L, session, 1)));

        assertThat(quote.pointsUsed()).isZero();
        assertThat(quote.pointDiscountVnd()).isZero();
        assertThat(quote.maxPoints()).isEqualTo(1_400);
        assertThat(quote.minRedeemPoints()).isEqualTo(100);
        assertThat(quote.reasons()).containsExactly("Points must be between 100 and 1400");
        assertThat(quote.finalTotalVnd()).isEqualTo(150_000);
    }

    @Test
    void aGuestCartHasNoPointLimitsAndNoReasons() {
        UUID session = publishedLine("7", 150_000, 1, 6, 1, "Clay Workshop");
        // Whatever the policy says, a Guest has no wallet, so the quote shows no point limits.
        givenLimits(1_400, 100, 100, List.of());

        WorkshopCartQuoteResponse quote = quoteService.quote(null, cart(line(7L, session, 1)));

        assertThat(quote.maxPoints()).isZero();
        assertThat(quote.minRedeemPoints()).isZero();
        assertThat(quote.pointValueVnd()).isZero();
        assertThat(quote.reasons()).isEmpty();
        assertThat(quote.appliedDiscount()).isEqualTo("NONE");
    }

    // ---- T105: the automatic workshop discount ----

    @Test
    void aGuestCartGetsTheAutomaticDiscountSpreadPerTicketAndReservedWithoutAMember() {
        UUID clay = publishedLine("7", 300_000, 1, 6, 1, "Clay Workshop");
        // 006 picks the largest automatic discount for the cart: 100,000 over both tickets.
        givenPreview(100_000, 0, 0, 0, List.of(0, 1));

        WorkshopCartQuoteResponse quote = quoteService.quote(null, cart(line(7L, clay, 2)));

        ArgumentCaptor<TrustedSourceContext> context = ArgumentCaptor.forClass(TrustedSourceContext.class);
        ArgumentCaptor<BenefitSelection> selection = ArgumentCaptor.forClass(BenefitSelection.class);
        verify(loyalty).previewBenefits(context.capture(), selection.capture());
        assertThat(context.getValue().isGuest()).isTrue();
        assertThat(selection.getValue()).isEqualTo(new BenefitSelection(SelectionChoice.AUTO, null, BigInteger.ZERO));
        assertThat(quote.appliedDiscount()).isEqualTo("AUTO");
        assertThat(quote.voucherDiscountVnd()).isEqualTo(100_000);
        assertThat(quote.lines().get(0).tickets()).extracting(WorkshopCartQuoteResponse.QuotedTicket::voucherDiscountVnd)
                .containsExactly(50_000L, 50_000L);
        assertThat(quote.finalTotalVnd()).isEqualTo(500_000);
        assertThat(quote.maxPoints()).isZero();
        assertThat(saved.get(0).getBuyerMemberAccountId()).isNull();
        assertThat(saved.get(0).getTermsDigest()).isEqualTo("digest-1");

        openQuote(quote.quoteRef());
        when(operations.reserveGroupCapacity(anyString(), any(), anyString(), anyString()))
                .thenReturn(GroupCapacityOutcome.accepted(List.of(List.of(UUID.randomUUID())), HOLD_EXPIRY));
        when(loyalty.reserveAcceptedTransaction(any(), any(), anyString())).thenReturn(acceptedRef(100_000, 0));
        var response = creationService.create(new CreateBookingGroupRequest(quote.quoteRef(),
                "guest@example.com", "0901234567"), null, "corr-auto", null);

        // One snapshot without a Member for the group (006 D-014), the same AUTO choice and the quoted digest.
        ArgumentCaptor<AcceptedSourceContext> accepted = ArgumentCaptor.forClass(AcceptedSourceContext.class);
        ArgumentCaptor<BenefitSelection> acceptedSelection = ArgumentCaptor.forClass(BenefitSelection.class);
        verify(loyalty).reserveAcceptedTransaction(accepted.capture(), acceptedSelection.capture(), anyString());
        assertThat(accepted.getValue().trusted().isGuest()).isTrue();
        assertThat(accepted.getValue().memberOrigin()).isTrue();
        assertThat(accepted.getValue().termsDigest()).isEqualTo("digest-1");
        assertThat(acceptedSelection.getValue().choice()).isEqualTo(SelectionChoice.AUTO);
        assertThat(response.tickets()).extracting(dto -> dto.finalPriceVnd()).containsExactly(250_000L, 250_000L);
    }

    @Test
    void aMemberVoucherReplacesTheAutomaticDiscount() {
        UUID clay = publishedLine("7", 300_000, 1, 6, 1, "Clay Workshop");
        givenMember(42L);
        givenPreview(60_000, 0, 0, 0, List.of(0));

        WorkshopCartQuoteResponse quote = quoteService.quote("Bearer token", cartWithVoucher(line(7L, clay, 1)));

        // 006 FR-015: the voucher is sent alone, so 006 never adds the automatic discount to it.
        ArgumentCaptor<BenefitSelection> selection = ArgumentCaptor.forClass(BenefitSelection.class);
        verify(loyalty).previewBenefits(any(), selection.capture());
        assertThat(selection.getValue().choice()).isEqualTo(SelectionChoice.VOUCHER);
        assertThat(selection.getValue().voucherCode()).isEqualTo("SUMMER");
        assertThat(quote.appliedDiscount()).isEqualTo("VOUCHER");
        assertThat(quote.voucherDiscountVnd()).isEqualTo(60_000);
        assertThat(quote.finalTotalVnd()).isEqualTo(240_000);
    }

    @Test
    void aMemberWhoChoosesNoVoucherGetsTheAutomaticDiscountAndCanStillSpendPoints() {
        UUID clay = publishedLine("7", 300_000, 1, 6, 1, "Clay Workshop");
        givenMember(42L);

        quoteService.quote("Bearer token", cartWithPoints("100", line(7L, clay, 1)));

        ArgumentCaptor<BenefitSelection> selection = ArgumentCaptor.forClass(BenefitSelection.class);
        verify(loyalty).previewBenefits(any(), selection.capture());
        assertThat(selection.getValue()).isEqualTo(
                new BenefitSelection(SelectionChoice.AUTO, null, BigInteger.valueOf(100)));
    }

    @Test
    void aCartBelowTheAutomaticDiscountMinimumKeepsThePlainPriceAndReservesNothing() {
        UUID clay = publishedLine("7", 150_000, 1, 6, 1, "Clay Workshop");
        // 006 finds no automatic discount whose minimum fee the cart meets: nothing off, only that reason.
        when(loyalty.previewBenefits(any(), any())).thenReturn(new BenefitPreview(
                BigInteger.valueOf(150_000), BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(150_000),
                BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO,
                BigInteger.valueOf(150_000), BigInteger.valueOf(150_000), null, null, null, 3L, true,
                List.of("No automatic workshop discount applies"), "digest-none",
                List.of(BenefitReason.of(BenefitReasonCode.WORKSHOP_NO_AUTO_DISCOUNT_APPLIES)), List.of()));

        WorkshopCartQuoteResponse quote = quoteService.quote(null, cart(line(7L, clay, 1)));

        assertThat(quote.appliedDiscount()).isEqualTo("NONE");
        assertThat(quote.voucherDiscountVnd()).isZero();
        assertThat(quote.finalTotalVnd()).isEqualTo(150_000);
        // appliedDiscount already says it; the reasons stay for vouchers and points.
        assertThat(quote.reasons()).isEmpty();
        assertThat(saved.get(0).getBenefitSelectionJson()).isNull();
        assertThat(saved.get(0).getTermsDigest()).isNull();

        openQuote(quote.quoteRef());
        when(operations.reserveGroupCapacity(anyString(), any(), anyString(), anyString()))
                .thenReturn(GroupCapacityOutcome.accepted(List.of(List.of(UUID.randomUUID())), HOLD_EXPIRY));
        creationService.create(new CreateBookingGroupRequest(quote.quoteRef(), "guest@example.com", "0901234567"),
                null, "corr-min", null);
        verify(loyalty, never()).reserveAcceptedTransaction(any(), any(), anyString());
    }

    @Test
    void theAutomaticDiscountIsSpreadByLargestRemainderAcrossTicketsOfDifferentPrices() {
        UUID clay = publishedLine("7", 300_000, 1, 6, 1, "Clay Workshop");
        UUID wax = publishedLine("9", 500_000, 1, 4, 1, "Wax Casting");
        givenPreview(100_000, 0, 0, 0, List.of(0, 1, 2));

        WorkshopCartQuoteResponse quote = quoteService.quote(null, cart(line(7L, clay, 2), line(9L, wax, 1)));

        // 100,000 × 300/1,100 = 27,272.72… twice and × 500/1,100 = 45,454.54…: floors leave 2 VND, which go to the
        // two largest remainders, the Clay tickets (FR-033).
        assertThat(quote.lines().get(0).tickets()).extracting(WorkshopCartQuoteResponse.QuotedTicket::voucherDiscountVnd)
                .containsExactly(27_273L, 27_273L);
        assertThat(quote.lines().get(1).tickets().get(0).voucherDiscountVnd()).isEqualTo(45_454L);
        assertThat(quote.finalTotalVnd()).isEqualTo(1_000_000);
        assertThat(quote.appliedDiscount()).isEqualTo("AUTO");
    }

    @Test
    void aGuestCartAskingForAVoucherOrPointsIsRefused() {
        UUID session = publishedLine("7", 150_000, 1, 6, 1, "Clay Workshop");
        WorkshopCartQuoteRequest withVoucher = new WorkshopCartQuoteRequest(List.of(line(7L, session, 1)),
                new WorkshopCartQuoteRequest.BenefitSelectionRequest(
                        WorkshopCartQuoteRequest.BenefitSelectionRequest.Choice.VOUCHER, "SUMMER", "0"));
        WorkshopCartQuoteRequest withPoints = new WorkshopCartQuoteRequest(List.of(line(7L, session, 1)),
                new WorkshopCartQuoteRequest.BenefitSelectionRequest(
                        WorkshopCartQuoteRequest.BenefitSelectionRequest.Choice.NONE, null, "500"));

        // FE-06 benefits belong to a Member, so a Guest cart has none to apply.
        for (WorkshopCartQuoteRequest request : List.of(withVoucher, withPoints)) {
            assertThatThrownBy(() -> quoteService.quote(null, request))
                    .isInstanceOf(BookingCreationRejectedException.class)
                    .hasFieldOrPropertyWithValue("code", WorkshopCartQuoteService.BENEFITS_NEED_A_MEMBER);
        }
        verifyNoInteractions(quotes);
        verifyNoInteractions(loyalty);
    }

    @Test
    void aMemberVoucherDiscountsOnlyTheTicketsLoyaltySaysItCovers() {
        UUID clay = publishedLine("7", 300_000, 1, 6, 1, "Clay Workshop");
        UUID wax = publishedLine("9", 500_000, 1, 4, 1, "Wax Casting");
        givenMember(42L);
        // 006 answers for the cart: 100,000 off, and only the two Clay tickets are inside the voucher scope.
        givenPreview(100_000, 0, 0, 0, List.of(0, 1));

        WorkshopCartQuoteResponse quote = quoteService.quote("Bearer token",
                cartWithVoucher(line(7L, clay, 2), line(9L, wax, 1)));

        assertThat(quote.voucherDiscountVnd()).isEqualTo(100_000);
        assertThat(quote.lines().get(0).tickets()).extracting(WorkshopCartQuoteResponse.QuotedTicket::voucherDiscountVnd)
                .containsExactly(50_000L, 50_000L);
        assertThat(quote.lines().get(1).tickets().get(0).voucherDiscountVnd()).isZero();
        assertThat(quote.finalTotalVnd()).isEqualTo(1_000_000);
        assertThat(saved.get(0).getTermsDigest()).isEqualTo("digest-1");
    }

    @Test
    void theCartHandedToLoyaltyIsOneTicketPerPersonInCartOrder() {
        UUID clay = publishedLine("7", 300_000, 1, 6, 1, "Clay Workshop");
        UUID wax = publishedLine("9", 500_000, 1, 4, 1, "Wax Casting");
        givenMember(42L);
        givenPreview(0, 0, 0, 0, List.of());

        quoteService.quote("Bearer token", cartWithVoucher(line(7L, clay, 2), line(9L, wax, 1)));

        ArgumentCaptor<TrustedSourceContext> context = ArgumentCaptor.forClass(TrustedSourceContext.class);
        verify(loyalty).previewBenefits(context.capture(), any());
        WorkshopFact fact = context.getValue().workshop();
        assertThat(fact.isCart()).isTrue();
        assertThat(fact.tickets()).extracting(WorkshopTicketFact::ticketRef)
                .containsExactly("L0-T1", "L0-T2", "L1-T1");
        assertThat(fact.tickets()).extracting(ticket -> ticket.ticketPrice().longValueExact())
                .containsExactly(300_000L, 300_000L, 500_000L);
        assertThat(fact.participantCount()).isEqualTo(3);
        assertThat(fact.grossPackageAmount().longValueExact()).isEqualTo(1_100_000);
    }

    @Test
    void pointsFollowThePostVoucherPriceOfEachTicket() {
        UUID clay = publishedLine("7", 300_000, 1, 6, 1, "Clay Workshop");
        givenMember(42L);
        // 100 points at 100 VND, no voucher: 10,000 VND spread over two equal tickets.
        givenPreview(0, 100, 100, 10_000, List.of());

        WorkshopCartQuoteResponse quote = quoteService.quote("Bearer token", cartWithPoints("100", line(7L, clay, 2)));

        assertThat(quote.pointsUsed()).isEqualTo(100);
        assertThat(quote.lines().get(0).tickets()).extracting(WorkshopCartQuoteResponse.QuotedTicket::pointsUsed)
                .containsExactly(50L, 50L);
        assertThat(quote.finalTotalVnd()).isEqualTo(590_000);
    }

    @Test
    void confirmingAMemberCartHoldsOneLoyaltyReservationForTheWholeGroup() {
        UUID clay = publishedLine("7", 300_000, 1, 6, 1, "Clay Workshop");
        givenMember(42L);
        givenPreview(100_000, 0, 0, 0, List.of(0, 1));
        WorkshopCartQuoteResponse quote = quoteService.quote("Bearer token", cartWithVoucher(line(7L, clay, 2)));
        openQuote(quote.quoteRef());
        when(operations.reserveGroupCapacity(anyString(), any(), anyString(), anyString()))
                .thenReturn(GroupCapacityOutcome.accepted(List.of(List.of(UUID.randomUUID())), HOLD_EXPIRY));
        when(loyalty.reserveAcceptedTransaction(any(), any(), anyString())).thenReturn(acceptedRef(100_000, 0));

        var response = creationService.create(new CreateBookingGroupRequest(quote.quoteRef(),
                "buyer@example.com", "0901234567"), 42L, "corr-9", null);

        ArgumentCaptor<AcceptedSourceContext> accepted = ArgumentCaptor.forClass(AcceptedSourceContext.class);
        verify(loyalty, times(1)).reserveAcceptedTransaction(accepted.capture(), any(), anyString());
        assertThat(accepted.getValue().sourceRef()).isEqualTo(response.groupCode());
        assertThat(accepted.getValue().quoteRef()).isEqualTo(quote.quoteRef());
        assertThat(accepted.getValue().termsDigest()).isEqualTo("digest-1");
        assertThat(accepted.getValue().trusted().workshop().tickets()).hasSize(2);
        assertThat(response.tickets()).extracting(dto -> dto.finalPriceVnd())
                .containsExactly(250_000L, 250_000L);
    }

    @Test
    void aBenefitThatMovedSinceTheQuoteIsRefusedInsteadOfBooked() {
        UUID clay = publishedLine("7", 300_000, 1, 6, 1, "Clay Workshop");
        givenMember(42L);
        givenPreview(100_000, 0, 0, 0, List.of(0, 1));
        WorkshopCartQuoteResponse quote = quoteService.quote("Bearer token", cartWithVoucher(line(7L, clay, 2)));
        openQuote(quote.quoteRef());
        when(operations.reserveGroupCapacity(anyString(), any(), anyString(), anyString()))
                .thenReturn(GroupCapacityOutcome.accepted(List.of(List.of(UUID.randomUUID())), HOLD_EXPIRY));
        when(loyalty.reserveAcceptedTransaction(any(), any(), anyString())).thenReturn(acceptedRef(60_000, 0));

        assertThatThrownBy(() -> creationService.create(new CreateBookingGroupRequest(quote.quoteRef(),
                "buyer@example.com", "0901234567"), 42L, "corr-10", null))
                .isInstanceOf(BookingCreationRejectedException.class)
                .hasFieldOrPropertyWithValue("code", "QUOTE_CHANGED");
    }

    @Test
    void aCartWithNoBenefitIsNeverReserved() {
        UUID clay = publishedLine("7", 300_000, 1, 6, 1, "Clay Workshop");
        WorkshopCartQuoteResponse quote = quoteService.quote(null, cart(line(7L, clay, 1)));
        openQuote(quote.quoteRef());
        when(operations.reserveGroupCapacity(anyString(), any(), anyString(), anyString()))
                .thenReturn(GroupCapacityOutcome.accepted(List.of(List.of(UUID.randomUUID())), HOLD_EXPIRY));

        creationService.create(new CreateBookingGroupRequest(quote.quoteRef(), "buyer@example.com", "0901234567"),
                null, "corr-11", null);

        // The quote asked 006 for an automatic discount and got none, so confirmation holds nothing.
        verify(loyalty, never()).reserveAcceptedTransaction(any(), any(), anyString());
    }

    @Test
    void anExplicitlyEmptySelectionStillQuotes() {
        UUID session = publishedLine("7", 150_000, 1, 6, 1, "Clay Workshop");
        WorkshopCartQuoteRequest request = new WorkshopCartQuoteRequest(List.of(line(7L, session, 1)),
                new WorkshopCartQuoteRequest.BenefitSelectionRequest(
                        WorkshopCartQuoteRequest.BenefitSelectionRequest.Choice.NONE, null, "0"));

        assertThat(quoteService.quote(null, request).finalTotalVnd()).isEqualTo(150_000);
    }

    @Test
    void aLineThatNoLongerHasRoomForEveryoneIsRefusedBeforeAnythingIsHeld() {
        UUID session = UUID.randomUUID();
        when(operations.getPublishedPackageSelection("7"))
                .thenReturn(new PublishedPackageSelectionSnapshot("7", 150_000, 1, 6, 1, true, 1, "Clay Workshop"));
        when(operations.getSaleEligibility(session)).thenReturn(new SaleEligibilitySnapshot(session, true, "ON_SALE",
                5L, Instant.parse("2026-11-20T02:00:00Z"), Instant.parse("2026-11-20T05:00:00Z"), 1,
                java.time.LocalDate.parse("2026-11-20")));

        assertThatThrownBy(() -> quoteService.quote(null, cart(line(7L, session, 3))))
                .isInstanceOfSatisfying(CartHoldRejectedException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("CART_LINE_UNAVAILABLE");
                    assertThat(e.getFailingLines()).containsExactly(0);
                });
    }

    @Test
    void aLineThatDoesNotMatchThePackageRequirementsIsRejected() {
        UUID session = publishedLine("7", 150_000, 2, 6, 1, "Clay Workshop");

        assertThatThrownBy(() -> quoteService.quote(null, cart(line(7L, session, 1))))
                .isInstanceOf(BookingValidationException.class);
    }

    @Test
    void aFreeCartIsConfirmedInternally() {
        UUID session = publishedLine("7", 0, 1, 6, 1, "Open Day");

        WorkshopCartQuoteResponse quote = quoteService.quote(null, cart(line(7L, session, 2)));

        assertThat(quote.groupCollectionVnd()).isZero();
        assertThat(quote.paymentMode()).isEqualTo(GroupPaymentMode.INTERNAL_FREE.name());
    }

    @Test
    void aCartUnderTheVnpayFloorIsDeferredToCheckout() {
        UUID session = publishedLine("7", 3_000, 1, 6, 1, "Mini Workshop");

        WorkshopCartQuoteResponse quote = quoteService.quote(null, cart(line(7L, session, 1)));

        assertThat(quote.paymentMode()).isEqualTo(GroupPaymentMode.INTERNAL_DEFERRED.name());
        assertThat(quote.groupCollectionVnd()).isZero();
        assertThat(quote.lines().get(0).tickets().get(0).remainingVnd()).isEqualTo(3_000);
    }

    // ---- confirming the cart into a group ----

    @Test
    void holdsEveryLineThenStoresOneTicketAndOneSnapshotPerPerson() {
        UUID clay = publishedLine("7", 203_000, 1, 6, 1, "Clay Workshop");
        UUID wax = publishedLine("9", 500_000, 1, 4, 1, "Wax Casting");
        WorkshopCartQuoteResponse quote = quoteService.quote(null, cart(line(7L, clay, 2), line(9L, wax, 1)));
        openQuote(quote.quoteRef());
        UUID clayHold = UUID.randomUUID();
        UUID waxHold = UUID.randomUUID();
        when(operations.reserveGroupCapacity(anyString(), any(), anyString(), eq("corr-1")))
                .thenReturn(GroupCapacityOutcome.accepted(List.of(List.of(clayHold), List.of(waxHold)), HOLD_EXPIRY));

        var response = creationService.create(new CreateBookingGroupRequest(quote.quoteRef(),
                "buyer@example.com", "0901234567"), null, "corr-1", null);

        assertThat(response.groupStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT.name());
        assertThat(response.groupCollectionVnd()).isEqualTo(600_000);
        assertThat(response.holdExpiresAt()).isEqualTo(HOLD_EXPIRY);
        // FR-032: the customer's deadline is 2 minutes before the hold ends.
        assertThat(response.paymentDeadlineAt()).isEqualTo(HOLD_EXPIRY.minusSeconds(120));
        assertThat(response.tickets()).hasSize(3);
        assertThat(response.tickets()).extracting(dto -> dto.lineIndex() + "/" + dto.participantOrdinal())
                .containsExactly("0/1", "0/2", "1/1");
        assertThat(response.tickets()).extracting(dto -> dto.collectedAtBookingVnd())
                .containsExactly(200_000L, 200_000L, 200_000L);
        assertThat(response.tickets()).extracting(dto -> dto.remainingVnd())
                .containsExactly(3_000L, 3_000L, 300_000L);
        verify(tickets, times(3)).save(any());
        verify(snapshots, times(3)).save(any());

        ArgumentCaptor<BookingGroupLine> stored = ArgumentCaptor.forClass(BookingGroupLine.class);
        verify(lines, times(2)).save(stored.capture());
        assertThat(stored.getAllValues()).extracting(BookingGroupLine::getTicketCount).containsExactly(2, 1);
        assertThat(stored.getAllValues().get(0).getSessions()).singleElement()
                .satisfies(session -> {
                    assertThat(session.getHoldReference()).isEqualTo(clayHold.toString());
                    assertThat(session.getHoldStatus()).isEqualTo(LineHoldStatus.RESERVED);
                });
        assertThat(saved.get(0).getQuoteState()).isEqualTo(CartQuoteState.ACCEPTED);
    }

    @Test
    void aRefusedHoldNamesTheFailingLinesAndWritesNothing() {
        UUID clay = publishedLine("7", 203_000, 1, 6, 1, "Clay Workshop");
        UUID wax = publishedLine("9", 500_000, 1, 4, 1, "Wax Casting");
        WorkshopCartQuoteResponse quote = quoteService.quote(null, cart(line(7L, clay, 2), line(9L, wax, 1)));
        openQuote(quote.quoteRef());
        when(operations.reserveGroupCapacity(anyString(), any(), anyString(), anyString()))
                .thenReturn(GroupCapacityOutcome.rejected(RejectionReason.INSUFFICIENT_CAPACITY, List.of(1)));

        assertThatThrownBy(() -> creationService.create(new CreateBookingGroupRequest(quote.quoteRef(),
                "buyer@example.com", "0901234567"), null, "corr-2", null))
                .isInstanceOf(CartHoldRejectedException.class)
                .hasFieldOrPropertyWithValue("code", "INSUFFICIENT_CAPACITY")
                .hasFieldOrPropertyWithValue("failingLines", List.of(1));

        verifyNoInteractions(groups);
        verifyNoInteractions(tickets);
        verifyNoInteractions(snapshots);
        assertThat(saved.get(0).getQuoteState()).isEqualTo(CartQuoteState.OPEN);
    }

    @Test
    void aQuoteIsConfirmedOnlyOnce() {
        UUID session = publishedLine("7", 203_000, 1, 6, 1, "Clay Workshop");
        WorkshopCartQuoteResponse quote = quoteService.quote(null, cart(line(7L, session, 1)));
        WorkshopCartQuote stored = saved.get(0);
        stored.setQuoteState(CartQuoteState.ACCEPTED);
        when(quotes.findForUpdate(quote.quoteRef())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> creationService.create(new CreateBookingGroupRequest(quote.quoteRef(),
                "buyer@example.com", "0901234567"), null, "corr-3", null))
                .isInstanceOf(BookingCreationRejectedException.class)
                .hasFieldOrPropertyWithValue("code", "QUOTE_ALREADY_ACCEPTED");
    }

    @Test
    void anExpiredQuoteIsRefused() {
        UUID session = publishedLine("7", 203_000, 1, 6, 1, "Clay Workshop");
        WorkshopCartQuoteResponse quote = quoteService.quote(null, cart(line(7L, session, 1)));
        WorkshopCartQuote stored = saved.get(0);
        stored.setExpiresAt(Instant.parse("2020-01-01T00:00:00Z"));
        when(quotes.findForUpdate(quote.quoteRef())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> creationService.create(new CreateBookingGroupRequest(quote.quoteRef(),
                "buyer@example.com", "0901234567"), null, "corr-4", null))
                .isInstanceOf(BookingCreationRejectedException.class)
                .hasFieldOrPropertyWithValue("code", "QUOTE_EXPIRED");
    }

    @Test
    void aPriceThatMovedSinceTheQuoteIsRefusedInsteadOfBooked() {
        UUID session = publishedLine("7", 203_000, 1, 6, 1, "Clay Workshop");
        WorkshopCartQuoteResponse quote = quoteService.quote(null, cart(line(7L, session, 1)));
        openQuote(quote.quoteRef());
        when(operations.getPublishedPackageSelection("7"))
                .thenReturn(new PublishedPackageSelectionSnapshot("7", 250_000, 1, 6, 1, true, 1, "Clay Workshop"));

        assertThatThrownBy(() -> creationService.create(new CreateBookingGroupRequest(quote.quoteRef(),
                "buyer@example.com", "0901234567"), null, "corr-5", null))
                .isInstanceOf(BookingCreationRejectedException.class)
                .hasFieldOrPropertyWithValue("code", "QUOTE_CHANGED");
        verifyNoInteractions(groups);
    }

    @Test
    void anotherMembersQuoteIsNotFound() {
        UUID session = publishedLine("7", 203_000, 1, 6, 1, "Clay Workshop");
        when(authorization.requireMemberOwnership("Bearer token", null))
                .thenReturn(new AccountSummary(42L, ActorRole.MEMBER, ActorStatus.ACTIVE, null, null, null));
        WorkshopCartQuoteResponse quote = quoteService.quote("Bearer token", cart(line(7L, session, 1)));
        openQuote(quote.quoteRef());

        assertThatThrownBy(() -> creationService.create(new CreateBookingGroupRequest(quote.quoteRef(),
                "buyer@example.com", "0901234567"), 99L, "corr-6", null))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void aFreeCartBecomesAnInternallyConfirmedGroupThatCollectsNothing() {
        UUID session = publishedLine("7", 0, 1, 6, 1, "Open Day");
        WorkshopCartQuoteResponse quote = quoteService.quote(null, cart(line(7L, session, 2)));
        openQuote(quote.quoteRef());
        when(operations.reserveGroupCapacity(anyString(), any(), anyString(), anyString()))
                .thenReturn(GroupCapacityOutcome.accepted(List.of(List.of(UUID.randomUUID())), HOLD_EXPIRY));

        var response = creationService.create(new CreateBookingGroupRequest(quote.quoteRef(),
                "buyer@example.com", "0901234567"), null, "corr-7", null);

        assertThat(response.paymentMode()).isEqualTo(GroupPaymentMode.INTERNAL_FREE.name());
        assertThat(response.groupCollectionVnd()).isZero();
        assertThat(response.tickets()).hasSize(2)
                .allSatisfy(ticket -> assertThat(ticket.collectionKind())
                        .isEqualTo(TicketCollectionKind.FREE.name()));
        // FR-039: nothing to collect, so the group is confirmed here instead of waiting for a gateway.
        verify(groupConfirmation).confirmInternalGroup(eq(response.groupCode()), anyString(), eq("corr-7"));
    }

    @Test
    void aPayingGroupWaitsForItsGatewayPaymentBeforeAnythingIsConfirmed() {
        UUID session = publishedLine("7", 203_000, 1, 6, 1, "Clay Workshop");
        WorkshopCartQuoteResponse quote = quoteService.quote(null, cart(line(7L, session, 1)));
        openQuote(quote.quoteRef());
        when(operations.reserveGroupCapacity(anyString(), any(), anyString(), anyString()))
                .thenReturn(GroupCapacityOutcome.accepted(List.of(List.of(UUID.randomUUID())), HOLD_EXPIRY));

        var response = creationService.create(new CreateBookingGroupRequest(quote.quoteRef(),
                "buyer@example.com", "0901234567"), null, "corr-8", null);

        assertThat(response.groupStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT.name());
        verifyNoInteractions(groupConfirmation);
    }

    // ---- helpers ----

    private void openQuote(String quoteRef) {
        WorkshopCartQuote stored = saved.stream().filter(q -> q.getQuoteRef().equals(quoteRef)).findFirst()
                .orElseThrow();
        when(quotes.findForUpdate(quoteRef)).thenReturn(Optional.of(stored));
    }

    private void givenMember(long accountId) {
        when(authorization.requireMemberOwnership("Bearer token", null))
                .thenReturn(new AccountSummary(accountId, ActorRole.MEMBER, ActorStatus.ACTIVE, null, null, null));
    }

    /** The answer 006 gives for a whole cart, including which tickets the voucher covers. */
    private void givenPreview(long discount, long pointsUsed, long pointValue, long pointDiscount,
                              List<Integer> eligibleTickets) {
        when(loyalty.previewBenefits(any(), any())).thenReturn(
                preview(discount, pointsUsed, pointValue, pointDiscount, eligibleTickets, 0, 0, List.of()));
    }

    /** The point limits and reasons 006 reports for a cart that applied nothing (FR-034). */
    private void givenLimits(long maxPoints, long minRedeemPoints, long pointValue, List<String> reasons) {
        when(loyalty.previewBenefits(any(), any())).thenReturn(
                preview(0, 0, pointValue, 0, List.of(), maxPoints, minRedeemPoints, reasons));
    }

    private static BenefitPreview preview(long discount, long pointsUsed, long pointValue, long pointDiscount,
                                          List<Integer> eligibleTickets, long maxPoints, long minRedeemPoints,
                                          List<String> reasons) {
        return new BenefitPreview(
                BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(discount), BigInteger.ZERO,
                BigInteger.valueOf(maxPoints), BigInteger.valueOf(minRedeemPoints), BigInteger.valueOf(pointValue),
                BigInteger.valueOf(pointsUsed), BigInteger.valueOf(pointDiscount), BigInteger.ZERO, BigInteger.ZERO,
                null, null, null, 3L, true, reasons, "digest-1", List.of(), eligibleTickets);
    }

    private static AcceptedBenefitRef acceptedRef(long discount, long points) {
        return new AcceptedBenefitRef(1L, SourceKind.WORKSHOP, "GRP", BigInteger.ZERO,
                BigInteger.valueOf(discount), BigInteger.valueOf(points), BigInteger.ZERO, BigInteger.ZERO,
                BigInteger.ZERO, 3L, ComponentState.HELD, ComponentState.HELD, EarnState.PENDING);
    }

    private UUID publishedLine(String packageReference, long price, int min, int max, int requiredSessions,
                               String name) {
        UUID session = UUID.randomUUID();
        when(operations.getPublishedPackageSelection(packageReference)).thenReturn(
                new PublishedPackageSelectionSnapshot(packageReference, price, min, max, requiredSessions, true, 1,
                        name));
        when(operations.getSaleEligibility(session)).thenReturn(new SaleEligibilitySnapshot(session, true, "ON_SALE",
                5L, Instant.parse("2026-11-20T02:00:00Z"), Instant.parse("2026-11-20T05:00:00Z"), 20,
                java.time.LocalDate.parse("2026-11-20")));
        return session;
    }

    private static WorkshopCartQuoteRequest.Line line(long packageId, UUID session, int participants) {
        return new WorkshopCartQuoteRequest.Line(packageId, List.of(session.toString()), participants);
    }

    private static WorkshopCartQuoteRequest cart(WorkshopCartQuoteRequest.Line... lines) {
        return new WorkshopCartQuoteRequest(List.of(lines), null);
    }

    /** The same cart, with the buyer asking for a voucher so 006 is consulted. */
    private static WorkshopCartQuoteRequest cartWithVoucher(WorkshopCartQuoteRequest.Line... lines) {
        return new WorkshopCartQuoteRequest(List.of(lines), new WorkshopCartQuoteRequest.BenefitSelectionRequest(
                WorkshopCartQuoteRequest.BenefitSelectionRequest.Choice.VOUCHER, "SUMMER", "0"));
    }

    private static WorkshopCartQuoteRequest cartWithPoints(String points, WorkshopCartQuoteRequest.Line... lines) {
        return new WorkshopCartQuoteRequest(List.of(lines), new WorkshopCartQuoteRequest.BenefitSelectionRequest(
                WorkshopCartQuoteRequest.BenefitSelectionRequest.Choice.NONE, null, points));
    }
}
