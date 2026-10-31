package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingSessionCommitmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.PendingHoldReleaseRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingSessionCommitment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingPendingHoldReleaseServiceTest {

    @Mock private WorkshopBookingRepository bookings;
    @Mock private BookingSessionCommitmentRepository commitments;
    @Mock private WorkshopBookingFacade bookingFacade;
    @Mock private WorkshopBookingAuthorizationService authorization;
    @Mock private BookingIdempotencyService idempotency;
    @Mock private BookingPendingHoldReleaseTransactionService transactionService;
    @InjectMocks private BookingPendingHoldReleaseService service;

    @Test
    void guestMustProvideExactlyOneProofAndProofIsNotPassedAsActorIdentity() {
        WorkshopBooking booking = booking("BK-GUEST-1", 17L);
        when(bookings.findByBookingCode("BK-GUEST-1")).thenReturn(Optional.of(booking));
        when(commitments.findByBookingIdOrderByOrdinalAsc(17L)).thenReturn(List.of(commitment(4L)));
        when(bookingFacade.verifyCustomerBookingAccess(any())).thenReturn(new CustomerBookingAccessDecision(true));
        when(idempotency.acquireClaim(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(BookingIdempotencyClaim.live(80L, 1L));
        when(transactionService.release(any(), anyString(), anyString(), any(), any(), any(), anyString(), anyString(), any()))
                .thenReturn(new BookingRecoveryCommandResult(204, ""));

        BookingRecoveryCommandResult result = service.release(null, "BK-GUEST-1", "release-guest-key-0001", "corr-1",
                PendingHoldReleaseRequest.builder().contactPhone("0900000000").reason("Plans changed").build());

        assertThat(result.statusCode()).isEqualTo(204);
        verify(transactionService).release(isNull(), eq("BK-GUEST-1"), eq("GUEST"), isNull(), isNull(),
                eq("0900000000"), eq("Plans changed"), eq("corr-1"), any());
        verify(bookingFacade).verifyCustomerBookingAccess(argThat(query -> query.memberAccountId() == null
                && query.contactEmail() == null && "0900000000".equals(query.contactPhone())));
    }

    @Test
    void memberUsesAuthenticatedOwnershipWithoutContactProof() {
        WorkshopBooking booking = booking("BK-MEMBER-1", 18L);
        AccountSummary member = new AccountSummary(44L, ActorRole.MEMBER, ActorStatus.ACTIVE, "member@example.test", null, null);
        when(bookings.findByBookingCode("BK-MEMBER-1")).thenReturn(Optional.of(booking));
        when(commitments.findByBookingIdOrderByOrdinalAsc(18L)).thenReturn(List.of(commitment(4L)));
        when(authorization.requireMemberOwnership("Bearer member-token", null,
                "BK-MEMBER-1", 4L, "corr-2")).thenReturn(member);
        when(bookingFacade.verifyCustomerBookingAccess(any())).thenReturn(new CustomerBookingAccessDecision(true));
        when(idempotency.acquireClaim(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(BookingIdempotencyClaim.live(81L, 1L));
        when(transactionService.release(anyString(), anyString(), anyString(), anyLong(), isNull(), isNull(),
                anyString(), anyString(), any())).thenReturn(new BookingRecoveryCommandResult(204, ""));

        BookingRecoveryCommandResult result = service.release("Bearer member-token", "BK-MEMBER-1",
                "release-member-key-0001", "corr-2",
                PendingHoldReleaseRequest.builder().reason("I no longer need this booking").build());

        assertThat(result.statusCode()).isEqualTo(204);
        verify(bookingFacade).verifyCustomerBookingAccess(argThat(query -> Long.valueOf(44L).equals(query.memberAccountId())
                && query.contactEmail() == null && query.contactPhone() == null));
        verify(transactionService).release(eq("Bearer member-token"), eq("BK-MEMBER-1"), eq("MEMBER"), eq(44L),
                isNull(), isNull(), eq("I no longer need this booking"), eq("corr-2"), any());
    }

    @Test
    void rejectsMultipleGuestProofAndContactDetailsInReasonBeforeDatabaseAccess() {
        PendingHoldReleaseRequest request = PendingHoldReleaseRequest.builder()
                .contactEmail("guest@example.test").contactPhone("0900000000")
                .reason("Please call me at 0900000000").build();

        assertThatThrownBy(() -> service.release(null, "BK-GUEST-2", "release-guest-key-0002", "corr-3", request))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("must not contain");
        verifyNoInteractions(bookings, commitments, bookingFacade, authorization, idempotency, transactionService);
    }

    @Test
    void replayReturnsCachedNoContentWithoutRepeatingHoldRelease() {
        WorkshopBooking booking = booking("BK-REPLAY-1", 19L);
        when(bookings.findByBookingCode("BK-REPLAY-1")).thenReturn(Optional.of(booking));
        when(commitments.findByBookingIdOrderByOrdinalAsc(19L)).thenReturn(List.of(commitment(4L)));
        when(bookingFacade.verifyCustomerBookingAccess(any())).thenReturn(new CustomerBookingAccessDecision(true));
        when(idempotency.acquireClaim("release-replay-key-001", "RELEASE_PENDING_HOLD",
                "BK-REPLAY-1|GUEST|null|Plans changed", "corr-replay"))
                .thenReturn(BookingIdempotencyClaim.cached(204, null));

        BookingRecoveryCommandResult result = service.release(null, "BK-REPLAY-1", "release-replay-key-001",
                "corr-replay", PendingHoldReleaseRequest.builder()
                        .contactPhone("0900000000").reason("Plans changed").build());

        assertThat(result.statusCode()).isEqualTo(204);
        assertThat(result.responsePayload()).isEmpty();
        verifyNoInteractions(transactionService);
    }

    private WorkshopBooking booking(String code, Long id) {
        return WorkshopBooking.builder().id(id).bookingCode(code).build();
    }

    private BookingSessionCommitment commitment(Long locationId) {
        return BookingSessionCommitment.builder().locationId(locationId).ordinal(1).build();
    }
}
