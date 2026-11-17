package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AuthoritativeOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.OutcomeKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopCartQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.GroupPaymentMode;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopCartQuote;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.GroupLoyaltyOutcomeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 003.1 FR-039 with the 006 cart contract: one booking group holds one voucher use and one points hold, so
 * one outcome settles or releases it.
 */
class GroupLoyaltyOutcomeServiceTest {

    private static final String GROUP = "GRP-TEST0001";
    private static final Instant HOLD = Instant.parse("2026-11-12T10:12:00Z");

    private WorkshopCartQuoteRepository quotes;
    private LoyaltyFacade loyalty;
    private GroupLoyaltyOutcomeService service;

    @BeforeEach
    void setUp() {
        quotes = mock(WorkshopCartQuoteRepository.class);
        loyalty = mock(LoyaltyFacade.class);
        service = new GroupLoyaltyOutcomeService(quotes, loyalty);
        when(quotes.findByAcceptedGroupCode(GROUP)).thenReturn(Optional.of(
                WorkshopCartQuote.builder().quoteRef("quote-1").acceptedGroupCode(GROUP).build()));
    }

    @Test
    void aPaidGroupConsumesItsHeldComponentsOnceAgainstTheGatewayEvidence() {
        service.settleConfirmedGroup(group("benefit-1"), "VNPAY-TX-7");

        ArgumentCaptor<AuthoritativeOutcome> outcome = ArgumentCaptor.forClass(AuthoritativeOutcome.class);
        verify(loyalty).settleAcceptedBenefits(outcome.capture());
        assertThat(outcome.getValue().sourceKind()).isEqualTo(SourceKind.WORKSHOP);
        assertThat(outcome.getValue().sourceRef()).isEqualTo(GROUP);
        assertThat(outcome.getValue().outcome()).isEqualTo(OutcomeKind.WORKSHOP_DEPOSIT_ACCEPTED);
        assertThat(outcome.getValue().snapshotRef()).isEqualTo("quote-1");
        assertThat(outcome.getValue().proofRef()).isEqualTo("VNPAY-TX-7");
        assertThat(outcome.getValue().sourceOutcomeKey()).isEqualTo("group-confirmed-" + GROUP);
        verify(loyalty, never()).releaseHeldBenefits(any());
    }

    @Test
    void anInternallyConfirmedGroupSettlesWithItsOwnReferenceInsteadOfPaymentEvidence() {
        service.settleConfirmedGroup(group("benefit-1"), null);

        ArgumentCaptor<AuthoritativeOutcome> outcome = ArgumentCaptor.forClass(AuthoritativeOutcome.class);
        verify(loyalty).settleAcceptedBenefits(outcome.capture());
        assertThat(outcome.getValue().proofRef()).isEqualTo("INTERNAL-" + GROUP);
    }

    @Test
    void theSameConfirmationKeyIsUsedEveryTimeSoAReplayCannotConsumeTwice() {
        BookingGroup group = group("benefit-1");

        service.settleConfirmedGroup(group, "VNPAY-TX-7");
        service.settleConfirmedGroup(group, "VNPAY-TX-7");

        ArgumentCaptor<AuthoritativeOutcome> outcome = ArgumentCaptor.forClass(AuthoritativeOutcome.class);
        verify(loyalty, times(2)).settleAcceptedBenefits(outcome.capture());
        assertThat(outcome.getAllValues()).extracting(AuthoritativeOutcome::sourceOutcomeKey)
                .containsExactly("group-confirmed-" + GROUP, "group-confirmed-" + GROUP);
    }

    @Test
    void anExpiredGroupReleasesItsComponentsAndClosesTheEarn() {
        service.releaseExpiredGroup(group("benefit-1"));

        ArgumentCaptor<AuthoritativeOutcome> released = ArgumentCaptor.forClass(AuthoritativeOutcome.class);
        verify(loyalty).releaseHeldBenefits(released.capture());
        assertThat(released.getValue().outcome()).isEqualTo(OutcomeKind.UNPAID_EXPIRY_CONFIRMED);
        assertThat(released.getValue().sourceOutcomeKey()).isEqualTo("group-expired-" + GROUP);

        ArgumentCaptor<AuthoritativeOutcome> earn = ArgumentCaptor.forClass(AuthoritativeOutcome.class);
        verify(loyalty).finishIneligibleEarn(earn.capture());
        assertThat(earn.getValue().sourceOutcomeKey()).isEqualTo("group-expired-" + GROUP + "-earn");
        // A distinct version keeps the two outcomes apart for the same group.
        assertThat(earn.getValue().sourceOutcomeVersion())
                .isGreaterThan(released.getValue().sourceOutcomeVersion());
        verify(loyalty, never()).settleAcceptedBenefits(any());
    }

    @Test
    void aGroupThatHeldNoBenefitIsLeftAlone() {
        service.settleConfirmedGroup(group(null), "VNPAY-TX-7");
        service.releaseExpiredGroup(group(null));

        verifyNoInteractions(loyalty);
        verify(quotes, never()).findByAcceptedGroupCode(anyString());
    }

    @Test
    void aGroupWhoseQuoteIsGoneIsLeftAloneRatherThanGuessed() {
        when(quotes.findByAcceptedGroupCode(GROUP)).thenReturn(Optional.empty());

        service.settleConfirmedGroup(group("benefit-1"), "VNPAY-TX-7");

        verifyNoInteractions(loyalty);
    }

    private static BookingGroup group(String voucherUseReference) {
        return BookingGroup.builder().id(1L).groupCode(GROUP).contactEmail("buyer@example.com")
                .contactPhone("0901234567").groupStatus(BookingStatus.CONFIRMED).paymentMode(GroupPaymentMode.VNPAY)
                .groupCollectionVnd(400_000).holdExpiresAt(HOLD).version(2L)
                .voucherUseReference(voucherUseReference).build();
    }
}
