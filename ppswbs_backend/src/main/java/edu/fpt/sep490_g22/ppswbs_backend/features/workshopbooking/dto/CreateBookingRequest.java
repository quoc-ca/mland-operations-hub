package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.UniqueElements;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequest {

    @NotNull(message = "packageId is required")
    private Long packageId;

    @NotEmpty(message = "sessionReferences cannot be empty")
    @Size(min = 1, max = 2, message = "sessionReferences must contain 1 or 2 sessions")
    @UniqueElements(message = "sessionReferences must be unique")
    private List<@NotNull UUID> sessionReferences;

    @NotNull(message = "participantCount is required")
    @Min(value = 1, message = "participantCount must be at least 1")
    private Integer participantCount;

    @NotBlank(message = "contactEmail is required")
    @Email(message = "contactEmail must be a valid email format")
    @Size(max = 254, message = "contactEmail must not exceed 254 characters")
    private String contactEmail;

    @NotBlank(message = "contactPhone is required")
    @Size(min = 7, max = 32, message = "contactPhone must be between 7 and 32 characters")
    private String contactPhone;
}
