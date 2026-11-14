package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 003.2 FR-035 / T095: a Manager sets or clears one session's reschedule quota.
 * {@code quota} null returns the session to the default floor(25% x capacity) with a minimum of 1;
 * 0 closes the session to customer changes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RescheduleQuotaRequest {

    @Min(value = 0, message = "quota must not be negative")
    private Integer quota;

    @NotBlank(message = "reason is required")
    @Size(max = 500, message = "reason must not exceed 500 characters")
    private String reason;
}
