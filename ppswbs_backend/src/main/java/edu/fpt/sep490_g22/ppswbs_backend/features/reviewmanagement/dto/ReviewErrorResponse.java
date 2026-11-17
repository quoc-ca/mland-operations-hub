package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewErrorResponse {
    private String code;
    private String message;
    private Instant timestamp;
    private String path;
    private String correlationId;
    private List<String> details;
}

