package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.OtaChannelRequests.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class OtaAddonDtos {
    private OtaAddonDtos() {}

    public record AddonQuoteRequest(
            @NotBlank @Pattern(regexp = "LASER_ENGRAVING|PAID_OPTION") String addonType,
            @Size(max = 100) String itemCode,
            @NotBlank @Size(max = 500) String itemDescription,
            @NotNull @Valid Money amount
    ) implements OtaStrictRequest {}

    public record AddonAcceptanceRequest(
            @NotNull @JsonDeserialize(using = OtaRequestScalars.Timestamp.class) Instant acceptedAt,
            @NotBlank @Size(max = 200) String acceptanceReference
    ) implements OtaStrictRequest {}

    public record AddonQuoteResponse(
            String quoteId,
            String channelBookingId,
            String addonType,
            String itemCode,
            Money amount,
            String status
    ) {}

    public record DirectBillLinkResponse(
            String quoteId,
            String settlementBillReference,
            String correlationId
    ) {}
}

