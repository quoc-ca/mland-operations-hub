package edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ImageConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImageConsentRepository extends JpaRepository<ImageConsent, Long> {

    Optional<ImageConsent> findByActorAccountIdAndDesignRequestIdAndConsentVersion(
            Long actorAccountId, String designRequestId, String consentVersion
    );

    boolean existsByActorAccountIdAndDesignRequestIdAndConsentVersion(
            Long actorAccountId, String designRequestId, String consentVersion
    );

    List<ImageConsent> findByDesignRequestId(String designRequestId);
}
