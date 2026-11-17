package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRevisionRepository extends JpaRepository<ReviewRevision, String> {

    List<ReviewRevision> findByReviewIdOrderByRevisionNumberDesc(String reviewId);

    Optional<ReviewRevision> findTopByReviewIdOrderByRevisionNumberDesc(String reviewId);
}

