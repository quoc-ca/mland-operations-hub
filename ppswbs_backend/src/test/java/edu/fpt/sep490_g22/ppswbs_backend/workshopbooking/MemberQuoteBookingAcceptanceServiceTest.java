package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingSummaryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.CreateBookingRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemberQuoteBookingAcceptanceServiceTest {
    private final WorkshopBookingAuthorizationService authorization = mock(WorkshopBookingAuthorizationService.class);
    private final WorkshopOperationsAdapter operations = mock(WorkshopOperationsAdapter.class);
    private final LoyaltyFacade loyalty = mock(LoyaltyFacade.class);
    private final MemberWorkshopBookingQuoteRepository quotes = mock(MemberWorkshopBookingQuoteRepository.class);
    private final WorkshopBookingRepository bookings = mock(WorkshopBookingRepository.class);
    private final BookingSessionCommitmentRepository commitments = mock(BookingSessionCommitmentRepository.class);
    private final BookingCapacityHoldRepository holds = mock(BookingCapacityHoldRepository.class);
    private final BookingInvoiceRepository invoices = mock(BookingInvoiceRepository.class);
    private final BookingAuditEvidenceRepository audits = mock(BookingAuditEvidenceRepository.class);
    private final BookingIdempotencyService idempotency = mock(BookingIdempotencyService.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private MemberQuoteBookingAcceptanceService service;

    private final UUID firstSession = UUID.fromString("0a328a43-20cf-4c6b-bdd3-f821670d4ac0");
    private final UUID secondSession = UUID.fromString("8f77585e-5bd8-4d10-b8af-9a39ef4ebf55");
    private final long memberId = 9102L;
    private MemberWorkshopBookingQuote quote;

    @BeforeEach
    void setUp() throws Exception {
        service = new MemberQuoteBookingAcceptanceService(authorization, operations, loyalty, quotes, bookings,
                commitments, holds, invoices, audits, idempotency, mapper);
        quote = MemberWorkshopBookingQuote.builder().quoteRef("c320fb9f-84c2-4b42-b40f-9296b5b58039")
                .memberAccountId(memberId).packageId(15L).packageNameSnapshot("Silverclay")
                .pricePerParticipant(101L).participantCount(1)
                .sessionFactsJson(mapper.writeValueAsString(List.of(
                        WorkshopSessionFact.of(firstSession.toString(), 31L, LocalDate.parse("2026-11-15")),
                        WorkshopSessionFact.of(secondSession.toString(), 31L, LocalDate.parse("2026-11-22")))))
                .benefitSelectionJson(mapper.writeValueAsString(BenefitSelection.none()))
                .grossAmount(101L).programDiscount(0L).pointsUsed(0L).pointDiscount(0L)
                .postPromotionAmount(101L).earnBase(101L).termsDigest("digest-accepted-quote")
                .quoteState("OPEN").createdAt(Instant.now()).expiresAt(Instant.now().plusSeconds(1800)).build();
        when(quotes.findOwnedForUpdate(quote.getQuoteRef(), memberId)).thenReturn(Optional.of(quote));
        when(operations.getPublishedPackageSelection("15")).thenReturn(new PublishedPackageSelectionSnapshot(
                "15", 101L, 1, 8, 2, true, 0, "Silverclay"));
        when(operations.getSaleEligibility(firstSession)).thenReturn(new SaleEligibilitySnapshot(firstSession, true,
                "PUBLISHED", 31L, Instant.parse("2026-11-15T02:00:00Z"), Instant.parse("2026-11-15T04:00:00Z"), 8,
                LocalDate.parse("2026-11-15")));
        when(operations.getSaleEligibility(secondSession)).thenReturn(new SaleEligibilitySnapshot(secondSession, true,
                "PUBLISHED", 31L, Instant.parse("2026-11-22T02:00:00Z"), Instant.parse("2026-11-22T04:00:00Z"), 8,
                LocalDate.parse("2026-11-22")));
        when(operations.reserveBookingCapacity(anyString(), eq("15"), anyList(), eq(1), anyString(), anyString()))
                .thenAnswer(invocation -> CapacityHoldOutcome.accepted(
                        List.of(UUID.randomUUID(), UUID.randomUUID()), Instant.now().plusSeconds(900)));
        when(loyalty.reserveAcceptedTransaction(any(), any(), anyString())).thenAnswer(invocation -> {
            AcceptedSourceContext context = invocation.getArgument(0);
            return new AcceptedBenefitRef(88L, SourceKind.WORKSHOP, context.sourceRef(), BigInteger.valueOf(101),
                    BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(101), BigInteger.valueOf(101),
                    1L, ComponentState.NONE, ComponentState.NONE, EarnState.PENDING);
        });
        when(loyalty.previewBenefits(any(), any())).thenReturn(new BenefitPreview(BigInteger.valueOf(101),
                BigInteger.valueOf(101), BigInteger.ZERO, BigInteger.valueOf(101), BigInteger.ZERO,
                BigInteger.ZERO, BigInteger.ONE, BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(101),
                BigInteger.valueOf(101), null, null, null, 1L, false, List.of(), "digest-accepted-quote"));
        when(bookings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void acceptsOwnedQuoteWithCanonicalSessionUuidsAndCeilingDepositInOneOrchestration() {
        BookingSummaryResponse response = service.accept(request(), memberId, "corr-accept", BookingIdempotencyClaim.live(1L, 1L));

        assertThat(response.getBookingStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.getSessions()).extracting(s -> s.getSessionReference())
                .containsExactly(firstSession, secondSession);
        ArgumentCaptor<BookingInvoice> invoiceCaptor = ArgumentCaptor.forClass(BookingInvoice.class);
        verify(invoices).save(invoiceCaptor.capture());
        assertThat(invoiceCaptor.getValue().getPackageTotal()).isEqualTo(101L);
        assertThat(invoiceCaptor.getValue().getPostPromotionAmount()).isEqualTo(101L);
        assertThat(invoiceCaptor.getValue().getDepositAmount()).isEqualTo(51L);
        assertThat(invoiceCaptor.getValue().getRemainingAmount()).isEqualTo(50L);
        verify(operations).reserveBookingCapacity(anyString(), eq("15"), eq(List.of(firstSession, secondSession)),
                eq(1), anyString(), eq("corr-accept"));
        ArgumentCaptor<AcceptedSourceContext> context = ArgumentCaptor.forClass(AcceptedSourceContext.class);
        verify(loyalty).reserveAcceptedTransaction(context.capture(), eq(BenefitSelection.none()), anyString());
        verify(loyalty).previewBenefits(any(), eq(BenefitSelection.none()));
        assertThat(context.getValue().trusted().workshop().sessions())
                .extracting(WorkshopSessionFact::sessionRef).containsExactly(firstSession.toString(), secondSession.toString());
        assertThat(context.getValue().quoteRef()).isEqualTo(quote.getQuoteRef());
        assertThat(context.getValue().termsDigest()).isEqualTo("digest-accepted-quote");
        assertThat(quote.getQuoteState()).isEqualTo("ACCEPTED");
        assertThat(quote.getAcceptedBookingCode()).isEqualTo(response.getBookingCode());
        verify(idempotency).completeClaimWithinTransaction(eq(1L), eq(1L), eq(201), anyString());
    }

    @Test
    void rejectsExpiredQuoteBeforeCapacityOrLoyaltyReservation() {
        quote.setExpiresAt(Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> service.accept(request(), memberId, "corr-expired", null))
                .isInstanceOf(BookingCreationRejectedException.class)
                .satisfies(error -> assertThat(((BookingCreationRejectedException) error).getCode()).isEqualTo("QUOTE_EXPIRED"));
        verifyNoInteractions(operations, loyalty, bookings, commitments, holds, invoices, audits);
    }

    @Test
    void rejectsQuoteWhenCurrentPreviewHasIneligibleBenefitReasonsBeforeCapacityReservation() {
        when(loyalty.previewBenefits(any(), any())).thenReturn(new BenefitPreview(BigInteger.valueOf(101),
                BigInteger.valueOf(101), BigInteger.ZERO, BigInteger.valueOf(101), BigInteger.ZERO,
                BigInteger.ZERO, BigInteger.ONE, BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(101),
                BigInteger.valueOf(101), null, null, null, 1L, false, List.of("VOUCHER_INELIGIBLE"),
                "digest-accepted-quote"));

        assertThatThrownBy(() -> service.accept(request(), memberId, "corr-ineligible", null))
                .isInstanceOf(BookingCreationRejectedException.class)
                .satisfies(error -> assertThat(((BookingCreationRejectedException) error).getCode())
                        .isEqualTo("PROMOTION_INELIGIBLE"));
        verify(operations, never()).reserveBookingCapacity(anyString(), anyString(), anyList(), anyInt(), anyString(), anyString());
        verify(loyalty, never()).reserveAcceptedTransaction(any(), any(), anyString());
        verifyNoInteractions(bookings, commitments, holds, invoices, audits);
    }

    @Test
    void rejectsQuoteWhenLoyaltyTermsDigestChangedBeforeCapacityReservation() {
        when(loyalty.previewBenefits(any(), any())).thenReturn(new BenefitPreview(BigInteger.valueOf(101),
                BigInteger.valueOf(101), BigInteger.ZERO, BigInteger.valueOf(101), BigInteger.ZERO,
                BigInteger.ZERO, BigInteger.ONE, BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(101),
                BigInteger.valueOf(101), null, null, null, 2L, false, List.of(), "new-digest"));

        assertThatThrownBy(() -> service.accept(request(), memberId, "corr-changed", null))
                .isInstanceOf(BookingCreationRejectedException.class)
                .satisfies(error -> assertThat(((BookingCreationRejectedException) error).getCode()).isEqualTo("QUOTE_CHANGED"));
        verify(operations, never()).reserveBookingCapacity(anyString(), anyString(), anyList(), anyInt(), anyString(), anyString());
        verify(loyalty, never()).reserveAcceptedTransaction(any(), any(), anyString());
        verifyNoInteractions(bookings, commitments, holds, invoices, audits);
    }

    @Test
    void quoteNotOwnedByAuthenticatedMemberIsNotFoundAndHasNoSideEffects() {
        when(quotes.findOwnedForUpdate(quote.getQuoteRef(), memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(request(), memberId, "corr-owner", null))
                .isInstanceOf(BookingNotFoundException.class);
        verifyNoInteractions(operations, loyalty, bookings, commitments, holds, invoices, audits);
    }

    private CreateBookingRequest request() {
        return CreateBookingRequest.builder().quoteRef(quote.getQuoteRef())
                .contactEmail("member@example.com").contactPhone("0901234567").build();
    }
}
