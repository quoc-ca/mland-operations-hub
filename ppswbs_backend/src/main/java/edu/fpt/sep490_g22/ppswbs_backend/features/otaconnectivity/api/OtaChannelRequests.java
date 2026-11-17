package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class OtaChannelRequests {
    private OtaChannelRequests() {}

    public record MappingUpsert(
            @NotBlank @Size(max = 200) @OtaJsonString String providerProductReference,
            @NotBlank @Size(max = 200) @OtaJsonString String providerOptionReference,
            @NotBlank @Size(max = 200) @OtaJsonString String workshopOfferReference,
            @NotBlank @Size(max = 200) @OtaJsonString String sessionReference) implements OtaStrictRequest {}

    public record MappingUpdate(
            @NotBlank @Size(max = 200) @OtaJsonString String providerProductReference,
            @NotBlank @Size(max = 200) @OtaJsonString String providerOptionReference,
            @NotBlank @Size(max = 200) @OtaJsonString String workshopOfferReference,
            @NotBlank @Size(max = 200) @OtaJsonString String sessionReference,
            @NotNull @Min(1) @JsonDeserialize(using = OtaRequestScalars.Version.class) Long version) implements OtaStrictRequest {}

    public record Money(@NotNull @Pattern(regexp = "^-?[0-9]+(\\.[0-9]{1,4})?$") @OtaJsonString String amount,
                        @NotNull @Pattern(regexp = "^[A-Z]{3}$") @OtaJsonString String currency) implements OtaStrictRequest {}

    public record PriceTerms(@NotNull @Valid Money retailAmount, @NotNull @Valid Money targetNetAmount,
            @NotBlank @Size(max = 255) @OtaJsonString String providerConfirmationEvidenceReference,
            @NotNull @JsonDeserialize(using = OtaRequestScalars.Timestamp.class) Instant effectiveFrom, @JsonDeserialize(using = OtaRequestScalars.Timestamp.class) Instant effectiveTo,
            @NotBlank @Size(max = 500) @OtaJsonString String reason) implements OtaStrictRequest {}

    public record Sync(@NotNull @Pattern(regexp = "PREPARATION|FULL_RESYNC") @OtaJsonString String mode,
                       @NotBlank @Size(max = 500) @OtaJsonString String reason) implements OtaStrictRequest {}

    public record Reason(@NotBlank @Size(max = 500) @OtaJsonString String reason) implements OtaStrictRequest {}

    public record ReadinessEvidence(@NotNull @OtaJsonString @Pattern(regexp = "CONTRACT_ONBOARDING|COMMERCIAL_TERMS|CONNECTION_OWNERSHIP|CALLBACK_AUTHENTICATION|QUOTA_DELIVERY|PRODUCT_SESSION_MAPPING|DATA_ALLOWLIST|PREPARATION_SYNC|TICKET_DELIVERY_SCAN|CANCELLATION_REFUND|LATE_BOOKING_CAPACITY|FAILURE_RECONCILIATION") String check,
            @NotNull @OtaJsonString @Pattern(regexp = "VERIFIED|FAILED|MISSING") String outcome,
            @Size(min = 1, max = 255) @OtaJsonString String evidenceReference) implements OtaStrictRequest {}

    public record BookingEnable(@NotBlank @Size(max = 255) @OtaJsonString String readinessRecordReference,
            @NotNull @Size(min = 12, max = 12) List<@NotNull @Valid ReadinessEvidence> checks,
            @NotBlank @Size(max = 500) @OtaJsonString String reason) implements OtaStrictRequest {}
}
