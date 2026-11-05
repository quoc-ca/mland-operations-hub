package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Safe value projections assembled inside the application transaction. */
public final class OtaChannelViews {
    private OtaChannelViews() {}

    public record Mapping(UUID mappingId, UUID channelId, String provider, String state,
            String commercialStatus, boolean bookingEnabled, String safeReadinessStatus,
            long version, String salesControlStatus) {}

    public record Channels(List<Mapping> items) {
        public Channels { items = List.copyOf(items); }
    }

    public record Money(String amount, String currency) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PriceTerms(UUID priceTermsId, UUID mappingId, Money retailAmount, Money targetNetAmount,
            String providerConfirmationEvidenceReference, Instant effectiveFrom, Instant effectiveTo, String status) {}
}
