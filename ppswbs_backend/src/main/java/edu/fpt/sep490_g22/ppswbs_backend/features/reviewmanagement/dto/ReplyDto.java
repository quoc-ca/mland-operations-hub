package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyDto {
    private String id;
    private String reviewId;
    private String bodyText;
    private Long createdByManagerId;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}

