package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto;

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
public class ReplyCommandRequest {

    @NotBlank(message = "bodyText is required")
    @Size(max = 4000, message = "bodyText cannot exceed 4000 characters")
    private String bodyText;

    private Long expectedVersion;

    private String idempotencyKey;
}

