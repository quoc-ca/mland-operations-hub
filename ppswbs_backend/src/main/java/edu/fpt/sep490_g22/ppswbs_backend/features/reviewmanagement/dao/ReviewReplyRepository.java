package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewReplyRepository extends JpaRepository<ReviewReply, String> {

    Optional<ReviewReply> findByReviewId(String reviewId);

    List<ReviewReply> findByReviewIdIn(Collection<String> reviewIds);
}

