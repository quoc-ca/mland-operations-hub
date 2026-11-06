package edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.DesignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DesignRequestRepository extends JpaRepository<DesignRequest, String> {

    Optional<DesignRequest> findByOwnerAccountIdAndRequestVersionAndIdempotencyKey(
            Long ownerAccountId, Integer requestVersion, String idempotencyKey
    );

    List<DesignRequest> findByOwnerAccountIdOrderByCreatedAtDesc(Long ownerAccountId);

    List<DesignRequest> findByReviewStatusOrderByCreatedAtAsc(ReviewStatus reviewStatus);

    Optional<DesignRequest> findByRequestReference(String requestReference);
}
