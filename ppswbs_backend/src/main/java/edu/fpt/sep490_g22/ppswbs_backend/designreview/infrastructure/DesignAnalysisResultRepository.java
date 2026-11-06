package edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.DesignAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DesignAnalysisResultRepository extends JpaRepository<DesignAnalysisResult, Long> {

    Optional<DesignAnalysisResult> findByDesignRequestId(String designRequestId);
}
