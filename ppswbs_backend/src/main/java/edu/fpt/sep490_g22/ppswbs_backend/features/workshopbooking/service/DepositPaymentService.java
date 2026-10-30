package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.DepositPaymentAttemptResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepositPaymentService {
    private final BookingDepositPaymentAttemptService attemptService;
    private final BookingPaymentAttemptRepository attempts;
    private final PaymentGatewayCheckoutPort gateway;

    @Transactional
    public DepositPaymentAttemptResponse initiate(String bookingCode, String idempotencyKey, String clientIp) {
        if (idempotencyKey == null || idempotencyKey.trim().length() < 16 || idempotencyKey.trim().length() > 128) {
            throw new BookingValidationException("Idempotency-Key must be between 16 and 128 characters");
        }
        Instant now = Instant.now();
        var existing = attempts.findByBookingIdOrderByCreatedAtDesc(
                attemptService.requireBookingId(bookingCode)).stream().findFirst();
        if (existing.isPresent()) {
            var current = existing.get();
            if (current.getStatus() == PaymentAttemptStatus.PENDING && current.getExpiresAt().isAfter(now)) {
                return response(current);
            }
            if (current.getStatus() == PaymentAttemptStatus.PENDING) current.setStatus(PaymentAttemptStatus.EXPIRED);
        }
        var booking = attemptService.requireBooking(bookingCode);
        if (booking.getHoldExpiresAt() == null || !booking.getHoldExpiresAt().isAfter(now)) {
            throw new BookingConflictException("Booking hold has expired");
        }
        Instant expiresAt = now.plusSeconds(10 * 60L);
        if (booking.getHoldExpiresAt().isBefore(expiresAt)) expiresAt = booking.getHoldExpiresAt();
        var invoice = attemptService.requireInvoice(booking.getId());
        String merchantReference = "DEP-" + bookingCode + "-" + UUID.randomUUID();
        String url = gateway.createDepositUrl(merchantReference, bookingCode, invoice.getDepositAmount(), now, clientIp);
        var attempt = attemptService.create(bookingCode, url, expiresAt, merchantReference);
        return response(attempt);
    }
    private DepositPaymentAttemptResponse response(BookingPaymentAttempt a) {
        return new DepositPaymentAttemptResponse(a.getAttemptReference(), a.getMerchantReference(), a.getAttemptAmount(), "VND", a.getPaymentUrlSnapshot(), a.getExpiresAt(), a.getStatus().name());
    }
}
