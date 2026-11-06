package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.MediaReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaReferenceRepository extends JpaRepository<MediaReference, Long> {

    Optional<MediaReference> findByMediaObjectIdAndOwnerModuleAndOwnerRecordRef(UUID mediaObjectId, String ownerModule,
                                                                                String ownerRecordRef);

    /** Active references block cleanup; an unknown or unreadable registry must also block it. */
    boolean existsByMediaObjectIdAndReleasedAtIsNull(UUID mediaObjectId);

    List<MediaReference> findByMediaObjectId(UUID mediaObjectId);
}
