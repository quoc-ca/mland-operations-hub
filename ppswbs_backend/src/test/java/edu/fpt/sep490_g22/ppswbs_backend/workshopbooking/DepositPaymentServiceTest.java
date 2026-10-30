package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepositPaymentServiceTest {
    @Mock BookingDepositPaymentAttemptService attemptService;
    @Mock BookingPaymentAttemptRepository attempts;
    @Mock PaymentGatewayCheckoutPort gateway;

    @Test void reusesPendingAttemptWithoutCreatingAnotherOwner() {
        var booking = booking(1L, Instant.now().plusSeconds(900));
        var current = BookingPaymentAttempt.builder().booking(booking).attemptReference("ATT-1")
                .merchantReference("DEP-BK-1-1").attemptAmount(50_000L).status(PaymentAttemptStatus.PENDING)
                .paymentUrlSnapshot("https://sandbox/payment").expiresAt(Instant.now().plusSeconds(300)).build();
        when(attemptService.requireBookingId("BK-1")).thenReturn(1L);
        when(attempts.findByBookingIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(current));
        var result = new DepositPaymentService(attemptService, attempts, gateway)
                .initiate("BK-1", "idempotency-key-0001", "127.0.0.1");
        assertThat(result.attemptReference()).isEqualTo("ATT-1");
        verifyNoInteractions(gateway);
        verify(attemptService, never()).create(anyString(), anyString(), any(), anyString());
    }

    @Test void capsNewAttemptAtBookingHoldExpiry() {
        Instant holdExpiry = Instant.now().plusSeconds(120);
        var booking = booking(1L, holdExpiry);
        var invoice = BookingInvoice.builder().booking(booking).depositAmount(50_000L).build();
        var created = BookingPaymentAttempt.builder().booking(booking).attemptReference("ATT-2")
                .merchantReference("DEP-BK-1-2").attemptAmount(50_000L).status(PaymentAttemptStatus.PENDING)
                .paymentUrlSnapshot("https://sandbox/payment").expiresAt(holdExpiry).build();
        when(attemptService.requireBookingId("BK-1")).thenReturn(1L);
        when(attempts.findByBookingIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(attemptService.requireBooking("BK-1")).thenReturn(booking);
        when(attemptService.requireInvoice(1L)).thenReturn(invoice);
        when(gateway.createDepositUrl(anyString(), eq("BK-1"), eq(50_000L), any(), eq("127.0.0.1")))
                .thenReturn("https://sandbox/payment");
        when(attemptService.create(eq("BK-1"), eq("https://sandbox/payment"), any(), anyString())).thenReturn(created);
        var result = new DepositPaymentService(attemptService, attempts, gateway)
                .initiate("BK-1", "idempotency-key-0002", "127.0.0.1");
        assertThat(result.expiresAt()).isEqualTo(holdExpiry);
        verify(attemptService).create(eq("BK-1"), eq("https://sandbox/payment"), argThat(value -> !value.isAfter(holdExpiry)), anyString());
    }

    private static WorkshopBooking booking(Long id, Instant holdExpiry) {
        return WorkshopBooking.builder().id(id).bookingCode("BK-1").bookingStatus(BookingStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.UNPAID).holdExpiresAt(holdExpiry).build();
    }
}
