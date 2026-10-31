package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.UniqueElements;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequest {

    @Positive(message = "packageId must be positive")
    private Long packageId;

    @Size(min = 1, max = 2, message = "sessionReferences must contain one or two sessions")
    @UniqueElements(message = "sessionReferences must be unique")
    private List<@NotNull UUID> sessionReferences;

    @Min(value = 1, message = "participantCount must be at least 1")
    private Integer participantCount;

    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "quoteRef must be a UUID")
    private String quoteRef;

    @NotBlank(message = "contactEmail is required")
    @Email(message = "contactEmail must be a valid email format")
    @Size(max = 254, message = "contactEmail must not exceed 254 characters")
    private String contactEmail;

    @NotBlank(message = "contactPhone is required")
    @Size(min = 7, max = 32, message = "contactPhone must be between 7 and 32 characters")
    private String contactPhone;

    @AssertTrue(message = "Provide either quoteRef or the complete Guest package/session selection")
    public boolean isRequestModeValid() {
        boolean memberQuote = quoteRef != null && !quoteRef.isBlank();
        if (memberQuote) {
            return packageId == null && sessionReferences == null && participantCount == null;
        }
        return packageId != null && sessionReferences != null && !sessionReferences.isEmpty()
                && participantCount != null;
    }
}
