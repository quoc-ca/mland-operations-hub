package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao;

public interface ReviewAggregateProjection {
    Long getTotalCount();
    Double getAverageRating();
}

