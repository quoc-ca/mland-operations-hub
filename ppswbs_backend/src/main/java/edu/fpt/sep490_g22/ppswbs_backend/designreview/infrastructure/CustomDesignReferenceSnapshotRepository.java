package edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.CustomDesignReferenceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomDesignReferenceSnapshotRepository extends JpaRepository<CustomDesignReferenceSnapshot, Long> {
    Optional<CustomDesignReferenceSnapshot> findTopByRequestIdOrderByCreatedAtDesc(String requestId);
    Optional<CustomDesignReferenceSnapshot> findTopByDesignReferenceOrderByCreatedAtDesc(String designReference);
}
