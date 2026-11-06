package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewModerationEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewModerationEventRepository extends JpaRepository<ReviewModerationEvent, String> {

    List<ReviewModerationEvent> findByReviewIdOrderByCreatedAtDesc(String reviewId);

    Page<ReviewModerationEvent> findByReviewIdOrderByCreatedAtDesc(String reviewId, Pageable pageable);
}

