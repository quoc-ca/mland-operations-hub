package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CapacityHoldState;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * T026: Privacy-safe DTO representing a workshop session capacity hold.
 * <p>
 * Encapsulates hold reference, session, booking reference, requested seats,
 * lifecycle state, and expiration deadline.
 * Does not expose JPA entities, payment detail, or customer PII.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapacityHoldResponse {

    @NotNull(message = "reference is required")
    private String reference;

    @NotNull(message = "sessionId is required")
    private String sessionId;

    @NotNull(message = "bookingReference is required")
    private String bookingReference;

    @NotNull(message = "requestedTicketCount is required")
    @Min(value = 1, message = "requestedTicketCount must be at least 1")
    private Integer requestedTicketCount;

    @NotNull(message = "state is required")
    private CapacityHoldState state;

    @NotNull(message = "expiresAt is required")
    private Instant expiresAt;
}
