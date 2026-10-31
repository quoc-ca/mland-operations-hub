package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductImportBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductImportBatchRepository extends JpaRepository<ProductImportBatch, UUID> {

    /** Serializes confirmation and cleanup of one batch. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ProductImportBatch b where b.id = :id")
    Optional<ProductImportBatch> lockById(@Param("id") UUID id);

    /** Batches past expiry that still hold a payload or errors, or are still VALIDATED, oldest first. */
    @Query("""
            select b.id from ProductImportBatch b
            where b.expiresAt <= :now
              and (b.state = :validated or b.errors is not null or b.parsedPayload is not null)
            order by b.expiresAt, b.id""")
    List<UUID> findExpiredWithData(@Param("now") Instant now,
                                   @Param("validated") ProductImportBatch.State validated,
                                   Pageable pageable);
}
