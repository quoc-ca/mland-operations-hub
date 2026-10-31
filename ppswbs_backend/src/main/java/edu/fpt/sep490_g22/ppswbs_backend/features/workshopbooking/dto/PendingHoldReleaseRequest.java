package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingHoldReleaseRequest {

    @Email(message = "contactEmail must be a valid email format")
    @Size(max = 255, message = "contactEmail must not exceed 255 characters")
    private String contactEmail;

    @Size(max = 64, message = "contactPhone must not exceed 64 characters")
    private String contactPhone;

    @NotBlank(message = "reason is required")
    @Size(max = 1000, message = "reason must not exceed 1000 characters")
    private String reason;
}
