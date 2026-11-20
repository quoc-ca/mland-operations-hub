package edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.GuestDesignRequestQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface GuestDesignRequestQuotaRepository extends JpaRepository<GuestDesignRequestQuota, Long> {
    Optional<GuestDesignRequestQuota> findBySessionDigestAndQuotaDate(String sessionDigest, LocalDate quotaDate);
}
