package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingSessionCommitmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingSessionCommitment;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingPendingHoldReleaseTransactionServiceTest {

    @Mock private WorkshopBookingRepository bookings;
    @Mock private BookingSessionCommitmentRepository commitments;
    @Mock private WorkshopBookingFacade bookingFacade;
    @Mock private WorkshopBookingAuthorizationService authorization;
    @Mock private BookingHoldReleaseService holdRelease;
    @Mock private BookingIdempotencyService idempotency;
    @Spy private ObjectMapper mapper = new ObjectMapper();
    @InjectMocks private BookingPendingHoldReleaseTransactionService service;

    private WorkshopBooking pending;
    private BookingIdempotencyClaim claim;

    @BeforeEach
    void setUp() {
        pending = WorkshopBooking.builder().id(19L).bookingCode("BK-RELEASE-1")
                .bookingStatus(BookingStatus.PENDING_PAYMENT).paymentStatus(PaymentStatus.UNPAID)
                .holdExpiresAt(Instant.now().plusSeconds(300)).build();
        claim = BookingIdempotencyClaim.live(91L, 1L);
        when(bookings.findLockedByBookingCode("BK-RELEASE-1")).thenReturn(Optional.of(pending));
        when(commitments.findByBookingIdOrderByOrdinalAsc(19L)).thenReturn(List.of(
                BookingSessionCommitment.builder().locationId(5L).ordinal(1).build()));
    }

    @Test
    void guestReleaseRechecksStoredProofUnderLockAndCommitsAuditBearingCommand() {
        when(bookingFacade.verifyCustomerBookingAccess(any())).thenReturn(new CustomerBookingAccessDecision(true));

        BookingRecoveryCommandResult result = service.release(null, "BK-RELEASE-1", "GUEST", null,
                null, "0900000000", "Plans changed", "corr-1", claim);

        assertThat(result.statusCode()).isEqualTo(204);
        verify(holdRelease).releasePendingHold("BK-RELEASE-1", "REL-CUSTOMER-91", "corr-1",
                "GUEST", null, "Plans changed");
        verify(idempotency).completeClaimWithinTransaction(91L, 1L, 204, "");
    }

    @Test
    void memberReleaseRechecksAuthenticatedOwnershipAndNeverRequiresContactProof() {
        AccountSummary member = new AccountSummary(44L, ActorRole.MEMBER, ActorStatus.ACTIVE,
                "member@example.test", null, null);
        when(authorization.requireMemberOwnership("Bearer member-token", null,
                "BK-RELEASE-1", 5L, "corr-2")).thenReturn(member);
        when(bookingFacade.verifyCustomerBookingAccess(any())).thenReturn(new CustomerBookingAccessDecision(true));

        BookingRecoveryCommandResult result = service.release("Bearer member-token", "BK-RELEASE-1", "MEMBER", 44L,
                null, null, "No longer needed", "corr-2", claim);

        assertThat(result.statusCode()).isEqualTo(204);
        verify(bookingFacade).verifyCustomerBookingAccess(argThat(query -> Long.valueOf(44L).equals(query.memberAccountId())
                && query.contactEmail() == null && query.contactPhone() == null));
        verify(holdRelease).releasePendingHold("BK-RELEASE-1", "REL-CUSTOMER-91", "corr-2",
                "MEMBER", 44L, "No longer needed");
    }

    @Test
    void expiredHoldIsNotReleasedOrCancelled() {
        pending.setHoldExpiresAt(Instant.now().minusSeconds(1));
        when(bookingFacade.verifyCustomerBookingAccess(any())).thenReturn(new CustomerBookingAccessDecision(true));

        BookingRecoveryCommandResult result = service.release(null, "BK-RELEASE-1", "GUEST", null,
                null, "0900000000", "Plans changed", "corr-3", claim);

        assertThat(result.statusCode()).isEqualTo(409);
        assertThat(pending.getBookingStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        verifyNoInteractions(holdRelease);
    }
}
