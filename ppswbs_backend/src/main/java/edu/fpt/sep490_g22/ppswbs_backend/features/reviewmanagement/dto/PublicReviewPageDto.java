package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicReviewPageDto {
    private ReviewAggregateDto aggregate;
    private List<ReviewDto> items;
    private String nextCursor;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
}

