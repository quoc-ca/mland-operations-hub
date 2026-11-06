package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingSummaryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.CreateBookingRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingCreationRejectedException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.MemberQuoteBookingAcceptanceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Persists Member quote acceptance on the isolated H2 test profile. */
@SpringBootTest
@ActiveProfiles("test")
class MemberQuoteBookingAcceptanceIT {
    private static final long MEMBER_ID = 19031L;
    private static final UUID SESSION_ONE = UUID.fromString("9e993555-6d1f-47ac-9a52-9a98fe9bcd01");
    private static final UUID SESSION_TWO = UUID.fromString("13dc4a5e-2651-47be-83de-1ba4db4cce02");

    @Autowired private MemberQuoteBookingAcceptanceService acceptance;
    @Autowired private MemberWorkshopBookingQuoteRepository quotes;
    @Autowired private WorkshopBookingRepository bookings;
    @Autowired private BookingSessionCommitmentRepository commitments;
    @Autowired private BookingCapacityHoldRepository holds;
    @Autowired private BookingInvoiceRepository invoices;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager entityManager;

    @MockBean private WorkshopOperationsAdapter operations;
    @MockBean private LoyaltyFacade loyalty;

    @Test
    void acceptsMemberQuoteAndPersistsCanonicalSessionsCapacityAndCeilingDeposit() throws Exception {
        String quoteRef = UUID.randomUUID().toString();
        saveQuote(quoteRef, Instant.now().plusSeconds(1800));
        entityManager.clear();
        MemberWorkshopBookingQuote quote = quotes.findByQuoteRefAndMemberAccountId(quoteRef, MEMBER_ID).orElseThrow();
        assertThat(quote.getSessionFactsJson()).contains(SESSION_ONE.toString(), SESSION_TWO.toString());
        assertThat(mapper.readTree(quote.getSessionFactsJson()).isTextual()).isTrue();
        configureAuthoritativeFacts();

        BookingSummaryResponse response = acceptance.accept(request(quoteRef), MEMBER_ID, "member-accept-it", null);

        WorkshopBooking booking = bookings.findByBookingCode(response.getBookingCode()).orElseThrow();
        assertThat(booking.getMemberAccountId()).isEqualTo(MEMBER_ID);
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(response.getSessions()).extracting(session -> session.getSessionReference())
                .containsExactly(SESSION_ONE, SESSION_TWO);

        MemberWorkshopBookingQuote persistedQuote = quotes.findByQuoteRefAndMemberAccountId(quoteRef, MEMBER_ID).orElseThrow();
        assertThat(persistedQuote.getQuoteState()).isEqualTo("ACCEPTED");
        assertThat(persistedQuote.getAcceptedBookingCode()).isEqualTo(booking.getBookingCode());
        assertThat(commitments.findByBookingIdOrderByOrdinalAsc(booking.getId()))
                .extracting(BookingSessionCommitment::getSessionReference)
                .containsExactly(SESSION_ONE.toString(), SESSION_TWO.toString());
        assertThat(holds.findByBookingId(booking.getId())).hasSize(2)
                .allSatisfy(hold -> assertThat(hold.getHoldStatus()).isEqualTo("RESERVED"));
        BookingInvoice invoice = invoices.findByBookingId(booking.getId()).orElseThrow();
        assertThat(invoice.getPostPromotionAmount()).isEqualTo(101L);
        assertThat(invoice.getDepositAmount()).isEqualTo(51L);
        assertThat(invoice.getRemainingAmount()).isEqualTo(50L);
        verify(operations).reserveBookingCapacity(eq(booking.getBookingCode()), eq("15"),
                eq(List.of(SESSION_ONE, SESSION_TWO)), eq(1), startsWith("CREATE-"), eq("member-accept-it"));
        verify(loyalty).reserveAcceptedTransaction(any(AcceptedSourceContext.class), eq(BenefitSelection.none()),
                eq("create-" + booking.getBookingCode()));
    }

