package edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.AiPromptQuotaUsage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface AiPromptQuotaUsageRepository extends JpaRepository<AiPromptQuotaUsage, Long> {

    Optional<AiPromptQuotaUsage> findByQuotaScopeAndQuotaKeyDigestAndQuotaDate(
            String quotaScope, String quotaKeyDigest, LocalDate quotaDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM AiPromptQuotaUsage q WHERE q.quotaScope = :scope AND q.quotaKeyDigest = :keyDigest AND q.quotaDate = :date")
    Optional<AiPromptQuotaUsage> findForUpdate(
            @Param("scope") String quotaScope,
            @Param("keyDigest") String quotaKeyDigest,
            @Param("date") LocalDate quotaDate);
}
