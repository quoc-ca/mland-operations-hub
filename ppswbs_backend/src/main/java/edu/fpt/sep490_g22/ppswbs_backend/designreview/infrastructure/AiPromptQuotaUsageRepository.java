package edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.AiPromptQuotaUsage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface AiPromptQuotaUsageRepository extends JpaRepository<AiPromptQuotaUsage, Long> {

    /**
     * Creates the daily row without turning a concurrent first request into a
     * failed transaction. The unique key remains the source of truth.
     */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO ai_prompt_quota_usage
                (quota_scope, quota_key_digest, quota_date, limit_count, used_count, created_at, updated_at)
            VALUES (:scope, :keyDigest, :date, :limitCount, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    int insertIfAbsent(@Param("scope") String quotaScope,
                       @Param("keyDigest") String quotaKeyDigest,
                       @Param("date") LocalDate quotaDate,
                       @Param("limitCount") int limitCount);

    /**
     * Consumes one slot only while the configured limit has not been reached.
     * The affected-row count is the atomic admission decision.
     */
    @Modifying
    @Query(value = """
            UPDATE ai_prompt_quota_usage
               SET used_count = used_count + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE quota_scope = :scope
               AND quota_key_digest = :keyDigest
               AND quota_date = :date
               AND used_count < limit_count
            """, nativeQuery = true)
    int incrementIfAvailable(@Param("scope") String quotaScope,
                             @Param("keyDigest") String quotaKeyDigest,
                             @Param("date") LocalDate quotaDate);

    Optional<AiPromptQuotaUsage> findByQuotaScopeAndQuotaKeyDigestAndQuotaDate(
            String quotaScope, String quotaKeyDigest, LocalDate quotaDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM AiPromptQuotaUsage q WHERE q.quotaScope = :scope AND q.quotaKeyDigest = :keyDigest AND q.quotaDate = :date")
    Optional<AiPromptQuotaUsage> findForUpdate(
            @Param("scope") String quotaScope,
            @Param("keyDigest") String quotaKeyDigest,
            @Param("date") LocalDate quotaDate);
}
