package edu.fpt.sep490_g22.ppswbs_backend.common.payment;

import java.time.Instant;

public interface PaymentGatewayCheckoutPort {
    String createDepositUrl(String merchantReference, String bookingCode, long amountVnd,
                             Instant createdAt, Instant expiresAt, String clientIp);

    String createFinalPaymentUrl(String merchantReference, String bookingCode, long amountVnd,
                                 Instant createdAt, Instant expiresAt, String clientIp);
}
