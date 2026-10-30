package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentOwnerRegistrationPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingDepositPaymentAttemptService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingDepositPaymentAttemptServiceTest {
    @Mock WorkshopBookingRepository bookings; @Mock BookingInvoiceRepository invoices;
    @Mock BookingPaymentAttemptRepository attempts; @Mock PaymentOwnerRegistrationPort owners;
    @Test void registersOwnerBeforeReturningAttempt() {
        var booking = WorkshopBooking.builder().id(1L).bookingCode("BK-1").bookingStatus(BookingStatus.PENDING_PAYMENT).build();
        var invoice = BookingInvoice.builder().id(2L).booking(booking).depositAmount(50_000L).build();
        when(bookings.findByBookingCode("BK-1")).thenReturn(java.util.Optional.of(booking));
        when(invoices.findByBookingId(1L)).thenReturn(java.util.Optional.of(invoice));
        when(attempts.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        var attempt = new BookingDepositPaymentAttemptService(bookings, invoices, attempts, owners)
                .create("BK-1", "https://sandbox.vnpayment.vn/payment", Instant.now().plusSeconds(900));
        assertThat(attempt.getMerchantReference()).startsWith("DEP-BK-1-");
        verify(owners).registerOwner(eq(attempt.getMerchantReference()), eq("BK-1"), eq("DEPOSIT"), eq(50_000L), eq("VND"));
    }
}
