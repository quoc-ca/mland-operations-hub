package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.OtaChannelRequests.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class OtaFinancialEvidenceDtos {
    private OtaFinancialEvidenceDtos() {}

    public record PayoutReconciliationRequest(
            @NotBlank @Pattern(regexp = "PAYOUT|VARIANCE|DISPUTE|ADJUSTMENT|REVERSAL") String kind,
            @NotBlank @Pattern(regexp = "ELIGIBILITY|INVOICE|REMITTANCE|BANK_RECEIPT|ADJUSTMENT|REVERSAL|VARIANCE|DISPUTE") String evidenceType,
            @NotNull @Valid Money amount,
            @NotBlank @Size(max = 200) String reference,
            @NotBlank @Size(max = 1000) String reason,
            @NotNull @JsonDeserialize(using = OtaRequestScalars.Timestamp.class) Instant occurredAt,
            @Size(max = 36) String relatedEntryId,
            @Size(max = 128) String allocationReference,
            @Size(max = 200) String payoutReference) implements OtaStrictRequest {}

    public record FinancialEvidenceResponse(String channelBookingId,
                                             String customerPaymentState,
                                             String payoutState,
                                             List<Entry> entries) {}

    public record Entry(String entryId, String kind, String evidenceType, Money amount,
                        String reference, String reason, String relatedEntryId,
                        String allocationReference, String payoutReference,
                        Instant recordedAt) {}
}
