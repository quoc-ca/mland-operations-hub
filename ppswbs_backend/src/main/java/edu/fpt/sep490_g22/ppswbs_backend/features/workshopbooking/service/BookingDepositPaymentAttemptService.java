package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentOwnerRegistrationPort;
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
    private final PaymentOwnerRegistrationPort owners;

    @Transactional
    public BookingPaymentAttempt create(String bookingCode, String paymentUrl, Instant expiresAt) {
        return create(bookingCode, paymentUrl, expiresAt, "DEP-" + bookingCode + "-" + UUID.randomUUID(), null);
    }
    @Transactional
    public BookingPaymentAttempt create(String bookingCode, String paymentUrl, Instant expiresAt, String merchantReference) {
        return create(bookingCode, paymentUrl, expiresAt, merchantReference, null);
    }
    @Transactional
    public BookingPaymentAttempt create(String bookingCode, String paymentUrl, Instant expiresAt,
                                        String merchantReference, String providerCreateDate) {
        require(bookingCode, "bookingCode"); require(paymentUrl, "paymentUrl");
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) throw new BookingValidationException("expiresAt must be in the future");
        var booking = requireBooking(bookingCode);
        var invoice = requireInvoice(booking.getId());
        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) throw new BookingConflictException("Booking is not awaiting payment");
        var attempt = attempts.saveAndFlush(BookingPaymentAttempt.builder().booking(booking).invoice(invoice)
                .attemptReference("ATT-" + UUID.randomUUID()).merchantReference(merchantReference)
                .providerCreateDate(providerCreateDate)
                .attemptAmount(invoice.getDepositAmount()).status(PaymentAttemptStatus.PENDING)
                .paymentUrlSnapshot(paymentUrl).expiresAt(expiresAt).build());
        owners.registerOwner(merchantReference, bookingCode, "DEPOSIT", invoice.getDepositAmount(), "VND", providerCreateDate);
        return attempt;
    }
    public WorkshopBooking requireBooking(String bookingCode) { return bookings.findByBookingCode(bookingCode).orElseThrow(() -> new BookingNotFoundException("Booking " + bookingCode + " not found")); }
    public BookingInvoice requireInvoice(Long bookingId) { return invoices.findByBookingId(bookingId).orElseThrow(() -> new BookingValidationException("Invoice is missing")); }
    public Long requireBookingId(String bookingCode) { return requireBooking(bookingCode).getId(); }
    private static void require(String value,String field){if(value==null||value.isBlank())throw new BookingValidationException(field+" is required");}
}
