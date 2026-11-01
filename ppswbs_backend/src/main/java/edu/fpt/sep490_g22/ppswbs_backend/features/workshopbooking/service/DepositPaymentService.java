package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.DepositPaymentAttemptResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepositPaymentService {
    private static final DateTimeFormatter PROVIDER_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.ofHours(7));
    private final BookingDepositPaymentAttemptService attemptService;
    private final BookingPaymentAttemptRepository attempts;
    private final PaymentGatewayCheckoutPort gateway;
    private final BookingIdempotencyService idempotency;
    private final ObjectMapper mapper;

    @Transactional
    public DepositPaymentAttemptResponse initiate(String bookingCode, String idempotencyKey, String clientIp) {
        if (idempotencyKey == null || idempotencyKey.trim().length() < 16 || idempotencyKey.trim().length() > 128) {
            throw new BookingValidationException("Idempotency-Key must be between 16 and 128 characters");
        }
        if (bookingCode == null || bookingCode.isBlank()) {
            throw new BookingValidationException("bookingCode is required");
        }
        BookingIdempotencyClaim claim = idempotency.acquireClaim(idempotencyKey.trim(),
                "START_DEPOSIT_PAYMENT", "bookingCode=" + bookingCode,
                UUID.randomUUID().toString());
        if (claim.cachedOutcome()) {
            try {
                return mapper.readValue(claim.responsePayload(), DepositPaymentAttemptResponse.class);
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Stored deposit payment response could not be read", ex);
            }
        }

        Instant now = Instant.now();
        var booking = attemptService.requireBooking(bookingCode);
        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT
                || booking.getPaymentStatus() != PaymentStatus.UNPAID) {
            throw new BookingConflictException("Booking is not awaiting deposit payment");
        }
        if (booking.getHoldExpiresAt() == null || !booking.getHoldExpiresAt().isAfter(now)) {
            throw new BookingConflictException("Booking hold has expired");
        }
        var existing = attempts.findByBookingIdOrderByCreatedAtDesc(booking.getId()).stream().findFirst();
        if (existing.isPresent()) {
            var current = existing.get();
            if (current.getStatus() == PaymentAttemptStatus.PENDING) {
                if (current.getExpiresAt().isAfter(now)
                        && !current.getExpiresAt().isAfter(booking.getHoldExpiresAt())) {
                    return complete(claim, response(current));
                }
                current.setStatus(PaymentAttemptStatus.EXPIRED);
            }
        }
        Instant expiresAt = now.plusSeconds(10 * 60L);
        if (booking.getHoldExpiresAt().isBefore(expiresAt)) expiresAt = booking.getHoldExpiresAt();
        var invoice = attemptService.requireInvoice(booking.getId());
        String merchantReference = "DEP-" + bookingCode + "-" + UUID.randomUUID();
        String url = gateway.createDepositUrl(merchantReference, bookingCode, invoice.getDepositAmount(),
                now, expiresAt, clientIp);
        var attempt = attemptService.create(bookingCode, url, expiresAt, merchantReference, PROVIDER_DATE.format(now));
        return complete(claim, response(attempt));
    }

    private DepositPaymentAttemptResponse complete(BookingIdempotencyClaim claim,
                                                    DepositPaymentAttemptResponse response) {
        try {
            idempotency.completeClaimWithinTransaction(claim.recordId(), claim.claimVersion(),
                    200, mapper.writeValueAsString(response));
            return response;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Deposit payment response could not be stored", ex);
        }
    }

    private DepositPaymentAttemptResponse response(BookingPaymentAttempt a) {
        return new DepositPaymentAttemptResponse(a.getAttemptReference(), a.getMerchantReference(), a.getAttemptAmount(), "VND", a.getPaymentUrlSnapshot(), a.getExpiresAt(), a.getStatus().name());
    }
}
