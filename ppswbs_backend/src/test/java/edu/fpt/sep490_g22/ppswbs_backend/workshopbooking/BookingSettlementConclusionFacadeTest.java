package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingSettlementConclusionFacadeTest {
    @Mock private WorkshopBookingRepository bookings;
    @Mock private BookingInvoiceRepository invoices;
    @Mock private BookingSessionCommitmentRepository commitments;
    @Mock private MemberWorkshopBookingQuoteRepository memberQuotes;
    @Mock private BookingPaymentAttemptRepository paymentAttempts;
    @Mock private BookingLatePaymentEvidenceRepository lateEvidence;
    @Mock private BookingPaymentReceiptRepository paymentReceipts;
    @Mock private BookingAuditEvidenceRepository audits;
    @Mock private ObjectMapper objectMapper;
    @Mock private WorkshopOperationsAdapter operations;
    @InjectMocks private WorkshopBookingFacadeImpl facade;

    @Test
    void recordsEachLegalServiceConclusionFromConfirmedExactlyOnce() {
        for (BookingServiceOutcome outcome : BookingServiceOutcome.values()) {
            WorkshopBooking booking = confirmed("BK-SERVICE-" + outcome);
            when(bookings.findLockedByBookingCode(booking.getBookingCode())).thenReturn(Optional.of(booking));

            BookingServiceOutcomeResult result = facade.recordBookingServiceOutcome(service(booking.getBookingCode(), outcome));

            assertThat(result.status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
            assertThat(booking.getBookingStatus().name()).isEqualTo(outcome.name());
        }
        verify(bookings, times(3)).save(any(WorkshopBooking.class));
        verify(audits, times(3)).save(any(BookingAuditEvidence.class));
    }

    @Test
    void serviceOutcomeReplayIsAcceptedWithoutSecondMutationOrAudit() {
        WorkshopBooking booking = confirmed("BK-REPLAY");
        booking.setBookingStatus(BookingStatus.COMPLETED);
        when(bookings.findLockedByBookingCode("BK-REPLAY")).thenReturn(Optional.of(booking));

        BookingServiceOutcomeResult result = facade.recordBookingServiceOutcome(service("BK-REPLAY", BookingServiceOutcome.COMPLETED));

        assertThat(result.status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        verify(bookings, never()).save(any());
        verifyNoInteractions(audits);
    }

    @Test
    void rejectsDifferentOrPreConfirmationServiceConclusionWithoutMutation() {
        WorkshopBooking completed = confirmed("BK-DIFFERENT");
        completed.setBookingStatus(BookingStatus.COMPLETED);
        when(bookings.findLockedByBookingCode("BK-DIFFERENT")).thenReturn(Optional.of(completed));
        WorkshopBooking pending = confirmed("BK-PENDING");
        pending.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        when(bookings.findLockedByBookingCode("BK-PENDING")).thenReturn(Optional.of(pending));

        assertThat(facade.recordBookingServiceOutcome(service("BK-DIFFERENT", BookingServiceOutcome.NO_SHOW)).status())
                .isEqualTo(BookingOutcomeStatus.REJECTED);
        assertThat(facade.recordBookingServiceOutcome(service("BK-PENDING", BookingServiceOutcome.CANCELLED)).status())
                .isEqualTo(BookingOutcomeStatus.REJECTED);
        assertThat(completed.getBookingStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(pending.getBookingStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        verify(bookings, never()).save(any());
        verifyNoInteractions(audits);
    }

    @Test
    void recordsMonotonicRefundStatusesAndAcceptsTheirReplays() {
        WorkshopBooking booking = confirmed("BK-REFUND");
        when(bookings.findLockedByBookingCode("BK-REFUND")).thenReturn(Optional.of(booking));

        assertThat(facade.recordBookingRefundStatus(refund("BK-REFUND", BookingRefundStatus.REFUND_PENDING)).status())
                .isEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(facade.recordBookingRefundStatus(refund("BK-REFUND", BookingRefundStatus.REFUNDED)).status())
                .isEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(facade.recordBookingRefundStatus(refund("BK-REFUND", BookingRefundStatus.REFUNDED)).status())
                .isEqualTo(BookingOutcomeStatus.ACCEPTED);
        verify(bookings, times(2)).save(booking);
        verify(audits, times(2)).save(any(BookingAuditEvidence.class));
    }

    @Test
    void rejectsOutOfOrderRefundWithoutMutation() {
        WorkshopBooking booking = confirmed("BK-REFUND-INVALID");
        when(bookings.findLockedByBookingCode("BK-REFUND-INVALID")).thenReturn(Optional.of(booking));

        BookingRefundStatusResult result = facade.recordBookingRefundStatus(refund("BK-REFUND-INVALID", BookingRefundStatus.REFUNDED));

        assertThat(result.status()).isEqualTo(BookingOutcomeStatus.REJECTED);
        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.DEPOSIT_PAID);
        verify(bookings, never()).save(any());
        verifyNoInteractions(audits);
    }

    private static WorkshopBooking confirmed(String code) {
        return WorkshopBooking.builder().id(1L).bookingCode(code).bookingStatus(BookingStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.DEPOSIT_PAID).build();
    }

    private static RecordBookingServiceOutcomeCommand service(String code, BookingServiceOutcome outcome) {
        return new RecordBookingServiceOutcomeCommand(code, outcome, "FOLIO-1", "OP-1", "CORR-1");
    }

    private static RecordBookingRefundStatusCommand refund(String code, BookingRefundStatus status) {
        return new RecordBookingRefundStatusCommand(code, status, "FOLIO-1", "OP-1", "CORR-1");
    }
}
