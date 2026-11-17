package edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.TemporaryImageReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemporaryImageReferenceRepository extends JpaRepository<TemporaryImageReference, Long> {

    Optional<TemporaryImageReference> findByDesignRequestId(String designRequestId);

    List<TemporaryImageReference> findByDeletedFalse();
}
