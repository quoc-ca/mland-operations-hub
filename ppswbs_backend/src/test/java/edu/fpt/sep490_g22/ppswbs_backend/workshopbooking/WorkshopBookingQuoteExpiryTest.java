package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitPreview;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TrustedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.MemberWorkshopBookingQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.WorkshopBookingQuoteRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MemberWorkshopBookingQuote;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingQuoteService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkshopBookingQuoteExpiryTest {

    private final WorkshopBookingAuthorizationService authorization = mock(WorkshopBookingAuthorizationService.class);
    private final WorkshopOperationsAdapter operations = mock(WorkshopOperationsAdapter.class);
    private final LoyaltyFacade loyalty = mock(LoyaltyFacade.class);
    private final MemberWorkshopBookingQuoteRepository quotes = mock(MemberWorkshopBookingQuoteRepository.class);
    private final WorkshopBookingQuoteService service = new WorkshopBookingQuoteService(
            authorization, operations, loyalty, quotes, new ObjectMapper().findAndRegisterModules());

    @Test
    void previewPersistsAndReturnsThirtyMinuteExpiryWithoutReservingBenefitsOrCapacity() {
        UUID sessionReference = UUID.randomUUID();
        UUID secondSessionReference = UUID.randomUUID();
        when(authorization.requireMemberOwnership("Bearer member", null))
                .thenReturn(new AccountSummary(71L, ActorRole.MEMBER, ActorStatus.ACTIVE,
                        "member@example.test", Instant.now(), Instant.now()));
        when(operations.getPublishedPackageSelection("5"))
                .thenReturn(new PublishedPackageSelectionSnapshot("5", 450_000L,
                        1, 4, 2, true, 0, "Pottery"));
        when(operations.getSaleEligibility(sessionReference))
                .thenReturn(new SaleEligibilitySnapshot(sessionReference, true, "PUBLISHED", 9L,
                        Instant.parse("2026-11-10T02:00:00Z"), Instant.parse("2026-11-10T03:00:00Z"), 10,
                        LocalDate.of(2026, 11, 10)));
        when(operations.getSaleEligibility(secondSessionReference))
                .thenReturn(new SaleEligibilitySnapshot(secondSessionReference, true, "PUBLISHED", 9L,
                        Instant.parse("2026-11-11T02:00:00Z"), Instant.parse("2026-11-11T03:00:00Z"), 10,
                        LocalDate.of(2026, 11, 11)));
        when(loyalty.previewBenefits(any(), any())).thenReturn(preview());
        when(quotes.save(any(MemberWorkshopBookingQuote.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.preview("Bearer member", new WorkshopBookingQuoteRequest(
                5L, List.of(sessionReference.toString(), secondSessionReference.toString()), 1,
                new WorkshopBookingQuoteRequest.BenefitSelectionRequest(
                        WorkshopBookingQuoteRequest.BenefitSelectionRequest.Choice.NONE, null, "0")));

        var persisted = org.mockito.ArgumentCaptor.forClass(MemberWorkshopBookingQuote.class);
        verify(quotes).save(persisted.capture());
        MemberWorkshopBookingQuote quote = persisted.getValue();
        assertThat(quote.getMemberAccountId()).isEqualTo(71L);
        assertThat(quote.getPackageId()).isEqualTo(5L);
        assertThat(quote.getPackageNameSnapshot()).isEqualTo("Pottery");
        assertThat(quote.getSessionFactsJson()).contains(sessionReference.toString(), secondSessionReference.toString(),
                "\"branchId\":9", "2026,11,10", "2026,11,11");
        assertThat(quote.getBenefitSelectionJson()).contains("NONE", "requestedPoints", "0");
        assertThat(quote.getGrossAmount()).isEqualTo(450_000L);
        assertThat(quote.getProgramDiscount()).isZero();
        assertThat(quote.getPointsUsed()).isZero();
        assertThat(quote.getPointDiscount()).isZero();
        assertThat(quote.getPostPromotionAmount()).isEqualTo(450_000L);
        assertThat(quote.getEarnBase()).isEqualTo(450_000L);
        assertThat(quote.getTermsDigest()).isEqualTo("terms-v1");
        assertThat(quote.getExpiresAt()).isEqualTo(quote.getCreatedAt().plus(MemberWorkshopBookingQuote.QUOTE_TTL));
        assertThat(response.quoteRef()).isEqualTo(quote.getQuoteRef());
        assertThat(response.expiresAt()).isEqualTo(quote.getExpiresAt());
        assertThat(response.expiresAt()).isAfter(quote.getCreatedAt());
        assertThat(response.grossAmount()).isEqualTo("450000");
        assertThat(response.programDiscount()).isEqualTo("0");
        assertThat(response.pointsUsed()).isEqualTo("0");
        assertThat(response.maxPoints()).isEqualTo("0");
        assertThat(response.pointDiscount()).isEqualTo("0");
        assertThat(response.postPromotionAmount()).isEqualTo("450000");
        assertThat(response.depositAmount()).isEqualTo("225000");
        assertThat(response.remainingAmount()).isEqualTo("225000");
        assertThat(response.earnBase()).isEqualTo("450000");
        assertThat(response.termsDigest()).isEqualTo("terms-v1");
        verify(operations).getPublishedPackageSelection("5");
        verify(operations).getSaleEligibility(sessionReference);
        verify(operations).getSaleEligibility(secondSessionReference);
        verifyNoMoreInteractions(operations);
        var context = org.mockito.ArgumentCaptor.forClass(TrustedSourceContext.class);
        var selection = org.mockito.ArgumentCaptor.forClass(BenefitSelection.class);
        verify(loyalty).previewBenefits(context.capture(), selection.capture());
        assertThat(context.getValue().sourceKind()).isEqualTo(SourceKind.WORKSHOP);
        assertThat(context.getValue().memberId()).isEqualTo(71L);
        assertThat(context.getValue().workshop().sessions()).hasSize(2);
        assertThat(context.getValue().workshop().grossPackageAmount()).isEqualTo(BigInteger.valueOf(450_000L));
        assertThat(selection.getValue().choice()).isEqualTo(SelectionChoice.NONE);
        verifyNoMoreInteractions(loyalty);
    }

    private static BenefitPreview preview() {
        BigInteger amount = BigInteger.valueOf(450_000L);
        return new BenefitPreview(amount, amount, BigInteger.ZERO, amount, BigInteger.ZERO,
                BigInteger.ZERO, BigInteger.ONE, BigInteger.ZERO, BigInteger.ZERO, amount, amount,
                null, null, null, 1L, false, List.of(), "terms-v1");
    }
}