    @Test
    void expiredMemberQuoteCreatesNoBookingAndDoesNotReserveCapacityOrBenefits() throws Exception {
        String quoteRef = UUID.randomUUID().toString();
        saveQuote(quoteRef, Instant.now().minusSeconds(1));
        long bookingCountBefore = bookings.count();

        assertThatThrownBy(() -> acceptance.accept(request(quoteRef), MEMBER_ID, "expired-member-quote-it", null))
                .isInstanceOf(BookingCreationRejectedException.class)
                .satisfies(error -> assertThat(((BookingCreationRejectedException) error).getCode())
                        .isEqualTo("QUOTE_EXPIRED"));

        assertThat(bookings.count()).isEqualTo(bookingCountBefore);
        assertThat(quotes.findByQuoteRefAndMemberAccountId(quoteRef, MEMBER_ID).orElseThrow().getQuoteState())
                .isEqualTo("OPEN");
        verifyNoInteractions(operations, loyalty);
    }

    private MemberWorkshopBookingQuote saveQuote(String quoteRef, Instant expiresAt) throws Exception {
        return quotes.saveAndFlush(MemberWorkshopBookingQuote.builder().quoteRef(quoteRef).memberAccountId(MEMBER_ID)
                .packageId(15L).packageNameSnapshot("Silverclay").pricePerParticipant(101L).participantCount(1)
                .sessionFactsJson(mapper.writeValueAsString(List.of(
                        WorkshopSessionFact.of(SESSION_ONE.toString(), 31L, LocalDate.parse("2026-11-15")),
                        WorkshopSessionFact.of(SESSION_TWO.toString(), 31L, LocalDate.parse("2026-11-22")))))
                .benefitSelectionJson(mapper.writeValueAsString(BenefitSelection.none()))
                .grossAmount(101L).programDiscount(0L).pointsUsed(0L).pointDiscount(0L)
                .postPromotionAmount(101L).earnBase(101L).termsDigest("member-quote-it-digest")
                .quoteState("OPEN").createdAt(Instant.now().minusSeconds(5)).expiresAt(expiresAt).build());
    }

    private void configureAuthoritativeFacts() {
        when(operations.getPublishedPackageSelection("15")).thenReturn(new PublishedPackageSelectionSnapshot(
                "15", 101L, 1, 8, 2, true, 0, "Silverclay"));
        when(operations.getSaleEligibility(SESSION_ONE)).thenReturn(new SaleEligibilitySnapshot(SESSION_ONE, true,
                "PUBLISHED", 31L, Instant.parse("2026-11-15T02:00:00Z"), Instant.parse("2026-11-15T04:00:00Z"),
                8, LocalDate.parse("2026-11-15")));
        when(operations.getSaleEligibility(SESSION_TWO)).thenReturn(new SaleEligibilitySnapshot(SESSION_TWO, true,
                "PUBLISHED", 31L, Instant.parse("2026-11-22T02:00:00Z"), Instant.parse("2026-11-22T04:00:00Z"),
                8, LocalDate.parse("2026-11-22")));
        when(operations.reserveBookingCapacity(anyString(), eq("15"), anyList(), eq(1), anyString(), anyString()))
                .thenAnswer(invocation -> CapacityHoldOutcome.accepted(
                        List.of(UUID.randomUUID(), UUID.randomUUID()), Instant.now().plusSeconds(600)));
        when(loyalty.previewBenefits(any(TrustedSourceContext.class), eq(BenefitSelection.none())))
                .thenReturn(new BenefitPreview(BigInteger.valueOf(101), BigInteger.valueOf(101), BigInteger.ZERO,
                        BigInteger.valueOf(101), BigInteger.ZERO, BigInteger.ZERO, BigInteger.ONE, BigInteger.ZERO,
                        BigInteger.ZERO, BigInteger.valueOf(101), BigInteger.valueOf(101), null, null, null,
                        1L, false, List.of(), "member-quote-it-digest"));
        when(loyalty.reserveAcceptedTransaction(any(AcceptedSourceContext.class), eq(BenefitSelection.none()), anyString()))
                .thenAnswer(invocation -> {
                    AcceptedSourceContext source = invocation.getArgument(0);
                    return new AcceptedBenefitRef(19032L, SourceKind.WORKSHOP, source.sourceRef(),
                            BigInteger.valueOf(101), BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO,
                            BigInteger.valueOf(101), BigInteger.valueOf(101), 1L,
                            ComponentState.NONE, ComponentState.NONE, EarnState.PENDING);
                });
    }

    private CreateBookingRequest request(String quoteRef) {
        return CreateBookingRequest.builder().quoteRef(quoteRef).contactEmail("member@example.test")
                .contactPhone("0900000000").build();
    }
}
