package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogueMediaRepository extends JpaRepository<CatalogueMediaObject, UUID> {

    Optional<CatalogueMediaObject> findByStorageKey(String storageKey);

    /** Upload retry with the same command key returns the same staged object. */
    Optional<CatalogueMediaObject> findByUploadedByAndCommandId(String uploadedBy, UUID commandId);

    /** Media gate, always in ascending ID order so attach, retain and cleanup never deadlock each other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CatalogueMediaObject m where m.id in :ids order by m.id")
    List<CatalogueMediaObject> lockAllByIdIn(@Param("ids") Collection<UUID> ids);

    /** IDs of objects in one state, oldest first (re-verification of UNAVAILABLE images, T073). */
    @Query("select m.id from CatalogueMediaObject m where m.state = :state order by m.createdAt, m.id")
    List<UUID> findIdsByState(@Param("state") CatalogueMediaObject.State state, Pageable pageable);

    /**
     * Possible cleanup candidates, re-checked under the media lock: deletes to retry, objects never attached
     * since {@code neverAttachedBefore} and objects unreferenced since {@code detachedBefore}.
     */
    @Query("""
            select m.id from CatalogueMediaObject m
            where m.state = :pending
               or (m.state <> :deleted and m.firstAttachedAt is null and m.createdAt <= :neverAttachedBefore)
               or (m.state <> :deleted and m.lastUnreferencedAt is not null and m.lastUnreferencedAt <= :detachedBefore)
            order by m.id""")
    List<UUID> findCleanupCandidates(@Param("pending") CatalogueMediaObject.State pending,
                                     @Param("deleted") CatalogueMediaObject.State deleted,
                                     @Param("neverAttachedBefore") Instant neverAttachedBefore,
                                     @Param("detachedBefore") Instant detachedBefore,
                                     Pageable pageable);
}
