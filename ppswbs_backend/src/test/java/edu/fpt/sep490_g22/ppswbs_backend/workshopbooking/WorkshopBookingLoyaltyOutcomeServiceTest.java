package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.MemberWorkshopBookingQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MemberWorkshopBookingQuote;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingLoyaltyOutcomeService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WorkshopBookingLoyaltyOutcomeServiceTest {
    private final MemberWorkshopBookingQuoteRepository quotes = mock(MemberWorkshopBookingQuoteRepository.class);
    private final LoyaltyFacade loyalty = mock(LoyaltyFacade.class);
    private final WorkshopBookingLoyaltyOutcomeService service = new WorkshopBookingLoyaltyOutcomeService(quotes, loyalty);

    @Test
    void settlesBenefitsForAcceptedMemberQuoteWithOpaqueEvidenceAndQuoteReferences() {
        Instant deadline = Instant.parse("2026-10-31T12:10:00Z");
        WorkshopBooking booking = WorkshopBooking.builder().bookingCode("BK-MEMBER-1").memberAccountId(42L)
                .version(7L).holdExpiresAt(deadline).build();
        MemberWorkshopBookingQuote quote = MemberWorkshopBookingQuote.builder().quoteRef("quote-opaque-1")
                .memberAccountId(42L).acceptedBookingCode("BK-MEMBER-1").build();
        when(quotes.findByAcceptedBookingCode("BK-MEMBER-1")).thenReturn(Optional.of(quote));

        service.settleVerifiedDeposit(booking, "provider-event-opaque-1");

        var captor = org.mockito.ArgumentCaptor.forClass(AuthoritativeOutcome.class);
        verify(loyalty).settleAcceptedBenefits(captor.capture());
        AuthoritativeOutcome outcome = captor.getValue();
        assertThat(outcome.sourceRef()).isEqualTo("BK-MEMBER-1");
        assertThat(outcome.ownerModule()).isEqualTo("workshopbooking");
        assertThat(outcome.sourceOutcomeKey()).isEqualTo("deposit-provider-event-opaque-1");
        assertThat(outcome.sourceOutcomeVersion()).isEqualTo(7L);
        assertThat(outcome.originalDeadline()).isEqualTo(deadline);
        assertThat(outcome.snapshotRef()).isEqualTo("quote-opaque-1");
        assertThat(outcome.memberOrigin()).isTrue();
        assertThat(outcome.proofRef()).isEqualTo("provider-event-opaque-1");
    }

    @Test
    void doesNotCallLoyaltyForGuestBookings() {
        WorkshopBooking guest = WorkshopBooking.builder().bookingCode("BK-GUEST-1").memberAccountId(null).build();

        service.settleVerifiedDeposit(guest, "provider-event-opaque-2");

        verifyNoInteractions(quotes, loyalty);
    }

    @Test
    void failsClosedWhenAcceptedQuoteDoesNotBelongToBookingMember() {
        WorkshopBooking booking = WorkshopBooking.builder().bookingCode("BK-MEMBER-2").memberAccountId(42L).build();
        MemberWorkshopBookingQuote quote = MemberWorkshopBookingQuote.builder().quoteRef("quote-opaque-2")
                .memberAccountId(99L).acceptedBookingCode("BK-MEMBER-2").build();
        when(quotes.findByAcceptedBookingCode("BK-MEMBER-2")).thenReturn(Optional.of(quote));

        service.settleVerifiedDeposit(booking, "provider-event-opaque-3");

        verifyNoInteractions(loyalty);
    }
}
