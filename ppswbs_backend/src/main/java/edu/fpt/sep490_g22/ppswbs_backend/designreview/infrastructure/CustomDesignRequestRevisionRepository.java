package edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.CustomDesignRequestRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomDesignRequestRevisionRepository extends JpaRepository<CustomDesignRequestRevision, Long> {
    List<CustomDesignRequestRevision> findByRequestIdOrderByRevisionNumberAsc(String requestId);
}
