package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingSessionCommitmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.OtaBookingCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.OtaBookingOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacadeImpl;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OtaBookingCapacityOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtaBookingFacadeIT {
    @Mock private WorkshopBookingRepository bookingRepository;
    @Mock private BookingSessionCommitmentRepository commitmentRepository;
    @Mock private WorkshopOperationsAdapter operationsAdapter;
    @InjectMocks private WorkshopBookingFacadeImpl facade;

    @Test
    void acceptedOtaBookingCreatesOpaqueBookingAndReturnsOperationsTickets() {
        UUID session = UUID.randomUUID();
        UUID ticket = UUID.randomUUID();
        var command = new OtaBookingCommand("KLOOK", "K-100", "17", session, 1, "op-100", "corr-100");
        when(bookingRepository.findLockedByBookingCode(any())).thenReturn(Optional.empty());
        when(operationsAdapter.getSaleEligibility(session)).thenReturn(
                new SaleEligibilitySnapshot(session, true, "OPEN_FOR_SALE", 7L, null, null, 10, null));
        when(operationsAdapter.acceptOtaBooking(any(), any(), any(), any(Integer.class), any(), any()))
                .thenReturn(OtaBookingCapacityOutcome.accepted(List.of(ticket)));
        when(bookingRepository.save(any(WorkshopBooking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OtaBookingOutcome outcome = facade.createOrReturnOtaBooking(command);

        assertThat(outcome.status()).isEqualTo(OtaBookingOutcome.Status.ACCEPTED);
        assertThat(outcome.bookingReference()).startsWith("OTA-").hasSize(32);
        assertThat(outcome.ticketReferences()).containsExactly(ticket);
    }

    @Test
    void changedReplayIsRejectedBeforeOperationsCall() {
        UUID session = UUID.randomUUID();
        var existing = WorkshopBooking.builder().id(10L).bookingCode("OTA-placeholder")
                .packageId(17L).participantCount(1).build();
        when(bookingRepository.findLockedByBookingCode(any())).thenReturn(Optional.of(existing));
        when(commitmentRepository.findByBookingIdOrderByOrdinalAsc(10L)).thenReturn(List.of());

        OtaBookingOutcome outcome = facade.createOrReturnOtaBooking(
                new OtaBookingCommand("KLOOK", "K-100", "17", session, 1, "op-100", "corr-100"));

        assertThat(outcome.status()).isEqualTo(OtaBookingOutcome.Status.REJECTED);
        assertThat(outcome.rejectionReason()).isEqualTo("IDEMPOTENCY_CONFLICT");
    }
}
