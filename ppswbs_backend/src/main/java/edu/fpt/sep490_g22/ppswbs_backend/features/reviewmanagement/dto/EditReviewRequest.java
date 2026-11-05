package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditReviewRequest {

    @NotNull(message = "rating is required")
    @Min(value = 1, message = "rating must be at least 1")
    @Max(value = 5, message = "rating must be at most 5")
    private Integer rating;

    @Size(max = 4000, message = "bodyText cannot exceed 4000 characters")
    private String bodyText;

    @Size(max = 1000, message = "reason cannot exceed 1000 characters")
    private String reason;

    @NotNull(message = "expectedVersion is required")
    private Long expectedVersion;

    private String guestHandle;
}

