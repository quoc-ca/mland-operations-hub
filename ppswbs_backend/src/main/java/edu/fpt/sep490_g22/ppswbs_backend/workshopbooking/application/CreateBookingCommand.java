package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command object for creating a new workshop booking.
 * Both contactEmail and contactPhone are required for all bookings.
 * For Member bookings, values are auto-filled from profile but may be overridden.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingCommand {

    @NotNull(message = "packageId is required")
    private Long packageId;

    @NotNull(message = "sessionId is required")
    private Long sessionId;

    @Min(value = 1, message = "participantCount must be at least 1")
    private int participantCount;

    @NotBlank(message = "contactEmail is required")
    @Email(message = "contactEmail must be a valid email address")
    @Size(max = 254, message = "contactEmail must not exceed 254 characters")
    private String contactEmail;

    @NotBlank(message = "contactPhone is required")
    @Size(min = 7, max = 32, message = "contactPhone must be between 7 and 32 characters")
    private String contactPhone;

    @Size(max = 2000, message = "designReference must not exceed 2000 characters")
    private String designReference;
}
