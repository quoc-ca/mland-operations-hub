package edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.DesignRequestImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DesignRequestImageRepository extends JpaRepository<DesignRequestImage, Long> {
    List<DesignRequestImage> findByDesignRequestId(String designRequestId);
    Optional<DesignRequestImage> findByImageReference(String imageReference);
}
