package edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ImageConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImageConsentRepository extends JpaRepository<ImageConsent, Long> {

    Optional<ImageConsent> findByMemberIdAndBookingCodeAndConsentVersion(
            Long memberId, String bookingCode, String consentVersion
    );

    boolean existsByMemberIdAndBookingCodeAndConsentVersion(
            Long memberId, String bookingCode, String consentVersion
    );
}
