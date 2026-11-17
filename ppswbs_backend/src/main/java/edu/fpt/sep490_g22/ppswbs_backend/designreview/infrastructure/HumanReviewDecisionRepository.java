package edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.HumanReviewDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HumanReviewDecisionRepository extends JpaRepository<HumanReviewDecision, Long> {

    List<HumanReviewDecision> findByDesignRequestIdOrderByDecidedAtAsc(String designRequestId);
}
