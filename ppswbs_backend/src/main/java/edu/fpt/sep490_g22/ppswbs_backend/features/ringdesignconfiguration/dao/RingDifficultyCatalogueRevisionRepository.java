package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDifficultyCatalogueRevision;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RingDifficultyCatalogueRevisionRepository extends JpaRepository<RingDifficultyCatalogueRevision, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RingDifficultyCatalogueRevision r WHERE r.revisionScope = :scope")
    Optional<RingDifficultyCatalogueRevision> findLockedByRevisionScope(@Param("scope") String scope);
}
