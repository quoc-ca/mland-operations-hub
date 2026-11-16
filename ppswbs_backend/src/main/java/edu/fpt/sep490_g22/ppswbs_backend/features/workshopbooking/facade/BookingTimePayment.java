package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import java.time.Instant;
import java.util.List;

/**
 * One online booking-time VNPay payment of a booking group (003.1 T150, for 003.3 FR-056). One record per payment;
 * {@code amountVnd} is the stored requested amount; {@code confirmedAt} is the group confirmation time and is null
 * unless the payment is CONFIRMED. Carries no contact PII, name, phone, email or OTP.
 */
public record BookingTimePayment(
        String bookingGroupCode,
        List<String> ticketCodes,
        long amountVnd,
        String method,
        BookingTimePaymentStatus verificationStatus,
        String merchantReference,
        String attemptReference,
        Instant createdAt,
        Instant confirmedAt,
        List<Long> locationIds
) {
    public static final String METHOD_VNPAY_ONLINE = "VNPAY_ONLINE_BOOKING_TIME";

    public BookingTimePayment {
        ticketCodes = ticketCodes == null ? List.of() : List.copyOf(ticketCodes);
        locationIds = locationIds == null ? List.of() : List.copyOf(locationIds);
    }
}
