package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import java.time.Instant;
import java.util.List;

public final class OtaAddonPaymentDtos {
    private OtaAddonPaymentDtos() {}

    public record OtaAddonPaymentAttemptResponse(
            String attemptReference,
            String billReference,
            String merchantReference,
            Long amountVnd,
            String currency,
            String paymentUrl,
            Instant expiresAt,
            String status) {}

    public record OtaAddonRefundCommand(
            Long amountVnd,
            String reason) {}

    public record OtaAddonRefundProjection(
            String refundReference,
            String caseReference,
            String billReference,
            String channelBookingReference,
            Long amountVnd,
            String currency,
            String reason,
            String lifecycleStatus,
            Instant createdAt,
            Instant updatedAt) {}

    public record OtaAddonBillProjection(
            String billReference,
            String channelBookingReference,
            String quoteReference,
            String itemCode,
            String itemDescription,
            Long amountVnd,
            String currency,
            String lifecycleStatus,
            String locationId,
            Instant createdAt,
            OtaAddonPaymentAttemptResponse latestPaymentAttempt,
            List<OtaAddonRefundProjection> refunds) {}
}

