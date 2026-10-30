package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.entity.PaymentOwnerKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.facade.PaymentOwnerRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

/** Creates the direct-booking deposit attempt and registers its immutable payment owner before redirect. */
@Service @RequiredArgsConstructor
public class BookingDepositPaymentAttemptService {
    private final WorkshopBookingRepository bookings;
    private final BookingInvoiceRepository invoices;
    private final BookingPaymentAttemptRepository attempts;
    private final PaymentOwnerRegistry owners;

    @Transactional
    public BookingPaymentAttempt create(String bookingCode, String paymentUrl, Instant expiresAt) {
        require(bookingCode, "bookingCode"); require(paymentUrl, "paymentUrl");
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) throw new BookingValidationException("expiresAt must be in the future");
        var booking = bookings.findByBookingCode(bookingCode).orElseThrow(() -> new BookingNotFoundException("Booking " + bookingCode + " not found"));
        var invoice = invoices.findByBookingId(booking.getId()).orElseThrow(() -> new BookingValidationException("Invoice is missing"));
        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) throw new BookingConflictException("Booking is not awaiting payment");
        String merchantReference = "DEP-" + bookingCode + "-" + UUID.randomUUID();
        var attempt = attempts.saveAndFlush(BookingPaymentAttempt.builder().booking(booking).invoice(invoice)
                .attemptReference("ATT-" + UUID.randomUUID()).merchantReference(merchantReference)
                .attemptAmount(invoice.getDepositAmount()).status(PaymentAttemptStatus.PENDING)
                .paymentUrlSnapshot(paymentUrl).expiresAt(expiresAt).build());
        owners.register(merchantReference, bookingCode, PaymentOwnerKind.DEPOSIT, invoice.getDepositAmount(), "VND");
        return attempt;
    }
    private static void require(String value,String field){if(value==null||value.isBlank())throw new BookingValidationException(field+" is required");}
}
