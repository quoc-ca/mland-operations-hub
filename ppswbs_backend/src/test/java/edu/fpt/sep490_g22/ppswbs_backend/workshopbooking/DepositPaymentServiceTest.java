package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.DepositPaymentAttemptResponse;
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
    @Mock BookingIdempotencyService idempotency;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test void reusesPendingAttemptWithoutCreatingAnotherOwner() throws Exception {
        var booking = booking(1L, Instant.now().plusSeconds(900));
        var current = BookingPaymentAttempt.builder().booking(booking).attemptReference("ATT-1")
                .merchantReference("DEP-BK-1-1").attemptAmount(50_000L).status(PaymentAttemptStatus.PENDING)
                .paymentUrlSnapshot("https://sandbox/payment").expiresAt(Instant.now().plusSeconds(300)).build();
        stubClaim("idempotency-key-0001");
        when(attemptService.requireBooking("BK-1")).thenReturn(booking);
        when(attempts.findByBookingIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(current));
        var result = new DepositPaymentService(attemptService, attempts, gateway, idempotency, mapper)
                .initiate("BK-1", "idempotency-key-0001", "127.0.0.1");
        assertThat(result.attemptReference()).isEqualTo("ATT-1");
        verifyNoInteractions(gateway);
        verify(attemptService, never()).create(anyString(), anyString(), any(), anyString());
        verify(idempotency).completeClaimWithinTransaction(10L, 1L, 200, mapper.writeValueAsString(result));
    }

    @Test void capsNewAttemptAtBookingHoldExpiry() throws Exception {
        Instant holdExpiry = Instant.now().plusSeconds(120);
        var booking = booking(1L, holdExpiry);
        var invoice = BookingInvoice.builder().booking(booking).depositAmount(50_000L).build();
        var created = BookingPaymentAttempt.builder().booking(booking).attemptReference("ATT-2")
                .merchantReference("DEP-BK-1-2").attemptAmount(50_000L).status(PaymentAttemptStatus.PENDING)
                .paymentUrlSnapshot("https://sandbox/payment").expiresAt(holdExpiry).build();
        stubClaim("idempotency-key-0002");
        when(attempts.findByBookingIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(attemptService.requireBooking("BK-1")).thenReturn(booking);
        when(attemptService.requireInvoice(1L)).thenReturn(invoice);
        when(gateway.createDepositUrl(anyString(), eq("BK-1"), eq(50_000L), any(), any(), eq("127.0.0.1")))
                .thenReturn("https://sandbox/payment");
        when(attemptService.create(eq("BK-1"), eq("https://sandbox/payment"), any(), anyString(), anyString())).thenReturn(created);
        var result = new DepositPaymentService(attemptService, attempts, gateway, idempotency, mapper)
                .initiate("BK-1", "idempotency-key-0002", "127.0.0.1");
        assertThat(result.expiresAt()).isEqualTo(holdExpiry);
        ArgumentCaptor<Instant> gatewayExpiry = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> attemptExpiry = ArgumentCaptor.forClass(Instant.class);
        verify(gateway).createDepositUrl(anyString(), eq("BK-1"), eq(50_000L), any(),
                gatewayExpiry.capture(), eq("127.0.0.1"));
        verify(attemptService).create(eq("BK-1"), eq("https://sandbox/payment"), attemptExpiry.capture(),
                anyString(), anyString());
        assertThat(gatewayExpiry.getValue()).isEqualTo(holdExpiry);
        assertThat(attemptExpiry.getValue()).isEqualTo(holdExpiry);
        verify(idempotency).completeClaimWithinTransaction(10L, 1L, 200, mapper.writeValueAsString(result));
    }

    @Test void returnsCachedOutcomeForAnIdempotentReplayWithoutReadingBookingOrCallingGateway() {
        var expected = new DepositPaymentAttemptResponse("ATT-1", "DEP-BK-1-1", 50_000,
                "VND", "https://sandbox/payment", Instant.parse("2030-01-01T00:10:00Z"), "PENDING");
        when(idempotency.acquireClaim(eq("idempotency-key-replay-001"), eq("START_DEPOSIT_PAYMENT"),
                eq("bookingCode=BK-1"), anyString()))
                .thenReturn(BookingIdempotencyClaim.cached(200, writeJson(expected)));

        var result = new DepositPaymentService(attemptService, attempts, gateway, idempotency, mapper)
                .initiate("BK-1", "idempotency-key-replay-001", "127.0.0.1");

        assertThat(result).isEqualTo(expected);
        verifyNoInteractions(attemptService, attempts, gateway);
        verify(idempotency, never()).completeClaimWithinTransaction(any(), any(), anyInt(), anyString());
    }

    @Test void refusesPaymentLinkForBookingThatIsNoLongerAwaitingPayment() {
        stubClaim("idempotency-key-paid-0001");
        var booking = booking(1L, Instant.now().plusSeconds(900));
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        when(attemptService.requireBooking("BK-1")).thenReturn(booking);

        assertThatThrownBy(() -> new DepositPaymentService(attemptService, attempts, gateway, idempotency, mapper)
                .initiate("BK-1", "idempotency-key-paid-0001", "127.0.0.1"))
                .isInstanceOf(BookingConflictException.class);
        verifyNoInteractions(gateway, attempts);
    }

    @Test void capsPaymentLinkAtTenMinutesWhenTheCapacityHoldLastsLonger() {
        stubClaim("idempotency-key-ten-minutes-01");
        var booking = booking(1L, Instant.now().plusSeconds(15 * 60L));
        var invoice = BookingInvoice.builder().booking(booking).depositAmount(50_001L).build();
        var created = BookingPaymentAttempt.builder().booking(booking).attemptReference("ATT-3")
                .merchantReference("DEP-BK-1-3").attemptAmount(50_001L).status(PaymentAttemptStatus.PENDING)
                .paymentUrlSnapshot("https://sandbox/payment").expiresAt(Instant.now().plusSeconds(600)).build();
        when(attemptService.requireBooking("BK-1")).thenReturn(booking);
        when(attempts.findByBookingIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(attemptService.requireInvoice(1L)).thenReturn(invoice);
        when(gateway.createDepositUrl(anyString(), eq("BK-1"), eq(50_001L), any(), any(), eq("127.0.0.1")))
                .thenReturn("https://sandbox/payment");
        when(attemptService.create(eq("BK-1"), eq("https://sandbox/payment"), any(), anyString(), anyString())).thenReturn(created);
        Instant callStarted = Instant.now();

        new DepositPaymentService(attemptService, attempts, gateway, idempotency, mapper)
                .initiate("BK-1", "idempotency-key-ten-minutes-01", "127.0.0.1");

        ArgumentCaptor<Instant> expiry = ArgumentCaptor.forClass(Instant.class);
        verify(attemptService).create(eq("BK-1"), eq("https://sandbox/payment"), expiry.capture(), anyString(), anyString());
        assertThat(expiry.getValue()).isBetween(callStarted.plusSeconds(600), Instant.now().plusSeconds(601));
    }

    @Test void refusesExpiredCapacityHoldBeforeCreatingGatewayUrl() {
        stubClaim("idempotency-key-expired-hold-01");
        when(attemptService.requireBooking("BK-1"))
                .thenReturn(booking(1L, Instant.now().minusSeconds(1)));

        assertThatThrownBy(() -> new DepositPaymentService(attemptService, attempts, gateway, idempotency, mapper)
                .initiate("BK-1", "idempotency-key-expired-hold-01", "127.0.0.1"))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("hold has expired");
        verifyNoInteractions(attempts, gateway);
    }

    private static WorkshopBooking booking(Long id, Instant holdExpiry) {
        return WorkshopBooking.builder().id(id).bookingCode("BK-1").bookingStatus(BookingStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.UNPAID).holdExpiresAt(holdExpiry).build();
    }

    private void stubClaim(String key) {
        when(idempotency.acquireClaim(eq(key), eq("START_DEPOSIT_PAYMENT"), eq("bookingCode=BK-1"), anyString()))
                .thenReturn(BookingIdempotencyClaim.live(10L, 1L));
    }

    private String writeJson(DepositPaymentAttemptResponse response) {
        try {
            return mapper.writeValueAsString(response);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
